package com.example.data.model

data class SmartSuggestion(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val note: String = "",
    val suggestedTimestamp: Long,
    val category: ReminderCategory,
    val priority: Priority = Priority.MEDIUM,
    val isNagging: Boolean = false,
    val naggingIntervalMinutes: Int = 5,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val recurrenceInterval: Int = 1,
    val reason: String, // e.g. "Based on your frequent 9:00 AM routines"
    val source: SuggestionSource = SuggestionSource.OFFLINE_PATTERN
)

enum class SuggestionSource {
    GEMINI_AI,
    OFFLINE_PATTERN,
    SCHEDULE_CONTEXT,
    BIXBY_ROUTINE
}
