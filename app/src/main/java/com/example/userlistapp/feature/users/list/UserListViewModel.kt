package com.example.userlistapp.feature.users.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.core.common.DefaultDispatcher
import com.example.userlistapp.core.common.UiText
import com.example.userlistapp.core.common.toUiText
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.domain.model.UserSort
import com.example.userlistapp.domain.usecase.FilterAndSortUsersUseCase
import com.example.userlistapp.domain.usecase.ObserveUsersUseCase
import com.example.userlistapp.domain.usecase.RefreshUsersUseCase
import com.example.userlistapp.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class UserListUiState(
    val users: List<User> = emptyList(),
    val hasCachedUsers: Boolean = false,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val initialError: UiText? = null,
    val query: String = "",
    val sort: UserSort = UserSort.NAME_ASCENDING,
    val favoritesOnly: Boolean = false,
)

@HiltViewModel
class UserListViewModel @Inject constructor(
    observeUsers: ObserveUsersUseCase,
    private val refreshUsers: RefreshUsersUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val filterAndSortUsers: FilterAndSortUsersUseCase,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(UserSort.NAME_ASCENDING)
    private val favoritesOnly = MutableStateFlow(false)
    private val refreshState = MutableStateFlow(RefreshState(running = true))
    private val refreshInFlight = AtomicBoolean(false)
    private val _events = MutableSharedFlow<UiText>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()
    private val cachedUsers: StateFlow<List<User>?> = observeUsers()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<UserListUiState> = combine(
        cachedUsers, query, sort, favoritesOnly, refreshState,
    ) { cachedValue, queryValue, sortValue, favoritesValue, refreshValue ->
        val cached = cachedValue.orEmpty()
        val visible = filterAndSortUsers(cached, queryValue, sortValue, favoritesValue)
        UserListUiState(
            users = visible,
            hasCachedUsers = cached.isNotEmpty(),
            isInitialLoading = cached.isEmpty() && refreshValue.running,
            isRefreshing = cached.isNotEmpty() && refreshValue.running,
            initialError = if (cached.isEmpty()) refreshValue.error else null,
            query = queryValue,
            sort = sortValue,
            favoritesOnly = favoritesValue,
        )
    }
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserListUiState())

    init {
        refresh()
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(value: UserSort) {
        sort.value = value
    }

    fun setFavoritesOnly(value: Boolean) {
        favoritesOnly.value = value
    }

    fun toggleFavorite(user: User) {
        viewModelScope.launch {
            when (val result = toggleFavorite(user.id, user.isFavorite)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> _events.emit(result.error.toUiText())
            }
        }
    }

    fun refresh() {
        if (!refreshInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                refreshState.value = RefreshState(running = true)
                cachedUsers.filterNotNull().first()
                when (val result = refreshUsers()) {
                    is AppResult.Success -> refreshState.value = RefreshState()
                    is AppResult.Failure -> {
                        val message = result.error.toUiText()
                        val hadCache = cachedUsers.value.orEmpty().isNotEmpty()
                        refreshState.value = RefreshState(error = message)
                        if (hadCache) _events.tryEmit(message)
                    }
                }
            } finally {
                refreshState.value = refreshState.value.copy(running = false)
                refreshInFlight.set(false)
            }
        }
    }

    private data class RefreshState(val running: Boolean = false, val error: UiText? = null)
}
