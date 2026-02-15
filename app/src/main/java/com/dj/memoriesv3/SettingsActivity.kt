package com.dj.memoriesv3

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dj.memoriesv3.ui.theme.MemoriesTheme

class SettingsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        setContent {
            MemoriesTheme {
                var orgName by remember { mutableStateOf(sharedPreferences.getString(Constants.KEY_ORG_NAME, "") ?: "") }
                var token by remember { mutableStateOf(sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, "") ?: "") }
                var tokenVisible by remember { mutableStateOf(false) }
                var saveSuccess by remember { mutableStateOf(false) }

                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
                    rememberTopAppBarState()
                )

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = {
                                Text(
                                    "Settings",
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            scrollBehavior = scrollBehavior,
                            colors = TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            )
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                    ) {
                        // ── GitHub Configuration Section ──
                        SectionHeader(
                            title = "GitHub Configuration",
                            icon = Icons.Outlined.Code,
                        )

                        Spacer(Modifier.height(8.dp))

                        // Organization card
                        SettingsCard {
                            Column(modifier = Modifier.padding(20.dp)) {
                                SettingsFieldLabel(
                                    icon = Icons.Outlined.Business,
                                    label = "Organization",
                                    description = "The GitHub organization name that contains your photo repositories",
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = orgName,
                                    onValueChange = {
                                        orgName = it
                                        saveSuccess = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("e.g., my-org") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Token card
                        SettingsCard {
                            Column(modifier = Modifier.padding(20.dp)) {
                                SettingsFieldLabel(
                                    icon = Icons.Outlined.Key,
                                    label = "Personal Access Token",
                                    description = "A GitHub token with repo access permissions",
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = token,
                                    onValueChange = {
                                        token = it
                                        saveSuccess = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("ghp_xxxxxxxxxxxx") },
                                    singleLine = true,
                                    visualTransformation = if (tokenVisible)
                                        VisualTransformation.None
                                    else
                                        PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                            Icon(
                                                if (tokenVisible) Icons.Outlined.VisibilityOff
                                                else Icons.Outlined.Visibility,
                                                contentDescription = "Toggle password visibility",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Save Button
                        Button(
                            onClick = {
                                if (orgName.isEmpty() || token.isEmpty()) {
                                    Toast.makeText(
                                        this@SettingsActivity,
                                        "Please enter both Organization Name and Token",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    sharedPreferences.edit().apply {
                                        putString(Constants.KEY_ORG_NAME, orgName)
                                        putString(Constants.KEY_GITHUB_TOKEN, token)
                                        apply()
                                    }
                                    saveSuccess = true
                                    Toast.makeText(this@SettingsActivity, "Settings saved!", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            AnimatedContent(
                                targetState = saveSuccess,
                                transitionSpec = {
                                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                                },
                                label = "saveButtonContent",
                            ) { success ->
                                if (success) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Saved!",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                } else {
                                    Text(
                                        "Save Settings",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(32.dp))

                        // ── About Section ──
                        SectionHeader(
                            title = "About",
                            icon = Icons.Outlined.Info,
                        )

                        Spacer(Modifier.height(8.dp))

                        SettingsCard {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Outlined.PhotoLibrary,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            "Memories",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            "Version 3.0",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Your private photo gallery powered by GitHub.\nOrganize and access your memories securely.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                )
                            }
                        }

                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    @Composable
    fun SectionHeader(title: String, icon: ImageVector) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    @Composable
    fun SettingsCard(content: @Composable () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            content()
        }
    }

    @Composable
    fun SettingsFieldLabel(icon: ImageVector, label: String, description: String) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}