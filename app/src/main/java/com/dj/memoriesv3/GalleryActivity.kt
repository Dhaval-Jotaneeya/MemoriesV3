package com.dj.memoriesv3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var adapter: GalleryAdapter
    private lateinit var githubToken: String
    private lateinit var organization: String

    private val service: GitHubService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        // Get Repo Name from Intent
        val repoName = intent.getStringExtra("REPO_NAME") ?: return finish()

        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        githubToken = sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, null) ?: ""
        organization = sharedPreferences.getString(Constants.KEY_ORG_NAME, null) ?: ""

        if (githubToken.isEmpty() || organization.isEmpty()) {
            Toast.makeText(this, "App not configured. Please set token and organization in settings.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupUI(repoName)
        fetchThumbnails(repoName)
    }

    private fun setupUI(repoName: String) {
        toolbar = findViewById(R.id.galleryToolbar)
        toolbar.title = repoName
        toolbar.setNavigationOnClickListener { finish() }

        progressBar = findViewById(R.id.progressBarGallery)
        emptyText = findViewById(R.id.textEmptyState)
        recyclerView = findViewById(R.id.recyclerViewGallery)

        // 3 Columns Grid
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        adapter = GalleryAdapter(mutableListOf()) { file ->
            val intent = Intent(this, PhotoViewerActivity::class.java)
            intent.putExtra("REPO_NAME", repoName)
            intent.putExtra("FILE_NAME", file.name)
            intent.putExtra("FILE_PATH", file.path)
            intent.putExtra("FILE_URL", file.url)
            intent.putExtra("ORIGINAL_PATH", file.originalPath)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
    }

    private fun fetchThumbnails(repoName: String) {
        progressBar.visibility = View.VISIBLE
        toolbar.subtitle = "Locating thumbnails..."

        // Step 1: Get Root Tree (Non-recursive) to find 'thumbnails' folder
        // We pass 0 for recursive to avoid fetching the whole repo
        service.getRepoTree(organization, repoName, "HEAD", "Bearer $githubToken", 0)
            .enqueue(object : Callback<GitHubTreeResponse> {
                override fun onResponse(call: Call<GitHubTreeResponse>, response: Response<GitHubTreeResponse>) {
                    if (response.isSuccessful) {
                        val rootItems = response.body()?.tree ?: emptyList()
                        val thumbDir = rootItems.find { it.path.equals("thumbnails", ignoreCase = true) && it.type == "tree" }
                        
                        // Map lowercase filename to actual filename for root files to handle casing (e.g. .jpg vs .JPG)
                        val rootFilesMap = rootItems
                            .filter { it.type == "blob" }
                            .associate { it.path.lowercase() to it.path }

                        if (thumbDir != null) {
                            fetchThumbnailsTree(repoName, thumbDir.sha, thumbDir.path, rootFilesMap)
                        } else {
                            showError("No 'thumbnails' folder found.")
                        }
                    } else {
                        showError("Error: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<GitHubTreeResponse>, t: Throwable) {
                    showError("Failure: ${t.message}")
                }
            })
    }

    private fun fetchThumbnailsTree(repoName: String, sha: String, folderPath: String, rootFilesMap: Map<String, String>) {
        toolbar.subtitle = "Fetching image list..."
        // Step 2: Get Tree of the thumbnails folder
        service.getRepoTree(organization, repoName, sha, "Bearer $githubToken", 1)
            .enqueue(object : Callback<GitHubTreeResponse> {
                override fun onResponse(call: Call<GitHubTreeResponse>, response: Response<GitHubTreeResponse>) {
                    if (response.isSuccessful) {
                        val treeItems = response.body()?.tree ?: emptyList()
                        processImages(treeItems, repoName, folderPath, rootFilesMap)
                    } else {
                        showError("Error: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<GitHubTreeResponse>, t: Throwable) {
                    showError("Failure: ${t.message}")
                }
            })
    }

    private fun processImages(treeItems: List<GitHubTreeItem>, repoName: String, folderPath: String, rootFilesMap: Map<String, String>) {
        toolbar.subtitle = "Processing ${treeItems.size} files..."
        Thread {
            val images = treeItems.filter { item ->
                item.type == "blob" &&
                (item.path.endsWith(".jpg", true) || 
                 item.path.endsWith(".png", true) || 
                 item.path.endsWith(".jpeg", true))
            }.map { item ->
                val fullPath = "$folderPath/${item.path}"
                val simpleName = item.path.substringAfterLast('/')
                // Try to find the exact original path from root files map
                val originalPath = rootFilesMap[simpleName.lowercase()]
                GitHubFile(
                    name = simpleName,
                    path = fullPath,
                    download_url = "https://raw.githubusercontent.com/$organization/$repoName/HEAD/$fullPath",
                    url = item.url,
                    originalPath = originalPath
                )
            }

            runOnUiThread {
                progressBar.visibility = View.GONE
                toolbar.subtitle = "${images.size} images"
                if (images.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "No images found in thumbnails/"
                } else {
                    emptyText.visibility = View.GONE
                    adapter.updateData(images)
                }
            }
        }.start()
    }

    private fun showError(message: String) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            toolbar.subtitle = "Error"
            Toast.makeText(this@GalleryActivity, message, Toast.LENGTH_SHORT).show()
        }
    }
}

// Data Model
data class GitHubFile(
    val name: String,
    val path: String,
    val download_url: String?,
    val url: String?, // Fallback for private repos if download_url is null
    val originalPath: String? = null
) {
    fun getImageUrl(): String {
        // If download_url is null (common in private repos via API), use url with raw parameter logic
        // However, standard API usually provides download_url if token has access.
        // If using raw content API for private repos, you might need to append headers to Glide.
        return download_url ?: url ?: ""
    }
}