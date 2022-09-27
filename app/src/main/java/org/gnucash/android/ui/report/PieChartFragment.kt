/*
 * Copyright (c) 2014-2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuItem
import butterknife.BindView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend.LegendForm
import com.github.mikephil.charting.components.Legend.LegendPosition
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.highlight.Highlight
import org.gnucash.android.R
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.instance
import org.gnucash.android.model.Account

/**
 * Activity used for drawing a pie chart
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class PieChartFragment : BaseReportFragment() {
    @JvmField
    @BindView(R.id.pie_chart)
    var mChart: PieChart? = null
    private var mAccountsDbAdapter: AccountsDbAdapter? = null
    private var mChartDataPresent = true
    private var mUseAccountColor = true
    private var mGroupSmallerSlices = true
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mUseAccountColor = PreferenceManager.getDefaultSharedPreferences(activity)
            .getBoolean(getString(R.string.key_use_account_color), false)
        mAccountsDbAdapter = instance
        mChart!!.setCenterTextSize(CENTER_TEXT_SIZE.toFloat())
        mChart!!.setDescription("")
        mChart!!.setOnChartValueSelectedListener(this)
        mChart!!.legend.form = LegendForm.CIRCLE
        mChart!!.legend.isWordWrapEnabled = true
        mChart!!.legend.position = LegendPosition.BELOW_CHART_CENTER
    }

    override fun getTitle(): Int {
        return R.string.title_pie_chart
    }

    override fun getReportType(): ReportType {
        return ReportType.PIE_CHART
    }

    override fun getLayoutResource(): Int {
        return R.layout.fragment_pie_chart
    }

    override fun generateReport() {
        val pieData = data
        if (pieData != null && pieData.yValCount != 0) {
            mChartDataPresent = true
            mChart!!.data = if (mGroupSmallerSlices) groupSmallerSlices(pieData, activity) else pieData
            val sum = mChart!!.data.yValueSum
            val total = resources.getString(R.string.label_chart_total)
            val currencySymbol = mCommodity!!.symbol
            mChart!!.centerText =
                String.format(TOTAL_VALUE_LABEL_PATTERN, total, sum, currencySymbol)
        } else {
            mChartDataPresent = false
            mChart!!.centerText = resources.getString(R.string.label_chart_no_data)
            mChart!!.data = emptyData
        }
    }

    override fun displayReport() {
        if (mChartDataPresent) {
            mChart!!.animateXY(ANIMATION_DURATION, ANIMATION_DURATION)
        }
        mSelectedValueTextView!!.setText(R.string.label_select_pie_slice_to_see_details)
        mChart!!.setTouchEnabled(mChartDataPresent)
        mChart!!.highlightValues(null)
        mChart!!.invalidate()
    }

    /**
     * Returns `PieData` instance with data entries, colors and labels
     * @return `PieData` instance
     */
    private val data: PieData
        private get() {
            val dataSet = PieDataSet(null, "")
            val labels: MutableList<String?> = ArrayList()
            val colors: MutableList<Int> = ArrayList()
            for (account in mAccountsDbAdapter!!.simpleAccountList) {
                if (account.mAccountType === mAccountType && !account.isPlaceholderAccount
                    && account.getMCommodity().equals(mCommodity)
                ) {
                    val balance = mAccountsDbAdapter!!.getAccountsBalance(
                        listOf(account.mUID),
                        mReportPeriodStart, mReportPeriodEnd
                    ).asDouble()
                    if (balance > 0) {
                        dataSet.addEntry(Entry(balance.toFloat(), dataSet.entryCount))
                        var color: Int
                        color = if (mUseAccountColor) {
                            if (account.getMColor() != Account.DEFAULT_COLOR) account.getMColor() else ReportsActivity.COLORS[(dataSet.entryCount - 1) % ReportsActivity.COLORS.size]
                        } else {
                            ReportsActivity.COLORS[(dataSet.entryCount - 1) % ReportsActivity.COLORS.size]
                        }
                        colors.add(color)
                        labels.add(account.mName)
                    }
                }
            }
            dataSet.colors = colors
            dataSet.sliceSpace = SPACE_BETWEEN_SLICES
            return PieData(labels, dataSet)
        }

    /**
     * Returns a data object that represents situation when no user data available
     * @return a `PieData` instance for situation when no user data available
     */
    private val emptyData: PieData
        private get() {
            val dataSet = PieDataSet(null, resources.getString(R.string.label_chart_no_data))
            dataSet.addEntry(Entry(1f, 0))
            dataSet.color = NO_DATA_COLOR
            dataSet.setDrawValues(false)
            return PieData(listOf(""), dataSet)
        }

    /**
     * Sorts the pie's slices in ascending order
     */
    private fun bubbleSort() {
        val labels = mChart!!.data.xVals
        val values = mChart!!.data.dataSet.yVals
        val colors = mChart!!.data.dataSet.colors
        var tmp1: Float
        var tmp2: String
        var tmp3: Int
        for (i in 0 until values.size - 1) {
            for (j in 1 until values.size - i) {
                if (values[j - 1].getVal() > values[j].getVal()) {
                    tmp1 = values[j - 1].getVal()
                    values[j - 1].setVal(values[j].getVal())
                    values[j].setVal(tmp1)
                    tmp2 = labels[j - 1]
                    labels[j - 1] = labels[j]
                    labels[j] = tmp2
                    tmp3 = colors[j - 1]
                    colors[j - 1] = colors[j]
                    colors[j] = tmp3
                }
            }
        }
        mChart!!.notifyDataSetChanged()
        mChart!!.highlightValues(null)
        mChart!!.invalidate()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.menu_order_by_size).isVisible = mChartDataPresent
        menu.findItem(R.id.menu_toggle_labels).isVisible = mChartDataPresent
        menu.findItem(R.id.menu_group_other_slice).isVisible = mChartDataPresent
        // hide line/bar chart specific menu items
        menu.findItem(R.id.menu_percentage_mode).isVisible = false
        menu.findItem(R.id.menu_toggle_average_lines).isVisible = false
        menu.findItem(R.id.menu_group_reports_by).isVisible = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.isCheckable) item.isChecked = !item.isChecked
        return when (item.itemId) {
            R.id.menu_order_by_size -> {
                bubbleSort()
                true
            }

            R.id.menu_toggle_legend -> {
                mChart!!.legend.isEnabled = !mChart!!.legend.isEnabled
                mChart!!.notifyDataSetChanged()
                mChart!!.invalidate()
                true
            }

            R.id.menu_toggle_labels -> {
                mChart!!.data.setDrawValues(!mChart!!.isDrawSliceTextEnabled)
                mChart!!.setDrawSliceText(!mChart!!.isDrawSliceTextEnabled)
                mChart!!.invalidate()
                true
            }

            R.id.menu_group_other_slice -> {
                mGroupSmallerSlices = !mGroupSmallerSlices
                refresh()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onValueSelected(e: Entry, dataSetIndex: Int, h: Highlight) {
        if (e == null) return
        val label = mChart!!.data.xVals[e.xIndex]
        val value = e.getVal()
        val percent = value / mChart!!.data.yValueSum * 100
        mSelectedValueTextView!!.text = String.format(SELECTED_VALUE_PATTERN, label, value, percent)
    }

    companion object {
        const val TOTAL_VALUE_LABEL_PATTERN = "%s\n%.2f %s"
        private const val ANIMATION_DURATION = 1800
        const val CENTER_TEXT_SIZE = 18

        /**
         * The space in degrees between the chart slices
         */
        const val SPACE_BETWEEN_SLICES = 2f

        /**
         * All pie slices less than this threshold will be group in "other" slice. Using percents not absolute values.
         */
        private const val GROUPING_SMALLER_SLICES_THRESHOLD = 5.0

        /**
         * Groups smaller slices. All smaller slices will be combined and displayed as a single "Other".
         * @param data the pie data which smaller slices will be grouped
         * @param context Context for retrieving resources
         * @return a `PieData` instance with combined smaller slices
         */
        @JvmStatic
        fun groupSmallerSlices(data: PieData, context: Context?): PieData {
            var otherSlice = 0f
            val newEntries: MutableList<Entry> = ArrayList()
            val newLabels: MutableList<String> = ArrayList()
            val newColors: MutableList<Int> = ArrayList()
            val entries = data.dataSet.yVals
            for (i in entries.indices) {
                val `val` = entries[i].getVal()
                if (`val` / data.yValueSum * 100 > GROUPING_SMALLER_SLICES_THRESHOLD) {
                    newEntries.add(Entry(`val`, newEntries.size))
                    newLabels.add(data.xVals[i])
                    newColors.add(data.dataSet.colors[i])
                } else {
                    otherSlice += `val`
                }
            }
            if (otherSlice > 0) {
                newEntries.add(Entry(otherSlice, newEntries.size))
                newLabels.add(context!!.resources.getString(R.string.label_other_slice))
                newColors.add(Color.LTGRAY)
            }
            val dataSet = PieDataSet(newEntries, "")
            dataSet.sliceSpace = SPACE_BETWEEN_SLICES
            dataSet.colors = newColors
            return PieData(newLabels, dataSet)
        }
    }
}