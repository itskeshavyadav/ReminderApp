package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Reminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, dueTimestamp ASC")
    fun getAllReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY dueTimestamp ASC")
    fun getActiveReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 1 ORDER BY completedAt DESC, dueTimestamp DESC")
    fun getCompletedReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 AND dueTimestamp >= :startOfDay AND dueTimestamp <= :endOfDay ORDER BY dueTimestamp ASC")
    fun getTodayReminders(startOfDay: Long, endOfDay: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 AND isNagging = 1 ORDER BY dueTimestamp ASC")
    fun getNaggingReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE recurrenceType != 'NONE' ORDER BY isCompleted ASC, dueTimestamp ASC")
    fun getRecurringReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun getReminderById(id: Long): Flow<Reminder?>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderByIdSync(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY dueTimestamp ASC")
    suspend fun getAllActiveRemindersSync(): List<Reminder>

    @Query("SELECT * FROM reminders")
    suspend fun getAllRemindersListSync(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY dueTimestamp ASC LIMIT 5")
    suspend fun getTopUpcomingRemindersSync(): List<Reminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<Reminder>)

    @Update
    suspend fun updateReminder(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun deleteAllReminders()

    @Query("UPDATE reminders SET isCompleted = 1, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long = System.currentTimeMillis())

    @Query("UPDATE reminders SET isCompleted = 0, completedAt = NULL WHERE id = :id")
    suspend fun markUncompleted(id: Long)

    @Query("UPDATE reminders SET dueTimestamp = :newDueTime, lastNotifiedAt = :lastNotifiedAt WHERE id = :id")
    suspend fun updateDueTimestamp(id: Long, newDueTime: Long, lastNotifiedAt: Long? = null)
}
