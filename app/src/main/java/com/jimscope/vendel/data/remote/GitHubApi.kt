package com.jimscope.vendel.data.remote

import com.jimscope.vendel.data.remote.dto.GitHubReleaseResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApi {

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 1
    ): Response<List<GitHubReleaseResponse>>
}
