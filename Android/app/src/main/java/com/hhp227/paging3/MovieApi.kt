package com.hhp227.paging3

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

interface MovieApi {
    @GET("3/movie/popular")
    suspend fun getPopularMovies(
        @Query("page") pageNumber: Int,
        @Query("api_key") apiKey: String
    ): MovieListResponse

    companion object {
        fun create(): MovieApi {
            val logger = HttpLoggingInterceptor { Log.d("API", it) }
            logger.level = HttpLoggingInterceptor.Level.BASIC
            val client = OkHttpClient.Builder()
                .addInterceptor(logger)
                .build()
            return Retrofit.Builder()
                .baseUrl("https://api.themoviedb.org/".toHttpUrlOrNull()!!)
                .client(client)
                .addConverterFactory(Json {
                    ignoreUnknownKeys = true
                }.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(MovieApi::class.java)
        }
    }
}