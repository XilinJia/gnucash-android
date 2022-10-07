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
package org.gnucash.android.ui.util

import android.os.Bundle
import android.text.format.Time
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment.OnRecurrenceSetListener

/**
 * Shows the recurrence dialog when the recurrence view is clicked
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class RecurrenceViewClickListener(
    var mActivity: AppCompatActivity, var mRecurrenceRule: String,
    var mRecurrenceSetListener: OnRecurrenceSetListener
) : View.OnClickListener {
    override fun onClick(v: View) {
        val fm = mActivity.supportFragmentManager
        val b = Bundle()
        val t = Time()
        t.setToNow()
        b.putLong(RecurrencePickerDialogFragment.BUNDLE_START_TIME_MILLIS, t.toMillis(false))
        b.putString(RecurrencePickerDialogFragment.BUNDLE_TIME_ZONE, t.timezone)

        // may be more efficient to serialize and pass in EventRecurrence
        b.putString(RecurrencePickerDialogFragment.BUNDLE_RRULE, mRecurrenceRule)
        var rpd = fm.findFragmentByTag(
            FRAGMENT_TAG_RECURRENCE_PICKER
        ) as RecurrencePickerDialogFragment?
        rpd?.dismiss()
        rpd = RecurrencePickerDialogFragment()
        rpd.arguments = b
        rpd.setOnRecurrenceSetListener(mRecurrenceSetListener)
        rpd.show(fm, FRAGMENT_TAG_RECURRENCE_PICKER)
    }

    companion object {
        private const val FRAGMENT_TAG_RECURRENCE_PICKER = "recurrence_picker"
    }
}