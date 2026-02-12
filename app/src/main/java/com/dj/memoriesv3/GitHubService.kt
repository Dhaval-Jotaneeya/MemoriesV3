package com.dj.memoriesv3

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubService {
    @GET("orgs/{org}/repos")
    suspend fun getOrgRepos(
        @Path("org") org: String,
        @Header("Authorization") token: String,
        @Query("per_page") perPage: Int,
        @Query("page") page: Int
    ): List<GitHubRepo>

    @GET("repos/{owner}/{repo}/git/trees/{branch}")
    suspend fun getRepoTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Header("Authorization") token: String,
        @Query("recursive") recursive: Int = 1
    ): GitHubTreeResponse
}
