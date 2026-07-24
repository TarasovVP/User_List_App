package com.example.userlistapp.feature.users.list

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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.userlistapp.R
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.domain.model.UserSort
import com.example.userlistapp.feature.users.components.UserAvatar
import com.example.userlistapp.ui.theme.FavoriteSelectedColor
import com.example.userlistapp.ui.theme.UserListTheme

@Composable
fun UserListRoute(onUser: (Int) -> Unit, onSettings: () -> Unit, viewModel: UserListViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it.resolve(context)) } }
    UserListScreen(
        state,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    state: UserListUiState,
    onQuery: (String) -> Unit,
    onSort: (UserSort) -> Unit,
    onFavoritesOnly: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onUser: (Int) -> Unit,
    onFavorite: (User) -> Unit,
    onSettings: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var searchActive by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
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
        searchValue = TextFieldValue("")
        onQuery("")
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
        topBar = { TopAppBar(
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
                if (searchActive) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (searchValue.text.isEmpty()) {
                            Text(
                                stringResource(R.string.search_users),
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
                                .testTag("search"),
                        )
                    }
                } else {
                    Text(stringResource(R.string.users_title))
                }
            },
            actions = {
            if (searchActive && searchValue.text.isNotEmpty()) {
                IconButton(onClick = {
                    searchValue = TextFieldValue("")
                    onQuery("")
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
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isInitialLoading -> Centered(padding) { CircularProgressIndicator(Modifier.testTag("initial_loading")) }
            state.initialError != null -> Centered(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.initialError.resolve(LocalContext.current), modifier = Modifier.padding(24.dp))
                    Button(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
                }
            }
            else -> PullToRefreshBox(
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
                    if (state.users.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(if (state.hasCachedUsers || state.query.isNotBlank() || state.favoritesOnly) R.string.no_results else R.string.no_users))
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("user_list")) {
                            items(state.users, key = User::id) { user ->
                                UserCard(user, onClick = { onUser(user.id) }, onFavorite = { onFavorite(user) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserControls(state: UserListUiState, onSort: (UserSort) -> Unit, onFavorite: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    UserSort.entries.forEach { sort -> DropdownMenuItem(
                        text = { Text(stringResource(if (sort == UserSort.NAME_ASCENDING) R.string.sort_az else R.string.sort_za)) },
                        onClick = { onSort(sort); expanded = false },
                    ) }
                }
            }
            FilterChip(
                selected = state.favoritesOnly,
                onClick = { onFavorite(!state.favoritesOnly) },
                label = { Text(stringResource(R.string.favorites_only)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    selectedLeadingIconColor = FavoriteSelectedColor,
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
                            FavoriteSelectedColor
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
private fun UserCard(user: User, onClick: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth().testTag("user_${user.id}")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(user.imageUrl, user.fullName, Modifier.size(72.dp).clip(CircleShape))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.fullName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onFavorite, modifier = Modifier.testTag("favorite_${user.id}")) {
                        Icon(
                            imageVector = if (user.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = stringResource(if (user.isFavorite) R.string.favorite else R.string.not_favorite),
                            tint = if (user.isFavorite) {
                                FavoriteSelectedColor
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
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
            User(1, "Ada", "Lovelace", 36, "ada@example.com", "+1 555", "ada", "", "admin", "Analytical Engines", "Research", "Engineer", "1 Main Street", "London", "England", "UK", isFavorite = true),
            onClick = {},
            onFavorite = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) = Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { content() }
