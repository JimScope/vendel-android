package com.jimscope.vendel.data.repository

import com.jimscope.vendel.data.remote.GitHubApi
import com.jimscope.vendel.data.remote.dto.GitHubReleaseResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val gitHubApi: GitHubApi
) {
    suspend fun getLatestRelease(): Result<GitHubReleaseResponse> {
        return runCatching {
            val response = gitHubApi.getReleases(OWNER, REPO, perPage = 1)
            val body = response.body()
            if (response.isSuccessful && !body.isNullOrEmpty()) {
                body.first()
            } else if (response.isSuccessful && body.isNullOrEmpty()) {
                throw Exception("No releases published yet")
            } else {
                throw Exception("GitHub API error: ${response.code()}")
            }
        }
    }

    companion object {
        const val OWNER = "JimScope"
        const val REPO = "vendel-android"
    }
}
