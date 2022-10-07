/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.db.adapter

import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQueryBuilder
import android.database.sqlite.SQLiteStatement
import android.text.TextUtils
import android.util.Log
import com.crashlytics.android.Crashlytics
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema.*
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Money
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.Transaction.Companion.computeBalance
import org.gnucash.android.util.TimestampHelper
import java.sql.Timestamp

/**
 * Manages persistence of [Transaction]s in the database
 * Handles adding, modifying and deleting of transaction records.
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Yongxin Wang <fefe.wyx></fefe.wyx>@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class TransactionsDbAdapter(db: SQLiteDatabase?, val splitDbAdapter: SplitsDbAdapter) : DatabaseAdapter<Transaction>(
    db!!, TransactionEntry.TABLE_NAME, arrayOf(
        TransactionEntry.COLUMN_DESCRIPTION,
        TransactionEntry.COLUMN_NOTES,
        TransactionEntry.COLUMN_TIMESTAMP,
        TransactionEntry.COLUMN_EXPORTED,
        TransactionEntry.COLUMN_CURRENCY,
        TransactionEntry.COLUMN_COMMODITY_UID,
        TransactionEntry.COLUMN_CREATED_AT,
        TransactionEntry.COLUMN_SCHEDX_ACTION_UID,
        TransactionEntry.COLUMN_TEMPLATE
    )
) {
    private val mCommoditiesDbAdapter: CommoditiesDbAdapter

    /**
     * Overloaded constructor. Creates adapter for already open db
     * @param db SQlite db instance
     */
    init {
        mCommoditiesDbAdapter = CommoditiesDbAdapter(db)
    }

    /**
     * Adds an transaction to the database.
     * If a transaction already exists in the database with the same unique ID,
     * then the record will just be updated instead
     * @param model [Transaction] to be inserted to database
     */
    override fun addRecord(model: Transaction, updateMethod: UpdateMethod) {
        Log.d(LOG_TAG, "Adding transaction to the db via " + updateMethod.name)
        mDb.beginTransaction()
        try {
            val imbalanceSplit = model.createAutoBalanceSplit()
            if (imbalanceSplit != null) {
                val imbalanceAccountUID = AccountsDbAdapter(mDb, this)
                    .getOrCreateImbalanceAccountUID(model.mCommodity!!)
                imbalanceSplit.mAccountUID = imbalanceAccountUID
            }
            super.addRecord(model, updateMethod)
            Log.d(LOG_TAG, "Adding splits for transaction")
            val splitUIDs = ArrayList<String?>(model.getMSplitList().size)
            for (split in model.getMSplitList()) {
                Log.d(LOG_TAG, "Replace transaction split in db")
                if (imbalanceSplit === split) {
                    splitDbAdapter.addRecord(split, UpdateMethod.insert)
                } else {
                    splitDbAdapter.addRecord(split, updateMethod)
                }
                splitUIDs.add(split.mUID)
            }
            Log.d(LOG_TAG, model.getMSplitList().size.toString() + " splits added")
            val deleted = mDb.delete(
                SplitEntry.TABLE_NAME,
                SplitEntry.COLUMN_TRANSACTION_UID + " = ? AND "
                        + SplitEntry.COLUMN_UID + " NOT IN ('" + TextUtils.join("' , '", splitUIDs) + "')",
                arrayOf(model.mUID)
            ).toLong()
            Log.d(LOG_TAG, "$deleted splits deleted")
            mDb.setTransactionSuccessful()
        } catch (sqlEx: SQLException) {
            Log.e(LOG_TAG, sqlEx.message!!)
            Crashlytics.logException(sqlEx)
        } finally {
            mDb.endTransaction()
        }
    }

    /**
     * Adds an several transactions to the database.
     * If a transaction already exists in the database with the same unique ID,
     * then the record will just be updated instead. Recurrence Transactions will not
     * be inserted, instead schedule Transaction would be called. If an exception
     * occurs, no transaction would be inserted.
     * @param modelList [Transaction] transactions to be inserted to database
     * @return Number of transactions inserted
     */
    override fun bulkAddRecords(modelList: List<Transaction>, updateMethod: UpdateMethod): Long {
        var start = System.nanoTime()
        val rowInserted = super.bulkAddRecords(modelList, updateMethod)
        val end = System.nanoTime()
        Log.d(javaClass.simpleName, String.format("bulk add transaction time %d ", end - start))
        val splitList: MutableList<Split> = ArrayList(modelList.size * 3)
        for (transaction in modelList) {
            splitList.addAll(transaction.getMSplitList())
        }
        if (rowInserted != 0L && splitList.isNotEmpty()) {
            try {
                start = System.nanoTime()
                val nSplits = splitDbAdapter.bulkAddRecords(splitList, updateMethod)
                Log.d(LOG_TAG, String.format("%d splits inserted in %d ns", nSplits, System.nanoTime() - start))
            } finally {
                val deleteEmptyTransaction = mDb.compileStatement(
                    "DELETE FROM " +
                            TransactionEntry.TABLE_NAME + " WHERE NOT EXISTS ( SELECT * FROM " +
                            SplitEntry.TABLE_NAME +
                            " WHERE " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID +
                            " = " + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + " ) "
                )
                deleteEmptyTransaction.execute()
            }
        }
        return rowInserted
    }

    override fun setBindings(stmt: SQLiteStatement, model: Transaction): SQLiteStatement {
        stmt.clearBindings()
        stmt.bindString(1, model.getMDescription())
        stmt.bindString(2, model.mNotes)
        stmt.bindLong(3, model.mTimestamp)
        stmt.bindLong(4, (if (model.mIsExported) 1 else 0).toLong())
        stmt.bindString(5, model.mMnemonic)
        stmt.bindString(6, model.mCommodity!!.mUID)
        stmt.bindString(7, TimestampHelper.getUtcStringFromTimestamp(model.mCreatedTimestamp))
        if (model.mScheduledActionUID == null) stmt.bindNull(8) else stmt.bindString(
            8,
            model.mScheduledActionUID
        )
        stmt.bindLong(9, (if (model.mIsTemplate) 1 else 0).toLong())
        stmt.bindString(10, model.mUID)
        return stmt
    }

    /**
     * Returns a cursor to a set of all transactions which have a split belonging to the accound with unique ID
     * `accountUID`.
     * @param accountUID UID of the account whose transactions are to be retrieved
     * @return Cursor holding set of transactions for particular account
     * @throws java.lang.IllegalArgumentException if the accountUID is null
     */
    fun fetchAllTransactionsForAccount(accountUID: String): Cursor {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.tables = (TransactionEntry.TABLE_NAME
                + " INNER JOIN " + SplitEntry.TABLE_NAME + " ON "
                + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
                + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID)
        queryBuilder.isDistinct = true
        val projectionIn = arrayOf(TransactionEntry.TABLE_NAME + ".*")
        val selection = (SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
                + " AND " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " = 0")
        val selectionArgs = arrayOf(accountUID)
        val sortOrder = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " DESC"
        return queryBuilder.query(mDb, projectionIn, selection, selectionArgs, null, null, sortOrder)
    }

    /**
     * Returns a cursor to all scheduled transactions which have at least one split in the account
     *
     * This is basically a set of all template transactions for this account
     * @param accountUID GUID of account
     * @return Cursor with set of transactions
     */
    fun fetchScheduledTransactionsForAccount(accountUID: String): Cursor {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.tables = (TransactionEntry.TABLE_NAME
                + " INNER JOIN " + SplitEntry.TABLE_NAME + " ON "
                + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
                + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID)
        queryBuilder.isDistinct = true
        val projectionIn = arrayOf(TransactionEntry.TABLE_NAME + ".*")
        val selection = (SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
                + " AND " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " = 1")
        val selectionArgs = arrayOf(accountUID)
        val sortOrder = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " DESC"
        return queryBuilder.query(mDb, projectionIn, selection, selectionArgs, null, null, sortOrder)
    }

    /**
     * Deletes all transactions which contain a split in the account.
     *
     * **Note:**As long as the transaction has one split which belongs to the account `accountUID`,
     * it will be deleted. The other splits belonging to the transaction will also go away
     * @param accountUID GUID of the account
     */
    fun deleteTransactionsForAccount(accountUID: String) {
        val rawDeleteQuery =
            ("DELETE FROM " + TransactionEntry.TABLE_NAME + " WHERE " + TransactionEntry.COLUMN_UID + " IN "
                    + " (SELECT " + SplitEntry.COLUMN_TRANSACTION_UID + " FROM " + SplitEntry.TABLE_NAME + " WHERE "
                    + SplitEntry.COLUMN_ACCOUNT_UID + " = ?)")
        mDb.execSQL(rawDeleteQuery, arrayOf(accountUID))
    }

    /**
     * Deletes all transactions which have no splits associated with them
     * @return Number of records deleted
     */
    fun deleteTransactionsWithNoSplits(): Int {
        return mDb.delete(
            TransactionEntry.TABLE_NAME,
            "NOT EXISTS ( SELECT * FROM " + SplitEntry.TABLE_NAME +
                    " WHERE " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID +
                    " = " + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + " ) ",
            null
        )
    }

    /**
     * Fetches all recurring transactions from the database.
     *
     * Recurring transactions are the transaction templates which have an entry in the scheduled events table
     * @return Cursor holding set of all recurring transactions
     */
    fun fetchAllScheduledTransactions(): Cursor {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.tables = (TransactionEntry.TABLE_NAME + " INNER JOIN " + ScheduledActionEntry.TABLE_NAME + " ON "
                + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
                + ScheduledActionEntry.TABLE_NAME + "." + ScheduledActionEntry.COLUMN_ACTION_UID)
        val projectionIn = arrayOf(
            TransactionEntry.TABLE_NAME + ".*",
            ScheduledActionEntry.TABLE_NAME + "." + ScheduledActionEntry.COLUMN_UID + " AS " + "origin_scheduled_action_uid"
        )
        val sortOrder = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_DESCRIPTION + " ASC"
        //        queryBuilder.setDistinct(true);
        return queryBuilder.query(mDb, projectionIn, null, null, null, null, sortOrder)
    }

    /**
     * Returns list of all transactions for account with UID `accountUID`
     * @param accountUID UID of account whose transactions are to be retrieved
     * @return List of [Transaction]s for account with UID `accountUID`
     */
    fun getAllTransactionsForAccount(accountUID: String): List<Transaction> {
        val c = fetchAllTransactionsForAccount(accountUID)
        val transactionsList = ArrayList<Transaction>()
        try {
            while (c.moveToNext()) {
                transactionsList.add(buildModelInstance(c))
            }
        } finally {
            c.close()
        }
        return transactionsList
    }

    /**
     * Returns all transaction instances in the database.
     * @return List of all transactions
     */
    val allTransactions: List<Transaction>
        get() {
            val cursor = fetchAllRecords()
            val transactions: MutableList<Transaction> = ArrayList()
            try {
                while (cursor.moveToNext()) {
                    transactions.add(buildModelInstance(cursor))
                }
            } finally {
                cursor.close()
            }
            return transactions
        }

    fun fetchTransactionsWithSplits(
        columns: Array<String?>?,
        where: String?,
        whereArgs: Array<String?>?,
        orderBy: String?
    ): Cursor {
        return mDb.query(
            TransactionEntry.TABLE_NAME + " , " + SplitEntry.TABLE_NAME +
                    " ON " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID +
                    " = " + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID +
                    " , trans_extra_info ON trans_extra_info.trans_acct_t_uid = " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID,
            columns, where, whereArgs, null, null,
            orderBy
        )
    }

    /**
     * Fetch all transactions modified since a given timestamp
     * @param timestamp Timestamp in milliseconds (since Epoch)
     * @return Cursor to the results
     */
    fun fetchTransactionsModifiedSince(timestamp: Timestamp): Cursor {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.tables = TransactionEntry.TABLE_NAME
        val startTimeString = TimestampHelper.getUtcStringFromTimestamp(timestamp)
        return queryBuilder.query(
            mDb, null, TransactionEntry.COLUMN_MODIFIED_AT + " >= \"" + startTimeString + "\"",
            null, null, null, TransactionEntry.COLUMN_TIMESTAMP + " ASC", null
        )
    }

    fun fetchTransactionsWithSplitsWithTransactionAccount(
        columns: Array<String?>?,
        where: String?,
        whereArgs: Array<String?>?,
        orderBy: String?
    ): Cursor {
        // table is :
        // trans_split_acct , trans_extra_info ON trans_extra_info.trans_acct_t_uid = transactions_uid ,
        // accounts AS account1 ON account1.uid = trans_extra_info.trans_acct_a_uid
        //
        // views effectively simplified this query
        //
        // account1 provides information for the grouped account. Splits from the grouped account
        // can be eliminated with a WHERE clause. Transactions in QIF can be auto balanced.
        //
        // Account, transaction and split Information can be retrieve in a single query.
        return mDb.query(
            "trans_split_acct , trans_extra_info ON trans_extra_info.trans_acct_t_uid = trans_split_acct." +
                    TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID + " , " +
                    AccountEntry.TABLE_NAME + " AS account1 ON account1." + AccountEntry.COLUMN_UID +
                    " = trans_extra_info.trans_acct_a_uid",
            columns, where, whereArgs, null, null, orderBy
        )
    }

    /**
     * Return number of transactions in the database (excluding templates)
     * @return Number of transactions
     */
    override val recordsCount: Long
        get() {
            val queryCount = "SELECT COUNT(*) FROM " + TransactionEntry.TABLE_NAME +
                    " WHERE " + TransactionEntry.COLUMN_TEMPLATE + " =0"
            val cursor = mDb.rawQuery(queryCount, null)
            return try {
                cursor.moveToFirst()
                cursor.getLong(0)
            } finally {
                cursor.close()
            }
        }

    /**
     * Returns the number of transactions in the database which fulfill the conditions
     * @param where SQL WHERE clause without the "WHERE" itself
     * @param whereArgs Arguments to substitute question marks for
     * @return Number of records in the databases
     */
    fun getRecordsCount(where: String?, whereArgs: Array<String?>?): Long {
        val cursor = mDb.query(
            true, TransactionEntry.TABLE_NAME + " , trans_extra_info ON "
                    + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID
                    + " = trans_extra_info.trans_acct_t_uid", arrayOf("COUNT(*)"),
            where,
            whereArgs,
            null,
            null,
            null,
            null
        )
        return try {
            cursor.moveToFirst()
            cursor.getLong(0)
        } finally {
            cursor.close()
        }
    }

    /**
     * Builds a transaction instance with the provided cursor.
     * The cursor should already be pointing to the transaction record in the database
     * @param cursor Cursor pointing to transaction record in database
     * @return [Transaction] object constructed from database record
     */
    override fun buildModelInstance(cursor: Cursor): Transaction {
        val name = cursor.getString(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_DESCRIPTION))
        val transaction = Transaction(name)
        populateBaseModelAttributes(cursor, transaction)
        transaction.setMTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_TIMESTAMP)))
        transaction.mNotes = cursor.getString(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_NOTES))
        transaction.mIsExported = cursor.getInt(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_EXPORTED)) == 1
        transaction.mIsTemplate = cursor.getInt(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_TEMPLATE)) == 1
        val currencyCode = cursor.getString(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_CURRENCY))
        transaction.mCommodity = mCommoditiesDbAdapter.getCommodity(currencyCode)
        transaction.mScheduledActionUID =
            cursor.getString(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_SCHEDX_ACTION_UID))
        val transactionID = cursor.getLong(cursor.getColumnIndexOrThrow(TransactionEntry._ID))
        transaction.setMSplitList(splitDbAdapter.getSplitsForTransaction(transactionID).toMutableList())
        return transaction
    }

    /**
     * Returns the transaction balance for the transaction for the specified account.
     *
     * We consider only those splits which belong to this account
     * @param transactionUID GUID of the transaction
     * @param accountUID GUID of the account
     * @return [org.gnucash.android.model.Money] balance of the transaction for that account
     */
    fun getBalance(transactionUID: String?, accountUID: String?): Money {
        val splitList = splitDbAdapter.getSplitsForTransactionInAccount(
            transactionUID, accountUID
        )
        return computeBalance(accountUID!!, splitList)
    }

    /**
     * Assigns transaction with id `rowId` to account with id `accountId`
     * @param transactionUID GUID of the transaction
     * @param srcAccountUID GUID of the account from which the transaction is to be moved
     * @param dstAccountUID GUID of the account to which the transaction will be assigned
     * @return Number of transactions splits affected
     */
    fun moveTransaction(transactionUID: String, srcAccountUID: String, dstAccountUID: String): Int {
        Log.i(
            LOG_TAG, "Moving transaction ID " + transactionUID
                    + " splits from " + srcAccountUID + " to account " + dstAccountUID
        )
        val splits = splitDbAdapter.getSplitsForTransactionInAccount(transactionUID, srcAccountUID)
        for (split in splits) {
            split.mAccountUID = dstAccountUID
        }
        splitDbAdapter.bulkAddRecords(splits, UpdateMethod.update)
        return splits.size
    }

    /**
     * Returns the number of transactions belonging to an account
     * @param accountUID GUID of the account
     * @return Number of transactions with splits in the account
     */
    fun getTransactionsCount(accountUID: String): Int {
        val cursor = fetchAllTransactionsForAccount(accountUID)
        val count: Int = cursor.count
        cursor.close()
        return count
    }

    /**
     * Returns the number of template transactions in the database
     * @return Number of template transactions
     */
    val templateTransactionsCount: Long
        get() {
            val sql = ("SELECT COUNT(*) FROM " + TransactionEntry.TABLE_NAME
                    + " WHERE " + TransactionEntry.COLUMN_TEMPLATE + "=1")
            val statement = mDb.compileStatement(sql)
            return statement.simpleQueryForLong()
        }

    /**
     * Returns a list of all scheduled transactions in the database
     * @return List of all scheduled transactions
     */
    fun getScheduledTransactionsForAccount(accountUID: String): List<Transaction> {
        val cursor = fetchScheduledTransactionsForAccount(accountUID)
        val scheduledTransactions: MutableList<Transaction> = ArrayList()
        return try {
            while (cursor.moveToNext()) {
                scheduledTransactions.add(buildModelInstance(cursor))
            }
            scheduledTransactions
        } finally {
            cursor.close()
        }
    }

    /**
     * Returns the number of splits for the transaction in the database
     * @param transactionUID GUID of the transaction
     * @return Number of splits belonging to the transaction
     */
    fun getSplitCount(transactionUID: String): Long {
        val sql = ("SELECT COUNT(*) FROM " + SplitEntry.TABLE_NAME
                + " WHERE " + SplitEntry.COLUMN_TRANSACTION_UID + "= '" + transactionUID + "'")
        val statement = mDb.compileStatement(sql)
        return statement.simpleQueryForLong()
    }

    /**
     * Returns a cursor to transactions whose name (UI: description) start with the `prefix`
     *
     * This method is used for autocomplete suggestions when creating new transactions. <br></br>
     * The suggestions are either transactions which have at least one split with `accountUID` or templates.
     * @param prefix Starting characters of the transaction name
     * @param accountUID GUID of account within which to search for transactions
     * @return Cursor to the data set containing all matching transactions
     */
    fun fetchTransactionSuggestions(prefix: String, accountUID: String): Cursor {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.tables = (TransactionEntry.TABLE_NAME
                + " INNER JOIN " + SplitEntry.TABLE_NAME + " ON "
                + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
                + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID)
        queryBuilder.isDistinct = true
        val projectionIn = arrayOf(TransactionEntry.TABLE_NAME + ".*")
        val selection = ("(" + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
                + " OR " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + "=1 )"
                + " AND " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_DESCRIPTION + " LIKE '" + prefix + "%'")
        val selectionArgs = arrayOf(accountUID)
        val sortOrder = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " DESC"
        val groupBy = TransactionEntry.COLUMN_DESCRIPTION
        val limit = 5.toString()
        return queryBuilder.query(mDb, projectionIn, selection, selectionArgs, groupBy, null, sortOrder, limit)
    }

    /**
     * Updates a specific entry of an transaction
     * @param contentValues Values with which to update the record
     * @param whereClause Conditions for updating formatted as SQL where statement
     * @param whereArgs Arguments for the SQL wehere statement
     * @return Number of records affected
     */
    fun updateTransaction(contentValues: ContentValues?, whereClause: String?, whereArgs: Array<String?>?): Int {
        return mDb.update(TransactionEntry.TABLE_NAME, contentValues, whereClause, whereArgs)
    }

    /**
     * Return the number of currencies used in the transaction.
     * For example if there are different splits with different currencies
     * @param transactionUID GUID of the transaction
     * @return Number of currencies within the transaction
     */
    fun getNumCurrencies(transactionUID: String): Int {
        val cursor = mDb.query(
            "trans_extra_info", arrayOf("trans_currency_count"),
            "trans_acct_t_uid=?", arrayOf(transactionUID),
            null, null, null
        )
        var numCurrencies = 0
        try {
            if (cursor.moveToFirst()) {
                numCurrencies = cursor.getInt(0)
            }
        } finally {
            cursor.close()
        }
        return numCurrencies
    }

    /**
     * Deletes all transactions except those which are marked as templates.
     *
     * If you want to delete really all transaction records, use [.deleteAllRecords]
     * @return Number of records deleted
     */
    fun deleteAllNonTemplateTransactions(): Int {
        val where = TransactionEntry.COLUMN_TEMPLATE + "=0"
        return mDb.delete(mTableName, where, null)
    }

    /**
     * Returns a timestamp of the earliest transaction for a specified account type and currency
     * @param type the account type
     * @param currencyCode the currency code
     * @return the earliest transaction's timestamp. Returns 1970-01-01 00:00:00.000 if no transaction found
     */
    fun getTimestampOfEarliestTransaction(type: AccountType, currencyCode: String): Long {
        return getTimestamp("MIN", type, currencyCode)
    }

    /**
     * Returns a timestamp of the latest transaction for a specified account type and currency
     * @param type the account type
     * @param currencyCode the currency code
     * @return the latest transaction's timestamp. Returns 1970-01-01 00:00:00.000 if no transaction found
     */
    fun getTimestampOfLatestTransaction(type: AccountType, currencyCode: String): Long {
        return getTimestamp("MAX", type, currencyCode)
    }//in case there were no transactions in the XML file (account structure only)

    /**
     * Returns the most recent `modified_at` timestamp of non-template transactions in the database
     * @return Last moodified time in milliseconds or current time if there is none in the database
     */
    val timestampOfLastModification: Timestamp
        get() {
            val cursor = mDb.query(
                TransactionEntry.TABLE_NAME, arrayOf("MAX(" + TransactionEntry.COLUMN_MODIFIED_AT + ")"),
                null, null, null, null, null
            )
            var timestamp = TimestampHelper.timestampFromNow
            if (cursor.moveToFirst()) {
                val timeString = cursor.getString(0)
                if (timeString != null) { //in case there were no transactions in the XML file (account structure only)
                    timestamp = TimestampHelper.getTimestampFromUtcString(timeString)
                }
            }
            cursor.close()
            return timestamp
        }

    /**
     * Returns the earliest or latest timestamp of transactions for a specific account type and currency
     * @param mod Mode (either MAX or MIN)
     * @param type AccountType
     * @param currencyCode the currency code
     * @return earliest or latest timestamp of transactions
     * @see .getTimestampOfLatestTransaction
     * @see .getTimestampOfEarliestTransaction
     */
    private fun getTimestamp(mod: String, type: AccountType, currencyCode: String): Long {
        val sql = ("SELECT " + mod + "(" + TransactionEntry.COLUMN_TIMESTAMP + ")"
                + " FROM " + TransactionEntry.TABLE_NAME
                + " INNER JOIN " + SplitEntry.TABLE_NAME + " ON "
                + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + " = "
                + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID
                + " INNER JOIN " + AccountEntry.TABLE_NAME + " ON "
                + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID + " = "
                + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID
                + " WHERE " + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_TYPE + " = ? AND "
                + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_CURRENCY + " = ? AND "
                + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " = 0")
        val cursor = mDb.rawQuery(sql, arrayOf(type.name, currencyCode))
        var timestamp: Long = 0
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                timestamp = cursor.getLong(0)
            }
            cursor.close()
        }
        return timestamp
    }

    companion object {
        /**
         * Returns an application-wide instance of the database adapter
         * @return Transaction database adapter
         */
        @JvmStatic
        val instance: TransactionsDbAdapter
            get() = GnuCashApplication.transactionDbAdapter!!
    }
}