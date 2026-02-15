package com.dj.memoriesv3

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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

    /**
     * Upload a file to a GitHub repo via the Contents API.
     * @param base64Content The file content encoded as Base64.
     * @param filePath The path in the repo (e.g., "my_image.jpg" or "thumbnails/my_image.jpg").
     * @param commitMessage The commit message.
     * @return true on success, false on failure.
     */
    suspend fun uploadFile(
        owner: String,
        repo: String,
        filePath: String,
        token: String,
        base64Content: String,
        commitMessage: String
    ): Boolean {
        val json = JSONObject().apply {
            put("message", commitMessage)
            put("content", base64Content)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val response = service.createOrUpdateFile(owner, repo, filePath, "Bearer $token", body)
        return response.isSuccessful
    }

    /**
     * Create a new album (GitHub repo) with a README.md and a thumbnails folder.
     * @param org The GitHub organization name.
     * @param repoName The name for the new repository/album.
     * @param description Optional description for the album.
     * @param token The GitHub personal access token.
     * @return The created GitHubRepo on success, or throws an exception on failure.
     */
    suspend fun createAlbum(
        org: String,
        repoName: String,
        description: String,
        token: String
    ): GitHubRepo {
        // Step 1: Create the repository
        val createJson = JSONObject().apply {
            put("name", repoName)
            put("description", description)
            put("private", false)
            put("auto_init", false) // We'll add files manually
        }
        val createBody = createJson.toString().toRequestBody("application/json".toMediaType())
        val createResponse = service.createOrgRepo(org, "Bearer $token", createBody)

        if (!createResponse.isSuccessful) {
            val errorBody = createResponse.errorBody()?.string() ?: "Unknown error"
            throw Exception("Failed to create repository: $errorBody")
        }

        val createdRepo = createResponse.body()
            ?: throw Exception("Repository created but response body is empty")

        // Step 2: Upload README.md
        val readmeContent = "# $repoName\n\n${description.ifEmpty { "A photo album created with Memories." }}\n"
        val readmeBase64 = android.util.Base64.encodeToString(
            readmeContent.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        uploadFile(org, repoName, "README.md", token, readmeBase64, "Initial commit: Add README")

        // Step 3: Create thumbnails folder with .gitkeep
        val gitkeepBase64 = android.util.Base64.encodeToString(
            ByteArray(0),
            android.util.Base64.NO_WRAP
        )
        uploadFile(org, repoName, "thumbnails/.gitkeep", token, gitkeepBase64, "Create thumbnails folder")

        return createdRepo
    }

    /**
     * Delete a repository.
     */
    suspend fun deleteRepo(owner: String, repo: String, token: String): Boolean {
        val response = service.deleteRepo(owner, repo, "Bearer $token")
        return response.isSuccessful
    }

    /**
     * Rename (update) a repository.
     */
    suspend fun renameRepo(owner: String, oldName: String, newName: String, token: String): Boolean {
        val json = JSONObject().apply {
            put("name", newName)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val response = service.updateRepo(owner, oldName, "Bearer $token", body)
        return response.isSuccessful
    }

    /**
     * Get the SHA of a file in a repo (needed for deletion via Contents API).
     */
    suspend fun getFileSha(owner: String, repo: String, filePath: String, token: String): String? {
        val response = service.getFileInfo(owner, repo, filePath, "Bearer $token")
        if (!response.isSuccessful) return null
        return try {
            // The response body is a LinkedTreeMap from Gson; convert to JSON to extract sha
            val bodyStr = com.google.gson.Gson().toJson(response.body())
            val json = JSONObject(bodyStr)
            json.optString("sha", null)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Delete a file from a GitHub repo using the Contents API.
     * Requires the file's SHA (use getFileSha first).
     */
    suspend fun deleteFile(
        owner: String,
        repo: String,
        filePath: String,
        sha: String,
        token: String,
        commitMessage: String
    ): Boolean {
        val json = JSONObject().apply {
            put("message", commitMessage)
            put("sha", sha)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val response = service.deleteFile(owner, repo, filePath, "Bearer $token", body)
        return response.isSuccessful
    }
}

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}