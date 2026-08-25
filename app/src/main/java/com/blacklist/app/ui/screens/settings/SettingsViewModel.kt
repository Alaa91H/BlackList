package com.blacklist.app.ui.screens.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blacklist.app.domain.backup.EncryptedBackupService
import com.blacklist.app.domain.repository.BlackListRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: BlackListRepository) : ViewModel() {
    sealed interface BackupEvent {
        data class Exported(val summary: EncryptedBackupService.ExportResult) : BackupEvent
        data class Restored(val summary: EncryptedBackupService.RestoreResult) : BackupEvent
        data class Failed(val action: Action, val error: Throwable) : BackupEvent
    }

    enum class Action { EXPORT, RESTORE }

    val settings = repo.observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val blockedNumbers = repo.observeBlockedNumbers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _backupEvents = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 1)
    val backupEvents = _backupEvents.asSharedFlow()

    fun setBlockUnknown(value: Boolean) = viewModelScope.launch { repo.updateSettings { it.copy(blockUnknown = value) } }
    fun setBlockPrivate(value: Boolean) = viewModelScope.launch { repo.updateSettings { it.copy(blockPrivate = value) } }
    fun setBlockAllExcept(value: Boolean) = viewModelScope.launch { repo.updateSettings { it.copy(blockAllExceptWhitelist = value) } }
    fun setNotifications(value: Boolean) = viewModelScope.launch { repo.updateSettings { it.copy(showBlockedNotification = value) } }
    fun setTheme(mode: String) = viewModelScope.launch { repo.updateSettings { it.copy(themeMode = mode) } }
    fun setBlockedNumberNotification(id: Long, enabled: Boolean) = viewModelScope.launch { repo.setBlockedNotificationEnabled(id, enabled) }
    fun setAllBlockedNumberNotifications(enabled: Boolean) = viewModelScope.launch { repo.setAllBlockedNotificationsEnabled(enabled) }

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
