package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CrossPlatformReminderItem(
    val syncId: String,
    val title: String,
    val note: String,
    val category: String,
    val priority: String,
    val dueTimestamp: Long,
    val isCompleted: Boolean,
    val isNagging: Boolean,
    val naggingIntervalMinutes: Int,
    val recurrenceType: String,
    val recurrenceInterval: Int,
    val createdAt: Long
)

@JsonClass(generateAdapter = true)
data class EncryptedBackupBundle(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val saltBase64: String,
    val ivBase64: String,
    val encryptedDataCipherBase64: String,
    val authTagCheck: String = "REMIND_AES_256_GCM_V1"
)

data class PrivacyTransparencyReport(
    val totalRemindersStoredLocally: Int,
    val offlineSecurityLevel: String = "100% On-Device Room Database",
    val encryptionStandard: String = "AES-256-GCM Zero-Knowledge Key",
    val aiDataSharingEnabled: Boolean = false,
    val totalAiQueriesProcessed: Int = 0,
    val auditEvents: List<PrivacyAuditEvent> = emptyList()
)

data class PrivacyAuditEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val detail: String
)
