package com.example.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface ReportApi {
    @POST
    suspend fun uploadReport(
        @Url url: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: RequestBody
    ): Response<ResponseBody>

    companion object {
        private var instance: ReportApi? = null

        fun get(): ReportApi {
            return instance ?: synchronized(this) {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }

                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .addInterceptor(logging)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl("http://localhost/") // Base URL placeholder, dynamic @Url is used
                    .client(okHttpClient)
                    .build()

                val api = retrofit.create(ReportApi::class.java)
                instance = api
                api
            }
        }

        fun createJsonBody(jsonString: String): RequestBody {
            return jsonString.toRequestBody("application/json; charset=utf-8".toMediaType())
        }
    }
}
