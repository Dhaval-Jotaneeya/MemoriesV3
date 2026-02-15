package com.dj.memoriesv3

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.dj.memoriesv3.ui.theme.MemoriesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PhotoViewerActivity : ComponentActivity() {

    private var currentImageFile: File? = null
    private var currentBitmap: Bitmap? = null

    // State flows for Compose
    private val _viewerState = MutableStateFlow<ViewerState>(ViewerState.Loading)
    private val viewerState: StateFlow<ViewerState> = _viewerState

    private val _downloadProgress = MutableStateFlow(0f)
    private val downloadProgress: StateFlow<Float> = _downloadProgress

    private val _downloadDetails = MutableStateFlow("")
    private val downloadDetails: StateFlow<String> = _downloadDetails

    private val _downloadSpeed = MutableStateFlow("")
    private val downloadSpeed: StateFlow<String> = _downloadSpeed

    sealed class ViewerState {
        object Loading : ViewerState()
        data class Success(val bitmap: Bitmap) : ViewerState()
        data class Error(val message: String) : ViewerState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repoName = intent.getStringExtra("REPO_NAME") ?: return finish()
        val fileName = intent.getStringExtra("FILE_NAME") ?: return finish()
        val filePath = intent.getStringExtra("FILE_PATH") ?: fileName
        val originalPathExtra = intent.getStringExtra("ORIGINAL_PATH")

        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val token = sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, "") ?: ""
        val orgName = sharedPreferences.getString(Constants.KEY_ORG_NAME, "") ?: ""

        val originalPath = originalPathExtra ?: filePath.replaceFirst(Regex("^thumbnails/", RegexOption.IGNORE_CASE), "")
        val encodedOriginalPath = originalPath.split('/').joinToString("/") { android.net.Uri.encode(it) }

        val originalUrl = if (token.isNotEmpty()) {
            "https://api.github.com/repos/$orgName/$repoName/contents/$encodedOriginalPath"
        } else {
            "https://raw.githubusercontent.com/$orgName/$repoName/HEAD/$encodedOriginalPath"
        }

        downloadImage(originalUrl, token)

        setContent {
            MemoriesTheme(darkTheme = true) {
                PhotoViewerScreen(fileName)
            }
        }
    }

    // ─────────────────────── Main Screen ───────────────────────

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PhotoViewerScreen(fileName: String) {
        val state by viewerState.collectAsState()
        val progress by downloadProgress.collectAsState()
        val details by downloadDetails.collectAsState()
        val speed by downloadSpeed.collectAsState()
        var controlsVisible by remember { mutableStateOf(true) }
        var showInfoDialog by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding(),
        ) {
            // ── Image with Gestures ──
            when (val s = state) {
                is ViewerState.Loading -> {
                    DownloadProgressOverlay(progress, details, speed)
                }
                is ViewerState.Success -> {
                    ZoomableImage(
                        bitmap = s.bitmap,
                        contentDescription = fileName,
                        onSingleTap = { controlsVisible = !controlsVisible },
                        onLongPress = { showInfoDialog = true },
                        onSwipeDown = { finish() },
                    )
                }
                is ViewerState.Error -> {
                    ErrorOverlay(s.message)
                }
            }

            // ── Zoom level indicator (shows briefly when zooming) ──
            // (handled inside ZoomableImage)

            // ── Top bar with gradient scrim ──
            AnimatedVisibility(
                visible = controlsVisible && state !is ViewerState.Loading,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Transparent,
                                ),
                            )
                        )
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                fileName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }
            }

            // ── Bottom action bar with gradient scrim ──
            AnimatedVisibility(
                visible = controlsVisible && state is ViewerState.Success,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f),
                                ),
                            )
                        )
                        .padding(bottom = 16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        // Save Button
                        ActionChip(
                            icon = Icons.Outlined.Download,
                            label = "Save",
                            onClick = { saveImageToGallery() },
                        )
                        // Share Button
                        ActionChip(
                            icon = Icons.Outlined.Share,
                            label = "Share",
                            onClick = { shareImage() },
                        )
                        // Info Button
                        ActionChip(
                            icon = Icons.Outlined.Info,
                            label = "Info",
                            onClick = { showInfoDialog = true },
                        )
                    }
                }
            }
        }

        // ── Image Info Dialog ──
        if (showInfoDialog) {
            ImageInfoDialog(
                bitmap = currentBitmap,
                fileName = fileName,
                fileSize = currentImageFile?.length(),
                onDismiss = { showInfoDialog = false },
            )
        }
    }

    // ─────────────────────── Zoomable Image ───────────────────────

    @Composable
    fun ZoomableImage(
        bitmap: Bitmap,
        contentDescription: String,
        onSingleTap: () -> Unit,
        onLongPress: () -> Unit,
        onSwipeDown: () -> Unit,
    ) {
        // Zoom & pan state
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        var rotation by remember { mutableFloatStateOf(0f) }

        // Animated zoom (for double-tap)
        val animatedScale = remember { Animatable(1f) }
        val animatedOffsetX = remember { Animatable(0f) }
        val animatedOffsetY = remember { Animatable(0f) }

        var containerSize by remember { mutableStateOf(IntSize.Zero) }

        // Zoom indicator
        var showZoomIndicator by remember { mutableStateOf(false) }
        var zoomLevel by remember { mutableFloatStateOf(1f) }

        // Swipe-down-to-dismiss tracking
        var dismissOffset by remember { mutableFloatStateOf(0f) }
        val dismissAlpha by remember { derivedStateOf { 1f - (abs(dismissOffset) / 800f).coerceIn(0f, 0.7f) } }

        val coroutineScope = rememberCoroutineScope()

        val minScale = 0.5f
        val maxScale = 8f
        val doubleTapScale = 3f

        // Sync animated values back to mutable state
        LaunchedEffect(animatedScale.value) { scale = animatedScale.value }
        LaunchedEffect(animatedOffsetX.value) { offsetX = animatedOffsetX.value }
        LaunchedEffect(animatedOffsetY.value) { offsetY = animatedOffsetY.value }

        // Auto-hide zoom indicator
        LaunchedEffect(showZoomIndicator) {
            if (showZoomIndicator) {
                delay(1200)
                showZoomIndicator = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onSingleTap() },
                        onDoubleTap = { tapOffset ->
                            coroutineScope.launch {
                                if (scale > 1.1f) {
                                    // Reset to 1x
                                    launch { animatedScale.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
                                    launch { animatedOffsetX.animateTo(0f, tween(350, easing = FastOutSlowInEasing)) }
                                    launch { animatedOffsetY.animateTo(0f, tween(350, easing = FastOutSlowInEasing)) }
                                    rotation = 0f
                                    zoomLevel = 1f
                                } else {
                                    // Zoom to doubleTapScale, centered on tap point
                                    val newScale = doubleTapScale
                                    val centerX = containerSize.width / 2f
                                    val centerY = containerSize.height / 2f
                                    val focusX = tapOffset.x - centerX
                                    val focusY = tapOffset.y - centerY
                                    val newOffsetX = -focusX * (newScale - 1f)
                                    val newOffsetY = -focusY * (newScale - 1f)

                                    launch { animatedScale.animateTo(newScale, tween(350, easing = FastOutSlowInEasing)) }
                                    launch { animatedOffsetX.animateTo(newOffsetX, tween(350, easing = FastOutSlowInEasing)) }
                                    launch { animatedOffsetY.animateTo(newOffsetY, tween(350, easing = FastOutSlowInEasing)) }
                                    zoomLevel = newScale
                                }
                                showZoomIndicator = true
                            }
                        },
                        onLongPress = { onLongPress() },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, gestureRotation ->
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                        
                        if (newScale <= 1f && scale <= 1f) {
                            // If at 1x or below, track vertical pan for swipe-down-to-dismiss
                            dismissOffset += pan.y
                            if (abs(dismissOffset) > 300f) {
                                onSwipeDown()
                                return@detectTransformGestures
                            }
                        } else {
                            dismissOffset = 0f
                        }

                        // Calculate max pan bounds
                        val maxX = max(0f, (containerSize.width * (newScale - 1f)) / 2f)
                        val maxY = max(0f, (containerSize.height * (newScale - 1f)) / 2f)

                        val newOffsetX = (offsetX + pan.x * newScale / scale).coerceIn(-maxX, maxX)
                        val newOffsetY = (offsetY + pan.y * newScale / scale).coerceIn(-maxY, maxY)

                        scale = newScale
                        offsetX = newOffsetX
                        offsetY = newOffsetY
                        rotation += gestureRotation
                        zoomLevel = newScale

                        // Update animatable values to current state (no animation)
                        coroutineScope.launch {
                            animatedScale.snapTo(newScale)
                            animatedOffsetX.snapTo(newOffsetX)
                            animatedOffsetY.snapTo(newOffsetY)
                        }

                        showZoomIndicator = true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // The actual image
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX + (if (scale <= 1f) 0f else 0f)
                        translationY = offsetY + dismissOffset
                        rotationZ = rotation
                        alpha = dismissAlpha
                    },
            )

            // ── Zoom Level Indicator ──
            AnimatedVisibility(
                visible = showZoomIndicator,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(400)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (zoomLevel >= 1f) Icons.Outlined.ZoomIn else Icons.Outlined.ZoomOut,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFB8C3FF),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${String.format(Locale.getDefault(), "%.1f", zoomLevel)}×",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }

            // ── Quick zoom controls (show with controls) ──
            // Floating zoom buttons bottom-right when zoomed
            if (scale > 1.05f) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Zoom In
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                val newScale = (scale * 1.5f).coerceAtMost(maxScale)
                                launch { animatedScale.animateTo(newScale, tween(250)) }
                                zoomLevel = newScale
                                showZoomIndicator = true
                            }
                        },
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White,
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Outlined.ZoomIn, "Zoom In", modifier = Modifier.size(20.dp))
                    }
                    // Zoom Out
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                val newScale = (scale / 1.5f).coerceAtLeast(1f)
                                val maxX = max(0f, (containerSize.width * (newScale - 1f)) / 2f)
                                val maxY = max(0f, (containerSize.height * (newScale - 1f)) / 2f)
                                launch { animatedScale.animateTo(newScale, tween(250)) }
                                launch { animatedOffsetX.animateTo(offsetX.coerceIn(-maxX, maxX), tween(250)) }
                                launch { animatedOffsetY.animateTo(offsetY.coerceIn(-maxY, maxY), tween(250)) }
                                zoomLevel = newScale
                                showZoomIndicator = true
                            }
                        },
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White,
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Outlined.ZoomOut, "Zoom Out", modifier = Modifier.size(20.dp))
                    }
                    // Reset
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                launch { animatedScale.animateTo(1f, tween(300)) }
                                launch { animatedOffsetX.animateTo(0f, tween(300)) }
                                launch { animatedOffsetY.animateTo(0f, tween(300)) }
                                rotation = 0f
                                zoomLevel = 1f
                                showZoomIndicator = true
                            }
                        },
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White,
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Outlined.RestartAlt, "Reset", modifier = Modifier.size(20.dp))
                    }
                    // Reset Rotation (only if rotated)
                    if (abs(rotation) > 5f) {
                        SmallFloatingActionButton(
                            onClick = {
                                rotation = 0f
                            },
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White,
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Outlined.RotateLeft, "Reset Rotation", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────── Image Info Dialog ───────────────────────

    @Composable
    fun ImageInfoDialog(
        bitmap: Bitmap?,
        fileName: String,
        fileSize: Long?,
        onDismiss: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Color(0xFF1E1E2E),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f),
            title = {
                Text(
                    "Image Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoRow("File Name", fileName)
                    bitmap?.let {
                        InfoRow("Resolution", "${it.width} × ${it.height} px")
                        InfoRow("Megapixels", String.format(Locale.getDefault(), "%.1f MP", (it.width.toLong() * it.height) / 1_000_000.0))
                        InfoRow("Color Config", it.config?.name ?: "Unknown")
                    }
                    fileSize?.let {
                        InfoRow("File Size", formatFileSize(it))
                    }
                    bitmap?.let {
                        val ratio = it.width.toFloat() / it.height
                        InfoRow("Aspect Ratio", String.format(Locale.getDefault(), "%.2f : 1", ratio))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Color(0xFFB8C3FF))
                }
            },
        )
    }

    @Composable
    fun InfoRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }
    }

    // ─────────────────────── Action Chip ───────────────────────

    @Composable
    fun ActionChip(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String,
        onClick: () -> Unit,
    ) {
        Surface(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.15f),
            contentColor = Color.White,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
            }
        }
    }

    // ─────────────────────── Download Progress ───────────────────────

    @Composable
    fun DownloadProgressOverlay(progress: Float, details: String, speed: String) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                // Circular progress indicator
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(80.dp),
                        color = Color(0xFFB8C3FF),
                        trackColor = Color.White.copy(alpha = 0.1f),
                        strokeWidth = 4.dp,
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Spacer(Modifier.height(20.dp))

                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
                if (speed.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = speed,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }

    // ─────────────────────── Error Overlay ───────────────────────

    @Composable
    fun ErrorOverlay(message: String) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Text(
                    text = "⚠️",
                    fontSize = 48.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Failed to load image",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(
                    onClick = { finish() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Go Back")
                }
            }
        }
    }

    // ─────────────────────── Download Logic ───────────────────────

    private fun downloadImage(urlString: String, token: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            _viewerState.value = ViewerState.Loading
            _downloadDetails.value = "Connecting..."

            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                if (token.isNotEmpty()) {
                    connection.addRequestProperty("Authorization", "Bearer $token")
                    connection.addRequestProperty("Accept", "application/vnd.github.v3.raw")
                }
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned ${connection.responseCode}")
                }

                val fileLength = connection.contentLength
                val input: InputStream = connection.inputStream

                val outputFile = File(cacheDir, "temp_viewer_${System.currentTimeMillis()}.jpg")
                val output = FileOutputStream(outputFile)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int

                var lastUpdate = System.currentTimeMillis()
                var lastBytes: Long = 0

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    output.write(data, 0, count)

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdate > 200) {
                        val timeDiff = currentTime - lastUpdate
                        val bytesDiff = total - lastBytes
                        val speed = if (timeDiff > 0) (bytesDiff * 1000) / timeDiff else 0
                        val progress = if (fileLength > 0) (total.toFloat() / fileLength) else 0f

                        val downloadedStr = formatFileSize(total)
                        val totalStr = if (fileLength > 0) formatFileSize(fileLength.toLong()) else "?"
                        val speedStr = "${formatFileSize(speed)}/s"

                        _downloadProgress.value = progress
                        _downloadDetails.value = "$downloadedStr / $totalStr"
                        _downloadSpeed.value = speedStr

                        lastUpdate = currentTime
                        lastBytes = total
                    }
                }

                output.flush()
                output.close()
                input.close()

                val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                currentImageFile = outputFile
                currentBitmap = bitmap

                if (bitmap != null) {
                    _viewerState.value = ViewerState.Success(bitmap)
                } else {
                    _viewerState.value = ViewerState.Error("Failed to decode image")
                }
            } catch (e: Exception) {
                _viewerState.value = ViewerState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    // ─────────────────────── Save / Share ───────────────────────

    private fun saveImageToGallery() {
        val bitmap = currentBitmap ?: return
        val fileName = intent.getStringExtra("FILE_NAME") ?: "image_${System.currentTimeMillis()}.jpg"

        lifecycleScope.launch(Dispatchers.IO) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, if (fileName.endsWith(".png", true)) "image/png" else "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MemoriesV3")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { stream ->
                        val format = if (fileName.endsWith(".png", true)) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                        bitmap.compress(format, 100, stream)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PhotoViewerActivity, "✓ Image saved to Gallery", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PhotoViewerActivity, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PhotoViewerActivity, "Failed to create media entry", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareImage() {
        val file = currentImageFile ?: return
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Image"))
        } catch (e: Exception) {
            Toast.makeText(this, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
