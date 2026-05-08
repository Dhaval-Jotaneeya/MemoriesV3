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
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
                
                var isCheckingUpdate by remember { mutableStateOf(false) }
                var updateInfo by remember { mutableStateOf<GitHubRepository.LatestRelease?>(null) }
                var showUpdateDialog by remember { mutableStateOf(false) }
                var isDownloadingUpdate by remember { mutableStateOf(false) }
                var downloadProgress by remember { mutableFloatStateOf(0f) }

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

                        // ── Cache Management Section ──
                        SectionHeader(
                            title = "Cache Management",
                            icon = Icons.Outlined.FolderDelete,
                        )

                        Spacer(Modifier.height(8.dp))

                        // Calculate cache sizes
                        var thumbnailCacheSize by remember { mutableLongStateOf(0L) }
                        var hdCacheSize by remember { mutableLongStateOf(0L) }
                        var showClearThumbnailDialog by remember { mutableStateOf(false) }
                        var showClearHdDialog by remember { mutableStateOf(false) }
                        var showClearAllDialog by remember { mutableStateOf(false) }

                        // Recalculate sizes
                        fun recalculateSizes() {
                            val thumbDir = java.io.File(cacheDir, "thumbnails_cache")
                            thumbnailCacheSize = if (thumbDir.exists()) thumbDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
                            val hdDir = java.io.File(filesDir, "hd_cache")
                            hdCacheSize = if (hdDir.exists()) hdDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
                        }

                        LaunchedEffect(Unit) { recalculateSizes() }

                        fun formatCacheSize(bytes: Long): String {
                            if (bytes < 1024) return "$bytes B"
                            val kb = bytes / 1024.0
                            if (kb < 1024) return String.format("%.1f KB", kb)
                            val mb = kb / 1024.0
                            if (mb < 1024) return String.format("%.1f MB", mb)
                            val gb = mb / 1024.0
                            return String.format("%.2f GB", gb)
                        }

                        // Thumbnail Cache Card
                        SettingsCard {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Outlined.Image,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                "Thumbnail Cache",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "Gallery preview images • ${formatCacheSize(thumbnailCacheSize)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 30.dp),
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { showClearThumbnailDialog = true },
                                        enabled = thumbnailCacheSize > 0,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    ) {
                                        Text("Clear", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // HD Photo Cache Card
                        SettingsCard {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Outlined.HighQuality,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                "HD Photo Cache",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "Full resolution viewed photos • ${formatCacheSize(hdCacheSize)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 30.dp),
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { showClearHdDialog = true },
                                        enabled = hdCacheSize > 0,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    ) {
                                        Text("Clear", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Clear All Cache Button
                        OutlinedButton(
                            onClick = { showClearAllDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = (thumbnailCacheSize + hdCacheSize) > 0,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if ((thumbnailCacheSize + hdCacheSize) > 0)
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Icon(
                                Icons.Outlined.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Clear All Cache (${formatCacheSize(thumbnailCacheSize + hdCacheSize)})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        // ── Confirmation Dialogs ──

                        if (showClearThumbnailDialog) {
                            AlertDialog(
                                onDismissRequest = { showClearThumbnailDialog = false },
                                shape = RoundedCornerShape(24.dp),
                                icon = {
                                    Icon(
                                        Icons.Outlined.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                title = { Text("Clear Thumbnail Cache?") },
                                text = {
                                    Text("This will remove ${formatCacheSize(thumbnailCacheSize)} of cached gallery thumbnails. They will be re-downloaded when you open an album.")
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            val freed = thumbnailCacheSize
                                            val thumbDir = java.io.File(cacheDir, "thumbnails_cache")
                                            if (thumbDir.exists()) thumbDir.deleteRecursively()
                                            showClearThumbnailDialog = false
                                            recalculateSizes()
                                            Toast.makeText(this@SettingsActivity, "Freed ${formatCacheSize(freed)}", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                    ) { Text("Clear") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClearThumbnailDialog = false }) { Text("Cancel") }
                                },
                            )
                        }

                        if (showClearHdDialog) {
                            AlertDialog(
                                onDismissRequest = { showClearHdDialog = false },
                                shape = RoundedCornerShape(24.dp),
                                icon = {
                                    Icon(
                                        Icons.Outlined.HighQuality,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                title = { Text("Clear HD Photo Cache?") },
                                text = {
                                    Text("This will remove ${formatCacheSize(hdCacheSize)} of cached full-resolution photos. They will be re-downloaded when you view them again.")
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            val freed = hdCacheSize
                                            val hdDir = java.io.File(filesDir, "hd_cache")
                                            if (hdDir.exists()) hdDir.deleteRecursively()
                                            showClearHdDialog = false
                                            recalculateSizes()
                                            Toast.makeText(this@SettingsActivity, "Freed ${formatCacheSize(freed)}", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                    ) { Text("Clear") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClearHdDialog = false }) { Text("Cancel") }
                                },
                            )
                        }

                        if (showClearAllDialog) {
                            AlertDialog(
                                onDismissRequest = { showClearAllDialog = false },
                                shape = RoundedCornerShape(24.dp),
                                icon = {
                                    Icon(
                                        Icons.Outlined.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                title = { Text("Clear All Cache?") },
                                text = {
                                    Text("This will remove all cached data (${formatCacheSize(thumbnailCacheSize + hdCacheSize)} total):\n\n• Thumbnail Cache: ${formatCacheSize(thumbnailCacheSize)}\n• HD Photo Cache: ${formatCacheSize(hdCacheSize)}\n\nAll images will need to be re-downloaded.")
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            val freed = thumbnailCacheSize + hdCacheSize
                                            val thumbDir = java.io.File(cacheDir, "thumbnails_cache")
                                            if (thumbDir.exists()) thumbDir.deleteRecursively()
                                            val hdDir = java.io.File(filesDir, "hd_cache")
                                            if (hdDir.exists()) hdDir.deleteRecursively()
                                            showClearAllDialog = false
                                            recalculateSizes()
                                            Toast.makeText(this@SettingsActivity, "Freed ${formatCacheSize(freed)}", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                    ) { Text("Clear All") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClearAllDialog = false }) { Text("Cancel") }
                                },
                            )
                        }

                        if (showUpdateDialog && updateInfo != null) {
                            AlertDialog(
                                onDismissRequest = { showUpdateDialog = false },
                                shape = RoundedCornerShape(24.dp),
                                icon = {
                                    Icon(
                                        Icons.Outlined.SystemUpdate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                title = { Text("New Update Available") },
                                text = {
                                    Column {
                                        Text("Version ${updateInfo?.tagName} is available.")
                                        if (updateInfo?.releaseNotes?.isNotEmpty() == true) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                "Release Notes:",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                updateInfo?.releaseNotes ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 5,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        
                                        if (isDownloadingUpdate) {
                                            Spacer(Modifier.height(16.dp))
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                LinearProgressIndicator(
                                                    progress = { downloadProgress },
                                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    "Downloading: ${(downloadProgress * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (!isDownloadingUpdate) {
                                                isDownloadingUpdate = true
                                                downloadAndInstallApk(updateInfo?.downloadUrl ?: "") { progress ->
                                                    downloadProgress = progress
                                                }
                                            }
                                        },
                                        enabled = !isDownloadingUpdate,
                                        shape = RoundedCornerShape(12.dp),
                                    ) { 
                                        if (isDownloadingUpdate) Text("Downloading...") else Text("Update Now") 
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showUpdateDialog = false },
                                        enabled = !isDownloadingUpdate
                                    ) { Text("Later") }
                                },
                            )
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
                                            "Version ${BuildConfig.VERSION_NAME}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                
                                // Check for Updates Button
                                Button(
                                    onClick = {
                                        isCheckingUpdate = true
                                        lifecycleScope.launch {
                                            val info = GitHubRepository.getLatestReleaseInfo()
                                            isCheckingUpdate = false
                                            if (info != null) {
                                                // Compare versions. Simple string comparison for now, 
                                                // or more advanced logic if needed.
                                                if (info.tagName != BuildConfig.VERSION_NAME && 
                                                    info.tagName != "v${BuildConfig.VERSION_NAME}") {
                                                    updateInfo = info
                                                    showUpdateDialog = true
                                                } else {
                                                    Toast.makeText(this@SettingsActivity, "You are on the latest version!", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(this@SettingsActivity, "Failed to check for updates", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isCheckingUpdate,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    if (isCheckingUpdate) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Checking...")
                                    } else {
                                        Icon(Icons.Outlined.Update, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Check for Updates")
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

    private fun downloadAndInstallApk(url: String, onProgress: (Float) -> Unit) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) throw Exception("Failed to download file")
                
                val body = response.body ?: throw Exception("Empty response body")
                val totalBytes = body.contentLength()
                val file = java.io.File(cacheDir, "update.apk")
                
                body.byteStream().use { input ->
                    java.io.FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalBytes > 0) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    onProgress(totalRead.toFloat() / totalBytes)
                                }
                            }
                        }
                    }
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    installApk(file)
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}