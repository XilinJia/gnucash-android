/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.db.adapter

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.net.Uri
import android.util.Log
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseSchema.BookEntry
import org.gnucash.android.model.Book
import org.gnucash.android.ui.settings.PreferenceActivity
import org.gnucash.android.util.TimestampHelper

/**
 * Database adapter for creating/modifying book entries
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class BooksDbAdapter
/**
 * Opens the database adapter with an existing database
 * @param db        SQLiteDatabase object
 */
    (db: SQLiteDatabase?) : DatabaseAdapter<Book>(
    db!!, BookEntry.TABLE_NAME, arrayOf(
        BookEntry.COLUMN_DISPLAY_NAME,
        BookEntry.COLUMN_ROOT_GUID,
        BookEntry.COLUMN_TEMPLATE_GUID,
        BookEntry.COLUMN_SOURCE_URI,
        BookEntry.COLUMN_ACTIVE,
        BookEntry.COLUMN_UID,
        BookEntry.COLUMN_LAST_SYNC
    )
) {
    override fun buildModelInstance(cursor: Cursor): Book {
        val rootAccountGUID = cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_ROOT_GUID))
        val rootTemplateGUID = cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_TEMPLATE_GUID))
        val uriString = cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_SOURCE_URI))
        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_DISPLAY_NAME))
        val active = cursor.getInt(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_ACTIVE))
        val lastSync = cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_LAST_SYNC))
        val book = Book(rootAccountGUID)
        book.mDisplayName = displayName
        book.mRootTemplateUID = rootTemplateGUID
        book.mSourceUri = if (uriString == null) null else Uri.parse(uriString)
        book.setMActive(active > 0)
        book.mLastSync = TimestampHelper.getTimestampFromUtcString(lastSync)
        populateBaseModelAttributes(cursor, book)
        return book
    }

    override fun setBindings(stmt: SQLiteStatement, model: Book): SQLiteStatement {
        stmt.clearBindings()
        val displayName = if (model.mDisplayName == null) generateDefaultBookName() else model.mDisplayName!!
        stmt.bindString(1, displayName)
        stmt.bindString(2, model.mRootAccountUID)
        stmt.bindString(3, model.mRootTemplateUID)
        if (model.mSourceUri != null) stmt.bindString(4, model.mSourceUri.toString())
        stmt.bindLong(5, if (model.isActive) 1L else 0L)
        stmt.bindString(6, model.mUID)
        stmt.bindString(7, TimestampHelper.getUtcStringFromTimestamp(model.mLastSync!!))
        return stmt
    }

    /**
     * Deletes a book - removes the book record from the database and deletes the database file from the disk
     * @param bookUID GUID of the book
     * @return `true` if deletion was successful, `false` otherwise
     * @see .deleteRecord
     */
    fun deleteBook(bookUID: String): Boolean {
        val context = GnuCashApplication.appContext
        var result = context!!.deleteDatabase(bookUID)
        if (result) //delete the db entry only if the file deletion was successful
            result = result and deleteRecord(bookUID)
        PreferenceActivity.getBookSharedPreferences(bookUID).edit().clear().apply()
        return result
    }

    /**
     * Sets the book with unique identifier `uid` as active and all others as inactive
     *
     * If the parameter is null, then the currently active book is not changed
     * @param bookUID Unique identifier of the book
     * @return GUID of the currently active book
     */
    fun setActive(bookUID: String): String {
        val contentValues = ContentValues()
        contentValues.put(BookEntry.COLUMN_ACTIVE, 0)
        mDb.update(mTableName, contentValues, null, null) //disable all
        contentValues.clear()
        contentValues.put(BookEntry.COLUMN_ACTIVE, 1)
        mDb.update(mTableName, contentValues, BookEntry.COLUMN_UID + " = ?", arrayOf(bookUID))
        return bookUID
    }

    /**
     * Checks if the book is active or not
     * @param bookUID GUID of the book
     * @return `true` if the book is active, `false` otherwise
     */
    fun isActive(bookUID: String?): Boolean {
        val isActive = getAttribute(bookUID!!, BookEntry.COLUMN_ACTIVE)
        return isActive.toInt() > 0
    }

    /**
     * Returns the GUID of the current active book
     * @return GUID of the active book
     */
    val activeBookUID: String
        get() {
            mDb.query(
                mTableName, arrayOf(BookEntry.COLUMN_UID),
                BookEntry.COLUMN_ACTIVE + "= 1",
                null,
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (cursor.count == 0) {
                    val e = NoActiveBookFoundException(
                        """
                     There is no active book in the app.This should NEVER happen, fix your bugs!
                     $noActiveBookFoundExceptionInfo
                     """.trimIndent()
                    )
                    e.printStackTrace()
                    throw e
                }
                cursor.moveToFirst()
                return cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_UID))
            }
        }
    private val noActiveBookFoundExceptionInfo: String
        get() {
            val info = StringBuilder("UID, created, source\n")
            for (book in allRecords) {
                info.append(
                    String.format(
                        "%s, %s, %s\n",
                        book.mUID,
                        book.mCreatedTimestamp,
                        book.mSourceUri
                    )
                )
            }
            return info.toString()
        }

    inner class NoActiveBookFoundException(message: String?) : RuntimeException(message)

    /** Tries to fix the books database.  */
    fun fixBooksDatabase() {
        Log.w(LOG_TAG, "Looking for books to set as active...")
        if (recordsCount <= 0) {
            Log.w(LOG_TAG, "No books found in the database. Recovering books records...")
            recoverBookRecords()
        }
        setFirstBookAsActive()
    }

    /**
     * Restores the records in the book database.
     *
     * Does so by looking for database files from books.
     */
    private fun recoverBookRecords() {
        for (dbName in bookDatabases) {
            val book = Book(getRootAccountUID(dbName))
            book.mUID = dbName
            book.mDisplayName = generateDefaultBookName()
            addRecord(book)
            Log.w(LOG_TAG, "Recovered book record: " + book.mUID)
        }
    }

    /**
     * Returns the root account UID from the database with name dbName.
     */
    private fun getRootAccountUID(dbName: String): String? {
        val context = GnuCashApplication.appContext
        val databaseHelper = DatabaseHelper(context, dbName)
        val db = databaseHelper.readableDatabase
        val accountsDbAdapter = AccountsDbAdapter(
            db,
            TransactionsDbAdapter(db, SplitsDbAdapter(db))
        )
        val uid = accountsDbAdapter.orCreateGnuCashRootAccountUID
        db.close()
        return uid
    }

    /**
     * Sets the first book in the database as active.
     */
    private fun setFirstBookAsActive() {
        val firstBook = allRecords[0]
        firstBook.setMActive(true)
        addRecord(firstBook)
        Log.w(LOG_TAG, "Book " + firstBook.mUID + " set as active.")
    }

    /**
     * Returns a list of database names corresponding to book databases.
     */
    private val bookDatabases: List<String>
        get() {
            val bookDatabases: MutableList<String> = ArrayList()
            for (database in GnuCashApplication.appContext!!.databaseList()) {
                if (isBookDatabase(database)) {
                    bookDatabases.add(database)
                }
            }
            return bookDatabases
        }

    private fun isBookDatabase(databaseName: String): Boolean {
        return databaseName.matches("[a-z0-9]{32}".toRegex()) // UID regex
    }

    val allBookUIDs: List<String>
        get() {
            val bookUIDs: MutableList<String> = ArrayList()
            mDb.query(
                true, mTableName, arrayOf(BookEntry.COLUMN_UID),
                null, null, null, null, null, null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    bookUIDs.add(cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_UID)))
                }
            }
            return bookUIDs
        }

    /**
     * Return the name of the currently active book.
     * Or a generic name if there is no active book (should never happen)
     * @return Display name of the book
     */
    val activeBookDisplayName: String
        get() {
            val cursor = mDb.query(
                mTableName, arrayOf(BookEntry.COLUMN_DISPLAY_NAME), BookEntry.COLUMN_ACTIVE + " = 1",
                null, null, null, null
            )
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_DISPLAY_NAME))
                }
            } finally {
                cursor.close()
            }
            return "Book1"
        }

    /**
     * Generates a new default name for a new book
     * @return String with default name
     */
    fun generateDefaultBookName(): String {
        var bookCount = recordsCount + 1
        val sql = "SELECT COUNT(*) FROM " + mTableName + " WHERE " + BookEntry.COLUMN_DISPLAY_NAME + " = ?"
        val statement = mDb.compileStatement(sql)
        while (true) {
            val context = GnuCashApplication.appContext
            val name = context!!.getString(R.string.book_default_name, bookCount)
            //String name = "Book" + " " + bookCount;
            statement.clearBindings()
            statement.bindString(1, name)
            val nameCount = statement.simpleQueryForLong()
            if (nameCount == 0L) {
                return name
            }
            bookCount++
        }
    }

    companion object {
        /**
         * Return the application instance of the books database adapter
         * @return Books database adapter
         */
        @JvmStatic
        val instance: BooksDbAdapter
            get() = GnuCashApplication.booksDbAdapter!!
    }
}