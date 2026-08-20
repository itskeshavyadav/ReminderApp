package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.ReminderCategory
import com.example.receiver.NotificationHelper
import com.example.ui.ReminderFilterTab
import com.example.ui.ReminderViewModel
import com.example.ui.components.AddEditReminderSheet
import com.example.ui.components.CrossPlatformSyncDialog
import com.example.ui.components.PrivacyTransparencyDialog
import com.example.ui.components.ReminderItemCard
import com.example.ui.components.SmartSuggestionsSection
import com.example.ui.components.VoiceCommandDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.voice.VoiceCommandParser
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: ReminderViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Notification Channels
        NotificationHelper.createNotificationChannels(this)

        // Request runtime notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Handle Bixby / Assistant / Voice Intents
        intent?.let { handleIntent(it) }

        setContent {
            MyApplicationTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent) {
        if (intent.getBooleanExtra("open_add_reminder", false) ||
            intent.action == "com.aistudio.reminders.ACTION_CREATE_REMINDER"
        ) {
            viewModel.openAddSheet()
            return
        }

        if (intent.getBooleanExtra("open_voice_command", false) ||
            intent.action == "com.aistudio.reminders.ACTION_VOICE_COMMAND"
        ) {
            viewModel.openVoiceDialog()
            return
        }

        val voiceReminder = VoiceCommandParser.parseAssistantIntent(intent)
        if (voiceReminder != null) {
            viewModel.saveReminder(
                title = voiceReminder.title,
                note = voiceReminder.note,
                category = voiceReminder.getCategoryEnum(),
                priority = voiceReminder.getPriorityEnum(),
                dueTimestamp = voiceReminder.dueTimestamp,
                isNagging = voiceReminder.isNagging,
                naggingIntervalMinutes = voiceReminder.naggingIntervalMinutes,
                recurrenceType = voiceReminder.getRecurrenceEnum(),
                recurrenceInterval = voiceReminder.recurrenceInterval
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: ReminderViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showSearchInput by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Clean Minimalism Bottom Navigation with rounded top (32.dp)
            Surface(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = Color(0xFFF3F3FA),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.setFilterTab(ReminderFilterTab.ALL) },
                            modifier = Modifier.testTag("nav_home_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = if (uiState.activeFilterTab == ReminderFilterTab.ALL) Color(0xFF001D36) else Color(0xFF44474E),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.setFilterTab(ReminderFilterTab.COMPLETED) },
                            modifier = Modifier.testTag("nav_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History",
                                tint = if (uiState.activeFilterTab == ReminderFilterTab.COMPLETED) Color(0xFF001D36) else Color(0xFF44474E),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.openPrivacyDialog() },
                            modifier = Modifier.testTag("nav_privacy_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color(0xFF44474E),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Action buttons: Quick Add & Voice Mic
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Add FAB
                        Surface(
                            onClick = { viewModel.openAddSheet() },
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFFD3E4FF),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("quick_add_fab")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Task",
                                    tint = Color(0xFF001C3B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Primary Voice Action Button
                        Surface(
                            onClick = { viewModel.openVoiceDialog() },
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF0061A4),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("primary_voice_fab")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Assistant",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Clean Minimalism Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF006874))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PRIVACY SECURED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp
                            ),
                            color = Color(0xFF74777F)
                        )
                    }
                    Text(
                        text = when (uiState.activeFilterTab) {
                            ReminderFilterTab.ALL -> "Reminders"
                            ReminderFilterTab.TODAY -> "Today"
                            ReminderFilterTab.NAGGING -> "Persistent Alerts"
                            ReminderFilterTab.RECURRING -> "Recurring Tasks"
                            ReminderFilterTab.COMPLETED -> "Completed"
                        },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color(0xFF1A1C1E)
                    )
                }

                // Top right actions (Encrypted & Sync badges)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = { viewModel.openPrivacyDialog() },
                        shape = CircleShape,
                        color = Color(0xFFE0E2EC),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("header_privacy_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Privacy Shield",
                                tint = Color(0xFF1A1C1E),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { viewModel.openSyncBackupDialog() },
                        shape = CircleShape,
                        color = Color(0xFFD3E4FF),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("header_sync_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Sync and Backup",
                                tint = Color(0xFF0061A4),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Smart Suggestion Banner
            SmartSuggestionsSection(
                suggestions = uiState.smartSuggestions,
                isLoading = uiState.isSuggestionsLoading,
                aiEnabled = uiState.aiDataSharingEnabled,
                onAccept = { viewModel.acceptSuggestion(it) },
                onDismiss = { viewModel.dismissSuggestion(it) },
                onRefresh = { viewModel.loadSmartSuggestions() }
            )

            // Tabs / Filters Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReminderFilterTab.values().forEach { tab ->
                    val isSelected = uiState.activeFilterTab == tab
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setFilterTab(tab) },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        shape = RoundedCornerShape(100.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0061A4),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFFF1F0F4),
                            labelColor = Color(0xFF44474E)
                        ),
                        border = null,
                        modifier = Modifier.testTag("tab_${tab.name}")
                    )
                }
            }

            // Category Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedCategory == null,
                    onClick = { viewModel.setCategory(null) },
                    label = { Text("All Categories", fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFD3E4FF),
                        selectedLabelColor = Color(0xFF001C3B)
                    ),
                    modifier = Modifier.testTag("category_all")
                )

                ReminderCategory.values().forEach { cat ->
                    val isSelected = uiState.selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setCategory(if (isSelected) null else cat) },
                        label = { Text("${cat.icon} ${cat.displayName}", fontSize = 12.sp) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFD3E4FF),
                            selectedLabelColor = Color(0xFF001C3B)
                        ),
                        modifier = Modifier.testTag("filter_cat_${cat.name}")
                    )
                }
            }

            // Schedule Header & Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "YOUR SCHEDULE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    ),
                    color = Color(0xFF74777F)
                )

                Text(
                    text = if (uiState.aiDataSharingEnabled) "Offline Private Storage" else "100% Offline Secured",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF0061A4)
                )
            }

            // List of Reminders
            if (uiState.reminders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF1F0F4),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF74777F),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Text(
                            text = "No reminders here",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1A1C1E)
                        )
                        Text(
                            text = "Tap '+' to add a reminder or use the mic for natural voice parsing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF74777F),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(uiState.reminders, key = { it.id }) { reminder ->
                        ReminderItemCard(
                            reminder = reminder,
                            onToggleComplete = { viewModel.toggleComplete(reminder) },
                            onEdit = { viewModel.openEditSheet(reminder) },
                            onDelete = { viewModel.deleteReminder(reminder.id) },
                            onSnooze = { minutes -> viewModel.snoozeReminder(reminder, minutes) }
                        )
                    }
                }
            }
        }
    }

    // Modal Sheets & Dialogs
    if (uiState.showAddEditSheet) {
        AddEditReminderSheet(
            reminder = uiState.editingReminder,
            onDismiss = { viewModel.closeAddEditSheet() },
            onSave = { id, title, note, category, priority, dueTimestamp, isNagging, naggingInterval, recurrenceType, recurrenceInterval ->
                viewModel.saveReminder(
                    id = id,
                    title = title,
                    note = note,
                    category = category,
                    priority = priority,
                    dueTimestamp = dueTimestamp,
                    isNagging = isNagging,
                    naggingIntervalMinutes = naggingInterval,
                    recurrenceType = recurrenceType,
                    recurrenceInterval = recurrenceInterval
                )
            }
        )
    }

    if (uiState.showVoiceDialog) {
        VoiceCommandDialog(
            onDismiss = { viewModel.closeVoiceDialog() },
            onSubmitCommand = { prompt ->
                viewModel.handleVoiceCommand(prompt)
            }
        )
    }

    if (uiState.showPrivacyDialog) {
        PrivacyTransparencyDialog(
            report = uiState.privacyReport,
            aiDataSharingEnabled = uiState.aiDataSharingEnabled,
            onToggleAiDataSharing = { viewModel.setAiDataSharingEnabled(it) },
            onClearAllData = { viewModel.clearAllData() },
            onDismiss = { viewModel.closePrivacyDialog() }
        )
    }

    if (uiState.showSyncBackupDialog) {
        CrossPlatformSyncDialog(
            onExport = { passphrase -> viewModel.exportEncryptedBackup(passphrase) },
            onImport = { json, passphrase -> viewModel.importEncryptedBackup(json, passphrase) },
            onDismiss = { viewModel.closeSyncBackupDialog() }
        )
    }
}
