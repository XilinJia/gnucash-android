/*
 * Copyright (c) 2014 - 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.export

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.util.Log
import com.crashlytics.android.Crashlytics
import org.gnucash.android.BuildConfig
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.*
import org.gnucash.android.export.Exporter.ExporterException
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Base class for the different exporters
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Yongxin Wang <fefe.wyx></fefe.wyx>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
abstract class Exporter(
    /**
     * Export options
     */
    protected val mExportParams: ExportParams, db: SQLiteDatabase?
) {
    /**
     * Cache directory to which files will be first exported before moved to final destination.
     *
     * There is a different cache dir per export format, which has the name of the export format.<br></br>
     * The cache dir is cleared every time a new [Exporter] is instantiated.
     * The files created here are only accessible within this application, and should be copied to SD card before they can be shared
     *
     */
    private val mCacheDir: File

    /**
     * Adapter for retrieving accounts to export
     * Subclasses should close this object when they are done with exporting
     */
    @JvmField
    protected var mAccountsDbAdapter: AccountsDbAdapter? = null
    @JvmField
    protected var mTransactionsDbAdapter: TransactionsDbAdapter? = null
    protected var mSplitsDbAdapter: SplitsDbAdapter? = null
    @JvmField
    protected var mScheduledActionDbAdapter: ScheduledActionDbAdapter? = null
    @JvmField
    protected var mPricesDbAdapter: PricesDbAdapter? = null
    @JvmField
    protected var mCommoditiesDbAdapter: CommoditiesDbAdapter? = null
    @JvmField
    protected var mBudgetsDbAdapter: BudgetsDbAdapter? = null
    @JvmField
    protected val mContext: Context
    private var mExportCacheFilePath: String?

    /**
     * Database being currently exported
     */
    protected var mDb: SQLiteDatabase? = null

    /**
     * GUID of the book being exported
     */
    @JvmField
    var mBookUID: String

    init {
        mContext = GnuCashApplication.appContext!!
        if (db == null) {
            mAccountsDbAdapter = AccountsDbAdapter.instance
            mTransactionsDbAdapter = TransactionsDbAdapter.instance
            mSplitsDbAdapter = SplitsDbAdapter.instance
            mPricesDbAdapter = PricesDbAdapter.instance
            mCommoditiesDbAdapter = CommoditiesDbAdapter.instance
            mBudgetsDbAdapter = BudgetsDbAdapter.instance
            mScheduledActionDbAdapter = ScheduledActionDbAdapter.instance
            mDb = GnuCashApplication.activeDb
        } else {
            mDb = db
            mSplitsDbAdapter = SplitsDbAdapter(db)
            mTransactionsDbAdapter = TransactionsDbAdapter(db, mSplitsDbAdapter!!)
            mAccountsDbAdapter = AccountsDbAdapter(db, mTransactionsDbAdapter!!)
            mPricesDbAdapter = PricesDbAdapter(db)
            mCommoditiesDbAdapter = CommoditiesDbAdapter(db)
            val recurrenceDbAdapter = RecurrenceDbAdapter(db)
            mBudgetsDbAdapter = BudgetsDbAdapter(db, BudgetAmountsDbAdapter(db), recurrenceDbAdapter)
            mScheduledActionDbAdapter = ScheduledActionDbAdapter(db, recurrenceDbAdapter)
        }
        mBookUID = File(mDb!!.path).name //this depends on the database file always having the name of the book GUID
        mExportCacheFilePath = null
        mCacheDir = File(mContext.cacheDir, mExportParams.exportFormat.name)
        mCacheDir.mkdir()
        purgeDirectory(mCacheDir)
    }

    /**
     * Generates the export output
     * @throws ExporterException if an error occurs during export
     */
    @Throws(ExporterException::class)
    abstract fun generateExport(): List<String?>?

    /**
     * Recursively delete all files in a directory
     * @param directory File descriptor for directory
     */
    private fun purgeDirectory(directory: File) {
        for (file in directory.listFiles()) {
            if (file.isDirectory) purgeDirectory(file) else file.delete()
        }
    }// The file name contains a timestamp, so ensure it doesn't change with multiple calls to
    // avoid issues like #448
    /**
     * Returns the path to the file where the exporter should save the export during generation
     *
     * This path is a temporary cache file whose file extension matches the export format.<br></br>
     * This file is deleted every time a new export is started
     * @return Absolute path to file
     */
    protected val exportCacheFilePath: String
        protected get() {
            // The file name contains a timestamp, so ensure it doesn't change with multiple calls to
            // avoid issues like #448
            if (mExportCacheFilePath == null) {
                var cachePath = mCacheDir.absolutePath
                if (!cachePath.endsWith("/")) cachePath += "/"
                val bookName =
                    BooksDbAdapter.instance.getAttribute(mBookUID, DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME)
                mExportCacheFilePath = cachePath + buildExportFilename(mExportParams.exportFormat, bookName)
            }
            return mExportCacheFilePath!!
        }

    /**
     * Returns the MIME type for this exporter.
     * @return MIME type as string
     */
    open val exportMimeType: String?
        get() = "text/plain"

    class ExporterException : RuntimeException {
        constructor(params: ExportParams) : super("Failed to generate export with parameters:  $params") {}
        constructor(
            params: ExportParams,
            msg: String
        ) : super("Failed to generate export with parameters: $params - $msg") {
        }

        constructor(params: ExportParams, throwable: Throwable) : super(
            "Failed to generate " + params.exportFormat.toString() + "-" + throwable.message,
            throwable
        ) {
        }
    }

    companion object {
        /**
         * Tag for logging
         */
//        @JvmStatic
        @JvmField
        var LOG_TAG = "Exporter"

        /**
         * Application folder on external storage
         */
        @JvmField
        @Deprecated("Use {@link #BASE_FOLDER_PATH} instead")
        val LEGACY_BASE_FOLDER_PATH =
            Environment.getExternalStorageDirectory().toString() + "/" + BuildConfig.APPLICATION_ID

        /**
         * Application folder on external storage
         */
        @JvmField
        val BASE_FOLDER_PATH = GnuCashApplication.appContext!!.getExternalFilesDir(null)!!.absolutePath
        private val EXPORT_FILENAME_DATE_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

        /**
         * Strings a string of any characters not allowed in a file name.
         * All unallowed characters are replaced with an underscore
         * @param inputName Raw file name input
         * @return Sanitized file name
         */
        @JvmStatic
        fun sanitizeFilename(inputName: String): String {
            return inputName.replace("[^a-zA-Z0-9-_\\.]".toRegex(), "_")
        }

        /**
         * Builds a file name based on the current time stamp for the exported file
         * @param format Format to use when exporting
         * @param bookName Name of the book being exported. This name will be included in the generated file name
         * @return String containing the file name
         */
        @JvmStatic
        fun buildExportFilename(format: ExportFormat, bookName: String): String {
            return (EXPORT_FILENAME_DATE_FORMAT.format(Date(System.currentTimeMillis()))
                    + "_gnucash_export_" + sanitizeFilename(bookName) +
                    (if (format == ExportFormat.CSVA) "_accounts" else "") +
                    (if (format == ExportFormat.CSVT) "_transactions" else "") +
                    format.extension)
        }

        /**
         * Parses the name of an export file and returns the date of export
         * @param filename Export file name generated by [.buildExportFilename]
         * @return Date in milliseconds
         */
        @JvmStatic
        fun getExportTime(filename: String): Long {
            val tokens = filename.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var timeMillis: Long = 0
            if (tokens.size < 2) {
                return timeMillis
            }
            try {
                val date = EXPORT_FILENAME_DATE_FORMAT.parse(tokens[0] + "_" + tokens[1])
                timeMillis = date.time
            } catch (e: ParseException) {
                Log.e("Exporter", "Error parsing time from file name: " + e.message)
                Crashlytics.logException(e)
            }
            return timeMillis
        }

        /**
         * Returns that path to the export folder for the book with GUID `bookUID`.
         * This is the folder where exports like QIF and OFX will be saved for access by external programs
         * @param bookUID GUID of the book being exported. Each book has its own export path
         * @return Absolute path to export folder for active book
         */
        @JvmStatic
        fun getExportFolderPath(bookUID: String): String {
            val path = BASE_FOLDER_PATH + "/" + bookUID + "/exports/"
            val file = File(path)
            if (!file.exists()) file.mkdirs()
            return path
        }
    }
}