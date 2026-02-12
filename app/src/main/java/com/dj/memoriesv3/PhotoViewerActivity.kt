package com.dj.memoriesv3

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

class PhotoViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repoName = intent.getStringExtra("REPO_NAME") ?: return finish()
        val fileName = intent.getStringExtra("FILE_NAME") ?: return finish()
        val filePath = intent.getStringExtra("FILE_PATH") ?: fileName
        val fileUrl = intent.getStringExtra("FILE_URL")
        val originalPathExtra = intent.getStringExtra("ORIGINAL_PATH")

        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val token = sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, "") ?: ""
        val orgName = sharedPreferences.getString(Constants.KEY_ORG_NAME, "") ?: ""

        // Prepare Original URL
        val originalPath = originalPathExtra ?: filePath.replaceFirst(Regex("^thumbnails/", RegexOption.IGNORE_CASE), "")
        val encodedOriginalPath = originalPath.split('/').joinToString("/") { android.net.Uri.encode(it) }

        val originalUrl = if (token.isNotEmpty()) {
            "https://api.github.com/repos/$orgName/$repoName/contents/$encodedOriginalPath"
        } else {
            "https://raw.githubusercontent.com/$orgName/$repoName/HEAD/$encodedOriginalPath"
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PhotoViewerScreen(fileName, originalUrl, token)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PhotoViewerScreen(fileName: String, originalUrl: String, token: String) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(fileName) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(originalUrl)
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Accept", "application/vnd.github.v3.raw")
                        .build(),
                    contentDescription = fileName,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
