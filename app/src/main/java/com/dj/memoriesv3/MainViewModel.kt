package com.dj.memoriesv3

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val _reposState = MutableStateFlow<UiState<List<GitHubRepo>>>(UiState.Loading)
    val reposState: StateFlow<UiState<List<GitHubRepo>>> = _reposState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isCreatingAlbum = MutableStateFlow(false)
    val isCreatingAlbum: StateFlow<Boolean> = _isCreatingAlbum

    private val _createAlbumResult = MutableStateFlow<CreateAlbumResult?>(null)
    val createAlbumResult: StateFlow<CreateAlbumResult?> = _createAlbumResult

    private val _isPerformingAction = MutableStateFlow(false)
    val isPerformingAction: StateFlow<Boolean> = _isPerformingAction

    private val _repoActionResult = MutableStateFlow<RepoActionResult?>(null)
    val repoActionResult: StateFlow<RepoActionResult?> = _repoActionResult

    private var fullRepoList: List<GitHubRepo> = emptyList()
    private var hasLoadedOnce = false
    private var lastOrgName: String? = null
    private var lastToken: String? = null

    /**
     * Called from onResume. Only loads if never loaded before or if org/token changed.
     */
    fun refreshIfNeeded(orgName: String, token: String) {
        if (!hasLoadedOnce || orgName != lastOrgName || token != lastToken) {
            loadRepositories(orgName, token)
        }
    }

    /**
     * Force refresh from the manual refresh button.
     */
    fun forceRefresh(orgName: String, token: String) {
        loadRepositories(orgName, token)
    }

    private fun loadRepositories(orgName: String, token: String) {
        lastOrgName = orgName
        lastToken = token

        viewModelScope.launch {
            // If we already loaded, show pull-to-refresh style instead of full loading
            if (hasLoadedOnce) {
                _isRefreshing.value = true
            } else {
                _reposState.value = UiState.Loading
            }

            val allRepos = mutableListOf<GitHubRepo>()
            var page = 1
            var hasMore = true

            try {
                while (hasMore) {
                    val repos = GitHubRepository.getOrgRepos(orgName, token, page)
                    allRepos.addAll(repos)
                    if (repos.size < 100) {
                        hasMore = false
                    } else {
                        page++
                    }
                }
                fullRepoList = allRepos.sortedByDescending { it.updatedAt }
                _reposState.value = UiState.Success(fullRepoList)
                hasLoadedOnce = true
            } catch (e: Exception) {
                _reposState.value = UiState.Error(e.message ?: "Unknown error")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun filterRepositories(query: String) {
        val current = _reposState.value
        if (current is UiState.Success || current is UiState.Loading) {
            if (fullRepoList.isNotEmpty()) {
                val filtered = if (query.isEmpty()) fullRepoList else fullRepoList.filter { it.name.contains(query, true) }
                _reposState.value = UiState.Success(filtered)
            }
        }
    }

    /**
     * Creates a new album (GitHub repo) with a thumbnails folder and README.
     */
    fun createAlbum(orgName: String, token: String, albumName: String, description: String) {
        viewModelScope.launch {
            _isCreatingAlbum.value = true
            _createAlbumResult.value = null

            try {
                val createdRepo = GitHubRepository.createAlbum(
                    org = orgName,
                    repoName = albumName,
                    description = description,
                    token = token
                )
                _createAlbumResult.value = CreateAlbumResult.Success(createdRepo.name)

                // Auto-refresh the repo list so the new album appears
                forceRefresh(orgName, token)
            } catch (e: Exception) {
                _createAlbumResult.value = CreateAlbumResult.Error(
                    e.message ?: "Failed to create album"
                )
            } finally {
                _isCreatingAlbum.value = false
            }
        }
    }

    fun clearCreateResult() {
        _createAlbumResult.value = null
    }

    /**
     * Delete a repository.
     */
    fun deleteRepo(orgName: String, token: String, repoName: String) {
        viewModelScope.launch {
            _isPerformingAction.value = true
            _repoActionResult.value = null

            try {
                val success = GitHubRepository.deleteRepo(orgName, repoName, token)
                if (success) {
                    _repoActionResult.value = RepoActionResult.Success("Album \"$repoName\" deleted successfully")
                    forceRefresh(orgName, token)
                } else {
                    _repoActionResult.value = RepoActionResult.Error("Failed to delete album. Check permissions.")
                }
            } catch (e: Exception) {
                _repoActionResult.value = RepoActionResult.Error(e.message ?: "Failed to delete album")
            } finally {
                _isPerformingAction.value = false
            }
        }
    }

    /**
     * Rename a repository.
     */
    fun renameRepo(orgName: String, token: String, oldName: String, newName: String) {
        viewModelScope.launch {
            _isPerformingAction.value = true
            _repoActionResult.value = null

            try {
                val success = GitHubRepository.renameRepo(orgName, oldName, newName, token)
                if (success) {
                    _repoActionResult.value = RepoActionResult.Success("Album renamed to \"$newName\"")
                    forceRefresh(orgName, token)
                } else {
                    _repoActionResult.value = RepoActionResult.Error("Failed to rename album. Check permissions.")
                }
            } catch (e: Exception) {
                _repoActionResult.value = RepoActionResult.Error(e.message ?: "Failed to rename album")
            } finally {
                _isPerformingAction.value = false
            }
        }
    }

    fun clearRepoActionResult() {
        _repoActionResult.value = null
    }
}

sealed class CreateAlbumResult {
    data class Success(val albumName: String) : CreateAlbumResult()
    data class Error(val message: String) : CreateAlbumResult()
}

sealed class RepoActionResult {
    data class Success(val message: String) : RepoActionResult()
    data class Error(val message: String) : RepoActionResult()
}