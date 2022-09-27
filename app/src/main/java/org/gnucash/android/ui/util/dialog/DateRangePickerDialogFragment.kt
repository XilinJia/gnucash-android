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
package org.gnucash.android.ui.util.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import butterknife.BindView
import butterknife.ButterKnife
import com.squareup.timessquare.CalendarPickerView
import org.gnucash.android.R
import org.joda.time.LocalDate
import java.util.*

/**
 * Dialog for picking date ranges in terms of months.
 * It is currently used for selecting ranges for reports
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class DateRangePickerDialogFragment : DialogFragment() {
    @JvmField
    @BindView(R.id.calendar_view)
    var mCalendarPickerView: CalendarPickerView? = null

    @JvmField
    @BindView(R.id.btn_save)
    var mDoneButton: Button? = null

    @JvmField
    @BindView(R.id.btn_cancel)
    var mCancelButton: Button? = null
    private var mStartRange = LocalDate.now().minusMonths(1).toDate()
    private var mEndRange = LocalDate.now().toDate()
    private var mDateRangeSetListener: OnDateRangeSetListener? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_date_range_picker, container, false)
        ButterKnife.bind(this, view)
        val nextYear = Calendar.getInstance()
        nextYear.add(Calendar.YEAR, 1)
        val today = Date()
        mCalendarPickerView!!.init(mStartRange, mEndRange)
            .inMode(CalendarPickerView.SelectionMode.RANGE)
            .withSelectedDate(today)
        mDoneButton!!.setText(R.string.done_label)
        mDoneButton!!.setOnClickListener {
            val selectedDates = mCalendarPickerView!!.selectedDates
            val startDate = selectedDates[0]
            // If only one day is selected (no interval) start and end should be the same (the selected one)
            val endDate = if (selectedDates.size > 1) selectedDates[selectedDates.size - 1] else Date(startDate.time)
            // CaledarPicker returns the start of the selected day but we want all transactions of that day to be included.
            // Therefore we have to add 24 hours to the endDate.
            endDate.time = endDate.time + ONE_DAY_IN_MILLIS
            mDateRangeSetListener!!.onDateRangeSet(startDate, endDate)
            dismiss()
        }
        mCancelButton!!.setOnClickListener { dismiss() }
        return view
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setTitle("Pick time range")
        return dialog
    }

    interface OnDateRangeSetListener {
        fun onDateRangeSet(startDate: Date, endDate: Date)
    }

    companion object {
        private const val ONE_DAY_IN_MILLIS = (24 * 60 * 60 * 1000).toLong()
        fun newInstance(dateRangeSetListener: OnDateRangeSetListener?): DateRangePickerDialogFragment {
            val fragment = DateRangePickerDialogFragment()
            fragment.mDateRangeSetListener = dateRangeSetListener
            return fragment
        }

        fun newInstance(
            startDate: Long, endDate: Long,
            dateRangeSetListener: OnDateRangeSetListener?
        ): DateRangePickerDialogFragment {
            val fragment = DateRangePickerDialogFragment()
            fragment.mStartRange = Date(startDate)
            fragment.mEndRange = Date(endDate)
            fragment.mDateRangeSetListener = dateRangeSetListener
            return fragment
        }
    }
}