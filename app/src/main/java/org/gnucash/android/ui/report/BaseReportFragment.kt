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

import android.content.Context
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import butterknife.BindView
import butterknife.ButterKnife
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.CommoditiesDbAdapter.Companion.instance
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Commodity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.report.ReportsActivity.GroupInterval
import org.joda.time.LocalDateTime
import org.joda.time.Months
import org.joda.time.Years

/**
 * Base class for report fragments.
 *
 * All report fragments should extend this class. At the minimum, reports must implement
 * [.getLayoutResource], [.getReportType], [.generateReport], [.displayReport] and [.getTitle]
 *
 * Implementing classes should create their own XML layouts and provide it in [.getLayoutResource].
 * Then annotate any views in the resource using `@Bind` annotation from ButterKnife library.
 * This base activity will automatically call [ButterKnife.bind] for the layout.
 *
 *
 * Any custom information to be initialized for the report should be done in [.onActivityCreated] in implementing classes.
 * The report is then generated in [.onStart]
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
abstract class BaseReportFragment : Fragment(), OnChartValueSelectedListener, ReportOptionsListener, Refreshable {
    /**
     * Reporting period start time
     */
    protected var mReportPeriodStart: Long = -1

    /**
     * Reporting period end time
     */
    protected var mReportPeriodEnd: Long = -1

    /**
     * Account type for which to display reports
     */
    protected var mAccountType: AccountType? = null

    /**
     * Commodity for which to display reports
     */
    @JvmField
    protected var mCommodity: Commodity? = null

    /**
     * Intervals in which to group reports
     */
    protected var mGroupInterval: GroupInterval? = GroupInterval.MONTH
    protected var mReportsActivity: ReportsActivity? = null

    @JvmField
    @BindView(R.id.selected_chart_slice)
    var mSelectedValueTextView: TextView? = null
    private var mReportGenerator: AsyncTask<Void, Void, Void>? = null

    /**
     * Return the title of this report
     * @return Title string identifier
     */
    @StringRes
    abstract fun getTitle(): Int

    /**
     * Returns the layout resource to use for this report
     * @return Layout resource identifier
     */
    @LayoutRes
    abstract fun getLayoutResource(): Int

    /**
     * Returns what kind of report this is
     * @return Type of report
     */
    abstract fun getReportType(): ReportType

    /**
     * Return `true` if this report fragment requires account type options.
     *
     * Sub-classes should implement this method. The base implementation returns `true`
     * @return `true` if the fragment makes use of account type options, `false` otherwise
     */
    open fun requiresAccountTypeOptions(): Boolean {
        return true
    }

    /**
     * Return `true` if this report fragment requires time range options.
     *
     * Base implementation returns true
     * @return `true` if the report fragment requires time range options, `false` otherwise
     */
    open fun requiresTimeRangeOptions(): Boolean {
        return true
    }

    /**
     * Generates the data for the report
     *
     * This method should not call any methods which modify the UI as it will be run in a background thread
     * <br></br>Put any code to update the UI in [.displayReport]
     *
     */
    protected abstract fun generateReport()

    /**
     * Update the view after the report chart has been generated <br></br>
     * Sub-classes should call to the base method
     */
    protected abstract fun displayReport()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TAG = this.javaClass.simpleName
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(getLayoutResource(), container, false)
        ButterKnife.bind(this, view)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar!!
        actionBar.setTitle(getTitle())
        setHasOptionsMenu(true)
        mCommodity = instance.getCommodity(GnuCashApplication.defaultCurrencyCode!!)
        val reportsActivity = activity as ReportsActivity?
        mReportPeriodStart = reportsActivity!!.reportPeriodStart
        mReportPeriodEnd = reportsActivity.reportPeriodEnd
        mAccountType = reportsActivity.accountType
    }

    override fun onStart() {
        super.onStart()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        mReportsActivity!!.setAppBarColor(getReportType().titleColor)
        mReportsActivity!!.toggleToolbarTitleVisibility()
        toggleBaseReportingOptionsVisibility()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mReportsActivity =
            if (activity !is ReportsActivity) throw RuntimeException("Report fragments can only be used with the ReportsActivity") else activity as ReportsActivity?
    }

    override fun onDetach() {
        super.onDetach()
        if (mReportGenerator != null) mReportGenerator!!.cancel(true)
    }

    private fun toggleBaseReportingOptionsVisibility() {
        val timeRangeLayout = mReportsActivity!!.findViewById<View>(R.id.time_range_layout)
        val dateRangeDivider = mReportsActivity!!.findViewById<View>(R.id.date_range_divider)
        if (timeRangeLayout != null && dateRangeDivider != null) {
            val visibility = if (requiresTimeRangeOptions()) View.VISIBLE else View.GONE
            timeRangeLayout.visibility = visibility
            dateRangeDivider.visibility = visibility
        }
        val accountTypeSpinner = mReportsActivity!!.findViewById<View>(R.id.report_account_type_spinner)
        val visibility = if (requiresAccountTypeOptions()) View.VISIBLE else View.GONE
        accountTypeSpinner.visibility = visibility
    }

    /**
     * Calculates difference between two date values accordingly to `mGroupInterval`
     * @param start start date
     * @param end end date
     * @return difference between two dates or `-1`
     */
    protected fun getDateDiff(start: LocalDateTime, end: LocalDateTime): Int {
        return when (mGroupInterval) {
            GroupInterval.QUARTER -> {
                val y = Years.yearsBetween(
                    start.withDayOfYear(1).withMillisOfDay(0),
                    end.withDayOfYear(1).withMillisOfDay(0)
                ).years
                getQuarter(end) - getQuarter(start) + y * 4
            }

            GroupInterval.MONTH -> Months.monthsBetween(
                start.withDayOfMonth(1).withMillisOfDay(0),
                end.withDayOfMonth(1).withMillisOfDay(0)
            ).months

            GroupInterval.YEAR -> Years.yearsBetween(
                start.withDayOfYear(1).withMillisOfDay(0),
                end.withDayOfYear(1).withMillisOfDay(0)
            ).years

            else -> -1
        }
    }

    /**
     * Returns a quarter of the specified date
     * @param date date
     * @return a quarter
     */
    protected fun getQuarter(date: LocalDateTime): Int {
        return (date.monthOfYear - 1) / 3 + 1
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.chart_actions, menu)
    }

    override fun refresh() {
        if (mReportGenerator != null) mReportGenerator!!.cancel(true)
        mReportGenerator = object : AsyncTask<Void, Void, Void>() {
            override fun onPreExecute() {
                mReportsActivity!!.progressBar!!.visibility = View.VISIBLE
            }

            protected override fun doInBackground(vararg params: Void): Void? {
                generateReport()
                return null
            }

            override fun onPostExecute(aVoid: Void?) {
                displayReport()
                mReportsActivity!!.progressBar!!.visibility = View.GONE
            }
        }
        mReportGenerator!!.execute()
    }

    /**
     * Charts do not support account specific refreshes in general.
     * So we provide a base implementation which just calls [.refresh]
     *
     * @param uid GUID of relevant item to be refreshed
     */
    override fun refresh(uid: String?) {
        refresh()
    }

    override fun onGroupingUpdated(groupInterval: GroupInterval?) {
        if (mGroupInterval != groupInterval) {
            mGroupInterval = groupInterval
            refresh()
        }
    }

    override fun onTimeRangeUpdated(start: Long, end: Long) {
        if (mReportPeriodStart != start || mReportPeriodEnd != end) {
            mReportPeriodStart = start
            mReportPeriodEnd = end
            refresh()
        }
    }

    override fun onAccountTypeUpdated(accountType: AccountType?) {
        if (mAccountType !== accountType) {
            mAccountType = accountType
            refresh()
        }
    }

    override fun onValueSelected(e: Entry, dataSetIndex: Int, h: Highlight) {
        //nothing to see here, move along
    }

    override fun onNothingSelected() {
        if (mSelectedValueTextView != null) mSelectedValueTextView!!.setText(R.string.select_chart_to_view_details)
    }

    companion object {
        /**
         * Color for chart with no data
         */
        const val NO_DATA_COLOR = Color.LTGRAY
        @JvmStatic
        protected var TAG = "BaseReportFragment"

        /**
         * Pattern to use to display selected chart values
         */
        const val SELECTED_VALUE_PATTERN = "%s - %.2f (%.2f %%)"
    }
}