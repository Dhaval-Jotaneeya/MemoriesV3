package com.dj.memoriesv3

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.appbar.MaterialToolbar

class PhotoViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_viewer)

        val repoName = intent.getStringExtra("REPO_NAME") ?: return finish()
        val fileName = intent.getStringExtra("FILE_NAME") ?: return finish()
        val filePath = intent.getStringExtra("FILE_PATH") ?: fileName
        val fileUrl = intent.getStringExtra("FILE_URL")
        val originalPathExtra = intent.getStringExtra("ORIGINAL_PATH")

        val toolbar = findViewById<MaterialToolbar>(R.id.viewerToolbar)
        toolbar.title = fileName
        toolbar.setNavigationOnClickListener { finish() }

        val imageView = findViewById<ImageView>(R.id.fullScreenImageView)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        val sharedPreferences = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val token = sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, "") ?: ""
        val orgName = sharedPreferences.getString(Constants.KEY_ORG_NAME, "") ?: ""

        progressBar.visibility = View.VISIBLE

        // 1. Prepare Thumbnail URL (Fallback)
        val encodedThumbPath = filePath.split('/').joinToString("/") { android.net.Uri.encode(it) }
        val thumbGlideUrl = if (token.isNotEmpty()) {
            // Use GitHub Blob URL (if available) or Contents API with raw header
            val apiUrl = fileUrl ?: "https://api.github.com/repos/$orgName/$repoName/contents/$encodedThumbPath"
            GlideUrl(apiUrl, LazyHeaders.Builder()
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github.v3.raw")
                .build())
        } else {
            // Fallback for public repos without token (avoids API rate limits)
            "https://raw.githubusercontent.com/$orgName/$repoName/HEAD/$encodedThumbPath"
        }

        // 2. Prepare Original URL (Primary)
        // Use the mapped original path if available, otherwise fallback to guessing by stripping "thumbnails/"
        val originalPath = originalPathExtra ?: filePath.replaceFirst(Regex("^thumbnails/", RegexOption.IGNORE_CASE), "")
        val encodedOriginalPath = originalPath.split('/').joinToString("/") { android.net.Uri.encode(it) }

        val originalGlideUrl = if (token.isNotEmpty()) {
            val apiUrl = "https://api.github.com/repos/$orgName/$repoName/contents/$encodedOriginalPath"
            GlideUrl(apiUrl, LazyHeaders.Builder()
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github.v3.raw")
                .build())
        } else {
            "https://raw.githubusercontent.com/$orgName/$repoName/HEAD/$encodedOriginalPath"
        }

        val thumbRequest = Glide.with(this)
            .load(thumbGlideUrl)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@PhotoViewerActivity, "Failed to load image", Toast.LENGTH_SHORT).show()
                    e?.logRootCauses("PhotoViewer-Thumb")
                    return false
                }
                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                    progressBar.visibility = View.GONE
                    return false
                }
            })

        Glide.with(this)
            .load(originalGlideUrl)
            .error(thumbRequest)
            .fitCenter()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    // Primary failed, Glide will try error request. Keep progress bar visible.
                    e?.logRootCauses("PhotoViewer-Original")
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    progressBar.visibility = View.GONE
                    return false
                }
            })
            .into(imageView)
    }
}
