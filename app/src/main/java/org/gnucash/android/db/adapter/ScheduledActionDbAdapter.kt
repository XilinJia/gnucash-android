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
package org.gnucash.android.db.adapter

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.util.Log
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry
import org.gnucash.android.model.Recurrence
import org.gnucash.android.model.ScheduledAction

/**
 * Database adapter for fetching/saving/modifying scheduled events
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class ScheduledActionDbAdapter(db: SQLiteDatabase?, private val mRecurrenceDbAdapter: RecurrenceDbAdapter) :
    DatabaseAdapter<ScheduledAction>(
        db!!, ScheduledActionEntry.TABLE_NAME, arrayOf(
            ScheduledActionEntry.COLUMN_ACTION_UID,
            ScheduledActionEntry.COLUMN_TYPE,
            ScheduledActionEntry.COLUMN_START_TIME,
            ScheduledActionEntry.COLUMN_END_TIME,
            ScheduledActionEntry.COLUMN_LAST_RUN,
            ScheduledActionEntry.COLUMN_ENABLED,
            ScheduledActionEntry.COLUMN_CREATED_AT,
            ScheduledActionEntry.COLUMN_TAG,
            ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY,
            ScheduledActionEntry.COLUMN_RECURRENCE_UID,
            ScheduledActionEntry.COLUMN_AUTO_CREATE,
            ScheduledActionEntry.COLUMN_AUTO_NOTIFY,
            ScheduledActionEntry.COLUMN_ADVANCE_CREATION,
            ScheduledActionEntry.COLUMN_ADVANCE_NOTIFY,
            ScheduledActionEntry.COLUMN_TEMPLATE_ACCT_UID,
            ScheduledActionEntry.COLUMN_EXECUTION_COUNT
        )
    ) {
    init {
        LOG_TAG = "ScheduledActionDbAdapter"
    }

    override fun addRecord(model: ScheduledAction, updateMethod: UpdateMethod) {
        mRecurrenceDbAdapter.addRecord(model.mRecurrence!!, updateMethod)
        super.addRecord(model, updateMethod)
    }

    override fun bulkAddRecords(modelList: List<ScheduledAction>, updateMethod: UpdateMethod): Long {
        val recurrenceList: MutableList<Recurrence> = ArrayList(modelList.size)
        for (scheduledAction in modelList) {
            recurrenceList.add(scheduledAction.mRecurrence!!)
        }

        //first add the recurrences, they have no dependencies (foreign key constraints)
        val nRecurrences = mRecurrenceDbAdapter.bulkAddRecords(recurrenceList.toList(), updateMethod)
        Log.d(LOG_TAG, String.format("Added %d recurrences for scheduled actions", nRecurrences))
        return super.bulkAddRecords(modelList, updateMethod)
    }

    /**
     * Updates only the recurrence attributes of the scheduled action.
     * The recurrence attributes are the period, start time, end time and/or total frequency.
     * All other properties of a scheduled event are only used for internal database tracking and are
     * not central to the recurrence schedule.
     *
     * **The GUID of the scheduled action should already exist in the database**
     * @param scheduledAction Scheduled action
     * @return Database record ID of the edited scheduled action
     */
    fun updateRecurrenceAttributes(scheduledAction: ScheduledAction): Long {
        //since we are updating, first fetch the existing recurrence UID and set it to the object
        //so that it will be updated and not a new one created
        val recurrenceDbAdapter = RecurrenceDbAdapter(mDb)
        val recurrenceUID =
            recurrenceDbAdapter.getAttribute(scheduledAction.mUID!!, ScheduledActionEntry.COLUMN_RECURRENCE_UID)
        val recurrence = scheduledAction.mRecurrence
        recurrence!!.mUID = recurrenceUID
        recurrenceDbAdapter.addRecord(recurrence, UpdateMethod.update)
        val contentValues = ContentValues()
        extractBaseModelAttributes(contentValues, scheduledAction)
        contentValues.put(ScheduledActionEntry.COLUMN_START_TIME, scheduledAction.mStartTime)
        contentValues.put(ScheduledActionEntry.COLUMN_END_TIME, scheduledAction.getMEndDate())
        contentValues.put(ScheduledActionEntry.COLUMN_TAG, scheduledAction.mTag)
        contentValues.put(ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY, scheduledAction.mTotalFrequency)
        Log.d(LOG_TAG, "Updating scheduled event recurrence attributes")
        val where = ScheduledActionEntry.COLUMN_UID + "=?"
        val whereArgs = arrayOf(scheduledAction.mUID)
        return mDb.update(ScheduledActionEntry.TABLE_NAME, contentValues, where, whereArgs).toLong()
    }

    override fun setBindings(stmt: SQLiteStatement, model: ScheduledAction): SQLiteStatement {
        stmt.clearBindings()
        stmt.bindString(1, model.getMActionUID())
        stmt.bindString(2, model.mActionType.name)
        stmt.bindLong(3, model.mStartTime)
        stmt.bindLong(4, model.getMEndDate())
        stmt.bindLong(5, model.mLastRun)
        stmt.bindLong(6, (if (model.isEnabled) 1 else 0).toLong())
        stmt.bindString(7, model.mCreatedTimestamp.toString())
        if (model.mTag == null) stmt.bindNull(8) else stmt.bindString(8, model.mTag)
        stmt.bindString(9, model.mTotalFrequency.toString())
        stmt.bindString(10, model.mRecurrence!!.mUID)
        stmt.bindLong(11, (if (model.shouldAutoCreate()) 1 else 0).toLong())
        stmt.bindLong(12, (if (model.shouldAutoNotify()) 1 else 0).toLong())
        stmt.bindLong(13, model.mAdvanceCreateDays.toLong())
        stmt.bindLong(14, model.mAdvanceNotifyDays.toLong())
        stmt.bindString(15, model.mTemplateAccountUID)
        stmt.bindString(16, model.mExecutionCount.toString())
        stmt.bindString(17, model.mUID)
        return stmt
    }

    /**
     * Builds a [org.gnucash.android.model.ScheduledAction] instance from a row to cursor in the database.
     * The cursor should be already pointing to the right entry in the data set. It will not be modified in any way
     * @param cursor Cursor pointing to data set
     * @return ScheduledEvent object instance
     */
    override fun buildModelInstance(cursor: Cursor): ScheduledAction {
        val actionUid = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_ACTION_UID))
        val startTime = cursor.getLong(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_START_TIME))
        val endTime = cursor.getLong(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_END_TIME))
        val lastRun = cursor.getLong(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_LAST_RUN))
        val typeString = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_TYPE))
        val tag = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_TAG))
        val enabled = cursor.getInt(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_ENABLED)) > 0
        val numOccurrences = cursor.getInt(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY))
        val execCount = cursor.getInt(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_EXECUTION_COUNT))
        val autoCreate = cursor.getInt(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_AUTO_CREATE))
        val autoNotify = cursor.getInt(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_AUTO_NOTIFY))
        val advanceCreate = cursor.getInt(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_ADVANCE_CREATION))
        val advanceNotify = cursor.getInt(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_ADVANCE_NOTIFY))
        val recurrenceUID = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_RECURRENCE_UID))
        val templateActUID =
            cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_TEMPLATE_ACCT_UID))
        val event = ScheduledAction(ScheduledAction.ActionType.valueOf(typeString))
        populateBaseModelAttributes(cursor, event)
        event.mStartTime = startTime
        event.setMEndDate(endTime)
        event.setMActionUID(actionUid)
        event.mLastRun = lastRun
        event.mTag = tag
        event.setMIsEnabled(enabled)
        event.mTotalFrequency = numOccurrences
        event.mExecutionCount = execCount
        event.setMAutoCreate(autoCreate == 1)
        event.setMAutoNotify(autoNotify == 1)
        event.mAdvanceCreateDays = advanceCreate
        event.mAdvanceNotifyDays = advanceNotify
        //TODO: optimize by doing overriding fetchRecord(String) and join the two tables
        event.setMRecurrence(mRecurrenceDbAdapter.getRecord(recurrenceUID))
        event.mTemplateAccountUID = templateActUID
        return event
    }

    /**
     * Returns all [org.gnucash.android.model.ScheduledAction]s from the database with the specified action UID.
     * Note that the parameter is not of the the scheduled action record, but from the action table
     * @param actionUID GUID of the event itself
     * @return List of ScheduledEvents
     */
    fun getScheduledActionsWithUID(actionUID: String): List<ScheduledAction> {
        val cursor = mDb.query(
            ScheduledActionEntry.TABLE_NAME, null,
            ScheduledActionEntry.COLUMN_ACTION_UID + "= ?", arrayOf(actionUID), null, null, null
        )
        val scheduledActions: MutableList<ScheduledAction> = ArrayList()
        try {
            while (cursor.moveToNext()) {
                scheduledActions.add(buildModelInstance(cursor))
            }
        } finally {
            cursor.close()
        }
        return scheduledActions
    }

    /**
     * Returns all enabled scheduled actions in the database
     * @return List of enabled scheduled actions
     */
    val allEnabledScheduledActions: List<ScheduledAction>
        get() {
            val cursor = mDb.query(
                mTableName,
                null, ScheduledActionEntry.COLUMN_ENABLED + "=1", null, null, null, null
            )
            val scheduledActions: MutableList<ScheduledAction> = ArrayList()
            while (cursor.moveToNext()) {
                scheduledActions.add(buildModelInstance(cursor))
            }
            return scheduledActions
        }

    /**
     * Returns the number of instances of the action which have been created from this scheduled action
     * @param scheduledActionUID GUID of scheduled action
     * @return Number of transactions created from scheduled action
     */
    fun getActionInstanceCount(scheduledActionUID: String?): Long {
        val sql = ("SELECT COUNT(*) FROM " + DatabaseSchema.TransactionEntry.TABLE_NAME
                + " WHERE " + DatabaseSchema.TransactionEntry.COLUMN_SCHEDX_ACTION_UID + "=?")
        val statement = mDb.compileStatement(sql)
        statement.bindString(1, scheduledActionUID)
        return statement.simpleQueryForLong()
    }

    companion object {
        /**
         * Returns application-wide instance of database adapter
         * @return ScheduledEventDbAdapter instance
         */
        @JvmStatic
        val instance: ScheduledActionDbAdapter
            get() = GnuCashApplication.scheduledEventDbAdapter!!
    }
}