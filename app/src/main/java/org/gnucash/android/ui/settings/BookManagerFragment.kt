/*
 * Copyright (c) 2016 Ngewi Fet <ngewif@gmail.com>
 * Copyright (C) 2022 Xilin Jia https://github.com/XilinJia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.ui.settings

import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.ListFragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.appContext
import org.gnucash.android.app.GnuCashApplication.Companion.defaultCurrencyCode
import org.gnucash.android.db.DatabaseCursorLoader
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseSchema.BookEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter.Companion.instance
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.ui.account.AccountsActivity.Companion.createDefaultAccounts
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.settings.dialog.DeleteBookConfirmationDialog.Companion.newInstance
import org.gnucash.android.util.BookUtils.loadBook
import org.gnucash.android.util.PreferencesHelper.getLastExportTime
import java.sql.Timestamp

/**
 * Fragment for managing the books in the database
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */

class BookManagerFragment : ListFragment(), LoaderManager.LoaderCallbacks<Cursor>, Refreshable {
    private var mCursorAdapter: SimpleCursorAdapter? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_book_list, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mCursorAdapter = BooksCursorAdapter(
            activity,
            R.layout.cardview_book,
            null,
            arrayOf(BookEntry.COLUMN_DISPLAY_NAME, BookEntry.COLUMN_SOURCE_URI),
            intArrayOf(R.id.primary_text, R.id.secondary_text)
        )
        listAdapter = mCursorAdapter
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar
        assert(actionBar != null)
        actionBar!!.setHomeButtonEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setTitle(R.string.title_manage_books)
        setHasOptionsMenu(true)
        listView.choiceMode = ListView.CHOICE_MODE_NONE
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.book_list_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_create_book -> {
                createDefaultAccounts(defaultCurrencyCode, activity)
                true
            }

            else -> false
        }
    }

    override fun refresh() {
        loaderManager.restartLoader(0, null, this)
    }

    override fun refresh(uid: String?) {
        refresh()
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        Log.d(LOG_TAG, "Creating loader for books")
        return BooksCursorLoader(activity)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
        Log.d(LOG_TAG, "Finished loading books from database")
        mCursorAdapter!!.swapCursor(data)
        mCursorAdapter!!.notifyDataSetChanged()
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        Log.d(LOG_TAG, "Resetting books list loader")
        mCursorAdapter!!.swapCursor(null)
    }

    private inner class BooksCursorAdapter(
        context: Context?,
        layout: Int,
        c: Cursor?,
        from: Array<String?>?,
        to: IntArray?
    ) : SimpleCursorAdapter(context, layout, c, from, to, 0) {
        override fun bindView(view: View, context: Context, cursor: Cursor) {
            super.bindView(view, context, cursor)
            val bookUID = cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_UID))
            setLastExportedText(view, bookUID)
            setStatisticsText(view, bookUID)
            setUpMenu(view, context, cursor, bookUID)
            view.setOnClickListener { //do nothing if the active book is tapped
                if (instance.activeBookUID != bookUID) {
                    loadBook(bookUID)
                }
            }
        }

        private fun setUpMenu(view: View, context: Context, cursor: Cursor, bookUID: String) {
            val bookName = cursor.getString(
                cursor.getColumnIndexOrThrow(BookEntry.COLUMN_DISPLAY_NAME)
            )
            val optionsMenu = view.findViewById<View>(R.id.options_menu) as ImageView
            optionsMenu.setOnClickListener { v ->
                val popupMenu = PopupMenu(context, v)
                val menuInflater = popupMenu.menuInflater
                menuInflater.inflate(R.menu.book_context_menu, popupMenu.menu)
                popupMenu.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.ctx_menu_rename_book -> handleMenuRenameBook(bookName, bookUID)
                        R.id.ctx_menu_sync_book ->                                     //TODO implement sync
                            false

                        R.id.ctx_menu_delete_book -> handleMenuDeleteBook(bookUID)
                        else -> true
                    }
                })
                val activeBookUID = instance.activeBookUID
                if ((activeBookUID == bookUID)) { //we cannot delete the active book
                    popupMenu.menu.findItem(R.id.ctx_menu_delete_book).isEnabled = false
                }
                popupMenu.show()
            }
        }

        private fun handleMenuDeleteBook(bookUID: String): Boolean {
            val dialog = newInstance(bookUID)
            dialog.show((fragmentManager)!!, "delete_book")
            dialog.setTargetFragment(this@BookManagerFragment, 0)
            return true
        }

        /**
         * Opens a dialog for renaming a book
         * @param bookName Current name of the book
         * @param bookUID GUID of the book
         * @return `true`
         */
        private fun handleMenuRenameBook(bookName: String, bookUID: String): Boolean {
            val dialogBuilder = AlertDialog.Builder(
                (activity)!!
            )
            dialogBuilder.setTitle(R.string.title_rename_book)
                .setView(R.layout.dialog_rename_book)
                .setPositiveButton(R.string.btn_rename) { dialog, _ ->
                    val bookTitle = (dialog as AlertDialog).findViewById<View>(R.id.input_book_title) as EditText?
                    instance
                        .updateRecord(bookUID,
                            BookEntry.COLUMN_DISPLAY_NAME,
                            bookTitle!!.text.toString().trim { it <= ' ' })
                    refresh()
                }
                .setNegativeButton(R.string.btn_cancel
                ) { dialog, _ -> dialog.dismiss() }
            val dialog = dialogBuilder.create()
            dialog.show()
            (dialog.findViewById<View>(R.id.input_book_title) as TextView?)!!.text = bookName
            return true
        }

        private fun setLastExportedText(view: View, bookUID: String) {
            val labelLastSync = view.findViewById<View>(R.id.label_last_sync) as TextView
            labelLastSync.setText(R.string.label_last_export_time)
            val lastSyncTime = getLastExportTime(bookUID)
            val lastSyncText = view.findViewById<View>(R.id.last_sync_time) as TextView
            if (lastSyncTime.equals(Timestamp(0))) lastSyncText.setText(R.string.last_export_time_never) else lastSyncText.text =
                lastSyncTime.toString()
        }

        private fun setStatisticsText(view: View, bookUID: String) {
            val dbHelper = DatabaseHelper(appContext, bookUID)
            val db = dbHelper.readableDatabase
            val trnAdapter = TransactionsDbAdapter(db, SplitsDbAdapter(db))
            val transactionCount = trnAdapter.recordsCount.toInt()
            val transactionStats =
                resources.getQuantityString(R.plurals.book_transaction_stats, transactionCount, transactionCount)
            val accountsDbAdapter = AccountsDbAdapter(db, trnAdapter)
            val accountsCount = accountsDbAdapter.recordsCount.toInt()
            val accountStats = resources.getQuantityString(R.plurals.book_account_stats, accountsCount, accountsCount)
            val stats = "$accountStats, $transactionStats"
            val statsText = view.findViewById<View>(R.id.secondary_text) as TextView
            statsText.text = stats
            if ((bookUID == instance.activeBookUID)) {
                (view.findViewById<View>(R.id.primary_text) as TextView)
                    .setTextColor(ContextCompat.getColor((context)!!, R.color.theme_primary))
            }
        }
    }

    /**
     * [DatabaseCursorLoader] for loading the book list from the database
     * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
     */
    private class BooksCursorLoader(context: Context?) : DatabaseCursorLoader(context) {
        override fun loadInBackground(): Cursor {
            val booksDbAdapter = instance
            val cursor = booksDbAdapter.fetchAllRecords()
            registerContentObserver(cursor)
            return cursor
        }
    }

    companion object {
        private const val LOG_TAG = "BookManagerFragment"
    }
}