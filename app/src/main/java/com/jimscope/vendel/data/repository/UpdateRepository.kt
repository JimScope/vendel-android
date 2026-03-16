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
            val response = gitHubApi.getLatestRelease(OWNER, REPO)
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
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
