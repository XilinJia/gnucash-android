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

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.DatePicker
import android.widget.Spinner
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import butterknife.BindView
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter.Companion.instance
import org.gnucash.android.model.AccountType
import org.gnucash.android.ui.common.BaseDrawerActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.util.dialog.DateRangePickerDialogFragment
import org.gnucash.android.ui.util.dialog.DateRangePickerDialogFragment.OnDateRangeSetListener
import org.joda.time.LocalDate
import java.util.*

/**
 * Activity for displaying report fragments (which must implement [BaseReportFragment])
 *
 * In order to add new reports, extend the [BaseReportFragment] class to provide the view
 * for the report. Then add the report mapping in [ReportType] constructor depending on what
 * kind of report it is. The report will be dynamically included at runtime.
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class ReportsActivity : BaseDrawerActivity(), OnItemSelectedListener, DatePickerDialog.OnDateSetListener,
    OnDateRangeSetListener, Refreshable {
    @JvmField
    @BindView(R.id.time_range_spinner)
    var mTimeRangeSpinner: Spinner? = null

    @JvmField
    @BindView(R.id.report_account_type_spinner)
    var mAccountTypeSpinner: Spinner? = null

    @JvmField
    @BindView(R.id.toolbar_spinner)
    var mReportTypeSpinner: Spinner? = null
    private var mTransactionsDbAdapter: TransactionsDbAdapter? = null
    var accountType = AccountType.EXPENSE
        private set
    private var mReportType: ReportType? = ReportType.NONE
    private var mReportsOverviewFragment: ReportsOverviewFragment? = null

    enum class GroupInterval {
        WEEK, MONTH, QUARTER, YEAR, ALL
    }

    /**
     * Return the start time of the reporting period
     * @return Time in millis
     */
    // default time range is the last 3 months
    var reportPeriodStart = LocalDate().minusMonths(2).dayOfMonth().withMinimumValue().toDate().time
        private set

    /**
     * Return the end time of the reporting period
     * @return Time in millis
     */
    var reportPeriodEnd = LocalDate().plusDays(1).toDate().time
        private set
    private var mReportGroupInterval = GroupInterval.MONTH
    private var mSkipNextReportTypeSelectedRun = false
    var mReportTypeSelectedListener: OnItemSelectedListener = object : OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
            if (mSkipNextReportTypeSelectedRun) {
                mSkipNextReportTypeSelectedRun = false
            } else {
                val reportName = parent.getItemAtPosition(position).toString()
                loadFragment(mReportType!!.getFragment(reportName))
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            //nothing to see here, move along
        }
    }
    override val contentView: Int
        get() = R.layout.activity_reports
    override val titleRes: Int
        get() = R.string.title_reports

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            mReportType = savedInstanceState.getSerializable(STATE_REPORT_TYPE) as ReportType?
        }
        super.onCreate(savedInstanceState)
        mTransactionsDbAdapter = instance
        val adapter = ArrayAdapter.createFromResource(
            this, R.array.report_time_range,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mTimeRangeSpinner!!.adapter = adapter
        mTimeRangeSpinner!!.onItemSelectedListener = this
        mTimeRangeSpinner!!.setSelection(1)
        val dataAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.report_account_types, android.R.layout.simple_spinner_item
        )
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mAccountTypeSpinner!!.adapter = dataAdapter
        mAccountTypeSpinner!!.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, position: Int, id: Long) {
                accountType = when (position) {
                    0 -> AccountType.EXPENSE
                    1 -> AccountType.INCOME
                    else -> AccountType.EXPENSE
                }
                updateAccountTypeOnFragments()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                //nothing to see here, move along
            }
        }
        mReportsOverviewFragment = ReportsOverviewFragment()
        if (savedInstanceState == null) {
            loadFragment(mReportsOverviewFragment)
        }
    }

    override fun onAttachFragment(fragment: Fragment) {
        super.onAttachFragment(fragment)
        if (fragment is BaseReportFragment) {
            updateReportTypeSpinner(fragment.getReportType(), getString(fragment.getTitle()))
        }
    }

    /**
     * Load the provided fragment into the view replacing the previous one
     * @param fragment BaseReportFragment instance
     */
    private fun loadFragment(fragment: BaseReportFragment?) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager
            .beginTransaction()
        fragmentTransaction.replace(R.id.fragment_container, fragment!!)
        fragmentTransaction.commit()
    }

    /**
     * Update the report type spinner
     */
    fun updateReportTypeSpinner(reportType: ReportType, reportName: String?) {
        if (reportType == mReportType) //if it is the same report type, don't change anything
            return
        mReportType = reportType
        val actionBar = supportActionBar!!
        val arrayAdapter = ArrayAdapter(
            actionBar.themedContext,
            android.R.layout.simple_list_item_1,
            mReportType!!.reportNames
        )
        mSkipNextReportTypeSelectedRun = true //selection event will be fired again
        mReportTypeSpinner!!.adapter = arrayAdapter
        mReportTypeSpinner!!.setSelection(arrayAdapter.getPosition(reportName))
        mReportTypeSpinner!!.onItemSelectedListener = mReportTypeSelectedListener
        toggleToolbarTitleVisibility()
    }

    fun toggleToolbarTitleVisibility() {
        val actionBar = supportActionBar!!
        if (mReportType == ReportType.NONE) {
            mReportTypeSpinner!!.visibility = View.GONE
        } else {
            mReportTypeSpinner!!.visibility = View.VISIBLE
        }
        actionBar.setDisplayShowTitleEnabled(mReportType == ReportType.NONE)
    }

    /**
     * Sets the color Action Bar and Status bar (where applicable)
     */
    fun setAppBarColor(color: Int) {
        val resolvedColor = ContextCompat.getColor(this, color)
        if (supportActionBar != null) supportActionBar!!.setBackgroundDrawable(ColorDrawable(resolvedColor))
        if (Build.VERSION.SDK_INT > 20) window.statusBarColor = GnuCashApplication.darken(resolvedColor)
    }

    /**
     * Updates the reporting time range for all listening fragments
     */
    private fun updateDateRangeOnFragment() {
        val fragments = supportFragmentManager.fragments
        for (fragment in fragments) {
            if (fragment is ReportOptionsListener) {
                (fragment as ReportOptionsListener).onTimeRangeUpdated(reportPeriodStart, reportPeriodEnd)
            }
        }
    }

    /**
     * Updates the account type for all attached fragments which are listening
     */
    private fun updateAccountTypeOnFragments() {
        val fragments = supportFragmentManager.fragments
        for (fragment in fragments) {
            if (fragment is ReportOptionsListener) {
                (fragment as ReportOptionsListener).onAccountTypeUpdated(accountType)
            }
        }
    }

    /**
     * Updates the report grouping interval on all attached fragments which are listening
     */
    private fun updateGroupingOnFragments() {
        val fragments = supportFragmentManager.fragments
        for (fragment in fragments) {
            if (fragment is ReportOptionsListener) {
                (fragment as ReportOptionsListener).onGroupingUpdated(mReportGroupInterval)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.report_actions, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_group_reports_by -> true
            R.id.group_by_month -> {
                item.isChecked = true
                mReportGroupInterval = GroupInterval.MONTH
                updateGroupingOnFragments()
                true
            }

            R.id.group_by_quarter -> {
                item.isChecked = true
                mReportGroupInterval = GroupInterval.QUARTER
                updateGroupingOnFragments()
                true
            }

            R.id.group_by_year -> {
                item.isChecked = true
                mReportGroupInterval = GroupInterval.YEAR
                updateGroupingOnFragments()
                true
            }

            android.R.id.home -> {
                super.onOptionsItemSelected(item)
                false
            }

            else -> false
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        reportPeriodEnd = LocalDate().plusDays(1).toDate().time
        when (position) {
            0 -> reportPeriodStart = LocalDate().dayOfMonth().withMinimumValue().toDate().time
            1 -> reportPeriodStart = LocalDate().minusMonths(2).dayOfMonth().withMinimumValue().toDate().time
            2 -> reportPeriodStart = LocalDate().minusMonths(5).dayOfMonth().withMinimumValue().toDate().time
            3 -> reportPeriodStart = LocalDate().minusMonths(11).dayOfMonth().withMinimumValue().toDate().time
            4 -> {
                reportPeriodStart = -1
                reportPeriodEnd = -1
            }

            5 -> {
                val mCurrencyCode = GnuCashApplication.defaultCurrencyCode
                val earliestTransactionTime = mTransactionsDbAdapter!!.getTimestampOfEarliestTransaction(
                    accountType, mCurrencyCode!!)
                val rangeFragment: DialogFragment = DateRangePickerDialogFragment.newInstance(
                    earliestTransactionTime,
                    LocalDate().plusDays(1).toDate().time,
                    this
                )
                rangeFragment.show(supportFragmentManager, "range_dialog")
            }
        }
        if (position != 5) { //the date picker will trigger the update itself
            updateDateRangeOnFragment()
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        //nothing to see here, move along
    }

    override fun onDateSet(view: DatePicker, year: Int, monthOfYear: Int, dayOfMonth: Int) {
        val calendar = Calendar.getInstance()
        calendar[year, monthOfYear] = dayOfMonth
        reportPeriodStart = calendar.timeInMillis
        updateDateRangeOnFragment()
    }

    override fun onDateRangeSet(startDate: Date, endDate: Date) {
        reportPeriodStart = startDate.time
        reportPeriodEnd = endDate.time
        updateDateRangeOnFragment()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mReportType != ReportType.NONE) {
                loadFragment(mReportsOverviewFragment)
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun refresh() {
        val fragments = supportFragmentManager.fragments
        for (fragment in fragments) {
            if (fragment is Refreshable) {
                (fragment as Refreshable).refresh()
            }
        }
    }

    /**
     * Just another call to refresh
     */
    override fun refresh(uid: String?) {
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(STATE_REPORT_TYPE, mReportType)
    }

    companion object {
        @JvmField
        val COLORS = intArrayOf(
            Color.parseColor("#17ee4e"), Color.parseColor("#cc1f09"), Color.parseColor("#3940f7"),
            Color.parseColor("#f9cd04"), Color.parseColor("#5f33a8"), Color.parseColor("#e005b6"),
            Color.parseColor("#17d6ed"), Color.parseColor("#e4a9a2"), Color.parseColor("#8fe6cd"),
            Color.parseColor("#8b48fb"), Color.parseColor("#343a36"), Color.parseColor("#6decb1"),
            Color.parseColor("#f0f8ff"), Color.parseColor("#5c3378"), Color.parseColor("#a6dcfd"),
            Color.parseColor("#ba037c"), Color.parseColor("#708809"), Color.parseColor("#32072c"),
            Color.parseColor("#fddef8"), Color.parseColor("#fa0e6e"), Color.parseColor("#d9e7b5")
        )
        private const val STATE_REPORT_TYPE = "STATE_REPORT_TYPE"
    }
}