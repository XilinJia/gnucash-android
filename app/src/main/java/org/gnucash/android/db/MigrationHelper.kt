/*
 * Copyright (c) 2014 - 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.db

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import androidx.preference.PreferenceManager
import com.crashlytics.android.Crashlytics
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHelper.Companion.createUpdatedAtTrigger
import org.gnucash.android.db.DatabaseSchema.*
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.importer.CommoditiesXmlHandler
import org.gnucash.android.model.*
import org.gnucash.android.model.BaseModel.Companion.generateUID
import org.gnucash.android.model.Recurrence.Companion.fromLegacyPeriod
import org.gnucash.android.service.ScheduledActionService
import org.gnucash.android.util.PreferencesHelper
import org.gnucash.android.util.TimestampHelper
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.*
import java.lang.Boolean
import java.math.BigDecimal
import java.sql.Timestamp
import java.util.*
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory
import kotlin.Exception
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.RuntimeException
import kotlin.String
import kotlin.Throws
import kotlin.arrayOf
import kotlin.plus
import kotlin.require

/**
 * Collection of helper methods which are used during database migrations
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
object MigrationHelper {
    const val LOG_TAG = "MigrationHelper"

    /**
     * Performs same function as [AccountsDbAdapter.getFullyQualifiedAccountName]
     *
     * This method is only necessary because we cannot open the database again (by instantiating [AccountsDbAdapter]
     * while it is locked for upgrades. So we re-implement the method here.
     * @param db SQLite database
     * @param accountUID Unique ID of account whose fully qualified name is to be determined
     * @return Fully qualified (colon-separated) account name
     * @see AccountsDbAdapter.getFullyQualifiedAccountName
     */
    fun getFullyQualifiedAccountName(db: SQLiteDatabase, accountUID: String): String? {
        //get the parent account UID of the account
        var cursor = db.query(
            AccountEntry.TABLE_NAME, arrayOf(AccountEntry.COLUMN_PARENT_ACCOUNT_UID),
            AccountEntry.COLUMN_UID + " = ?", arrayOf(accountUID),
            null, null, null, null
        )
        var parentAccountUID: String? = null
        if (cursor != null && cursor.moveToFirst()) {
            parentAccountUID = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_PARENT_ACCOUNT_UID))
            cursor.close()
        }

        //get the name of the account
        cursor = db.query(
            AccountEntry.TABLE_NAME, arrayOf(AccountEntry.COLUMN_NAME),
            AccountEntry.COLUMN_UID + " = ?", arrayOf(accountUID), null, null, null
        )
        var accountName: String? = null
        if (cursor != null && cursor.moveToFirst()) {
            accountName = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_NAME))
            cursor.close()
        }
        val gnucashRootAccountUID = getGnuCashRootAccountUID(db)
        if (parentAccountUID == null || accountName == null || parentAccountUID.equals(
                gnucashRootAccountUID,
                ignoreCase = true
            )
        ) {
            return accountName
        }
        val parentAccountName = getFullyQualifiedAccountName(db, parentAccountUID)
        return parentAccountName + AccountsDbAdapter.ACCOUNT_NAME_SEPARATOR + accountName
    }

    /**
     * Returns the GnuCash ROOT account UID.
     *
     * In GnuCash desktop account structure, there is a root account (which is not visible in the UI) from which
     * other top level accounts derive. GnuCash Android does not have this ROOT account by default unless the account
     * structure was imported from GnuCash for desktop. Hence this method also returns `null` as an
     * acceptable result.
     *
     * **Note:** NULL is an acceptable response, be sure to check for it
     * @return Unique ID of the GnuCash root account.
     */
    private fun getGnuCashRootAccountUID(db: SQLiteDatabase): String? {
        val condition = AccountEntry.COLUMN_TYPE + "= '" + AccountType.ROOT.name + "'"
        val cursor = db.query(
            AccountEntry.TABLE_NAME,
            null, condition, null, null, null,
            AccountEntry.COLUMN_NAME + " ASC"
        )
        var rootUID: String? = null
        if (cursor != null && cursor.moveToFirst()) {
            rootUID = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_UID))
            cursor.close()
        }
        return rootUID
    }

    /**
     * Copies the contents of the file in `src` to `dst` and then deletes the `src` if copy was successful.
     * If the file copy was unsuccessful, the src file will not be deleted.
     * @param src Source file
     * @param dst Destination file
     * @throws IOException if an error occurred during the file copy
     */
    @Throws(IOException::class)
    fun moveFile(src: File, dst: File) {
        Log.d(
            LOG_TAG, String.format(
                Locale.US, "Moving %s from %s to %s",
                src.name, src.parent, dst.parent
            )
        )
        val inChannel = FileInputStream(src).channel
        val outChannel = FileOutputStream(dst).channel
        try {
            val bytesCopied = inChannel!!.transferTo(0, inChannel.size(), outChannel)
            if (bytesCopied >= src.length()) {
                val result = src.delete()
                val msg = if (result) "Deleted src file: " else "Could not delete src: "
                Log.d(LOG_TAG, msg + src.path)
            }
        } finally {
            inChannel?.close()
            outChannel.close()
        }
    }

    /**
     * Runnable which moves all exported files (exports and backups) from the old SD card location which
     * was generic to the new folder structure which uses the application ID as folder name.
     *
     * The new folder structure also futher enables parallel installation of multiple flavours of
     * the program (like development and production) on the same device.
     */
    val moveExportedFilesToNewDefaultLocation = Runnable {
        val oldExportFolder = File(Environment.getExternalStorageDirectory().toString() + "/gnucash")
        if (oldExportFolder.exists()) {
            for (src in oldExportFolder.listFiles()!!) {
                if (src.isDirectory) continue
                val dst = File(Exporter.LEGACY_BASE_FOLDER_PATH + "/exports/" + src.name)
                try {
                    moveFile(src, dst)
                } catch (e: IOException) {
                    Log.e(LOG_TAG, "Error migrating " + src.name)
                    Crashlytics.logException(e)
                }
            }
        } else {
            //if the base folder does not exist, no point going one level deeper
            return@Runnable
        }
        val oldBackupFolder = File(oldExportFolder, "backup")
        if (oldBackupFolder.exists()) {
            for (src in File(oldExportFolder, "backup").listFiles()!!) {
                val dst = File(Exporter.LEGACY_BASE_FOLDER_PATH + "/backups/" + src.name)
                try {
                    moveFile(src, dst)
                } catch (e: IOException) {
                    Log.e(LOG_TAG, "Error migrating backup: " + src.name)
                    Crashlytics.logException(e)
                }
            }
        }
        if (oldBackupFolder.delete()) oldExportFolder.delete()
    }

    /**
     * Imports commodities into the database from XML resource file
     */
    @Throws(SAXException::class, ParserConfigurationException::class, IOException::class)
    fun importCommodities(db: SQLiteDatabase?) {
        val spf = SAXParserFactory.newInstance()
        val sp = spf.newSAXParser()
        val xr = sp.xmlReader
        val commoditiesInputStream = GnuCashApplication.appContext!!.resources
            .openRawResource(R.raw.iso_4217_currencies)
        val bos = BufferedInputStream(commoditiesInputStream)
        /** Create handler to handle XML Tags ( extends DefaultHandler )  */
        val handler = CommoditiesXmlHandler(db)
        xr.contentHandler = handler
        xr.parse(InputSource(bos))
    }

    /**
     * Upgrades the database from version 1 to 2
     * @param db SQLiteDatabase
     * @return Version number: 2 if upgrade successful, 1 otherwise
     */
    fun upgradeDbToVersion2(db: SQLiteDatabase): Int {
        val oldVersion: Int
        val addColumnSql = "ALTER TABLE " + TransactionEntry.TABLE_NAME +
                " ADD COLUMN double_account_uid varchar(255)"

        //introducing sub accounts
        Log.i(DatabaseHelper.LOG_TAG, "Adding column for parent accounts")
        val addParentAccountSql = "ALTER TABLE " + AccountEntry.TABLE_NAME +
                " ADD COLUMN " + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " varchar(255)"
        db.execSQL(addColumnSql)
        db.execSQL(addParentAccountSql)

        //update account types to GnuCash account types
        //since all were previously CHECKING, now all will be CASH
        Log.i(DatabaseHelper.LOG_TAG, "Converting account types to GnuCash compatible types")
        val cv = ContentValues()
        cv.put(SplitEntry.COLUMN_TYPE, AccountType.CASH.toString())
        db.update(AccountEntry.TABLE_NAME, cv, null, null)
        oldVersion = 2
        return oldVersion
    }

    /**
     * Upgrades the database from version 2 to 3
     * @param db SQLiteDatabase to upgrade
     * @return Version number: 3 if upgrade successful, 2 otherwise
     */
    fun upgradeDbToVersion3(db: SQLiteDatabase): Int {
        val oldVersion: Int
        val addPlaceHolderAccountFlagSql = "ALTER TABLE " + AccountEntry.TABLE_NAME +
                " ADD COLUMN " + AccountEntry.COLUMN_PLACEHOLDER + " tinyint default 0"
        db.execSQL(addPlaceHolderAccountFlagSql)
        oldVersion = 3
        return oldVersion
    }

    /**
     * Upgrades the database from version 3 to 4
     * @param db SQLiteDatabase
     * @return Version number: 4 if upgrade successful, 3 otherwise
     */
    fun upgradeDbToVersion4(db: SQLiteDatabase): Int {
        val oldVersion: Int
        val addRecurrencePeriod = "ALTER TABLE " + TransactionEntry.TABLE_NAME +
                " ADD COLUMN recurrence_period integer default 0"
        val addDefaultTransferAccount = ("ALTER TABLE " + AccountEntry.TABLE_NAME
                + " ADD COLUMN " + AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + " varchar(255)")
        val addAccountColor = (" ALTER TABLE " + AccountEntry.TABLE_NAME
                + " ADD COLUMN " + AccountEntry.COLUMN_COLOR_CODE + " varchar(255)")
        db.execSQL(addRecurrencePeriod)
        db.execSQL(addDefaultTransferAccount)
        db.execSQL(addAccountColor)
        oldVersion = 4
        return oldVersion
    }

    /**
     * Upgrades the database from version 4 to 5
     *
     * Adds favorites column to accounts
     * @param db SQLiteDatabase
     * @return Version number: 5 if upgrade successful, 4 otherwise
     */
    fun upgradeDbToVersion5(db: SQLiteDatabase): Int {
        val oldVersion: Int
        val addAccountFavorite = (" ALTER TABLE " + AccountEntry.TABLE_NAME
                + " ADD COLUMN " + AccountEntry.COLUMN_FAVORITE + " tinyint default 0")
        db.execSQL(addAccountFavorite)
        oldVersion = 5
        return oldVersion
    }

    /**
     * Upgrades the database from version 5 to version 6.<br></br>
     * This migration adds support for fully qualified account names and updates existing accounts.
     * @param db SQLite Database to be upgraded
     * @return New database version (6) if upgrade successful, old version (5) if unsuccessful
     */
    fun upgradeDbToVersion6(db: SQLiteDatabase): Int {
        val oldVersion: Int
        val addFullAccountNameQuery = (" ALTER TABLE " + AccountEntry.TABLE_NAME
                + " ADD COLUMN " + AccountEntry.COLUMN_FULL_NAME + " varchar(255) ")
        db.execSQL(addFullAccountNameQuery)

        //update all existing accounts with their fully qualified name
        val cursor = db.query(
            AccountEntry.TABLE_NAME, arrayOf(AccountEntry._ID, AccountEntry.COLUMN_UID),
            null, null, null, null, null
        )
        while (cursor != null && cursor.moveToNext()) {
            val uid = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_UID))
            val fullName = getFullyQualifiedAccountName(db, uid) ?: continue
            val contentValues = ContentValues()
            contentValues.put(AccountEntry.COLUMN_FULL_NAME, fullName)
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(AccountEntry._ID))
            db.update(AccountEntry.TABLE_NAME, contentValues, AccountEntry._ID + " = " + id, null)
        }
        cursor?.close()
        oldVersion = 6
        return oldVersion
    }

    /**
     * Code for upgrading the database to version 7 from version 6.<br></br>
     * Tasks accomplished in migration:
     *
     *  * Added new splits table for transaction splits
     *  * Extract existing info from transactions table to populate split table
     *
     * @param db SQLite Database
     * @return The new database version if upgrade was successful, or the old db version if it failed
     */
    fun upgradeDbToVersion7(db: SQLiteDatabase): Int {
        val oldVersion: Int
        db.beginTransaction()
        oldVersion = try {
            // backup transaction table
            db.execSQL("ALTER TABLE " + TransactionEntry.TABLE_NAME + " RENAME TO " + TransactionEntry.TABLE_NAME + "_bak")
            // create new transaction table
            db.execSQL(
                "create table " + TransactionEntry.TABLE_NAME + " ("
                        + TransactionEntry._ID + " integer primary key autoincrement, "
                        + TransactionEntry.COLUMN_UID + " varchar(255) not null, "
                        + TransactionEntry.COLUMN_DESCRIPTION + " varchar(255), "
                        + TransactionEntry.COLUMN_NOTES + " text, "
                        + TransactionEntry.COLUMN_TIMESTAMP + " integer not null, "
                        + TransactionEntry.COLUMN_EXPORTED + " tinyint default 0, "
                        + TransactionEntry.COLUMN_CURRENCY + " varchar(255) not null, "
                        + "recurrence_period integer default 0, "
                        + "UNIQUE (" + TransactionEntry.COLUMN_UID + ") "
                        + ");"
            )
            // initialize new transaction table wiht data from old table
            db.execSQL(
                "INSERT INTO " + TransactionEntry.TABLE_NAME + " ( "
                        + TransactionEntry._ID + " , "
                        + TransactionEntry.COLUMN_UID + " , "
                        + TransactionEntry.COLUMN_DESCRIPTION + " , "
                        + TransactionEntry.COLUMN_NOTES + " , "
                        + TransactionEntry.COLUMN_TIMESTAMP + " , "
                        + TransactionEntry.COLUMN_EXPORTED + " , "
                        + TransactionEntry.COLUMN_CURRENCY + " , "
                        + "recurrence_period )  SELECT "
                        + TransactionEntry.TABLE_NAME + "_bak." + TransactionEntry._ID + " , "
                        + TransactionEntry.TABLE_NAME + "_bak." + TransactionEntry.COLUMN_UID + " , "
                        + TransactionEntry.TABLE_NAME + "_bak." + TransactionEntry.COLUMN_DESCRIPTION + " , "
                        + TransactionEntry.TABLE_NAME + "_bak." + TransactionEntry.COLUMN_NOTES + " , "
                        + TransactionEntry.TABLE_NAME + "_bak." + TransactionEntry.COLUMN_TIMESTAMP + " , "
                        + TransactionEntry.TABLE_NAME + "_bak." + TransactionEntry.COLUMN_EXPORTED + " , "
                        + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_CURRENCY + " , "
                        + TransactionEntry.TABLE_NAME + "_bak.recurrence_period"
                        + " FROM " + TransactionEntry.TABLE_NAME + "_bak , " + AccountEntry.TABLE_NAME
                        + " ON " + TransactionEntry.TABLE_NAME + "_bak.account_uid == " + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID
            )
            // create split table
            db.execSQL(
                "CREATE TABLE " + SplitEntry.TABLE_NAME + " ("
                        + SplitEntry._ID + " integer primary key autoincrement, "
                        + SplitEntry.COLUMN_UID + " varchar(255) not null, "
                        + SplitEntry.COLUMN_MEMO + " text, "
                        + SplitEntry.COLUMN_TYPE + " varchar(255) not null, "
                        + "amount" + " varchar(255) not null, "
                        + SplitEntry.COLUMN_ACCOUNT_UID + " varchar(255) not null, "
                        + SplitEntry.COLUMN_TRANSACTION_UID + " varchar(255) not null, "
                        + "FOREIGN KEY (" + SplitEntry.COLUMN_ACCOUNT_UID + ") REFERENCES " + AccountEntry.TABLE_NAME + " (" + AccountEntry.COLUMN_UID + "), "
                        + "FOREIGN KEY (" + SplitEntry.COLUMN_TRANSACTION_UID + ") REFERENCES " + TransactionEntry.TABLE_NAME + " (" + TransactionEntry.COLUMN_UID + "), "
                        + "UNIQUE (" + SplitEntry.COLUMN_UID + ") "
                        + ");"
            )
            // Initialize split table with data from backup transaction table
            // New split table is initialized after the new transaction table as the
            // foreign key constraint will stop any data from being inserted
            // If new split table is created before the backup is made, the foreign key
            // constraint will be rewritten to refer to the backup transaction table
            db.execSQL(
                "INSERT INTO " + SplitEntry.TABLE_NAME + " ( "
                        + SplitEntry.COLUMN_UID + " , "
                        + SplitEntry.COLUMN_TYPE + " , "
                        + "amount" + " , "
                        + SplitEntry.COLUMN_ACCOUNT_UID + " , "
                        + SplitEntry.COLUMN_TRANSACTION_UID + " ) SELECT "
                        + "LOWER(HEX(RANDOMBLOB(16))) , "
                        + "CASE WHEN " + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_TYPE + " IN ( 'CASH' , 'BANK', 'ASSET', 'EXPENSE', 'RECEIVABLE', 'STOCK', 'MUTUAL' ) THEN CASE WHEN "
                        + "amount" + " < 0 THEN 'CREDIT' ELSE 'DEBIT' END ELSE CASE WHEN "
                        + "amount" + " < 0 THEN 'DEBIT' ELSE 'CREDIT' END END , "
                        + "ABS ( " + TransactionEntry.TABLE_NAME + "_bak.amount ) , "
                        + TransactionEntry.TABLE_NAME + "_bak.account_uid , "
                        + TransactionEntry.TABLE_NAME + "_bak." + TransactionEntry.COLUMN_UID
                        + " FROM " + TransactionEntry.TABLE_NAME + "_bak , " + AccountEntry.TABLE_NAME
                        + " ON " + TransactionEntry.TABLE_NAME + "_bak.account_uid = " + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID
                        + " UNION SELECT "
                        + "LOWER(HEX(RANDOMBLOB(16))) AS " + SplitEntry.COLUMN_UID + " , "
                        + "CASE WHEN " + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_TYPE + " IN ( 'CASH' , 'BANK', 'ASSET', 'EXPENSE', 'RECEIVABLE', 'STOCK', 'MUTUAL' ) THEN CASE WHEN "
                        + "amount" + " < 0 THEN 'DEBIT' ELSE 'CREDIT' END ELSE CASE WHEN "
                        + "amount" + " < 0 THEN 'CREDIT' ELSE 'DEBIT' END END , "
                        + "ABS ( " + TransactionEntry.TABLE_NAME + "_bak.amount ) , "
                        + TransactionEntry.TABLE_NAME + "_bak.double_account_uid , "
                        + TransactionEntry.TABLE_NAME + "_baK." + TransactionEntry.COLUMN_UID
                        + " FROM " + TransactionEntry.TABLE_NAME + "_bak , " + AccountEntry.TABLE_NAME
                        + " ON " + TransactionEntry.TABLE_NAME + "_bak.account_uid = " + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID
                        + " WHERE " + TransactionEntry.TABLE_NAME + "_bak.double_account_uid IS NOT NULL"
            )
            // drop backup transaction table
            db.execSQL("DROP TABLE " + TransactionEntry.TABLE_NAME + "_bak")
            db.setTransactionSuccessful()
            7
        } finally {
            db.endTransaction()
        }
        return oldVersion
    }

    /**
     * Upgrades the database from version 7 to version 8.
     *
     * This migration accomplishes the following:
     *
     *  * Added created_at and modified_at columns to all tables (including triggers for updating the columns).
     *  * New table for scheduled actions and migrate all existing recurring transactions
     *  * Auto-balancing of all existing splits
     *  * Added "hidden" flag to accounts table
     *  * Add flag for transaction templates
     *
     *
     * @param db SQLite Database to be upgraded
     * @return New database version (8) if upgrade successful, old version (7) if unsuccessful
     */
    fun upgradeDbToVersion8(db: SQLiteDatabase): Int {
        Log.i(DatabaseHelper.LOG_TAG, "Upgrading database to version 8")
        val oldVersion: Int
        File(Exporter.LEGACY_BASE_FOLDER_PATH + "/backups/").mkdirs()
        File(Exporter.LEGACY_BASE_FOLDER_PATH + "/exports/").mkdirs()
        //start moving the files in background thread before we do the database stuff
        Thread(moveExportedFilesToNewDefaultLocation).start()
        db.beginTransaction()
        try {
            Log.i(DatabaseHelper.LOG_TAG, "Creating scheduled actions table")
            db.execSQL(
                "CREATE TABLE " + ScheduledActionEntry.TABLE_NAME + " ("
                        + ScheduledActionEntry._ID + " integer primary key autoincrement, "
                        + ScheduledActionEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                        + ScheduledActionEntry.COLUMN_ACTION_UID + " varchar(255) not null, "
                        + ScheduledActionEntry.COLUMN_TYPE + " varchar(255) not null, "
                        + "period " + " integer not null, "
                        + ScheduledActionEntry.COLUMN_LAST_RUN + " integer default 0, "
                        + ScheduledActionEntry.COLUMN_START_TIME + " integer not null, "
                        + ScheduledActionEntry.COLUMN_END_TIME + " integer default 0, "
                        + ScheduledActionEntry.COLUMN_TAG + " text, "
                        + ScheduledActionEntry.COLUMN_ENABLED + " tinyint default 1, " //enabled by default
                        + ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY + " integer default 0, "
                        + ScheduledActionEntry.COLUMN_EXECUTION_COUNT + " integer default 0, "
                        + ScheduledActionEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + ScheduledActionEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP "
                        + ");" + createUpdatedAtTrigger(ScheduledActionEntry.TABLE_NAME)
            )


            //==============================BEGIN TABLE MIGRATIONS ========================================
            Log.i(DatabaseHelper.LOG_TAG, "Migrating accounts table")
            // backup transaction table
            db.execSQL("ALTER TABLE " + AccountEntry.TABLE_NAME + " RENAME TO " + AccountEntry.TABLE_NAME + "_bak")
            // create new transaction table
            db.execSQL(
                "CREATE TABLE " + AccountEntry.TABLE_NAME + " ("
                        + AccountEntry._ID + " integer primary key autoincrement, "
                        + AccountEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                        + AccountEntry.COLUMN_NAME + " varchar(255) not null, "
                        + AccountEntry.COLUMN_TYPE + " varchar(255) not null, "
                        + AccountEntry.COLUMN_CURRENCY + " varchar(255) not null, "
                        + AccountEntry.COLUMN_DESCRIPTION + " varchar(255), "
                        + AccountEntry.COLUMN_COLOR_CODE + " varchar(255), "
                        + AccountEntry.COLUMN_FAVORITE + " tinyint default 0, "
                        + AccountEntry.COLUMN_HIDDEN + " tinyint default 0, "
                        + AccountEntry.COLUMN_FULL_NAME + " varchar(255), "
                        + AccountEntry.COLUMN_PLACEHOLDER + " tinyint default 0, "
                        + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " varchar(255), "
                        + AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + " varchar(255), "
                        + AccountEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + AccountEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP "
                        + ");" + createUpdatedAtTrigger(AccountEntry.TABLE_NAME)
            )

            // initialize new account table with data from old table
            db.execSQL(
                "INSERT INTO " + AccountEntry.TABLE_NAME + " ( "
                        + AccountEntry._ID + ","
                        + AccountEntry.COLUMN_UID + " , "
                        + AccountEntry.COLUMN_NAME + " , "
                        + AccountEntry.COLUMN_TYPE + " , "
                        + AccountEntry.COLUMN_CURRENCY + " , "
                        + AccountEntry.COLUMN_COLOR_CODE + " , "
                        + AccountEntry.COLUMN_FAVORITE + " , "
                        + AccountEntry.COLUMN_FULL_NAME + " , "
                        + AccountEntry.COLUMN_PLACEHOLDER + " , "
                        + AccountEntry.COLUMN_HIDDEN + " , "
                        + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " , "
                        + AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID
                        + ") SELECT "
                        + AccountEntry.TABLE_NAME + "_bak." + AccountEntry._ID + " , "
                        + AccountEntry.TABLE_NAME + "_bak." + AccountEntry.COLUMN_UID + " , "
                        + AccountEntry.TABLE_NAME + "_bak." + AccountEntry.COLUMN_NAME + " , "
                        + AccountEntry.TABLE_NAME + "_bak." + AccountEntry.COLUMN_TYPE + " , "
                        + AccountEntry.TABLE_NAME + "_bak." + AccountEntry.COLUMN_CURRENCY + " , "
                        + AccountEntry.TABLE_NAME + "_bak." + AccountEntry.COLUMN_COLOR_CODE + " , "
                        + AccountEntry.TABLE_NAME + "_bak." + AccountEntry.COLUMN_FAVORITE + " , "
                        + AccountEntry.TABLE_NAME + "_bak." + AccountEntry.COLUMN_FULL_NAME + " , "
                        + AccountEntry.TABLE_NAME + "_bak." + AccountEntry.COLUMN_PLACEHOLDER + " , "
                        + " CASE WHEN " + AccountEntry.TABLE_NAME + "_bak.type = 'ROOT' THEN 1 ELSE 0 END, "
                        + AccountEntry.TABLE_NAME + "_bak." + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " , "
                        + AccountEntry.TABLE_NAME + "_bak." + AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID
                        + " FROM " + AccountEntry.TABLE_NAME + "_bak;"
            )
            Log.i(DatabaseHelper.LOG_TAG, "Migrating transactions table")
            // backup transaction table
            db.execSQL("ALTER TABLE " + TransactionEntry.TABLE_NAME + " RENAME TO " + TransactionEntry.TABLE_NAME + "_bak")
            // create new transaction table
            db.execSQL(
                "CREATE TABLE " + TransactionEntry.TABLE_NAME + " ("
                        + TransactionEntry._ID + " integer primary key autoincrement, "
                        + TransactionEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                        + TransactionEntry.COLUMN_DESCRIPTION + " varchar(255), "
                        + TransactionEntry.COLUMN_NOTES + " text, "
                        + TransactionEntry.COLUMN_TIMESTAMP + " integer not null, "
                        + TransactionEntry.COLUMN_EXPORTED + " tinyint default 0, "
                        + TransactionEntry.COLUMN_TEMPLATE + " tinyint default 0, "
                        + TransactionEntry.COLUMN_CURRENCY + " varchar(255) not null, "
                        + TransactionEntry.COLUMN_SCHEDX_ACTION_UID + " varchar(255), "
                        + TransactionEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + TransactionEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "FOREIGN KEY (" + TransactionEntry.COLUMN_SCHEDX_ACTION_UID + ") REFERENCES " + ScheduledActionEntry.TABLE_NAME + " (" + ScheduledActionEntry.COLUMN_UID + ") ON DELETE SET NULL "
                        + ");" + createUpdatedAtTrigger(TransactionEntry.TABLE_NAME)
            )

            // initialize new transaction table with data from old table
            db.execSQL(
                "INSERT INTO " + TransactionEntry.TABLE_NAME + " ( "
                        + TransactionEntry._ID + " , "
                        + TransactionEntry.COLUMN_UID + " , "
                        + TransactionEntry.COLUMN_DESCRIPTION + " , "
                        + TransactionEntry.COLUMN_NOTES + " , "
                        + TransactionEntry.COLUMN_TIMESTAMP + " , "
                        + TransactionEntry.COLUMN_EXPORTED + " , "
                        + TransactionEntry.COLUMN_CURRENCY + " , "
                        + TransactionEntry.COLUMN_TEMPLATE
                        + ")  SELECT "
                        + TransactionEntry.TABLE_NAME + "_bak." + TransactionEntry._ID + " , "
                        + TransactionEntry.TABLE_NAME + "_bak." + TransactionEntry.COLUMN_UID + " , "
                        + TransactionEntry.TABLE_NAME + "_bak." + TransactionEntry.COLUMN_DESCRIPTION + " , "
                        + TransactionEntry.TABLE_NAME + "_bak." + TransactionEntry.COLUMN_NOTES + " , "
                        + TransactionEntry.TABLE_NAME + "_bak." + TransactionEntry.COLUMN_TIMESTAMP + " , "
                        + TransactionEntry.TABLE_NAME + "_bak." + TransactionEntry.COLUMN_EXPORTED + " , "
                        + TransactionEntry.TABLE_NAME + "_bak." + TransactionEntry.COLUMN_CURRENCY + " , "
                        + " CASE WHEN " + TransactionEntry.TABLE_NAME + "_bak.recurrence_period > 0 THEN 1 ELSE 0 END "
                        + " FROM " + TransactionEntry.TABLE_NAME + "_bak;"
            )
            Log.i(DatabaseHelper.LOG_TAG, "Migrating splits table")
            // backup split table
            db.execSQL("ALTER TABLE " + SplitEntry.TABLE_NAME + " RENAME TO " + SplitEntry.TABLE_NAME + "_bak")
            // create new split table
            db.execSQL(
                "CREATE TABLE " + SplitEntry.TABLE_NAME + " ("
                        + SplitEntry._ID + " integer primary key autoincrement, "
                        + SplitEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                        + SplitEntry.COLUMN_MEMO + " text, "
                        + SplitEntry.COLUMN_TYPE + " varchar(255) not null, "
                        + "amount" + " varchar(255) not null, "
                        + SplitEntry.COLUMN_ACCOUNT_UID + " varchar(255) not null, "
                        + SplitEntry.COLUMN_TRANSACTION_UID + " varchar(255) not null, "
                        + SplitEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + SplitEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "FOREIGN KEY (" + SplitEntry.COLUMN_ACCOUNT_UID + ") REFERENCES " + AccountEntry.TABLE_NAME + " (" + AccountEntry.COLUMN_UID + ") ON DELETE CASCADE, "
                        + "FOREIGN KEY (" + SplitEntry.COLUMN_TRANSACTION_UID + ") REFERENCES " + TransactionEntry.TABLE_NAME + " (" + TransactionEntry.COLUMN_UID + ") ON DELETE CASCADE "
                        + ");" + createUpdatedAtTrigger(SplitEntry.TABLE_NAME)
            )

            // initialize new split table with data from old table
            db.execSQL(
                "INSERT INTO " + SplitEntry.TABLE_NAME + " ( "
                        + SplitEntry._ID + " , "
                        + SplitEntry.COLUMN_UID + " , "
                        + SplitEntry.COLUMN_MEMO + " , "
                        + SplitEntry.COLUMN_TYPE + " , "
                        + "amount" + " , "
                        + SplitEntry.COLUMN_ACCOUNT_UID + " , "
                        + SplitEntry.COLUMN_TRANSACTION_UID
                        + ")  SELECT "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry._ID + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_UID + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_MEMO + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_TYPE + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + "amount" + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_ACCOUNT_UID + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_TRANSACTION_UID
                        + " FROM " + SplitEntry.TABLE_NAME + "_bak;"
            )


            //================================ END TABLE MIGRATIONS ================================

            // String timestamp to be used for all new created entities in migration
            val timestamp = TimestampHelper.getUtcStringFromTimestamp(TimestampHelper.timestampFromNow)

            //ScheduledActionDbAdapter scheduledActionDbAdapter = new ScheduledActionDbAdapter(db);
            //SplitsDbAdapter splitsDbAdapter = new SplitsDbAdapter(db);
            //TransactionsDbAdapter transactionsDbAdapter = new TransactionsDbAdapter(db, splitsDbAdapter);
            //AccountsDbAdapter accountsDbAdapter = new AccountsDbAdapter(db,transactionsDbAdapter);
            Log.i(DatabaseHelper.LOG_TAG, "Creating default root account if none exists")
            val contentValues = ContentValues()
            //assign a root account to all accounts which had null as parent except ROOT (top-level accounts)
            val rootAccountUID: String
            var cursor = db.query(
                AccountEntry.TABLE_NAME, arrayOf(AccountEntry.COLUMN_UID),
                AccountEntry.COLUMN_TYPE + "= ?", arrayOf(AccountType.ROOT.name), null, null, null
            )
            try {
                if (cursor.moveToFirst()) {
                    rootAccountUID = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_UID))
                } else {
                    rootAccountUID = generateUID()
                    contentValues.clear()
                    contentValues.put(CommonColumns.COLUMN_UID, rootAccountUID)
                    contentValues.put(CommonColumns.COLUMN_CREATED_AT, timestamp)
                    contentValues.put(AccountEntry.COLUMN_NAME, "ROOT")
                    contentValues.put(AccountEntry.COLUMN_TYPE, "ROOT")
                    contentValues.put(AccountEntry.COLUMN_CURRENCY, Money.DEFAULT_CURRENCY_CODE)
                    contentValues.put(AccountEntry.COLUMN_PLACEHOLDER, 0)
                    contentValues.put(AccountEntry.COLUMN_HIDDEN, 1)
                    contentValues.putNull(AccountEntry.COLUMN_COLOR_CODE)
                    contentValues.put(AccountEntry.COLUMN_FAVORITE, 0)
                    contentValues.put(AccountEntry.COLUMN_FULL_NAME, " ")
                    contentValues.putNull(AccountEntry.COLUMN_PARENT_ACCOUNT_UID)
                    contentValues.putNull(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID)
                    db.insert(AccountEntry.TABLE_NAME, null, contentValues)
                }
            } finally {
                cursor.close()
            }
            //String rootAccountUID = accountsDbAdapter.getOrCreateGnuCashRootAccountUID();
            contentValues.clear()
            contentValues.put(AccountEntry.COLUMN_PARENT_ACCOUNT_UID, rootAccountUID)
            db.update(
                AccountEntry.TABLE_NAME,
                contentValues,
                AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " IS NULL AND " + AccountEntry.COLUMN_TYPE + " != ?",
                arrayOf("ROOT")
            )
            Log.i(DatabaseHelper.LOG_TAG, "Migrating existing recurring transactions")
            cursor =
                db.query(TransactionEntry.TABLE_NAME + "_bak", null, "recurrence_period > 0", null, null, null, null)
            val lastRun = System.currentTimeMillis()
            while (cursor.moveToNext()) {
                contentValues.clear()
                val timestampT =
                    Timestamp(cursor.getLong(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_TIMESTAMP)))
                contentValues.put(
                    TransactionEntry.COLUMN_CREATED_AT,
                    TimestampHelper.getUtcStringFromTimestamp(timestampT)
                )
                val transactionId = cursor.getLong(cursor.getColumnIndexOrThrow(TransactionEntry._ID))
                db.update(TransactionEntry.TABLE_NAME, contentValues, TransactionEntry._ID + "=" + transactionId, null)

                //ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.TRANSACTION);
                //scheduledAction.setActionUID(cursor.getString(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_UID)));
                //long period = cursor.getLong(cursor.getColumnIndexOrThrow("recurrence_period"));
                //scheduledAction.setPeriod(period);
                //scheduledAction.setStartTime(timestampT.getTime()); //the start time is when the transaction was created
                //scheduledAction.setLastRun(System.currentTimeMillis()); //prevent this from being executed at the end of migration
                contentValues.clear()
                contentValues.put(CommonColumns.COLUMN_UID, generateUID())
                contentValues.put(CommonColumns.COLUMN_CREATED_AT, timestamp)
                contentValues.put(
                    ScheduledActionEntry.COLUMN_ACTION_UID,
                    cursor.getString(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_UID))
                )
                contentValues.put("period", cursor.getLong(cursor.getColumnIndexOrThrow("recurrence_period")))
                contentValues.put(ScheduledActionEntry.COLUMN_START_TIME, timestampT.time)
                contentValues.put(ScheduledActionEntry.COLUMN_END_TIME, 0)
                contentValues.put(ScheduledActionEntry.COLUMN_LAST_RUN, lastRun)
                contentValues.put(ScheduledActionEntry.COLUMN_TYPE, "TRANSACTION")
                contentValues.put(ScheduledActionEntry.COLUMN_TAG, "")
                contentValues.put(ScheduledActionEntry.COLUMN_ENABLED, 1)
                contentValues.put(ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY, 0)
                contentValues.put(ScheduledActionEntry.COLUMN_EXECUTION_COUNT, 0)
                //scheduledActionDbAdapter.addRecord(scheduledAction);
                db.insert(ScheduledActionEntry.TABLE_NAME, null, contentValues)

                //build intent for recurring transactions in the database
                val intent = Intent(Intent.ACTION_INSERT)
                intent.type = Transaction.MIME_TYPE

                //cancel existing pending intent
                val context = GnuCashApplication.appContext
                val recurringPendingIntent = PendingIntent.getBroadcast(
                    context,
                    transactionId.toInt(),
                    intent,
                    PendingIntent.FLAG_CANCEL_CURRENT
                )
                val alarmManager = context?.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(recurringPendingIntent)
            }
            cursor.close()

            //auto-balance existing splits
            Log.i(DatabaseHelper.LOG_TAG, "Auto-balancing existing transaction splits")
            cursor = db.query(
                TransactionEntry.TABLE_NAME + " , " + SplitEntry.TABLE_NAME + " ON "
                        + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + "=" + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID
                        + " , " + AccountEntry.TABLE_NAME + " ON "
                        + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + "=" + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID,
                arrayOf(
                    TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " AS trans_uid",
                    TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_CURRENCY + " AS trans_currency",
                    "TOTAL ( CASE WHEN " +
                            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TYPE + " = 'DEBIT' THEN " +
                            SplitEntry.TABLE_NAME + "." + "amount" + " ELSE - " +
                            SplitEntry.TABLE_NAME + "." + "amount" + " END ) AS trans_acct_balance",
                    "COUNT ( DISTINCT " +
                            AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_CURRENCY +
                            " ) AS trans_currency_count"
                ),
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " == 0",
                null,
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID,
                "trans_acct_balance != 0 AND trans_currency_count = 1",
                null
            )
            try {
                while (cursor.moveToNext()) {
                    val imbalance = cursor.getDouble(cursor.getColumnIndexOrThrow("trans_acct_balance"))
                    val decimalImbalance = BigDecimal.valueOf(imbalance).setScale(2, BigDecimal.ROUND_HALF_UP)
                    if (decimalImbalance.compareTo(BigDecimal.ZERO) != 0) {
                        val currencyCode = cursor.getString(cursor.getColumnIndexOrThrow("trans_currency"))
                        val imbalanceAccountName = GnuCashApplication.appContext!!
                            .getString(R.string.imbalance_account_name) + "-" + currencyCode
                        var imbalanceAccountUID: String
                        val c = db.query(
                            AccountEntry.TABLE_NAME, arrayOf(AccountEntry.COLUMN_UID),
                            AccountEntry.COLUMN_FULL_NAME + "= ?", arrayOf(imbalanceAccountName),
                            null, null, null
                        )
                        try {
                            if (c.moveToFirst()) {
                                imbalanceAccountUID = c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_UID))
                            } else {
                                imbalanceAccountUID = generateUID()
                                contentValues.clear()
                                contentValues.put(CommonColumns.COLUMN_UID, imbalanceAccountUID)
                                contentValues.put(CommonColumns.COLUMN_CREATED_AT, timestamp)
                                contentValues.put(AccountEntry.COLUMN_NAME, imbalanceAccountName)
                                contentValues.put(AccountEntry.COLUMN_TYPE, "BANK")
                                contentValues.put(AccountEntry.COLUMN_CURRENCY, currencyCode)
                                contentValues.put(AccountEntry.COLUMN_PLACEHOLDER, 0)
                                contentValues.put(
                                    AccountEntry.COLUMN_HIDDEN,
                                    if (GnuCashApplication.isDoubleEntryEnabled) 0 else 1
                                )
                                contentValues.putNull(AccountEntry.COLUMN_COLOR_CODE)
                                contentValues.put(AccountEntry.COLUMN_FAVORITE, 0)
                                contentValues.put(AccountEntry.COLUMN_FULL_NAME, imbalanceAccountName)
                                contentValues.put(AccountEntry.COLUMN_PARENT_ACCOUNT_UID, rootAccountUID)
                                contentValues.putNull(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID)
                                db.insert(AccountEntry.TABLE_NAME, null, contentValues)
                            }
                        } finally {
                            c.close()
                        }
                        val TransactionUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_uid"))
                        contentValues.clear()
                        contentValues.put(CommonColumns.COLUMN_UID, generateUID())
                        contentValues.put(CommonColumns.COLUMN_CREATED_AT, timestamp)
                        contentValues.put("amount", decimalImbalance.abs().toPlainString())
                        contentValues.put(
                            SplitEntry.COLUMN_TYPE,
                            if (decimalImbalance.compareTo(BigDecimal.ZERO) < 0) "DEBIT" else "CREDIT"
                        )
                        contentValues.put(SplitEntry.COLUMN_MEMO, "")
                        contentValues.put(SplitEntry.COLUMN_ACCOUNT_UID, imbalanceAccountUID)
                        contentValues.put(SplitEntry.COLUMN_TRANSACTION_UID, TransactionUID)
                        db.insert(SplitEntry.TABLE_NAME, null, contentValues)
                        contentValues.clear()
                        contentValues.put(TransactionEntry.COLUMN_MODIFIED_AT, timestamp)
                        db.update(
                            TransactionEntry.TABLE_NAME,
                            contentValues,
                            TransactionEntry.COLUMN_UID + " == ?",
                            arrayOf(TransactionUID)
                        )
                    }
                }
            } finally {
                cursor.close()
            }
            Log.i(DatabaseHelper.LOG_TAG, "Dropping temporary migration tables")
            db.execSQL("DROP TABLE " + SplitEntry.TABLE_NAME + "_bak")
            db.execSQL("DROP TABLE " + AccountEntry.TABLE_NAME + "_bak")
            db.execSQL("DROP TABLE " + TransactionEntry.TABLE_NAME + "_bak")
            db.setTransactionSuccessful()
            oldVersion = 8
        } finally {
            db.endTransaction()
        }
        GnuCashApplication.startScheduledActionExecutionService(GnuCashApplication.appContext!!)
        return oldVersion
    }

    /**
     * Upgrades the database from version 8 to version 9.
     *
     * This migration accomplishes the following:
     *
     *  * Adds a commodities table to the database
     *  * Adds prices table to the database
     *  * Add separate columns for split value and quantity
     *  * Migrate amounts to use the correct denominations for the currency
     *
     *
     * @param db SQLite Database to be upgraded
     * @return New database version (9) if upgrade successful, old version (8) if unsuccessful
     * @throws RuntimeException if the default commodities could not be imported
     */
    fun upgradeDbToVersion9(db: SQLiteDatabase): Int {
        Log.i(DatabaseHelper.LOG_TAG, "Upgrading database to version 9")
        val oldVersion: Int
        db.beginTransaction()
        try {
            db.execSQL(
                "CREATE TABLE " + CommodityEntry.TABLE_NAME + " ("
                        + CommodityEntry._ID + " integer primary key autoincrement, "
                        + CommodityEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                        + CommodityEntry.COLUMN_NAMESPACE + " varchar(255) not null default " + Commodity.Namespace.ISO4217.name + ", "
                        + CommodityEntry.COLUMN_FULLNAME + " varchar(255) not null, "
                        + CommodityEntry.COLUMN_MNEMONIC + " varchar(255) not null, "
                        + CommodityEntry.COLUMN_LOCAL_SYMBOL + " varchar(255) not null default '', "
                        + CommodityEntry.COLUMN_CUSIP + " varchar(255), "
                        + CommodityEntry.COLUMN_SMALLEST_FRACTION + " integer not null, "
                        + CommodityEntry.COLUMN_QUOTE_FLAG + " integer not null, "
                        + CommodityEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + CommodityEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP "
                        + ");" + createUpdatedAtTrigger(CommodityEntry.TABLE_NAME)
            )
            db.execSQL(
                "CREATE UNIQUE INDEX '" + CommodityEntry.INDEX_UID
                        + "' ON " + CommodityEntry.TABLE_NAME + "(" + CommodityEntry.COLUMN_UID + ")"
            )
            try {
                importCommodities(db)
            } catch (e: SAXException) {
                Log.e(DatabaseHelper.LOG_TAG, "Error loading currencies into the database", e)
                Crashlytics.logException(e)
                throw RuntimeException(e)
            } catch (e: ParserConfigurationException) {
                Log.e(DatabaseHelper.LOG_TAG, "Error loading currencies into the database", e)
                Crashlytics.logException(e)
                throw RuntimeException(e)
            } catch (e: IOException) {
                Log.e(DatabaseHelper.LOG_TAG, "Error loading currencies into the database", e)
                Crashlytics.logException(e)
                throw RuntimeException(e)
            }
            db.execSQL(
                " ALTER TABLE " + AccountEntry.TABLE_NAME
                        + " ADD COLUMN " + AccountEntry.COLUMN_COMMODITY_UID + " varchar(255) "
                        + " REFERENCES " + CommodityEntry.TABLE_NAME + " (" + CommodityEntry.COLUMN_UID + ") "
            )
            db.execSQL(
                " ALTER TABLE " + TransactionEntry.TABLE_NAME
                        + " ADD COLUMN " + TransactionEntry.COLUMN_COMMODITY_UID + " varchar(255) "
                        + " REFERENCES " + CommodityEntry.TABLE_NAME + " (" + CommodityEntry.COLUMN_UID + ") "
            )
            db.execSQL(
                "UPDATE " + AccountEntry.TABLE_NAME + " SET " + AccountEntry.COLUMN_COMMODITY_UID + " = "
                        + " (SELECT " + CommodityEntry.COLUMN_UID
                        + " FROM " + CommodityEntry.TABLE_NAME
                        + " WHERE " + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_COMMODITY_UID + " = " + CommodityEntry.TABLE_NAME + "." + CommodityEntry.COLUMN_UID
                        + ")"
            )
            db.execSQL(
                "UPDATE " + TransactionEntry.TABLE_NAME + " SET " + TransactionEntry.COLUMN_COMMODITY_UID + " = "
                        + " (SELECT " + CommodityEntry.COLUMN_UID
                        + " FROM " + CommodityEntry.TABLE_NAME
                        + " WHERE " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_COMMODITY_UID + " = " + CommodityEntry.TABLE_NAME + "." + CommodityEntry.COLUMN_UID
                        + ")"
            )
            db.execSQL(
                "CREATE TABLE " + PriceEntry.TABLE_NAME + " ("
                        + PriceEntry._ID + " integer primary key autoincrement, "
                        + PriceEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                        + PriceEntry.COLUMN_COMMODITY_UID + " varchar(255) not null, "
                        + PriceEntry.COLUMN_CURRENCY_UID + " varchar(255) not null, "
                        + PriceEntry.COLUMN_TYPE + " varchar(255), "
                        + PriceEntry.COLUMN_DATE + " TIMESTAMP not null, "
                        + PriceEntry.COLUMN_SOURCE + " text, "
                        + PriceEntry.COLUMN_VALUE_NUM + " integer not null, "
                        + PriceEntry.COLUMN_VALUE_DENOM + " integer not null, "
                        + PriceEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + PriceEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "UNIQUE (" + PriceEntry.COLUMN_COMMODITY_UID + ", " + PriceEntry.COLUMN_CURRENCY_UID + ") ON CONFLICT REPLACE, "
                        + "FOREIGN KEY (" + PriceEntry.COLUMN_COMMODITY_UID + ") REFERENCES " + CommodityEntry.TABLE_NAME + " (" + CommodityEntry.COLUMN_UID + ") ON DELETE CASCADE, "
                        + "FOREIGN KEY (" + PriceEntry.COLUMN_CURRENCY_UID + ") REFERENCES " + CommodityEntry.TABLE_NAME + " (" + CommodityEntry.COLUMN_UID + ") ON DELETE CASCADE "
                        + ");" + createUpdatedAtTrigger(PriceEntry.TABLE_NAME)
            )
            db.execSQL(
                "CREATE UNIQUE INDEX '" + PriceEntry.INDEX_UID
                        + "' ON " + PriceEntry.TABLE_NAME + "(" + PriceEntry.COLUMN_UID + ")"
            )


            //store split amounts as integer components numerator and denominator
            db.execSQL("ALTER TABLE " + SplitEntry.TABLE_NAME + " RENAME TO " + SplitEntry.TABLE_NAME + "_bak")
            // create new split table
            db.execSQL(
                "CREATE TABLE " + SplitEntry.TABLE_NAME + " ("
                        + SplitEntry._ID + " integer primary key autoincrement, "
                        + SplitEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                        + SplitEntry.COLUMN_MEMO + " text, "
                        + SplitEntry.COLUMN_TYPE + " varchar(255) not null, "
                        + SplitEntry.COLUMN_VALUE_NUM + " integer not null, "
                        + SplitEntry.COLUMN_VALUE_DENOM + " integer not null, "
                        + SplitEntry.COLUMN_QUANTITY_NUM + " integer not null, "
                        + SplitEntry.COLUMN_QUANTITY_DENOM + " integer not null, "
                        + SplitEntry.COLUMN_ACCOUNT_UID + " varchar(255) not null, "
                        + SplitEntry.COLUMN_TRANSACTION_UID + " varchar(255) not null, "
                        + SplitEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + SplitEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "FOREIGN KEY (" + SplitEntry.COLUMN_ACCOUNT_UID + ") REFERENCES " + AccountEntry.TABLE_NAME + " (" + AccountEntry.COLUMN_UID + ") ON DELETE CASCADE, "
                        + "FOREIGN KEY (" + SplitEntry.COLUMN_TRANSACTION_UID + ") REFERENCES " + TransactionEntry.TABLE_NAME + " (" + TransactionEntry.COLUMN_UID + ") ON DELETE CASCADE "
                        + ");" + createUpdatedAtTrigger(SplitEntry.TABLE_NAME)
            )

            // initialize new split table with data from old table
            db.execSQL(
                "INSERT INTO " + SplitEntry.TABLE_NAME + " ( "
                        + SplitEntry._ID + " , "
                        + SplitEntry.COLUMN_UID + " , "
                        + SplitEntry.COLUMN_MEMO + " , "
                        + SplitEntry.COLUMN_TYPE + " , "
                        + SplitEntry.COLUMN_VALUE_NUM + " , "
                        + SplitEntry.COLUMN_VALUE_DENOM + " , "
                        + SplitEntry.COLUMN_QUANTITY_NUM + " , "
                        + SplitEntry.COLUMN_QUANTITY_DENOM + " , "
                        + SplitEntry.COLUMN_ACCOUNT_UID + " , "
                        + SplitEntry.COLUMN_TRANSACTION_UID
                        + ")  SELECT "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry._ID + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_UID + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_MEMO + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_TYPE + " , "
                        + SplitEntry.TABLE_NAME + "_bak.amount * 100, " //we will update this value in the next steps
                        + "100, "
                        + SplitEntry.TABLE_NAME + "_bak.amount * 100, " //default units of 2 decimal places were assumed until now
                        + "100, "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_ACCOUNT_UID + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_TRANSACTION_UID
                        + " FROM " + SplitEntry.TABLE_NAME + "_bak;"
            )


            //************** UPDATE SPLITS WHOSE CURRENCIES HAVE NO DECIMAL PLACES *****************
            //get all account UIDs which have currencies with fraction digits of 0
            var query = ("SELECT " + "A." + AccountEntry.COLUMN_UID + " AS account_uid "
                    + " FROM " + AccountEntry.TABLE_NAME + " AS A, " + CommodityEntry.TABLE_NAME + " AS C "
                    + " WHERE A." + AccountEntry.COLUMN_CURRENCY + " = C." + CommodityEntry.COLUMN_MNEMONIC
                    + " AND C." + CommodityEntry.COLUMN_SMALLEST_FRACTION + "= 1")
            var cursor = db.rawQuery(query, null)
            val accountUIDs: MutableList<String?> = ArrayList()
            try {
                while (cursor.moveToNext()) {
                    val accountUID = cursor.getString(cursor.getColumnIndexOrThrow("account_uid"))
                    accountUIDs.add(accountUID)
                }
            } finally {
                cursor.close()
            }
            var accounts = TextUtils.join("' , '", accountUIDs)
            db.execSQL(
                "REPLACE INTO " + SplitEntry.TABLE_NAME + " ( "
                        + SplitEntry.COLUMN_UID + " , "
                        + SplitEntry.COLUMN_MEMO + " , "
                        + SplitEntry.COLUMN_TYPE + " , "
                        + SplitEntry.COLUMN_ACCOUNT_UID + " , "
                        + SplitEntry.COLUMN_TRANSACTION_UID + " , "
                        + SplitEntry.COLUMN_CREATED_AT + " , "
                        + SplitEntry.COLUMN_MODIFIED_AT + " , "
                        + SplitEntry.COLUMN_VALUE_NUM + " , "
                        + SplitEntry.COLUMN_VALUE_DENOM + " , "
                        + SplitEntry.COLUMN_QUANTITY_NUM + " , "
                        + SplitEntry.COLUMN_QUANTITY_DENOM
                        + ")  SELECT "
                        + SplitEntry.COLUMN_UID + " , "
                        + SplitEntry.COLUMN_MEMO + " , "
                        + SplitEntry.COLUMN_TYPE + " , "
                        + SplitEntry.COLUMN_ACCOUNT_UID + " , "
                        + SplitEntry.COLUMN_TRANSACTION_UID + " , "
                        + SplitEntry.COLUMN_CREATED_AT + " , "
                        + SplitEntry.COLUMN_MODIFIED_AT + " , "
                        + " ROUND (" + SplitEntry.COLUMN_VALUE_NUM + "/ 100), "
                        + "1, "
                        + " ROUND (" + SplitEntry.COLUMN_QUANTITY_NUM + "/ 100), "
                        + "1 "
                        + " FROM " + SplitEntry.TABLE_NAME
                        + " WHERE " + SplitEntry.COLUMN_ACCOUNT_UID + " IN ('" + accounts + "')"
                        + ";"
            )


            //************ UPDATE SPLITS WITH CURRENCIES HAVING 3 DECIMAL PLACES *******************
            query = ("SELECT " + "A." + AccountEntry.COLUMN_UID + " AS account_uid "
                    + " FROM " + AccountEntry.TABLE_NAME + " AS A, " + CommodityEntry.TABLE_NAME + " AS C "
                    + " WHERE A." + AccountEntry.COLUMN_CURRENCY + " = C." + CommodityEntry.COLUMN_MNEMONIC
                    + " AND C." + CommodityEntry.COLUMN_SMALLEST_FRACTION + "= 1000")
            cursor = db.rawQuery(query, null)
            accountUIDs.clear()
            try {
                while (cursor.moveToNext()) {
                    val accountUID = cursor.getString(cursor.getColumnIndexOrThrow("account_uid"))
                    accountUIDs.add(accountUID)
                }
            } finally {
                cursor.close()
            }
            accounts = TextUtils.join("' , '", accountUIDs)
            db.execSQL(
                "REPLACE INTO " + SplitEntry.TABLE_NAME + " ( "
                        + SplitEntry.COLUMN_UID + " , "
                        + SplitEntry.COLUMN_MEMO + " , "
                        + SplitEntry.COLUMN_TYPE + " , "
                        + SplitEntry.COLUMN_ACCOUNT_UID + " , "
                        + SplitEntry.COLUMN_TRANSACTION_UID + " , "
                        + SplitEntry.COLUMN_CREATED_AT + " , "
                        + SplitEntry.COLUMN_MODIFIED_AT + " , "
                        + SplitEntry.COLUMN_VALUE_NUM + " , "
                        + SplitEntry.COLUMN_VALUE_DENOM + " , "
                        + SplitEntry.COLUMN_QUANTITY_NUM + " , "
                        + SplitEntry.COLUMN_QUANTITY_DENOM
                        + ")  SELECT "
                        + SplitEntry.COLUMN_UID + " , "
                        + SplitEntry.COLUMN_MEMO + " , "
                        + SplitEntry.COLUMN_TYPE + " , "
                        + SplitEntry.COLUMN_ACCOUNT_UID + " , "
                        + SplitEntry.COLUMN_TRANSACTION_UID + " , "
                        + SplitEntry.COLUMN_CREATED_AT + " , "
                        + SplitEntry.COLUMN_MODIFIED_AT + " , "
                        + SplitEntry.COLUMN_VALUE_NUM + "* 10, " //add an extra zero because we used only 2 digits before
                        + "1000, "
                        + SplitEntry.COLUMN_QUANTITY_NUM + "* 10, "
                        + "1000 "
                        + " FROM " + SplitEntry.TABLE_NAME
                        + " WHERE " + SplitEntry.COLUMN_ACCOUNT_UID + " IN ('" + accounts + "')"
                        + ";"
            )
            db.execSQL("DROP TABLE " + SplitEntry.TABLE_NAME + "_bak")
            db.setTransactionSuccessful()
            oldVersion = 9
        } finally {
            db.endTransaction()
        }
        return oldVersion
    }

    /**
     * Upgrades the database to version 10
     *
     * This method converts all saved scheduled export parameters to the new format using the
     * timestamp of last export
     * @param db SQLite database
     * @return 10 if upgrade was successful, 9 otherwise
     */
    fun upgradeDbToVersion10(db: SQLiteDatabase): Int {
        Log.i(DatabaseHelper.LOG_TAG, "Upgrading database to version 9")
        val oldVersion: Int
        db.beginTransaction()
        try {
            val cursor = db.query(
                ScheduledActionEntry.TABLE_NAME,
                arrayOf(ScheduledActionEntry.COLUMN_UID, ScheduledActionEntry.COLUMN_TAG),
                ScheduledActionEntry.COLUMN_TYPE + " = ?",
                arrayOf(ScheduledAction.ActionType.BACKUP.name),
                null,
                null,
                null
            )
            val contentValues = ContentValues()
            while (cursor.moveToNext()) {
                val paramString = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_TAG))
                val tokens = paramString.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val params = ExportParams(ExportFormat.valueOf(tokens[0]))
                params.exportTarget = ExportParams.ExportTarget.valueOf(tokens[1])
                params.setDeleteTransactionsAfterExport(Boolean.parseBoolean(tokens[3]))
                val exportAll = Boolean.parseBoolean(tokens[2])
                if (exportAll) {
                    params.exportStartTime = TimestampHelper.timestampFromEpochZero
                } else {
                    val timestamp = PreferencesHelper.lastExportTime
                    if (timestamp != null) {
                        params.exportStartTime = timestamp
                    }
                }
                val uid = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_UID))
                contentValues.clear()
                contentValues.put(ScheduledActionEntry.COLUMN_UID, uid)
                contentValues.put(ScheduledActionEntry.COLUMN_TAG, params.toCsv())
                db.insert(ScheduledActionEntry.TABLE_NAME, null, contentValues)
            }
            cursor.close()
            db.setTransactionSuccessful()
            oldVersion = 10
        } finally {
            db.endTransaction()
        }
        return oldVersion
    }

    /**
     * Upgrade database to version 11
     *
     *
     * Migrate scheduled backups and update export parameters to the new format
     *
     * @param db SQLite database
     * @return 11 if upgrade was successful, 10 otherwise
     */
    fun upgradeDbToVersion11(db: SQLiteDatabase): Int {
        Log.i(DatabaseHelper.LOG_TAG, "Upgrading database to version 9")
        val oldVersion: Int
        db.beginTransaction()
        try {
            val cursor = db.query(
                ScheduledActionEntry.TABLE_NAME,
                null,
                ScheduledActionEntry.COLUMN_TYPE + "= ?",
                arrayOf(ScheduledAction.ActionType.BACKUP.name),
                null,
                null,
                null
            )
            val uidToTagMap: MutableMap<String, String> = HashMap()
            while (cursor.moveToNext()) {
                val uid = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_UID))
                var tag = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_TAG))
                val tokens = tag.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                try {
                    val timestamp = TimestampHelper.getTimestampFromUtcString(tokens[2])
                } catch (ex: IllegalArgumentException) {
                    tokens[2] = TimestampHelper.getUtcStringFromTimestamp(PreferencesHelper.lastExportTime!!)
                } finally {
                    tag = TextUtils.join(";", tokens)
                }
                uidToTagMap[uid] = tag
            }
            cursor.close()
            val contentValues = ContentValues()
            for ((key, value) in uidToTagMap) {
                contentValues.clear()
                contentValues.put(ScheduledActionEntry.COLUMN_TAG, value)
                db.update(
                    ScheduledActionEntry.TABLE_NAME, contentValues,
                    ScheduledActionEntry.COLUMN_UID + " = ?", arrayOf(key)
                )
            }
            db.setTransactionSuccessful()
            oldVersion = 11
        } finally {
            db.endTransaction()
        }
        return oldVersion
    }

    @JvmStatic
    fun subtractTimeZoneOffset(timestamp: Timestamp, timeZone: TimeZone): Timestamp {
        val millisecondsToSubtract = Math.abs(timeZone.getOffset(timestamp.time)).toLong()
        return Timestamp(timestamp.time - millisecondsToSubtract)
    }

    /**
     * Upgrade database to version 12
     *
     *
     * Change last_export_time Android preference to current value - N
     * where N is the absolute timezone offset for current user time zone.
     * For details see #467.
     *
     * @param db SQLite database
     * @return 12 if upgrade was successful, 11 otherwise
     */
    fun upgradeDbToVersion12(db: SQLiteDatabase?): Int {
        Log.i(LOG_TAG, "Upgrading database to version 12")
        var oldVersion = 11
        try {
            val currentLastExportTime = PreferencesHelper.lastExportTime!!
            val updatedLastExportTime = subtractTimeZoneOffset(
                currentLastExportTime, TimeZone.getDefault()
            )
            PreferencesHelper.lastExportTime = updatedLastExportTime
            oldVersion = 12
        } catch (ignored: Exception) {
            // Do nothing: here oldVersion = 11.
        }
        return oldVersion
    }

    /**
     * Upgrades the database to version 13.
     *
     * This migration makes the following changes to the database:
     *
     *  * Adds support for multiple database for different books and one extra database for storing book info
     *  * Adds a table for budgets
     *  * Adds an extra table for recurrences
     *  * Migrate scheduled transaction recurrences to own table
     *  * Adds flags for reconciled status to split table
     *  * Add flags for auto-/advance- create and notification to scheduled actions
     *  * Migrate old shared preferences into new book-specific preferences
     *
     *
     * @param db SQlite database to be upgraded
     * @return New database version, 13 if migration succeeds, 11 otherwise
     */
    fun upgradeDbToVersion13(db: SQLiteDatabase): Int {
        Log.i(DatabaseHelper.LOG_TAG, "Upgrading database to version 13")
        val oldVersion: Int
        db.beginTransaction()
        try {
            db.execSQL(
                "CREATE TABLE " + RecurrenceEntry.TABLE_NAME + " ("
                        + RecurrenceEntry._ID + " integer primary key autoincrement, "
                        + RecurrenceEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                        + RecurrenceEntry.COLUMN_MULTIPLIER + " integer not null default 1, "
                        + RecurrenceEntry.COLUMN_PERIOD_TYPE + " varchar(255) not null, "
                        + RecurrenceEntry.COLUMN_BYDAY + " varchar(255), "
                        + RecurrenceEntry.COLUMN_PERIOD_START + " timestamp not null, "
                        + RecurrenceEntry.COLUMN_PERIOD_END + " timestamp, "
                        + RecurrenceEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + RecurrenceEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP); "
                        + createUpdatedAtTrigger(RecurrenceEntry.TABLE_NAME)
            )
            db.execSQL(
                "CREATE TABLE " + BudgetEntry.TABLE_NAME + " ("
                        + BudgetEntry._ID + " integer primary key autoincrement, "
                        + BudgetEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                        + BudgetEntry.COLUMN_NAME + " varchar(255) not null, "
                        + BudgetEntry.COLUMN_DESCRIPTION + " varchar(255), "
                        + BudgetEntry.COLUMN_RECURRENCE_UID + " varchar(255) not null, "
                        + BudgetEntry.COLUMN_NUM_PERIODS + " integer, "
                        + BudgetEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + BudgetEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "FOREIGN KEY (" + BudgetEntry.COLUMN_RECURRENCE_UID + ") REFERENCES " + RecurrenceEntry.TABLE_NAME + " (" + RecurrenceEntry.COLUMN_UID + ") "
                        + ");" + createUpdatedAtTrigger(BudgetEntry.TABLE_NAME)
            )
            db.execSQL(
                "CREATE UNIQUE INDEX '" + BudgetEntry.INDEX_UID
                        + "' ON " + BudgetEntry.TABLE_NAME + "(" + BudgetEntry.COLUMN_UID + ")"
            )
            db.execSQL(
                "CREATE TABLE " + BudgetAmountEntry.TABLE_NAME + " ("
                        + BudgetAmountEntry._ID + " integer primary key autoincrement, "
                        + BudgetAmountEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                        + BudgetAmountEntry.COLUMN_BUDGET_UID + " varchar(255) not null, "
                        + BudgetAmountEntry.COLUMN_ACCOUNT_UID + " varchar(255) not null, "
                        + BudgetAmountEntry.COLUMN_AMOUNT_NUM + " integer not null, "
                        + BudgetAmountEntry.COLUMN_AMOUNT_DENOM + " integer not null, "
                        + BudgetAmountEntry.COLUMN_PERIOD_NUM + " integer not null, "
                        + BudgetAmountEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + BudgetAmountEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "FOREIGN KEY (" + BudgetAmountEntry.COLUMN_ACCOUNT_UID + ") REFERENCES " + AccountEntry.TABLE_NAME + " (" + AccountEntry.COLUMN_UID + ") ON DELETE CASCADE, "
                        + "FOREIGN KEY (" + BudgetAmountEntry.COLUMN_BUDGET_UID + ") REFERENCES " + BudgetEntry.TABLE_NAME + " (" + BudgetEntry.COLUMN_UID + ") ON DELETE CASCADE "
                        + ");" + createUpdatedAtTrigger(BudgetAmountEntry.TABLE_NAME)
            )
            db.execSQL(
                "CREATE UNIQUE INDEX '" + BudgetAmountEntry.INDEX_UID
                        + "' ON " + BudgetAmountEntry.TABLE_NAME + "(" + BudgetAmountEntry.COLUMN_UID + ")"
            )


            //extract recurrences from scheduled actions table and put in the recurrence table
            db.execSQL("ALTER TABLE " + ScheduledActionEntry.TABLE_NAME + " RENAME TO " + ScheduledActionEntry.TABLE_NAME + "_bak")
            db.execSQL(
                "CREATE TABLE " + ScheduledActionEntry.TABLE_NAME + " ("
                        + ScheduledActionEntry._ID + " integer primary key autoincrement, "
                        + ScheduledActionEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                        + ScheduledActionEntry.COLUMN_ACTION_UID + " varchar(255) not null, "
                        + ScheduledActionEntry.COLUMN_TYPE + " varchar(255) not null, "
                        + ScheduledActionEntry.COLUMN_RECURRENCE_UID + " varchar(255) not null, "
                        + ScheduledActionEntry.COLUMN_TEMPLATE_ACCT_UID + " varchar(255) not null, "
                        + ScheduledActionEntry.COLUMN_LAST_RUN + " integer default 0, "
                        + ScheduledActionEntry.COLUMN_START_TIME + " integer not null, "
                        + ScheduledActionEntry.COLUMN_END_TIME + " integer default 0, "
                        + ScheduledActionEntry.COLUMN_TAG + " text, "
                        + ScheduledActionEntry.COLUMN_ENABLED + " tinyint default 1, " //enabled by default
                        + ScheduledActionEntry.COLUMN_AUTO_CREATE + " tinyint default 1, "
                        + ScheduledActionEntry.COLUMN_AUTO_NOTIFY + " tinyint default 0, "
                        + ScheduledActionEntry.COLUMN_ADVANCE_CREATION + " integer default 0, "
                        + ScheduledActionEntry.COLUMN_ADVANCE_NOTIFY + " integer default 0, "
                        + ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY + " integer default 0, "
                        + ScheduledActionEntry.COLUMN_EXECUTION_COUNT + " integer default 0, "
                        + ScheduledActionEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + ScheduledActionEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "FOREIGN KEY (" + ScheduledActionEntry.COLUMN_RECURRENCE_UID + ") REFERENCES " + RecurrenceEntry.TABLE_NAME + " (" + RecurrenceEntry.COLUMN_UID + ") "
                        + ");" + createUpdatedAtTrigger(ScheduledActionEntry.TABLE_NAME)
            )


            // initialize new transaction table with data from old table
            db.execSQL(
                "INSERT INTO " + ScheduledActionEntry.TABLE_NAME + " ( "
                        + ScheduledActionEntry._ID + " , "
                        + ScheduledActionEntry.COLUMN_UID + " , "
                        + ScheduledActionEntry.COLUMN_ACTION_UID + " , "
                        + ScheduledActionEntry.COLUMN_TYPE + " , "
                        + ScheduledActionEntry.COLUMN_LAST_RUN + " , "
                        + ScheduledActionEntry.COLUMN_START_TIME + " , "
                        + ScheduledActionEntry.COLUMN_END_TIME + " , "
                        + ScheduledActionEntry.COLUMN_ENABLED + " , "
                        + ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY + " , "
                        + ScheduledActionEntry.COLUMN_EXECUTION_COUNT + " , "
                        + ScheduledActionEntry.COLUMN_CREATED_AT + " , "
                        + ScheduledActionEntry.COLUMN_MODIFIED_AT + " , "
                        + ScheduledActionEntry.COLUMN_RECURRENCE_UID + " , "
                        + ScheduledActionEntry.COLUMN_TEMPLATE_ACCT_UID + " , "
                        + ScheduledActionEntry.COLUMN_TAG
                        + ")  SELECT "
                        + ScheduledActionEntry.TABLE_NAME + "_bak." + ScheduledActionEntry._ID + " , "
                        + ScheduledActionEntry.TABLE_NAME + "_bak." + ScheduledActionEntry.COLUMN_UID + " , "
                        + ScheduledActionEntry.TABLE_NAME + "_bak." + ScheduledActionEntry.COLUMN_ACTION_UID + " , "
                        + ScheduledActionEntry.TABLE_NAME + "_bak." + ScheduledActionEntry.COLUMN_TYPE + " , "
                        + ScheduledActionEntry.TABLE_NAME + "_bak." + ScheduledActionEntry.COLUMN_LAST_RUN + " , "
                        + ScheduledActionEntry.TABLE_NAME + "_bak." + ScheduledActionEntry.COLUMN_START_TIME + " , "
                        + ScheduledActionEntry.TABLE_NAME + "_bak." + ScheduledActionEntry.COLUMN_END_TIME + " , "
                        + ScheduledActionEntry.TABLE_NAME + "_bak." + ScheduledActionEntry.COLUMN_ENABLED + " , "
                        + ScheduledActionEntry.TABLE_NAME + "_bak." + ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY + " , "
                        + ScheduledActionEntry.TABLE_NAME + "_bak." + ScheduledActionEntry.COLUMN_EXECUTION_COUNT + " , "
                        + ScheduledActionEntry.TABLE_NAME + "_bak." + ScheduledActionEntry.COLUMN_CREATED_AT + " , "
                        + ScheduledActionEntry.TABLE_NAME + "_bak." + ScheduledActionEntry.COLUMN_MODIFIED_AT + " , "
                        + " 'dummy-string' ," //will be updated in next steps
                        + " 'dummy-string' ,"
                        + ScheduledActionEntry.TABLE_NAME + "_bak." + ScheduledActionEntry.COLUMN_TAG
                        + " FROM " + ScheduledActionEntry.TABLE_NAME + "_bak;"
            )

            //update the template-account-guid and the recurrence guid for all scheduled actions
            val cursor = db.query(
                ScheduledActionEntry.TABLE_NAME + "_bak", arrayOf(
                    ScheduledActionEntry.COLUMN_UID,
                    "period",
                    ScheduledActionEntry.COLUMN_START_TIME
                ),
                null, null, null, null, null
            )
            val contentValues = ContentValues()
            while (cursor.moveToNext()) {
                val uid = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_UID))
                val period = cursor.getLong(cursor.getColumnIndexOrThrow("period"))
                val startTime = cursor.getLong(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_START_TIME))
                val recurrence = fromLegacyPeriod(period)
                recurrence.mPeriodStart = Timestamp(startTime)
                contentValues.clear()
                contentValues.put(RecurrenceEntry.COLUMN_UID, recurrence.mUID)
                contentValues.put(RecurrenceEntry.COLUMN_MULTIPLIER, recurrence.mMultiplier)
                contentValues.put(RecurrenceEntry.COLUMN_PERIOD_TYPE, recurrence.mPeriodType!!.name)
                contentValues.put(RecurrenceEntry.COLUMN_PERIOD_START, recurrence.mPeriodStart.toString())
                db.insert(RecurrenceEntry.TABLE_NAME, null, contentValues)
                contentValues.clear()
                contentValues.put(ScheduledActionEntry.COLUMN_RECURRENCE_UID, recurrence.mUID)
                contentValues.put(ScheduledActionEntry.COLUMN_TEMPLATE_ACCT_UID, generateUID())
                db.update(
                    ScheduledActionEntry.TABLE_NAME, contentValues,
                    ScheduledActionEntry.COLUMN_UID + " = ?", arrayOf(uid)
                )
            }
            cursor.close()
            db.execSQL("DROP TABLE " + ScheduledActionEntry.TABLE_NAME + "_bak")


            //==============  Add RECONCILE_STATE and RECONCILE_DATE to the splits table ==========
            //We migrate the whole table because we want those columns to have default values
            db.execSQL("ALTER TABLE " + SplitEntry.TABLE_NAME + " RENAME TO " + SplitEntry.TABLE_NAME + "_bak")
            db.execSQL(
                "CREATE TABLE " + SplitEntry.TABLE_NAME + " ("
                        + SplitEntry._ID + " integer primary key autoincrement, "
                        + SplitEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                        + SplitEntry.COLUMN_MEMO + " text, "
                        + SplitEntry.COLUMN_TYPE + " varchar(255) not null, "
                        + SplitEntry.COLUMN_VALUE_NUM + " integer not null, "
                        + SplitEntry.COLUMN_VALUE_DENOM + " integer not null, "
                        + SplitEntry.COLUMN_QUANTITY_NUM + " integer not null, "
                        + SplitEntry.COLUMN_QUANTITY_DENOM + " integer not null, "
                        + SplitEntry.COLUMN_ACCOUNT_UID + " varchar(255) not null, "
                        + SplitEntry.COLUMN_TRANSACTION_UID + " varchar(255) not null, "
                        + SplitEntry.COLUMN_RECONCILE_STATE + " varchar(1) not null default 'n', "
                        + SplitEntry.COLUMN_RECONCILE_DATE + " timestamp not null default current_timestamp, "
                        + SplitEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + SplitEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "FOREIGN KEY (" + SplitEntry.COLUMN_ACCOUNT_UID + ") REFERENCES " + AccountEntry.TABLE_NAME + " (" + AccountEntry.COLUMN_UID + ") ON DELETE CASCADE, "
                        + "FOREIGN KEY (" + SplitEntry.COLUMN_TRANSACTION_UID + ") REFERENCES " + TransactionEntry.TABLE_NAME + " (" + TransactionEntry.COLUMN_UID + ") ON DELETE CASCADE "
                        + ");" + createUpdatedAtTrigger(SplitEntry.TABLE_NAME)
            )
            db.execSQL(
                "INSERT INTO " + SplitEntry.TABLE_NAME + " ( "
                        + SplitEntry._ID + " , "
                        + SplitEntry.COLUMN_UID + " , "
                        + SplitEntry.COLUMN_MEMO + " , "
                        + SplitEntry.COLUMN_TYPE + " , "
                        + SplitEntry.COLUMN_VALUE_NUM + " , "
                        + SplitEntry.COLUMN_VALUE_DENOM + " , "
                        + SplitEntry.COLUMN_QUANTITY_NUM + " , "
                        + SplitEntry.COLUMN_QUANTITY_DENOM + " , "
                        + SplitEntry.COLUMN_ACCOUNT_UID + " , "
                        + SplitEntry.COLUMN_TRANSACTION_UID
                        + ")  SELECT "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry._ID + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_UID + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_MEMO + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_TYPE + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_VALUE_NUM + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_VALUE_DENOM + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_QUANTITY_NUM + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_QUANTITY_DENOM + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_ACCOUNT_UID + " , "
                        + SplitEntry.TABLE_NAME + "_bak." + SplitEntry.COLUMN_TRANSACTION_UID
                        + " FROM " + SplitEntry.TABLE_NAME + "_bak;"
            )
            db.execSQL("DROP TABLE " + SplitEntry.TABLE_NAME + "_bak")
            db.setTransactionSuccessful()
            oldVersion = 13
        } finally {
            db.endTransaction()
        }

        //Migrate book-specific preferences away from shared preferences
        Log.d(LOG_TAG, "Migrating shared preferences into book preferences")
        val context = GnuCashApplication.appContext
        val keyUseDoubleEntry = context?.getString(R.string.key_use_double_entry)
        val keySaveOpeningBalance = context?.getString(R.string.key_save_opening_balances)
        val keyLastExportTime = PreferencesHelper.PREFERENCE_LAST_EXPORT_TIME_KEY
        val keyUseCompactView = context?.getString(R.string.key_use_compact_list)
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val lastExportTime =
            sharedPrefs.getString(keyLastExportTime, TimestampHelper.timestampFromEpochZero.toString())
        val useDoubleEntry = sharedPrefs.getBoolean(keyUseDoubleEntry, true)
        val saveOpeningBalance = sharedPrefs.getBoolean(keySaveOpeningBalance, false)
        val useCompactTrnView = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context?.getString(R.string.key_use_double_entry), !useDoubleEntry)
        val rootAccountUID = getGnuCashRootAccountUID(db)
        val bookPrefs = context?.getSharedPreferences(rootAccountUID, Context.MODE_PRIVATE)
        bookPrefs!!.edit()
            .putString(keyLastExportTime, lastExportTime)
            .putBoolean(keyUseDoubleEntry, useDoubleEntry)
            .putBoolean(keySaveOpeningBalance, saveOpeningBalance)
            .putBoolean(keyUseCompactView, useCompactTrnView)
            .apply()
        rescheduleServiceAlarm()
        return oldVersion
    }

    /**
     * Cancel the existing alarm for the scheduled service and restarts/reschedules the service
     */
    private fun rescheduleServiceAlarm() {
        val context = GnuCashApplication.appContext

        //cancel the existing pending intent so that the alarm can be rescheduled
        val alarmIntent = Intent(context, ScheduledActionService::class.java)
        val pendingIntent = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_NO_CREATE)
        if (pendingIntent != null) {
            val alarmManager = context?.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        GnuCashApplication.startScheduledActionExecutionService(context!!)
    }

    /**
     * Move files from `srcDir` to `dstDir`
     * Subdirectories will be created in the target as necessary
     * @param srcDir Source directory which should already exist
     * @param dstDir Destination directory which should already exist
     * @see .moveFile
     * @throws IOException if the `srcDir` does not exist or `dstDir` could not be created
     * @throws IllegalArgumentException if `srcDir` is not a directory
     */
    @Throws(IOException::class)
    private fun moveDirectory(srcDir: File, dstDir: File) {
        require(srcDir.isDirectory) { "Source is not a directory: " + srcDir.path }
        if (!srcDir.exists()) {
            val msg = String.format(Locale.US, "Source directory %s does not exist", srcDir.path)
            Log.e(LOG_TAG, msg)
            throw IOException(msg)
        }
        if (!dstDir.exists() || !dstDir.isDirectory) {
            Log.w(LOG_TAG, "Target directory does not exist. Attempting to create..." + dstDir.path)
            if (!dstDir.mkdirs()) {
                throw IOException(
                    String.format(
                        "Target directory %s does not exist and could not be created",
                        dstDir.path
                    )
                )
            }
        }
        if (srcDir.listFiles() == null) //nothing to see here, move along
            return
        for (src in srcDir.listFiles()!!) {
            if (src.isDirectory) {
                val dst = File(dstDir, src.name)
                dst.mkdir()
                moveDirectory(src, dst)
                if (!src.delete()) Log.i(LOG_TAG, "Failed to delete directory: " + src.path)
                continue
            }
            try {
                val dst = File(dstDir, src.name)
                moveFile(src, dst)
            } catch (e: IOException) {
                Log.e(LOG_TAG, "Error moving file " + src.path)
                Crashlytics.logException(e)
            }
        }
    }

    /**
     * Upgrade the database to version 14
     *
     *
     * This migration actually does not change anything in the database
     * It moves the backup files to a new backup location which does not require SD CARD write permission
     *
     * @param db SQLite database to be upgraded
     * @return New database version
     */
    fun upgradeDbToVersion14(db: SQLiteDatabase?): Int {
        Log.i(DatabaseHelper.LOG_TAG, "Upgrading database to version 14")
        val oldDbVersion = 13
        val backupFolder = File(Exporter.BASE_FOLDER_PATH)
        backupFolder.mkdir()
        Thread {
            val srcDir = File(Exporter.LEGACY_BASE_FOLDER_PATH)
            val dstDir = File(Exporter.BASE_FOLDER_PATH)
            try {
                moveDirectory(srcDir, dstDir)
                val readmeFile = File(Exporter.LEGACY_BASE_FOLDER_PATH, "README.txt")
                val writer: FileWriter?
                writer = FileWriter(readmeFile)
                writer.write(
                    """
    Backup files have been moved to ${dstDir.path}
    You can now delete this folder
    """.trimIndent()
                )
                writer.flush()
            } catch (ex: IOException) {
                ex.printStackTrace()
                val msg = String.format("Error moving files from %s to %s", srcDir.path, dstDir.path)
                Log.e(LOG_TAG, msg)
                Crashlytics.log(msg)
                Crashlytics.logException(ex)
            } catch (ex: IllegalArgumentException) {
                ex.printStackTrace()
                val msg = String.format("Error moving files from %s to %s", srcDir.path, dstDir.path)
                Log.e(LOG_TAG, msg)
                Crashlytics.log(msg)
                Crashlytics.logException(ex)
            }
        }.start()
        return 14
    }

    /**
     * Upgrades the database to version 14.
     *
     * This migration makes the following changes to the database:
     *
     *  * Fixes accounts referencing a default transfer account that no longer
     * exists (see #654)
     *
     *
     * @param db SQLite database to be upgraded
     * @return New database version, 14 if migration succeeds, 13 otherwise
     */
    fun upgradeDbToVersion15(db: SQLiteDatabase): Int {
        Log.i(DatabaseHelper.LOG_TAG, "Upgrading database to version 15")
        val dbVersion: Int
        db.beginTransaction()
        dbVersion = try {
            val contentValues = ContentValues()
            contentValues.putNull(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID)
            db.update(
                AccountEntry.TABLE_NAME,
                contentValues,
                AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID
                        + " NOT IN (SELECT " + AccountEntry.COLUMN_UID
                        + "             FROM " + AccountEntry.TABLE_NAME + ")",
                null
            )
            db.setTransactionSuccessful()
            15
        } finally {
            db.endTransaction()
        }

        //remove previously saved export destination index because the number of destinations has changed
        //an invalid value would lead to crash on start
        val context = GnuCashApplication.appContext
        android.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(context?.getString(R.string.key_last_export_destination))
            .apply()

        //the default interval has been changed from daily to hourly with this release. So reschedule alarm
        rescheduleServiceAlarm()
        return dbVersion
    }
}