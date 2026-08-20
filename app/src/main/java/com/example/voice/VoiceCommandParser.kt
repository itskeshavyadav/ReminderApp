package com.example.voice

import android.content.Intent
import android.provider.AlarmClock
import com.example.data.gemini.OfflineSmartEngine
import com.example.data.model.Priority
import com.example.data.model.Reminder
import java.util.Calendar

object VoiceCommandParser {

    /**
     * Parses incoming Android Voice / Bixby / Google Assistant intents
     */
    fun parseAssistantIntent(intent: Intent?): Reminder? {
        if (intent == null) return null

        val action = intent.action ?: return null

        if (action == AlarmClock.ACTION_SET_ALARM || action == AlarmClock.ACTION_SET_TIMER) {
            val message = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE) ?: "Voice Reminder"
            val hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1)
            val minutes = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1)

            val cal = Calendar.getInstance()
            if (hour != -1) {
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, if (minutes != -1) minutes else 0)
                cal.set(Calendar.SECOND, 0)
                if (cal.timeInMillis < System.currentTimeMillis()) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            } else {
                cal.add(Calendar.HOUR_OF_DAY, 1)
            }

            return Reminder(
                title = message,
                dueTimestamp = cal.timeInMillis,
                priority = Priority.HIGH.name,
                aiSuggested = true
            )
        }

        if (action == "com.aistudio.reminders.ACTION_CREATE_REMINDER") {
            val text = intent.getStringExtra("query") ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            val parsed = OfflineSmartEngine.parseNaturalLanguage(text)
            return Reminder(
                title = parsed.title,
                note = parsed.note,
                category = parsed.category.name,
                priority = parsed.priority.name,
                dueTimestamp = parsed.dueTimestamp,
                isNagging = parsed.isNagging,
                naggingIntervalMinutes = parsed.naggingIntervalMinutes,
                recurrenceType = parsed.recurrenceType.name,
                recurrenceInterval = parsed.recurrenceInterval,
                aiSuggested = true
            )
        }

        return null
    }
}
