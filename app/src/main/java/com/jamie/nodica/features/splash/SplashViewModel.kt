package com.jamie.nodica.features.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SplashViewModel(private val supabase: SupabaseClient) : ViewModel() {

    private val _isLoadingDone = MutableStateFlow(false)
    val isLoadingDone: StateFlow<Boolean> = _isLoadingDone

    init {
        viewModelScope.launch {
            delay(1500) // Simulate loading
            val user = supabase.auth.currentUserOrNull()
            // Navigate based on whether a user is logged in.
            _isLoadingDone.value = true
        }
    }
}
