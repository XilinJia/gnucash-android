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
package org.gnucash.android.model

import org.gnucash.android.util.TimestampHelper
import java.sql.Timestamp
import java.util.*

/**
 * Abstract class representing the base data model which is persisted to the database.
 * All other models should extend this base model.
 */
abstract class BaseModel {
    /**
     * Returns a unique string identifier for this model instance
     * @return GUID for this model
     */
    /**
     * Sets the GUID of the model.
     *
     * A new GUID can be generated with a call to [.generateUID]
     * @param uid String unique ID
     * @author Xilin Jia <https://github.com/XilinJia> []Kotlin code created (Copyright (C) 2022)]
     */
    /**
     * Unique identifier of this model instance.
     *
     * It is declared private because it is generated only on-demand. Sub-classes should use the accessor methods to read and write this value
     * @see .getMUID
     * @see .setMUID
     */
    open var mUID: String? = null
        get() {
            if (field == null) {
                field = generateUID()
            }
            return field
        }
    /**8
     * Returns the timestamp when this model entry was created in the database
     * @return Timestamp of creation of model
     */
    /**
     * Sets the timestamp when the model was created
     * @param createdTimestamp Timestamp of model creation
     */
    var mCreatedTimestamp: Timestamp
    /**
     * Returns the timestamp when the model record in the database was last modified.
     * @return Timestamp of last modification
     */
    /**
     * Sets the timestamp when the model was last modified in the database
     *
     * Although the database automatically has triggers for entering the timestamp,
     * when SQL INSERT OR REPLACE syntax is used, it is possible to override the modified timestamp.
     * <br></br>In that case, it has to be explicitly set in the SQL statement.
     * @param modifiedTimestamp Timestamp of last modification
     */
    var mModifiedTimestamp: Timestamp

    /**
     * Initializes the model attributes.
     *
     * A GUID for this model is not generated in the constructor.
     * A unique ID will be generated on demand with a call to [.getMUID]
     */
    init {
        mCreatedTimestamp = TimestampHelper.getTimestampFromNow()
        mModifiedTimestamp = TimestampHelper.getTimestampFromNow()
    }

    /**
     * Two instances are considered equal if their GUID's are the same
     * @param o BaseModel instance to compare
     * @return `true` if both instances are equal, `false` otherwise
     */
    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is BaseModel) return false
        return mUID == o.mUID
    }

    override fun hashCode(): Int {
        return mUID.hashCode()
    }

    companion object {
        /**
         * Method for generating the Global Unique ID for the model object
         * @return Random GUID for the model object
         */
        @JvmStatic
        fun generateUID(): String {
            return UUID.randomUUID().toString().replace("-".toRegex(), "")
        }
    }
}