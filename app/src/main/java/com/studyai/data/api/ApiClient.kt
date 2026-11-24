package com.studyai.data.api

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    const val DEFAULT_BASE_URL = "https://unripened-un-nonorientable.ngrok-free.dev/"
    private const val PREFS_NAME = "studyai_prefs"
    private const val KEY_SERVER_BASE_URL = "server_base_url"

    // URL động: hỗ trợ đổi trực tiếp sang Ngrok mới hoặc IP LAN (http://192.168.x.x:8000/) ngay trong app
    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        private set

    fun normalizeBaseUrl(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.isEmpty()) return DEFAULT_BASE_URL
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed = "http://$trimmed"
        }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SERVER_BASE_URL, null)
        if (!saved.isNullOrBlank()) {
            val normalized = normalizeBaseUrl(saved)
            if (normalized.toHttpUrlOrNull() != null) {
                baseUrl = normalized
            }
        }
    }

    fun updateBaseUrl(context: Context, newUrl: String): String {
        val normalized = normalizeBaseUrl(newUrl)
        require(normalized.toHttpUrlOrNull() != null) { "Định dạng URL không hợp lệ: $newUrl" }
        baseUrl = normalized
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_BASE_URL, normalized)
            .apply()
        return normalized
    }

    suspend fun testConnection(candidateUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalized = normalizeBaseUrl(candidateUrl)
            val parsed = normalized.toHttpUrlOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("URL không hợp lệ"))
            val probeUrl = parsed.newBuilder()
                .addPathSegments("api/v1/courses")
                .build()

            val probeClient = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()

            val t0 = System.currentTimeMillis()
            val req = Request.Builder()
                .url(probeUrl)
                .addHeader("ngrok-skip-browser-warning", "true")
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "StudyAiAndroid/1.0")
                .get()
                .build()

            probeClient.newCall(req).execute().use { resp ->
                val elapsedMs = System.currentTimeMillis() - t0
                if (resp.isSuccessful) {
                    Result.success("Kết nối thành công! (HTTP ${resp.code} • ${elapsedMs}ms)")
                } else {
                    Result.failure(RuntimeException("Máy chủ phản hồi HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
            val original = chain.request()
            val targetBase = baseUrl.toHttpUrlOrNull()
            val rewrittenUrl = if (targetBase != null) {
                val basePrefix = targetBase.encodedPath.trimEnd('/')
                val newPath = if (basePrefix.isNotEmpty()) {
                    "$basePrefix${original.url.encodedPath}"
                } else {
                    original.url.encodedPath
                }
                original.url.newBuilder()
                    .scheme(targetBase.scheme)
                    .host(targetBase.host)
                    .port(targetBase.port)
                    .encodedPath(newPath)
                    .build()
            } else {
                original.url
            }

            val request = original.newBuilder()
                .url(rewrittenUrl)
                .addHeader("ngrok-skip-browser-warning", "true")
                .addHeader("User-Agent", "StudyAiAndroid/1.0")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .writeTimeout(600, TimeUnit.SECONDS)
        .build()

    val apiService: StudyAiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(DEFAULT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(StudyAiApiService::class.java)
    }
}
