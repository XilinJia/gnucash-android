/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.report

import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.widget.TableLayout
import android.widget.TextView
import butterknife.BindView
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.instance
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Money
import org.gnucash.android.ui.transaction.TransactionsActivity.Companion.displayBalance

/**
 * Balance sheet report fragment
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class BalanceSheetFragment : BaseReportFragment() {
    @JvmField
    @BindView(R.id.table_assets)
    var mAssetsTableLayout: TableLayout? = null

    @JvmField
    @BindView(R.id.table_liabilities)
    var mLiabilitiesTableLayout: TableLayout? = null

    @JvmField
    @BindView(R.id.table_equity)
    var mEquityTableLayout: TableLayout? = null

    @JvmField
    @BindView(R.id.total_liability_and_equity)
    var mNetWorth: TextView? = null
    var mAccountsDbAdapter = instance
    private var mAssetsBalance: Money? = null
    private var mLiabilitiesBalance: Money? = null
    private var mAssetAccountTypes: MutableList<AccountType>? = null
    private var mLiabilityAccountTypes: MutableList<AccountType>? = null
    private var mEquityAccountTypes: MutableList<AccountType>? = null

    override fun getLayoutResource(): Int {
        return R.layout.fragment_text_report
    }

    override fun getTitle(): Int {
        return R.string.title_balance_sheet_report
    }

    override fun getReportType(): ReportType {
        return ReportType.TEXT
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mAssetAccountTypes = ArrayList()
        mAssetAccountTypes!!.add(AccountType.ASSET)
        mAssetAccountTypes!!.add(AccountType.CASH)
        mAssetAccountTypes!!.add(AccountType.BANK)
        mLiabilityAccountTypes = ArrayList()
        mLiabilityAccountTypes!!.add(AccountType.LIABILITY)
        mLiabilityAccountTypes!!.add(AccountType.CREDIT)
        mEquityAccountTypes = ArrayList()
        mEquityAccountTypes!!.add(AccountType.EQUITY)
    }

    override fun requiresAccountTypeOptions(): Boolean {
        return false
    }

    override fun requiresTimeRangeOptions(): Boolean {
        return false
    }

    override fun generateReport() {
        mAssetsBalance = mAccountsDbAdapter.getAccountBalance(mAssetAccountTypes!!.toMutableList(), -1, System.currentTimeMillis())
        mLiabilitiesBalance =
            mAccountsDbAdapter.getAccountBalance(mLiabilityAccountTypes!!, -1L, System.currentTimeMillis())
    }

    override fun displayReport() {
        loadAccountViews(mAssetAccountTypes, mAssetsTableLayout)
        loadAccountViews(mLiabilityAccountTypes, mLiabilitiesTableLayout)
        loadAccountViews(mEquityAccountTypes, mEquityTableLayout)
        displayBalance(mNetWorth!!, mAssetsBalance!!.subtract(mLiabilitiesBalance!!))
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.menu_group_reports_by).isVisible = false
    }

    /**
     * Loads rows for the individual accounts and adds them to the report
     * @param accountTypes Account types for which to load balances
     * @param tableLayout Table layout into which to load the rows
     */
    private fun loadAccountViews(accountTypes: List<AccountType>?, tableLayout: TableLayout?) {
        val inflater = LayoutInflater.from(activity)
        val cursor = mAccountsDbAdapter.fetchAccounts(
            DatabaseSchema.AccountEntry.COLUMN_TYPE
                    + " IN ( '" + TextUtils.join("' , '", accountTypes!!) + "' ) AND "
                    + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0",
            null, DatabaseSchema.AccountEntry.COLUMN_FULL_NAME + " ASC"
        )
        while (cursor.moveToNext()) {
            val accountUID = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_UID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_NAME))
            val balance = mAccountsDbAdapter.getAccountBalance(accountUID)
            val view = inflater.inflate(R.layout.row_balance_sheet, tableLayout, false)
            (view.findViewById<View>(R.id.account_name) as TextView).text = name
            val balanceTextView = view.findViewById<View>(R.id.account_balance) as TextView
            displayBalance(balanceTextView, balance)
            tableLayout!!.addView(view)
        }
        val totalView = inflater.inflate(R.layout.row_balance_sheet, tableLayout, false)
        val layoutParams = totalView.layoutParams as TableLayout.LayoutParams
        layoutParams.setMargins(layoutParams.leftMargin, 20, layoutParams.rightMargin, layoutParams.bottomMargin)
        totalView.layoutParams = layoutParams
        val accountName = totalView.findViewById<View>(R.id.account_name) as TextView
        accountName.textSize = 16f
        accountName.setText(R.string.label_balance_sheet_total)
        val accountBalance = totalView.findViewById<View>(R.id.account_balance) as TextView
        accountBalance.textSize = 16f
        accountBalance.setTypeface(null, Typeface.BOLD)
        displayBalance(
            accountBalance,
            mAccountsDbAdapter.getAccountBalance(accountTypes, -1L, System.currentTimeMillis())
        )
        tableLayout!!.addView(totalView)
    }
}