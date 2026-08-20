package com.example.widget.glance

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.data.model.RecurrenceType
import com.example.data.model.Reminder
import com.example.receiver.ReminderAlarmScheduler
import com.example.widget.WidgetUpdateHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RemindersGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getDatabase(context)
        val activeReminders = db.reminderDao().getAllActiveRemindersSync()

        provideContent {
            GlanceTheme {
                RemindersWidgetContent(context = context, reminders = activeReminders)
            }
        }
    }

    companion object {
        val ActionOpenAddKey = ActionParameters.Key<Boolean>("open_add_reminder")
        val ActionOpenVoiceKey = ActionParameters.Key<Boolean>("open_voice_command")
    }
}

@Composable
private fun RemindersWidgetContent(context: Context, reminders: List<Reminder>) {
    val mainActivityComponent = ComponentName(context, MainActivity::class.java)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFFFDFBFF))
            .cornerRadius(24.dp)
            .padding(14.dp)
    ) {
        // Header Row: App title, voice mic, and + Add Quick Action
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.End
        ) {
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(mainActivityComponent))
            ) {
                Text(
                    text = "UPCOMING",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF74777F)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Reminders",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF1A1C1E)),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Voice Quick Action Button
            Box(
                modifier = GlanceModifier
                    .size(36.dp)
                    .background(Color(0xFFD3E4FF))
                    .cornerRadius(12.dp)
                    .clickable(
                        actionStartActivity<MainActivity>(
                            actionParametersOf(RemindersGlanceWidget.ActionOpenVoiceKey to true)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎤",
                    style = TextStyle(fontSize = 14.sp)
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Add Quick Action Button
            Box(
                modifier = GlanceModifier
                    .height(36.dp)
                    .background(Color(0xFF0061A4))
                    .cornerRadius(12.dp)
                    .padding(horizontal = 12.dp)
                    .clickable(
                        actionStartActivity<MainActivity>(
                            actionParametersOf(RemindersGlanceWidget.ActionOpenAddKey to true)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "+ Task",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // List of Reminders or Empty State
        if (reminders.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFFF1F0F4))
                    .cornerRadius(16.dp)
                    .padding(16.dp)
                    .clickable(
                        actionStartActivity<MainActivity>(
                            actionParametersOf(RemindersGlanceWidget.ActionOpenAddKey to true)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✨ All caught up!",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF1A1C1E)),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "Tap '+ Task' to add a reminder",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF74777F)),
                            fontSize = 12.sp
                        )
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                items(reminders, itemId = { it.id }) { reminder ->
                    GlanceReminderRow(context = context, reminder = reminder)
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun GlanceReminderRow(context: Context, reminder: Reminder) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    val isToday = isToday(reminder.dueTimestamp)
    val timeLabel = if (isToday) {
        "Today, ${timeFormat.format(Date(reminder.dueTimestamp))}"
    } else {
        "${dateFormat.format(Date(reminder.dueTimestamp))}, ${timeFormat.format(Date(reminder.dueTimestamp))}"
    }

    val hasRecurrence = reminder.recurrenceType != RecurrenceType.NONE.name
    val isNagging = reminder.isNagging

    val mainActivityComponent = ComponentName(context, MainActivity::class.java)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(Color(0xFFF1F0F4))
            .cornerRadius(14.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Complete action checkbox button
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .background(Color(0xFFE0E2EC))
                .cornerRadius(8.dp)
                .clickable(
                    actionRunCallback<CompleteReminderGlanceCallback>(
                        actionParametersOf(CompleteReminderGlanceCallback.ReminderIdKey to reminder.id)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF0061A4)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = GlanceModifier.width(10.dp))

        // Title and Time info
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(mainActivityComponent))
        ) {
            Text(
                text = reminder.title,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color(0xFF1A1C1E)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeLabel,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF74777F)),
                        fontSize = 11.sp
                    )
                )

                if (isNagging) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "• ⚡ ${reminder.naggingIntervalMinutes}m",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFD97706)),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                } else if (hasRecurrence) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "• 🔁",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF0061A4)),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

private fun isToday(timestamp: Long): Boolean {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    return cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
}

class CompleteReminderGlanceCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val reminderId = parameters[ReminderIdKey] ?: return
        val db = AppDatabase.getDatabase(context)
        db.reminderDao().markCompleted(reminderId)
        ReminderAlarmScheduler.cancelAlarm(context, reminderId)

        // Update all Glance widgets and standard widgets
        RemindersGlanceWidget().updateAll(context)
        WidgetUpdateHelper.updateAllWidgets(context)
    }

    companion object {
        val ReminderIdKey = ActionParameters.Key<Long>("reminder_id")
    }
}
