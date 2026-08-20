package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.crypto.EncryptionUtil
import com.example.data.gemini.GeminiReminderService
import com.example.data.gemini.OfflineSmartEngine
import com.example.data.local.ReminderDao
import com.example.data.model.CrossPlatformReminderItem
import com.example.data.model.EncryptedBackupBundle
import com.example.data.model.PrivacyAuditEvent
import com.example.data.model.PrivacyTransparencyReport
import com.example.data.model.RecurrenceType
import com.example.data.model.Reminder
import com.example.data.model.SmartSuggestion
import com.example.receiver.NotificationHelper
import com.example.receiver.ReminderAlarmScheduler
import com.example.widget.WidgetUpdateHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Calendar

class ReminderRepository(
    private val context: Context,
    private val dao: ReminderDao,
    private val geminiService: GeminiReminderService = GeminiReminderService()
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val _privacyAuditEvents = MutableStateFlow<List<PrivacyAuditEvent>>(
        listOf(
            PrivacyAuditEvent(eventType = "INIT", detail = "Encrypted local SQLite Room storage initialized with zero telemetry.")
        )
    )
    val privacyAuditEvents: Flow<List<PrivacyAuditEvent>> = _privacyAuditEvents.asStateFlow()

    private var aiQueriesCount = 0

    val allReminders: Flow<List<Reminder>> = dao.getAllReminders()
    val activeReminders: Flow<List<Reminder>> = dao.getActiveReminders()
    val completedReminders: Flow<List<Reminder>> = dao.getCompletedReminders()
    val naggingReminders: Flow<List<Reminder>> = dao.getNaggingReminders()
    val recurringReminders: Flow<List<Reminder>> = dao.getRecurringReminders()

    fun getTodayReminders(): Flow<List<Reminder>> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfDay = cal.timeInMillis

        return dao.getTodayReminders(startOfDay, endOfDay)
    }

    suspend fun createReminder(reminder: Reminder): Long = withContext(Dispatchers.IO) {
        val id = dao.insertReminder(reminder)
        val saved = reminder.copy(id = id)
        
        // Schedule Alarm
        ReminderAlarmScheduler.scheduleAlarm(context, saved, saved.dueTimestamp)
        WidgetUpdateHelper.updateAllWidgets(context)

        logAudit("REMINDER_CREATED", "Reminder '${saved.title}' saved locally on device.")
        id
    }

    suspend fun updateReminder(reminder: Reminder) = withContext(Dispatchers.IO) {
        dao.updateReminder(reminder)
        ReminderAlarmScheduler.cancelAlarm(context, reminder.id)
        if (!reminder.isCompleted) {
            ReminderAlarmScheduler.scheduleAlarm(context, reminder, reminder.dueTimestamp)
        } else {
            NotificationHelper.cancelNotification(context, reminder.id)
        }
        WidgetUpdateHelper.updateAllWidgets(context)
        logAudit("REMINDER_UPDATED", "Updated reminder '${reminder.title}'.")
    }

    suspend fun toggleComplete(reminder: Reminder) = withContext(Dispatchers.IO) {
        if (!reminder.isCompleted) {
            dao.markCompleted(reminder.id, System.currentTimeMillis())
            NotificationHelper.cancelNotification(context, reminder.id)
            ReminderAlarmScheduler.cancelAlarm(context, reminder.id)

            // If recurring, advance to next occurrence
            if (reminder.getRecurrenceEnum() != RecurrenceType.NONE) {
                advanceRecurring(reminder)
            }
            logAudit("REMINDER_COMPLETED", "Task '${reminder.title}' checked off.")
        } else {
            dao.markUncompleted(reminder.id)
            ReminderAlarmScheduler.scheduleAlarm(context, reminder.copy(isCompleted = false), reminder.dueTimestamp)
            logAudit("REMINDER_RESTORED", "Task '${reminder.title}' reopened.")
        }
        WidgetUpdateHelper.updateAllWidgets(context)
    }

    private suspend fun advanceRecurring(reminder: Reminder) {
        val recurrence = reminder.getRecurrenceEnum()
        val cal = Calendar.getInstance()
        cal.timeInMillis = reminder.dueTimestamp
        val now = System.currentTimeMillis()
        if (cal.timeInMillis < now) cal.timeInMillis = now

        when (recurrence) {
            RecurrenceType.DAILY -> cal.add(Calendar.DAY_OF_YEAR, reminder.recurrenceInterval.coerceAtLeast(1))
            RecurrenceType.WEEKDAYS -> {
                do {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                } while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            }
            RecurrenceType.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, reminder.recurrenceInterval.coerceAtLeast(1))
            RecurrenceType.MONTHLY -> cal.add(Calendar.MONTH, reminder.recurrenceInterval.coerceAtLeast(1))
            RecurrenceType.CUSTOM_HOURS -> cal.add(Calendar.HOUR_OF_DAY, reminder.recurrenceInterval.coerceAtLeast(1))
            RecurrenceType.CUSTOM_DAYS -> cal.add(Calendar.DAY_OF_YEAR, reminder.recurrenceInterval.coerceAtLeast(1))
            RecurrenceType.NONE -> return
        }

        val next = reminder.copy(
            id = 0,
            dueTimestamp = cal.timeInMillis,
            isCompleted = false,
            completedAt = null,
            lastNotifiedAt = null,
            createdAt = System.currentTimeMillis()
        )
        val newId = dao.insertReminder(next)
        ReminderAlarmScheduler.scheduleAlarm(context, next.copy(id = newId), cal.timeInMillis)
    }

    suspend fun deleteReminder(id: Long) = withContext(Dispatchers.IO) {
        NotificationHelper.cancelNotification(context, id)
        ReminderAlarmScheduler.cancelAlarm(context, id)
        dao.deleteReminderById(id)
        WidgetUpdateHelper.updateAllWidgets(context)
        logAudit("REMINDER_DELETED", "Deleted reminder #$id.")
    }

    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        val all = dao.getAllRemindersListSync()
        all.forEach {
            NotificationHelper.cancelNotification(context, it.id)
            ReminderAlarmScheduler.cancelAlarm(context, it.id)
        }
        dao.deleteAllReminders()
        WidgetUpdateHelper.updateAllWidgets(context)
        logAudit("ALL_DATA_CLEARED", "All local database tables purged completely.")
    }

    suspend fun fetchSmartSuggestions(aiEnabled: Boolean): List<SmartSuggestion> = withContext(Dispatchers.IO) {
        val existing = dao.getAllActiveRemindersSync()
        if (aiEnabled) {
            aiQueriesCount++
            logAudit("AI_SUGGESTION_FETCH", "Contextual schedule suggestions requested (Privacy protected).")
        } else {
            logAudit("OFFLINE_SUGGESTION_FETCH", "On-device pattern suggestions generated without network.")
        }
        geminiService.generateSmartSuggestions(existing, aiEnabled)
    }

    suspend fun parseVoiceOrTextPrompt(prompt: String): OfflineSmartEngine.ParsedTask = withContext(Dispatchers.IO) {
        aiQueriesCount++
        logAudit("VOICE_PARSE", "Natural language parsed for voice task.")
        geminiService.parseVoiceOrTextCommand(prompt)
    }

    // Encrypted Cloud Backup & Cross-Platform Sync
    suspend fun exportEncryptedBackup(passphrase: String): String = withContext(Dispatchers.IO) {
        val all = dao.getAllRemindersListSync()
        val dtoList = all.map {
            CrossPlatformReminderItem(
                syncId = it.syncId,
                title = it.title,
                note = it.note,
                category = it.category,
                priority = it.priority,
                dueTimestamp = it.dueTimestamp,
                isCompleted = it.isCompleted,
                isNagging = it.isNagging,
                naggingIntervalMinutes = it.naggingIntervalMinutes,
                recurrenceType = it.recurrenceType,
                recurrenceInterval = it.recurrenceInterval,
                createdAt = it.createdAt
            )
        }

        val listType = Types.newParameterizedType(List::class.java, CrossPlatformReminderItem::class.java)
        val jsonPayload = moshi.adapter<List<CrossPlatformReminderItem>>(listType).toJson(dtoList)

        val encrypted = EncryptionUtil.encrypt(jsonPayload, passphrase.toCharArray())
        val bundle = EncryptedBackupBundle(
            saltBase64 = encrypted.saltBase64,
            ivBase64 = encrypted.ivBase64,
            encryptedDataCipherBase64 = encrypted.cipherTextBase64
        )

        val bundleJson = moshi.adapter(EncryptedBackupBundle::class.java).toJson(bundle)
        logAudit("ENCRYPTED_BACKUP_EXPORT", "Exported ${all.size} reminders encrypted with AES-256-GCM.")
        bundleJson
    }

    suspend fun importEncryptedBackup(backupJson: String, passphrase: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val bundle = moshi.adapter(EncryptedBackupBundle::class.java).fromJson(backupJson.trim())
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid backup bundle format"))

            val decryptedJson = EncryptionUtil.decrypt(
                cipherTextBase64 = bundle.encryptedDataCipherBase64,
                saltBase64 = bundle.saltBase64,
                ivBase64 = bundle.ivBase64,
                passphrase = passphrase.toCharArray()
            )

            val listType = Types.newParameterizedType(List::class.java, CrossPlatformReminderItem::class.java)
            val dtoList = moshi.adapter<List<CrossPlatformReminderItem>>(listType).fromJson(decryptedJson)
                ?: return@withContext Result.failure(IllegalArgumentException("Decrypted payload corrupted"))

            val restoredReminders = dtoList.map { item ->
                Reminder(
                    title = item.title,
                    note = item.note,
                    category = item.category,
                    priority = item.priority,
                    dueTimestamp = item.dueTimestamp,
                    isCompleted = item.isCompleted,
                    isNagging = item.isNagging,
                    naggingIntervalMinutes = item.naggingIntervalMinutes,
                    recurrenceType = item.recurrenceType,
                    recurrenceInterval = item.recurrenceInterval,
                    createdAt = item.createdAt,
                    syncId = item.syncId
                )
            }

            dao.insertAll(restoredReminders)
            val active = dao.getAllActiveRemindersSync()
            active.forEach {
                ReminderAlarmScheduler.scheduleAlarm(context, it, it.dueTimestamp)
            }
            WidgetUpdateHelper.updateAllWidgets(context)

            logAudit("ENCRYPTED_BACKUP_IMPORT", "Successfully decrypted & imported ${restoredReminders.size} reminders.")
            Result.success(restoredReminders.size)
        } catch (e: Exception) {
            Log.e("ReminderRepo", "Import failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getPrivacyReport(aiEnabled: Boolean): PrivacyTransparencyReport = withContext(Dispatchers.IO) {
        val count = dao.getAllRemindersListSync().size
        PrivacyTransparencyReport(
            totalRemindersStoredLocally = count,
            offlineSecurityLevel = "100% On-Device Room SQLite Database",
            encryptionStandard = "AES-256-GCM Zero-Knowledge Key",
            aiDataSharingEnabled = aiEnabled,
            totalAiQueriesProcessed = aiQueriesCount,
            auditEvents = _privacyAuditEvents.value
        )
    }

    private fun logAudit(type: String, detail: String) {
        val event = PrivacyAuditEvent(eventType = type, detail = detail)
        _privacyAuditEvents.value = (listOf(event) + _privacyAuditEvents.value).take(50)
    }
}
