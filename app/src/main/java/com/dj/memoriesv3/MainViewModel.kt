package com.dj.memoriesv3

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val _reposState = MutableStateFlow<UiState<List<GitHubRepo>>>(UiState.Loading)
    val reposState: StateFlow<UiState<List<GitHubRepo>>> = _reposState

    private var fullRepoList: List<GitHubRepo> = emptyList()

    fun loadRepositories(orgName: String, token: String) {
        viewModelScope.launch {
            _reposState.value = UiState.Loading
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
            } catch (e: Exception) {
                _reposState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun filterRepositories(query: String) {
        val current = _reposState.value
        if (current is UiState.Success || current is UiState.Loading) {
            // Only filter if we have data or had data
            if (fullRepoList.isNotEmpty()) {
                val filtered = if (query.isEmpty()) fullRepoList else fullRepoList.filter { it.name.contains(query, true) }
                _reposState.value = UiState.Success(filtered)
            }
        }
    }
}