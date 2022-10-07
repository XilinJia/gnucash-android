/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.transaction

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.ButterKnife
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseCursorLoader
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Transaction
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity.Companion.updateAllWidgets
import org.gnucash.android.ui.settings.PreferenceActivity
import org.gnucash.android.ui.transaction.TransactionsActivity.Companion.displayBalance
import org.gnucash.android.ui.transaction.TransactionsActivity.Companion.getPrettyDateFormat
import org.gnucash.android.ui.transaction.dialog.BulkMoveDialogFragment
import org.gnucash.android.ui.util.CursorRecyclerAdapter
import org.gnucash.android.ui.util.widget.EmptyRecyclerView
import org.gnucash.android.util.BackupManager

/**
 * List Fragment for displaying list of transactions for an account
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class TransactionsListFragment : Fragment(), Refreshable, LoaderManager.LoaderCallbacks<Cursor> {
    private var mTransactionsDbAdapter: TransactionsDbAdapter? = null
    private var mAccountUID: String? = null
    private var mUseCompactView = false
    private var mTransactionRecyclerAdapter: TransactionRecyclerAdapter? = null

    @JvmField
	@BindView(R.id.transaction_recycler_view)
    var mRecyclerView: EmptyRecyclerView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        val args = arguments
        mAccountUID = args!!.getString(UxArgument.SELECTED_ACCOUNT_UID)
        mUseCompactView = PreferenceActivity.activeBookSharedPreferences
            .getBoolean(activity!!.getString(R.string.key_use_compact_list), !GnuCashApplication.isDoubleEntryEnabled)
        //if there was a local override of the global setting, respect it
        if (savedInstanceState != null) mUseCompactView =
            savedInstanceState.getBoolean(getString(R.string.key_use_compact_list), mUseCompactView)
        mTransactionsDbAdapter = TransactionsDbAdapter.instance
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(getString(R.string.key_use_compact_list), mUseCompactView)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_transactions_list, container, false)
        ButterKnife.bind(this, view)
        mRecyclerView!!.setHasFixedSize(true)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val gridLayoutManager = GridLayoutManager(activity, 2)
            mRecyclerView!!.layoutManager = gridLayoutManager
        } else {
            val mLayoutManager = LinearLayoutManager(activity)
            mRecyclerView!!.layoutManager = mLayoutManager
        }
        mRecyclerView!!.setEmptyView(view.findViewById(R.id.empty_view))
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val aBar = (activity as AppCompatActivity?)!!.supportActionBar
        aBar!!.setDisplayShowTitleEnabled(false)
        aBar.setDisplayHomeAsUpEnabled(true)
        mTransactionRecyclerAdapter = TransactionRecyclerAdapter(null)
        mRecyclerView!!.adapter = mTransactionRecyclerAdapter
        setHasOptionsMenu(true)
    }

    /**
     * Refresh the list with transactions from account with ID `accountId`
     * @param uid GUID of account to load transactions from
     */
    override fun refresh(uid: String?) {
        mAccountUID = uid
        refresh()
    }

    /**
     * Reload the list of transactions and recompute account balances
     */
    override fun refresh() {
        loaderManager.restartLoader(0, null, this)
    }

    override fun onResume() {
        super.onResume()
        (activity as TransactionsActivity?)!!.updateNavigationSelection()
        refresh()
    }

    fun onListItemClick(id: Long) {
        val intent = Intent(activity, TransactionDetailActivity::class.java)
        intent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, mTransactionsDbAdapter!!.getUID(id))
        intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID)
        startActivity(intent)
        //		mTransactionEditListener.editTransaction(mTransactionsDbAdapter.getUID(id));
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.transactions_list_actions, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val item = menu.findItem(R.id.menu_compact_trn_view)
        item.isChecked = mUseCompactView
        item.isEnabled = GnuCashApplication.isDoubleEntryEnabled //always compact for single-entry
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_compact_trn_view -> {
                item.isChecked = !item.isChecked
                mUseCompactView = !mUseCompactView
                refresh()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateLoader(arg0: Int, arg1: Bundle?): Loader<Cursor> {
        Log.d(LOG_TAG, "Creating transactions loader")
        return TransactionsCursorLoader(activity, mAccountUID)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor) {
        Log.d(LOG_TAG, "Transactions loader finished. Swapping in cursor")
        mTransactionRecyclerAdapter!!.swapCursor(cursor)
        mTransactionRecyclerAdapter!!.notifyDataSetChanged()
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        Log.d(LOG_TAG, "Resetting transactions loader")
        mTransactionRecyclerAdapter!!.swapCursor(null)
    }

    /**
     * [DatabaseCursorLoader] for loading transactions asynchronously from the database
     * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
     */
    private class TransactionsCursorLoader(context: Context?, private val accountUID: String?) :
        DatabaseCursorLoader(context) {
        override fun loadInBackground(): Cursor {
            mDatabaseAdapter = TransactionsDbAdapter.instance
            val c = (mDatabaseAdapter as TransactionsDbAdapter).fetchAllTransactionsForAccount(
                accountUID!!
            )
            registerContentObserver(c)
            return c
        }
    }

    inner class TransactionRecyclerAdapter(cursor: Cursor?) :
        CursorRecyclerAdapter<TransactionRecyclerAdapter.ViewHolder>(cursor) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layoutRes =
                if (viewType == Companion.ITEM_TYPE_COMPACT) R.layout.cardview_compact_transaction else R.layout.cardview_transaction
            val v = LayoutInflater.from(parent.context)
                .inflate(layoutRes, parent, false)
            return ViewHolder(v)
        }

        override fun getItemViewType(position: Int): Int {
            return if (mUseCompactView) Companion.ITEM_TYPE_COMPACT else Companion.ITEM_TYPE_FULL
        }

        override fun onBindViewHolderCursor(holder: ViewHolder, cursor: Cursor?) {
            if (cursor == null) return

            holder.transactionId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry._ID))
            val description =
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_DESCRIPTION))
            holder.primaryText!!.text = description
            val transactionUID =
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_UID))
            val amount = mTransactionsDbAdapter!!.getBalance(transactionUID, mAccountUID)
            displayBalance(holder.transactionAmount!!, amount)
            val dateMillis =
                cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_TIMESTAMP))
            val dateText = getPrettyDateFormat(activity, dateMillis)
            val id = holder.transactionId
            holder.itemView.setOnClickListener { onListItemClick(id) }
            if (mUseCompactView) {
                holder.secondaryText!!.text = dateText
            } else {
                val splits = SplitsDbAdapter.instance.getSplitsForTransaction(transactionUID)
                var text = ""
                if (splits.size == 2 && splits[0].isPairOf(splits[1])) {
                    for (split in splits) {
                        if (split.mAccountUID != mAccountUID) {
                            text = AccountsDbAdapter.instance.getFullyQualifiedAccountName(split.mAccountUID)
                            break
                        }
                    }
                }
                if (splits.size > 2) {
                    text = splits.size.toString() + " splits"
                }
                holder.secondaryText!!.text = text
                holder.transactionDate!!.text = dateText
                holder.editTransaction!!.setOnClickListener {
                    val intent = Intent(activity, FormActivity::class.java)
                    intent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
                    intent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID)
                    intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID)
                    startActivity(intent)
                }
            }
        }

        inner class ViewHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!),
            PopupMenu.OnMenuItemClickListener {
            @JvmField
			@BindView(R.id.primary_text)
            var primaryText: TextView? = null

            @JvmField
			@BindView(R.id.secondary_text)
            var secondaryText: TextView? = null

            @JvmField
			@BindView(R.id.transaction_amount)
            var transactionAmount: TextView? = null

            @JvmField
			@BindView(R.id.options_menu)
            var optionsMenu: ImageView? = null

            //these views are not used in the compact view, hence the nullability
            @JvmField
			@BindView(R.id.transaction_date)
            var transactionDate: TextView? = null

            @JvmField
			@BindView(R.id.edit_transaction)
            var editTransaction: ImageView? = null
            var transactionId: Long = 0

            init {
                ButterKnife.bind(this, itemView!!)
                primaryText!!.textSize = 18f
                optionsMenu!!.setOnClickListener { v ->
                    val popup = PopupMenu(activity!!, v)
                    popup.setOnMenuItemClickListener(this@ViewHolder)
                    val inflater = popup.menuInflater
                    inflater.inflate(R.menu.transactions_context_menu, popup.menu)
                    popup.show()
                }
            }

            override fun onMenuItemClick(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.context_menu_delete -> {
                        BackupManager.backupActiveBook()
                        mTransactionsDbAdapter!!.deleteRecord(transactionId)
                        updateAllWidgets(activity!!)
                        refresh()
                        true
                    }

                    R.id.context_menu_duplicate_transaction -> {
                        val transaction = mTransactionsDbAdapter!!.getRecord(transactionId)
                        val duplicate = Transaction(transaction, true)
                        duplicate.setMTimestamp(System.currentTimeMillis())
                        mTransactionsDbAdapter!!.addRecord(duplicate, DatabaseAdapter.UpdateMethod.insert)
                        refresh()
                        true
                    }

                    R.id.context_menu_move_transaction -> {
                        val ids = longArrayOf(transactionId)
                        val fragment = BulkMoveDialogFragment.newInstance(ids, mAccountUID)
                        fragment.show(activity!!.supportFragmentManager, "bulk_move_transactions")
                        fragment.setTargetFragment(this@TransactionsListFragment, 0)
                        true
                    }

                    else -> false
                }
            }
        }

//        companion object {
//            const val ITEM_TYPE_COMPACT = 0x111
//            const val ITEM_TYPE_FULL = 0x100
//        }
    }

    companion object {
        /**
         * Logging tag
         */
        private const val LOG_TAG = "TransactionListFragment"

        const val ITEM_TYPE_COMPACT = 0x111
        const val ITEM_TYPE_FULL = 0x100
    }
}