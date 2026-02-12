package com.dj.memoriesv3

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel : ViewModel() {

    private val _imagesState = MutableStateFlow<UiState<List<GitHubFile>>>(UiState.Loading)
    val imagesState: StateFlow<UiState<List<GitHubFile>>> = _imagesState

    private val _statusMessage = MutableStateFlow<String>("")
    val statusMessage: StateFlow<String> = _statusMessage

    fun loadImages(organization: String, repoName: String, token: String) {
        viewModelScope.launch {
            try {
                _imagesState.value = UiState.Loading
                _statusMessage.value = "Locating thumbnails..."

                // Step 1: Get Root Tree
                val rootTree = GitHubRepository.getRepoTree(organization, repoName, "HEAD", token, 0)
                val rootItems = rootTree.tree
                val thumbDir = rootItems.find { it.path.equals("thumbnails", ignoreCase = true) && it.type == "tree" }

                if (thumbDir == null) {
                    _imagesState.value = UiState.Error("No 'thumbnails' folder found.")
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
                                 item.path.endsWith(".jpeg", true))
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
}