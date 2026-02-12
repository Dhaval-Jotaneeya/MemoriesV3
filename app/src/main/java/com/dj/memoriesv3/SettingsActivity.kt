package com.dj.memoriesv3

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class SettingsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var orgName by remember { mutableStateOf(sharedPreferences.getString(Constants.KEY_ORG_NAME, "") ?: "") }
                var token by remember { mutableStateOf(sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, "") ?: "") }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Settings") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                        OutlinedTextField(
                            value = orgName,
                            onValueChange = { orgName = it },
                            label = { Text("Organization Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("GitHub Token") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (orgName.isEmpty() || token.isEmpty()) {
                                    Toast.makeText(this@SettingsActivity, "Please enter both Org Name and Token", Toast.LENGTH_SHORT).show()
                                } else {
                                    sharedPreferences.edit().apply {
                                        putString(Constants.KEY_ORG_NAME, orgName)
                                        putString(Constants.KEY_GITHUB_TOKEN, token)
                                        apply()
                                    }
                                    Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Settings")
                        }
                    }
                }
            }
        }
    }
}