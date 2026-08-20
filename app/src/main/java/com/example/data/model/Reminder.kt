package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String = "",
    val category: String = ReminderCategory.GENERAL.name,
    val priority: String = Priority.MEDIUM.name,
    val dueTimestamp: Long, // Epoch millis
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    
    // Persistent "Keep Reminding" / Nagging Option
    val isNagging: Boolean = false, // If true, sounds/notifies every interval until marked done
    val naggingIntervalMinutes: Int = 5, // e.g. 5, 10, 15, 30 minutes
    
    // Recurring tasks with custom intervals
    val recurrenceType: String = RecurrenceType.NONE.name,
    val recurrenceInterval: Int = 1, // e.g. every 2 days, every 4 hours, every 3 weeks
    val recurrenceDaysOfWeek: String = "", // e.g. "1,2,3,4,5"
    
    // Smart metadata & Cross-platform sync
    val createdAt: Long = System.currentTimeMillis(),
    val lastNotifiedAt: Long? = null,
    val aiSuggested: Boolean = false,
    val syncId: String = java.util.UUID.randomUUID().toString(),
    val isSynced: Boolean = true
) {
    fun getPriorityEnum(): Priority = try {
        Priority.valueOf(priority)
    } catch (e: Exception) {
        Priority.MEDIUM
    }

    fun getRecurrenceEnum(): RecurrenceType = try {
        RecurrenceType.valueOf(recurrenceType)
    } catch (e: Exception) {
        RecurrenceType.NONE
    }

    fun getCategoryEnum(): ReminderCategory = try {
        ReminderCategory.valueOf(category)
    } catch (e: Exception) {
        ReminderCategory.GENERAL
    }
}
