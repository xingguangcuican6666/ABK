package com.abk.kernel.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    private const val GITHUB_API_BASE = "https://api.github.com/"
    private const val GITHUB_BASE = "https://github.com/"

    private fun buildOkHttpClient(token: String? = null): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .header("X-GitHub-Api-Version", "2022-11-28")
                if (original.header("Accept").isNullOrBlank()) {
                    builder.header("Accept", "application/vnd.github+json")
                }
                if (!token.isNullOrBlank()) {
                    builder.header("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    internal fun createApiService(
        token: String? = null,
        baseUrl: String = GITHUB_API_BASE
    ): GitHubApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(buildOkHttpClient(token))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApiService::class.java)
    }

    fun createAuthService(): GitHubAuthService {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(GITHUB_BASE)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubAuthService::class.java)
    }
}
