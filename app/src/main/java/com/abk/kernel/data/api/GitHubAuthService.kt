package com.abk.kernel.data.api

import com.abk.kernel.data.model.AccessTokenResponse
import com.abk.kernel.data.model.DeviceCodeResponse
import retrofit2.Response
import retrofit2.http.*

interface GitHubAuthService {

    @POST("login/device/code")
    @FormUrlEncoded
    suspend fun requestDeviceCode(
        @Field("client_id") clientId: String,
        @Field("scope") scope: String = "repo workflow"
    ): Response<DeviceCodeResponse>

    @POST("login/oauth/access_token")
    @FormUrlEncoded
    suspend fun pollAccessToken(
        @Field("client_id") clientId: String,
        @Field("device_code") deviceCode: String,
        @Field("grant_type") grantType: String = "urn:ietf:params:oauth:grant-type:device_code"
    ): Response<AccessTokenResponse>
}
