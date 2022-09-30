/*
 * Copyright (c) 2013 - 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.export.qif

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.gnucash.android.db.DatabaseSchema.*
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.model.Commodity.Companion.getInstance
import org.gnucash.android.util.FileUtils
import org.gnucash.android.util.PreferencesHelper
import org.gnucash.android.util.TimestampHelper
import java.io.*
import java.math.BigDecimal
import java.util.*

/**
 * Exports the accounts and transactions in the database to the QIF format
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Yongxin Wang <fefe.wyx></fefe.wyx>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class QifExporter : Exporter {
    /**
     * Initialize the exporter
     * @param params Export options
     */
    constructor(params: ExportParams?) : super(params!!, null) {
        LOG_TAG = "QifExporter"
    }

    /**
     * Initialize the exporter
     * @param params Options for export
     * @param db SQLiteDatabase to export
     */
    constructor(params: ExportParams?, db: SQLiteDatabase?) : super(params!!, db) {
        LOG_TAG = "QifExporter"
    }

    @Throws(ExporterException::class)
    override fun generateExport(): List<String>? {
        val newLine = "\n"
        val transactionsDbAdapter = mTransactionsDbAdapter!!
        return try {
            val lastExportTimeStamp = TimestampHelper.getUtcStringFromTimestamp(mExportParams.exportStartTime)
            val cursor = transactionsDbAdapter.fetchTransactionsWithSplitsWithTransactionAccount(
                arrayOf(
                    TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID + " AS trans_uid",
                    TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TIMESTAMP + " AS trans_time",
                    TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_DESCRIPTION + " AS trans_desc",
                    TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_NOTES + " AS trans_notes",
                    SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_QUANTITY_NUM + " AS split_quantity_num",
                    SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_QUANTITY_DENOM + " AS split_quantity_denom",
                    SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_TYPE + " AS split_type",
                    SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_MEMO + " AS split_memo",
                    "trans_extra_info.trans_acct_balance AS trans_acct_balance",
                    "trans_extra_info.trans_split_count AS trans_split_count",
                    "account1." + AccountEntry.COLUMN_UID + " AS acct1_uid",
                    "account1." + AccountEntry.COLUMN_FULL_NAME + " AS acct1_full_name",
                    "account1." + AccountEntry.COLUMN_CURRENCY + " AS acct1_currency",
                    "account1." + AccountEntry.COLUMN_TYPE + " AS acct1_type",
                    AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_FULL_NAME + " AS acct2_full_name"
                ),  // no recurrence transactions
                TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TEMPLATE + " == 0 AND " +  // in qif, split from the one account entry is not recorded (will be auto balanced)
                        "( " + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_UID + " != account1." + AccountEntry.COLUMN_UID + " OR " +  // or if the transaction has only one split (the whole transaction would be lost if it is not selected)
                        "trans_split_count == 1 )" +
                        (" AND " + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_MODIFIED_AT + " > \"" + lastExportTimeStamp + "\""),
                null,  // trans_time ASC : put transactions in time order
                // trans_uid ASC  : put splits from the same transaction together
                "acct1_currency ASC, trans_time ASC, trans_uid ASC"
            )
            val file = File(exportCacheFilePath)
            val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file), "UTF-8"))
            try {
                var currentCurrencyCode = ""
                var currentAccountUID = ""
                var currentTransactionUID = ""
                while (cursor.moveToNext()) {
                    val currencyCode = cursor.getString(cursor.getColumnIndexOrThrow("acct1_currency"))
                    val accountUID = cursor.getString(cursor.getColumnIndexOrThrow("acct1_uid"))
                    val transactionUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_uid"))
                    if (transactionUID != currentTransactionUID) {
                        if (currentTransactionUID != "") {
                            writer.append(QifHelper.ENTRY_TERMINATOR).append(newLine)
                            // end last transaction
                        }
                        if (accountUID != currentAccountUID) {
                            // no need to end account
                            //if (!currentAccountUID.equals("")) {
                            //    // end last account
                            //}
                            if (currencyCode != currentCurrencyCode) {
                                currentCurrencyCode = currencyCode
                                writer.append(QifHelper.INTERNAL_CURRENCY_PREFIX)
                                    .append(currencyCode)
                                    .append(newLine)
                            }
                            // start new account
                            currentAccountUID = accountUID
                            writer.append(QifHelper.ACCOUNT_HEADER).append(newLine)
                            writer.append(QifHelper.ACCOUNT_NAME_PREFIX)
                                .append(cursor.getString(cursor.getColumnIndexOrThrow("acct1_full_name")))
                                .append(newLine)
                            writer.append(QifHelper.ENTRY_TERMINATOR).append(newLine)
                            writer.append(QifHelper.getQifHeader(cursor.getString(cursor.getColumnIndexOrThrow("acct1_type"))))
                                .append(newLine)
                        }
                        // start new transaction
                        currentTransactionUID = transactionUID
                        writer.append(QifHelper.DATE_PREFIX)
                            .append(QifHelper.formatDate(cursor.getLong(cursor.getColumnIndexOrThrow("trans_time"))))
                            .append(newLine)
                        // Payee / description
                        writer.append(QifHelper.PAYEE_PREFIX)
                            .append(cursor.getString(cursor.getColumnIndexOrThrow("trans_desc")))
                            .append(newLine)
                        // Notes, memo
                        writer.append(QifHelper.MEMO_PREFIX)
                            .append(cursor.getString(cursor.getColumnIndexOrThrow("trans_notes")))
                            .append(newLine)
                        // deal with imbalance first
                        val imbalance = cursor.getDouble(cursor.getColumnIndexOrThrow("trans_acct_balance"))
                        val decimalImbalance = BigDecimal.valueOf(imbalance).setScale(2, BigDecimal.ROUND_HALF_UP)
                        if (decimalImbalance.compareTo(BigDecimal.ZERO) != 0) {
                            writer.append(QifHelper.SPLIT_CATEGORY_PREFIX)
                                .append(
                                    AccountsDbAdapter.getImbalanceAccountName(
                                        getInstance(cursor.getString(cursor.getColumnIndexOrThrow("acct1_currency")))
                                    )
                                )
                                .append(newLine)
                            writer.append(QifHelper.SPLIT_AMOUNT_PREFIX)
                                .append(decimalImbalance.toPlainString())
                                .append(newLine)
                        }
                    }
                    if (cursor.getInt(cursor.getColumnIndexOrThrow("trans_split_count")) == 1) {
                        // No other splits should be recorded if this is the only split.
                        continue
                    }
                    // all splits
                    // amount associated with the header account will not be exported.
                    // It can be auto balanced when importing to GnuCash
                    writer.append(QifHelper.SPLIT_CATEGORY_PREFIX)
                        .append(cursor.getString(cursor.getColumnIndexOrThrow("acct2_full_name")))
                        .append(newLine)
                    val splitMemo = cursor.getString(cursor.getColumnIndexOrThrow("split_memo"))
                    if (splitMemo != null && splitMemo.isNotEmpty()) {
                        writer.append(QifHelper.SPLIT_MEMO_PREFIX)
                            .append(splitMemo)
                            .append(newLine)
                    }
                    val splitType = cursor.getString(cursor.getColumnIndexOrThrow("split_type"))
                    val quantity_num = cursor.getDouble(cursor.getColumnIndexOrThrow("split_quantity_num"))
                    val quantity_denom = cursor.getInt(cursor.getColumnIndexOrThrow("split_quantity_denom"))
                    var precision = 0
                    when (quantity_denom) {
                        0 -> {}
                        1 -> precision = 0
                        10 -> precision = 1
                        100 -> precision = 2
                        1000 -> precision = 3
                        10000 -> precision = 4
                        100000 -> precision = 5
                        1000000 -> precision = 6
                        else -> throw ExporterException(
                            mExportParams,
                            "split quantity has illegal denominator: $quantity_denom"
                        )
                    }
                    var quantity = 0.0
                    if (quantity_denom != 0) {
                        quantity = quantity_num / quantity_denom
                    }
                    val noLocale: Locale? = null
                    writer.append(QifHelper.SPLIT_AMOUNT_PREFIX)
                        .append(if (splitType == "DEBIT") "-" else "")
                        .append(String.format(noLocale, "%." + precision + "f", quantity))
                        .append(newLine)
                }
                if (currentTransactionUID != "") {
                    // end last transaction
                    writer.append(QifHelper.ENTRY_TERMINATOR).append(newLine)
                }
                writer.flush()
            } finally {
                cursor.close()
                writer.close()
            }
            val contentValues = ContentValues()
            contentValues.put(TransactionEntry.COLUMN_EXPORTED, 1)
            transactionsDbAdapter.updateTransaction(contentValues, null, null)

            /// export successful
            PreferencesHelper.lastExportTime = TimestampHelper.timestampFromNow
            val exportedFiles = splitQIF(file)
            if (exportedFiles.isEmpty()) emptyList()
            else if (exportedFiles.size > 1) zipQifs(exportedFiles)
            else exportedFiles
        } catch (e: IOException) {
            throw ExporterException(mExportParams, e)
        }
    }

    @Throws(IOException::class)
    private fun zipQifs(exportedFiles: List<String>): List<String> {
        val zipFileName = "$exportCacheFilePath.zip"
        FileUtils.zipFiles(exportedFiles, zipFileName)
        return listOf(zipFileName)
    }

    /**
     * Splits a Qif file into several ones for each currency.
     *
     * @param file File object of the Qif file to split.
     * @return a list of paths of the newly created Qif files.
     * @throws IOException if something went wrong while splitting the file.
     */
    @Throws(IOException::class)
    private fun splitQIF(file: File): List<String> {
        // split only at the last dot
        val pathParts = file.path.split("(?=\\.[^\\.]+$)".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val splitFiles = ArrayList<String>()
        var line: String
        val `in` = BufferedReader(FileReader(file))
        var out: BufferedWriter? = null
        try {
            while (`in`.readLine().also { line = it } != null) {
                if (line.startsWith(QifHelper.INTERNAL_CURRENCY_PREFIX)) {
                    val currencyCode = line.substring(1)
                    out?.close()
                    val newFileName = pathParts[0] + "_" + currencyCode + pathParts[1]
                    splitFiles.add(newFileName)
                    out = BufferedWriter(FileWriter(newFileName))
                } else {
                    requireNotNull(out) { file.path + " format is not correct" }
                    out.append(line).append('\n')
                }
            }
        } finally {
            `in`.close()
            out?.close()
        }
        return splitFiles
    }

    /**
     * Returns the mime type for this Exporter.
     * @return MIME type as string
     */
    override val exportMimeType: String
        get() = "text/plain"
}