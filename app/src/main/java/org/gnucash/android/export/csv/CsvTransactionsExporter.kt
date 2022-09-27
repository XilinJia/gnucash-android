/*
 * Copyright (c) 2018 Semyannikov Gleb <nightdevgame@gmail.com>
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
import android.util.Log
import com.crashlytics.android.Crashlytics
import org.gnucash.android.R
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.export.Exporter.ExporterException
import org.gnucash.android.model.Account
import org.gnucash.android.model.Split
import org.gnucash.android.model.TransactionType
import org.gnucash.android.util.PreferencesHelper
import org.gnucash.android.util.TimestampHelper
import java.io.FileWriter
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Creates a GnuCash CSV transactions representation of the accounts and transactions
 *
 * @author Semyannikov Gleb <nightdevgame></nightdevgame>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class CsvTransactionsExporter : Exporter {
    private var mCsvSeparator: Char
    private val dateFormat: DateFormat = SimpleDateFormat("YYYY-MM-dd", Locale.US)

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
    override fun generateExport(): List<String?>? {
        val outputFile = exportCacheFilePath
        try {
            CsvWriter(FileWriter(outputFile), "" + mCsvSeparator).use { csvWriter -> generateExport(csvWriter) }
        } catch (ex: IOException) {
            Crashlytics.log("Error exporting CSV")
            Crashlytics.logException(ex)
            throw ExporterException(mExportParams, ex)
        }
        return Arrays.asList(outputFile)
    }

    /**
     * Write splits to CSV format
     * @param splits Splits to be written
     */
    @Throws(IOException::class)
    private fun writeSplitsToCsv(splits: List<Split>, writer: CsvWriter) {
        var index = 0
        val uidAccountMap: MutableMap<String?, Account?> = HashMap()
        for (split in splits) {
            if (index++ > 0) { // the first split is on the same line as the transactions. But after that, we
                writer.write(
                    "" + mCsvSeparator + mCsvSeparator + mCsvSeparator + mCsvSeparator
                            + mCsvSeparator + mCsvSeparator + mCsvSeparator + mCsvSeparator
                )
            }
            writer.writeToken(split.mMemo)

            //cache accounts so that we do not have to go to the DB each time
            val accountUID = split.mAccountUID
            var account: Account?
            if (uidAccountMap.containsKey(accountUID)) {
                account = uidAccountMap[accountUID]
            } else {
                account = mAccountsDbAdapter!!.getRecord(accountUID!!)
                uidAccountMap[accountUID] = account
            }
            writer.writeToken(account!!.mFullName)
            writer.writeToken(account.mName)
            val sign = if (split.mSplitType === TransactionType.CREDIT) "-" else ""
            writer.writeToken(sign + split.mQuantity!!.formattedString())
            writer.writeToken(sign + split.mQuantity!!.toLocaleString())
            writer.writeToken("" + split.mReconcileState)
            if (split.mReconcileState == Split.FLAG_RECONCILED) {
                val recDateString = dateFormat.format(Date(split.mReconcileDate.time))
                writer.writeToken(recDateString)
            } else {
                writer.writeToken(null)
            }
            writer.writeEndToken(split.mQuantity!!.divide(split.mValue!!).toLocaleString())
        }
    }

    @Throws(ExporterException::class)
    private fun generateExport(csvWriter: CsvWriter) {
        try {
            val names = Arrays.asList(*mContext.resources.getStringArray(R.array.csv_transaction_headers))
            for (i in names.indices) {
                csvWriter.writeToken(names[i])
            }
            csvWriter.newLine()
            val cursor = mTransactionsDbAdapter!!.fetchTransactionsModifiedSince(mExportParams.exportStartTime)
            Log.d(LOG_TAG, String.format("Exporting %d transactions to CSV", cursor.count))
            while (cursor.moveToNext()) {
                val transaction = mTransactionsDbAdapter!!.buildModelInstance(cursor)
                val date = Date(transaction.mTimestamp)
                csvWriter.writeToken(dateFormat.format(date))
                csvWriter.writeToken(transaction.mUID)
                csvWriter.writeToken(null) //Transaction number
                csvWriter.writeToken(transaction.getMDescription())
                csvWriter.writeToken(transaction.mNotes)
                csvWriter.writeToken("CURRENCY::" + transaction.mMnemonic)
                csvWriter.writeToken(null) // Void Reason
                csvWriter.writeToken(null) // Action
                writeSplitsToCsv(transaction.getMSplitList(), csvWriter)
            }
            PreferencesHelper.lastExportTime = TimestampHelper.timestampFromNow
        } catch (e: IOException) {
            Crashlytics.logException(e)
            throw ExporterException(mExportParams, e)
        }
    }
}