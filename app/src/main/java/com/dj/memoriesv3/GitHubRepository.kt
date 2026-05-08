package com.dj.memoriesv3

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object GitHubRepository {

    data class LatestRelease(
        val tagName: String,
        val downloadUrl: String,
        val releaseNotes: String
    )

    private const val BASE_URL = "https://api.github.com/"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private val service: GitHubService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubService::class.java)
    }

    /**
     * Fetches the latest release info for the app from the public repository.
     */
    suspend fun getLatestReleaseInfo(): LatestRelease? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${BASE_URL}repos/Dhaval-Jotaneeya/MemoriesV3/releases/latest")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    
                    val json = JSONObject(response.body?.string() ?: "")
                    val tagName = json.getString("tag_name")
                    val body = json.optString("body", "")
                    
                    // Find the first .apk asset
                    val assets = json.getJSONArray("assets")
                    var apkUrl = ""
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                    
                    if (apkUrl.isNotEmpty()) LatestRelease(tagName, apkUrl, body) else null
                }
            } catch (e: Exception) {
                null
            }
        }
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
    // ── Batch Upload via Git Data API (single commit for all files) ──

    /**
     * Data class for a file to be included in a batch upload.
     */
    data class BatchFile(
        val repoPath: String,    // e.g. "photo.jpg" or "thumbnails/photo.jpg"
        val base64Content: String
    )

    /**
     * Create a git blob and return its SHA.
     */
    /**
     * Create a git blob and return its SHA.
     */
    suspend fun createBlob(
        owner: String, repo: String, token: String, base64Content: String
    ): String {
        val json = JSONObject().apply {
            put("content", base64Content)
            put("encoding", "base64")
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val response = service.createBlob(owner, repo, "Bearer $token", body)
        if (!response.isSuccessful) {
            throw Exception("Failed to create blob: ${response.code()} ${response.errorBody()?.string()}")
        }
        return response.body()?.sha ?: throw Exception("Blob response missing SHA")
    }

    /**
     * Get the SHA of the HEAD of a branch.
     */
    suspend fun getBranchSha(owner: String, repo: String, token: String, branch: String): String {
        val response = service.getRef(owner, repo, "heads/$branch", "Bearer $token")
        if (!response.isSuccessful) {
            throw Exception("Failed to get ref heads/$branch: ${response.code()}")
        }
        return response.body()?.obj?.sha ?: throw Exception("Ref response missing SHA")
    }

    /**
     * Get the base tree SHA for a commit.
     */
    suspend fun getBaseTreeSha(owner: String, repo: String, token: String, commitSha: String): String {
        val response = service.getCommit(owner, repo, commitSha, "Bearer $token")
        if (!response.isSuccessful) {
            throw Exception("Failed to get commit: ${response.code()}")
        }
        return response.body()?.tree?.sha ?: throw Exception("Commit response missing tree SHA")
    }

    /**
     * Create a new tree with the given items.
     */
    suspend fun createTree(
        owner: String, repo: String, token: String,
        baseTreeSha: String, treeItems: List<JSONObject>
    ): String {
        val treeArray = JSONArray()
        treeItems.forEach { treeArray.put(it) }
        val json = JSONObject().apply {
            put("base_tree", baseTreeSha)
            put("tree", treeArray)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val response = service.createTree(owner, repo, "Bearer $token", body)
        if (!response.isSuccessful) {
            throw Exception("Failed to create tree: ${response.code()} ${response.errorBody()?.string()}")
        }
        return response.body()?.sha ?: throw Exception("Tree response missing SHA")
    }

    /**
     * Create a commit pointing to a tree.
     */
    suspend fun createCommitObj(
        owner: String, repo: String, token: String,
        message: String, treeSha: String, parentSha: String
    ): String {
        val parentsArray = JSONArray().apply { put(parentSha) }
        val json = JSONObject().apply {
            put("message", message)
            put("tree", treeSha)
            put("parents", parentsArray)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val response = service.createCommit(owner, repo, "Bearer $token", body)
        if (!response.isSuccessful) {
            throw Exception("Failed to create commit: ${response.code()} ${response.errorBody()?.string()}")
        }
        return response.body()?.sha ?: throw Exception("Commit response missing SHA")
    }

    /**
     * Update a branch ref to point to a new commit.
     */
    suspend fun updateRef(
        owner: String, repo: String, token: String,
        branch: String, commitSha: String
    ) {
        val json = JSONObject().apply {
            put("sha", commitSha)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val response = service.updateRef(owner, repo, "heads/$branch", "Bearer $token", body)
        if (!response.isSuccessful) {
            throw Exception("Failed to update ref: ${response.code()} ${response.errorBody()?.string()}")
        }
    }

    /**
     * Resolve the default branch name (tries 'main', falls back to 'master').
     * Returns a Pair of (headSha, branchName).
     */
    suspend fun resolveDefaultBranch(owner: String, repo: String, token: String): Pair<String, String> {
        return try {
            getBranchSha(owner, repo, token, "main") to "main"
        } catch (_: Exception) {
            getBranchSha(owner, repo, token, "master") to "master"
        }
    }

    /**
     * Retry a suspend block with exponential backoff.
     * @param maxRetries Number of retry attempts (default 3).
     * @param initialDelayMs Initial delay before first retry (default 2000ms).
     * @param maxDelayMs Maximum delay cap (default 15000ms).
     */
    suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 2000,
        maxDelayMs: Long = 15000,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        var currentDelay = initialDelayMs
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
                }
            }
        }
        throw lastException ?: Exception("Retry failed")
    }

    /**
     * Upload multiple files in a single commit using the Git Data API.
     *
     * Steps:
     * 1. Get HEAD SHA of the branch
     * 2. Get the base tree SHA
     * 3. Create blobs for each file (reporting progress via callback)
     * 4. Create a new tree with all blobs
     * 5. Create a commit on the new tree
     * 6. Update the branch ref
     *
     * @param onBlobProgress Called after each blob is created: (completedCount, totalCount, fileName)
     */
    suspend fun uploadBatch(
        owner: String,
        repo: String,
        token: String,
        files: List<BatchFile>,
        commitMessage: String,
        branch: String = "main",
        onBlobProgress: ((Int, Int, String) -> Unit)? = null
    ) {
        // 1. Get HEAD SHA (try 'main', fallback to 'master')
        val (headSha, actualBranch) = try {
            getBranchSha(owner, repo, token, branch) to branch
        } catch (_: Exception) {
            getBranchSha(owner, repo, token, "master") to "master"
        }

        // 2. Get base tree SHA
        val baseTreeSha = getBaseTreeSha(owner, repo, token, headSha)

        // 3. Create blobs for each file
        val treeItems = mutableListOf<JSONObject>()
        for ((index, file) in files.withIndex()) {
            onBlobProgress?.invoke(index, files.size, file.repoPath)
            val blobSha = createBlob(owner, repo, token, file.base64Content)
            treeItems.add(JSONObject().apply {
                put("path", file.repoPath)
                put("mode", "100644")
                put("type", "blob")
                put("sha", blobSha)
            })
        }
        onBlobProgress?.invoke(files.size, files.size, "")

        // 4. Create tree
        val newTreeSha = createTree(owner, repo, token, baseTreeSha, treeItems)

        // 5. Create commit
        val newCommitSha = createCommitObj(owner, repo, token, commitMessage, newTreeSha, headSha)

        // 6. Update ref
        updateRef(owner, repo, token, actualBranch, newCommitSha)
    }
}

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}