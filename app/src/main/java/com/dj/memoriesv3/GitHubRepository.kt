package com.dj.memoriesv3

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object GitHubRepository {

    private const val BASE_URL = "https://api.github.com/"

    private val service: GitHubService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubService::class.java)
    }

    suspend fun getOrgRepos(org: String, token: String, page: Int): List<GitHubRepo> {
        return service.getOrgRepos(org, "Bearer $token", 100, page)
    }

    suspend fun getRepoTree(
        owner: String,
        repo: String,
        branch: String,
        token: String,
        recursive: Int
    ): GitHubTreeResponse {
        return service.getRepoTree(owner, repo, branch, "Bearer $token", recursive)
    }
}

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}