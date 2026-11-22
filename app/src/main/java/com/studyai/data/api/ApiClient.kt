package com.studyai.data.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // 10.0.2.2 là địa chỉ IP của máy Host khi chạy trên Android Emulator
    // Đối với thiết bị thật, đổi thành IP LAN (ví dụ: 192.168.1.x)
    var baseUrl: String = "http://10.0.2.2:8000/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: StudyAiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StudyAiApiService::class.java)
    }
}
