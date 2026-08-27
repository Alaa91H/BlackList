package com.blacklist.app.ui.screens.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.domain.backup.EncryptedBackupService
import com.blacklist.app.domain.model.ProtectionProfilePreset
import com.blacklist.app.domain.model.ProtectionProfiles
import com.blacklist.app.domain.importexport.CsvImportPreview
import com.blacklist.app.domain.importexport.CsvListRow
import com.blacklist.app.domain.importexport.CsvListTarget
import com.blacklist.app.domain.importexport.CsvListTransferService
import com.blacklist.app.domain.importexport.OfflineReputationImportPreview
import com.blacklist.app.domain.importexport.OfflineReputationListTransferService
import com.blacklist.app.domain.repository.BlackListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val repo: BlackListRepository) : ViewModel() {
    sealed interface BackupEvent {
        data class Exported(val summary: EncryptedBackupService.ExportResult) : BackupEvent
        data class Restored(val summary: EncryptedBackupService.RestoreResult) : BackupEvent
        data class Failed(val action: Action, val error: Throwable) : BackupEvent
    }

    enum class Action { EXPORT, RESTORE }

    sealed interface CsvEvent {
        data class Exported(val target: CsvListTarget, val count: Int) : CsvEvent
        data class Imported(val target: CsvListTarget, val added: Int, val skipped: Int) : CsvEvent
        data class Failed(val error: Throwable) : CsvEvent
    }

    data class PendingCsvImport(val target: CsvListTarget, val preview: CsvImportPreview)

    sealed interface OfflineReputationEvent {
        data class Imported(val sourceName: String, val entries: Int) : OfflineReputationEvent
        data class Failed(val error: Throwable) : OfflineReputationEvent
    }

    data class PendingOfflineReputationImport(val preview: OfflineReputationImportPreview)

    val settings = repo.observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val blockedNumbers = repo.observeBlockedNumbers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val offlineReputationSources = repo.observeOfflineReputationSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _backupEvents = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1)
    val backupEvents = _backupEvents.asSharedFlow()
    private val csvTransfer = CsvListTransferService()
    private val _pendingCsvImport = MutableStateFlow<PendingCsvImport?>(null)
    val pendingCsvImport = _pendingCsvImport.asStateFlow()
    private val _csvEvents = MutableSharedFlow<CsvEvent>(extraBufferCapacity = 1)
    val csvEvents = _csvEvents.asSharedFlow()
    private val offlineReputationTransfer = OfflineReputationListTransferService()
    private val _pendingOfflineReputationImport = MutableStateFlow<PendingOfflineReputationImport?>(null)
    val pendingOfflineReputationImport = _pendingOfflineReputationImport.asStateFlow()
    private val _offlineReputationEvents = MutableSharedFlow<OfflineReputationEvent>(extraBufferCapacity = 1)
    val offlineReputationEvents = _offlineReputationEvents.asSharedFlow()

    fun setBlockUnknown(value: Boolean) = viewModelScope.launch {
        repo.updateSettings { it.copy(blockUnknown = value, activeProfileId = ProtectionProfiles.CUSTOM) }
    }

    fun setBlockPrivate(value: Boolean) = viewModelScope.launch {
        repo.updateSettings { it.copy(blockPrivate = value, activeProfileId = ProtectionProfiles.CUSTOM) }
    }

    fun setSilenceUnknown(value: Boolean) = viewModelScope.launch {
        repo.updateSettings { it.copy(silenceUnknown = value, activeProfileId = ProtectionProfiles.CUSTOM) }
    }

    fun setSilencePrivate(value: Boolean) = viewModelScope.launch {
        repo.updateSettings { it.copy(silencePrivate = value, activeProfileId = ProtectionProfiles.CUSTOM) }
    }

    fun setBlockAllExcept(value: Boolean) = viewModelScope.launch {
        repo.updateSettings { it.copy(blockAllExceptWhitelist = value, activeProfileId = ProtectionProfiles.CUSTOM) }
    }

    fun applyProtectionProfile(profile: ProtectionProfilePreset) = viewModelScope.launch {
        repo.updateSettings { profile.applyTo(it) }
    }
    fun setNotifications(value: Boolean) = viewModelScope.launch { repo.updateSettings { it.copy(showBlockedNotification = value) } }
    fun setPrivateBlockedHistory(value: Boolean) = viewModelScope.launch {
        repo.updateSettings { it.copy(hideBlockedCallsFromSystemLog = value) }
    }
    fun setOutboundCallbackGrace(value: Boolean) = viewModelScope.launch {
        repo.updateSettings { it.copy(allowOutboundCallbackGrace = value, activeProfileId = ProtectionProfiles.CUSTOM) }
    }
    fun setTheme(mode: String) = viewModelScope.launch { repo.updateSettings { it.copy(themeMode = mode) } }
    fun setBlockedNumberNotification(id: Long, enabled: Boolean) = viewModelScope.launch { repo.setBlockedNotificationEnabled(id, enabled) }
    fun setAllBlockedNumberNotifications(enabled: Boolean) = viewModelScope.launch { repo.setAllBlockedNotificationsEnabled(enabled) }

    fun previewCsvImport(contentResolver: ContentResolver, uri: Uri, target: CsvListTarget) {
        viewModelScope.launch {
            runCatching {
                contentResolver.openInputStream(uri)?.use(csvTransfer::preview)
                    ?: error("Unable to read the selected CSV file.")
            }.onSuccess { preview ->
                _pendingCsvImport.value = PendingCsvImport(target, preview)
            }.onFailure { error ->
                _csvEvents.emit(CsvEvent.Failed(error))
            }
        }
    }

    fun dismissCsvImportPreview() {
        _pendingCsvImport.value = null
    }

    fun confirmCsvImport() {
        val pending = _pendingCsvImport.value ?: return
        _pendingCsvImport.value = null
        viewModelScope.launch {
            var added = 0
            var skipped = pending.preview.duplicateRows + pending.preview.invalidRows
            pending.preview.rows.forEach { row ->
                val result = when (pending.target) {
                    CsvListTarget.BLACKLIST -> repo.addBlockedNumber(row.number, row.displayName)
                    CsvListTarget.WHITELIST -> repo.addWhitelisted(row.number, row.displayName)
                }
                if (result.isSuccess) added++ else skipped++
            }
            _csvEvents.emit(CsvEvent.Imported(pending.target, added, skipped))
        }
    }

    fun previewOfflineReputationImport(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use(offlineReputationTransfer::preview)
                        ?: error("Unable to read the selected reputation list.")
                }
            }.onSuccess { preview ->
                _pendingOfflineReputationImport.value = PendingOfflineReputationImport(preview)
            }.onFailure { error ->
                _offlineReputationEvents.emit(OfflineReputationEvent.Failed(error))
            }
        }
    }

    fun dismissOfflineReputationImport() {
        _pendingOfflineReputationImport.value = null
    }

    fun confirmOfflineReputationImport() {
        val pending = _pendingOfflineReputationImport.value ?: return
        _pendingOfflineReputationImport.value = null
        viewModelScope.launch {
            repo.importOfflineReputationList(pending.preview)
                .onSuccess { sourceId ->
                    _offlineReputationEvents.emit(
                        OfflineReputationEvent.Imported(pending.preview.sourceName, pending.preview.rows.size)
                    )
                }
                .onFailure { error -> _offlineReputationEvents.emit(OfflineReputationEvent.Failed(error)) }
        }
    }

    fun deleteOfflineReputationSource(id: Long) = viewModelScope.launch {
        repo.deleteOfflineReputationSource(id)
    }

    fun exportCsv(contentResolver: ContentResolver, uri: Uri, target: CsvListTarget) {
        viewModelScope.launch {
            runCatching {
                val rows = when (target) {
                    CsvListTarget.BLACKLIST -> repo.observeBlockedNumbers().first().map {
                        CsvListRow(it.rawNumber, it.displayName)
                    }
                    CsvListTarget.WHITELIST -> repo.observeWhitelisted().first().map {
                        CsvListRow(it.rawNumber, it.displayName)
                    }
                }
                contentResolver.openOutputStream(uri)?.use { csvTransfer.export(rows, it) }
                    ?: error("Unable to write the selected CSV file.")
                rows.size
            }.onSuccess { count ->
                _csvEvents.emit(CsvEvent.Exported(target, count))
            }.onFailure { error ->
                _csvEvents.emit(CsvEvent.Failed(error))
            }
        }
    }

    fun exportEncryptedBackup(contentResolver: ContentResolver, uri: Uri, passphrase: CharArray) {
        performBackup(Action.EXPORT, passphrase) {
            contentResolver.openOutputStream(uri)?.use { output -> repo.exportEncryptedBackup(output, passphrase) }
                ?: Result.failure(IllegalStateException("Unable to write the selected backup file."))
        }
    }

    fun restoreEncryptedBackup(contentResolver: ContentResolver, uri: Uri, passphrase: CharArray) {
        performBackup(Action.RESTORE, passphrase) {
            contentResolver.openInputStream(uri)?.use { input -> repo.restoreEncryptedBackup(input, passphrase) }
                ?: Result.failure(IllegalStateException("Unable to read the selected backup file."))
        }
    }

    private fun <T> performBackup(
        action: Action,
        passphrase: CharArray,
        execute: suspend () -> Result<T>
    ) {
        viewModelScope.launch {
            try {
                execute().fold(
                    onSuccess = { summary ->
                        when (summary) {
                            is EncryptedBackupService.ExportResult -> _backupEvents.emit(BackupEvent.Exported(summary))
                            is EncryptedBackupService.RestoreResult -> _backupEvents.emit(BackupEvent.Restored(summary))
                            else -> _backupEvents.emit(BackupEvent.Failed(action, IllegalStateException("Unexpected backup result.")))
                        }
                    },
                    onFailure = { error -> _backupEvents.emit(BackupEvent.Failed(action, error)) }
                )
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }
}
