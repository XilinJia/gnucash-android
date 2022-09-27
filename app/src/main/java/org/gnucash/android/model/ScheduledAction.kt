/*
 * Copyright (c) 2014 - 2015 Ngewi Fet <ngewif@gmail.com>
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
import org.gnucash.android.model.Recurrence.Companion.fromLegacyPeriod
import org.joda.time.LocalDateTime
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Represents a scheduled event which is stored in the database and run at regular mPeriod
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> []Kotlin code created (Copyright (C) 2022)]
 */
class ScheduledAction    //all actions are enabled by default
    (
    /**
     * Type of event being scheduled
     */
    var mActionType: ActionType
) : BaseModel() {
    private var mStartDate: Long = 0
    private var mEndDate: Long = 0
    /**
     * Returns the tag of this scheduled action
     *
     * The tag saves additional information about the scheduled action,
     * e.g. such as export parameters for scheduled backups
     * @return Tag of scheduled action
     */
    /**
     * Sets the tag of the schedules action.
     *
     * The tag saves additional information about the scheduled action,
     * e.g. such as export parameters for scheduled backups
     * @param tag Tag of scheduled action
     */
    var mTag: String? = null
    /**
     * Return GUID of recurrence pattern for this scheduled action
     * @return [Recurrence] object
     */
    /**
     * Recurrence of this scheduled action
     */
    var mRecurrence: Recurrence? = null
        private set

    /**
     * Types of events which can be scheduled
     */
    enum class ActionType {
        TRANSACTION, BACKUP
    }
    /**
     * Returns the timestamp of the last execution of this scheduled action
     *
     * This is not necessarily the time when the scheduled action was due, only when it was actually last executed.
     * @return Timestamp in milliseconds since Epoch
     */
    /**
     * Set time of last execution of the scheduled action
     * @param nextRun Timestamp in milliseconds since Epoch
     */
    /**
     * Next scheduled run of Event
     */
    var mLastRun: Long = 0

    /**
     * Unique ID of the template from which the recurring event will be executed.
     * For example, transaction UID
     */
    private var mActionUID: String? = null
    /**
     * Returns `true` if the scheduled action is enabled, `false` otherwise
     * @return `true` if the scheduled action is enabled, `false` otherwise
     */
    /**
     * Flag indicating if this event is enabled or not
     */
    var isEnabled = true
        private set
    /**
     * Returns the type of action to be performed by this scheduled action
     * @return ActionType of the scheduled action
     */
    /**
     * Sets the [ActionType]
     * @param mActionType Type of action
     */
    /**
     * Returns the total number of planned occurrences of this scheduled action.
     * @return Total number of planned occurrences of this action
     */
    /**
     * Sets the number of occurences of this action
     * @param plannedExecutions Number of occurences
     */
    /**
     * Number of times this event is planned to be executed
     */
    var mTotalFrequency = 0
    /**
     * Returns how many times this scheduled action has already been executed
     * @return Number of times this action has been executed
     */
    /**
     * Sets the number of times this scheduled action has been executed
     * @param executionCount Number of executions
     */
    /**
     * How many times this action has already been executed
     */
    var mExecutionCount = 0

    /**
     * Flag for whether the scheduled transaction should be auto-created
     */
    private var mAutoCreate = true
    private var mAutoNotify = false
    /**
     * Returns number of days in advance to create the transaction
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     * @return Number of days in advance to create transaction
     */
    /**
     * Set number of days in advance to create the transaction
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     * @param advanceCreateDays Number of days
     */
    var mAdvanceCreateDays = 0
    /**
     * Returns the number of days in advance to notify of scheduled transactions
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     * @return `true` if user will be notified, `false` otherwise
     */
    /**
     * Set number of days in advance to notify of scheduled transactions
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     * @param advanceNotifyDays Number of days
     */
    var mAdvanceNotifyDays = 0
    /**
     * Return the template account GUID for this scheduled action
     *
     * This method generates one if none was set
     * @return String GUID of template account
     */
    /**
     * Set the template account GUID
     * @param templateAccountUID String GUID of template account
     */
    var mTemplateAccountUID: String? = null
        get() = if (field == null) generateUID().also { field = it } else field

    /**
     * Returns the GUID of the action covered by this scheduled action
     * @return GUID of action
     */
    fun getMActionUID(): String? {
        return mActionUID
    }

    /**
     * Sets the GUID of the action being scheduled
     * @param actionUID GUID of the action
     */
    fun setMActionUID(actionUID: String?) {
        mActionUID = actionUID
    }

    /**
     * Returns the time when the last schedule in the sequence of planned executions was executed.
     * This relies on the number of executions of the scheduled action
     *
     * This is different from [.getMLastRun] which returns the date when the system last
     * run the scheduled action.
     * @return Time of last schedule, or -1 if the scheduled action has never been run
     */
    fun timeOfLastSchedule(): Long {
        if (mExecutionCount == 0) return -1
        var startTime = LocalDateTime.fromDateFields(Date(mStartDate))
        val multiplier = mRecurrence!!.mMultiplier
        val factor = (mExecutionCount - 1) * multiplier
        when (mRecurrence!!.mPeriodType) {
            PeriodType.HOUR -> startTime = startTime.plusHours(factor)
            PeriodType.DAY -> startTime = startTime.plusDays(factor)
            PeriodType.WEEK -> startTime = startTime.plusWeeks(factor)
            PeriodType.MONTH -> startTime = startTime.plusMonths(factor)
            PeriodType.YEAR -> startTime = startTime.plusYears(factor)
            else -> {}
        }
        return startTime.toDate().time
    }

    /**
     * Computes the next time that this scheduled action is supposed to be
     * executed based on the execution count.
     *
     *
     * This method does not consider the end time, or number of times it should be run.
     * It only considers when the next execution would theoretically be due.
     *
     * @return Next run time in milliseconds
     */
    fun computeNextCountBasedScheduledExecutionTime(): Long {
        return computeNextScheduledExecutionTimeStartingAt(timeOfLastSchedule())
    }

    /**
     * Computes the next time that this scheduled action is supposed to be
     * executed based on the time of the last run.
     *
     *
     * This method does not consider the end time, or number of times it should be run.
     * It only considers when the next execution would theoretically be due.
     *
     * @return Next run time in milliseconds
     */
    fun computeNextTimeBasedScheduledExecutionTime(): Long {
        return computeNextScheduledExecutionTimeStartingAt(mLastRun)
    }

    /**
     * Computes the next time that this scheduled action is supposed to be
     * executed starting at startTime.
     *
     *
     * This method does not consider the end time, or number of times it should be run.
     * It only considers when the next execution would theoretically be due.
     *
     * @param startTime time in milliseconds to use as start to compute the next schedule.
     *
     * @return Next run time in milliseconds
     */
    private fun computeNextScheduledExecutionTimeStartingAt(startTime: Long): Long {
        if (startTime <= 0) { // has never been run
            return mStartDate
        }
        val multiplier = mRecurrence!!.mMultiplier
        var nextScheduledExecution = LocalDateTime.fromDateFields(Date(startTime))
        when (mRecurrence!!.mPeriodType) {
            PeriodType.HOUR -> nextScheduledExecution = nextScheduledExecution.plusHours(multiplier)
            PeriodType.DAY -> nextScheduledExecution = nextScheduledExecution.plusDays(multiplier)
            PeriodType.WEEK -> nextScheduledExecution = computeNextWeeklyExecutionStartingAt(nextScheduledExecution)
            PeriodType.MONTH -> nextScheduledExecution = nextScheduledExecution.plusMonths(multiplier)
            PeriodType.YEAR -> nextScheduledExecution = nextScheduledExecution.plusYears(multiplier)
            else -> {}
        }
        return nextScheduledExecution.toDate().time
    }

    /**
     * Computes the next time that this weekly scheduled action is supposed to be
     * executed starting at startTime.
     *
     * If no days of the week have been set (GnuCash desktop allows it), it will return a
     * date in the future to ensure ScheduledActionService doesn't execute it.
     *
     * @param startTime LocalDateTime to use as start to compute the next schedule.
     *
     * @return Next run time as a LocalDateTime. A date in the future, if no days of the week
     * were set in the Recurrence.
     */
    private fun computeNextWeeklyExecutionStartingAt(startTime: LocalDateTime): LocalDateTime {
        if (mRecurrence!!.byDays().isEmpty()) return LocalDateTime.now().plusDays(1) // Just a date in the future

        // Look into the week of startTime for another scheduled day of the week
        for (dayOfWeek in mRecurrence!!.byDays()) {
            val jodaDayOfWeek = convertCalendarDayOfWeekToJoda(dayOfWeek)
            val candidateNextDueTime = startTime.withDayOfWeek(jodaDayOfWeek)
            if (candidateNextDueTime.isAfter(startTime)) return candidateNextDueTime
        }

        // Return the first scheduled day of the week from the next due week
        val firstScheduledDayOfWeek = convertCalendarDayOfWeekToJoda(mRecurrence!!.byDays()[0])
        return startTime.plusWeeks(mRecurrence!!.mMultiplier)
            .withDayOfWeek(firstScheduledDayOfWeek)
    }

    /**
     * Converts a java.util.Calendar day of the week constant to the
     * org.joda.time.DateTimeConstants equivalent.
     *
     * @param calendarDayOfWeek day of the week constant from java.util.Calendar
     * @return day of the week constant equivalent from org.joda.time.DateTimeConstants
     */
    private fun convertCalendarDayOfWeekToJoda(calendarDayOfWeek: Int): Int {
        val cal = Calendar.getInstance()
        cal[Calendar.DAY_OF_WEEK] = calendarDayOfWeek
        return LocalDateTime.fromCalendarFields(cal).dayOfWeek
    }

    /**
     * Returns the period of this scheduled action in milliseconds.
     * @return Period in milliseconds since Epoch
     */
    @Deprecated("Uses fixed values for time of months and years (which actually vary depending on number of days in month or leap year)")
    fun period(): Long {
        return mRecurrence!!.period()
    }
    /**
     * Returns the time of first execution of the scheduled action
     * @return Start time of scheduled action in milliseconds since Epoch
     */
    /**
     * Sets the time of first execution of the scheduled action
     * @param startDate Timestamp in milliseconds since Epoch
     */
    var mStartTime: Long
        get() = mStartDate
        set(startDate) {
            mStartDate = startDate
            if (mRecurrence != null) {
                mRecurrence!!.mPeriodStart = Timestamp(startDate)
            }
        }

    /**
     * Returns the time of last execution of the scheduled action
     * @return Timestamp in milliseconds since Epoch
     */
    fun getMEndDate(): Long {
        return mEndDate
    }

    /**
     * Sets the end time of the scheduled action
     * @param endDate Timestamp in milliseconds since Epoch
     */
    fun setMEndDate(endDate: Long) {
        mEndDate = endDate
        if (mRecurrence != null) {
            mRecurrence!!.setMPeriodEnd(Timestamp(mEndDate))
        }
    }

    /**
     * Toggles the enabled state of the scheduled action
     * Disabled scheduled actions will not be executed
     * @param enabled Flag if the scheduled action is enabled or not
     */
    fun setMIsEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    /**
     * Returns flag if transactions should be automatically created or not
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     * @return `true` if the transaction should be auto-created, `false` otherwise
     */
    fun shouldAutoCreate(): Boolean {
        return mAutoCreate
    }

    /**
     * Set flag for automatically creating transaction based on this scheduled action
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     * @param autoCreate Flag for auto creating transactions
     */
    fun setMAutoCreate(autoCreate: Boolean) {
        mAutoCreate = autoCreate
    }

    /**
     * Check if user will be notified of creation of scheduled transactions
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     * @return `true` if user will be notified, `false` otherwise
     */
    fun shouldAutoNotify(): Boolean {
        return mAutoNotify
    }

    /**
     * Sets whether to notify the user that scheduled transactions have been created
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     * @param autoNotify Boolean flag
     */
    fun setMAutoNotify(autoNotify: Boolean) {
        mAutoNotify = autoNotify
    }

    /**
     * Returns the event schedule (start, end and recurrence)
     * @return String description of repeat schedule
     */
    fun repeatString(): String {
        val ruleBuilder = StringBuilder(mRecurrence!!.repeatString())
        val context = GnuCashApplication.appContext
        if (mEndDate <= 0 && mTotalFrequency > 0) {
            ruleBuilder.append(", ").append(context?.getString(R.string.repeat_x_times, mTotalFrequency))
        }
        return ruleBuilder.toString()
    }

    /**
     * Creates an RFC 2445 string which describes this recurring event
     *
     * See http://recurrance.sourceforge.net/
     * @return String describing event
     */
    fun ruleString(): String {
        val separator = ";"
        val ruleBuilder = StringBuilder(mRecurrence!!.ruleString())
        if (mEndDate > 0) {
            val df = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
            df.timeZone = TimeZone.getTimeZone("UTC")
            ruleBuilder.append("UNTIL=").append(df.format(Date(mEndDate))).append(separator)
        } else if (mTotalFrequency > 0) {
            ruleBuilder.append("COUNT=").append(mTotalFrequency).append(separator)
        }
        return ruleBuilder.toString()
    }

    /**
     * Overloaded method for setting the recurrence of the scheduled action.
     *
     * This method allows you to specify the periodicity and the ordinal of it. For example,
     * a recurrence every fortnight would give parameters: [PeriodType.WEEK], ordinal:2
     * @param periodType Periodicity of the scheduled action
     * @param ordinal Ordinal of the periodicity. If unsure, specify 1
     * @see .setMRecurrence
     */
    fun setMRecurrence(periodType: PeriodType?, ordinal: Int) {
        val recurrence = Recurrence(periodType!!)
        recurrence.mMultiplier = ordinal
        setMRecurrence(recurrence)
    }

    /**
     * Sets the recurrence pattern of this scheduled action
     *
     * This also sets the start period of the recurrence object, if there is one
     * @param recurrence [Recurrence] object
     */
    fun setMRecurrence(recurrence: Recurrence) {
        mRecurrence = recurrence
        //if we were parsing XML and parsed the start and end date from the scheduled action first,
        //then use those over the values which might be gotten from the recurrence
        if (mStartDate > 0) {
            mRecurrence!!.mPeriodStart = Timestamp(mStartDate)
        } else {
            mStartDate = mRecurrence!!.mPeriodStart.time
        }
        if (mEndDate > 0) {
            mRecurrence!!.setMPeriodEnd(Timestamp(mEndDate))
        } else if (mRecurrence!!.mPeriodEnd != null) {
            mEndDate = mRecurrence!!.mPeriodEnd!!.time
        }
    }

    override fun toString(): String {
        return mActionType.name + " - " + repeatString()
    }

    companion object {
        /**
         * Creates a ScheduledAction from a Transaction and a period
         * @param transaction Transaction to be scheduled
         * @param period Period in milliseconds since Epoch
         * @return Scheduled Action
         */
        @JvmStatic
        @Deprecated("Used for parsing legacy backup files. Use {@link Recurrence} instead")
        fun parseScheduledAction(transaction: Transaction, period: Long): ScheduledAction {
            val scheduledAction = ScheduledAction(ActionType.TRANSACTION)
            scheduledAction.mActionUID = transaction.mUID
            val recurrence = fromLegacyPeriod(period)
            scheduledAction.setMRecurrence(recurrence)
            return scheduledAction
        }
    }
}