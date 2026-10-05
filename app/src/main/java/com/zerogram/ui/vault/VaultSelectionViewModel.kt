package com.zerogram.feature.vault

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerogram.domain.model.AppResult
import com.zerogram.domain.repository.ITelegramRepository
import com.zerogram.domain.repository.IVaultManager
import com.zerogram.domain.repository.TelegramChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class VaultSelectionState {
    object Loading : VaultSelectionState()
    data class Success(val channels: List<TelegramChannel>) : VaultSelectionState()
    data class Error(val message: String) : VaultSelectionState()
}

@HiltViewModel
class VaultSelectionViewModel @Inject constructor(
    private val context: Application,
    private val telegramRepository: ITelegramRepository,
    private val vaultManager: IVaultManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<VaultSelectionState>(VaultSelectionState.Loading)
    val uiState: StateFlow<VaultSelectionState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<VaultActionState>(VaultActionState.Idle)
    val actionState: StateFlow<VaultActionState> = _actionState.asStateFlow()

    init {
        loadChannels()
    }

    fun loadChannels() {
        viewModelScope.launch {
            _uiState.value = VaultSelectionState.Loading
            when (val result = telegramRepository.getOwnedChannels()) {
                is AppResult.Success -> {
                    _uiState.value = VaultSelectionState.Success(result.data)
                }
                is AppResult.Failure -> {
                    _uiState.value = VaultSelectionState.Error(result.error.message)
                }
            }
        }
    }

    fun selectChannel(channel: TelegramChannel) {
        viewModelScope.launch {
            _actionState.value = VaultActionState.Checking
            
            // If this vault is already the active vault, just unlock it immediately!
            val activeChatId = vaultManager.getActiveVaultChatId()
            if (activeChatId == channel.chatId) {
                _actionState.value = VaultActionState.VaultUnlocked
                return@launch
            }
            
            when (val result = vaultManager.checkDatabaseExists(channel.chatId)) {
                is AppResult.Success -> {
                    val exists = result.data
                    if (exists) {
                        _actionState.value = VaultActionState.NavigateToUnlock(channel.chatId)
                    } else {
                        // Channel exists but no database.encrypt found -> initialize here
                        initializeOnExisting(channel.chatId, channel.title)
                    }
                }
                is AppResult.Failure -> {
                    _actionState.value = VaultActionState.Error(result.error.message)
                }
            }
        }
    }

    private fun initializeOnExisting(chatId: Long, vaultName: String) {
        viewModelScope.launch {
            _actionState.value = VaultActionState.Creating
            when (val result = vaultManager.initVaultOnExistingChannel(chatId, vaultName)) {
                is AppResult.Success -> {
                    _actionState.value = VaultActionState.VaultCreated(result.data)
                }
                is AppResult.Failure -> {
                    _actionState.value = VaultActionState.Error(result.error.message)
                }
            }
        }
    }

    fun createNewVault(name: String) {
        viewModelScope.launch {
            _actionState.value = VaultActionState.Creating
            when (val result = vaultManager.createNewVault(0L, name)) { // 0L is unused for new channel creation
                is AppResult.Success -> {
                    _actionState.value = VaultActionState.VaultCreated(result.data)
                }
                is AppResult.Failure -> {
                    _actionState.value = VaultActionState.Error(result.error.message)
                }
            }
        }
    }

    fun unlockVault(chatId: Long, keyBase64: String) {
        viewModelScope.launch {
            _actionState.value = VaultActionState.Unlocking
            when (val result = vaultManager.unlockVault(chatId, keyBase64)) {
                is AppResult.Success -> {
                    _actionState.value = VaultActionState.VaultUnlocked
                    val intent = android.content.Intent().apply { setClassName(context.packageName, "com.zerogram.service.TransferService") }
            context.startForegroundService(intent)
                }
                is AppResult.Failure -> {
                    _actionState.value = VaultActionState.UnlockFailed(result.error.message)
                }
            }
        }
    }

    fun resetActionState() {
        _actionState.value = VaultActionState.Idle
    }
}

sealed class VaultActionState {
    object Idle : VaultActionState()
    object Checking : VaultActionState()
    object Creating : VaultActionState()
    object Unlocking : VaultActionState()
    
    data class NavigateToUnlock(val chatId: Long) : VaultActionState()
    data class VaultCreated(val newKeyBase64: String) : VaultActionState()
    object VaultUnlocked : VaultActionState()
    data class Error(val message: String) : VaultActionState()
    data class UnlockFailed(val message: String) : VaultActionState()
}
