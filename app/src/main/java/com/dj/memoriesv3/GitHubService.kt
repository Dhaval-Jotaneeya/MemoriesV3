package com.dj.memoriesv3

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
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

    /**
     * Create a new repository inside an organization.
     * Body JSON: { "name": "...", "description": "...", "private": false, "auto_init": true }
     */
    @POST("orgs/{org}/repos")
    suspend fun createOrgRepo(
        @Path("org") org: String,
        @Header("Authorization") token: String,
        @Body body: RequestBody
    ): Response<GitHubRepo>

    /**
     * Upload/create a file in a GitHub repo using the Contents API.
     * Request body must be JSON: { "message": "...", "content": "base64..." }
     */
    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Header("Authorization") token: String,
        @Body body: RequestBody
    ): Response<Any>

    /**
     * Delete a repository.
     */
    @DELETE("repos/{owner}/{repo}")
    suspend fun deleteRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") token: String
    ): Response<Any>

    /**
     * Rename (update) a repository.
     * Body JSON: { "name": "new-name" }
     */
    @PATCH("repos/{owner}/{repo}")
    suspend fun updateRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") token: String,
        @Body body: RequestBody
    ): Response<GitHubRepo>

    /**
     * Get file info (needed to get sha for deletion).
     */
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileInfo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Header("Authorization") token: String
    ): Response<Any>

    /**
     * Delete a file in a repo using the Contents API.
     * Requires SHA of the file blob. Body: { "message": "...", "sha": "..." }
     */
    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Header("Authorization") token: String,
        @Body body: RequestBody
    ): Response<Any>
}
