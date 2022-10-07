/*
 * Copyright (c) 2016 Alceu Rodrigues Neto <alceurneto@gmail.com>
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

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import java.sql.Timestamp

/**
 * A utility class to deal with [Timestamp] operations in a centralized way.
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
object TimestampHelper {
    /**
     * @return A [Timestamp] with time in milliseconds equals to zero.
     */
    @JvmStatic
    val timestampFromEpochZero = Timestamp(0)

    /**
     * We are using Joda Time classes because they are thread-safe.
     */
    private val UTC_TIME_ZONE = DateTimeZone.forID("UTC")
    private val UTC_DATE_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
    private val UTC_DATE_WITH_MILLISECONDS_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS")

    /**
     * Get a [String] representing the [Timestamp]
     * in UTC time zone and 'yyyy-MM-dd HH:mm:ss.SSS' format.
     *
     * @param timestamp The [Timestamp] to format.
     * @return The formatted [String].
     */
    @JvmStatic
    fun getUtcStringFromTimestamp(timestamp: Timestamp): String {
        return UTC_DATE_WITH_MILLISECONDS_FORMAT.withZone(UTC_TIME_ZONE).print(timestamp.time)
    }

    /**
     * Get the [Timestamp] with the value of given UTC [String].
     * The [String] should be a representation in UTC time zone with the following format
     * 'yyyy-MM-dd HH:mm:ss.SSS' OR 'yyyy-MM-dd HH:mm:ss' otherwise an IllegalArgumentException
     * will be throw.
     *
     * @param utcString A [String] in UTC.
     * @return A [Timestamp] for given utcString.
     */
    @JvmStatic
    fun getTimestampFromUtcString(utcString: String): Timestamp {
        var dateTime: DateTime
        return try {
            dateTime = UTC_DATE_WITH_MILLISECONDS_FORMAT.withZone(UTC_TIME_ZONE).parseDateTime(utcString)
            Timestamp(dateTime.millis)
        } catch (firstException: IllegalArgumentException) {
            try {
                // In case of parsing of string without milliseconds.
                dateTime = UTC_DATE_FORMAT.withZone(UTC_TIME_ZONE).parseDateTime(utcString)
                Timestamp(dateTime.millis)
            } catch (secondException: IllegalArgumentException) {
                // If we are here:
                // - The utcString has an invalid format OR
                // - We are missing some relevant pattern.
                throw IllegalArgumentException("Unknown utcString = '$utcString'.", secondException)
            }
        }
    }

    /**
     * @return A [Timestamp] initialized with the system current time.
     */
    @JvmStatic
    val timestampFromNow: Timestamp
        get() = Timestamp(System.currentTimeMillis())
}