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
        val (latestCore, latestPre) = splitSemver(latest)
        val (currentCore, currentPre) = splitSemver(current)
        for (i in 0 until maxOf(latestCore.size, currentCore.size)) {
            val l = latestCore.getOrElse(i) { 0 }
            val c = currentCore.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return when {
            latestPre == null && currentPre == null -> false
            latestPre == null && currentPre != null -> true
            latestPre != null && currentPre == null -> false
            else -> latestPre!! > currentPre!!
        }
    }

    private fun splitSemver(version: String): Pair<List<Int>, String?> {
        val parts = version.split("-", limit = 2)
        val core = parts[0].split(".").mapNotNull { it.toIntOrNull() }
        val pre = parts.getOrNull(1)
        return core to pre
    }
}
