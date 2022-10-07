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
package org.gnucash.android.db.adapter

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema.RecurrenceEntry
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Recurrence
import java.sql.Timestamp
import java.util.*

/**
 * Database adapter for [Recurrence] entries
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class RecurrenceDbAdapter
/**
 * Opens the database adapter with an existing database
 *
 * @param db        SQLiteDatabase object
 */
    (db: SQLiteDatabase?) : DatabaseAdapter<Recurrence>(
    db!!, RecurrenceEntry.TABLE_NAME, arrayOf(
        RecurrenceEntry.COLUMN_MULTIPLIER,
        RecurrenceEntry.COLUMN_PERIOD_TYPE,
        RecurrenceEntry.COLUMN_BYDAY,
        RecurrenceEntry.COLUMN_PERIOD_START,
        RecurrenceEntry.COLUMN_PERIOD_END
    )
) {
    override fun buildModelInstance(cursor: Cursor): Recurrence {
        val type = cursor.getString(cursor.getColumnIndexOrThrow(RecurrenceEntry.COLUMN_PERIOD_TYPE))
        val multiplier = cursor.getLong(cursor.getColumnIndexOrThrow(RecurrenceEntry.COLUMN_MULTIPLIER))
        val periodStart = cursor.getString(cursor.getColumnIndexOrThrow(RecurrenceEntry.COLUMN_PERIOD_START))
        val periodEnd = cursor.getString(cursor.getColumnIndexOrThrow(RecurrenceEntry.COLUMN_PERIOD_END))
        val byDays = cursor.getString(cursor.getColumnIndexOrThrow(RecurrenceEntry.COLUMN_BYDAY))
        val periodType = PeriodType.valueOf(type)
        val recurrence = Recurrence(periodType)
        recurrence.mMultiplier = multiplier.toInt()
        recurrence.mPeriodStart = Timestamp.valueOf(periodStart)
        if (periodEnd != null) recurrence.setMPeriodEnd(Timestamp.valueOf(periodEnd))
        recurrence.byDays(stringToByDays(byDays))
        populateBaseModelAttributes(cursor, recurrence)
        return recurrence
    }

    override fun setBindings(stmt: SQLiteStatement, model: Recurrence): SQLiteStatement {
        stmt.clearBindings()
        stmt.bindLong(1, model.mMultiplier.toLong())
        stmt.bindString(2, model.mPeriodType!!.name)
        if (model.byDays().isNotEmpty()) stmt.bindString(3, byDaysToString(model.byDays()))
        //recurrence should always have a start date
        stmt.bindString(4, model.mPeriodStart.toString())
        if (model.mPeriodEnd != null) stmt.bindString(5, model.mPeriodEnd.toString())
        stmt.bindString(6, model.mUID)
        return stmt
    }

    companion object {
        @JvmStatic
        val instance: RecurrenceDbAdapter
            get() = GnuCashApplication.recurrenceDbAdapter!!

        /**
         * Converts a list of days of week as Calendar constants to an String for
         * storing in the database.
         *
         * @param byDays list of days of week constants from Calendar
         * @return String of days of the week or null if `byDays` was empty
         */
        private fun byDaysToString(byDays: List<Int>): String {
            val builder = StringBuilder()
            for (day in byDays) {
                when (day) {
                    Calendar.MONDAY -> builder.append("MO")
                    Calendar.TUESDAY -> builder.append("TU")
                    Calendar.WEDNESDAY -> builder.append("WE")
                    Calendar.THURSDAY -> builder.append("TH")
                    Calendar.FRIDAY -> builder.append("FR")
                    Calendar.SATURDAY -> builder.append("SA")
                    Calendar.SUNDAY -> builder.append("SU")
                    else -> throw RuntimeException("bad day of week: $day")
                }
                builder.append(",")
            }
            builder.deleteCharAt(builder.length - 1)
            return builder.toString()
        }

        /**
         * Converts a String with the comma-separated days of the week into a
         * list of Calendar constants.
         *
         * @param byDaysString String with comma-separated days fo the week
         * @return list of days of the week as Calendar constants.
         */
        private fun stringToByDays(byDaysString: String?): List<Int> {
            if (byDaysString == null) return emptyList()
            val byDaysList: MutableList<Int> = ArrayList()
            for (day in byDaysString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                when (day) {
                    "MO" -> byDaysList.add(Calendar.MONDAY)
                    "TU" -> byDaysList.add(Calendar.TUESDAY)
                    "WE" -> byDaysList.add(Calendar.WEDNESDAY)
                    "TH" -> byDaysList.add(Calendar.THURSDAY)
                    "FR" -> byDaysList.add(Calendar.FRIDAY)
                    "SA" -> byDaysList.add(Calendar.SATURDAY)
                    "SU" -> byDaysList.add(Calendar.SUNDAY)
                    else -> throw RuntimeException("bad day of week: $day")
                }
            }
            return byDaysList
        }
    }
}