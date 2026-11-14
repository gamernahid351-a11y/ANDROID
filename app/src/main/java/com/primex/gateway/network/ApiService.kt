package com.primex.gateway.network

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

data class CreateUserRequest(val name: String, val password: String)
data class LoginRequest(val username: String, val password: String)
data class TransactionRequest(
    val trxId: String,
    val amount: Double,
    val method: String,
    val senderNumber: String
)

data class UserResponse(
    val username: String? = null,
    val name: String? = null,
    val token: String? = null,
    val specialNumber: String? = null,
    val apiKey: String? = null,
    val balance: Double? = null,
    val smsEndpoint: String? = null,
    val createdAt: String? = null,
    val error: String? = null,
    val message: String? = null
)

data class TrxResponse(
    val success: Boolean? = null,
    val status: String? = null,
    val trxId: String? = null,
    val error: String? = null,
    val message: String? = null
)

data class ServerStatus(
    val online: Boolean = false,
    val upstreamUrl: String = "",
    val latencyMs: Long = 0,
    val mode: String = "offline"
)

interface ApiService {
    @POST("api/user/create")
    suspend fun createUser(@Body req: CreateUserRequest): UserResponse

    @POST("api/user/login")
    suspend fun login(@Body req: LoginRequest): UserResponse

    @GET("api/user/find/{username}")
    suspend fun findUser(@Path("username") username: String): UserResponse

    @GET("api/server/status")
    suspend fun getServerStatus(): ServerStatus

    @POST("api/trx/sms-receive/{username}")
    suspend fun sendTransaction(
        @Path("username") username: String,
        @Body req: TransactionRequest
    ): TrxResponse
}

object RetrofitClient {
    private var instance: ApiService? = null
    private var currentBaseUrl: String = "http://5.83.134.223:3000/"

    fun get(baseUrl: String = currentBaseUrl): ApiService {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (instance == null || url != currentBaseUrl) {
            currentBaseUrl = url
            instance = buildClient(url)
        }
        return instance!!
    }

    private fun buildClient(url: String): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val okhttp = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(url)
            .client(okhttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun reset() { instance = null }
}
