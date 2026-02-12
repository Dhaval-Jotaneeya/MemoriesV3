package com.dj.memoriesv3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest

class GalleryActivity : ComponentActivity() {

    private lateinit var githubToken: String
    private lateinit var organization: String
    private lateinit var viewModel: GalleryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        viewModel = ViewModelProvider(this)[GalleryViewModel::class.java]

        viewModel.loadImages(organization, repoName, githubToken)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GalleryScreen(repoName)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GalleryScreen(repoName: String) {
        val imagesState by viewModel.imagesState.collectAsState()
        val statusMessage by viewModel.statusMessage.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(repoName)
                            if (statusMessage.isNotEmpty()) {
                                Text(statusMessage, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (val state = imagesState) {
                    is UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is UiState.Error -> Text("Error: ${state.message}", Modifier.align(Alignment.Center))
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            Text("No images found in thumbnails/", Modifier.align(Alignment.Center))
                        } else {
                            LazyVerticalGrid(columns = GridCells.Fixed(3)) {
                                items(state.data) { file ->
                                    GalleryItem(file, repoName)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun GalleryItem(file: GitHubFile, repoName: String) {
        val context = LocalContext.current
        val imageUrl = file.getImageUrl()
        
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .addHeader("Authorization", "Bearer $githubToken")
                .crossfade(true)
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .aspectRatio(1f)
                .padding(2.dp)
                .clickable {
                    val intent = Intent(context, PhotoViewerActivity::class.java)
                    intent.putExtra("REPO_NAME", repoName)
                    intent.putExtra("FILE_NAME", file.name)
                    intent.putExtra("FILE_PATH", file.path)
                    intent.putExtra("FILE_URL", file.url)
                    intent.putExtra("ORIGINAL_PATH", file.originalPath)
                    context.startActivity(intent)
                }
        )
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