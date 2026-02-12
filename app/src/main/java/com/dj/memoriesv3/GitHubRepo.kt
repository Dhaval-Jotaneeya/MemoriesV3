package com.dj.memoriesv3

import com.google.gson.annotations.SerializedName

data class GitHubRepo(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("updated_at") val updatedAt: String?
)
