/*
 * Copyright (c) 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import butterknife.BindView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.LargeValueFormatter
import org.gnucash.android.R
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.AccountType
import org.gnucash.android.ui.report.ReportsActivity.GroupInterval
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import java.util.*

/**
 * Fragment for line chart reports
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class CashFlowLineChartFragment : BaseReportFragment() {
    private val mAccountsDbAdapter = AccountsDbAdapter.instance
    private val mEarliestTimestampsMap: MutableMap<AccountType, Long> = EnumMap(AccountType::class.java)
    private val mLatestTimestampsMap: MutableMap<AccountType, Long> = EnumMap(AccountType::class.java)
    private var mEarliestTransactionTimestamp: Long = 0
    private var mLatestTransactionTimestamp: Long = 0
    private var mChartDataPresent = true

    @JvmField
    @BindView(R.id.line_chart)
    var mChart: LineChart? = null
    override fun getLayoutResource(): Int {
        return R.layout.fragment_line_chart
    }

    override fun getTitle(): Int {
        return R.string.title_cash_flow_report
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mChart!!.setOnChartValueSelectedListener(this)
        mChart!!.setDescription("")
        mChart!!.xAxis.setDrawGridLines(false)
        mChart!!.axisRight.isEnabled = false
        mChart!!.axisLeft.enableGridDashedLine(4.0f, 4.0f, 0f)
        mChart!!.axisLeft.valueFormatter = LargeValueFormatter(mCommodity!!.symbol)
        val legend = mChart!!.legend
        legend.position = Legend.LegendPosition.BELOW_CHART_CENTER
        legend.textSize = 16f
        legend.form = Legend.LegendForm.CIRCLE
    }

    override fun getReportType(): ReportType {
        return ReportType.LINE_CHART
    }

    /**
     * Returns a data object that represents a user data of the specified account types
     * @param accountTypeList account's types which will be displayed
     * @return a `LineData` instance that represents a user data
     */
    private fun getData(accountTypeList: MutableList<AccountType>): LineData {
        Log.w(TAG, "getData")
        calculateEarliestAndLatestTimestamps(accountTypeList)
        // LocalDateTime?
        var startDate: LocalDate
        val endDate: LocalDate
        if (mReportPeriodStart == -1L && mReportPeriodEnd == -1L) {
            startDate = LocalDate(mEarliestTransactionTimestamp).withDayOfMonth(1)
            endDate = LocalDate(mLatestTransactionTimestamp).withDayOfMonth(1)
        } else {
            startDate = LocalDate(mReportPeriodStart).withDayOfMonth(1)
            endDate = LocalDate(mReportPeriodEnd).withDayOfMonth(1)
        }
        val count = getDateDiff(LocalDateTime(startDate.toDate().time), LocalDateTime(endDate.toDate().time))
        Log.d(TAG, "X-axis count$count")
        val xValues: MutableList<String> = ArrayList()
        for (i in 0..count) {
            when (mGroupInterval) {
                GroupInterval.MONTH -> {
                    xValues.add(startDate.toString(X_AXIS_PATTERN))
                    Log.d(TAG, "X-axis " + startDate.toString("MM yy"))
                    startDate = startDate.plusMonths(1)
                }

                GroupInterval.QUARTER -> {
                    val quarter = getQuarter(LocalDateTime(startDate.toDate().time))
                    xValues.add("Q" + quarter + startDate.toString(" yy"))
                    Log.d(TAG, "X-axis " + "Q" + quarter + startDate.toString(" MM yy"))
                    startDate = startDate.plusMonths(3)
                }

                GroupInterval.YEAR -> {
                    xValues.add(startDate.toString("yyyy"))
                    Log.d(TAG, "X-axis " + startDate.toString("yyyy"))
                    startDate = startDate.plusYears(1)
                }

                else -> {}
            }
        }
        val dataSets: MutableList<LineDataSet> = ArrayList()
        for (accountType in accountTypeList) {
            val set = LineDataSet(getEntryList(accountType), accountType.toString())
            set.setDrawFilled(true)
            set.lineWidth = 2f
            set.color = COLORS[dataSets.size]
            set.fillColor = FILL_COLORS[dataSets.size]
            dataSets.add(set)
        }
        val lineData = LineData(xValues, dataSets)
        if (lineData.yValueSum == 0f) {
            mChartDataPresent = false
            return emptyData
        }
        return lineData
    }

    /**
     * Returns a data object that represents situation when no user data available
     * @return a `LineData` instance for situation when no user data available
     */
    private val emptyData: LineData
        get() {
            val xValues: MutableList<String> = ArrayList()
            val yValues: MutableList<Entry> = ArrayList()
            for (i in 0 until NO_DATA_BAR_COUNTS) {
                xValues.add("")
                yValues.add(Entry(if (i % 2 == 0) 5f else 4.5f, i))
            }
            val set = LineDataSet(yValues, resources.getString(R.string.label_chart_no_data))
            set.setDrawFilled(true)
            set.setDrawValues(false)
            set.color = NO_DATA_COLOR
            set.fillColor = NO_DATA_COLOR
            return LineData(xValues, listOf(set))
        }

    /**
     * Returns entries which represent a user data of the specified account type
     * @param accountType account's type which user data will be processed
     * @return entries which represent a user data
     */
    private fun getEntryList(accountType: AccountType): List<Entry> {
        val accountUIDList: MutableList<String?> = ArrayList()
        for (account in mAccountsDbAdapter.simpleAccountList) {
            if (account.mAccountType === accountType && !account.isPlaceholderAccount
                && account.getMCommodity() == mCommodity
            ) {
                accountUIDList.add(account.mUID)
            }
        }
        var earliest: LocalDateTime
        val latest: LocalDateTime
        if (mReportPeriodStart == -1L && mReportPeriodEnd == -1L) {
            earliest = LocalDateTime(mEarliestTimestampsMap[accountType])
            latest = LocalDateTime(mLatestTimestampsMap[accountType])
        } else {
            earliest = LocalDateTime(mReportPeriodStart)
            latest = LocalDateTime(mReportPeriodEnd)
        }
        Log.d(TAG, "Earliest " + accountType + " date " + earliest.toString("dd MM yyyy"))
        Log.d(TAG, "Latest " + accountType + " date " + latest.toString("dd MM yyyy"))
        val xAxisOffset = getDateDiff(LocalDateTime(mEarliestTransactionTimestamp), earliest)
        val count = getDateDiff(earliest, latest)
        val values: MutableList<Entry> = ArrayList(count + 1)
        for (i in 0..count) {
            var start: Long = 0
            var end: Long = 0
            when (mGroupInterval) {
                GroupInterval.QUARTER -> {
                    val quarter = getQuarter(earliest)
                    start = earliest.withMonthOfYear(quarter * 3 - 2).dayOfMonth().withMinimumValue().millisOfDay()
                        .withMinimumValue().toDate().time
                    end = earliest.withMonthOfYear(quarter * 3).dayOfMonth().withMaximumValue().millisOfDay()
                        .withMaximumValue().toDate().time
                    earliest = earliest.plusMonths(3)
                }

                GroupInterval.MONTH -> {
                    start = earliest.dayOfMonth().withMinimumValue().millisOfDay().withMinimumValue().toDate().time
                    end = earliest.dayOfMonth().withMaximumValue().millisOfDay().withMaximumValue().toDate().time
                    earliest = earliest.plusMonths(1)
                }

                GroupInterval.YEAR -> {
                    start = earliest.dayOfYear().withMinimumValue().millisOfDay().withMinimumValue().toDate().time
                    end = earliest.dayOfYear().withMaximumValue().millisOfDay().withMaximumValue().toDate().time
                    earliest = earliest.plusYears(1)
                }

                else -> {}
            }
            val balance = mAccountsDbAdapter.getAccountsBalance(accountUIDList, start, end).asDouble().toFloat()
            values.add(Entry(balance, i + xAxisOffset))
            Log.d(TAG, accountType.toString() + earliest.toString(" MMM yyyy") + ", balance = " + balance)
        }
        return values
    }

    /**
     * Calculates the earliest and latest transaction's timestamps of the specified account types
     * @param accountTypeList account's types which will be processed
     */
    private fun calculateEarliestAndLatestTimestamps(accountTypeList: MutableList<AccountType>) {
        if (mReportPeriodStart != -1L && mReportPeriodEnd != -1L) {
            mEarliestTransactionTimestamp = mReportPeriodStart
            mLatestTransactionTimestamp = mReportPeriodEnd
            return
        }
        val dbAdapter = TransactionsDbAdapter.instance
        val iter = accountTypeList.iterator()
        while (iter.hasNext()) {
            val type = iter.next()
            val earliest = dbAdapter.getTimestampOfEarliestTransaction(type, mCommodity!!.mMnemonic)
            val latest = dbAdapter.getTimestampOfLatestTransaction(type, mCommodity!!.mMnemonic)
            if (earliest > 0 && latest > 0) {
                mEarliestTimestampsMap[type] = earliest
                mLatestTimestampsMap[type] = latest
            } else {
                iter.remove()
            }
        }
        if (mEarliestTimestampsMap.isEmpty() || mLatestTimestampsMap.isEmpty()) {
            return
        }
        val timestamps: MutableList<Long> = ArrayList(mEarliestTimestampsMap.values)
        timestamps.addAll(mLatestTimestampsMap.values)
        timestamps.sort()
        mEarliestTransactionTimestamp = timestamps[0]
        mLatestTransactionTimestamp = timestamps[timestamps.size - 1]
    }

    override fun requiresAccountTypeOptions(): Boolean {
        return false
    }

    override fun generateReport() {
        val lineData = getData(ArrayList(listOf(AccountType.INCOME, AccountType.EXPENSE)))
        if (lineData != null) { // TODO: always true? XJ
            mChart!!.data = lineData
            mChartDataPresent = true
        } else {
            mChartDataPresent = false
        }
    }

    override fun displayReport() {
        if (!mChartDataPresent) {
            mChart!!.axisLeft.axisMaxValue = 10f
            mChart!!.axisLeft.setDrawLabels(false)
            mChart!!.xAxis.setDrawLabels(false)
            mChart!!.setTouchEnabled(false)
            mSelectedValueTextView!!.text = resources.getString(R.string.label_chart_no_data)
        } else {
            mChart!!.animateX(ANIMATION_DURATION)
        }
        mChart!!.invalidate()
    }

    override fun onTimeRangeUpdated(start: Long, end: Long) {
        if (mReportPeriodStart != start || mReportPeriodEnd != end) {
            mReportPeriodStart = start
            mReportPeriodEnd = end
            mChart!!.data = getData(
                ArrayList(
                    listOf(
                        AccountType.INCOME,
                        AccountType.EXPENSE
                    )
                )
            )
            mChart!!.invalidate()
        }
    }

    override fun onGroupingUpdated(groupInterval: GroupInterval?) {
        if (mGroupInterval != groupInterval) {
            mGroupInterval = groupInterval
            mChart!!.data = getData(
                ArrayList(
                    listOf(
                        AccountType.INCOME,
                        AccountType.EXPENSE
                    )
                )
            )
            mChart!!.invalidate()
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.menu_toggle_average_lines).isVisible = mChartDataPresent
        // hide pie/bar chart specific menu items
        menu.findItem(R.id.menu_order_by_size).isVisible = false
        menu.findItem(R.id.menu_toggle_labels).isVisible = false
        menu.findItem(R.id.menu_percentage_mode).isVisible = false
        menu.findItem(R.id.menu_group_other_slice).isVisible = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.isCheckable) item.isChecked = !item.isChecked
        return when (item.itemId) {
            R.id.menu_toggle_legend -> {
                mChart!!.legend.isEnabled = !mChart!!.legend.isEnabled
                mChart!!.invalidate()
                true
            }

            R.id.menu_toggle_average_lines -> {
                if (mChart!!.axisLeft.limitLines.isEmpty()) {
                    for (set in mChart!!.data.dataSets) {
                        val line = LimitLine(set.yValueSum / set.entryCount, set.label)
                        line.enableDashedLine(10f, 5f, 0f)
                        line.lineColor = set.color
                        mChart!!.axisLeft.addLimitLine(line)
                    }
                } else {
                    mChart!!.axisLeft.removeAllLimitLines()
                }
                mChart!!.invalidate()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onValueSelected(e: Entry, dataSetIndex: Int, h: Highlight) {
        val label = mChart!!.data.xVals[e.xIndex]
        val value = e.getVal().toDouble()
        val sum = mChart!!.data.getDataSetByIndex(dataSetIndex).yValueSum.toDouble()
        mSelectedValueTextView!!.text = String.format(SELECTED_VALUE_PATTERN, label, value, value / sum * 100)
    }

    companion object {
        private const val X_AXIS_PATTERN = "MMM YY"
        private const val ANIMATION_DURATION = 3000
        private const val NO_DATA_BAR_COUNTS = 5
        private val COLORS = intArrayOf(
            Color.parseColor("#68F1AF"), Color.parseColor("#cc1f09"), Color.parseColor("#EE8600"),
            Color.parseColor("#1469EB"), Color.parseColor("#B304AD")
        )
        private val FILL_COLORS = intArrayOf(
            Color.parseColor("#008000"), Color.parseColor("#FF0000"), Color.parseColor("#BE6B00"),
            Color.parseColor("#0065FF"), Color.parseColor("#8F038A")
        )
    }
}