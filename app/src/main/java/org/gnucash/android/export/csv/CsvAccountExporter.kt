/*
 * Copyright (c) 2018 Semyannikov Gleb <nightdevgame@gmail.com>
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
package org.gnucash.android.export.csv

import android.database.sqlite.SQLiteDatabase
import com.crashlytics.android.Crashlytics
import org.gnucash.android.R
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import java.io.FileWriter
import java.io.IOException
import java.util.*

/**
 * Creates a GnuCash CSV account representation of the accounts and transactions
 *
 * @author Semyannikov Gleb <nightdevgame></nightdevgame>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class CsvAccountExporter : Exporter {
    private var mCsvSeparator: Char

    /**
     * Construct a new exporter with export parameters
     * @param params Parameters for the export
     */
    constructor(params: ExportParams) : super(params, null) {
        mCsvSeparator = params.csvSeparator
        LOG_TAG = "GncXmlExporter"
    }

    /**
     * Overloaded constructor.
     * Creates an exporter with an already open database instance.
     * @param params Parameters for the export
     * @param db SQLite database
     */
    constructor(params: ExportParams, db: SQLiteDatabase?) : super(params, db) {
        mCsvSeparator = params.csvSeparator
        LOG_TAG = "GncXmlExporter"
    }

    @Throws(ExporterException::class)
    override fun generateExport(): List<String> {
        val outputFile = exportCacheFilePath
        try {
            CsvWriter(FileWriter(outputFile), mCsvSeparator.toString() + "").use { writer -> generateExport(writer) }
        } catch (ex: IOException) {
            Crashlytics.log("Error exporting CSV")
            Crashlytics.logException(ex)
            throw ExporterException(mExportParams, ex)
        }
        return listOf(outputFile)
    }

    /**
     * Writes out all the accounts in the system as CSV to the provided writer
     * @param csvWriter Destination for the CSV export
     * @throws ExporterException if an error occurred while writing to the stream
     */
    @Throws(ExporterException::class)
    fun generateExport(csvWriter: CsvWriter) {
        try {
            val names = listOf(*mContext.resources.getStringArray(R.array.csv_account_headers))
            val accounts = mAccountsDbAdapter!!.allRecords
            for (i in names.indices) {
                csvWriter.writeToken(names[i])
            }
            csvWriter.newLine()
            for (account in accounts) {
                csvWriter.writeToken(account.mAccountType.toString())
                csvWriter.writeToken(account.mFullName)
                csvWriter.writeToken(account.mName)
                csvWriter.writeToken(null) //Account code
                csvWriter.writeToken(account.mDescription)
                csvWriter.writeToken(account.colorHexString)
                csvWriter.writeToken(null) //Account notes
                csvWriter.writeToken(account.getMCommodity().mMnemonic)
                csvWriter.writeToken("CURRENCY")
                csvWriter.writeToken(if (account.isHidden) "T" else "F")
                csvWriter.writeToken("F") //Tax
                csvWriter.writeEndToken(if (account.isPlaceholderAccount) "T" else "F")
            }
        } catch (e: IOException) {
            Crashlytics.logException(e)
            throw ExporterException(mExportParams, e)
        }
    }
}