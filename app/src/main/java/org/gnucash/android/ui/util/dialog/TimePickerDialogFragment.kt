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

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import java.util.*

/**
 * Fragment for displaying a time choose dialog
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class TimePickerDialogFragment
/**
 * Default constructor
 * Is required for when the device is rotated while the dialog is open.
 * If this constructor is not present, the app will crash
 */
    : DialogFragment() {
    /**
     * Listener to notify when the time is set
     */
    private var mListener: TimePickerDialog.OnTimeSetListener? = null

    /**
     * Current time to initialize the dialog to, or to notify the listener of.
     */
    var mCurrentTime: Calendar? = null

    /**
     * Creates and returns an Android [TimePickerDialog]
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val cal = if (mCurrentTime == null) Calendar.getInstance() else mCurrentTime!!
        val hour = cal[Calendar.HOUR_OF_DAY]
        val minute = cal[Calendar.MINUTE]
        return TimePickerDialog(
            activity,
            mListener,
            hour,
            minute,
            true
        )
    }

    companion object {
        /**
         * Overloaded constructor
         * @param listener [OnTimeSetListener] to notify when the time has been set
         * @param timeMillis Time in milliseconds to initialize the dialog to
         */
        fun newInstance(listener: TimePickerDialog.OnTimeSetListener?, timeMillis: Long): TimePickerDialogFragment {
            val timePickerDialogFragment = TimePickerDialogFragment()
            timePickerDialogFragment.mListener = listener
            if (timeMillis > 0) {
                timePickerDialogFragment.mCurrentTime = GregorianCalendar()
                timePickerDialogFragment.mCurrentTime!!.timeInMillis = timeMillis
            }
            return timePickerDialogFragment
        }
    }
}