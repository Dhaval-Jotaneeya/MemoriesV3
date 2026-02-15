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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.dj.memoriesv3.ui.theme.MemoriesTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.*
import kotlin.math.PI
import kotlin.math.max

class PhotoViewerActivity : ComponentActivity() {

    private lateinit var repoName: String
    private lateinit var token: String
    private lateinit var orgName: String
    private var photoList: List<GitHubFile> = emptyList()
    private var initialIndex: Int = 0

    // Per-page state maps
    private val pageStates = mutableMapOf<Int, MutableState<ViewerState>>()
    private val pageProgress = mutableMapOf<Int, MutableState<Float>>()
    private val pageDetails = mutableMapOf<Int, MutableState<String>>()
    private val pageSpeed = mutableMapOf<Int, MutableState<String>>()
    private val pageBitmaps = mutableMapOf<Int, Bitmap>()
    private val pageFiles = mutableMapOf<Int, File>()
    private val downloadingPages = mutableSetOf<Int>()

    sealed class ViewerState {
        object Loading : ViewerState()
        data class Success(val bitmap: Bitmap) : ViewerState()
        data class Error(val message: String) : ViewerState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        repoName = intent.getStringExtra("REPO_NAME") ?: return finish()

        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        token = sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, "") ?: ""
        orgName = sharedPreferences.getString(Constants.KEY_ORG_NAME, "") ?: ""

        // Parse photo list from JSON
        val photoListJson = intent.getStringExtra("PHOTO_LIST_JSON")
        if (photoListJson != null) {
            val type = object : TypeToken<List<GitHubFile>>() {}.type
            photoList = Gson().fromJson(photoListJson, type)
        }
        initialIndex = intent.getIntExtra("CURRENT_INDEX", 0).coerceIn(0, (photoList.size - 1).coerceAtLeast(0))

        if (photoList.isEmpty()) {
            finish()
            return
        }

        setContent {
            MemoriesTheme(darkTheme = true) {
                PhotoViewerPagerScreen()
            }
        }
    }

    // ─────────────── HD Cache Helpers ───────────────

    private fun getHdCacheDir(): File {
        val dir = File(filesDir, "hd_cache/$repoName")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getCachedHdFile(fileName: String): File {
        return File(getHdCacheDir(), fileName)
    }

    // ─────────────── State Accessors ───────────────

    @Composable
    private fun getPageState(index: Int): MutableState<ViewerState> {
        return pageStates.getOrPut(index) { mutableStateOf(ViewerState.Loading) }
    }

    @Composable
    private fun getPageProgress(index: Int): MutableState<Float> {
        return pageProgress.getOrPut(index) { mutableStateOf(0f) }
    }

    @Composable
    private fun getPageDetails(index: Int): MutableState<String> {
        return pageDetails.getOrPut(index) { mutableStateOf("") }
    }

    @Composable
    private fun getPageSpeed(index: Int): MutableState<String> {
        return pageSpeed.getOrPut(index) { mutableStateOf("") }
    }

    // ─────────────── Main Pager Screen ───────────────

    @OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    fun PhotoViewerPagerScreen() {
        val pagerState = rememberPagerState(
            initialPage = initialIndex,
            pageCount = { photoList.size }
        )
        val currentPage by remember { derivedStateOf { pagerState.currentPage } }
        val currentFile = photoList.getOrNull(currentPage)
        val currentFileName = currentFile?.name ?: "Photo"

        var controlsVisible by remember { mutableStateOf(true) }
        var showInfoDialog by remember { mutableStateOf(false) }

        // Track which pages need loading
        LaunchedEffect(currentPage) {
            // Load current page and adjacent pages
            for (offset in -1..1) {
                val pageIndex = currentPage + offset
                if (pageIndex in photoList.indices) {
                    loadPageIfNeeded(pageIndex)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding(),
        ) {
            // ── Horizontal Pager ──
            HorizontalPager(
                state = pagerState,
                beyondBoundsPageCount = 1,
                key = { photoList[it].path },
                userScrollEnabled = true,
            ) { pageIndex ->
                val state by getPageState(pageIndex)
                val progress by getPageProgress(pageIndex)
                val details by getPageDetails(pageIndex)
                val speed by getPageSpeed(pageIndex)

                when (val s = state) {
                    is ViewerState.Loading -> {
                        DownloadProgressOverlay(progress, details, speed)
                    }
                    is ViewerState.Success -> {
                        ZoomableImage(
                            bitmap = s.bitmap,
                            contentDescription = photoList[pageIndex].name,
                            onSingleTap = { controlsVisible = !controlsVisible },
                            onLongPress = { showInfoDialog = true },
                            onSwipeDown = { finish() },
                        )
                    }
                    is ViewerState.Error -> {
                        ErrorOverlay(s.message)
                    }
                }
            }

            // ── Top bar ──
            AnimatedVisibility(
                visible = controlsVisible && (getPageState(currentPage).value !is ViewerState.Loading),
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xE6101020),
                    shadowElevation = 12.dp,
                    tonalElevation = 4.dp,
                ) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    currentFileName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                                Text(
                                    "${currentPage + 1} / ${photoList.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                            }
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

            // ── Bottom action bar ──
            AnimatedVisibility(
                visible = controlsVisible && (getPageState(currentPage).value is ViewerState.Success),
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xE6101020),
                    shadowElevation = 16.dp,
                    tonalElevation = 4.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.12f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ActionChip(
                            icon = Icons.Outlined.Download,
                            label = "Save",
                            onClick = { saveImageToGallery(currentPage) },
                        )
                        ActionChip(
                            icon = Icons.Outlined.Share,
                            label = "Share",
                            onClick = { shareImage(currentPage) },
                        )
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
                bitmap = pageBitmaps[currentPage],
                fileName = currentFileName,
                fileSize = pageFiles[currentPage]?.length(),
                onDismiss = { showInfoDialog = false },
            )
        }
    }

    // ─────────────── Load Page ───────────────

    private fun loadPageIfNeeded(index: Int) {
        if (index !in photoList.indices) return
        // Already loaded or loading
        val existingState = pageStates[index]?.value
        if (existingState is ViewerState.Success || downloadingPages.contains(index)) return

        downloadingPages.add(index)
        val file = photoList[index]
        val fileName = file.name
        val filePath = file.originalPath ?: file.path.replaceFirst(Regex("^thumbnails/", RegexOption.IGNORE_CASE), "")
        val encodedPath = filePath.split('/').joinToString("/") { android.net.Uri.encode(it) }

        val originalUrl = if (token.isNotEmpty()) {
            "https://api.github.com/repos/$orgName/$repoName/contents/$encodedPath"
        } else {
            "https://raw.githubusercontent.com/$orgName/$repoName/HEAD/$encodedPath"
        }

        // Ensure state holders exist
        if (pageStates[index] == null) pageStates[index] = mutableStateOf(ViewerState.Loading)
        if (pageProgress[index] == null) pageProgress[index] = mutableStateOf(0f)
        if (pageDetails[index] == null) pageDetails[index] = mutableStateOf("")
        if (pageSpeed[index] == null) pageSpeed[index] = mutableStateOf("")

        // Check HD cache first
        val cachedFile = getCachedHdFile(fileName)
        if (cachedFile.exists() && cachedFile.length() > 0) {
            lifecycleScope.launch(Dispatchers.IO) {
                val bitmap = BitmapFactory.decodeFile(cachedFile.absolutePath)
                if (bitmap != null) {
                    pageBitmaps[index] = bitmap
                    pageFiles[index] = cachedFile
                    pageStates[index]?.value = ViewerState.Success(bitmap)
                } else {
                    // Corrupted cache, delete and re-download
                    cachedFile.delete()
                    downloadImage(index, originalUrl)
                }
                downloadingPages.remove(index)
            }
            return
        }

        downloadImage(index, originalUrl)
    }

    // ─────────────── Download Image ───────────────

    private fun downloadImage(index: Int, urlString: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            pageStates[index]?.value = ViewerState.Loading
            pageDetails[index]?.value = "Connecting..."

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

                // Download to a temp file first, then move to cache
                val tempFile = File(cacheDir, "temp_viewer_${index}_${System.currentTimeMillis()}.jpg")
                val output = FileOutputStream(tempFile)

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

                        pageProgress[index]?.value = progress
                        pageDetails[index]?.value = "$downloadedStr / $totalStr"
                        pageSpeed[index]?.value = speedStr

                        lastUpdate = currentTime
                        lastBytes = total
                    }
                }

                output.flush()
                output.close()
                input.close()

                // Copy to HD cache
                val fileName = photoList[index].name
                val cacheFile = getCachedHdFile(fileName)
                tempFile.copyTo(cacheFile, overwrite = true)
                tempFile.delete()

                val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                pageBitmaps[index] = bitmap
                pageFiles[index] = cacheFile

                if (bitmap != null) {
                    pageStates[index]?.value = ViewerState.Success(bitmap)
                } else {
                    pageStates[index]?.value = ViewerState.Error("Failed to decode image")
                }
            } catch (e: Exception) {
                pageStates[index]?.value = ViewerState.Error(e.message ?: "Unknown error")
            } finally {
                downloadingPages.remove(index)
            }
        }
    }

    // ─────────────── Zoomable Image ───────────────

    private fun calculateFitImageSize(
        bitmapWidth: Int,
        bitmapHeight: Int,
        containerWidth: Int,
        containerHeight: Int,
    ): Pair<Float, Float> {
        if (containerWidth <= 0 || containerHeight <= 0) return Pair(0f, 0f)
        val imageAspect = bitmapWidth.toFloat() / bitmapHeight.toFloat()
        val containerAspect = containerWidth.toFloat() / containerHeight.toFloat()
        return if (imageAspect > containerAspect) {
            val displayW = containerWidth.toFloat()
            val displayH = containerWidth.toFloat() / imageAspect
            Pair(displayW, displayH)
        } else {
            val displayH = containerHeight.toFloat()
            val displayW = containerHeight.toFloat() * imageAspect
            Pair(displayW, displayH)
        }
    }

    @Composable
    fun ZoomableImage(
        bitmap: Bitmap,
        contentDescription: String,
        onSingleTap: () -> Unit,
        onLongPress: () -> Unit,
        onSwipeDown: () -> Unit,
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        val animatedScale = remember { Animatable(1f) }
        val animatedOffsetX = remember { Animatable(0f) }
        val animatedOffsetY = remember { Animatable(0f) }

        var containerSize by remember { mutableStateOf(IntSize.Zero) }

        var showZoomIndicator by remember { mutableStateOf(false) }
        var zoomLevel by remember { mutableFloatStateOf(1f) }



        val coroutineScope = rememberCoroutineScope()

        val minScale = 1f
        val maxScale = 8f
        val doubleTapScale = 3f

        LaunchedEffect(animatedScale.value) { scale = animatedScale.value }
        LaunchedEffect(animatedOffsetX.value) { offsetX = animatedOffsetX.value }
        LaunchedEffect(animatedOffsetY.value) { offsetY = animatedOffsetY.value }

        LaunchedEffect(showZoomIndicator) {
            if (showZoomIndicator) {
                delay(1200)
                showZoomIndicator = false
            }
        }

        fun calculatePanBounds(currentScale: Float): Pair<Float, Float> {
            val (imgW, imgH) = calculateFitImageSize(
                bitmap.width, bitmap.height,
                containerSize.width, containerSize.height
            )
            val overflowX = max(0f, (imgW * currentScale - containerSize.width) / 2f)
            val overflowY = max(0f, (imgH * currentScale - containerSize.height) / 2f)
            return Pair(overflowX, overflowY)
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
                                    launch { animatedScale.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
                                    launch { animatedOffsetX.animateTo(0f, tween(350, easing = FastOutSlowInEasing)) }
                                    launch { animatedOffsetY.animateTo(0f, tween(350, easing = FastOutSlowInEasing)) }
                                    zoomLevel = 1f
                                } else {
                                    val newScale = doubleTapScale
                                    val centerX = containerSize.width / 2f
                                    val centerY = containerSize.height / 2f
                                    val focusX = tapOffset.x - centerX
                                    val focusY = tapOffset.y - centerY
                                    val rawOffsetX = -focusX * (newScale - 1f)
                                    val rawOffsetY = -focusY * (newScale - 1f)

                                    val (maxX, maxY) = calculatePanBounds(newScale)
                                    val clampedX = rawOffsetX.coerceIn(-maxX, maxX)
                                    val clampedY = rawOffsetY.coerceIn(-maxY, maxY)

                                    launch { animatedScale.animateTo(newScale, tween(350, easing = FastOutSlowInEasing)) }
                                    launch { animatedOffsetX.animateTo(clampedX, tween(350, easing = FastOutSlowInEasing)) }
                                    launch { animatedOffsetY.animateTo(clampedY, tween(350, easing = FastOutSlowInEasing)) }
                                    zoomLevel = newScale
                                }
                                showZoomIndicator = true
                            }
                        },
                        onLongPress = { onLongPress() },
                    )
                }
                .pointerInput(Unit) {
                    detectContentTransformGestures { _, pan, zoom, _, changes ->
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)

                        // ── Pager Compatibility ──
                        // If we are at 100% scale (or less) and not zooming,
                        // and the user is swiping horizontally, we let the Pager handle it.
                        if (scale == 1f && newScale == 1f && changes.size == 1) {
                            val isHorizontalDrag = abs(pan.x) > abs(pan.y)
                            if (isHorizontalDrag) {
                                // Do not consume, let Pager handle this
                                return@detectContentTransformGestures
                            } else {
                                // Vertical drag? User requested "shouldn't be able to move vertically"
                                // We consume it to prevent any weird behavior, but don't apply it.
                                changes.forEach { it.consume() }
                                return@detectContentTransformGestures
                            }
                        }

                        val (maxX, maxY) = calculatePanBounds(newScale)

                        val newOffsetX = (offsetX + pan.x * newScale / scale).coerceIn(-maxX, maxX)
                        val newOffsetY = (offsetY + pan.y * newScale / scale).coerceIn(-maxY, maxY)

                        scale = newScale
                        offsetX = newOffsetX
                        offsetY = newOffsetY

                        zoomLevel = newScale

                        coroutineScope.launch {
                            animatedScale.snapTo(newScale)
                            animatedOffsetX.snapTo(newOffsetX)
                            animatedOffsetY.snapTo(newOffsetY)
                        }

                        showZoomIndicator = true
                        
                        // Consume events since we handled the transformation
                        changes.forEach {
                            if (it.positionChanged()) {
                                it.consume()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                         // alpha = dismissAlpha (removed)
                    },
            )
        }
    }

    // ─────────────── Image Info Dialog ───────────────

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

    // ─────────────── Action Chip ───────────────

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
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color(0xFFB8C3FF),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }

    // ─────────────── Download Progress ───────────────

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

    // ─────────────── Error Overlay ───────────────

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

    // ─────────────── Custom Gesture Detector ───────────────

    suspend fun PointerInputScope.detectContentTransformGestures(
        onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float, changes: List<PointerInputChange>) -> Unit
    ) {
        awaitEachGesture {
            var rotation = 0f
            var zoom = 1f
            var pan = Offset.Zero
            var pastTouchSlop = false
            val touchSlop = viewConfiguration.touchSlop

            do {
                val event = awaitPointerEvent()
                val canceled = event.changes.any { it.isConsumed }
                if (canceled) break

                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    if (zoomChange != 1f ||
                        rotationChange != 0f ||
                        panChange != Offset.Zero
                    ) {
                        onGesture(
                            event.calculateCentroid(useCurrent = false),
                            panChange,
                            zoomChange,
                            rotationChange,
                            event.changes
                        )
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }


    // ─────────────── Utils ───────────────

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    // ─────────────── Save / Share ───────────────

    private fun saveImageToGallery(pageIndex: Int) {
        val bitmap = pageBitmaps[pageIndex] ?: return
        val fileName = photoList.getOrNull(pageIndex)?.name ?: "image_${System.currentTimeMillis()}.jpg"

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

    private fun shareImage(pageIndex: Int) {
        val file = pageFiles[pageIndex] ?: return
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
