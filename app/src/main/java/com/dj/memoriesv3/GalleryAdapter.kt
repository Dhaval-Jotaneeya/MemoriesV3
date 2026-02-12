package com.dj.memoriesv3

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

class GalleryAdapter(
    private var images: List<GitHubFile>,
    private val onItemClick: (GitHubFile) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.idIVImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = images[position]
        val context = holder.itemView.context
        
        val sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val token = sharedPreferences.getString(Constants.KEY_GITHUB_TOKEN, "") ?: ""

        val glideUrl = if (token.isNotEmpty()) {
             GlideUrl(file.getImageUrl(), LazyHeaders.Builder()
                .addHeader("Authorization", "Bearer $token")
                .build())
        } else {
            file.getImageUrl()
        }

        Glide.with(context).load(glideUrl).centerCrop().into(holder.imageView)

        holder.itemView.setOnClickListener { onItemClick(file) }
    }

    override fun getItemCount() = images.size

    fun updateData(newImages: List<GitHubFile>) {
        images = newImages
        notifyDataSetChanged()
    }
}