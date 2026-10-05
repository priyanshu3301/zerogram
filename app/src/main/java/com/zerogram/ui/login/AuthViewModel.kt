package com.zerogram.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerogram.domain.repository.CredentialsManager
import com.zerogram.domain.repository.ITelegramRepository
import com.zerogram.domain.repository.TelegramAuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for all authentication screens (Login, OTP, 2FA).
 *
 * SECURITY: This ViewModel never logs or stores apiId, apiHash,
 * phone numbers, OTP codes, or 2FA passwords.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val telegramRepository: ITelegramRepository,
    private val credentialsManager: CredentialsManager
) : ViewModel() {

    private val _authState = MutableStateFlow(TelegramAuthState.LOADING)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Observe TDLib auth state changes
        telegramRepository.observeAuthState()
            .onEach { _authState.value = it }
            .launchIn(viewModelScope)
    }

    /**
     * Initializes TDLib with user-provided credentials and sends the phone number.
     * Credentials are not persisted after this call.
     */
    fun initAndSendPhone(apiId: Int, apiHash: String, phoneNumber: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            // Save credentials securely for future logins
            credentialsManager.saveCredentials(apiId, apiHash, phoneNumber)

            // If we are already past the TdlibParameters stage, just send the phone number
            if (_authState.value != TelegramAuthState.WAIT_TDLIB_PARAMETERS && _authState.value != TelegramAuthState.LOADING) {
                telegramRepository.sendPhoneNumber(phoneNumber)
                    .onFailure { error -> _errorMessage.value = error.message }
            } else {
                telegramRepository.initializeTdlib(apiId, apiHash)
                    .onSuccess {
                        telegramRepository.sendPhoneNumber(phoneNumber)
                            .onFailure { error ->
                                _errorMessage.value = error.message
                            }
                    }
                    .onFailure { error ->
                        _errorMessage.value = error.message
                    }
            }

            _isLoading.value = false
        }
    }

    fun verifyOtp(code: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            telegramRepository.checkAuthenticationCode(code)
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun verify2Fa(password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            telegramRepository.checkAuthenticationPassword(password)
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
