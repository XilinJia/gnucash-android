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
package org.gnucash.android.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.JobIntentService
import com.crashlytics.android.Crashlytics
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.*
import org.gnucash.android.export.ExportAsyncTask
import org.gnucash.android.export.ExportParams
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.ScheduledAction.ActionType
import org.gnucash.android.model.Transaction
import java.sql.Timestamp
import java.text.DateFormat
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * Service for running scheduled events.
 *
 *
 * It's run every time the `enqueueWork` is called. It goes
 * through all scheduled event entries in the the database and executes them.
 *
 *
 * Scheduled runs of the service should be achieved using an
 * [android.app.AlarmManager], with
 * [org.gnucash.android.receivers.PeriodicJobReceiver] as an intermediary.
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class ScheduledActionService : JobIntentService() {
    override fun onHandleWork(intent: Intent) {
        Log.i(LOG_TAG, "Starting scheduled action service")
        val booksDbAdapter = BooksDbAdapter.instance
        val books = booksDbAdapter.allRecords
        for (book in books) { //// TODO: 20.04.2017 Retrieve only the book UIDs with new method
            val dbHelper = DatabaseHelper(GnuCashApplication.appContext, book.mUID)
            val db = dbHelper.writableDatabase
            val recurrenceDbAdapter = RecurrenceDbAdapter(db)
            val scheduledActionDbAdapter = ScheduledActionDbAdapter(db, recurrenceDbAdapter)
            val scheduledActions = scheduledActionDbAdapter.allEnabledScheduledActions
            Log.i(
                LOG_TAG, String.format(
                    "Processing %d total scheduled actions for Book: %s",
                    scheduledActions.size, book.mDisplayName
                )
            )
            processScheduledActions(scheduledActions, db)

            //close all databases except the currently active database
            if (db.path != GnuCashApplication.activeDb.path) db.close()
        }
        Log.i(LOG_TAG, "Completed service @ " + DateFormat.getDateTimeInstance().format(Date()))
    }

    companion object {
        private const val LOG_TAG = "ScheduledActionService"
        private const val JOB_ID = 1001
        @JvmStatic
        fun enqueueWork(context: Context?) {
            val intent = Intent(context, ScheduledActionService::class.java)
            enqueueWork(context!!, ScheduledActionService::class.java, JOB_ID, intent)
        }

        /**
         * Process scheduled actions and execute any pending actions
         * @param scheduledActions List of scheduled actions
         */
        //made public static for testing. Do not call these methods directly
        @JvmStatic
        @VisibleForTesting
        fun processScheduledActions(scheduledActions: List<ScheduledAction>, db: SQLiteDatabase) {
            for (scheduledAction in scheduledActions) {
                val now = System.currentTimeMillis()
                val totalPlannedExecutions = scheduledAction.mTotalFrequency
                val executionCount = scheduledAction.mExecutionCount

                //the end time of the ScheduledAction is not handled here because
                //it is handled differently for transactions and backups. See the individual methods.
                if ((scheduledAction.mStartTime > now //if schedule begins in the future
                            || !scheduledAction.isEnabled) || totalPlannedExecutions in 1..executionCount
                ) { //limit was set and we reached or exceeded it
                    Log.i(LOG_TAG, "Skipping scheduled action: $scheduledAction")
                    continue
                }
                executeScheduledEvent(scheduledAction, db)
            }
        }

        /**
         * Executes a scheduled event according to the specified parameters
         * @param scheduledAction ScheduledEvent to be executed
         */
        private fun executeScheduledEvent(scheduledAction: ScheduledAction, db: SQLiteDatabase) {
            Log.i(LOG_TAG, "Executing scheduled action: $scheduledAction")
            var executionCount = 0
            executionCount += when (scheduledAction.mActionType) {
                ActionType.TRANSACTION -> executeTransactions(scheduledAction, db)
                ActionType.BACKUP -> executeBackup(scheduledAction, db)
            }
            if (executionCount > 0) {
                scheduledAction.mLastRun = System.currentTimeMillis()
                // Set the execution count in the object because it will be checked
                // for the next iteration in the calling loop.
                // This call is important, do not remove!!
                scheduledAction.mExecutionCount = scheduledAction.mExecutionCount + executionCount
                // Update the last run time and execution count
                val contentValues = ContentValues()
                contentValues.put(
                    DatabaseSchema.ScheduledActionEntry.COLUMN_LAST_RUN,
                    scheduledAction.mLastRun
                )
                contentValues.put(
                    DatabaseSchema.ScheduledActionEntry.COLUMN_EXECUTION_COUNT,
                    scheduledAction.mExecutionCount
                )
                db.update(
                    DatabaseSchema.ScheduledActionEntry.TABLE_NAME, contentValues,
                    DatabaseSchema.ScheduledActionEntry.COLUMN_UID + "=?", arrayOf(scheduledAction.mUID)
                )
            }
        }

        /**
         * Executes scheduled backups for a given scheduled action.
         * The backup will be executed only once, even if multiple schedules were missed
         * @param scheduledAction Scheduled action referencing the backup
         * @param db SQLiteDatabase to backup
         * @return Number of times backup is executed. This should either be 1 or 0
         */
        private fun executeBackup(scheduledAction: ScheduledAction, db: SQLiteDatabase): Int {
            if (!shouldExecuteScheduledBackup(scheduledAction)) return 0
            val params = ExportParams.parseCsv(scheduledAction.mTag!!)
            // HACK: the tag isn't updated with the new date, so set the correct by hand
            params.exportStartTime = Timestamp(scheduledAction.mLastRun)
            var result: Boolean? = false
            try {
                //wait for async task to finish before we proceed (we are holding a wake lock)
                result = ExportAsyncTask(GnuCashApplication.appContext!!, db).execute(params).get()
            } catch (e: InterruptedException) {
                Crashlytics.logException(e)
                Log.e(LOG_TAG, e.message!!)
            } catch (e: ExecutionException) {
                Crashlytics.logException(e)
                Log.e(LOG_TAG, e.message!!)
            }
            if (!result!!) {
                Log.i(
                    LOG_TAG, "Backup/export did not occur. There might have been no"
                            + " new transactions to export or it might have crashed"
                )
                // We don't know if something failed or there weren't transactions to export,
                // so fall on the safe side and return as if something had failed.
                // FIXME: Change ExportAsyncTask to distinguish between the two cases
                return 0
            }
            return 1
        }

        /**
         * Check if a scheduled action is due for execution
         * @param scheduledAction Scheduled action
         * @return `true` if execution is due, `false` otherwise
         */
        private fun shouldExecuteScheduledBackup(scheduledAction: ScheduledAction): Boolean {
            val now = System.currentTimeMillis()
            val endTime = scheduledAction.getMEndDate()
            if (endTime in (1 until now)) return false
            return scheduledAction.computeNextTimeBasedScheduledExecutionTime() <= now
        }

        /**
         * Executes scheduled transactions which are to be added to the database.
         *
         * If a schedule was missed, all the intervening transactions will be generated, even if
         * the end time of the transaction was already reached
         * @param scheduledAction Scheduled action which references the transaction
         * @param db SQLiteDatabase where the transactions are to be executed
         * @return Number of transactions created as a result of this action
         */
        private fun executeTransactions(scheduledAction: ScheduledAction, db: SQLiteDatabase): Int {
            var executionCount = 0
            val actionUID = scheduledAction.getMActionUID()
            val transactionsDbAdapter = TransactionsDbAdapter(db, SplitsDbAdapter(db))
            val trxnTemplate: Transaction = try {
                transactionsDbAdapter.getRecord(actionUID!!)
            } catch (ex: IllegalArgumentException) { //if the record could not be found, abort
                Log.e(
                    LOG_TAG,
                    "Scheduled transaction with UID " + actionUID + " could not be found in the db with path " + db.path
                )
                return executionCount
            }
            val now = System.currentTimeMillis()
            //if there is an end time in the past, we execute all schedules up to the end time.
            //if the end time is in the future, we execute all schedules until now (current time)
            //if there is no end time, we execute all schedules until now
            val endTime = if (scheduledAction.getMEndDate() > 0) scheduledAction.getMEndDate().coerceAtMost(now) else now
            val totalPlannedExecutions = scheduledAction.mTotalFrequency
            val transactions: MutableList<Transaction> = ArrayList()
            val previousExecutionCount = scheduledAction.mExecutionCount // We'll modify it
            //we may be executing scheduled action significantly after scheduled time (depending on when Android fires the alarm)
            //so compute the actual transaction time from pre-known values
            var transactionTime = scheduledAction.computeNextCountBasedScheduledExecutionTime()
            while (transactionTime <= endTime) {
                val recurringTrxn = Transaction(trxnTemplate, true)
                recurringTrxn.setMTimestamp(transactionTime)
                transactions.add(recurringTrxn)
                recurringTrxn.mScheduledActionUID = scheduledAction.mUID
                scheduledAction.mExecutionCount = ++executionCount //required for computingNextScheduledExecutionTime
                if (totalPlannedExecutions in (1..executionCount)) break //if we hit the total planned executions set, then abort
                transactionTime = scheduledAction.computeNextCountBasedScheduledExecutionTime()
            }
            transactionsDbAdapter.bulkAddRecords(transactions, DatabaseAdapter.UpdateMethod.insert)
            // Be nice and restore the parameter's original state to avoid confusing the callers
            scheduledAction.mExecutionCount = previousExecutionCount
            return executionCount
        }
    }
}