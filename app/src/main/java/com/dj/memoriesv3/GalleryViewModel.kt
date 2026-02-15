package com.dj.memoriesv3

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                val thumbTree = GitHubRepository.getRepoTree(organization, repoName, thumbDir.sha, token, 1)
                
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
     * Upload multiple images to the repository.
     * For each image:
     *  1. Read the original image bytes
     *  2. Upload the original to the repo root
     *  3. Create a compressed thumbnail (max ~10KB)
     *  4. Upload the thumbnail to thumbnails/ folder
     */
    fun uploadImages(context: Context, imageUris: List<Uri>) {
        if (currentOrganization.isEmpty() || currentRepoName.isEmpty() || currentToken.isEmpty()) return

        viewModelScope.launch {
            val totalFiles = imageUris.size
            val errors = mutableListOf<String>()

            _uploadProgress.value = UploadProgress(
                isUploading = true,
                totalFiles = totalFiles,
                completedFiles = 0,
                currentStep = "Starting upload...",
                overallProgress = 0f
            )

            for ((index, uri) in imageUris.withIndex()) {
                val fileName = getFileName(context, uri) ?: "image_${System.currentTimeMillis()}_$index.jpg"
                val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")

                try {
                    // Step 1: Read original image bytes
                    _uploadProgress.value = _uploadProgress.value.copy(
                        currentFileName = safeFileName,
                        currentStep = "Reading image...",
                        overallProgress = (index.toFloat() / totalFiles)
                    )

                    val originalBytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } ?: throw Exception("Cannot read file: $safeFileName")

                    // Step 2: Upload original to repo root
                    _uploadProgress.value = _uploadProgress.value.copy(
                        currentStep = "Uploading original (${formatSize(originalBytes.size.toLong())})...",
                        overallProgress = (index.toFloat() + 0.2f) / totalFiles
                    )

                    val originalBase64 = withContext(Dispatchers.Default) {
                        Base64.encodeToString(originalBytes, Base64.NO_WRAP)
                    }

                    val uploadOriginalSuccess = withContext(Dispatchers.IO) {
                        GitHubRepository.uploadFile(
                            owner = currentOrganization,
                            repo = currentRepoName,
                            filePath = safeFileName,
                            token = currentToken,
                            base64Content = originalBase64,
                            commitMessage = "Add $safeFileName"
                        )
                    }

                    if (!uploadOriginalSuccess) {
                        errors.add("$safeFileName: Failed to upload original")
                        _uploadProgress.value = _uploadProgress.value.copy(
                            completedFiles = index + 1,
                            overallProgress = ((index + 1).toFloat() / totalFiles),
                            errors = errors.toList()
                        )
                        continue
                    }

                    // Step 3: Create compressed thumbnail (max ~10KB)
                    _uploadProgress.value = _uploadProgress.value.copy(
                        currentStep = "Creating thumbnail...",
                        overallProgress = (index.toFloat() + 0.5f) / totalFiles
                    )

                    val thumbnailBytes = withContext(Dispatchers.Default) {
                        createThumbnail(originalBytes, maxSizeKb = 10)
                    }

                    // Step 4: Upload thumbnail to thumbnails/ folder
                    _uploadProgress.value = _uploadProgress.value.copy(
                        currentStep = "Uploading thumbnail (${formatSize(thumbnailBytes.size.toLong())})...",
                        overallProgress = (index.toFloat() + 0.75f) / totalFiles
                    )

                    val thumbnailBase64 = withContext(Dispatchers.Default) {
                        Base64.encodeToString(thumbnailBytes, Base64.NO_WRAP)
                    }

                    // Use .jpg extension for thumbnails since we compress as JPEG
                    val thumbFileName = if (safeFileName.endsWith(".png", true) || safeFileName.endsWith(".webp", true)) {
                        safeFileName.substringBeforeLast('.') + ".jpg"
                    } else {
                        safeFileName
                    }

                    val uploadThumbSuccess = withContext(Dispatchers.IO) {
                        GitHubRepository.uploadFile(
                            owner = currentOrganization,
                            repo = currentRepoName,
                            filePath = "thumbnails/$thumbFileName",
                            token = currentToken,
                            base64Content = thumbnailBase64,
                            commitMessage = "Add thumbnail for $safeFileName"
                        )
                    }

                    if (!uploadThumbSuccess) {
                        errors.add("$safeFileName: Failed to upload thumbnail")
                    }

                    _uploadProgress.value = _uploadProgress.value.copy(
                        completedFiles = index + 1,
                        overallProgress = ((index + 1).toFloat() / totalFiles),
                        errors = errors.toList()
                    )

                } catch (e: Exception) {
                    errors.add("$safeFileName: ${e.message}")
                    _uploadProgress.value = _uploadProgress.value.copy(
                        completedFiles = index + 1,
                        overallProgress = ((index + 1).toFloat() / totalFiles),
                        errors = errors.toList()
                    )
                }
            }

            _uploadProgress.value = _uploadProgress.value.copy(
                isUploading = false,
                isComplete = true,
                currentStep = if (errors.isEmpty()) "All uploads complete!" else "Upload finished with ${errors.size} error(s)",
                overallProgress = 1f,
                errors = errors.toList()
            )

            // Refresh gallery after upload
            if (currentOrganization.isNotEmpty() && currentRepoName.isNotEmpty() && currentToken.isNotEmpty()) {
                loadImages(currentOrganization, currentRepoName, currentToken)
            }
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

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.1f MB", mb)
    }
}