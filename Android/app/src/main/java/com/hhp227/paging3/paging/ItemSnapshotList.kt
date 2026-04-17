package com.hhp227.paging3.paging

import androidx.annotation.IntRange

public class ItemSnapshotList<T>(
    /**
     * Number of placeholders before the presented [items], 0 if
     * [enablePlaceholders][androidx.paging.PagingConfig.enablePlaceholders] is `false`.
     */
    @IntRange(from = 0)
    public val placeholdersBefore: Int,
    /**
     * Number of placeholders after the presented [items], 0 if
     * [enablePlaceholders][androidx.paging.PagingConfig.enablePlaceholders] is `false`.
     */
    @IntRange(from = 0)
    public val placeholdersAfter: Int,
    /**
     * The presented data, excluding placeholders.
     */
    public val items: List<T>
) : AbstractList<T?>() {

    /**
     * Size of [ItemSnapshotList] including placeholders.
     *
     * To get the size excluding placeholders, use [List.size] on [items] directly.
     *
     * @see items
     */
    public override val size: Int
        get() = placeholdersBefore + items.size + placeholdersAfter

    /**
     * Returns the item at [index], where [index] includes the position of placeholders. If [index]
     * points to the position of a placeholder, `null` is returned.
     *
     * To get the size using an index excluding placeholders, use [List.size] on [items] directly.
     *
     * @throws IndexOutOfBoundsException if [index] < 0 or [index] > [size].
     */
    override fun get(index: Int): T? {
        return when (index) {
            in 0 until placeholdersBefore -> null
            in placeholdersBefore until (placeholdersBefore + items.size) -> {
                items[index - placeholdersBefore]
            }
            in (placeholdersBefore + items.size) until size -> null
            else -> throw IndexOutOfBoundsException(
                "Illegal attempt to access index $index in ItemSnapshotList of size $size"
            )
        }
    }
}
