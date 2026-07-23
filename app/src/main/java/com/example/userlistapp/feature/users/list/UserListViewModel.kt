package com.example.userlistapp.feature.users.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.userlistapp.core.common.AppResult
import com.example.userlistapp.core.common.DefaultDispatcher
import com.example.userlistapp.core.common.UiText
import com.example.userlistapp.core.common.toUiText
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.domain.model.UserSort
import com.example.userlistapp.domain.usecase.ObserveUsersUseCase
import com.example.userlistapp.domain.usecase.RefreshUsersUseCase
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
import kotlinx.coroutines.flow.map
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
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(UserSort.NAME_ASCENDING)
    private val favoritesOnly = MutableStateFlow(false)
    private val refreshState = MutableStateFlow(RefreshState(running = true))
    private val refreshInFlight = AtomicBoolean(false)
    private val _events = MutableSharedFlow<UiText>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()
    private val cachedUsers = observeUsers()
        .map<List<User>, List<User>?> { it }
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

    init { refresh() }

    fun setQuery(value: String) { query.value = value }
    fun setSort(value: UserSort) { sort.value = value }
    fun setFavoritesOnly(value: Boolean) { favoritesOnly.value = value }

    fun refresh() {
        if (!refreshInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                refreshState.value = RefreshState(running = true)
                // Read Room before refreshing so a fast network failure cannot race the
                // first cached emission and suppress the required Snackbar.
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

internal fun filterAndSortUsers(
    users: List<User>,
    query: String,
    sort: UserSort,
    favoritesOnly: Boolean,
): List<User> {
    val needle = query.trim()
    val nameComparator = compareBy<User> { it.fullName.lowercase() }
    return users.asSequence()
        .filter { !favoritesOnly || it.isFavorite }
        .filter { user ->
            needle.isBlank() || listOf(user.fullName, user.email, user.companyName)
                .any { value -> value.contains(needle, ignoreCase = true) }
        }
        .sortedWith(if (sort == UserSort.NAME_ASCENDING) nameComparator else nameComparator.reversed())
        .toList()
}
