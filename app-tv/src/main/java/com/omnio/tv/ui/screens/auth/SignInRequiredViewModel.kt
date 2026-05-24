package com.omnio.tv.ui.screens.auth

import androidx.lifecycle.ViewModel
import com.omnio.tv.domain.auth.AuthManager
import com.omnio.tv.domain.model.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SignInRequiredViewModel @Inject constructor(
    authManager: AuthManager
) : ViewModel() {
    val authState: StateFlow<AuthState> = authManager.authState
}
