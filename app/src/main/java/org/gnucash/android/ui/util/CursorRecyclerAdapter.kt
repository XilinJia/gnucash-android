/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Matthieu Harlé
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.gnucash.android.ui.util

import android.database.ContentObserver
import android.database.Cursor
import android.database.DataSetObserver
import android.os.Handler
import android.widget.Filter
import android.widget.FilterQueryProvider
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView

/**
 * Provide a [RecyclerView.Adapter] implementation with cursor
 * support.
 *
 * Child classes only need to implement [.onCreateViewHolder] and
 * [.onBindViewHolderCursor].
 *
 * This class does not implement deprecated fields and methods from CursorAdapter! Incidentally,
 * only [android.widget.CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER] is available, so the
 * flag is implied, and only the Adapter behavior using this flag has been ported.
 *
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 * @param <VH> {@inheritDoc}
 *
 * @see RecyclerView.Adapter
 *
 * @see android.widget.CursorAdapter
 *
 * @see android.widget.Filterable
 *
 * @see CursorFilter.CursorFilterClient
</VH> */
abstract class CursorRecyclerAdapter<VH : RecyclerView.ViewHolder?>(cursor: Cursor?) : RecyclerView.Adapter<VH>(),
    Filterable, CursorFilter.CursorFilterClient {
    private var mDataValid = false
    private var mRowIDColumn = 0
    final override var cursor: Cursor? = null
        private set
    private var mChangeObserver: ChangeObserver? = null
    private var mDataSetObserver: DataSetObserver? = null
    private var mCursorFilter: CursorFilter? = null
    /**
     * Returns the query filter provider used for filtering. When the
     * provider is null, no filtering occurs.
     *
     * @return the current filter query provider or null if it does not exist
     *
     * @see .setFilterQueryProvider
     * @see .runQueryOnBackgroundThread
     */
    /**
     * Sets the query filter provider used to filter the current Cursor.
     * The provider's
     * [android.widget.FilterQueryProvider.runQuery]
     * method is invoked when filtering is requested by a client of
     * this adapter.
     *
     * @param filterQueryProvider the filter query provider or null to remove it
     *
     * @see .getFilterQueryProvider
     * @see .runQueryOnBackgroundThread
     */
    var filterQueryProvider: FilterQueryProvider? = null

    init {
        init(cursor)
    }

    fun init(c: Cursor?) {
        val cursorPresent = c != null
        cursor = c
        mDataValid = cursorPresent
        mRowIDColumn = if (cursorPresent) c!!.getColumnIndexOrThrow("_id") else -1
        mChangeObserver = ChangeObserver()
        mDataSetObserver = MyDataSetObserver()
        if (cursorPresent) {
            if (mChangeObserver != null) c!!.registerContentObserver(mChangeObserver)
            if (mDataSetObserver != null) c!!.registerDataSetObserver(mDataSetObserver)
        }
    }

    /**
     * This method will move the Cursor to the correct position and call
     * [.onBindViewHolderCursor].
     *
     * @param holder {@inheritDoc}
     * @param i {@inheritDoc}
     */
    override fun onBindViewHolder(holder: VH, i: Int) {
        check(mDataValid) { "this should only be called when the cursor is valid" }
        check(cursor!!.moveToPosition(i)) { "couldn't move cursor to position $i" }
        onBindViewHolderCursor(holder, cursor)
    }

    /**
     * See [android.widget.CursorAdapter.bindView],
     * [.onBindViewHolder]
     *
     * @param holder View holder.
     * @param cursor The cursor from which to get the data. The cursor is already
     * moved to the correct position.
     */
    abstract fun onBindViewHolderCursor(holder: VH, cursor: Cursor?)
    override fun getItemCount(): Int {
        return if (mDataValid && cursor != null) {
            cursor!!.count
        } else {
            0
        }
    }

    /**
     * @see android.widget.ListAdapter.getItemId
     */
    override fun getItemId(position: Int): Long {
        return if (mDataValid && cursor != null) {
            if (cursor!!.moveToPosition(position)) {
                cursor!!.getLong(mRowIDColumn)
            } else {
                0
            }
        } else {
            0
        }
    }

    /**
     * Change the underlying cursor to a new cursor. If there is an existing cursor it will be
     * closed.
     *
     * @param cursor The new cursor to be used
     */
    override fun changeCursor(cursor: Cursor?) {
        val old = swapCursor(cursor)
        old?.close()
    }

    /**
     * Swap in a new Cursor, returning the old Cursor.  Unlike
     * [.changeCursor], the returned old Cursor is *not*
     * closed.
     *
     * @param newCursor The new cursor to be used.
     * @return Returns the previously set Cursor, or null if there was not one.
     * If the given new Cursor is the same instance is the previously set
     * Cursor, null is also returned.
     */
    fun swapCursor(newCursor: Cursor?): Cursor? {
        if (newCursor === cursor) {
            return null
        }
        val oldCursor = cursor
        if (oldCursor != null) {
            if (mChangeObserver != null) oldCursor.unregisterContentObserver(mChangeObserver)
            if (mDataSetObserver != null) oldCursor.unregisterDataSetObserver(mDataSetObserver)
        }
        cursor = newCursor
        if (newCursor != null) {
            if (mChangeObserver != null) newCursor.registerContentObserver(mChangeObserver)
            if (mDataSetObserver != null) newCursor.registerDataSetObserver(mDataSetObserver)
            mRowIDColumn = newCursor.getColumnIndexOrThrow("_id")
            mDataValid = true
            // notify the observers about the new cursor
            notifyDataSetChanged()
        } else {
            mRowIDColumn = -1
            mDataValid = false
            // notify the observers about the lack of a data set
            // notifyDataSetInvalidated();
            notifyItemRangeRemoved(0, itemCount)
        }
        return oldCursor
    }

    /**
     *
     * Converts the cursor into a CharSequence. Subclasses should override this
     * method to convert their results. The default implementation returns an
     * empty String for null values or the default String representation of
     * the value.
     *
     * @param cursor the cursor to convert to a CharSequence
     * @return a CharSequence representing the value
     */
    override fun convertToString(cursor: Cursor?): CharSequence {
        return cursor?.toString() ?: ""
    }

    /**
     * Runs a query with the specified constraint. This query is requested
     * by the filter attached to this adapter.
     *
     * The query is provided by a
     * [android.widget.FilterQueryProvider].
     * If no provider is specified, the current cursor is not filtered and returned.
     *
     * After this method returns the resulting cursor is passed to [.changeCursor]
     * and the previous cursor is closed.
     *
     * This method is always executed on a background thread, not on the
     * application's main thread (or UI thread.)
     *
     * Contract: when constraint is null or empty, the original results,
     * prior to any filtering, must be returned.
     *
     * @param constraint the constraint with which the query must be filtered
     *
     * @return a Cursor representing the results of the new query
     *
     * @see .getFilter
     * @see .getFilterQueryProvider
     * @see .setFilterQueryProvider
     */
    override fun runQueryOnBackgroundThread(constraint: CharSequence?): Cursor? {
        return if (filterQueryProvider != null) {
            filterQueryProvider!!.runQuery(constraint)
        } else cursor
    }

    override fun getFilter(): Filter {
        if (mCursorFilter == null) {
            mCursorFilter = CursorFilter(this)
        }
        return mCursorFilter!!
    }

    /**
     * Called when the [ContentObserver] on the cursor receives a change notification.
     * Can be implemented by sub-class.
     *
     * @see ContentObserver.onChange
     */
    protected fun onContentChanged() {
        notifyDataSetChanged()
    }

    private inner class ChangeObserver : ContentObserver(Handler()) {
        override fun deliverSelfNotifications(): Boolean {
            return true
        }

        override fun onChange(selfChange: Boolean) {
            onContentChanged()
        }
    }

    private inner class MyDataSetObserver : DataSetObserver() {
        override fun onChanged() {
            mDataValid = true
            notifyDataSetChanged()
        }

        override fun onInvalidated() {
            mDataValid = false
            // notifyDataSetInvalidated();
            notifyItemRangeRemoved(0, itemCount)
        }
    }
    /**
     *
     * The CursorFilter delegates most of the work to the CursorAdapter.
     * Subclasses should override these delegate methods to run the queries
     * and convert the results into String that can be used by auto-completion
     * widgets.
     */
}

internal class CursorFilter(var mClient: CursorFilterClient) : Filter() {
    internal interface CursorFilterClient {
        fun convertToString(cursor: Cursor?): CharSequence
        fun runQueryOnBackgroundThread(constraint: CharSequence?): Cursor?
        val cursor: Cursor?
        fun changeCursor(cursor: Cursor?)
    }

    override fun convertResultToString(resultValue: Any): CharSequence {
        return mClient.convertToString(resultValue as Cursor)
    }

    override fun performFiltering(constraint: CharSequence): FilterResults {
        val cursor = mClient.runQueryOnBackgroundThread(constraint)
        val results = FilterResults()
        if (cursor != null) {
            results.count = cursor.count
            results.values = cursor
        } else {
            results.count = 0
            results.values = null
        }
        return results
    }

    override fun publishResults(constraint: CharSequence, results: FilterResults) {
        val oldCursor = mClient.cursor
        if (results.values != null && results.values !== oldCursor) {
            mClient.changeCursor(results.values as Cursor)
        }
    }
}