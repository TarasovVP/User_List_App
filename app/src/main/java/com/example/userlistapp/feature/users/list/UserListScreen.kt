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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.userlistapp.R
import com.example.userlistapp.domain.model.ThemeMode
import com.example.userlistapp.domain.model.User
import com.example.userlistapp.domain.model.UserSort
import com.example.userlistapp.feature.users.components.UserAvatar
import com.example.userlistapp.ui.theme.UserListTheme

@Composable
fun UserListRoute(onUser: (Int) -> Unit, onSettings: () -> Unit, viewModel: UserListViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it.resolve(context)) } }
    UserListScreen(state, viewModel::setQuery, viewModel::setSort, viewModel::setFavoritesOnly, viewModel::refresh, onUser, onSettings, snackbar)
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
    onSettings: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.users_title)) }, actions = {
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
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
            else -> PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.padding(padding)) {
                Column(Modifier.fillMaxSize()) {
                    UserControls(state, onQuery, onSort, onFavoritesOnly)
                    if (state.users.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(if (state.hasCachedUsers || state.query.isNotBlank() || state.favoritesOnly) R.string.no_results else R.string.no_users))
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("user_list")) {
                            items(state.users, key = User::id) { user -> UserCard(user, onClick = { onUser(user.id) }) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserControls(state: UserListUiState, onQuery: (String) -> Unit, onSort: (UserSort) -> Unit, onFavorite: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.search_users)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().testTag("search"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(expanded, { expanded = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = stringResource(if (state.sort == UserSort.NAME_ASCENDING) R.string.sort_az else R.string.sort_za),
                    onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.sort)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    UserSort.entries.forEach { sort -> DropdownMenuItem(
                        text = { Text(stringResource(if (sort == UserSort.NAME_ASCENDING) R.string.sort_az else R.string.sort_za)) },
                        onClick = { onSort(sort); expanded = false },
                    ) }
                }
            }
            FilterChip(selected = state.favoritesOnly, onClick = { onFavorite(!state.favoritesOnly) }, label = { Text(stringResource(R.string.favorites_only)) }, leadingIcon = { Icon(Icons.Default.Favorite, null) })
        }
    }
}

@Composable
private fun UserCard(user: User, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth().testTag("user_${user.id}")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(user.imageUrl, user.fullName, Modifier.size(72.dp).clip(CircleShape))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.fullName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (user.isFavorite) Icon(Icons.Default.Favorite, stringResource(R.string.favorite), tint = MaterialTheme.colorScheme.primary)
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
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) = Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { content() }
