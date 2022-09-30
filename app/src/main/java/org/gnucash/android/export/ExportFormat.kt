/*
 * Copyright (c) 2013 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.export

/**
 * Enumeration of the different export formats supported by the application
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
enum class ExportFormat(
    /**
     * Full name of the export format acronym
     */
    private val mDescription: String
) {
    QIF("Quicken Interchange Format"), OFX("Open Financial eXchange"), XML("GnuCash XML"), CSVA("GnuCash accounts CSV"), CSVT(
        "GnuCash transactions CSV"
    );

    /**
     * Returns the file extension for this export format including the period e.g. ".qif"
     * @return String file extension for the export format
     */
    val extension: String
        get() = when (this) {
            QIF -> ".qif"
            OFX -> ".ofx"
            XML -> ".gnca"
            CSVA, CSVT -> ".csv"
        }

    override fun toString(): String {
        return mDescription
    }
}