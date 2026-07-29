package com.example.userlistapp.feature.users.list

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.userlistapp.R
import com.example.userlistapp.core.common.EMPTY
import com.example.userlistapp.core.quality.TrackJankStates
import com.example.userlistapp.core.ui.UiAnimationLabels
import com.example.userlistapp.core.ui.UiTestTags
import com.example.userlistapp.core.ui.UiTextSnackbarEffect
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.domain.model.UserSort
import com.example.userlistapp.feature.users.components.UserAvatar
import com.example.userlistapp.ui.theme.UserListTheme
import com.example.userlistapp.ui.theme.extendedColors

@Composable
fun UserListRoute(
    onUser: (Int) -> Unit,
    onSettings: () -> Unit,
    viewModel: UserListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val contentState = state.toContentState(
        initialErrorMessage = state.initialError?.resolve(context),
    )
    TrackJankStates(
        mapOf(
            SCREEN_STATE_KEY to USERS_SCREEN_VALUE,
            PHASE_STATE_KEY to state.qualityPhase,
            INTERACTION_STATE_KEY to state.qualityInteraction,
            VISIBLE_USERS_STATE_KEY to state.users.size.qualityBucket,
        ),
    )
    UiTextSnackbarEffect(viewModel.events, snackbar)
    UserListScreen(
        state,
        contentState,
        viewModel::setQuery,
        viewModel::setSort,
        viewModel::setFavoritesOnly,
        viewModel::refresh,
        onUser,
        viewModel::toggleFavorite,
        onSettings,
        snackbar,
    )
}

sealed interface UserListContentState {
    data object InitialLoading : UserListContentState
    data class InitialError(val message: String) : UserListContentState
    data object Empty : UserListContentState
    data object Loaded : UserListContentState
}

fun UserListUiState.toContentState(initialErrorMessage: String?): UserListContentState = when {
    isInitialLoading -> UserListContentState.InitialLoading
    initialError != null -> UserListContentState.InitialError(
        requireNotNull(initialErrorMessage) {
            "An initial error must be resolved before rendering UserListScreen"
        },
    )
    users.isEmpty() -> UserListContentState.Empty
    else -> UserListContentState.Loaded
}

private val UserListUiState.qualityPhase: String
    get() = when {
        isInitialLoading -> INITIAL_LOADING_PHASE
        initialError != null -> ERROR_PHASE
        isRefreshing -> REFRESHING_PHASE
        users.isEmpty() -> EMPTY_PHASE
        else -> CONTENT_PHASE
    }

private val UserListUiState.qualityInteraction: String
    get() = when {
        query.isNotBlank() -> SEARCH_INTERACTION
        favoritesOnly -> FAVORITES_FILTER_INTERACTION
        else -> BROWSING_INTERACTION
    }

private val Int.qualityBucket: String
    get() = when (this) {
        0 -> ZERO_USERS_BUCKET
        in 1..10 -> ONE_TO_TEN_USERS_BUCKET
        in 11..50 -> ELEVEN_TO_FIFTY_USERS_BUCKET
        else -> FIFTY_ONE_PLUS_USERS_BUCKET
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    state: UserListUiState,
    contentState: UserListContentState,
    onQuery: (String) -> Unit,
    onSort: (UserSort) -> Unit,
    onFavoritesOnly: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onUser: (Int) -> Unit,
    onFavorite: (User) -> Unit,
    onSettings: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = state.query,
                selection = TextRange(state.query.length),
            ),
        )
    }
    val pullToRefreshState = rememberPullToRefreshState()
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun closeSearch() {
        searchValue = TextFieldValue(String.EMPTY)
        onQuery(String.EMPTY)
        searchActive = false
        keyboard?.hide()
    }
    BackHandler(enabled = searchActive, onBack = ::closeSearch)
    LaunchedEffect(searchActive) {
        if (searchActive) {
            searchFocusRequester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(Unit) {
        if (searchValue.text != state.query) onQuery(searchValue.text)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.shadow(4.dp),
                expandedHeight = 56.dp,
                navigationIcon = {
                    if (searchActive) {
                        IconButton(onClick = ::closeSearch) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                title = {
                    AnimatedContent(
                        targetState = searchActive,
                        label = UiAnimationLabels.SEARCH_TITLE,
                    ) { active ->
                        if (active) {
                            val searchLabel = stringResource(R.string.search_users)
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                if (searchValue.text.isEmpty()) {
                                    Text(
                                        searchLabel,
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                BasicTextField(
                                    value = searchValue,
                                    onValueChange = { value ->
                                        searchValue = value
                                        onQuery(value.text)
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                    }),
                                    textStyle = MaterialTheme.typography.titleMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester)
                                        .testTag(UiTestTags.SEARCH)
                                        .semantics { contentDescription = searchLabel },
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.users_title),
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                    }
                },
                actions = {
                    if (searchActive && searchValue.text.isNotEmpty()) {
                        IconButton(onClick = {
                            searchValue = TextFieldValue(String.EMPTY)
                            onQuery(String.EMPTY)
                        }) {
                            Icon(Icons.Default.Close, stringResource(R.string.clear_search))
                        }
                    } else if (!searchActive) {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(
                                Icons.Rounded.Search,
                                stringResource(R.string.search_users),
                                modifier = Modifier.offset(y = 1.dp),
                            )
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Default.Settings,
                            stringResource(R.string.settings)
                        )
                    }
                })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        AnimatedContent(
            targetState = contentState,
            contentKey = { it::class },
            transitionSpec = {
                fadeIn(tween(durationMillis = 220)) togetherWith
                    fadeOut(tween(durationMillis = 120))
            },
            modifier = Modifier.fillMaxSize(),
            label = UiAnimationLabels.USER_LIST_CONTENT,
        ) { targetState ->
            when (targetState) {
                UserListContentState.InitialLoading -> {
                    val loadingDescription = stringResource(R.string.loading)
                    Centered(padding) {
                        CircularProgressIndicator(
                            Modifier
                                .testTag(UiTestTags.USER_LIST_LOADING)
                                .semantics {
                                    contentDescription = loadingDescription
                                },
                        )
                    }
                }

                is UserListContentState.InitialError -> Centered(padding) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .testTag(UiTestTags.USER_LIST_ERROR)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(
                            targetState.message,
                            modifier = Modifier.padding(24.dp),
                        )
                        Button(onClick = onRefresh) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                UserListContentState.Empty,
                UserListContentState.Loaded,
                -> RefreshableUserContent(
                    state = state,
                    showEmptyState = targetState == UserListContentState.Empty,
                    padding = padding,
                    pullToRefreshState = pullToRefreshState,
                    onSort = onSort,
                    onFavoritesOnly = onFavoritesOnly,
                    onRefresh = onRefresh,
                    onUser = onUser,
                    onFavorite = onFavorite,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableUserContent(
    state: UserListUiState,
    showEmptyState: Boolean,
    padding: PaddingValues,
    pullToRefreshState: PullToRefreshState,
    onSort: (UserSort) -> Unit,
    onFavoritesOnly: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onUser: (Int) -> Unit,
    onFavorite: (User) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = Modifier.padding(padding),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = state.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            UserControls(state, onSort, onFavoritesOnly)
            if (showEmptyState) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag(UiTestTags.USER_LIST_EMPTY),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(
                            if (
                                state.hasCachedUsers ||
                                state.query.isNotBlank() ||
                                state.favoritesOnly
                            ) {
                                R.string.no_results
                            } else {
                                R.string.no_users
                            },
                        ),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.testTag(UiTestTags.USER_LIST),
                ) {
                    items(state.users, key = User::id) { user ->
                        UserCard(
                            user,
                            onClick = { onUser(user.id) },
                            onFavorite = { onFavorite(user) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserControls(
    state: UserListUiState,
    onSort: (UserSort) -> Unit,
    onFavorite: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(expanded, { expanded = it }) {
                FilterChip(
                    selected = false,
                    onClick = { expanded = true },
                    label = {
                        Text(stringResource(if (state.sort == UserSort.NAME_ASCENDING) R.string.sort_az else R.string.sort_za))
                    },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    UserSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(stringResource(if (sort == UserSort.NAME_ASCENDING) R.string.sort_az else R.string.sort_za)) },
                            onClick = { onSort(sort); expanded = false },
                        )
                    }
                }
            }
            FilterChip(
                selected = state.favoritesOnly,
                onClick = { onFavorite(!state.favoritesOnly) },
                label = { Text(stringResource(R.string.favorites_only)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    selectedLeadingIconColor = MaterialTheme.extendedColors.favoriteSelected,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = state.favoritesOnly,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                leadingIcon = {
                    Icon(
                        if (state.favoritesOnly) Icons.Default.Star else Icons.Outlined.StarOutline,
                        null,
                        tint = if (state.favoritesOnly) {
                            MaterialTheme.extendedColors.favoriteSelected
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun UserCard(
    user: User,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openDetailsLabel = stringResource(R.string.open_user_details, user.fullName)
    val favoriteStateDescription = stringResource(
        if (user.isFavorite) R.string.favorite else R.string.not_favorite,
    )
    Card(
        onClick = onClick, modifier = modifier
            .fillMaxWidth()
            .testTag(UiTestTags.user(user.id))
            .semantics {
                onClick(label = openDetailsLabel, action = null)
            },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(
                user.imageUrl, null, Modifier
                    .size(72.dp)
                    .clip(CircleShape)
            )
            Column(
                Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onFavorite,
                        modifier = Modifier
                            .testTag(UiTestTags.favorite(user.id))
                            .semantics {
                                stateDescription = favoriteStateDescription
                            },
                    ) {
                        AnimatedContent(
                            targetState = user.isFavorite,
                            label = UiAnimationLabels.FAVORITE_ICON,
                        ) { isFavorite ->
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                                contentDescription = stringResource(
                                    if (isFavorite) {
                                        R.string.remove_from_favorites
                                    } else {
                                        R.string.add_to_favorites
                                    },
                                ),
                                tint = if (isFavorite) {
                                    MaterialTheme.extendedColors.favoriteSelected
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
                Text(user.email, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(user.companyName, style = MaterialTheme.typography.bodyMedium)
                Text(user.jobTitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UserCardPreview() {
    UserListTheme(ThemeMode.LIGHT) {
        UserCard(
            User(
                1,
                PreviewUserData.FIRST_NAME,
                PreviewUserData.LAST_NAME,
                36,
                PreviewUserData.EMAIL,
                PreviewUserData.PHONE,
                PreviewUserData.USERNAME,
                String.EMPTY,
                PreviewUserData.ROLE,
                PreviewUserData.COMPANY,
                PreviewUserData.DEPARTMENT,
                PreviewUserData.JOB_TITLE,
                PreviewUserData.STREET,
                PreviewUserData.CITY,
                PreviewUserData.STATE,
                PreviewUserData.COUNTRY,
                isFavorite = true
            ),
            onClick = {},
            onFavorite = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) =
    Box(
        Modifier
            .fillMaxSize()
            .padding(padding), contentAlignment = Alignment.Center
    ) { content() }

private const val SCREEN_STATE_KEY = "screen"
private const val PHASE_STATE_KEY = "phase"
private const val INTERACTION_STATE_KEY = "interaction"
private const val VISIBLE_USERS_STATE_KEY = "visible_users"
private const val USERS_SCREEN_VALUE = "users"
private const val INITIAL_LOADING_PHASE = "initial_loading"
private const val ERROR_PHASE = "error"
private const val REFRESHING_PHASE = "refreshing"
private const val EMPTY_PHASE = "empty"
private const val CONTENT_PHASE = "content"
private const val SEARCH_INTERACTION = "search"
private const val FAVORITES_FILTER_INTERACTION = "favorites_filter"
private const val BROWSING_INTERACTION = "browsing"
private const val ZERO_USERS_BUCKET = "0"
private const val ONE_TO_TEN_USERS_BUCKET = "1_10"
private const val ELEVEN_TO_FIFTY_USERS_BUCKET = "11_50"
private const val FIFTY_ONE_PLUS_USERS_BUCKET = "51_plus"

private object PreviewUserData {
    const val FIRST_NAME = "Ada"
    const val LAST_NAME = "Lovelace"
    const val EMAIL = "ada@example.com"
    const val PHONE = "+1 555"
    const val USERNAME = "ada"
    const val ROLE = "admin"
    const val COMPANY = "Analytical Engines"
    const val DEPARTMENT = "Research"
    const val JOB_TITLE = "Engineer"
    const val STREET = "1 Main Street"
    const val CITY = "London"
    const val STATE = "England"
    const val COUNTRY = "UK"
}
