package com.hhp227.paging3

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import coil.decode.SvgDecoder
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.hhp227.paging3.paging.LazyPagingItems
import com.hhp227.paging3.paging.LoadState
import com.hhp227.paging3.paging.collectAsLazyPagingItems
import com.hhp227.paging3.paging.items
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(
        factory = viewModelProviderFactoryOf {
            MainViewModel(MainRepository(MovieApi.create()))
        }
    )
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(text = "MainScreen") }) },
        content = { paddingValues ->
            MovieList(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()),
                lazyPagingItems = viewModel.state.map { it.pagingData }
                    .onStart { Log.e("IDIS_TEST", "state pagingData onStart") }
                    .onCompletion { Log.e("IDIS_TEST", "state pagingData onCompletion") }
                    .collectAsLazyPagingItems(),
                onItemClick = viewModel::onItemClick
            )
        }
    )
}

@Composable
fun MovieList(modifier: Modifier, lazyPagingItems: LazyPagingItems<Movie>, onItemClick: (Movie?) -> Unit) {
    SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing = lazyPagingItems.loadState.refresh is LoadState.Loading),
        onRefresh = lazyPagingItems::refresh,
        modifier = modifier
    ) {
        LazyColumn {
            items(lazyPagingItems) { movie ->
                MovieItem(movie = movie, onItemClick = onItemClick)
            }
            item {
                when {
                    lazyPagingItems.loadState.refresh is LoadState.Loading -> {
                        LoadingView(modifier = Modifier.fillParentMaxSize())
                    }
                    lazyPagingItems.loadState.append is LoadState.Loading -> {
                        LoadingItem()
                    }
                    lazyPagingItems.loadState.refresh is LoadState.Error -> {
                        ErrorItem(
                            message = (lazyPagingItems.loadState.refresh as LoadState.Error).error.localizedMessage!!,
                            modifier = Modifier.fillParentMaxSize(),
                            onClickRetry = {  }
                        )
                    }
                    lazyPagingItems.loadState.append is LoadState.Error -> {
                        ErrorItem(
                            message = (lazyPagingItems.loadState.append as LoadState.Error).error.localizedMessage!!,
                            onClickRetry = {  }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MovieItem(movie: Movie?, onItemClick: (Movie?) -> Unit) {
    Row(
        modifier = Modifier
            .padding(start = 16.dp, top = 16.dp, end = 16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MovieTitle(
            movie?.title!!,
            modifier = Modifier.weight(1f),
            onItemClick = { onItemClick(movie) }
        )
        /*MovieImage(
            "https://image.tmdb.org/t/p/w500" + movie.backdropPath,
            modifier = Modifier
                .padding(start = 16.dp)
                .size(90.dp)
        )*/
    }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun MovieImage(
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    Image(
        painter = rememberImagePainter(
            data = imageUrl,
            imageLoader = ImageLoader.Builder(context = LocalContext.current)
                .crossfade(true)
                .componentRegistry {
                    add(SvgDecoder(LocalContext.current))
                }
                .build()
        ),
        contentScale = ContentScale.Crop,
        contentDescription = "Post image",
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    )
}

@Composable
fun MovieTitle(
    title: String,
    modifier: Modifier = Modifier,
    onItemClick: () -> Unit
) {
    Text(
        text = title,
        modifier = modifier.clickable(onClick = onItemClick),
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        maxLines = 2,
        style = MaterialTheme.typography.labelLarge
    )
}