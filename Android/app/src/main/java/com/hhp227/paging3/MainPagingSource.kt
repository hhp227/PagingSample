package com.hhp227.paging3

import android.util.Log
import com.hhp227.paging3.paging.extension.PagingSource
import com.hhp227.paging3.paging.extension.PagingState
import kotlinx.coroutines.delay

class MainPagingSource(private val repository: MainRepository, private val apiKey: String) : PagingSource<Int, Movie>() {
    override fun getRefreshKey(state: PagingState<Int, Movie>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Movie> {
        val nextPage = params.key ?: 1
        val movieListResponse = repository.getPopularMovies(nextPage, apiKey)
        //Log.e("TEST", "load: $nextPage, movieListResponse: ${movieListResponse.results.size}")
        delay(1000)
        return try {
            LoadResult.Page(
                data = movieListResponse.results,
                prevKey = if (nextPage == 1) null else nextPage - 1,
                nextKey = movieListResponse.page.plus(1)
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}