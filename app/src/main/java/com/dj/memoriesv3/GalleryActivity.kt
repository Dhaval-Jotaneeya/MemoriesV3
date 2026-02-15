package com.dj.memoriesv3

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import coil.compose.SubcomposeAsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.ImageLoader
import com.dj.memoriesv3.ui.theme.MemoriesTheme

class GalleryActivity : ComponentActivity() {

    private lateinit var githubToken: String
    private lateinit var organization: String
    private lateinit var viewModel: GalleryViewModel
    private lateinit var repoName: String
    private lateinit var imageLoader: ImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        repoName = intent.getStringExtra("REPO_NAME") ?: return finish()

        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        githubToken = sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, null) ?: ""
        organization = sharedPreferences.getString(Constants.KEY_ORG_NAME, null) ?: ""

        if (githubToken.isEmpty() || organization.isEmpty()) {
            Toast.makeText(this, "App not configured. Please set token and organization in settings.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.3)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("thumbnails_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()

        viewModel = ViewModelProvider(this)[GalleryViewModel::class.java]
        viewModel.loadImages(organization, repoName, githubToken)

        setContent {
            MemoriesTheme {
                GalleryScreen(repoName)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GalleryScreen(repoName: String) {
        val imagesState by viewModel.imagesState.collectAsState()
        val statusMessage by viewModel.statusMessage.collectAsState()
        val uploadProgress by viewModel.uploadProgress.collectAsState()
        val isSelectionMode by viewModel.isSelectionMode.collectAsState()
        val selectedFiles by viewModel.selectedFiles.collectAsState()
        val batchActionProgress by viewModel.batchActionProgress.collectAsState()
        val context = LocalContext.current
        var showDeleteConfirmation by remember { mutableStateOf(false) }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents()
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                viewModel.uploadImages(context, uris)
            }
        }

        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState()
        )

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                if (isSelectionMode) {
                    // Selection mode top bar
                    TopAppBar(
                        title = {
                            Text(
                                "${selectedFiles.size} selected",
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                            }
                        },
                        actions = {
                            // Select All
                            val currentImages = (imagesState as? UiState.Success)?.data ?: emptyList()
                            IconButton(
                                onClick = {
                                    if (selectedFiles.size == currentImages.size) {
                                        viewModel.clearSelection()
                                    } else {
                                        viewModel.selectAll(currentImages)
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Default.SelectAll,
                                    contentDescription = "Select All",
                                )
                            }
                            // Download
                            IconButton(
                                onClick = {
                                    viewModel.downloadSelectedPhotos(context)
                                },
                                enabled = selectedFiles.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Outlined.Download,
                                    contentDescription = "Download Selected",
                                )
                            }
                            // Delete
                            IconButton(
                                onClick = { showDeleteConfirmation = true },
                                enabled = selectedFiles.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Selected",
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
                    LargeTopAppBar(
                        title = {
                            Column {
                                Text(
                                    repoName,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                AnimatedVisibility(
                                    visible = statusMessage.isNotEmpty(),
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut(),
                                ) {
                                    Text(
                                        statusMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        )
                    )
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !uploadProgress.isUploading && !uploadProgress.isComplete
                            && !batchActionProgress.isRunning && !batchActionProgress.isComplete
                            && !isSelectionMode,
                    enter = scaleIn(animationSpec = tween(300)) + fadeIn(),
                    exit = scaleOut(animationSpec = tween(200)) + fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Add Photos") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (val state = imagesState) {
                    is UiState.Loading -> GalleryLoadingGrid()
                    is UiState.Error -> GalleryErrorState(state.message)
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            GalleryEmptyState()
                        } else {
                            GalleryGrid(
                                files = state.data,
                                repoName = repoName,
                                isSelectionMode = isSelectionMode,
                                selectedFiles = selectedFiles,
                            )
                        }
                    }
                }

                // Upload progress overlay
                AnimatedVisibility(
                    visible = uploadProgress.isUploading || uploadProgress.isComplete,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    UploadProgressCard(uploadProgress)
                }

                // Batch action progress overlay
                AnimatedVisibility(
                    visible = batchActionProgress.isRunning || batchActionProgress.isComplete,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    BatchActionProgressCard(batchActionProgress)
                }
            }
        }

        // Delete confirmation dialog
        if (showDeleteConfirmation) {
            DeletePhotosConfirmationDialog(
                count = selectedFiles.size,
                onDismiss = { showDeleteConfirmation = false },
                onConfirm = {
                    showDeleteConfirmation = false
                    viewModel.deleteSelectedPhotos()
                }
            )
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun GalleryGrid(
        files: List<GitHubFile>,
        repoName: String,
        isSelectionMode: Boolean,
        selectedFiles: Set<String>,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(files, key = { it.path }) { file ->
                GalleryItem(
                    file = file,
                    repoName = repoName,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedFiles.contains(file.path),
                )
            }
            // Bottom spacing for FAB
            item { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun GalleryItem(
        file: GitHubFile,
        repoName: String,
        isSelectionMode: Boolean,
        isSelected: Boolean,
    ) {
        val context = LocalContext.current
        val imageUrl = file.getImageUrl()
        val haptics = LocalHapticFeedback.current

        val scale by animateFloatAsState(
            targetValue = if (isSelected) 0.88f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "itemScale"
        )

        val borderColor by animateColorAsState(
            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
            animationSpec = tween(200),
            label = "borderColor"
        )

        Card(
            modifier = Modifier
                .aspectRatio(1f)
                .scale(scale)
                .animateContentSize()
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (isSelected) {
                        Modifier.border(3.dp, borderColor, RoundedCornerShape(12.dp))
                    } else Modifier
                )
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            viewModel.toggleSelection(file.path)
                        } else {
                            val currentFiles = (viewModel.imagesState.value as? UiState.Success)?.data ?: emptyList()
                            val currentIndex = currentFiles.indexOf(file).coerceAtLeast(0)
                            val intent = Intent(context, PhotoViewerActivity::class.java)
                            intent.putExtra("REPO_NAME", repoName)
                            intent.putExtra("CURRENT_INDEX", currentIndex)
                            intent.putExtra("PHOTO_LIST_JSON", com.google.gson.Gson().toJson(currentFiles))
                            context.startActivity(intent)
                        }
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!isSelectionMode) {
                            viewModel.toggleSelection(file.path)
                        }
                    },
                ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Box {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .addHeader("Authorization", "Bearer $githubToken")
                        .crossfade(400)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCacheKey(imageUrl)
                        .diskCacheKey(imageUrl)
                        .size(300)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    loading = {
                        ThumbnailPlaceholder()
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.BrokenImage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Selection overlay & checkbox
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(200)),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Dimming overlay for unselected items
                        if (!isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.15f))
                            )
                        }
                        // Check icon
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ThumbnailPlaceholder() {
        val shimmerTransition = rememberInfiniteTransition(label = "thumbShimmer")
        val shimmerTranslateAnim by shimmerTransition.animateFloat(
            initialValue = 0f,
            targetValue = 600f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "thumbShimmerTranslate"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        start = Offset(shimmerTranslateAnim - 150f, 0f),
                        end = Offset(shimmerTranslateAnim + 150f, 0f),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp),
            )
        }
    }

    @Composable
    fun GalleryLoadingGrid() {
        val shimmerTransition = rememberInfiniteTransition(label = "gridShimmer")
        val shimmerAnim by shimmerTransition.animateFloat(
            initialValue = 0f,
            targetValue = 800f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
            label = "gridShimmerTranslate"
        )

        val shimmerBrush = Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            start = Offset(shimmerAnim - 200f, 0f),
            end = Offset(shimmerAnim + 200f, 0f),
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(12) {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(shimmerBrush)
                )
            }
        }
    }

    @Composable
    fun GalleryEmptyState() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.ImageNotSupported,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "No photos yet",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap the + button to add your\nfirst photos to this album",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    @Composable
    fun GalleryErrorState(message: String) {
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
                        "Couldn't load photos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    FilledTonalButton(
                        onClick = { viewModel.loadImages(organization, repoName, githubToken) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }

    // ── Delete Confirmation Dialog ──
    @Composable
    fun DeletePhotosConfirmationDialog(
        count: Int,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
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
                    "Delete $count ${if (count == 1) "Photo" else "Photos"}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Are you sure you want to delete $count ${if (count == 1) "photo" else "photos"}? Both the original and thumbnail will be removed.",
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
                            "⚠️ This action cannot be undone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
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
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
        )
    }

    // ── Batch Action Progress Card ──
    @Composable
    fun BatchActionProgressCard(progress: BatchActionProgress) {
        val isDelete = progress.actionType == "delete"

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (progress.isComplete) {
                        Icon(
                            imageVector = if (progress.errors.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (progress.errors.isEmpty()) Color(0xFF4CAF50) else Color(0xFFFFA726),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                    } else {
                        Icon(
                            if (isDelete) Icons.Outlined.DeleteForever else Icons.Outlined.Download,
                            contentDescription = null,
                            tint = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                    }

                    Text(
                        text = when {
                            progress.isComplete && progress.errors.isEmpty() -> if (isDelete) "Deletion Complete!" else "Download Complete!"
                            progress.isComplete -> if (isDelete) "Deletion Finished" else "Download Finished"
                            else -> if (isDelete) "Deleting Photos" else "Downloading Photos"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.weight(1f))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDelete)
                            MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "${progress.completedFiles}/${progress.totalFiles}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isDelete)
                                MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { progress.overallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when {
                        progress.isComplete && progress.errors.isEmpty() -> Color(0xFF4CAF50)
                        progress.isComplete -> Color(0xFFFFA726)
                        isDelete -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )

                Spacer(Modifier.height(10.dp))

                // Current file / status
                if (!progress.isComplete && progress.currentFileName.isNotEmpty()) {
                    Text(
                        text = progress.currentFileName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                }

                Text(
                    text = progress.currentStep,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Show errors if any
                if (progress.errors.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Errors:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    progress.errors.take(3).forEach { error ->
                        Text(
                            text = "• $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (progress.errors.size > 3) {
                        Text(
                            text = "...and ${progress.errors.size - 3} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                        )
                    }
                }

                // Dismiss button when complete
                if (progress.isComplete) {
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilledTonalButton(
                            onClick = { viewModel.dismissBatchProgress() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (progress.errors.isEmpty())
                                    Color(0xFF4CAF50).copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (progress.errors.isEmpty())
                                    Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun UploadProgressCard(progress: UploadProgress) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (progress.isComplete) {
                        Icon(
                            imageVector = if (progress.errors.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (progress.errors.isEmpty()) Color(0xFF4CAF50) else Color(0xFFFFA726),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                    } else {
                        Icon(
                            Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                    }

                    Text(
                        text = when {
                            progress.isComplete && progress.errors.isEmpty() -> "Upload Complete!"
                            progress.isComplete -> "Upload Finished"
                            else -> "Uploading Photos"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.weight(1f))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "${progress.completedFiles}/${progress.totalFiles}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { progress.overallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when {
                        progress.isComplete && progress.errors.isEmpty() -> Color(0xFF4CAF50)
                        progress.isComplete -> Color(0xFFFFA726)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )

                Spacer(Modifier.height(10.dp))

                // Current file / status
                if (!progress.isComplete && progress.currentFileName.isNotEmpty()) {
                    Text(
                        text = progress.currentFileName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                }

                Text(
                    text = progress.currentStep,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Show errors if any
                if (progress.errors.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Errors:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    progress.errors.take(3).forEach { error ->
                        Text(
                            text = "• $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (progress.errors.size > 3) {
                        Text(
                            text = "...and ${progress.errors.size - 3} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                        )
                    }
                }

                // Dismiss button when complete
                if (progress.isComplete) {
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilledTonalButton(
                            onClick = { viewModel.dismissUploadProgress() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (progress.errors.isEmpty())
                                    Color(0xFF4CAF50).copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (progress.errors.isEmpty())
                                    Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

// Data Model
data class GitHubFile(
    val name: String,
    val path: String,
    val download_url: String?,
    val url: String?,
    val originalPath: String? = null
) {
    fun getImageUrl(): String {
        return download_url ?: url ?: ""
    }
}