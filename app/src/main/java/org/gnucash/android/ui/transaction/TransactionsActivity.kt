/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
import android.database.Cursor
import android.graphics.drawable.ColorDrawable
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.util.SparseArray
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.Spinner
import android.widget.SpinnerAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import butterknife.BindView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayout.TabLayoutOnPageChangeListener
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.getActiveAccountColorResource
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Money
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.account.AccountsListFragment
import org.gnucash.android.ui.account.OnAccountClickedListener
import org.gnucash.android.ui.common.BaseDrawerActivity
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.util.AccountBalanceTask
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter
import org.joda.time.LocalDate
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for displaying, creating and editing transactions
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class TransactionsActivity : BaseDrawerActivity(), Refreshable, OnAccountClickedListener, OnTransactionClickedListener {
    /**
     * Returns the global unique ID of the current account
     * @return GUID of the current account
     */
    /**
     * GUID of [Account] whose transactions are displayed
     */
    var currentAccountUID: String? = null
        private set

    /**
     * Account database adapter for manipulating the accounts list in navigation
     */
    private var mAccountsDbAdapter: AccountsDbAdapter? = null

    /**
     * Hold the accounts cursor that will be used in the Navigation
     */
    private var mAccountsCursor: Cursor? = null

    @JvmField
    @BindView(R.id.pager)
    var mViewPager: ViewPager? = null

    @JvmField
    @BindView(R.id.toolbar_spinner)
    var mToolbarSpinner: Spinner? = null

    @JvmField
    @BindView(R.id.tab_layout)
    var mTabLayout: TabLayout? = null

    @JvmField
    @BindView(R.id.transactions_sum)
    var mSumTextView: TextView? = null

    @JvmField
    @BindView(R.id.fab_create_transaction)
    var mCreateFloatingButton: FloatingActionButton? = null
    private val mFragmentPageReferenceMap = SparseArray<Refreshable>()

    /**
     * Flag for determining is the currently displayed account is a placeholder account or not.
     * This will determine if the transactions tab is displayed or not
     */
    private var mIsPlaceholderAccount = false
    private val mTransactionListNavigationListener: OnItemSelectedListener = object : OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
            currentAccountUID = mAccountsDbAdapter!!.getUID(id)
            intent.putExtra(
                UxArgument.SELECTED_ACCOUNT_UID,
                currentAccountUID
            ) //update the intent in case the account gets rotated
            mIsPlaceholderAccount = mAccountsDbAdapter!!.isPlaceholderAccount(currentAccountUID)
            if (mIsPlaceholderAccount) {
                if (mTabLayout!!.tabCount > 1) {
                    mPagerAdapter!!.notifyDataSetChanged()
                    mTabLayout!!.removeTabAt(1)
                }
            } else {
                if (mTabLayout!!.tabCount < 2) {
                    mPagerAdapter!!.notifyDataSetChanged()
                    mTabLayout!!.addTab(mTabLayout!!.newTab().setText(R.string.section_header_transactions))
                }
            }
            (view as TextView).setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            //refresh any fragments in the tab with the new account UID
            refresh()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            //nothing to see here, move along
        }
    }
    private var mPagerAdapter: PagerAdapter? = null

    /**
     * Adapter for managing the sub-account and transaction fragment pages in the accounts view
     */
    private inner class AccountViewPagerAdapter(fm: FragmentManager?) : FragmentStatePagerAdapter(
        fm!!
    ) {
        override fun getItem(i: Int): Fragment {
            if (mIsPlaceholderAccount) {
                val transactionsListFragment: Fragment = prepareSubAccountsListFragment()
                mFragmentPageReferenceMap.put(i, transactionsListFragment as Refreshable)
                return transactionsListFragment
            }
            val currentFragment: Fragment = when (i) {
                INDEX_SUB_ACCOUNTS_FRAGMENT -> prepareSubAccountsListFragment()
                INDEX_TRANSACTIONS_FRAGMENT -> prepareTransactionsListFragment()
                else -> prepareTransactionsListFragment()
            }
            mFragmentPageReferenceMap.put(i, currentFragment as Refreshable)
            return currentFragment
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            super.destroyItem(container, position, `object`)
            mFragmentPageReferenceMap.remove(position)
        }

        override fun getPageTitle(position: Int): CharSequence {
            return if (mIsPlaceholderAccount) getString(R.string.section_header_subaccounts) else when (position) {
                INDEX_SUB_ACCOUNTS_FRAGMENT -> getString(R.string.section_header_subaccounts)
                INDEX_TRANSACTIONS_FRAGMENT -> getString(R.string.section_header_transactions)
                else -> getString(R.string.section_header_transactions)
            }
        }

        override fun getCount(): Int {
            return if (mIsPlaceholderAccount) 1 else DEFAULT_NUM_PAGES
        }

        /**
         * Creates and initializes the fragment for displaying sub-account list
         * @return [AccountsListFragment] initialized with the sub-accounts
         */
        private fun prepareSubAccountsListFragment(): AccountsListFragment {
            val subAccountsListFragment = AccountsListFragment()
            val args = Bundle()
            args.putString(UxArgument.PARENT_ACCOUNT_UID, currentAccountUID)
            subAccountsListFragment.arguments = args
            return subAccountsListFragment
        }

        /**
         * Creates and initializes fragment for displaying transactions
         * @return [TransactionsListFragment] initialized with the current account transactions
         */
        private fun prepareTransactionsListFragment(): TransactionsListFragment {
            val transactionsListFragment = TransactionsListFragment()
            val args = Bundle()
            args.putString(UxArgument.SELECTED_ACCOUNT_UID, currentAccountUID)
            transactionsListFragment.arguments = args
            Log.i(TAG, "Opening transactions for account:  $currentAccountUID")
            return transactionsListFragment
        }
    }

    /**
     * Refreshes the fragments currently in the transactions activity
     */
    override fun refresh(uid: String?) {
        for (i in 0 until mFragmentPageReferenceMap.size()) {
            mFragmentPageReferenceMap.valueAt(i).refresh(uid)
        }
        if (mPagerAdapter != null) mPagerAdapter!!.notifyDataSetChanged()
        AccountBalanceTask(mSumTextView).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, currentAccountUID)
    }

    override fun refresh() {
        refresh(currentAccountUID)
        setTitleIndicatorColor()
    }

    override val contentView: Int
        get() = R.layout.activity_transactions
    override val titleRes: Int
        get() = R.string.title_transactions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.setDisplayShowTitleEnabled(false)
        currentAccountUID = intent.getStringExtra(UxArgument.SELECTED_ACCOUNT_UID)
        mAccountsDbAdapter = AccountsDbAdapter.instance
        mIsPlaceholderAccount = mAccountsDbAdapter!!.isPlaceholderAccount(currentAccountUID)
        mTabLayout!!.addTab(mTabLayout!!.newTab().setText(R.string.section_header_subaccounts))
        if (!mIsPlaceholderAccount) {
            mTabLayout!!.addTab(mTabLayout!!.newTab().setText(R.string.section_header_transactions))
        }
        setupActionBarNavigation()
        mPagerAdapter = AccountViewPagerAdapter(
            supportFragmentManager
        )
        mViewPager!!.adapter = mPagerAdapter
        mViewPager!!.addOnPageChangeListener(TabLayoutOnPageChangeListener(mTabLayout))
        mTabLayout!!.setOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                mViewPager!!.currentItem = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                //nothing to see here, move along
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                //nothing to see here, move along
            }
        })

        //if there are no transactions, and there are sub-accounts, show the sub-accounts
        if (TransactionsDbAdapter.instance.getTransactionsCount(currentAccountUID!!) == 0
            && mAccountsDbAdapter!!.getSubAccountCount(currentAccountUID!!) > 0
        ) {
            mViewPager!!.currentItem = INDEX_SUB_ACCOUNTS_FRAGMENT
        } else {
            mViewPager!!.currentItem = INDEX_TRANSACTIONS_FRAGMENT
        }
        mCreateFloatingButton!!.setOnClickListener {
            when (mViewPager!!.currentItem) {
                INDEX_SUB_ACCOUNTS_FRAGMENT -> {
                    val addAccountIntent = Intent(this@TransactionsActivity, FormActivity::class.java)
                    addAccountIntent.action = Intent.ACTION_INSERT_OR_EDIT
                    addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name)
                    addAccountIntent.putExtra(UxArgument.PARENT_ACCOUNT_UID, currentAccountUID)
                    startActivityForResult(addAccountIntent, AccountsActivity.REQUEST_EDIT_ACCOUNT)
                }

                INDEX_TRANSACTIONS_FRAGMENT -> createNewTransaction(currentAccountUID!!)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setTitleIndicatorColor()
    }

    /**
     * Sets the color for the ViewPager title indicator to match the account color
     */
    private fun setTitleIndicatorColor() {
        val iColor = getActiveAccountColorResource(currentAccountUID!!)
        mTabLayout!!.setBackgroundColor(iColor)
        if (supportActionBar != null) supportActionBar!!.setBackgroundDrawable(ColorDrawable(iColor))
        if (Build.VERSION.SDK_INT > 20) window.statusBarColor = GnuCashApplication.darken(iColor)
    }

    /**
     * Set up action bar navigation list and listener callbacks
     */
    private fun setupActionBarNavigation() {
        // set up spinner adapter for navigation list
        if (mAccountsCursor != null) {
            mAccountsCursor!!.close()
        }
        mAccountsCursor = mAccountsDbAdapter!!.fetchAllRecordsOrderedByFullName()
        val mSpinnerAdapter: SpinnerAdapter = QualifiedAccountNameCursorAdapter(
            supportActionBar!!.themedContext, mAccountsCursor, R.layout.account_spinner_item
        )
        mToolbarSpinner!!.adapter = mSpinnerAdapter
        mToolbarSpinner!!.onItemSelectedListener = mTransactionListNavigationListener
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        updateNavigationSelection()
    }

    /**
     * Updates the action bar navigation list selection to that of the current account
     * whose transactions are being displayed/manipulated
     */
    fun updateNavigationSelection() {
        // set the selected item in the spinner
        var i = 0
        val accountsCursor = mAccountsDbAdapter!!.fetchAllRecordsOrderedByFullName()
        while (accountsCursor.moveToNext()) {
            val uid =
                accountsCursor.getString(accountsCursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_UID))
            if (currentAccountUID == uid) {
                mToolbarSpinner!!.setSelection(i)
                break
            }
            ++i
        }
        accountsCursor.close()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val favoriteAccountMenuItem =
            menu.findItem(R.id.menu_favorite_account) ?: //when the activity is used to edit a transaction
            return super.onPrepareOptionsMenu(menu)
        val isFavoriteAccount = AccountsDbAdapter.instance.isFavoriteAccount(currentAccountUID)
        val favoriteIcon =
            if (isFavoriteAccount) R.drawable.ic_star_white_24dp else R.drawable.ic_star_border_white_24dp
        favoriteAccountMenuItem.setIcon(favoriteIcon)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> super.onOptionsItemSelected(item)
            R.id.menu_favorite_account -> {
                val accountsDbAdapter = AccountsDbAdapter.instance
                val accountId = accountsDbAdapter.getID(currentAccountUID!!)
                val isFavorite = accountsDbAdapter.isFavoriteAccount(currentAccountUID)
                //toggle favorite preference
                accountsDbAdapter.updateAccount(
                    accountId,
                    DatabaseSchema.AccountEntry.COLUMN_FAVORITE,
                    if (isFavorite) "0" else "1"
                )
                supportInvalidateOptionsMenu()
                true
            }

            R.id.menu_edit_account -> {
                val editAccountIntent = Intent(this, FormActivity::class.java)
                editAccountIntent.action = Intent.ACTION_INSERT_OR_EDIT
                editAccountIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, currentAccountUID)
                editAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name)
                startActivityForResult(editAccountIntent, AccountsActivity.REQUEST_EDIT_ACCOUNT)
                true
            }

            else -> false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_CANCELED) return
        refresh()
        setupActionBarNavigation()
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()
        mAccountsCursor!!.close()
    }

    override fun createNewTransaction(accountUID: String) {
        val createTransactionIntent = Intent(this.applicationContext, FormActivity::class.java)
        createTransactionIntent.action = Intent.ACTION_INSERT_OR_EDIT
        createTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
        createTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
        startActivity(createTransactionIntent)
    }

    override fun editTransaction(transactionUID: String) {
        val createTransactionIntent = Intent(this.applicationContext, FormActivity::class.java)
        createTransactionIntent.action = Intent.ACTION_INSERT_OR_EDIT
        createTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, currentAccountUID)
        createTransactionIntent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID)
        createTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
        startActivity(createTransactionIntent)
    }

    override fun accountSelected(accountUID: String?) {
        val restartIntent = Intent(this.applicationContext, TransactionsActivity::class.java)
        restartIntent.action = Intent.ACTION_VIEW
        restartIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
        startActivity(restartIntent)
    }

    companion object {
        /**
         * Logging tag
         */
        private const val TAG = "TransactionsActivity"

        /**
         * ViewPager index for sub-accounts fragment
         */
        private const val INDEX_SUB_ACCOUNTS_FRAGMENT = 0

        /**
         * ViewPager index for transactions fragment
         */
        private const val INDEX_TRANSACTIONS_FRAGMENT = 1

        /**
         * Number of pages to show
         */
        private const val DEFAULT_NUM_PAGES = 2
        private val mDayMonthDateFormat = SimpleDateFormat("EEE, d MMM")

        /**
         * Display the balance of a transaction in a text view and format the text color to match the sign of the amount
         * @param balanceTextView [android.widget.TextView] where balance is to be displayed
         * @param balance [org.gnucash.android.model.Money] balance to display
         */
        @JvmStatic
        fun displayBalance(balanceTextView: TextView, balance: Money) {
            balanceTextView.text = balance.formattedString()
            val context = GnuCashApplication.appContext
            var fontColor =
                if (balance.isNegative) context!!.resources.getColor(R.color.debit_red)
                else context!!.resources.getColor(R.color.credit_green)
            if (balance.asBigDecimal().compareTo(BigDecimal.ZERO) == 0) fontColor =
                context.resources.getColor(android.R.color.black)
            balanceTextView.setTextColor(fontColor)
        }

        /**
         * Formats the date to show the the day of the week if the `dateMillis` is within 7 days
         * of today. Else it shows the actual date formatted as short string. <br></br>
         * It also shows "today", "yesterday" or "tomorrow" if the date is on any of those days
         * @param dateMillis
         * @return
         */
        @JvmStatic
        fun getPrettyDateFormat(context: Context?, dateMillis: Long): String {
            val transactionTime = LocalDate(dateMillis)
            val today = LocalDate()
            val prettyDateText: String? = if (transactionTime >= today.minusDays(1) && transactionTime <= today.plusDays(1)) {
                    DateUtils.getRelativeTimeSpanString(
                        dateMillis,
                        System.currentTimeMillis(),
                        DateUtils.DAY_IN_MILLIS
                    ).toString()
                } else if (transactionTime.year == today.year) {
                    mDayMonthDateFormat.format(Date(dateMillis))
                } else {
                    DateUtils.formatDateTime(
                        context,
                        dateMillis,
                        DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR
                    )
                }
            return prettyDateText!!
        }
    }
}