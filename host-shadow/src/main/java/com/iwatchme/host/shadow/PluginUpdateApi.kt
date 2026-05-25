package com.iwatchme.host.shadow

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PluginUpdateApi {

    @GET("api/plugins/{partKey}/latest")
    suspend fun latest(
        @Path("partKey") partKey: String,
        @Query("deviceId") deviceId: String,
    ): PluginReleaseDto

    @GET("api/plugins/{partKey}/versions")
    suspend fun versions(
        @Path("partKey") partKey: String,
    ): List<PluginReleaseDto>
}

@Serializable
data class PluginReleaseDto(
    val id: Long,
    val partKey: String,
    val versionName: String,
    val versionCode: Int,
    val fileSize: Long,
    val md5: String,
    val rolloutPercent: Int,
    val rolledBack: Boolean,
    val releaseNotes: String? = null,
    val downloadUrl: String,
    val createdAt: String,
)
