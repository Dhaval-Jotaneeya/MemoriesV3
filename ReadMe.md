# MemoriesV3 — Project Overview

## What Is This App?

**MemoriesV3** is a native Android photo album manager that uses **GitHub repositories as a cloud storage backend**. Each "album" is a GitHub repo under a configurable GitHub Organization. Users can browse albums, view/upload/download/delete photos, and manage albums — all backed by the GitHub API.

- **Package:** `com.dj.memoriesv3`
- **Min SDK:** 24 · **Target/Compile SDK:** 36
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3) — all screens are fully Compose-based
- **Build System:** Gradle (Kotlin DSL)

---

## Architecture & Key Patterns

| Pattern             | Details                                                                 |
|---------------------|-------------------------------------------------------------------------|
| **UI Layer**        | Jetpack Compose with `ComponentActivity` + `setContent { … }`          |
| **State Management**| `ViewModel` + Kotlin `StateFlow` / `MutableStateFlow`                   |
| **Networking**      | Retrofit 2 + Gson for GitHub REST API calls                             |
| **Image Loading**   | **Coil** (compose variant) for thumbnails in gallery, **Glide** in legacy adapter |
| **Theme**           | Custom Material 3 `MemoriesTheme` with both light & dark color palettes; supports Android 12+ dynamic color |
| **Navigation**      | Activity-based (no Jetpack Navigation Compose). Data passed via `Intent` extras |
| **Settings Storage**| `SharedPreferences` (key: `app_settings`)                               |
| **Concurrency**     | `viewModelScope.launch` / `lifecycleScope.launch` + `Dispatchers.IO`   |

### State Pattern

All list screens use a shared sealed class for UI state:

```kotlin
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
```

This pattern is used in both `MainViewModel` (for repos list) and `GalleryViewModel` (for images list).

---

## Screens / Activities

### 1. `MainActivity` (~1165 lines)
- **Purpose:** Landing screen — shows a searchable list of GitHub repos (albums) in the organization.
- **ViewModel:** `MainViewModel`
- **Key Features:**
  - Repo list with search/filter, pull-to-refresh, manual refresh button
  - Create new album (creates repo + README + thumbnails folder)
  - Delete album (deletes the repo)
  - Rename album
  - Shimmer loading skeletons, empty state, error state
  - Long-press context actions on repo cards
- **Navigates to:** `GalleryActivity` (on repo click), `SettingsActivity` (from toolbar)

### 2. `GalleryActivity` (~1059 lines)
- **Purpose:** Shows a photo grid for a specific album/repo.
- **ViewModel:** `GalleryViewModel`
- **Key Features:**
  - Loads thumbnails from the `thumbnails/` folder in the repo via GitHub Tree API
  - Adaptive grid layout using `LazyVerticalGrid` with `GridCells.Adaptive(110.dp)`
  - Custom Coil `ImageLoader` with dedicated disk cache (`thumbnails_cache`, 100MB max) and memory cache (30%)
  - Multi-select mode with long-press to enter, select all, batch delete, batch download
  - Photo upload: picks images → uploads original to repo root → creates compressed thumbnail (max ~10KB) → uploads to `thumbnails/` folder
  - Upload/batch action progress overlay cards
  - Shimmer placeholders during loading
- **Navigates to:** `PhotoViewerActivity` (on image tap)
- **Data passed out:** `REPO_NAME`, `CURRENT_INDEX`, `PHOTO_LIST_JSON` (serialized list of `GitHubFile`)

### 3. `PhotoViewerActivity` (~970 lines)
- **Purpose:** Full-screen HD photo viewer with horizontal paging.
- **Key Features:**
  - `HorizontalPager` for swiping between photos
  - Downloads original HD images from GitHub (via Contents API with auth header, or raw URL)
  - HD image disk cache at `filesDir/hd_cache/{repoName}/`
  - Per-page state tracking with `MutableState` maps (state, progress, details, speed)
  - Custom `ZoomableImage` composable with:
    - Pinch-to-zoom (1x–8x), double-tap zoom (3x toggle)
    - Pan with bounds clamping
    - Pager-compatible gesture handling (lets horizontal swipes through at 1x zoom, blocks vertical drag at 1x)
  - Download progress overlay with percentage, speed, file size
  - Bottom action bar: Save to gallery, Share, Image Info dialog
  - Save: uses `MediaStore` on Android 10+, direct file write on older
  - Share: uses `FileProvider`
  - Top bar shows filename and page counter (e.g., "3 / 12")
  - Forced dark theme (`MemoriesTheme(darkTheme = true)`)

### 4. `SettingsActivity` (~617 lines)
- **Purpose:** App configuration screen.
- **Key Features:**
  - GitHub token input (password-masked)
  - Organization name input
  - Cache management (view cache size, clear HD cache, clear thumbnail cache)
  - Settings persisted in `SharedPreferences` with keys from `Constants`

---

## Data Models

### `GitHubRepo` (`GitHubRepo.kt`)
```kotlin
data class GitHubRepo(
    val id: Long,
    val name: String,
    val description: String?,
    val htmlUrl: String,
    val updatedAt: String?
)
```
Deserialized from GitHub API via Gson `@SerializedName`.

### `GitHubFile` (bottom of `GalleryActivity.kt`)
```kotlin
data class GitHubFile(
    val name: String,          // e.g. "photo1.jpg"
    val path: String,          // e.g. "thumbnails/photo1.jpg"
    val download_url: String?, // raw URL for thumbnail
    val url: String?,          // API URL
    val originalPath: String?  // e.g. "photo1.jpg" (root-level original)
)
```

### `GitHubTreeResponse` / `GitHubTreeItem` (`GitHubTree.kt`)
Used to parse the Git Tree API response for listing files efficiently.

### `UploadProgress` / `BatchActionProgress` (top of `GalleryViewModel.kt`)
Data classes tracking upload and batch operation progress (file count, errors, current step, etc.).

### `UiState<T>`, `CreateAlbumResult`, `RepoActionResult`
Sealed classes for screen state and operation results.

---

## Networking Layer

### `GitHubService.kt` (Retrofit Interface)
All GitHub API endpoints:
| Endpoint                     | Method  | Purpose                                      |
|------------------------------|---------|----------------------------------------------|
| `orgs/{org}/repos`           | GET     | List organization repos                      |
| `repos/{owner}/{repo}/git/trees/{branch}` | GET | Get repo file tree                |
| `orgs/{org}/repos`           | POST    | Create new repo in org                       |
| `repos/{owner}/{repo}/contents/{path}` | PUT | Upload/create a file                  |
| `repos/{owner}/{repo}`       | DELETE  | Delete a repository                          |
| `repos/{owner}/{repo}`       | PATCH   | Rename/update a repository                   |
| `repos/{owner}/{repo}/contents/{path}` | GET | Get file info (for SHA)               |
| `repos/{owner}/{repo}/contents/{path}` | DELETE | Delete a file (requires SHA in body) |

### `GitHubRepository.kt` (Repository / Data Layer)
Singleton `object` wrapping `GitHubService`. Handles:
- Token prefixing (`"Bearer $token"`)
- Repo CRUD (create album with README + thumbnails folder, delete, rename)
- File upload (Base64-encoded content via Contents API)
- File deletion (get SHA first, then delete)
- Retrofit instance with `https://api.github.com/` base URL

---

## Repo/Album Storage Convention

Each album (GitHub repo) follows this file structure:

```
repo-root/
├── README.md                    # Auto-created when album is made
├── photo1.jpg                   # Original full-size images
├── photo2.png
├── thumbnails/
│   ├── .gitkeep                 # Auto-created to ensure folder exists
│   ├── photo1.jpg               # Compressed thumbnail (~10KB max)
│   └── photo2.jpg               # Always JPEG regardless of original format
```

- **Originals** are stored at the repo root
- **Thumbnails** are JPEG-compressed copies (max ~10KB) in the `thumbnails/` folder
- The gallery uses thumbnails for grid display and maps each to its original via filename matching

---

## Key Configuration

### `Constants.kt`
```kotlin
object Constants {
    const val PREFS_NAME = "app_settings"
    const val KEY_ORG_NAME = "org_name"
    const val KEY_GITHUB_TOKEN = "github_token"
}
```

### Permissions (`AndroidManifest.xml`)
- `INTERNET` — required for GitHub API
- `READ_EXTERNAL_STORAGE` (≤ SDK 32) / `READ_MEDIA_IMAGES` (≥ SDK 33) — for picking photos to upload
- `WRITE_EXTERNAL_STORAGE` (≤ SDK 28) — for saving downloads on old devices
- `FileProvider` configured for sharing images

---

## File Structure

```
MemoriesV3/
├── app/
│   ├── build.gradle.kts                          # App-level build config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/dj/memoriesv3/
│       │   ├── Constants.kt                      # SharedPreferences keys
│       │   ├── GitHubRepo.kt                     # Repo data model
│       │   ├── GitHubTree.kt                     # Tree API response models
│       │   ├── GitHubService.kt                  # Retrofit API interface
│       │   ├── GitHubRepository.kt               # Data layer (API calls)
│       │   ├── MainActivity.kt                   # Albums list screen (Compose)
│       │   ├── MainViewModel.kt                  # Albums state management
│       │   ├── GalleryActivity.kt                # Photo grid screen (Compose) + GitHubFile model
│       │   ├── GalleryAdapter.kt                 # Legacy RecyclerView adapter (unused/backup)
│       │   ├── GalleryViewModel.kt               # Gallery state, upload, delete, download logic
│       │   ├── PhotoViewerActivity.kt            # Full-screen HD viewer with pager (Compose)
│       │   ├── SettingsActivity.kt               # Settings screen (Compose)
│       │   ├── FirstFragment.kt                  # Stub (unused)
│       │   ├── SecondFragment.kt                 # Stub (unused)
│       │   └── ui/theme/
│       │       └── Theme.kt                      # MemoriesTheme (Material 3, light & dark)
│       └── res/
│           ├── layout/                           # Legacy XML layouts (some may be unused)
│           │   ├── ui_repositories.xml
│           │   ├── activity_gallery.xml
│           │   ├── activity_photo_viewer.xml
│           │   ├── item_gallery_image.xml
│           │   └── settings_activity.xml
│           ├── drawable/                         # Icons and vector assets
│           ├── menu/                             # Menu resources
│           ├── navigation/                       # Nav graph (legacy, likely unused)
│           ├── values/                           # Strings, colors, styles, dimensions
│           ├── values-night/                     # Dark theme overrides
│           ├── xml/
│           │   ├── file_paths.xml                # FileProvider paths
│           │   ├── root_preferences.xml          # Preference screen definition
│           │   ├── backup_rules.xml
│           │   └── data_extraction_rules.xml
│           └── mipmap-*/                         # App launcher icons
├── build.gradle.kts                              # Root build config
├── settings.gradle.kts                           # Project settings
├── gradle.properties                             # Gradle/Android properties
└── Key/                                          # (Signing key or credentials — not tracked)
```

---

## Dependencies (from `app/build.gradle.kts`)

| Library                             | Purpose                            |
|-------------------------------------|------------------------------------|
| AndroidX Core KTX                   | Kotlin extensions for Android      |
| AndroidX AppCompat                  | Backward compatibility             |
| Material Components                 | Material Design widgets            |
| ConstraintLayout                    | Layout (legacy XML)                |
| Navigation Fragment/UI KTX          | Fragment navigation (legacy)       |
| CardView                            | Card widget (legacy)               |
| Preference                          | Settings screen support            |
| Retrofit 2 + Gson Converter         | HTTP client for GitHub API         |
| Glide 4.16.0                        | Image loading (legacy adapter)     |
| Compose BOM 2024.02.00              | Compose dependency management      |
| Compose UI, Graphics, Tooling       | Compose core                       |
| Compose Material 3                  | Material 3 components              |
| Compose Animation                   | Animations (AnimatedVisibility, etc.) |
| Material Icons Extended             | Full icon set                      |
| Activity Compose 1.8.2              | `setContent` / Compose integration |
| Coil Compose 2.6.0                  | Image loading for Compose          |
| Lifecycle ViewModel Compose 2.7.0   | ViewModel integration              |

---

## Important Notes for AI

1. **All screens are 100% Jetpack Compose** — do NOT suggest XML layout changes for UI modifications.
2. The XML layout files in `res/layout/` are **legacy/unused** — the app has been fully migrated to Compose.
3. `GalleryAdapter.kt`, `FirstFragment.kt`, `SecondFragment.kt` are **legacy stubs** — not actively used.
4. The `GitHubFile` data class is defined at the **bottom of `GalleryActivity.kt`**, not in its own file.
5. **No Jetpack Navigation Compose** — navigation is done via `Intent`-based `startActivity()` calls.
6. The app uses **`SharedPreferences`** (not DataStore) for settings.
7. Thumbnails are always compressed to **max ~10KB JPEG** regardless of the original format.
8. The photo viewer fetches **original full-size images** from GitHub (not thumbnails) and caches them in `filesDir/hd_cache/`.
9. The `MemoriesTheme` supports dynamic colors on Android 12+ and falls back to custom palettes.
10. The Kotlin Compose compiler plugin is version **2.0.0** (from root `build.gradle.kts`).
