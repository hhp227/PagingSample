package com.hhp227.paging3

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hhp227.paging3.paging.PagingData
import com.hhp227.paging3.paging.cachedIn
import com.hhp227.paging3.paging.extension.PagingConfig
import com.hhp227.paging3.paging.filter
import com.hhp227.paging3.paging.pager.Pager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(mainRepository: MainRepository) : ViewModel() {
    val movies: Flow<PagingData<Movie>> = Pager(PagingConfig(pageSize = 10)) {
        MainPagingSource(mainRepository, API_KEY)
    }.flow

    fun onItemClick(movie: Movie?) {
        val pagingData = state.value.pagingData.filter { it.id != movie?.id }
        state.value = state.value.copy(pagingData = pagingData)
        Log.e("TEST", "onItemClick: ${movie?.id}")
    }

    ///
    val state = MutableStateFlow(State())

    init {
        Pager(PagingConfig(pageSize = 10)) {
            MainPagingSource(mainRepository, API_KEY)
        }
            .flow
            .cachedIn(viewModelScope)
            .onEach {
                state.value = state.value.copy(pagingData = it)
            }
            .launchIn(viewModelScope)
    }

    data class State(val pagingData: PagingData<Movie> = PagingData.empty())

    companion object {
        const val API_KEY = "a86526535fa0fc12d269041691633aed"
    }
}