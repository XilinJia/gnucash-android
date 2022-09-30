/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import butterknife.BindView
import butterknife.OnClick
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend.LegendForm
import com.github.mikephil.charting.components.Legend.LegendPosition
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import org.gnucash.android.R
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.instance
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Money
import org.gnucash.android.ui.report.PieChartFragment.Companion.groupSmallerSlices
import org.gnucash.android.ui.transaction.TransactionsActivity.Companion.displayBalance
import org.joda.time.LocalDate

/**
 * Shows a summary of reports
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 */
class ReportsOverviewFragment : BaseReportFragment() {
    @JvmField
    @BindView(R.id.btn_pie_chart)
    var mPieChartButton: Button? = null

    @JvmField
    @BindView(R.id.btn_bar_chart)
    var mBarChartButton: Button? = null

    @JvmField
    @BindView(R.id.btn_line_chart)
    var mLineChartButton: Button? = null

    @JvmField
    @BindView(R.id.btn_balance_sheet)
    var mBalanceSheetButton: Button? = null

    @JvmField
    @BindView(R.id.pie_chart)
    var mChart: PieChart? = null

    @JvmField
    @BindView(R.id.total_assets)
    var mTotalAssets: TextView? = null

    @JvmField
    @BindView(R.id.total_liabilities)
    var mTotalLiabilities: TextView? = null

    @JvmField
    @BindView(R.id.net_worth)
    var mNetWorth: TextView? = null
    private var mAccountsDbAdapter: AccountsDbAdapter? = null
    private var mAssetsBalance: Money? = null
    private var mLiabilitiesBalance: Money? = null
    private var mChartHasData = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mAccountsDbAdapter = instance
    }

    override fun getLayoutResource(): Int {
        return R.layout.fragment_report_summary
    }

    override fun getTitle(): Int {
        return R.string.title_reports
    }

    override fun getReportType(): ReportType {
        return ReportType.NONE
    }

    override fun requiresAccountTypeOptions(): Boolean {
        return false
    }

    override fun requiresTimeRangeOptions(): Boolean {
        return false
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(false)
        mChart!!.setCenterTextSize(PieChartFragment.CENTER_TEXT_SIZE.toFloat())
        mChart!!.setDescription("")
        mChart!!.setDrawSliceText(false)
        val legend = mChart!!.legend
        legend.isEnabled = true
        legend.isWordWrapEnabled = true
        legend.form = LegendForm.CIRCLE
        legend.position = LegendPosition.RIGHT_OF_CHART_CENTER
        legend.textSize = LEGEND_TEXT_SIZE.toFloat()
        var csl = ColorStateList(
            arrayOf(IntArray(0)), intArrayOf(
                ContextCompat.getColor(
                    context!!, R.color.account_green
                )
            )
        )
        setButtonTint(mPieChartButton, csl)
        csl = ColorStateList(arrayOf(IntArray(0)), intArrayOf(ContextCompat.getColor(context!!, R.color.account_red)))
        setButtonTint(mBarChartButton, csl)
        csl = ColorStateList(arrayOf(IntArray(0)), intArrayOf(ContextCompat.getColor(context!!, R.color.account_blue)))
        setButtonTint(mLineChartButton, csl)
        csl =
            ColorStateList(arrayOf(IntArray(0)), intArrayOf(ContextCompat.getColor(context!!, R.color.account_purple)))
        setButtonTint(mBalanceSheetButton, csl)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.menu_group_reports_by).isVisible = false
    }

    override fun generateReport() {
        val pieData = groupSmallerSlices(data, activity)
        if (pieData != null && pieData.yValCount != 0) {    // TODO: always true? XJ
            mChart!!.data = pieData
            val sum = mChart!!.data.yValueSum
            val total = resources.getString(R.string.label_chart_total)
            val currencySymbol = mCommodity!!.symbol
            mChart!!.centerText = String.format(PieChartFragment.TOTAL_VALUE_LABEL_PATTERN, total, sum, currencySymbol)
            mChartHasData = true
        } else {
            mChart!!.data = emptyData
            mChart!!.centerText = resources.getString(R.string.label_chart_no_data)
            mChart!!.legend.isEnabled = false
            mChartHasData = false
        }
        val accountTypes: MutableList<AccountType> = ArrayList()
        accountTypes.add(AccountType.ASSET)
        accountTypes.add(AccountType.CASH)
        accountTypes.add(AccountType.BANK)
        mAssetsBalance = mAccountsDbAdapter!!.getAccountBalance(accountTypes, -1, System.currentTimeMillis())
        accountTypes.clear()
        accountTypes.add(AccountType.LIABILITY)
        accountTypes.add(AccountType.CREDIT)
        mLiabilitiesBalance = mAccountsDbAdapter!!.getAccountBalance(accountTypes, -1, System.currentTimeMillis())
    }

    /**
     * Returns `PieData` instance with data entries, colors and labels
     * @return `PieData` instance
     */
    private val data: PieData
        get() {
            val dataSet = PieDataSet(null, "")
            val labels: MutableList<String?> = ArrayList()
            val colors: MutableList<Int> = ArrayList()
            for (account in mAccountsDbAdapter!!.simpleAccountList) {
                if (account.mAccountType === AccountType.EXPENSE && !account.isPlaceholderAccount
                    && account.getMCommodity() == mCommodity
                ) {
                    val start = LocalDate().minusMonths(2).dayOfMonth().withMinimumValue().toDate().time
                    val end = LocalDate().plusDays(1).toDate().time
                    val balance = mAccountsDbAdapter!!.getAccountsBalance(listOf(account.mUID), start, end).asDouble()
                    if (balance > 0) {
                        dataSet.addEntry(Entry(balance.toFloat(), dataSet.entryCount))
                        colors.add(if (account.getMColor() != Account.DEFAULT_COLOR) account.getMColor() else ReportsActivity.COLORS[(dataSet.entryCount - 1) % ReportsActivity.COLORS.size])
                        labels.add(account.mName)
                    }
                }
            }
            dataSet.colors = colors
            dataSet.sliceSpace = PieChartFragment.SPACE_BETWEEN_SLICES
            return PieData(labels, dataSet)
        }

    override fun displayReport() {
        if (mChartHasData) {
            mChart!!.animateXY(1800, 1800)
            mChart!!.setTouchEnabled(true)
        } else {
            mChart!!.setTouchEnabled(false)
        }
        mChart!!.highlightValues(null)
        mChart!!.invalidate()
        displayBalance(mTotalAssets!!, mAssetsBalance!!)
        displayBalance(mTotalLiabilities!!, mLiabilitiesBalance!!)
        displayBalance(mNetWorth!!, mAssetsBalance!!.subtract(mLiabilitiesBalance!!))
    }

    /**
     * Returns a data object that represents situation when no user data available
     * @return a `PieData` instance for situation when no user data available
     */
    private val emptyData: PieData
        get() {
            val dataSet = PieDataSet(null, resources.getString(R.string.label_chart_no_data))
            dataSet.addEntry(Entry(1f, 0))
            dataSet.color = NO_DATA_COLOR
            dataSet.setDrawValues(false)
            return PieData(listOf(""), dataSet)
        }

    @OnClick(R.id.btn_bar_chart, R.id.btn_pie_chart, R.id.btn_line_chart, R.id.btn_balance_sheet)
    fun onClickChartTypeButton(view: View) {
        val fragment: BaseReportFragment = when (view.id) {
            R.id.btn_pie_chart -> PieChartFragment()
            R.id.btn_bar_chart -> StackedBarChartFragment()
            R.id.btn_line_chart -> CashFlowLineChartFragment()
            R.id.btn_balance_sheet -> BalanceSheetFragment()
            else -> this
        }
        val fragmentManager = activity!!.supportFragmentManager
        fragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    @SuppressLint("RestrictedApi")
    fun setButtonTint(button: Button?, tint: ColorStateList?) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP && button is AppCompatButton) {
            button.supportBackgroundTintList = tint
        } else {
            ViewCompat.setBackgroundTintList(button!!, tint)
        }
        button.setTextColor(ContextCompat.getColor(context!!, android.R.color.white))
    }

    companion object {
        const val LEGEND_TEXT_SIZE = 14
    }
}