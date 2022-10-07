/*
 * Copyright (c) 2013 - 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.export

import org.gnucash.android.util.TimestampHelper

/**
 * Encapsulation of the parameters used for exporting transactions.
 * The parameters are determined by the user in the export dialog and are then transmitted to the asynchronous task which
 * actually performs the export.
 * @see ExportFormFragment
 *
 * @see ExportAsyncTask
 *
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class ExportParams(format: ExportFormat) {
    /**
     * Options for the destination of the exported transctions file.
     * It could be stored on the [.SD_CARD] or exported through another program via [.SHARING]
     */
    enum class ExportTarget(val description: String) {
        SD_CARD("SD Card"), SHARING("External Service"), DROPBOX("Dropbox"), GOOGLE_DRIVE("Google Drive"), OWNCLOUD("ownCloud"), URI(
            "Sync Service"
        );

    }
    /**
     * Return the format used for exporting
     * @return [ExportFormat]
     */
    /**
     * Set the export format
     * @param exportFormat [ExportFormat]
     */
    /**
     * Format to use for the exported transactions
     * By default, the [ExportFormat.QIF] format is used
     */
    var exportFormat = ExportFormat.QIF
    /**
     * Return date from which to start exporting transactions
     *
     * Transactions created or modified after this timestamp will be exported
     * @return Timestamp from which to export
     */
    /**
     * Set the timestamp after which all transactions created/modified will be exported
     * @param exportStartTime Timestamp
     */
    /**
     * All transactions created after this date will be exported
     */
    var exportStartTime = TimestampHelper.timestampFromEpochZero

    /**
     * Flag to determine if all transactions should be deleted after exporting is complete
     * By default no transactions are deleted
     */
    private var mDeleteTransactionsAfterExport = false
    /**
     * Get the target for the exported file
     * @return [org.gnucash.android.export.ExportParams.ExportTarget]
     */
    /**
     * Set the target for the exported transactions
     * @param mExportTarget Target for exported transactions
     */
    /**
     * Destination for the exported transactions
     */
    var exportTarget = ExportTarget.SHARING
    /**
     * Return the location where the file should be exported to.
     * When used with [ExportTarget.URI], the returned value will be a URI which can be parsed
     * with [Uri.parse]
     * @return String representing export file destination.
     */
    /**
     * Set the location where to export the file
     * @param exportLocation Destination of the export
     */
    /**
     * Location to save the file name being exported.
     * This is typically a Uri and used for [ExportTarget.URI] target
     */
    var exportLocation: String? = null
    /**
     * Get the CSV-separator char
     * @return CSV-separator char
     */
    /**
     * Set the CSV-separator char
     * @param separator CSV-separator char
     */
    /**
     * CSV-separator char
     */
    var csvSeparator = ','

    /**
     * Creates a new set of paramters and specifies the export format
     * @param format Format to use when exporting the transactions
     */
    init {
        exportFormat = format
    }

    /**
     * Returns flag whether transactions should be deleted after export
     * @return `true` if all transactions will be deleted, `false` otherwise
     */
    fun shouldDeleteTransactionsAfterExport(): Boolean {
        return mDeleteTransactionsAfterExport
    }

    /**
     * Set flag to delete transactions after exporting is complete
     * @param deleteTransactions SEt to `true` if transactions should be deleted, false if not
     */
    fun setDeleteTransactionsAfterExport(deleteTransactions: Boolean) {
        mDeleteTransactionsAfterExport = deleteTransactions
    }

    override fun toString(): String {
        return ("Export all transactions created since " + TimestampHelper.getUtcStringFromTimestamp(exportStartTime) + " UTC"
                + " as " + exportFormat.name + " to " + exportTarget.name + if (exportLocation != null) " ($exportLocation)" else "")
    }

    /**
     * Returns the export parameters formatted as CSV.
     *
     * The CSV format is: exportformat;exportTarget;shouldExportAllTransactions;shouldDeleteAllTransactions
     * @return String containing CSV format of ExportParams
     */
    fun toCsv(): String {
        val separator = ";"
        return (exportFormat.name + separator
                + exportTarget.name + separator
                + TimestampHelper.getUtcStringFromTimestamp(exportStartTime) + separator
                + java.lang.Boolean.toString(mDeleteTransactionsAfterExport) + separator
                + if (exportLocation != null) exportLocation else "")
    }

    companion object {
        /**
         * Parses csv generated by [.toCsv] to create
         * @param csvParams String containing csv of params
         * @return ExportParams from the csv
         */
        @JvmStatic
        fun parseCsv(csvParams: String): ExportParams {
            val tokens = csvParams.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val params = ExportParams(ExportFormat.valueOf(tokens[0]))
            params.exportTarget = ExportTarget.valueOf(tokens[1])
            params.exportStartTime = TimestampHelper.getTimestampFromUtcString(tokens[2])
            params.setDeleteTransactionsAfterExport(java.lang.Boolean.parseBoolean(tokens[3]))
            if (tokens.size == 5) {
                params.exportLocation = tokens[4]
            }
            return params
        }
    }
}