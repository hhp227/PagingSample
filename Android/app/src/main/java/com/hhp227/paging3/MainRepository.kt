package com.hhp227.paging3

class MainRepository(
    private val movieApi: MovieApi
) {
    suspend fun getPopularMovies(pageNumber: Int, apiKey: String) = movieApi.getPopularMovies(pageNumber, apiKey)
}