package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.RecurrenceType
import com.example.data.model.Reminder
import com.example.widget.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REMINDER_ALARM = "com.example.ACTION_REMINDER_ALARM"
        const val ACTION_REMINDER_DONE = "com.example.ACTION_REMINDER_DONE"
        const val ACTION_REMINDER_SNOOZE = "com.example.ACTION_REMINDER_SNOOZE"
        const val ACTION_REMINDER_NAG = "com.example.ACTION_REMINDER_NAG"

        const val EXTRA_REMINDER_ID = "EXTRA_REMINDER_ID"
        const val EXTRA_SNOOZE_MINUTES = "EXTRA_SNOOZE_MINUTES"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return

        val action = intent.action ?: ACTION_REMINDER_ALARM
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.reminderDao()
                val reminder = dao.getReminderByIdSync(reminderId) ?: return@launch

                when (action) {
                    ACTION_REMINDER_ALARM -> {
                        if (reminder.isCompleted) return@launch

                        // 1. Show notification
                        NotificationHelper.showReminderNotification(context, reminder)

                        // 2. If persistent "Keep Reminding" / Nagging is enabled, schedule next nag alarm
                        if (reminder.isNagging) {
                            val intervalMs = reminder.naggingIntervalMinutes.coerceAtLeast(1) * 60 * 1000L
                            val nextNagTime = System.currentTimeMillis() + intervalMs
                            ReminderAlarmScheduler.scheduleAlarm(context, reminder, nextNagTime)
                            Log.d("ReminderAlarm", "Nagging reminder #${reminder.id} re-scheduled for +${reminder.naggingIntervalMinutes}m")
                        }
                    }

                    ACTION_REMINDER_DONE -> {
                        // Cancel current notification and any scheduled nagging alarms
                        NotificationHelper.cancelNotification(context, reminder.id)
                        ReminderAlarmScheduler.cancelAlarm(context, reminder.id)

                        // Mark completed in database
                        dao.markCompleted(reminder.id, System.currentTimeMillis())

                        // Handle recurring task advancement
                        handleRecurrenceOnComplete(context, dao, reminder)

                        WidgetUpdateHelper.updateAllWidgets(context)
                    }

                    ACTION_REMINDER_SNOOZE -> {
                        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
                        NotificationHelper.cancelNotification(context, reminder.id)

                        val snoozedTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
                        dao.updateDueTimestamp(reminder.id, snoozedTime, System.currentTimeMillis())
                        ReminderAlarmScheduler.scheduleAlarm(context, reminder.copy(dueTimestamp = snoozedTime), snoozedTime)

                        WidgetUpdateHelper.updateAllWidgets(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleRecurrenceOnComplete(
        context: Context,
        dao: com.example.data.local.ReminderDao,
        reminder: Reminder
    ) {
        val recurrence = reminder.getRecurrenceEnum()
        if (recurrence == RecurrenceType.NONE) return

        val cal = Calendar.getInstance()
        cal.timeInMillis = reminder.dueTimestamp

        val now = System.currentTimeMillis()
        if (cal.timeInMillis < now) {
            // Base next occurrence relative to now or original due time
            cal.timeInMillis = now
        }

        when (recurrence) {
            RecurrenceType.DAILY -> {
                cal.add(Calendar.DAY_OF_YEAR, reminder.recurrenceInterval.coerceAtLeast(1))
            }
            RecurrenceType.WEEKDAYS -> {
                do {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                } while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            }
            RecurrenceType.WEEKLY -> {
                cal.add(Calendar.WEEK_OF_YEAR, reminder.recurrenceInterval.coerceAtLeast(1))
            }
            RecurrenceType.MONTHLY -> {
                cal.add(Calendar.MONTH, reminder.recurrenceInterval.coerceAtLeast(1))
            }
            RecurrenceType.CUSTOM_HOURS -> {
                cal.add(Calendar.HOUR_OF_DAY, reminder.recurrenceInterval.coerceAtLeast(1))
            }
            RecurrenceType.CUSTOM_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, reminder.recurrenceInterval.coerceAtLeast(1))
            }
            RecurrenceType.NONE -> {}
        }

        val nextOccurrence = reminder.copy(
            id = 0, // Auto-generate new primary key
            dueTimestamp = cal.timeInMillis,
            isCompleted = false,
            completedAt = null,
            lastNotifiedAt = null,
            createdAt = System.currentTimeMillis()
        )

        val newId = dao.insertReminder(nextOccurrence)
        val savedNewOccurrence = nextOccurrence.copy(id = newId)
        ReminderAlarmScheduler.scheduleAlarm(context, savedNewOccurrence, savedNewOccurrence.dueTimestamp)
        Log.d("ReminderAlarm", "Scheduled next recurring occurrence #$newId for ${cal.time}")
    }
}
