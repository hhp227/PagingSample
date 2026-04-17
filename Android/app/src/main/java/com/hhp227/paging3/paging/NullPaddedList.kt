package com.hhp227.paging3.paging

// 원본과 동일
public interface NullPaddedList<T> {
    public val placeholdersBefore: Int
    public fun getFromStorage(localIndex: Int): T
    public val placeholdersAfter: Int
    public val size: Int
    public val storageCount: Int
}