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
package org.gnucash.android.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.crashlytics.android.Crashlytics
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema.BookEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.export.Exporter
import org.gnucash.android.model.BaseModel
import org.gnucash.android.model.Book
import org.gnucash.android.util.RecursiveMoveFiles
import java.io.File
import java.io.IOException

/**
 * Database helper for managing database which stores information about the books in the application
 * This is a different database from the one which contains the accounts and transaction data because
 * there are multiple accounts/transactions databases in the system and this one will be used to
 * switch between them.
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class BookDbHelper(private val mContext: Context) :
    SQLiteOpenHelper(mContext, DatabaseSchema.BOOK_DATABASE_NAME, null, DatabaseSchema.BOOK_DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(BOOKS_TABLE_CREATE)
        if (mContext.getDatabasePath(DatabaseSchema.LEGACY_DATABASE_NAME).exists()) {
            Log.d(LOG_TAG, "Legacy database found. Migrating to multibook format")
            val helper = DatabaseHelper(
                GnuCashApplication.appContext,
                DatabaseSchema.LEGACY_DATABASE_NAME
            )
            val mainDb = helper.writableDatabase
            val accountsDbAdapter = AccountsDbAdapter(
                mainDb,
                TransactionsDbAdapter(mainDb, SplitsDbAdapter(mainDb))
            )
            val rootAccountUID = accountsDbAdapter.orCreateGnuCashRootAccountUID
            val book = Book(rootAccountUID)
            book.setMActive(true)
            insertBook(db, book)
            val mainDbPath = mainDb.path
            helper.close()
            val src = File(mainDbPath)
            val dst = File(src.parent, book.mUID!!)
            try {
                MigrationHelper.moveFile(src, dst)
            } catch (e: IOException) {
                val err_msg = "Error renaming database file"
                Crashlytics.log(err_msg)
                Log.e(LOG_TAG, err_msg, e)
            }
            migrateBackupFiles(book.mUID)
        }
        val sql = "SELECT COUNT(*) FROM " + BookEntry.TABLE_NAME
        val statement = db.compileStatement(sql)
        val count = statement.simpleQueryForLong()
        if (count == 0L) { //no book in the database, create a default one
            Log.i(LOG_TAG, "No books found in database, creating default book")
            val book = Book()
            val helper = DatabaseHelper(GnuCashApplication.appContext, book.mUID)
            val mainDb = helper.writableDatabase //actually create the db
            val accountsDbAdapter = AccountsDbAdapter(
                mainDb,
                TransactionsDbAdapter(mainDb, SplitsDbAdapter(mainDb))
            )
            val rootAccountUID = accountsDbAdapter.orCreateGnuCashRootAccountUID
            book.mRootAccountUID = rootAccountUID
            book.setMActive(true)
            insertBook(db, book)
        }
    }

    /**
     * Inserts the book into the database
     * @param db Book database
     * @param book Book to insert
     */
    private fun insertBook(db: SQLiteDatabase, book: Book) {
        val contentValues = ContentValues()
        contentValues.put(BookEntry.COLUMN_UID, book.mUID)
        contentValues.put(BookEntry.COLUMN_ROOT_GUID, book.mRootAccountUID)
        contentValues.put(BookEntry.COLUMN_TEMPLATE_GUID, BaseModel.generateUID())
        contentValues.put(BookEntry.COLUMN_DISPLAY_NAME, BooksDbAdapter(db).generateDefaultBookName())
        contentValues.put(BookEntry.COLUMN_ACTIVE, if (book.isActive) 1 else 0)
        db.insert(BookEntry.TABLE_NAME, null, contentValues)
    }

    /**
     * Move the backup and export files from the old location (single-book) to the new multi-book
     * backup folder structure. Each book has its own directory as well as backups and exports.
     *
     * This method should be called only once during the initial migration to multi-book support
     * @param activeBookUID GUID of the book for which to migrate the files
     */
    private fun migrateBackupFiles(activeBookUID: String?) {
        Log.d(LOG_TAG, "Moving export and backup files to book-specific folders")
        val newBasePath = File(Exporter.LEGACY_BASE_FOLDER_PATH + "/" + activeBookUID)
        newBasePath.mkdirs()
        var src = File(Exporter.LEGACY_BASE_FOLDER_PATH + "/backups/")
        var dst = File(Exporter.LEGACY_BASE_FOLDER_PATH + "/" + activeBookUID + "/backups/")
        Thread(RecursiveMoveFiles(src, dst)).start()
        src = File(Exporter.LEGACY_BASE_FOLDER_PATH + "/exports/")
        dst = File(Exporter.LEGACY_BASE_FOLDER_PATH + "/" + activeBookUID + "/exports/")
        Thread(RecursiveMoveFiles(src, dst)).start()
        val nameFile = File(newBasePath, "Book 1")
        try {
            nameFile.createNewFile()
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Error creating name file for the database: " + nameFile.name)
            e.printStackTrace()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        //nothing to see here yet, move along
    }

    companion object {
        const val LOG_TAG = "BookDbHelper"

        /**
         * Create the books table
         */
        private val BOOKS_TABLE_CREATE = ("CREATE TABLE " + BookEntry.TABLE_NAME + " ("
                + BookEntry._ID + " integer primary key autoincrement, "
                + BookEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                + BookEntry.COLUMN_DISPLAY_NAME + " varchar(255) not null, "
                + BookEntry.COLUMN_ROOT_GUID + " varchar(255) not null, "
                + BookEntry.COLUMN_TEMPLATE_GUID + " varchar(255), "
                + BookEntry.COLUMN_ACTIVE + " tinyint default 0, "
                + BookEntry.COLUMN_SOURCE_URI + " varchar(255), "
                + BookEntry.COLUMN_LAST_SYNC + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + BookEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + BookEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP "
                + ");" + DatabaseHelper.createUpdatedAtTrigger(BookEntry.TABLE_NAME))

        /**
         * Returns the database for the book
         * @param bookUID GUID of the book
         * @return SQLiteDatabase of the book
         */
        @JvmStatic
        fun getDatabase(bookUID: String?): SQLiteDatabase {
            val dbHelper = DatabaseHelper(GnuCashApplication.appContext, bookUID)
            return dbHelper.writableDatabase
        }
    }
}