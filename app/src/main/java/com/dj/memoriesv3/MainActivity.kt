package com.dj.memoriesv3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.dj.memoriesv3.ui.theme.MemoriesTheme
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        checkSettings()

        setContent {
            MemoriesTheme {
                MainScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val state by viewModel.reposState.collectAsState()
        val isRefreshing by viewModel.isRefreshing.collectAsState()
        val isCreatingAlbum by viewModel.isCreatingAlbum.collectAsState()
        val createAlbumResult by viewModel.createAlbumResult.collectAsState()
        val isPerformingAction by viewModel.isPerformingAction.collectAsState()
        val repoActionResult by viewModel.repoActionResult.collectAsState()
        var searchQuery by remember { mutableStateOf("") }
        var searchActive by remember { mutableStateOf(false) }
        var showCreateDialog by remember { mutableStateOf(false) }

        // Selection state for long-press
        var selectedRepo by remember { mutableStateOf<GitHubRepo?>(null) }
        var showDeleteConfirmation by remember { mutableStateOf(false) }
        var showRenameDialog by remember { mutableStateOf(false) }

        val snackbarHostState = remember { SnackbarHostState() }

        // Handle create album result
        LaunchedEffect(createAlbumResult) {
            when (val result = createAlbumResult) {
                is CreateAlbumResult.Success -> {
                    snackbarHostState.showSnackbar(
                        message = "Album \"${result.albumName}\" created successfully!",
                        duration = SnackbarDuration.Short
                    )
                    viewModel.clearCreateResult()
                }
                is CreateAlbumResult.Error -> {
                    snackbarHostState.showSnackbar(
                        message = "Error: ${result.message}",
                        duration = SnackbarDuration.Long
                    )
                    viewModel.clearCreateResult()
                }
                null -> {}
            }
        }

        // Handle repo action result
        LaunchedEffect(repoActionResult) {
            when (val result = repoActionResult) {
                is RepoActionResult.Success -> {
                    selectedRepo = null
                    snackbarHostState.showSnackbar(
                        message = result.message,
                        duration = SnackbarDuration.Short
                    )
                    viewModel.clearRepoActionResult()
                }
                is RepoActionResult.Error -> {
                    snackbarHostState.showSnackbar(
                        message = "Error: ${result.message}",
                        duration = SnackbarDuration.Long
                    )
                    viewModel.clearRepoActionResult()
                }
                null -> {}
            }
        }

        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState()
        )

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                // Show contextual action bar when a repo is selected
                if (selectedRepo != null) {
                    TopAppBar(
                        title = {
                            Text(
                                selectedRepo?.name ?: "",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { selectedRepo = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Deselect")
                            }
                        },
                        actions = {
                            // Rename
                            IconButton(
                                onClick = { showRenameDialog = true },
                                enabled = !isPerformingAction,
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Rename",
                                )
                            }
                            // Delete
                            IconButton(
                                onClick = { showDeleteConfirmation = true },
                                enabled = !isPerformingAction,
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                "Memories",
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        actions = {
                            // Refresh button with rotation animation
                            val rotation by animateFloatAsState(
                                targetValue = if (isRefreshing) 360f else 0f,
                                animationSpec = if (isRefreshing) infiniteRepeatable(
                                    tween(1000, easing = LinearEasing),
                                    RepeatMode.Restart
                                ) else tween(0),
                                label = "refreshRotation"
                            )

                            IconButton(
                                onClick = {
                                    val sp = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                                    val org = sp.getString(Constants.KEY_ORG_NAME, null)
                                    val token = sp.getString(Constants.KEY_GITHUB_TOKEN, null)
                                    if (!org.isNullOrEmpty() && !token.isNullOrEmpty()) {
                                        viewModel.forceRefresh(org, token)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                )
                            }
                            IconButton(
                                onClick = {
                                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                                }
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    )
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = selectedRepo == null,
                    enter = scaleIn(animationSpec = tween(200)) + fadeIn(),
                    exit = scaleOut(animationSpec = tween(150)) + fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { showCreateDialog = true },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("New Album") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                // ── Refreshing / Creating / Action Indicator ──
                AnimatedVisibility(
                    visible = isRefreshing || isCreatingAlbum || isPerformingAction,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = when {
                                isPerformingAction -> MaterialTheme.colorScheme.error
                                isCreatingAlbum -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        if (isCreatingAlbum) {
                            Text(
                                text = "Creating album…",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        if (isPerformingAction) {
                            Text(
                                text = "Processing…",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                // ── Search Bar ──
                SearchBarSection(
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        viewModel.filterRepositories(it)
                    },
                )

                // ── Content ──
                when (val s = state) {
                    is UiState.Loading -> LoadingSkeletons()
                    is UiState.Error -> ErrorState(s.message)
                    is UiState.Success -> {
                        if (s.data.isEmpty() && searchQuery.isEmpty()) {
                            EmptyState()
                        } else if (s.data.isEmpty()) {
                            NoSearchResults(searchQuery)
                        } else {
                            RepoList(
                                repos = s.data,
                                selectedRepo = selectedRepo,
                                onRepoClick = { repo ->
                                    if (selectedRepo != null) {
                                        // If in selection mode, tap selects/deselects
                                        selectedRepo = if (selectedRepo?.id == repo.id) null else repo
                                    } else {
                                        val intent = Intent(this@MainActivity, GalleryActivity::class.java)
                                        intent.putExtra("REPO_NAME", repo.name)
                                        startActivity(intent)
                                    }
                                },
                                onRepoLongClick = { repo ->
                                    selectedRepo = repo
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── Create Album Dialog ──
        if (showCreateDialog) {
            CreateAlbumDialog(
                isCreating = isCreatingAlbum,
                onDismiss = { showCreateDialog = false },
                onConfirm = { albumName, description ->
                    val sp = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    val org = sp.getString(Constants.KEY_ORG_NAME, null)
                    val token = sp.getString(Constants.KEY_GITHUB_TOKEN, null)
                    if (!org.isNullOrEmpty() && !token.isNullOrEmpty()) {
                        viewModel.createAlbum(org, token, albumName, description)
                        showCreateDialog = false
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Please configure organization and token in Settings first.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }

        // ── Delete Confirmation Dialog ──
        if (showDeleteConfirmation && selectedRepo != null) {
            DeleteRepoConfirmationDialog(
                repoName = selectedRepo!!.name,
                isDeleting = isPerformingAction,
                onDismiss = { showDeleteConfirmation = false },
                onConfirm = {
                    val sp = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    val org = sp.getString(Constants.KEY_ORG_NAME, null)
                    val token = sp.getString(Constants.KEY_GITHUB_TOKEN, null)
                    if (!org.isNullOrEmpty() && !token.isNullOrEmpty()) {
                        viewModel.deleteRepo(org, token, selectedRepo!!.name)
                        showDeleteConfirmation = false
                    }
                }
            )
        }

        // ── Rename Dialog ──
        if (showRenameDialog && selectedRepo != null) {
            RenameRepoDialog(
                currentName = selectedRepo!!.name,
                isRenaming = isPerformingAction,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName ->
                    val sp = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    val org = sp.getString(Constants.KEY_ORG_NAME, null)
                    val token = sp.getString(Constants.KEY_GITHUB_TOKEN, null)
                    if (!org.isNullOrEmpty() && !token.isNullOrEmpty()) {
                        viewModel.renameRepo(org, token, selectedRepo!!.name, newName)
                        showRenameDialog = false
                    }
                }
            )
        }
    }

    @Composable
    fun SearchBarSection(query: String, onQueryChange: (String) -> Unit) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search albums...") },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun RepoList(
        repos: List<GitHubRepo>,
        selectedRepo: GitHubRepo?,
        onRepoClick: (GitHubRepo) -> Unit,
        onRepoLongClick: (GitHubRepo) -> Unit,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(repos, key = { _, repo -> repo.id }) { index, repo ->
                val animatedVisibility = remember { Animatable(0f) }

                LaunchedEffect(repo.id) {
                    animatedVisibility.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 400,
                            delayMillis = (index * 40).coerceAtMost(600),
                            easing = FastOutSlowInEasing,
                        )
                    )
                }

                RepoCard(
                    repo = repo,
                    isSelected = selectedRepo?.id == repo.id,
                    onClick = { onRepoClick(repo) },
                    onLongClick = { onRepoLongClick(repo) },
                    modifier = Modifier.animateItemPlacement(
                        animationSpec = tween(300),
                    )
                )
            }
            // Bottom spacing for FAB
            item { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun RepoCard(
        repo: GitHubRepo,
        isSelected: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val haptics = LocalHapticFeedback.current

        val containerColor by animateColorAsState(
            targetValue = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerLow,
            animationSpec = tween(250),
            label = "cardColor"
        )

        val borderColor by animateColorAsState(
            targetValue = if (isSelected)
                MaterialTheme.colorScheme.primary
            else Color.Transparent,
            animationSpec = tween(250),
            label = "borderColor"
        )

        Card(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp),
            border = if (isSelected) androidx.compose.foundation.BorderStroke(
                2.dp, borderColor
            ) else null,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Album icon with tinted background
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = repo.description ?: "No description",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    repo.updatedAt?.let { date ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = formatRelativeDate(date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                Icon(
                    Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    @Composable
    fun LoadingSkeletons() {
        val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
        val shimmerTranslateAnim by shimmerTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
            label = "shimmerTranslate"
        )

        val shimmerBrush = Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            start = Offset(shimmerTranslateAnim - 200f, 0f),
            end = Offset(shimmerTranslateAnim + 200f, 0f),
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(8) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(shimmerBrush)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush)
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush)
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.3f)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EmptyState() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "No albums yet",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Create your first album to start\norganizing your memories",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    @Composable
    fun NoSearchResults(query: String) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No results for \"$query\"",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Try a different search term",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    fun ErrorState(message: String) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.padding(32.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Outlined.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Something went wrong",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    FilledTonalButton(
                        onClick = {
                            val sp = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                            val org = sp.getString(Constants.KEY_ORG_NAME, null)
                            val token = sp.getString(Constants.KEY_GITHUB_TOKEN, null)
                            if (!org.isNullOrEmpty() && !token.isNullOrEmpty()) {
                                viewModel.forceRefresh(org, token)
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text("Try Again")
                    }
                }
            }
        }
    }

    // ── Delete Confirmation Dialog ──
    @Composable
    fun DeleteRepoConfirmationDialog(
        repoName: String,
        isDeleting: Boolean,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) onDismiss() },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            title = {
                Text(
                    "Delete Album",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Are you sure you want to permanently delete the album \"$repoName\"?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        ),
                    ) {
                        Text(
                            "⚠️ This action cannot be undone. All photos and data in this album will be permanently deleted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    if (isDeleting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "Deleting album…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isDeleting,
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // ── Rename Dialog ──
    @Composable
    fun RenameRepoDialog(
        currentName: String,
        isRenaming: Boolean,
        onDismiss: () -> Unit,
        onConfirm: (newName: String) -> Unit,
    ) {
        var newName by remember { mutableStateOf(currentName) }
        var nameError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isRenaming) onDismiss() },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DriveFileRenameOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            title = {
                Text(
                    "Rename Album",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Enter a new name for \"$currentName\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it.replace(Regex("[^a-zA-Z0-9._-]"), "-")
                            nameError = null
                        },
                        label = { Text("New Name") },
                        singleLine = true,
                        isError = nameError != null,
                        supportingText = nameError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isRenaming,
                    )
                    if (isRenaming) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                "Renaming album…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isEmpty()) {
                            nameError = "Name cannot be empty"
                            return@Button
                        }
                        if (trimmed.length < 2) {
                            nameError = "Name must be at least 2 characters"
                            return@Button
                        }
                        if (trimmed == currentName) {
                            nameError = "Name is the same as current name"
                            return@Button
                        }
                        onConfirm(trimmed)
                    },
                    enabled = !isRenaming && newName.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isRenaming,
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    private fun formatRelativeDate(isoDate: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(isoDate) ?: return isoDate
            val now = System.currentTimeMillis()
            val diff = now - date.time

            val minutes = diff / (1000 * 60)
            val hours = minutes / 60
            val days = hours / 24
            val weeks = days / 7
            val months = days / 30

            when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                hours < 24 -> "${hours}h ago"
                days < 7 -> "${days}d ago"
                weeks < 4 -> "${weeks}w ago"
                months < 12 -> "${months}mo ago"
                else -> SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) {
            isoDate
        }
    }

    @Composable
    fun CreateAlbumDialog(
        isCreating: Boolean,
        onDismiss: () -> Unit,
        onConfirm: (albumName: String, description: String) -> Unit
    ) {
        var albumName by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var nameError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isCreating) onDismiss() },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            title = {
                Text(
                    "Create New Album",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Create a new photo album with a thumbnails folder and README.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = albumName,
                        onValueChange = {
                            // GitHub repo names: alphanumeric, hyphens, underscores, dots
                            albumName = it.replace(Regex("[^a-zA-Z0-9._-]"), "-")
                            nameError = null
                        },
                        label = { Text("Album Name *") },
                        placeholder = { Text("e.g. summer-vacation-2026") },
                        singleLine = true,
                        isError = nameError != null,
                        supportingText = nameError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isCreating,
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        placeholder = { Text("A short description for this album") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isCreating,
                    )

                    if (isCreating) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Creating album and initializing files…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = albumName.trim()
                        if (trimmed.isEmpty()) {
                            nameError = "Album name is required"
                            return@Button
                        }
                        if (trimmed.length < 2) {
                            nameError = "Name must be at least 2 characters"
                            return@Button
                        }
                        onConfirm(trimmed, description.trim())
                    },
                    enabled = !isCreating && albumName.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isCreating,
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    private fun checkSettings() {
        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val orgName = sharedPreferences.getString(Constants.KEY_ORG_NAME, null)
        val token = sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, null)

        if (orgName.isNullOrEmpty() || token.isNullOrEmpty()) {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val orgName = sharedPreferences.getString(Constants.KEY_ORG_NAME, null)
        val token = sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, null)
        if (!orgName.isNullOrEmpty() && !token.isNullOrEmpty()) {
            viewModel.refreshIfNeeded(orgName, token)
        }
    }
}