package com.hhp227.paging3.paging.pager

import com.hhp227.paging3.paging.PagingData
import com.hhp227.paging3.paging.extension.PagingConfig
import com.hhp227.paging3.paging.extension.PagingSource
import com.hhp227.paging3.paging.extension.RemoteMediator
import kotlinx.coroutines.flow.Flow

// 원본과 동일
class Pager<Key : Any, Value : Any>(
    config: PagingConfig,
    initialKey: Key? = null,
    remoteMediator: RemoteMediator<Key, Value>?,
    pagingSourceFactory: () -> PagingSource<Key, Value>
) {
    @JvmOverloads
    public constructor(
        config: PagingConfig,
        initialKey: Key? = null,
        pagingSourceFactory: () -> PagingSource<Key, Value>
    ) : this(config, initialKey, null, pagingSourceFactory)

    val flow: Flow<PagingData<Value>> = PageFetcher(
        pagingSourceFactory = pagingSourceFactory,
        initialKey = initialKey,
        config = config,
        remoteMediator = remoteMediator
    ).flow
}
