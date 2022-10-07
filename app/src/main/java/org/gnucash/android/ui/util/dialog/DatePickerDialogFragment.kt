/*
 * Copyright (c) 2012 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.util.dialog

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import java.util.*

/**
 * Fragment for displaying a date picker dialog
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class DatePickerDialogFragment
/**
 * Default Constructor
 * Is required for when the device is rotated while the dialog is open.
 * If this constructor is not present, the app will crash
 */
    : DialogFragment() {
    /**
     * Listener to notify of events in the dialog
     */
    private var mDateSetListener: DatePickerDialog.OnDateSetListener? = null

    /**
     * Date selected in the dialog or to which the dialog is initialized
     */
    private var mDate: Calendar? = null

    /**
     * Creates and returns an Android [DatePickerDialog]
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val cal = if (mDate == null) Calendar.getInstance() else mDate!!
        return DatePickerDialog(
            activity!!,
            mDateSetListener, cal[Calendar.YEAR],
            cal[Calendar.MONTH], cal[Calendar.DAY_OF_MONTH]
        )
    }

    companion object {
        /**
         * Overloaded constructor
         * @param callback Listener to notify when the date is set and the dialog is closed
         * @param dateMillis Time in milliseconds to which to initialize the dialog
         */
        fun newInstance(callback: DatePickerDialog.OnDateSetListener?, dateMillis: Long): DatePickerDialogFragment {
            val datePickerDialogFragment = DatePickerDialogFragment()
            datePickerDialogFragment.mDateSetListener = callback
            if (dateMillis > 0) {
                datePickerDialogFragment.mDate = GregorianCalendar()
                datePickerDialogFragment.mDate!!.timeInMillis = dateMillis
            }
            return datePickerDialogFragment
        }
    }
}