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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.dj.memoriesv3.ui.theme.MemoriesTheme
import kotlinx.coroutines.Dispatchers
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PhotoViewerScreen(fileName: String) {
        val state by viewerState.collectAsState()
        val progress by downloadProgress.collectAsState()
        val details by downloadDetails.collectAsState()
        val speed by downloadSpeed.collectAsState()
        var controlsVisible by remember { mutableStateOf(true) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible }
                    )
                },
        ) {
            // ── Image ──
            when (val s = state) {
                is ViewerState.Loading -> {
                    // Download Progress
                    DownloadProgressOverlay(progress, details, speed)
                }
                is ViewerState.Success -> {
                    androidx.compose.foundation.Image(
                        bitmap = s.bitmap.asImageBitmap(),
                        contentDescription = fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is ViewerState.Error -> {
                    ErrorOverlay(s.message)
                }
            }

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
                    }
                }
            }
        }
    }

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
