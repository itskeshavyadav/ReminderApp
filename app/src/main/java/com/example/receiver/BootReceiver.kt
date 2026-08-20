package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d("BootReceiver", "Device rebooted, rescheduling active reminders...")
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val activeReminders = db.reminderDao().getAllActiveRemindersSync()
                val now = System.currentTimeMillis()

                activeReminders.forEach { reminder ->
                    if (reminder.dueTimestamp > now || reminder.isNagging) {
                        ReminderAlarmScheduler.scheduleAlarm(context, reminder, reminder.dueTimestamp)
                    }
                }
            }
        }
    }
}
