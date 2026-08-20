package com.example.data.model

enum class Priority(val label: String, val level: Int) {
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3),
    URGENT("Urgent", 4);

    val displayName: String get() = label
}

enum class RecurrenceType(val label: String) {
    NONE("Does not repeat"),
    DAILY("Every day"),
    WEEKDAYS("Mon - Fri"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
    CUSTOM_HOURS("Custom hours"),
    CUSTOM_DAYS("Custom days");

    val displayName: String get() = label
}

enum class ReminderCategory(val label: String, val iconName: String, val icon: String = "📋") {
    GENERAL("General", "task", "📋"),
    WORK("Work", "work", "💼"),
    HEALTH("Health", "favorite", "❤️"),
    PERSONAL("Personal", "person", "👤"),
    HABIT("Habits", "loop", "🔁"),
    FINANCES("Finances", "account_balance", "💰");

    val displayName: String get() = label
}
