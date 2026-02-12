package com.dj.memoriesv3

import com.google.gson.annotations.SerializedName

data class GitHubTreeResponse(
    @SerializedName("sha") val sha: String,
    @SerializedName("url") val url: String,
    @SerializedName("tree") val tree: List<GitHubTreeItem>,
    @SerializedName("truncated") val truncated: Boolean
)

data class GitHubTreeItem(
    @SerializedName("path") val path: String,
    @SerializedName("mode") val mode: String,
    @SerializedName("type") val type: String,
    @SerializedName("sha") val sha: String,
    @SerializedName("size") val size: Long?,
    @SerializedName("url") val url: String?
)