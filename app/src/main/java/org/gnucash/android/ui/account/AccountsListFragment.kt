/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (C) 2020 Xilin Jia https://github.com/XilinJia
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
package org.gnucash.android.ui.account

import android.app.Activity
import android.app.SearchManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
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
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.ui.account.AccountsListFragment.AccountRecyclerAdapter.AccountViewHolder
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.util.AccountBalanceTask
import org.gnucash.android.ui.util.CursorRecyclerAdapter
import org.gnucash.android.ui.util.widget.EmptyRecyclerView
import org.gnucash.android.util.BackupManager

/**
 * Fragment for displaying the list of accounts in the database
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class AccountsListFragment : Fragment(), Refreshable, LoaderManager.LoaderCallbacks<Cursor>,
    SearchView.OnQueryTextListener, SearchView.OnCloseListener {
    internal var mAccountRecyclerAdapter: AccountRecyclerAdapter? = null

    @JvmField
    @BindView(R.id.account_recycler_view)
    var mRecyclerView: EmptyRecyclerView? = null

    @JvmField
    @BindView(R.id.empty_view)
    var mEmptyTextView: TextView? = null

    /**
     * Describes the kinds of accounts that should be loaded in the accounts list.
     * This enhances reuse of the accounts list fragment
     */
    enum class DisplayMode {
        TOP_LEVEL, RECENT, FAVORITES
    }

    /**
     * Field indicating which kind of accounts to load.
     * Default value is [DisplayMode.TOP_LEVEL]
     */
    private var mDisplayMode: DisplayMode? = DisplayMode.TOP_LEVEL

    /**
     * Database adapter for loading Account records from the database
     */
    private var mAccountsDbAdapter: AccountsDbAdapter? = null

    /**
     * Listener to be notified when an account is clicked
     */
    private var mAccountSelectedListener: OnAccountClickedListener? = null

    /**
     * GUID of the account whose children will be loaded in the list fragment.
     * If no parent account is specified, then all top-level accounts are loaded.
     */
    private var mParentAccountUID: String? = null

    /**
     * Filter for which accounts should be displayed. Used by search interface
     */
    private var mCurrentFilter: String? = null

    /**
     * Search view for searching accounts
     */
    private var mSearchView: SearchView? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(
            R.layout.fragment_accounts_list, container,
            false
        )
        ButterKnife.bind(this, v)
        mRecyclerView!!.setHasFixedSize(true)
        mRecyclerView!!.setEmptyView(mEmptyTextView)
        when (mDisplayMode) {
            DisplayMode.TOP_LEVEL -> mEmptyTextView!!.setText(R.string.label_no_accounts)
            DisplayMode.RECENT -> mEmptyTextView!!.setText(R.string.label_no_recent_accounts)
            DisplayMode.FAVORITES -> mEmptyTextView!!.setText(R.string.label_no_favorite_accounts)
            else -> {}
        }
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val gridLayoutManager = GridLayoutManager(activity, 2)
            mRecyclerView!!.layoutManager = gridLayoutManager
        } else {
            val mLayoutManager = LinearLayoutManager(activity)
            mRecyclerView!!.layoutManager = mLayoutManager
        }
        return v
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments
        if (args != null) mParentAccountUID = args.getString(UxArgument.PARENT_ACCOUNT_UID)
        if (savedInstanceState != null) mDisplayMode =
            savedInstanceState.getSerializable(STATE_DISPLAY_MODE) as DisplayMode?
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val actionbar = (activity as AppCompatActivity?)!!.supportActionBar
        actionbar!!.setTitle(R.string.title_accounts)
        actionbar.setDisplayHomeAsUpEnabled(true)
        setHasOptionsMenu(true)


        // specify an adapter (see also next example)
        mAccountRecyclerAdapter = AccountRecyclerAdapter(null)
        mRecyclerView!!.adapter = mAccountRecyclerAdapter
    }

    override fun onStart() {
        super.onStart()
        mAccountsDbAdapter = AccountsDbAdapter.instance
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        mAccountSelectedListener = try {
            activity as OnAccountClickedListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$activity must implement OnAccountSelectedListener")
        }
    }

    fun onListItemClick(accountUID: String?) {
        mAccountSelectedListener!!.accountSelected(accountUID)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_CANCELED) return
        refresh()
    }

    /**
     * Delete the account with record ID `rowId`
     * It shows the delete confirmation dialog if the account has transactions,
     * else deletes the account immediately
     *
     * @param rowId The record ID of the account
     */
    fun tryDeleteAccount(rowId: Long) {
        val acc = mAccountsDbAdapter!!.getRecord(rowId)
        if (acc.transactionCount > 0 || mAccountsDbAdapter!!.getSubAccountCount(acc.mUID!!) > 0) {
            showConfirmationDialog(rowId)
        } else {
            BackupManager.backupActiveBook()
            // Avoid calling AccountsDbAdapter.deleteRecord(long). See #654
            val uid = mAccountsDbAdapter!!.getUID(rowId)
            mAccountsDbAdapter!!.deleteRecord(uid!!)
            refresh()
        }
    }

    /**
     * Shows the delete confirmation dialog
     *
     * @param id Record ID of account to be deleted after confirmation
     */
    fun showConfirmationDialog(id: Long) {
        val alertFragment = DeleteAccountDialogFragment.newInstance(mAccountsDbAdapter!!.getUID(id))
        alertFragment.setTargetFragment(this, 0)
        alertFragment.show(activity!!.supportFragmentManager, "delete_confirmation_dialog")
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (mParentAccountUID != null) inflater.inflate(R.menu.sub_account_actions, menu) else {
            inflater.inflate(R.menu.account_actions, menu)
            // Associate searchable configuration with the SearchView
            val searchManager =
                GnuCashApplication.appContext!!.getSystemService(Context.SEARCH_SERVICE) as SearchManager
            mSearchView = MenuItemCompat.getActionView(menu.findItem(R.id.menu_search)) as SearchView
            if (mSearchView == null) return
            mSearchView!!.setSearchableInfo(
                searchManager.getSearchableInfo(activity!!.componentName)
            )
            mSearchView!!.setOnQueryTextListener(this)
            mSearchView!!.setOnCloseListener(this)
        }
    }

    /**
     * Refresh the account list as a sublist of another account
     * @param uid GUID of the parent account
     */
    override fun refresh(uid: String?) {
        arguments!!.putString(UxArgument.PARENT_ACCOUNT_UID, uid)
        refresh()
    }

    /**
     * Refreshes the list by restarting the [DatabaseCursorLoader] associated
     * with the ListView
     */
    override fun refresh() {
        loaderManager.restartLoader(0, null, this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(STATE_DISPLAY_MODE, mDisplayMode)
    }

    /**
     * Closes any open database adapters used by the list
     */
    override fun onDestroy() {
        super.onDestroy()
        if (mAccountRecyclerAdapter != null) mAccountRecyclerAdapter!!.swapCursor(null)
    }

    /**
     * Opens a new activity for creating or editing an account.
     * If the `accountId` &lt; 1, then create else edit the account.
     * @param accountId Long record ID of account to be edited. Pass 0 to create a new account.
     */
    fun openCreateOrEditActivity(accountId: Long) {
        val editAccountIntent = Intent(this@AccountsListFragment.activity, FormActivity::class.java)
        editAccountIntent.action = Intent.ACTION_INSERT_OR_EDIT
        editAccountIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountsDbAdapter!!.getUID(accountId))
        editAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name)
        startActivityForResult(editAccountIntent, AccountsActivity.REQUEST_EDIT_ACCOUNT)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        Log.d(TAG, "Creating the accounts loader")
        val arguments = arguments
        val accountUID = arguments?.getString(UxArgument.PARENT_ACCOUNT_UID)
        return if (mCurrentFilter != null) {
            AccountsCursorLoader(activity, mCurrentFilter)
        } else {
            AccountsCursorLoader(this.activity, accountUID, mDisplayMode)
        }
    }

    override fun onLoadFinished(loaderCursor: Loader<Cursor>, cursor: Cursor) {
        Log.d(TAG, "Accounts loader finished. Swapping in cursor")
        mAccountRecyclerAdapter!!.swapCursor(cursor)
        mAccountRecyclerAdapter!!.notifyDataSetChanged()
    }

    override fun onLoaderReset(arg0: Loader<Cursor>) {
        Log.d(TAG, "Resetting the accounts loader")
        mAccountRecyclerAdapter!!.swapCursor(null)
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        //nothing to see here, move along
        return true
    }

    override fun onQueryTextChange(newText: String): Boolean {
        val newFilter = if (!TextUtils.isEmpty(newText)) newText else null
        if (mCurrentFilter == null && newFilter == null) {
            return true
        }
        if (mCurrentFilter != null && mCurrentFilter == newFilter) {
            return true
        }
        mCurrentFilter = newFilter
        loaderManager.restartLoader(0, null, this)
        return true
    }

    override fun onClose(): Boolean {
        if (!TextUtils.isEmpty(mSearchView!!.query)) {
            mSearchView!!.setQuery(null, true)
        }
        return true
    }

    /**
     * Extends [DatabaseCursorLoader] for loading of [Account] from the
     * database asynchronously.
     *
     * By default it loads only top-level accounts (accounts which have no parent or have GnuCash ROOT account as parent.
     * By submitting a parent account ID in the constructor parameter, it will load child accounts of that parent.
     *
     * Class must be static because the Android loader framework requires it to be so
     * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
     */
    private class AccountsCursorLoader : DatabaseCursorLoader {
        private var mParentAccountUID: String? = null
        private var mFilter: String? = null
        private var mDisplayMode: DisplayMode? = DisplayMode.TOP_LEVEL

        /**
         * Initializes the loader to load accounts from the database.
         * If the `parentAccountId <= 0` then only top-level accounts are loaded.
         * Else only the child accounts of the `parentAccountId` will be loaded
         * @param context Application context
         * @param parentAccountUID GUID of the parent account
         */
        constructor(context: Context?, parentAccountUID: String?, displayMode: DisplayMode?) : super(context) {
            mParentAccountUID = parentAccountUID
            mDisplayMode = displayMode
        }

        /**
         * Initializes the loader with a filter for account names.
         * Only accounts whose name match the filter will be loaded.
         * @param context Application context
         * @param filter Account name filter string
         */
        constructor(context: Context?, filter: String?) : super(context) {
            mFilter = filter
        }

        override fun loadInBackground(): Cursor {
            mDatabaseAdapter = AccountsDbAdapter.instance
            val cursor: Cursor = if (mFilter != null) {
                (mDatabaseAdapter as AccountsDbAdapter)
                    .fetchAccounts(
                        DatabaseSchema.AccountEntry.COLUMN_HIDDEN + "= 0 AND "
                                + DatabaseSchema.AccountEntry.COLUMN_NAME + " LIKE '%" + mFilter + "%'",
                        null, null
                    )
            } else {
                if (mParentAccountUID != null && mParentAccountUID!!.isNotEmpty()) (mDatabaseAdapter as AccountsDbAdapter).fetchSubAccounts(
                    mParentAccountUID!!
                ) else {
                    when (mDisplayMode) {
                        DisplayMode.RECENT -> (mDatabaseAdapter as AccountsDbAdapter).fetchRecentAccounts(10)
                        DisplayMode.FAVORITES -> (mDatabaseAdapter as AccountsDbAdapter).fetchFavoriteAccounts()
                        DisplayMode.TOP_LEVEL -> (mDatabaseAdapter as AccountsDbAdapter).fetchTopLevelAccounts()
                        else -> (mDatabaseAdapter as AccountsDbAdapter).fetchTopLevelAccounts()
                    }
                }
            }
            registerContentObserver(cursor)
            return cursor
        }
    }

    internal inner class AccountRecyclerAdapter(cursor: Cursor?) : CursorRecyclerAdapter<AccountViewHolder>(cursor) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.cardview_account, parent, false)
            return AccountViewHolder(v)
        }

        override fun onBindViewHolderCursor(holder: AccountViewHolder, cursor: Cursor?) {
            if (cursor == null) return

            val accountUID = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_UID))
            mAccountsDbAdapter = AccountsDbAdapter.instance
            holder.accoundId = mAccountsDbAdapter!!.getID(accountUID)
            holder.accountName!!.text =
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_NAME))
            val subAccountCount = mAccountsDbAdapter!!.getSubAccountCount(accountUID)
            if (subAccountCount > 0) {
                holder.description!!.visibility = View.VISIBLE
                val text = resources.getQuantityString(R.plurals.label_sub_accounts, subAccountCount, subAccountCount)
                holder.description!!.text = text
            } else holder.description!!.visibility = View.GONE

            // add a summary of transactions to the account view

            // Make sure the balance task is truly multithread
            AccountBalanceTask(holder.accountBalance).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, accountUID)
            val accountColor =
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_COLOR_CODE))
            val colorCode = if (accountColor == null) Color.TRANSPARENT else Color.parseColor(accountColor)
            holder.colorStripView!!.setBackgroundColor(colorCode)
            val isPlaceholderAccount = mAccountsDbAdapter!!.isPlaceholderAccount(accountUID)
            if (isPlaceholderAccount) {
                holder.createTransaction!!.visibility = View.GONE
            } else {
                holder.createTransaction!!.setOnClickListener {
                    val intent = Intent(activity, FormActivity::class.java)
                    intent.action = Intent.ACTION_INSERT_OR_EDIT
                    intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
                    intent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
                    activity!!.startActivity(intent)
                }
            }
            val budgets = BudgetsDbAdapter.instance.getAccountBudgets(accountUID)
            //TODO: include fetch only active budgets
            if (budgets.size == 1) {
                val budget = budgets[0]
                val balance = mAccountsDbAdapter!!.getAccountBalance(
                    accountUID,
                    budget.startofCurrentPeriod(),
                    budget.endOfCurrentPeriod()
                )
                val budgetProgress = balance.divide(budget.amount(accountUID)!!).asBigDecimal().toDouble() * 100
                holder.budgetIndicator!!.visibility = View.VISIBLE
                holder.budgetIndicator!!.progress = budgetProgress.toInt()
            } else {
                holder.budgetIndicator!!.visibility = View.GONE
            }
            if (mAccountsDbAdapter!!.isFavoriteAccount(accountUID)) {
                holder.favoriteStatus!!.setImageResource(R.drawable.ic_star_black_24dp)
            } else {
                holder.favoriteStatus!!.setImageResource(R.drawable.ic_star_border_black_24dp)
            }
            holder.favoriteStatus!!.setOnClickListener {
                val isFavoriteAccount = mAccountsDbAdapter!!.isFavoriteAccount(accountUID)
                val contentValues = ContentValues()
                contentValues.put(DatabaseSchema.AccountEntry.COLUMN_FAVORITE, !isFavoriteAccount)
                mAccountsDbAdapter!!.updateRecord(accountUID, contentValues)
                val drawableResource =
                    if (!isFavoriteAccount) R.drawable.ic_star_black_24dp else R.drawable.ic_star_border_black_24dp
                holder.favoriteStatus!!.setImageResource(drawableResource)
                if (mDisplayMode == DisplayMode.FAVORITES) refresh()
            }
            holder.itemView.setOnClickListener { onListItemClick(accountUID) }
        }

        internal inner class AccountViewHolder(itemView: View?) : RecyclerView.ViewHolder(
            itemView!!
        ), PopupMenu.OnMenuItemClickListener {
            @JvmField
            @BindView(R.id.primary_text)
            var accountName: TextView? = null

            @JvmField
            @BindView(R.id.secondary_text)
            var description: TextView? = null

            @JvmField
            @BindView(R.id.account_balance)
            var accountBalance: TextView? = null

            @JvmField
            @BindView(R.id.create_transaction)
            var createTransaction: ImageView? = null

            @JvmField
            @BindView(R.id.favorite_status)
            var favoriteStatus: ImageView? = null

            @JvmField
            @BindView(R.id.options_menu)
            var optionsMenu: ImageView? = null

            @JvmField
            @BindView(R.id.account_color_strip)
            var colorStripView: View? = null

            @JvmField
            @BindView(R.id.budget_indicator)
            var budgetIndicator: ProgressBar? = null
            var accoundId: Long = 0

            init {
                ButterKnife.bind(this, itemView!!)
                optionsMenu!!.setOnClickListener { v ->
                    val popup = PopupMenu(activity!!, v)
                    popup.setOnMenuItemClickListener(this@AccountViewHolder)
                    val inflater = popup.menuInflater
                    inflater.inflate(R.menu.account_context_menu, popup.menu)
                    popup.show()
                }
            }

            override fun onMenuItemClick(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.context_menu_edit_accounts -> {
                        openCreateOrEditActivity(accoundId)
                        true
                    }

                    R.id.context_menu_delete -> {
                        tryDeleteAccount(accoundId)
                        true
                    }

                    else -> false
                }
            }
        }
    }

    companion object {
        /**
         * Logging tag
         */
        private const val TAG = "AccountsListFragment"

        /**
         * Tag to save [AccountsListFragment.mDisplayMode] to fragment state
         */
        private const val STATE_DISPLAY_MODE = "mDisplayMode"
        fun newInstance(displayMode: DisplayMode?): AccountsListFragment {
            val fragment = AccountsListFragment()
            fragment.mDisplayMode = displayMode
            return fragment
        }
    }
}