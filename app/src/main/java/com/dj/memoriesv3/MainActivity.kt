package com.dj.memoriesv3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        checkSettings()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val state by viewModel.reposState.collectAsState()
        var searchQuery by remember { mutableStateOf("") }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("MemoriesV3") },
                    actions = {
                        IconButton(onClick = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { /* Create new repo action */ }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Repo")
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.filterRepositories(it)
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    label = { Text("Search Repositories") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )

                when (val s = state) {
                    is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                    }
                    is UiState.Success -> RepoList(s.data)
                }
            }
        }
    }

    @Composable
    fun RepoList(repos: List<GitHubRepo>) {
        LazyColumn {
            items(repos) { repo ->
                ListItem(
                    headlineContent = { Text(repo.name) },
                    supportingContent = { Text(repo.description ?: "No description") },
                    modifier = Modifier.clickable {
                        val intent = Intent(this@MainActivity, GalleryActivity::class.java)
                        intent.putExtra("REPO_NAME", repo.name)
                        startActivity(intent)
                    }
                )
                HorizontalDivider()
            }
        }
    }

    private fun checkSettings() {
        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val orgName = sharedPreferences.getString(Constants.KEY_ORG_NAME, null)
        val token = sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, null)

        if (orgName.isNullOrEmpty() || token.isNullOrEmpty()) {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh if we came back from settings
        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val orgName = sharedPreferences.getString(Constants.KEY_ORG_NAME, null)
        val token = sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, null)
        if (!orgName.isNullOrEmpty() && !token.isNullOrEmpty()) {
            viewModel.loadRepositories(orgName, token)
        }
    }
}