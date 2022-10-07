/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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

import android.text.format.Time
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Recurrence
import java.sql.Timestamp

/**
 * Parses [EventRecurrence]s to generate
 * [org.gnucash.android.model.ScheduledAction]s
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
object RecurrenceParser {
    //these are time millisecond constants which are used for scheduled actions.
    //they may not be calendar accurate, but they serve the purpose for scheduling approximate time for background service execution
    const val SECOND_MILLIS: Long = 1000
    const val MINUTE_MILLIS = 60 * SECOND_MILLIS
    const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    const val DAY_MILLIS = 24 * HOUR_MILLIS
    const val WEEK_MILLIS = 7 * DAY_MILLIS
    const val MONTH_MILLIS = 30 * DAY_MILLIS
    const val YEAR_MILLIS = 12 * MONTH_MILLIS

    /**
     * Parse an [EventRecurrence] into a [Recurrence] object
     * @param eventRecurrence EventRecurrence object
     * @return Recurrence object
     */
    @JvmStatic
    fun parse(eventRecurrence: EventRecurrence?): Recurrence? {
        if (eventRecurrence == null) return null
        val periodType: PeriodType = when (eventRecurrence.freq) {
            EventRecurrence.HOURLY -> PeriodType.HOUR
            EventRecurrence.DAILY -> PeriodType.DAY
            EventRecurrence.WEEKLY -> PeriodType.WEEK
            EventRecurrence.MONTHLY -> PeriodType.MONTH
            EventRecurrence.YEARLY -> PeriodType.YEAR
            else -> PeriodType.MONTH
        }
        val interval =
            if (eventRecurrence.interval == 0) 1 else eventRecurrence.interval //bug from betterpickers library sometimes returns 0 as the interval
        val recurrence = Recurrence(periodType)
        recurrence.mMultiplier = interval
        parseEndTime(eventRecurrence, recurrence)
        recurrence.byDays(parseByDay(eventRecurrence.byday))
        if (eventRecurrence.startDate != null) recurrence.mPeriodStart =
            Timestamp(eventRecurrence.startDate.toMillis(false))
        return recurrence
    }

    /**
     * Parses the end time from an EventRecurrence object and sets it to the `scheduledEvent`.
     * The end time is specified in the dialog either by number of occurrences or a date.
     * @param eventRecurrence Event recurrence pattern obtained from dialog
     * @param recurrence Recurrence event to set the end period to
     */
    private fun parseEndTime(eventRecurrence: EventRecurrence, recurrence: Recurrence) {
        if (eventRecurrence.until != null && eventRecurrence.until.isNotEmpty()) {
            val endTime = Time()
            endTime.parse(eventRecurrence.until)
            recurrence.setMPeriodEnd(Timestamp(endTime.toMillis(false)))
        } else if (eventRecurrence.count > 0) {
            recurrence.setMPeriodEnd(eventRecurrence.count)
        }
    }

    /**
     * Parses an array of byDay values to return a list of days of week
     * constants from [Calendar].
     *
     *
     * Currently only supports byDay values for weeks.
     *
     * @param byDay Array of byDay values
     * @return list of days of week constants from Calendar.
     */
    private fun parseByDay(byDay: IntArray?): List<Int> {
        if (byDay == null) {
            return emptyList()
        }
        val byDaysList: MutableList<Int> = ArrayList(byDay.size)
        for (day in byDay) {
            byDaysList.add(EventRecurrence.day2CalendarDay(day))
        }
        return byDaysList
    }
}