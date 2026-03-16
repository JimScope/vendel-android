package com.jimscope.vendel.domain

import com.jimscope.vendel.data.repository.UpdateRepository
import javax.inject.Inject

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val downloadUrl: String,
    val releasePageUrl: String,
    val isUpdateAvailable: Boolean
)

class CheckForUpdateUseCase @Inject constructor(
    private val updateRepository: UpdateRepository
) {
    suspend operator fun invoke(currentVersionName: String): Result<UpdateInfo> {
        return updateRepository.getLatestRelease().map { release ->
            val latestTag = release.tagName.removePrefix("v")
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            UpdateInfo(
                currentVersion = currentVersionName,
                latestVersion = latestTag,
                downloadUrl = apkAsset?.browserDownloadUrl ?: release.htmlUrl,
                releasePageUrl = release.htmlUrl,
                isUpdateAvailable = isNewer(latestTag, currentVersionName)
            )
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
