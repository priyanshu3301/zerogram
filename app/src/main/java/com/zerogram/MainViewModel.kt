package com.zerogram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerogram.domain.repository.CredentialsManager
import com.zerogram.domain.repository.ITelegramRepository
import com.zerogram.domain.repository.IVaultManager
import com.zerogram.domain.repository.TelegramAuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val telegramRepository: ITelegramRepository,
    private val credentialsManager: CredentialsManager,
    private val vaultManager: IVaultManager
    // NOTE: StatsRepository is intentionally NOT injected here anymore.
    // It used to be injected purely "to eagerly load stats", which made Hilt
    // construct it (and start its Room-querying stateIn() flows) the instant
    // the app process started - BEFORE the user ever picked/unlocked a vault.
    // That auto-created an empty zerogram_vault.db on disk immediately, which
    // made unlockVault()'s `dbFile.exists()` check always true, forcing a
    // full process kill+restart on every unlock and racing with the vault
    // file swap. StatsRepository is still a @Singleton and is still injected
    // by HomeViewModel, which is only ever created after the vault is
    // confirmed unlocked - so stats still load correctly, just no longer
    // before there is any vault to have stats about.
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        telegramRepository.observeAuthState()
            .onEach { state ->
                when (state) {
                    TelegramAuthState.READY -> {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                if (vaultManager.restoreSavedVault()) {
                                    _startDestination.value = "Home"
                                } else {
                                    _startDestination.value = "VaultSelection"
                                }
                            } catch (e: Exception) {
                                _startDestination.value = "VaultSelection"
                            }
                        }
                    }
                    TelegramAuthState.WAIT_TDLIB_PARAMETERS -> {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val apiId = credentialsManager.getApiId()
                                val apiHash = credentialsManager.getApiHash()
                                val phoneNumber = credentialsManager.getPhoneNumber()

                                if (apiId != null && apiHash != null && phoneNumber != null) {
                                    // Automatically initialize if credentials exist
                                    telegramRepository.initializeTdlib(apiId, apiHash)
                                        .onSuccess {
                                            telegramRepository.sendPhoneNumber(phoneNumber)
                                        }
                                } else {
                                    // No credentials, must login
                                    _startDestination.value = "Login"
                                }
                            } catch (e: Exception) {
                                _startDestination.value = "Login"
                            }
                        }
                    }
                    TelegramAuthState.WAIT_PHONE_NUMBER,
                    TelegramAuthState.WAIT_CODE,
                    TelegramAuthState.WAIT_PASSWORD -> {
                        _startDestination.value = "Login"
                    }
                    else -> {
                        // Keep loading for other states (LOADING, CLOSED, etc)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun setVaultUnlocked() {
        _startDestination.value = "Home"
    }
}
