package com.studyai.data.api

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Ngrok Public Tunnel URL: Cho phép điện thoại thật và máy ảo kết nối mượt mà
    var baseUrl: String = "https://unripened-un-nonorientable.ngrok-free.dev/"

    private val gson = GsonBuilder()
        .registerTypeAdapter(Boolean::class.java, JsonDeserializer { json, _, _ ->
            try {
                if (json.isJsonPrimitive) {
                    val prim = json.asJsonPrimitive
                    if (prim.isBoolean) prim.asBoolean
                    else if (prim.isNumber) prim.asInt != 0
                    else if (prim.isString) prim.asString.equals("true", ignoreCase = true) || prim.asString == "1"
                    else false
                } else false
            } catch (_: Exception) {
                false
            }
        })
        .registerTypeAdapter(java.lang.Boolean::class.java, JsonDeserializer { json, _, _ ->
            try {
                if (json.isJsonPrimitive) {
                    val prim = json.asJsonPrimitive
                    if (prim.isBoolean) prim.asBoolean
                    else if (prim.isNumber) prim.asInt != 0
                    else if (prim.isString) prim.asString.equals("true", ignoreCase = true) || prim.asString == "1"
                    else false
                } else false
            } catch (_: Exception) {
                java.lang.Boolean.FALSE
            }
        })
        .registerTypeAdapter(Float::class.java, JsonDeserializer { json, _, _ ->
            try {
                if (json.isJsonPrimitive) {
                    val prim = json.asJsonPrimitive
                    if (prim.isNumber) prim.asFloat
                    else if (prim.isString) prim.asString.toFloatOrNull() ?: 0.0f
                    else 0.0f
                } else 0.0f
            } catch (_: Exception) {
                0.0f
            }
        })
        .registerTypeAdapter(java.lang.Float::class.java, JsonDeserializer { json, _, _ ->
            try {
                if (json.isJsonPrimitive) {
                    val prim = json.asJsonPrimitive
                    if (prim.isNumber) prim.asFloat
                    else if (prim.isString) prim.asString.toFloatOrNull() ?: 0.0f
                    else 0.0f
                } else 0.0f
            } catch (_: Exception) {
                java.lang.Float.valueOf(0.0f)
            }
        })
        .setLenient()
        .create()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("ngrok-skip-browser-warning", "true")
                .addHeader("User-Agent", "StudyAiAndroid/1.0")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val apiService: StudyAiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(StudyAiApiService::class.java)
    }
}
