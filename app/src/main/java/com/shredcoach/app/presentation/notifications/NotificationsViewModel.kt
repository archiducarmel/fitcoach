package com.shredcoach.app.presentation.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.AppNotificationEntity
import com.shredcoach.app.data.repository.AppNotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsState(
    val notifications: List<AppNotificationEntity> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: AppNotificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAll().collect { list ->
                _state.update { it.copy(notifications = list, isLoading = false) }
            }
        }
        viewModelScope.launch {
            repository.getUnreadCount().collect { count ->
                _state.update { it.copy(unreadCount = count) }
            }
        }
        // Purge les vieilles notifications en arrière-plan
        viewModelScope.launch { repository.purgeOld() }
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch { repository.markAsRead(id) }
    }

    fun markAllAsRead() {
        viewModelScope.launch { repository.markAllAsRead() }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    fun deleteAll() {
        viewModelScope.launch { repository.deleteAll() }
    }
}
