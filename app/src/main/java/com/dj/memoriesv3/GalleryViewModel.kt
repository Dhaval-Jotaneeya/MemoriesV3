package com.dj.memoriesv3

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL

data class UploadProgress(
    val isUploading: Boolean = false,
    val totalFiles: Int = 0,
    val completedFiles: Int = 0,
    val currentFileName: String = "",
    val currentStep: String = "", // e.g. "Compressing thumbnail", "Uploading original", etc.
    val overallProgress: Float = 0f, // 0.0 to 1.0
    val errors: List<String> = emptyList(),
    val isComplete: Boolean = false
)

data class BatchActionProgress(
    val isRunning: Boolean = false,
    val totalFiles: Int = 0,
    val completedFiles: Int = 0,
    val currentFileName: String = "",
    val currentStep: String = "",
    val overallProgress: Float = 0f,
    val errors: List<String> = emptyList(),
    val isComplete: Boolean = false,
    val actionType: String = "" // "delete" or "download"
)

class GalleryViewModel : ViewModel() {

    private val _imagesState = MutableStateFlow<UiState<List<GitHubFile>>>(UiState.Loading)
    val imagesState: StateFlow<UiState<List<GitHubFile>>> = _imagesState

    private val _statusMessage = MutableStateFlow<String>("")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _uploadProgress = MutableStateFlow(UploadProgress())
    val uploadProgress: StateFlow<UploadProgress> = _uploadProgress

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    private val _batchActionProgress = MutableStateFlow(BatchActionProgress())
    val batchActionProgress: StateFlow<BatchActionProgress> = _batchActionProgress

    private var currentOrganization: String = ""
    private var currentRepoName: String = ""
    private var currentToken: String = ""

    fun loadImages(organization: String, repoName: String, token: String) {
        currentOrganization = organization
        currentRepoName = repoName
        currentToken = token

        viewModelScope.launch {
            try {
                _imagesState.value = UiState.Loading
                _statusMessage.value = "Locating thumbnails..."

                // Step 1: Get Root Tree
                val rootTree = GitHubRepository.getRepoTree(organization, repoName, "HEAD", token, 0)
                val rootItems = rootTree.tree
                val thumbDir = rootItems.find { it.path.equals("thumbnails", ignoreCase = true) && it.type == "tree" }

                if (thumbDir == null) {
                    _imagesState.value = UiState.Success(emptyList())
                    _statusMessage.value = ""
                    return@launch
                }

                // Map root files for case-insensitive matching
                val rootFilesMap = rootItems
                    .filter { it.type == "blob" }
                    .associate { it.path.lowercase() to it.path }

                _statusMessage.value = "Fetching image list..."
                // Use recursive=0 to avoid timeouts on large trees. Thumbnails folder is flat.
                val thumbTree = GitHubRepository.getRepoTree(organization, repoName, thumbDir.sha, token, 0)
                
                if (thumbTree.truncated) {
                    _statusMessage.value = "Warning: File list truncated by GitHub."
                }

                _statusMessage.value = "Processing files..."
                val images = withContext(Dispatchers.Default) {
                    thumbTree.tree.filter { item ->
                        item.type == "blob" &&
                                (item.path.endsWith(".jpg", true) ||
                                 item.path.endsWith(".png", true) ||
                                 item.path.endsWith(".jpeg", true) ||
                                 item.path.endsWith(".webp", true))
                    }.map { item ->
                        val fullPath = "${thumbDir.path}/${item.path}"
                        val simpleName = item.path.substringAfterLast('/')
                        val originalPath = rootFilesMap[simpleName.lowercase()]
                        GitHubFile(
                            name = simpleName,
                            path = fullPath,
                            download_url = "https://raw.githubusercontent.com/$organization/$repoName/HEAD/$fullPath",
                            url = item.url,
                            originalPath = originalPath
                        )
                    }
                }
                _imagesState.value = UiState.Success(images)
                _statusMessage.value = ""
            } catch (e: Exception) {
                _imagesState.value = UiState.Error(e.message ?: "Unknown error")
                _statusMessage.value = ""
            }
        }
    }

    // ── Selection Management ──

    fun toggleSelection(filePath: String) {
        val current = _selectedFiles.value.toMutableSet()
        if (current.contains(filePath)) {
            current.remove(filePath)
        } else {
            current.add(filePath)
        }
        _selectedFiles.value = current
        _isSelectionMode.value = current.isNotEmpty()
    }

    fun selectAll(files: List<GitHubFile>) {
        _selectedFiles.value = files.map { it.path }.toSet()
        _isSelectionMode.value = true
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
        _isSelectionMode.value = false
    }

    fun isSelected(filePath: String): Boolean {
        return _selectedFiles.value.contains(filePath)
    }

    // ── Delete Selected Photos ──

    fun deleteSelectedPhotos() {
        if (currentOrganization.isEmpty() || currentRepoName.isEmpty() || currentToken.isEmpty()) return

        val currentState = _imagesState.value
        if (currentState !is UiState.Success) return

        val selectedPaths = _selectedFiles.value.toList()
        val filesToDelete = currentState.data.filter { selectedPaths.contains(it.path) }

        if (filesToDelete.isEmpty()) return

        viewModelScope.launch {
            val totalFiles = filesToDelete.size
            val errors = mutableListOf<String>()

            _batchActionProgress.value = BatchActionProgress(
                isRunning = true,
                totalFiles = totalFiles,
                currentStep = "Starting deletion...",
                actionType = "delete"
            )

            for ((index, file) in filesToDelete.withIndex()) {
                try {
                    _batchActionProgress.value = _batchActionProgress.value.copy(
                        currentFileName = file.name,
                        currentStep = "Deleting ${file.name}...",
                        overallProgress = index.toFloat() / totalFiles
                    )

                    // Delete the thumbnail
                    val thumbSha = withContext(Dispatchers.IO) {
                        GitHubRepository.getFileSha(
                            currentOrganization, currentRepoName, file.path, currentToken
                        )
                    }
                    if (thumbSha != null) {
                        withContext(Dispatchers.IO) {
                            GitHubRepository.deleteFile(
                                currentOrganization, currentRepoName, file.path, thumbSha,
                                currentToken, "Delete thumbnail ${file.name}"
                            )
                        }
                    }

                    // Delete the original file if it exists
                    val originalPath = file.originalPath
                    if (originalPath != null) {
                        _batchActionProgress.value = _batchActionProgress.value.copy(
                            currentStep = "Deleting original ${file.name}..."
                        )
                        val origSha = withContext(Dispatchers.IO) {
                            GitHubRepository.getFileSha(
                                currentOrganization, currentRepoName, originalPath, currentToken
                            )
                        }
                        if (origSha != null) {
                            withContext(Dispatchers.IO) {
                                GitHubRepository.deleteFile(
                                    currentOrganization, currentRepoName, originalPath, origSha,
                                    currentToken, "Delete ${file.name}"
                                )
                            }
                        }
                    }

                    _batchActionProgress.value = _batchActionProgress.value.copy(
                        completedFiles = index + 1,
                        overallProgress = (index + 1).toFloat() / totalFiles,
                        errors = errors.toList()
                    )

                } catch (e: Exception) {
                    errors.add("${file.name}: ${e.message}")
                    _batchActionProgress.value = _batchActionProgress.value.copy(
                        completedFiles = index + 1,
                        overallProgress = (index + 1).toFloat() / totalFiles,
                        errors = errors.toList()
                    )
                }
            }

            _batchActionProgress.value = _batchActionProgress.value.copy(
                isRunning = false,
                isComplete = true,
                currentStep = if (errors.isEmpty()) "All files deleted!" else "Deletion finished with ${errors.size} error(s)",
                overallProgress = 1f,
                errors = errors.toList()
            )

            clearSelection()

            // Refresh gallery
            if (currentOrganization.isNotEmpty() && currentRepoName.isNotEmpty() && currentToken.isNotEmpty()) {
                loadImages(currentOrganization, currentRepoName, currentToken)
            }
        }
    }

    // ── Download Selected Photos (Original images) ──

    fun downloadSelectedPhotos(context: Context) {
        if (currentOrganization.isEmpty() || currentRepoName.isEmpty() || currentToken.isEmpty()) return

        val currentState = _imagesState.value
        if (currentState !is UiState.Success) return

        val selectedPaths = _selectedFiles.value.toList()
        val filesToDownload = currentState.data.filter { selectedPaths.contains(it.path) }

        if (filesToDownload.isEmpty()) return

        viewModelScope.launch {
            val totalFiles = filesToDownload.size
            val errors = mutableListOf<String>()

            _batchActionProgress.value = BatchActionProgress(
                isRunning = true,
                totalFiles = totalFiles,
                currentStep = "Starting download...",
                actionType = "download"
            )

            for ((index, file) in filesToDownload.withIndex()) {
                try {
                    val originalPath = file.originalPath ?: file.name
                    val downloadUrl = "https://raw.githubusercontent.com/$currentOrganization/$currentRepoName/HEAD/$originalPath"

                    _batchActionProgress.value = _batchActionProgress.value.copy(
                        currentFileName = file.name,
                        currentStep = "Downloading ${file.name}...",
                        overallProgress = index.toFloat() / totalFiles
                    )

                    // Download the original image bytes
                    val imageBytes = withContext(Dispatchers.IO) {
                        val connection = URL(downloadUrl).openConnection()
                        connection.setRequestProperty("Authorization", "Bearer $currentToken")
                        connection.connectTimeout = 30000
                        connection.readTimeout = 60000
                        connection.getInputStream().use { it.readBytes() }
                    }

                    // Save to device
                    withContext(Dispatchers.IO) {
                        saveImageToGallery(context, imageBytes, file.name)
                    }

                    _batchActionProgress.value = _batchActionProgress.value.copy(
                        completedFiles = index + 1,
                        overallProgress = (index + 1).toFloat() / totalFiles,
                        errors = errors.toList()
                    )

                } catch (e: Exception) {
                    errors.add("${file.name}: ${e.message}")
                    _batchActionProgress.value = _batchActionProgress.value.copy(
                        completedFiles = index + 1,
                        overallProgress = (index + 1).toFloat() / totalFiles,
                        errors = errors.toList()
                    )
                }
            }

            _batchActionProgress.value = _batchActionProgress.value.copy(
                isRunning = false,
                isComplete = true,
                currentStep = if (errors.isEmpty()) "All photos saved!" else "Download finished with ${errors.size} error(s)",
                overallProgress = 1f,
                errors = errors.toList()
            )

            clearSelection()
        }
    }

    private fun saveImageToGallery(context: Context, imageBytes: ByteArray, fileName: String) {
        val mimeType = when {
            fileName.endsWith(".png", true) -> "image/png"
            fileName.endsWith(".webp", true) -> "image/webp"
            else -> "image/jpeg"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for Android 10+
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Memories")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
            ) ?: throw Exception("Failed to create MediaStore entry")

            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(imageBytes)
            } ?: throw Exception("Failed to write to MediaStore")

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
        } else {
            // Legacy: write directly to Pictures directory
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Memories")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            file.writeBytes(imageBytes)

            // Notify media scanner
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }
    }

    fun dismissBatchProgress() {
        _batchActionProgress.value = BatchActionProgress()
    }

    /**
     * Upload multiple images using the Git Data API with parallel blob uploads
     * and size-based dynamic batching.
     *
     * Strategy:
     * - Up to 3 blobs upload in PARALLEL via Semaphore (3x faster than sequential)
     * - Each blob is retried 3x with exponential backoff
     * - A commit is created when cumulative blob size reaches ~100MB
     * - Adapts to image sizes: small photos → big batches, large photos → small batches
     * - HEAD SHA is re-fetched before each commit to avoid stale-ref errors
     * - At most ~3 images in memory at a time (bounded by semaphore)
     */
    companion object {
        /** Max cumulative blob size per commit (~100MB). */
        private const val BATCH_SIZE_LIMIT_BYTES = 100L * 1024 * 1024
        /** Max concurrent blob uploads. 3 is safe for GitHub's secondary rate limit. */
        private const val PARALLEL_UPLOADS = 3
    }

    /**
     * Data class to hold the result of a single file's parallel blob upload.
     */
    private data class BlobUploadResult(
        val originalTreeItem: JSONObject,
        val thumbnailTreeItem: JSONObject,
        val totalBytes: Long
    )

    fun uploadImages(context: Context, imageUris: List<Uri>) {
        if (currentOrganization.isEmpty() || currentRepoName.isEmpty() || currentToken.isEmpty()) return

        viewModelScope.launch {
            val totalFiles = imageUris.size
            val errors = mutableListOf<String>()
            val uploadSemaphore = Semaphore(PARALLEL_UPLOADS)

            _uploadProgress.value = UploadProgress(
                isUploading = true,
                totalFiles = totalFiles,
                completedFiles = 0,
                currentStep = "Preparing $totalFiles photos...",
                overallProgress = 0f
            )

            // Resolve branch once (handles main vs master)
            val actualBranch: String
            try {
                val (_, branch) = withContext(Dispatchers.IO) {
                    GitHubRepository.resolveDefaultBranch(currentOrganization, currentRepoName, currentToken)
                }
                actualBranch = branch
            } catch (e: Exception) {
                _uploadProgress.value = _uploadProgress.value.copy(
                    isUploading = false,
                    isComplete = true,
                    currentStep = "Failed to connect to repository: ${e.message}",
                    overallProgress = 0f,
                    errors = listOf("Could not resolve branch: ${e.message}")
                )
                return@launch
            }

            // ── Pre-read file sizes for smart batching (lightweight, no image bytes loaded) ──
            data class FileEntry(val uri: Uri, val index: Int, val safeFileName: String, val sizeBytes: Long)

            val fileEntries = imageUris.mapIndexedNotNull { index, uri ->
                try {
                    val fileName = getFileName(context, uri) ?: "image_${System.currentTimeMillis()}_$index.jpg"
                    val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val size = context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
                    FileEntry(uri, index, safeFileName, size)
                } catch (e: Exception) {
                    errors.add("Cannot read file #${index + 1}: ${e.message}")
                    null
                }
            }

            // ── Partition into size-based batches ──
            val batches = mutableListOf<List<FileEntry>>()
            var currentBatch = mutableListOf<FileEntry>()
            var currentBatchSize = 0L

            for (entry in fileEntries) {
                // If adding this file would exceed the limit and batch isn't empty, flush
                if (currentBatch.isNotEmpty() && currentBatchSize + entry.sizeBytes > BATCH_SIZE_LIMIT_BYTES) {
                    batches.add(currentBatch)
                    currentBatch = mutableListOf()
                    currentBatchSize = 0L
                }
                currentBatch.add(entry)
                currentBatchSize += entry.sizeBytes
            }
            if (currentBatch.isNotEmpty()) batches.add(currentBatch)

            val totalBatches = batches.size
            val completedCount = AtomicInteger(0)
            var totalBytesUploaded = 0L

            // ── Process each batch ──
            for ((batchIndex, batch) in batches.withIndex()) {
                val batchNumber = batchIndex + 1

                try {
                    _uploadProgress.value = _uploadProgress.value.copy(
                        currentStep = "Uploading batch $batchNumber/$totalBatches (${batch.size} files)..."
                    )

                    // Launch parallel blob uploads, gated by semaphore
                    val deferredResults = batch.map { entry ->
                        async(Dispatchers.IO) {
                            uploadSemaphore.withPermit {
                                uploadSingleFileBlobs(context, entry.uri, entry.safeFileName)
                            }.also {
                                // Update progress from any thread
                                val done = completedCount.incrementAndGet()
                                _uploadProgress.value = _uploadProgress.value.copy(
                                    currentFileName = entry.safeFileName,
                                    completedFiles = done,
                                    currentStep = "Uploading $done/$totalFiles" +
                                            if (totalBatches > 1) " (batch $batchNumber/$totalBatches)" else "...",
                                    overallProgress = done.toFloat() / totalFiles
                                )
                            }
                        }
                    }

                    // Await all blob uploads in this batch
                    val results = deferredResults.awaitAll()

                    // Collect tree items from successful uploads
                    val treeItems = mutableListOf<JSONObject>()
                    var batchBytes = 0L

                    for ((i, result) in results.withIndex()) {
                        if (result != null) {
                            treeItems.add(result.originalTreeItem)
                            treeItems.add(result.thumbnailTreeItem)
                            batchBytes += result.totalBytes
                        } else {
                            errors.add("${batch[i].safeFileName}: blob upload failed")
                        }
                    }

                    totalBytesUploaded += batchBytes

                    // ── Commit this batch ──
                    if (treeItems.isNotEmpty()) {
                        _uploadProgress.value = _uploadProgress.value.copy(
                            currentStep = "Committing batch $batchNumber/$totalBatches (${formatSize(batchBytes)})..."
                        )

                        // Fresh HEAD SHA for each commit
                        val headSha = GitHubRepository.retryWithBackoff(maxRetries = 2) {
                            GitHubRepository.getBranchSha(
                                currentOrganization, currentRepoName, currentToken, actualBranch
                            )
                        }

                        val baseTreeSha = GitHubRepository.retryWithBackoff(maxRetries = 2) {
                            GitHubRepository.getBaseTreeSha(
                                currentOrganization, currentRepoName, currentToken, headSha
                            )
                        }

                        val newTreeSha = GitHubRepository.retryWithBackoff(maxRetries = 2) {
                            GitHubRepository.createTree(
                                currentOrganization, currentRepoName, currentToken,
                                baseTreeSha, treeItems
                            )
                        }

                        val filesInBatch = treeItems.size / 2
                        val commitMsg = "Upload $filesInBatch photos (${formatSize(batchBytes)})"

                        val newCommitSha = GitHubRepository.retryWithBackoff(maxRetries = 2) {
                            GitHubRepository.createCommitObj(
                                currentOrganization, currentRepoName, currentToken,
                                commitMsg, newTreeSha, headSha
                            )
                        }

                        GitHubRepository.retryWithBackoff(maxRetries = 2) {
                            GitHubRepository.updateRef(
                                currentOrganization, currentRepoName, currentToken,
                                actualBranch, newCommitSha
                            )
                        }
                    }

                } catch (e: Exception) {
                    errors.add("Batch $batchNumber failed: ${e.message}")
                    // Count remaining files in this batch as processed
                    val remaining = batch.size - (completedCount.get() - batches.take(batchIndex).sumOf { it.size })
                    completedCount.addAndGet(remaining.coerceAtLeast(0))
                }
            }

            _uploadProgress.value = _uploadProgress.value.copy(
                isUploading = false,
                isComplete = true,
                completedFiles = totalFiles,
                currentStep = if (errors.isEmpty()) {
                    "All $totalFiles photos uploaded! (${formatSize(totalBytesUploaded)}, $totalBatches commit${if (totalBatches != 1) "s" else ""})"
                } else {
                    "Upload finished with ${errors.size} error(s)"
                },
                overallProgress = 1f,
                errors = errors.toList()
            )

            // Refresh gallery after upload
            if (currentOrganization.isNotEmpty() && currentRepoName.isNotEmpty() && currentToken.isNotEmpty()) {
                loadImages(currentOrganization, currentRepoName, currentToken)
            }
        }
    }

    /**
     * Upload a single file's original + thumbnail blobs to GitHub.
     * Returns BlobUploadResult on success, null on failure.
     * This is called from parallel coroutines — reads bytes, uploads, and frees memory.
     */
    private suspend fun uploadSingleFileBlobs(
        context: Context,
        uri: Uri,
        safeFileName: String
    ): BlobUploadResult? {
        return try {
            // A) Read original bytes
            val originalBytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: throw Exception("Cannot read file: $safeFileName")

            val fileSize = originalBytes.size.toLong()

            // B) Upload original blob with retry
            val originalBase64 = withContext(Dispatchers.Default) {
                android.util.Base64.encodeToString(originalBytes, android.util.Base64.NO_WRAP)
            }

            val originalBlobSha = GitHubRepository.retryWithBackoff(maxRetries = 3) {
                GitHubRepository.createBlob(
                    currentOrganization, currentRepoName, currentToken, originalBase64
                )
            }
            // originalBase64 out of scope → GC

            // C) Create thumbnail + upload blob
            val thumbnailBytes = withContext(Dispatchers.Default) {
                createThumbnail(originalBytes, maxSizeKb = 10)
            }
            // originalBytes can now be GC'd

            val thumbFileName = if (safeFileName.endsWith(".png", true) || safeFileName.endsWith(".webp", true)) {
                safeFileName.substringBeforeLast('.') + ".jpg"
            } else {
                safeFileName
            }

            val thumbnailBase64 = withContext(Dispatchers.Default) {
                android.util.Base64.encodeToString(thumbnailBytes, android.util.Base64.NO_WRAP)
            }

            val thumbBlobSha = GitHubRepository.retryWithBackoff(maxRetries = 3) {
                GitHubRepository.createBlob(
                    currentOrganization, currentRepoName, currentToken, thumbnailBase64
                )
            }

            BlobUploadResult(
                originalTreeItem = JSONObject().apply {
                    put("path", safeFileName)
                    put("mode", "100644")
                    put("type", "blob")
                    put("sha", originalBlobSha)
                },
                thumbnailTreeItem = JSONObject().apply {
                    put("path", "thumbnails/$thumbFileName")
                    put("mode", "100644")
                    put("type", "blob")
                    put("sha", thumbBlobSha)
                },
                totalBytes = fileSize + thumbnailBytes.size
            )
        } catch (e: Exception) {
            null // Caller will record the error
        }
    }

    /** Formats byte count into a human-readable string like "12.5 MB". */
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    fun dismissUploadProgress() {
        _uploadProgress.value = UploadProgress()
    }

    /**
     * Create a JPEG thumbnail from original bytes, targeting max ~10KB.
     * Progressively reduces resolution and quality until under the target.
     */
    private fun createThumbnail(originalBytes: ByteArray, maxSizeKb: Int): ByteArray {
        val targetBytes = maxSizeKb * 1024

        // Decode original dimensions without loading full bitmap
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, options)
        val origWidth = options.outWidth
        val origHeight = options.outHeight

        // Start with a reasonable thumbnail dimension
        var maxDim = 300
        var quality = 70

        while (maxDim >= 50) {
            val sampleSize = calculateInSampleSize(origWidth, origHeight, maxDim)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Smaller memory footprint
            }
            val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, decodeOptions)
                ?: throw Exception("Failed to decode image for thumbnail")

            // Scale to exact target if still larger
            val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
                if (scaled !== bitmap) bitmap.recycle()
                scaled
            } else {
                bitmap
            }

            // Try compressing at decreasing quality levels
            quality = 80
            while (quality >= 10) {
                val baos = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                val result = baos.toByteArray()
                if (result.size <= targetBytes) {
                    scaledBitmap.recycle()
                    return result
                }
                quality -= 10
            }

            scaledBitmap.recycle()
            maxDim -= 50
        }

        // Last resort: very small, very low quality
        val fallbackOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(origWidth, origHeight, 50)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val fallbackBitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, fallbackOptions)
            ?: throw Exception("Failed to create thumbnail")
        val baos = ByteArrayOutputStream()
        fallbackBitmap.compress(Bitmap.CompressFormat.JPEG, 5, baos)
        fallbackBitmap.recycle()
        return baos.toByteArray()
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqSize: Int): Int {
        var inSampleSize = 1
        if (width > reqSize || height > reqSize) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while ((halfWidth / inSampleSize) >= reqSize && (halfHeight / inSampleSize) >= reqSize) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.lastPathSegment
        }
        return name
    }
}