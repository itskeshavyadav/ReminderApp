package com.example.data.gemini

import com.example.data.model.Priority
import com.example.data.model.RecurrenceType
import com.example.data.model.Reminder
import com.example.data.model.ReminderCategory
import com.example.data.model.SmartSuggestion
import com.example.data.model.SuggestionSource
import java.util.Calendar
import java.util.Locale

object OfflineSmartEngine {

    data class ParsedTask(
        val title: String,
        val note: String = "",
        val category: ReminderCategory = ReminderCategory.GENERAL,
        val priority: Priority = Priority.MEDIUM,
        val dueTimestamp: Long,
        val isNagging: Boolean = false,
        val naggingIntervalMinutes: Int = 5,
        val recurrenceType: RecurrenceType = RecurrenceType.NONE,
        val recurrenceInterval: Int = 1
    )

    /**
     * Parses natural language input (voice or text) on-device with zero network requirement.
     * Examples:
     * - "Remind me to take medicine every 8 hours and keep reminding me"
     * - "Call mom tomorrow at 6pm priority high"
     * - "Review budget every Friday at 4pm"
     * - "Drink water in 30 minutes"
     */
    fun parseNaturalLanguage(rawInput: String): ParsedTask {
        val text = rawInput.trim()
        val lower = text.lowercase(Locale.ROOT)
        
        var title = text
            .replace(Regex("^(remind me to|remember to|don't forget to|set a reminder to|create task|task|todo)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

        // Detect Keep Reminding / Nagging intent
        var isNagging = false
        var naggingInterval = 5
        if (lower.contains("keep reminding") || lower.contains("nag") || lower.contains("persist") || 
            lower.contains("don't stop") || lower.contains("until done") || lower.contains("urgent alert")) {
            isNagging = true
            when {
                lower.contains("every 2 min") -> naggingInterval = 2
                lower.contains("every 5 min") -> naggingInterval = 5
                lower.contains("every 10 min") -> naggingInterval = 10
                lower.contains("every 15 min") -> naggingInterval = 15
                lower.contains("every 30 min") -> naggingInterval = 30
            }
            // Clean up title
            title = title.replace(Regex("(and\\s+)?(keep reminding me|nag me|persistently|until done|every \\d+ min(utes)?)", RegexOption.IGNORE_CASE), "").trim()
        }

        // Detect Recurrence
        var recurrence = RecurrenceType.NONE
        var recurrenceInterval = 1
        when {
            lower.contains("every day") || lower.contains("daily") -> {
                recurrence = RecurrenceType.DAILY
                title = title.replace(Regex("(every day|daily)", RegexOption.IGNORE_CASE), "").trim()
            }
            lower.contains("every weekday") || lower.contains("weekdays") -> {
                recurrence = RecurrenceType.WEEKDAYS
                title = title.replace(Regex("(every weekday|weekdays)", RegexOption.IGNORE_CASE), "").trim()
            }
            lower.contains("every week") || lower.contains("weekly") -> {
                recurrence = RecurrenceType.WEEKLY
                title = title.replace(Regex("(every week|weekly)", RegexOption.IGNORE_CASE), "").trim()
            }
            lower.contains("every month") || lower.contains("monthly") -> {
                recurrence = RecurrenceType.MONTHLY
                title = title.replace(Regex("(every month|monthly)", RegexOption.IGNORE_CASE), "").trim()
            }
            lower.contains("every ") && lower.contains("hours") -> {
                recurrence = RecurrenceType.CUSTOM_HOURS
                val match = Regex("every (\\d+) hours?").find(lower)
                recurrenceInterval = match?.groupValues?.get(1)?.toIntOrNull() ?: 2
                title = title.replace(Regex("every \\d+ hours?", RegexOption.IGNORE_CASE), "").trim()
            }
            lower.contains("every ") && lower.contains("days") -> {
                recurrence = RecurrenceType.CUSTOM_DAYS
                val match = Regex("every (\\d+) days?").find(lower)
                recurrenceInterval = match?.groupValues?.get(1)?.toIntOrNull() ?: 2
                title = title.replace(Regex("every \\d+ days?", RegexOption.IGNORE_CASE), "").trim()
            }
        }

        // Detect Category
        var category = ReminderCategory.GENERAL
        when {
            lower.contains("doctor") || lower.contains("medicine") || lower.contains("pill") ||
            lower.contains("water") || lower.contains("walk") || lower.contains("workout") ||
            lower.contains("gym") || lower.contains("health") || lower.contains("stretch") -> {
                category = ReminderCategory.HEALTH
            }
            lower.contains("meeting") || lower.contains("email") || lower.contains("project") ||
            lower.contains("boss") || lower.contains("client") || lower.contains("work") ||
            lower.contains("presentation") || lower.contains("report") || lower.contains("code") -> {
                category = ReminderCategory.WORK
            }
            lower.contains("call") || lower.contains("mom") || lower.contains("dad") ||
            lower.contains("birthday") || lower.contains("buy") || lower.contains("grocery") ||
            lower.contains("dinner") || lower.contains("home") -> {
                category = ReminderCategory.PERSONAL
            }
            lower.contains("read") || lower.contains("meditat") || lower.contains("journal") ||
            lower.contains("habit") || lower.contains("routine") -> {
                category = ReminderCategory.HABIT
            }
            lower.contains("pay") || lower.contains("bill") || lower.contains("rent") ||
            lower.contains("tax") || lower.contains("budget") || lower.contains("bank") ||
            lower.contains("finance") || lower.contains("invoice") -> {
                category = ReminderCategory.FINANCES
            }
        }

        // Detect Priority
        var priority = Priority.MEDIUM
        when {
            lower.contains("urgent") || lower.contains("asap") || isNagging -> {
                priority = Priority.URGENT
            }
            lower.contains("important") || lower.contains("high priority") -> {
                priority = Priority.HIGH
            }
            lower.contains("low priority") || lower.contains("someday") || lower.contains("casual") -> {
                priority = Priority.LOW
            }
        }

        // Detect Date & Time
        val cal = Calendar.getInstance()
        var timeSet = false

        // Check relative time offsets
        val inMinutesMatch = Regex("in (\\d+) min(utes)?").find(lower)
        val inHoursMatch = Regex("in (\\d+) hours?").find(lower)

        if (inMinutesMatch != null) {
            val mins = inMinutesMatch.groupValues[1].toIntOrNull() ?: 10
            cal.add(Calendar.MINUTE, mins)
            timeSet = true
            title = title.replace(Regex("in \\d+ min(utes)?", RegexOption.IGNORE_CASE), "").trim()
        } else if (inHoursMatch != null) {
            val hrs = inHoursMatch.groupValues[1].toIntOrNull() ?: 1
            cal.add(Calendar.HOUR_OF_DAY, hrs)
            timeSet = true
            title = title.replace(Regex("in \\d+ hours?", RegexOption.IGNORE_CASE), "").trim()
        } else {
            // Check day
            if (lower.contains("tomorrow")) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                title = title.replace("tomorrow", "", ignoreCase = true).trim()
            } else if (lower.contains("tonight")) {
                cal.set(Calendar.HOUR_OF_DAY, 20)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                timeSet = true
                title = title.replace("tonight", "", ignoreCase = true).trim()
            } else if (lower.contains("this afternoon")) {
                cal.set(Calendar.HOUR_OF_DAY, 14)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                timeSet = true
                title = title.replace("this afternoon", "", ignoreCase = true).trim()
            } else if (lower.contains("this morning")) {
                cal.set(Calendar.HOUR_OF_DAY, 9)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                timeSet = true
                title = title.replace("this morning", "", ignoreCase = true).trim()
            }

            // Check specific time e.g. "at 3pm", "at 9:30 am", "at 18:00"
            val atTimeMatch = Regex("at (\\d{1,2})(:(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(lower)
            if (atTimeMatch != null) {
                var hour = atTimeMatch.groupValues[1].toIntOrNull() ?: 9
                val minute = atTimeMatch.groupValues[3].toIntOrNull() ?: 0
                val ampm = atTimeMatch.groupValues[4].lowercase(Locale.ROOT)
                if (ampm == "pm" && hour < 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                timeSet = true
                title = title.replace(Regex("at \\d{1,2}(:\\d{2})?\\s*(am|pm)?", RegexOption.IGNORE_CASE), "").trim()
            }
        }

        // If no explicit time was stated, default to 1 hour from now or next logical morning
        if (!timeSet) {
            cal.add(Calendar.HOUR_OF_DAY, 1)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
        }

        // Ensure title is not empty
        if (title.isBlank()) {
            title = if (rawInput.isNotBlank()) rawInput else "New Reminder"
        }

        return ParsedTask(
            title = title,
            category = category,
            priority = priority,
            dueTimestamp = cal.timeInMillis,
            isNagging = isNagging,
            naggingIntervalMinutes = naggingInterval,
            recurrenceType = recurrence,
            recurrenceInterval = recurrenceInterval
        )
    }

    /**
     * Generates intelligent offline smart suggestions by analyzing existing reminders,
     * recent activity patterns, time of day, and recurring gaps.
     */
    fun generateOfflineSuggestions(
        existingReminders: List<Reminder>,
        currentTime: Long = System.currentTimeMillis()
    ): List<SmartSuggestion> {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTime }
        val hourOfDay = cal.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val suggestions = mutableListOf<SmartSuggestion>()

        val existingTitles = existingReminders.map { it.title.lowercase(Locale.ROOT) }

        // Morning Routine suggestions (6:00 - 11:00)
        if (hourOfDay in 6..11) {
            if (!existingTitles.any { it.contains("hydrate") || it.contains("water") }) {
                suggestions.add(
                    SmartSuggestion(
                        title = "Drink 500ml Water & Morning Stretch",
                        note = "Daily hydration boost based on your morning schedule",
                        suggestedTimestamp = cal.timeInMillis + 15 * 60 * 1000,
                        category = ReminderCategory.HEALTH,
                        priority = Priority.MEDIUM,
                        isNagging = false,
                        recurrenceType = RecurrenceType.DAILY,
                        reason = "Recommended for your morning routine",
                        source = SuggestionSource.OFFLINE_PATTERN
                    )
                )
            }
            if (!existingTitles.any { it.contains("plan") || it.contains("priority") }) {
                suggestions.add(
                    SmartSuggestion(
                        title = "Review Daily Priorities & Schedule",
                        note = "Identify top 3 outcomes for today",
                        suggestedTimestamp = cal.timeInMillis + 30 * 60 * 1000,
                        category = ReminderCategory.WORK,
                        priority = Priority.HIGH,
                        isNagging = false,
                        recurrenceType = RecurrenceType.WEEKDAYS,
                        reason = "Optimizes peak focus hours",
                        source = SuggestionSource.SCHEDULE_CONTEXT
                    )
                )
            }
        }

        // Afternoon / Work Focus (12:00 - 17:00)
        if (hourOfDay in 12..17) {
            if (!existingTitles.any { it.contains("break") || it.contains("walk") || it.contains("posture") }) {
                suggestions.add(
                    SmartSuggestion(
                        title = "Take a 5-Minute Screen Break & Walk",
                        note = "Rest eyes and reset physical posture",
                        suggestedTimestamp = cal.timeInMillis + 45 * 60 * 1000,
                        category = ReminderCategory.HEALTH,
                        priority = Priority.LOW,
                        isNagging = false,
                        recurrenceType = RecurrenceType.CUSTOM_HOURS,
                        recurrenceInterval = 3,
                        reason = "Based on continuous activity pattern",
                        source = SuggestionSource.OFFLINE_PATTERN
                    )
                )
            }
            if (dayOfWeek == Calendar.FRIDAY && !existingTitles.any { it.contains("weekly") || it.contains("wrap") }) {
                suggestions.add(
                    SmartSuggestion(
                        title = "Friday Weekly Wrap-Up & Inbox Zero",
                        note = "Archive finished tasks and organize next week",
                        suggestedTimestamp = cal.apply { set(Calendar.HOUR_OF_DAY, 16); set(Calendar.MINUTE, 30) }.timeInMillis,
                        category = ReminderCategory.WORK,
                        priority = Priority.MEDIUM,
                        isNagging = false,
                        recurrenceType = RecurrenceType.WEEKLY,
                        reason = "Recurring Friday afternoon event",
                        source = SuggestionSource.SCHEDULE_CONTEXT
                    )
                )
            }
        }

        // Evening / Night (18:00 - 23:00)
        if (hourOfDay in 18..23 || hourOfDay in 0..5) {
            if (!existingTitles.any { it.contains("medication") || it.contains("vitamin") || it.contains("pill") }) {
                suggestions.add(
                    SmartSuggestion(
                        title = "Evening Vitamins & Prescription",
                        note = "Take with water before sleep. High persistence enabled.",
                        suggestedTimestamp = cal.apply { set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0) }.timeInMillis,
                        category = ReminderCategory.HEALTH,
                        priority = Priority.URGENT,
                        isNagging = true,
                        naggingIntervalMinutes = 5,
                        recurrenceType = RecurrenceType.DAILY,
                        reason = "Critical recurring health task with keep-reminding",
                        source = SuggestionSource.OFFLINE_PATTERN
                    )
                )
            }
            if (!existingTitles.any { it.contains("wind down") || it.contains("read") || it.contains("journal") }) {
                suggestions.add(
                    SmartSuggestion(
                        title = "Wind-Down Routine & Reading",
                        note = "Put away blue light screens 30 minutes before bed",
                        suggestedTimestamp = cal.apply { set(Calendar.HOUR_OF_DAY, 22); set(Calendar.MINUTE, 15) }.timeInMillis,
                        category = ReminderCategory.HABIT,
                        priority = Priority.LOW,
                        isNagging = false,
                        recurrenceType = RecurrenceType.DAILY,
                        reason = "Encourages restorative sleep hygiene",
                        source = SuggestionSource.OFFLINE_PATTERN
                    )
                )
            }
        }

        // End of Month financial reminder
        val lastDayOfMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        if (lastDayOfMonth - currentDay <= 3 && !existingTitles.any { it.contains("bill") || it.contains("rent") || it.contains("budget") }) {
            suggestions.add(
                SmartSuggestion(
                    title = "Review Monthly Subscriptions & Pay Bills",
                    note = "Check utility statements and reconcile balance",
                    suggestedTimestamp = cal.apply { set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0) }.timeInMillis,
                    category = ReminderCategory.FINANCES,
                    priority = Priority.HIGH,
                    isNagging = true,
                    naggingIntervalMinutes = 15,
                    recurrenceType = RecurrenceType.MONTHLY,
                    reason = "End-of-month financial schedule",
                    source = SuggestionSource.SCHEDULE_CONTEXT
                )
            )
        }

        return suggestions
    }
}
