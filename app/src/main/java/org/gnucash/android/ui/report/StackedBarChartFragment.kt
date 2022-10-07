/*
 * Copyright (c) 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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

import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import butterknife.BindView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.LargeValueFormatter
import org.gnucash.android.R
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.ui.report.ReportsActivity.GroupInterval
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import java.util.*
import kotlin.math.abs

/**
 * Activity used for drawing a bar chart
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class StackedBarChartFragment : BaseReportFragment() {
    private val mAccountsDbAdapter = AccountsDbAdapter.instance

    @JvmField
    @BindView(R.id.bar_chart)
    var mChart: BarChart? = null
    private var mUseAccountColor = true
    private var mTotalPercentageMode = true
    private var mChartDataPresent = true
    override fun getTitle(): Int {
        return R.string.title_cash_flow_report
    }

    override fun getLayoutResource(): Int {
        return R.layout.fragment_bar_chart
    }

    override fun getReportType(): ReportType {
        return ReportType.BAR_CHART
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mUseAccountColor = PreferenceManager.getDefaultSharedPreferences(activity)
            .getBoolean(getString(R.string.key_use_account_color), false)
        mChart!!.setOnChartValueSelectedListener(this)
        mChart!!.setDescription("")
        //        mChart.setDrawValuesForWholeStack(false);
        mChart!!.xAxis.setDrawGridLines(false)
        mChart!!.axisRight.isEnabled = false
        mChart!!.axisLeft.setStartAtZero(false)
        mChart!!.axisLeft.enableGridDashedLine(4.0f, 4.0f, 0f)
        mChart!!.axisLeft.valueFormatter = LargeValueFormatter(mCommodity!!.symbol)
        val chartLegend = mChart!!.legend
        chartLegend.form = Legend.LegendForm.CIRCLE
        chartLegend.position = Legend.LegendPosition.BELOW_CHART_CENTER
        chartLegend.isWordWrapEnabled = true
    }

    /**
     * Returns a data object that represents a user data of the specified account types
     * @return a `BarData` instance that represents a user data
     */
    private val data: BarData
        get() {
            val values: MutableList<BarEntry> = ArrayList()
            val labels: MutableList<String?> = ArrayList()
            val colors: MutableList<Int?> = ArrayList()
            val accountToColorMap: MutableMap<String?, Int> = LinkedHashMap()
            val xValues: MutableList<String> = ArrayList()
            var tmpDate = LocalDateTime(getStartDate(mAccountType!!).toDate().time)
            val count = getDateDiff(
                LocalDateTime(getStartDate(mAccountType!!).toDate().time),
                LocalDateTime(getEndDate(mAccountType!!).toDate().time)
            )
            for (i in 0..count) {
                var start: Long = 0
                var end: Long = 0
                when (mGroupInterval) {
                    GroupInterval.MONTH -> {
                        start = tmpDate.dayOfMonth().withMinimumValue().millisOfDay().withMinimumValue().toDate().time
                        end = tmpDate.dayOfMonth().withMaximumValue().millisOfDay().withMaximumValue().toDate().time
                        xValues.add(tmpDate.toString(X_AXIS_MONTH_PATTERN))
                        tmpDate = tmpDate.plusMonths(1)
                    }

                    GroupInterval.QUARTER -> {
                        val quarter = getQuarter(tmpDate)
                        start = tmpDate.withMonthOfYear(quarter * 3 - 2).dayOfMonth().withMinimumValue().millisOfDay()
                            .withMinimumValue().toDate().time
                        end = tmpDate.withMonthOfYear(quarter * 3).dayOfMonth().withMaximumValue().millisOfDay()
                            .withMaximumValue().toDate().time
                        xValues.add(String.format(X_AXIS_QUARTER_PATTERN, quarter, tmpDate.toString(" YY")))
                        tmpDate = tmpDate.plusMonths(3)
                    }

                    GroupInterval.YEAR -> {
                        start = tmpDate.dayOfYear().withMinimumValue().millisOfDay().withMinimumValue().toDate().time
                        end = tmpDate.dayOfYear().withMaximumValue().millisOfDay().withMaximumValue().toDate().time
                        xValues.add(tmpDate.toString(X_AXIS_YEAR_PATTERN))
                        tmpDate = tmpDate.plusYears(1)
                    }

                    else -> {}
                }
                val stack: MutableList<Float> = ArrayList()
                for (account in mAccountsDbAdapter.simpleAccountList) {
                    if (account.mAccountType === mAccountType && !account.isPlaceholderAccount
                        && account.getMCommodity() == mCommodity
                    ) {
                        val balance = mAccountsDbAdapter.getAccountsBalance(listOf(account.mUID), start, end).asDouble()
                        if (balance != 0.0) {
                            stack.add(balance.toFloat())
                            var accountName = account.mName
                            while (labels.contains(accountName)) {
                                if (!accountToColorMap.containsKey(account.mUID)) {
                                    for (label in labels) {
                                        if (label == accountName) {
                                            accountName += " "
                                        }
                                    }
                                } else {
                                    break
                                }
                            }
                            labels.add(accountName)
                            if (!accountToColorMap.containsKey(account.mUID)) {
                                val color: Int = if (mUseAccountColor) {
                                    if (account.getMColor() != Account.DEFAULT_COLOR) account.getMColor() else ReportsActivity.COLORS[accountToColorMap.size % ReportsActivity.COLORS.size]
                                } else {
                                    ReportsActivity.COLORS[accountToColorMap.size % ReportsActivity.COLORS.size]
                                }
                                accountToColorMap[account.mUID] = color
                            }
                            colors.add(accountToColorMap[account.mUID])
                            Log.d(
                                TAG,
                                mAccountType.toString() + tmpDate.toString(" MMMM yyyy ") + account.mName + " = " + stack[stack.size - 1]
                            )
                        }
                    }
                }
                val stackLabels = labels.subList(labels.size - stack.size, labels.size).toString()
                values.add(BarEntry(floatListToArray(stack), i, stackLabels))
            }
            val set = BarDataSet(values, "")
            set.setDrawValues(false)
            set.stackLabels = labels.toTypedArray()
            set.colors = colors
            if (set.yValueSum == 0f) {
                mChartDataPresent = false
                return emptyData
            }
            mChartDataPresent = true
            return BarData(xValues, set)
        }

    /**
     * Returns a data object that represents situation when no user data available
     * @return a `BarData` instance for situation when no user data available
     */
    private val emptyData: BarData
        get() {
            val xValues: MutableList<String> = ArrayList()
            val yValues: MutableList<BarEntry> = ArrayList()
            for (i in 0 until NO_DATA_BAR_COUNTS) {
                xValues.add("")
                yValues.add(BarEntry((i + 1).toFloat(), i))
            }
            val set = BarDataSet(yValues, resources.getString(R.string.label_chart_no_data))
            set.setDrawValues(false)
            set.color = NO_DATA_COLOR
            return BarData(xValues, set)
        }

    /**
     * Returns the start data of x-axis for the specified account type
     * @param accountType account type
     * @return the start data
     */
    private fun getStartDate(accountType: AccountType): LocalDate {
        val adapter = TransactionsDbAdapter.instance
        val code = mCommodity!!.mMnemonic
        var startDate: LocalDate = if (mReportPeriodStart == -1L) {
            LocalDate(adapter.getTimestampOfEarliestTransaction(accountType, code))
        } else {
            LocalDate(mReportPeriodStart)
        }
        startDate = startDate.withDayOfMonth(1)
        Log.d(TAG, accountType.toString() + " X-axis star date: " + startDate.toString("dd MM yyyy"))
        return startDate
    }

    /**
     * Returns the end data of x-axis for the specified account type
     * @param accountType account type
     * @return the end data
     */
    private fun getEndDate(accountType: AccountType): LocalDate {
        val adapter = TransactionsDbAdapter.instance
        val code = mCommodity!!.mMnemonic
        var endDate: LocalDate = if (mReportPeriodEnd == -1L) {
            LocalDate(adapter.getTimestampOfLatestTransaction(accountType, code))
        } else {
            LocalDate(mReportPeriodEnd)
        }
        endDate = endDate.withDayOfMonth(1)
        Log.d(TAG, accountType.toString() + " X-axis end date: " + endDate.toString("dd MM yyyy"))
        return endDate
    }

    /**
     * Converts the specified list of floats to an array
     * @param list a list of floats
     * @return a float array
     */
    private fun floatListToArray(list: List<Float>): FloatArray {
        val array = FloatArray(list.size)
        for (i in list.indices) {
            array[i] = list[i]
        }
        return array
    }

    public override fun generateReport() {
        mChart!!.data = data
        setCustomLegend()
        mChart!!.axisLeft.setDrawLabels(mChartDataPresent)
        mChart!!.xAxis.setDrawLabels(mChartDataPresent)
        mChart!!.setTouchEnabled(mChartDataPresent)
    }

    override fun displayReport() {
        mChart!!.notifyDataSetChanged()
        mChart!!.highlightValues(null)
        if (mChartDataPresent) {
            mChart!!.animateY(ANIMATION_DURATION)
        } else {
            mChart!!.clearAnimation()
            mSelectedValueTextView!!.setText(R.string.label_chart_no_data)
        }
        mChart!!.invalidate()
    }

    /**
     * Sets custom legend. Disable legend if its items count greater than `COLORS` array size.
     */
    private fun setCustomLegend() {
        val legend = mChart!!.legend
        val dataSet = mChart!!.data.getDataSetByIndex(0)
        val labels = LinkedHashSet(listOf(*dataSet.stackLabels))
        val colors = LinkedHashSet(dataSet.colors)
        if (ReportsActivity.COLORS.size >= labels.size) {
            legend.setCustom(ArrayList(colors), ArrayList(labels))
            return
        }
        legend.isEnabled = false
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.menu_percentage_mode).isVisible = mChartDataPresent
        // hide pie/line chart specific menu items
        menu.findItem(R.id.menu_order_by_size).isVisible = false
        menu.findItem(R.id.menu_toggle_labels).isVisible = false
        menu.findItem(R.id.menu_toggle_average_lines).isVisible = false
        menu.findItem(R.id.menu_group_other_slice).isVisible = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.isCheckable) item.isChecked = !item.isChecked
        return when (item.itemId) {
            R.id.menu_toggle_legend -> {
                val legend = mChart!!.legend
                if (!legend.isLegendCustom) {
                    Toast.makeText(activity, R.string.toast_legend_too_long, Toast.LENGTH_LONG).show()
                    item.isChecked = false
                } else {
                    item.isChecked = !mChart!!.legend.isEnabled
                    legend.isEnabled = !mChart!!.legend.isEnabled
                    mChart!!.invalidate()
                }
                true
            }

            R.id.menu_percentage_mode -> {
                mTotalPercentageMode = !mTotalPercentageMode
                val msgId =
                    if (mTotalPercentageMode) R.string.toast_chart_percentage_mode_total else R.string.toast_chart_percentage_mode_current_bar
                Toast.makeText(activity, msgId, Toast.LENGTH_LONG).show()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onValueSelected(e: Entry, dataSetIndex: Int, h: Highlight) {
        if ((e as BarEntry).vals.isEmpty()) return
        val index = if (h.stackIndex == -1) 0 else h.stackIndex
        val stackLabels = e.data.toString()
        val label = (mChart!!.data.xVals[e.xIndex] + ", "
                + stackLabels.substring(1, stackLabels.length - 1).split(",".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()[index])
        val value = abs(e.vals[index]).toDouble()
        var sum = 0.0
        if (mTotalPercentageMode) {
            for (barEntry in mChart!!.data.getDataSetByIndex(dataSetIndex).yVals) {
                sum += (barEntry.negativeSum + barEntry.positiveSum).toDouble()
            }
        } else {
            sum = (e.negativeSum + e.positiveSum).toDouble()
        }
        mSelectedValueTextView!!.text =
            String.format(SELECTED_VALUE_PATTERN, label.trim { it <= ' ' }, value, value / sum * 100)
    }

    companion object {
        private const val X_AXIS_MONTH_PATTERN = "MMM YY"
        private const val X_AXIS_QUARTER_PATTERN = "Q%d %s"
        private const val X_AXIS_YEAR_PATTERN = "YYYY"
        private const val ANIMATION_DURATION = 2000
        private const val NO_DATA_BAR_COUNTS = 3
    }
}