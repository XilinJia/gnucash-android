/*
 * Copyright (c) 2015 Alceu Rodrigues Neto <alceurneto@gmail.com>
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
package org.gnucash.android.util

import android.content.Context
import android.util.Log
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.BooksDbAdapter.Companion.instance
import org.gnucash.android.ui.settings.PreferenceActivity
import org.gnucash.android.util.TimestampHelper.getTimestampFromUtcString
import org.gnucash.android.util.TimestampHelper.getUtcStringFromTimestamp
import org.gnucash.android.util.TimestampHelper.timestampFromEpochZero
import java.sql.Timestamp

/**
 * A utility class to deal with Android Preferences in a centralized way.
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
object PreferencesHelper {
    /**
     * Tag for logging
     */
    private const val LOG_TAG = "PreferencesHelper"

    /**
     * Preference key for saving the last export time
     */
    const val PREFERENCE_LAST_EXPORT_TIME_KEY = "last_export_time"

    /**
     * Set the last export time in UTC time zone for a specific book.
     * This value will be used during export to determine new transactions since the last export
     *
     * @param lastExportTime the last export time to set.
     */
    fun setLastExportTime(lastExportTime: Timestamp?, bookUID: String?) {
        val utcString = getUtcStringFromTimestamp(lastExportTime!!)
        Log.d(LOG_TAG, "Storing '$utcString' as lastExportTime in Android Preferences.")
        GnuCashApplication.appContext?.getSharedPreferences(bookUID, Context.MODE_PRIVATE)!!
            .edit()
            .putString(PREFERENCE_LAST_EXPORT_TIME_KEY, utcString)
            .apply()
    }
    /**
     * Get the time for the last export operation.
     *
     * @return A [Timestamp] with the time.
     */
    /**
     * Set the last export time in UTC time zone of the currently active Book in the application.
     * This method calls through to [.setLastExportTime]
     *
     * @param lastExportTime the last export time to set.
     * @see .setLastExportTime
     */
    @JvmStatic
    var lastExportTime: Timestamp?
        get() {
            val utcString = PreferenceActivity.activeBookSharedPreferences
                .getString(
                    PREFERENCE_LAST_EXPORT_TIME_KEY,
                    getUtcStringFromTimestamp(timestampFromEpochZero)
                )
            Log.d(LOG_TAG, "Retrieving '$utcString' as lastExportTime from Android Preferences.")
            return getTimestampFromUtcString(utcString!!)
        }
        set(lastExportTime) {
            Log.v(LOG_TAG, "Saving last export time for the currently active book")
            setLastExportTime(lastExportTime, instance.activeBookUID)
        }

    /**
     * Get the time for the last export operation of a specific book.
     *
     * @return A [Timestamp] with the time.
     */
    @JvmStatic
    fun getLastExportTime(bookUID: String?): Timestamp {
        val utcString = GnuCashApplication.appContext!!
            .getSharedPreferences(bookUID, Context.MODE_PRIVATE)
            .getString(
                PREFERENCE_LAST_EXPORT_TIME_KEY,
                getUtcStringFromTimestamp(
                    timestampFromEpochZero
                )
            )
        Log.d(LOG_TAG, "Retrieving '$utcString' as lastExportTime from Android Preferences.")
        return getTimestampFromUtcString(utcString!!)
    }
}