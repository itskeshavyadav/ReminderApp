package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Priority
import com.example.data.model.PrivacyTransparencyReport
import com.example.data.model.RecurrenceType
import com.example.data.model.Reminder
import com.example.data.model.ReminderCategory
import com.example.data.model.SmartSuggestion
import com.example.data.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class ReminderFilterTab(val label: String) {
    ALL("All"),
    TODAY("Today"),
    NAGGING("Persistent ⚡"),
    RECURRING("Recurring 🔁"),
    COMPLETED("Completed ✓")
}

data class ReminderUiState(
    val reminders: List<Reminder> = emptyList(),
    val activeFilterTab: ReminderFilterTab = ReminderFilterTab.ALL,
    val selectedCategory: ReminderCategory? = null,
    val searchQuery: String = "",
    val smartSuggestions: List<SmartSuggestion> = emptyList(),
    val isSuggestionsLoading: Boolean = false,
    val aiDataSharingEnabled: Boolean = false, // Defaults to 100% offline privacy
    val privacyReport: PrivacyTransparencyReport? = null,
    val showAddEditSheet: Boolean = false,
    val editingReminder: Reminder? = null,
    val showVoiceDialog: Boolean = false,
    val showPrivacyDialog: Boolean = false,
    val showSyncBackupDialog: Boolean = false,
    val snackbarMessage: String? = null
)

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ReminderRepository

    private val _activeTab = MutableStateFlow(ReminderFilterTab.ALL)
    val activeTab: StateFlow<ReminderFilterTab> = _activeTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ReminderCategory?>(null)
    val selectedCategory: StateFlow<ReminderCategory?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _smartSuggestions = MutableStateFlow<List<SmartSuggestion>>(emptyList())
    val smartSuggestions: StateFlow<List<SmartSuggestion>> = _smartSuggestions.asStateFlow()

    private val _isSuggestionsLoading = MutableStateFlow(false)
    val isSuggestionsLoading: StateFlow<Boolean> = _isSuggestionsLoading.asStateFlow()

    private val _aiDataSharingEnabled = MutableStateFlow(false)
    val aiDataSharingEnabled: StateFlow<Boolean> = _aiDataSharingEnabled.asStateFlow()

    private val _privacyReport = MutableStateFlow<PrivacyTransparencyReport?>(null)
    val privacyReport: StateFlow<PrivacyTransparencyReport?> = _privacyReport.asStateFlow()

    private val _showAddEditSheet = MutableStateFlow(false)
    val showAddEditSheet: StateFlow<Boolean> = _showAddEditSheet.asStateFlow()

    private val _editingReminder = MutableStateFlow<Reminder?>(null)
    val editingReminder: StateFlow<Reminder?> = _editingReminder.asStateFlow()

    private val _showVoiceDialog = MutableStateFlow(false)
    val showVoiceDialog: StateFlow<Boolean> = _showVoiceDialog.asStateFlow()

    private val _showPrivacyDialog = MutableStateFlow(false)
    val showPrivacyDialog: StateFlow<Boolean> = _showPrivacyDialog.asStateFlow()

    private val _showSyncBackupDialog = MutableStateFlow(false)
    val showSyncBackupDialog: StateFlow<Boolean> = _showSyncBackupDialog.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ReminderRepository(application, db.reminderDao())
        loadSmartSuggestions()
        refreshPrivacyReport()
    }

    private val filteredRemindersFlow = combine(
        repository.allReminders,
        _activeTab,
        _selectedCategory,
        _searchQuery
    ) { allReminders: List<Reminder>, tab: ReminderFilterTab, category: ReminderCategory?, query: String ->
        allReminders.filter { reminder ->
            val matchesTab = when (tab) {
                ReminderFilterTab.ALL -> !reminder.isCompleted
                ReminderFilterTab.TODAY -> {
                    if (reminder.isCompleted) false
                    else isTimestampToday(reminder.dueTimestamp)
                }
                ReminderFilterTab.NAGGING -> !reminder.isCompleted && reminder.isNagging
                ReminderFilterTab.RECURRING -> reminder.recurrenceType != RecurrenceType.NONE.name
                ReminderFilterTab.COMPLETED -> reminder.isCompleted
            }

            val matchesCategory = category == null || reminder.category == category.name
            val matchesQuery = query.isBlank() ||
                    reminder.title.contains(query, ignoreCase = true) ||
                    reminder.note.contains(query, ignoreCase = true)

            matchesTab && matchesCategory && matchesQuery
        }
    }

    private val dialogsStateFlow = combine(
        _showAddEditSheet,
        _editingReminder,
        _showVoiceDialog,
        _showPrivacyDialog,
        _showSyncBackupDialog
    ) { showAddEdit, editing, showVoice, showPrivacy, showSync ->
        DialogsState(showAddEdit, editing, showVoice, showPrivacy, showSync)
    }

    val uiState: StateFlow<ReminderUiState> = combine(
        filteredRemindersFlow,
        _activeTab,
        _selectedCategory,
        _smartSuggestions,
        _isSuggestionsLoading
    ) { reminders, tab, category, suggestions, isLoading ->
        ReminderUiState(
            reminders = reminders,
            activeFilterTab = tab,
            selectedCategory = category,
            searchQuery = _searchQuery.value,
            smartSuggestions = suggestions,
            isSuggestionsLoading = isLoading,
            aiDataSharingEnabled = _aiDataSharingEnabled.value,
            privacyReport = _privacyReport.value,
            showAddEditSheet = _showAddEditSheet.value,
            editingReminder = _editingReminder.value,
            showVoiceDialog = _showVoiceDialog.value,
            showPrivacyDialog = _showPrivacyDialog.value,
            showSyncBackupDialog = _showSyncBackupDialog.value,
            snackbarMessage = _snackbarMessage.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReminderUiState()
    )

    private data class DialogsState(
        val showAddEdit: Boolean,
        val editing: Reminder?,
        val showVoice: Boolean,
        val showPrivacy: Boolean,
        val showSync: Boolean
    )

    private fun isTimestampToday(timestamp: Long): Boolean {
        val today = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        return today.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

    fun setFilterTab(tab: ReminderFilterTab) {
        _activeTab.value = tab
    }

    fun setCategory(category: ReminderCategory?) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setAiDataSharingEnabled(enabled: Boolean) {
        _aiDataSharingEnabled.value = enabled
        loadSmartSuggestions()
        refreshPrivacyReport()
        showSnackbar(if (enabled) "AI smart suggestions enabled with Gemini" else "100% offline private heuristics mode active")
    }

    fun loadSmartSuggestions() {
        viewModelScope.launch {
            _isSuggestionsLoading.value = true
            try {
                val suggestions = repository.fetchSmartSuggestions(_aiDataSharingEnabled.value)
                _smartSuggestions.value = suggestions
            } catch (e: Exception) {
                _smartSuggestions.value = emptyList()
            } finally {
                _isSuggestionsLoading.value = false
            }
        }
    }

    fun acceptSuggestion(suggestion: SmartSuggestion) {
        viewModelScope.launch {
            val reminder = Reminder(
                title = suggestion.title,
                note = suggestion.note,
                category = suggestion.category.name,
                priority = suggestion.priority.name,
                dueTimestamp = suggestion.suggestedTimestamp,
                isNagging = suggestion.isNagging,
                naggingIntervalMinutes = suggestion.naggingIntervalMinutes,
                recurrenceType = suggestion.recurrenceType.name,
                recurrenceInterval = suggestion.recurrenceInterval,
                aiSuggested = true
            )
            repository.createReminder(reminder)
            _smartSuggestions.value = _smartSuggestions.value.filterNot { it.id == suggestion.id }
            showSnackbar("Added '${suggestion.title}' to reminders")
            refreshPrivacyReport()
        }
    }

    fun dismissSuggestion(suggestion: SmartSuggestion) {
        _smartSuggestions.value = _smartSuggestions.value.filterNot { it.id == suggestion.id }
    }

    fun openAddSheet() {
        _editingReminder.value = null
        _showAddEditSheet.value = true
    }

    fun openEditSheet(reminder: Reminder) {
        _editingReminder.value = reminder
        _showAddEditSheet.value = true
    }

    fun closeAddEditSheet() {
        _showAddEditSheet.value = false
        _editingReminder.value = null
    }

    fun saveReminder(
        id: Long = 0,
        title: String,
        note: String,
        category: ReminderCategory,
        priority: Priority,
        dueTimestamp: Long,
        isNagging: Boolean,
        naggingIntervalMinutes: Int,
        recurrenceType: RecurrenceType,
        recurrenceInterval: Int
    ) {
        viewModelScope.launch {
            if (id == 0L) {
                val newReminder = Reminder(
                    title = title.trim(),
                    note = note.trim(),
                    category = category.name,
                    priority = priority.name,
                    dueTimestamp = dueTimestamp,
                    isNagging = isNagging,
                    naggingIntervalMinutes = naggingIntervalMinutes,
                    recurrenceType = recurrenceType.name,
                    recurrenceInterval = recurrenceInterval
                )
                repository.createReminder(newReminder)
                showSnackbar("Reminder created")
            } else {
                val existing = _editingReminder.value ?: return@launch
                val updated = existing.copy(
                    title = title.trim(),
                    note = note.trim(),
                    category = category.name,
                    priority = priority.name,
                    dueTimestamp = dueTimestamp,
                    isNagging = isNagging,
                    naggingIntervalMinutes = naggingIntervalMinutes,
                    recurrenceType = recurrenceType.name,
                    recurrenceInterval = recurrenceInterval
                )
                repository.updateReminder(updated)
                showSnackbar("Reminder updated")
            }
            closeAddEditSheet()
            refreshPrivacyReport()
        }
    }

    fun toggleComplete(reminder: Reminder) {
        viewModelScope.launch {
            repository.toggleComplete(reminder)
            refreshPrivacyReport()
            showSnackbar(if (reminder.isCompleted) "Reopened reminder" else "Completed! ✓")
        }
    }

    fun snoozeReminder(reminder: Reminder, minutes: Int = 10) {
        viewModelScope.launch {
            val newDue = System.currentTimeMillis() + (minutes * 60 * 1000L)
            repository.updateReminder(reminder.copy(dueTimestamp = newDue))
            showSnackbar("Snoozed for $minutes min")
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            repository.deleteReminder(id)
            showSnackbar("Reminder deleted")
            refreshPrivacyReport()
        }
    }

    fun openVoiceDialog() {
        _showVoiceDialog.value = true
    }

    fun closeVoiceDialog() {
        _showVoiceDialog.value = false
    }

    fun handleVoiceCommand(rawPrompt: String, onParsed: ((Reminder) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val parsed = repository.parseVoiceOrTextPrompt(rawPrompt)
                val newReminder = Reminder(
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
                val id = repository.createReminder(newReminder)
                onParsed?.invoke(newReminder.copy(id = id))
                showSnackbar("Created from voice: '${parsed.title}'")
                refreshPrivacyReport()
            } catch (e: Exception) {
                showSnackbar("Could not parse voice command: ${e.message}")
            } finally {
                closeVoiceDialog()
            }
        }
    }

    fun openPrivacyDialog() {
        refreshPrivacyReport()
        _showPrivacyDialog.value = true
    }

    fun closePrivacyDialog() {
        _showPrivacyDialog.value = false
    }

    fun openSyncBackupDialog() {
        _showSyncBackupDialog.value = true
    }

    fun closeSyncBackupDialog() {
        _showSyncBackupDialog.value = false
    }

    suspend fun exportEncryptedBackup(passphrase: String): String {
        return repository.exportEncryptedBackup(passphrase)
    }

    suspend fun importEncryptedBackup(backupJson: String, passphrase: String): Result<Int> {
        val result = repository.importEncryptedBackup(backupJson, passphrase)
        if (result.isSuccess) {
            refreshPrivacyReport()
        }
        return result
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.deleteAllData()
            refreshPrivacyReport()
            showSnackbar("All local data cleared")
        }
    }

    fun refreshPrivacyReport() {
        viewModelScope.launch {
            _privacyReport.value = repository.getPrivacyReport(_aiDataSharingEnabled.value)
        }
    }

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
