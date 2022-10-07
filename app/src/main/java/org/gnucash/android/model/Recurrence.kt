/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (C) 2020 Xilin Jia https://github.com/XilinJia
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
package org.gnucash.android.model

import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.ui.util.RecurrenceParser
import org.joda.time.*
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Model for recurrences in the database
 *
 * Basically a wrapper around [PeriodType]
 * @author Xilin Jia <https://github.com/XilinJia> []Kotlin code created (Copyright (C) 2022)]
 */
class Recurrence(periodType: PeriodType) : BaseModel() {
    /**
     * Return the PeriodType for this recurrence
     * @return PeriodType for the recurrence
     */
    /**
     * Sets the period type for the recurrence
     * @param periodType PeriodType
     */
    var mPeriodType: PeriodType? = null
    /**
     * Return the start time for this recurrence
     * @return Timestamp of start of recurrence
     */
    /**
     * Set the start time of this recurrence
     * @param periodStart [Timestamp] of recurrence
     */
    /**
     * Start time of the recurrence
     */
    var mPeriodStart: Timestamp
    /**
     * Return the end date of the period in milliseconds
     * @return End date of the recurrence period
     */
    /**
     * End time of this recurrence
     *
     * This value is not persisted to the database
     */
    var mPeriodEnd: Timestamp? = null
        private set

    /**
     * Days of week on which to run the recurrence
     */
    private var mByDays = emptyList<Int>()
    /**
     * Returns the multiplier for the period type. The default multiplier is 1.
     * e.g. bi-weekly actions have period type [PeriodType.WEEK] and multiplier 2.
     *
     * @return  Multiplier for the period type
     */
    /**
     * Sets the multiplier for the period type.
     * e.g. bi-weekly actions have period type [PeriodType.WEEK] and multiplier 2.
     *
     * @param multiplier Multiplier for the period type
     */
    var mMultiplier = 1 //multiplier for the period type

    init {
        mPeriodType = periodType
        mPeriodStart = Timestamp(System.currentTimeMillis())
    }

    /**
     * Returns an approximate period for this recurrence
     *
     * The period is approximate because months do not all have the same number of days,
     * but that is assumed
     * @return Milliseconds since Epoch representing the period
     */
    @Deprecated("Do not use in new code. Uses fixed period values for months and years (which have variable units of time)")
    fun period(): Long {
        var baseMillis: Long = 0
        when (mPeriodType) {
            PeriodType.HOUR -> baseMillis = RecurrenceParser.HOUR_MILLIS
            PeriodType.DAY -> baseMillis = RecurrenceParser.DAY_MILLIS
            PeriodType.WEEK -> baseMillis = RecurrenceParser.WEEK_MILLIS
            PeriodType.MONTH -> baseMillis = RecurrenceParser.MONTH_MILLIS
            PeriodType.YEAR -> baseMillis = RecurrenceParser.YEAR_MILLIS
            else -> {}
        }
        return mMultiplier * baseMillis
    }

    /**
     * Returns the event schedule (start, end and recurrence)
     * @return String description of repeat schedule
     */
    fun repeatString(): String {
        val repeatBuilder = StringBuilder(frequencyRepeatString())
        val context = GnuCashApplication.appContext
        val dayOfWeek = SimpleDateFormat("EEEE", GnuCashApplication.defaultLocale)
            .format(Date(mPeriodStart.time))
        if (mPeriodType === PeriodType.WEEK) {
            repeatBuilder.append(" ").append(context?.getString(R.string.repeat_on_weekday, dayOfWeek))
        }
        if (mPeriodEnd != null) {
            val endDateString = SimpleDateFormat.getDateInstance().format(
                Date(
                    mPeriodEnd!!.time
                )
            )
            repeatBuilder.append(", ").append(context?.getString(R.string.repeat_until_date, endDateString))
        }
        return repeatBuilder.toString()
    }

    /**
     * Creates an RFC 2445 string which describes this recurring event.
     *
     * See http://recurrance.sourceforge.net/
     *
     * The output of this method is not meant for human consumption
     * @return String describing event
     */
    fun ruleString(): String {
        val separator = ";"
        val ruleBuilder = StringBuilder()

//        =======================================================================
        //This section complies with the formal rules, but the betterpickers library doesn't like/need it

//        SimpleDateFormat startDateFormat = new SimpleDateFormat("'TZID'=zzzz':'yyyyMMdd'T'HHmmss", Locale.US);
//        ruleBuilder.append("DTSTART;");
//        ruleBuilder.append(startDateFormat.format(new Date(mStartDate)));
//            ruleBuilder.append("\n");
//        ruleBuilder.append("RRULE:");
//        ========================================================================
        ruleBuilder.append("FREQ=").append(mPeriodType!!.frequencyDescription()).append(separator)
        ruleBuilder.append("INTERVAL=").append(mMultiplier).append(separator)
        if (count > 0) ruleBuilder.append("COUNT=").append(count).append(separator)
        ruleBuilder.append(mPeriodType!!.byParts(mPeriodStart.time)).append(separator)
        return ruleBuilder.toString()
    }

    /**
     * Return the number of days left in this period
     * @return Number of days left in period
     */
    fun daysLeftInCurrentPeriod(): Int {
        val startDate = LocalDateTime(System.currentTimeMillis())
        val interval = mMultiplier - 1
        var endDate: LocalDateTime? = null
        when (mPeriodType) {
            PeriodType.HOUR -> endDate = LocalDateTime(System.currentTimeMillis()).plusHours(interval)
            PeriodType.DAY -> endDate = LocalDateTime(System.currentTimeMillis()).plusDays(interval)
            PeriodType.WEEK -> endDate = startDate.dayOfWeek().withMaximumValue().plusWeeks(interval)
            PeriodType.MONTH -> endDate = startDate.dayOfMonth().withMaximumValue().plusMonths(interval)
            PeriodType.YEAR -> endDate = startDate.dayOfYear().withMaximumValue().plusYears(interval)
            else -> {}
        }
        return Days.daysBetween(startDate, endDate).days
    }

    /**
     * Returns the number of periods from the start date of this recurrence until the end of the
     * interval multiplier specified in the [PeriodType]
     * //fixme: Improve the documentation
     * @return Number of periods in this recurrence
     */
    fun numberOfPeriods(numberOfPeriods: Int): Int {
        val startDate = LocalDateTime(mPeriodStart.time)
        val endDate: LocalDateTime
        val interval = mMultiplier
        when (mPeriodType) {
            PeriodType.HOUR -> {
                endDate = startDate.plusHours(numberOfPeriods)
                return Hours.hoursBetween(startDate, endDate).hours
            }

            PeriodType.DAY -> {
                endDate = startDate.plusDays(numberOfPeriods)
                return Days.daysBetween(startDate, endDate).days
            }

            PeriodType.WEEK -> {
                endDate = startDate.dayOfWeek().withMaximumValue().plusWeeks(numberOfPeriods)
                return Weeks.weeksBetween(startDate, endDate).weeks / interval
            }

            PeriodType.MONTH -> {
                endDate = startDate.dayOfMonth().withMaximumValue().plusMonths(numberOfPeriods)
                return Months.monthsBetween(startDate, endDate).months / interval
            }

            PeriodType.YEAR -> {
                endDate = startDate.dayOfYear().withMaximumValue().plusYears(numberOfPeriods)
                return Years.yearsBetween(startDate, endDate).years / interval
            }

            else -> {}
        }
        return 0
    }

    /**
     * Return the name of the current period
     * @return String of current period
     */
    fun textOfCurrentPeriod(periodNum: Int): String {
        val startDate = LocalDate(mPeriodStart.time)
        when (mPeriodType) {
            PeriodType.HOUR -> {}
            PeriodType.DAY -> return startDate.dayOfWeek().asText
            PeriodType.WEEK -> return startDate.weekOfWeekyear().asText
            PeriodType.MONTH -> return startDate.monthOfYear().asText
            PeriodType.YEAR -> return startDate.year().asText
            else -> {}
        }
        return "Period $periodNum"
    }

    /**
     * Return the days of week on which to run the recurrence.
     *
     *
     * Days are expressed as defined in [java.util.Calendar].
     * For example, Calendar.MONDAY
     *
     * @return list of days of week on which to run the recurrence.
     */
    fun byDays(): List<Int> {
        return Collections.unmodifiableList(mByDays)
    }

    /**
     * Sets the days on which to run the recurrence.
     *
     *
     * Days must be expressed as defined in [java.util.Calendar].
     * For example, Calendar.MONDAY
     *
     * @param byDays list of days of week on which to run the recurrence.
     */
    fun byDays(byDays: List<Int>) {
        mByDays = ArrayList(byDays)
    }/*
        //this solution does not use looping, but is not very accurate

        int multiplier = mMultiplier;
        LocalDateTime startDate = new LocalDateTime(mPeriodStart.getTime());
        LocalDateTime endDate = new LocalDateTime(mPeriodEnd.getTime());
        switch (mPeriodType){
            case DAY:
                return Days.daysBetween(startDate, endDate).dividedBy(multiplier).getDays();
            case WEEK:
                return Weeks.weeksBetween(startDate, endDate).dividedBy(multiplier).getWeeks();
            case MONTH:
                return Months.monthsBetween(startDate, endDate).dividedBy(multiplier).getMonths();
            case YEAR:
                return Years.yearsBetween(startDate, endDate).dividedBy(multiplier).getYears();
            default:
                return -1;
        }
*/

    /**
     * Computes the number of occurrences of this recurrences between start and end date
     *
     * If there is no end date or the PeriodType is unknown, it returns -1
     * @return Number of occurrences, or -1 if there is no end date
     */
    val count: Int
        get() {
            if (mPeriodEnd == null) return -1
            val multiple = mMultiplier
            val jodaPeriod: ReadablePeriod = when (mPeriodType) {
                PeriodType.HOUR -> Hours.hours(multiple)
                PeriodType.DAY -> Days.days(multiple)
                PeriodType.WEEK -> Weeks.weeks(multiple)
                PeriodType.MONTH -> Months.months(multiple)
                PeriodType.YEAR -> Years.years(multiple)
                else -> Months.months(multiple)
            }
            var count = 0
            var startTime = LocalDateTime(mPeriodStart.time)
            while (startTime.toDateTime().millis < mPeriodEnd!!.time) {
                ++count
                startTime = startTime.plus(jodaPeriod)
            }
            return count

            /*
        //this solution does not use looping, but is not very accurate

        int multiplier = mMultiplier;
        LocalDateTime startDate = new LocalDateTime(mPeriodStart.getTime());
        LocalDateTime endDate = new LocalDateTime(mPeriodEnd.getTime());
        switch (mPeriodType){
            case DAY:
                return Days.daysBetween(startDate, endDate).dividedBy(multiplier).getDays();
            case WEEK:
                return Weeks.weeksBetween(startDate, endDate).dividedBy(multiplier).getWeeks();
            case MONTH:
                return Months.monthsBetween(startDate, endDate).dividedBy(multiplier).getMonths();
            case YEAR:
                return Years.yearsBetween(startDate, endDate).dividedBy(multiplier).getYears();
            default:
                return -1;
        }
*/
        }

    /**
     * Sets the end time of this recurrence by specifying the number of occurences
     * @param numberOfOccurences Number of occurences from the start time
     */
    fun setMPeriodEnd(numberOfOccurences: Int) {
        val localDate = LocalDateTime(mPeriodStart.time)
        val endDate: LocalDateTime
        val occurrenceDuration = numberOfOccurences * mMultiplier
        endDate = when (mPeriodType) {
            PeriodType.HOUR -> localDate.plusHours(occurrenceDuration)
            PeriodType.DAY -> localDate.plusDays(occurrenceDuration)
            PeriodType.WEEK -> localDate.plusWeeks(occurrenceDuration)
            PeriodType.MONTH -> localDate.plusMonths(occurrenceDuration)
            PeriodType.YEAR -> localDate.plusYears(occurrenceDuration)
            else -> localDate.plusMonths(occurrenceDuration)
        }
        mPeriodEnd = Timestamp(endDate.toDateTime().millis)
    }

    /**
     * Set period end date
     * @param endTimestamp End time in milliseconds
     */
    fun setMPeriodEnd(endTimestamp: Timestamp?) {
        mPeriodEnd = endTimestamp
    }

    /**
     * Returns a localized string describing the period type's frequency.
     *
     * @return String describing the period type
     */
    private fun frequencyRepeatString(): String {
        val res = GnuCashApplication.appContext!!.resources
        return when (mPeriodType) {
            PeriodType.HOUR -> res.getQuantityString(
                R.plurals.label_every_x_hours,
                mMultiplier,
                mMultiplier
            )

            PeriodType.DAY -> res.getQuantityString(
                R.plurals.label_every_x_days,
                mMultiplier,
                mMultiplier
            )

            PeriodType.WEEK -> res.getQuantityString(
                R.plurals.label_every_x_weeks,
                mMultiplier,
                mMultiplier
            )

            PeriodType.MONTH -> res.getQuantityString(
                R.plurals.label_every_x_months,
                mMultiplier,
                mMultiplier
            )

            PeriodType.YEAR -> res.getQuantityString(
                R.plurals.label_every_x_years,
                mMultiplier,
                mMultiplier
            )

            else -> ""
        }
    }

    companion object {
        /**
         * Returns a new [Recurrence] with the [PeriodType] specified in the old format.
         *
         * @param period Period in milliseconds since Epoch (old format to define a period)
         * @return Recurrence with the specified period.
         */
        @JvmStatic
        fun fromLegacyPeriod(period: Long): Recurrence {
            var result = (period / RecurrenceParser.YEAR_MILLIS).toInt()
            if (result > 0) {
                val recurrence = Recurrence(PeriodType.YEAR)
                recurrence.mMultiplier = result
                return recurrence
            }
            result = (period / RecurrenceParser.MONTH_MILLIS).toInt()
            if (result > 0) {
                val recurrence = Recurrence(PeriodType.MONTH)
                recurrence.mMultiplier = result
                return recurrence
            }
            result = (period / RecurrenceParser.WEEK_MILLIS).toInt()
            if (result > 0) {
                val recurrence = Recurrence(PeriodType.WEEK)
                recurrence.mMultiplier = result
                return recurrence
            }
            result = (period / RecurrenceParser.DAY_MILLIS).toInt()
            if (result > 0) {
                val recurrence = Recurrence(PeriodType.DAY)
                recurrence.mMultiplier = result
                return recurrence
            }
            result = (period / RecurrenceParser.HOUR_MILLIS).toInt()
            if (result > 0) {
                val recurrence = Recurrence(PeriodType.HOUR)
                recurrence.mMultiplier = result
                return recurrence
            }
            return Recurrence(PeriodType.DAY)
        }
    }
}