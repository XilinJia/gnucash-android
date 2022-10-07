/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.text.TextUtils
import android.util.Log
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.DatabaseSchema.*
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.BaseModel
import org.gnucash.android.util.TimestampHelper

/**
 * Adapter to be used for creating and opening the database for read/write operations.
 * The adapter abstracts several methods for database access and should be subclassed
 * by any other adapters to database-backed data models.
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
abstract class DatabaseAdapter<Model : BaseModel>(
    /**
     * SQLite database
     */
    protected val mDb: SQLiteDatabase, protected val mTableName: String, protected val mColumns: Array<String>
) {
    /**
     * Tag for logging
     */
    @JvmField
    protected var LOG_TAG = "DatabaseAdapter"

    @Volatile
    protected var mReplaceStatement: SQLiteStatement? = null

    @Volatile
    protected var mUpdateStatement: SQLiteStatement? = null

    @Volatile
    protected var mInsertStatement: SQLiteStatement? = null

    enum class UpdateMethod {
        insert, update, replace
    }

    /**
     * Opens the database adapter with an existing database
     * @param db SQLiteDatabase object
     */
    init {
        require(!(!mDb.isOpen || mDb.isReadOnly)) { "Database not open or is read-only. Require writeable database" }
        if (mDb.version >= 9) {
            createTempView()
        }
        LOG_TAG = javaClass.simpleName
    }

    private fun createTempView() {
        //the multiplication by 1.0 is to cause sqlite to handle the value as REAL and not to round off

        // Create some temporary views. Temporary views only exists in one DB session, and will not
        // be saved in the DB
        //
        // TODO: Useful views should be add to the DB
        //
        // create a temporary view, combining accounts, transactions and splits, as this is often used
        // in the queries

        //todo: would it be useful to add the split reconciled_state and reconciled_date to this view?
        mDb.execSQL(
            "CREATE TEMP VIEW IF NOT EXISTS trans_split_acct AS SELECT "
                    + TransactionEntry.TABLE_NAME + "." + CommonColumns.COLUMN_MODIFIED_AT + " AS "
                    + TransactionEntry.TABLE_NAME + "_" + CommonColumns.COLUMN_MODIFIED_AT + " , "
                    + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " AS "
                    + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID + " , "
                    + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_DESCRIPTION + " AS "
                    + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_DESCRIPTION + " , "
                    + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_NOTES + " AS "
                    + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_NOTES + " , "
                    + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_CURRENCY + " AS "
                    + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_CURRENCY + " , "
                    + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " AS "
                    + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TIMESTAMP + " , "
                    + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_EXPORTED + " AS "
                    + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_EXPORTED + " , "
                    + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " AS "
                    + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TEMPLATE + " , "
                    + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_UID + " AS "
                    + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_UID + " , "
                    + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TYPE + " AS "
                    + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_TYPE + " , "
                    + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_VALUE_NUM + " AS "
                    + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_VALUE_NUM + " , "
                    + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_VALUE_DENOM + " AS "
                    + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_VALUE_DENOM + " , "
                    + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_QUANTITY_NUM + " AS "
                    + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_QUANTITY_NUM + " , "
                    + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_QUANTITY_DENOM + " AS "
                    + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_QUANTITY_DENOM + " , "
                    + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_MEMO + " AS "
                    + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_MEMO + " , "
                    + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID + " AS "
                    + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_UID + " , "
                    + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_NAME + " AS "
                    + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_NAME + " , "
                    + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_CURRENCY + " AS "
                    + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_CURRENCY + " , "
                    + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " AS "
                    + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " , "
                    + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_PLACEHOLDER + " AS "
                    + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_PLACEHOLDER + " , "
                    + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_COLOR_CODE + " AS "
                    + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_COLOR_CODE + " , "
                    + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_FAVORITE + " AS "
                    + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_FAVORITE + " , "
                    + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_FULL_NAME + " AS "
                    + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_FULL_NAME + " , "
                    + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_TYPE + " AS "
                    + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_TYPE + " , "
                    + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + " AS "
                    + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID
                    + " FROM " + TransactionEntry.TABLE_NAME + " , " + SplitEntry.TABLE_NAME + " ON "
                    + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + "=" + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID
                    + " , " + AccountEntry.TABLE_NAME + " ON "
                    + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + "=" + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID
        )

        // SELECT transactions_uid AS trans_acct_t_uid ,
        //      SUBSTR (
        //          MIN (
        //              ( CASE WHEN IFNULL ( splits_memo , '' ) == '' THEN 'a' ELSE 'b' END ) || accounts_uid
        //          ) ,
        //          2
        //      ) AS trans_acct_a_uid ,
        //   TOTAL ( CASE WHEN splits_type = 'DEBIT' THEN splits_value_num
        //                ELSE - splits_value_num END ) * 1.0 / splits_value_denom AS trans_acct_balance ,
        //   COUNT ( DISTINCT accounts_currency_code ) AS trans_currency_count ,
        //   COUNT (*) AS trans_split_count
        //   FROM trans_split_acct GROUP BY transactions_uid
        //
        // This temporary view would pick one Account_UID for each
        // Transaction, which can be used to order all transactions. If possible, account_uid of a split whose
        // memo is null is select.
        //
        // Transaction balance is also picked out by this view
        //
        // a split without split memo is chosen if possible, in the following manner:
        //   if the splits memo is null or empty string, attach an 'a' in front of the split account uid,
        //   if not, attach a 'b' to the split account uid
        //   pick the minimal value of the modified account uid (one of the ones begins with 'a', if exists)
        //   use substr to get account uid
        mDb.execSQL(
            "CREATE TEMP VIEW IF NOT EXISTS trans_extra_info AS SELECT " + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID +
                    " AS trans_acct_t_uid , SUBSTR ( MIN ( ( CASE WHEN IFNULL ( " + SplitEntry.TABLE_NAME + "_" +
                    SplitEntry.COLUMN_MEMO + " , '' ) == '' THEN 'a' ELSE 'b' END ) || " +
                    AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_UID +
                    " ) , 2 ) AS trans_acct_a_uid , TOTAL ( CASE WHEN " + SplitEntry.TABLE_NAME + "_" +
                    SplitEntry.COLUMN_TYPE + " = 'DEBIT' THEN " + SplitEntry.TABLE_NAME + "_" +
                    SplitEntry.COLUMN_VALUE_NUM + " ELSE - " + SplitEntry.TABLE_NAME + "_" +
                    SplitEntry.COLUMN_VALUE_NUM + " END ) * 1.0 / " + SplitEntry.TABLE_NAME + "_" +
                    SplitEntry.COLUMN_VALUE_DENOM + " AS trans_acct_balance , COUNT ( DISTINCT " +
                    AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_CURRENCY +
                    " ) AS trans_currency_count , COUNT (*) AS trans_split_count FROM trans_split_acct " +
                    " GROUP BY " + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID
        )
    }

    /**
     * Checks if the database is open
     * @return `true` if the database is open, `false` otherwise
     */
    val isOpen: Boolean
        get() = mDb.isOpen

    /**
     * Adds a record to the database with the data contained in the model.
     *
     * This method uses the SQL REPLACE instructions to replace any record with a matching GUID.
     * So beware of any foreign keys with cascade dependencies which might need to be re-added
     * @param model Model to be saved to the database
     */
    fun addRecord(model: Model) {
        addRecord(model, UpdateMethod.replace)
    }

    /**
     * Add a model record to the database.
     *
     * If unsure about which `updateMethod` to use, use [UpdateMethod.replace]
     * @param model Subclass of [BaseModel] to be added
     * @param updateMethod Method to use for adding the record
     */
    open fun addRecord(model: Model, updateMethod: UpdateMethod) {
        Log.d(LOG_TAG, String.format("Adding %s record to database: ", model::class.java.simpleName))
        when (updateMethod) {
            UpdateMethod.insert -> synchronized(insertStatement) { setBindings(insertStatement, model).execute() }
            UpdateMethod.update -> synchronized(updateStatement) { setBindings(updateStatement, model).execute() }
            else -> synchronized(replaceStatement) { setBindings(replaceStatement, model).execute() }
        }
    }

    /**
     * Persist the model object to the database as records using the `updateMethod`
     * @param modelList List of records
     * @param updateMethod Method to use when persisting them
     * @return Number of rows affected in the database
     */
    private fun doAddModels(modelList: List<Model>, updateMethod: UpdateMethod): Long {
        var nRow: Long = 0
        when (updateMethod) {
            UpdateMethod.update -> synchronized(updateStatement) {
                for (model in modelList) {
                    setBindings(updateStatement, model).execute()
                    nRow++
                }
            }

            UpdateMethod.insert -> synchronized(insertStatement) {
                for (model in modelList) {
                    setBindings(insertStatement, model).execute()
                    nRow++
                }
            }

            else -> synchronized(replaceStatement) {
                for (model in modelList) {
                    setBindings(replaceStatement, model).execute()
                    nRow++
                }
            }
        }
        return nRow
    }

    /**
     * Add multiple records to the database at once
     *
     * Either all or none of the records will be inserted/updated into the database.
     * @param modelList List of model records
     * @return Number of rows inserted
     */
    fun bulkAddRecords(modelList: List<Model>): Long {
        return bulkAddRecords(modelList, UpdateMethod.replace)
    }

    open fun bulkAddRecords(modelList: List<Model>, updateMethod: UpdateMethod): Long {
        if (modelList.isEmpty()) {
            Log.d(LOG_TAG, "Empty model list. Cannot bulk add records, returning 0")
            return 0
        }
        Log.i(
            LOG_TAG, String.format(
                "Bulk adding %d %s records to the database", modelList.size,
                modelList[0]::class.java.simpleName
            )
        )
        val nRow: Long
        try {
            mDb.beginTransaction()
            nRow = doAddModels(modelList, updateMethod)
            mDb.setTransactionSuccessful()
        } finally {
            mDb.endTransaction()
        }
        return nRow
    }

    /**
     * Builds an instance of the model from the database record entry
     *
     * When implementing this method, remember to call [.populateBaseModelAttributes]
     * @param cursor Cursor pointing to the record
     * @return New instance of the model from database record
     */
    abstract fun buildModelInstance(cursor: Cursor): Model

    /**
     * Generates an [SQLiteStatement] with values from the `model`.
     * This statement can be executed to replace a record in the database.
     *
     * If the [.mReplaceStatement] is null, subclasses should create a new statement and return.<br></br>
     * If it is not null, the previous bindings will be cleared and replaced with those from the model
     * @return SQLiteStatement for replacing a record in the database
     */
    protected val replaceStatement: SQLiteStatement
        get() {
            var stmt = mReplaceStatement
            if (stmt == null) {
                synchronized(this) {
                    stmt = mReplaceStatement
                    if (stmt == null) {
                        stmt = mDb.compileStatement(
                            "REPLACE INTO " + mTableName + " ( "
                                    + TextUtils.join(" , ", mColumns) + " , "
                                    + CommonColumns.COLUMN_UID
                                    + " ) VALUES ( "
                                    + String(CharArray(mColumns.size)).replace("\u0000", "? , ")
                                    + "?)"
                        )
                        mReplaceStatement = stmt
                    }
                }
            }
            return stmt!!
        }
    protected val updateStatement: SQLiteStatement
        get() {
            var stmt = mUpdateStatement
            if (stmt == null) {
                synchronized(this) {
                    stmt = mUpdateStatement
                    if (stmt == null) {
                        stmt = mDb.compileStatement(
                            "UPDATE " + mTableName + " SET "
                                    + TextUtils.join(" = ? , ", mColumns) + " = ? WHERE "
                                    + CommonColumns.COLUMN_UID
                                    + " = ?"
                        )
                        mUpdateStatement = stmt
                    }
                }
            }
            return stmt!!
        }
    protected val insertStatement: SQLiteStatement
        get() {
            var stmt = mInsertStatement
            if (stmt == null) {
                synchronized(this) {
                    stmt = mInsertStatement
                    if (stmt == null) {
                        stmt = mDb.compileStatement(
                            "INSERT INTO " + mTableName + " ( "
                                    + TextUtils.join(" , ", mColumns) + " , "
                                    + CommonColumns.COLUMN_UID
                                    + " ) VALUES ( "
                                    + String(CharArray(mColumns.size)).replace("\u0000", "? , ")
                                    + "?)"
                        )
                        mInsertStatement = stmt
                    }
                }
            }
            return stmt!!
        }

    /**
     * Binds the values from the model the the SQL statement
     * @param stmt SQL statement with placeholders
     * @param model Model from which to read bind attributes
     * @return SQL statement ready for execution
     */
    protected abstract fun setBindings(stmt: SQLiteStatement, model: Model): SQLiteStatement

    /**
     * Returns a model instance populated with data from the record with GUID `uid`
     *
     * Sub-classes which require special handling should override this method
     * @param uid GUID of the record
     * @return BaseModel instance of the record
     * @throws IllegalArgumentException if the record UID does not exist in thd database
     */
    fun getRecord(uid: String): Model {
        Log.v(LOG_TAG, "Fetching record with GUID $uid")
        val cursor = fetchRecord(uid)
        return try {
            if (cursor.moveToFirst()) {
                buildModelInstance(cursor)
            } else {
                throw IllegalArgumentException("$LOG_TAG: Record with $uid does not exist")
            }
        } finally {
            cursor.close()
        }
    }

    /**
     * Overload of [.getRecord]
     * Simply converts the record ID to a GUID and calls [.getRecord]
     * @param id Database record ID
     * @return Subclass of [BaseModel] containing record info
     */
    fun getRecord(id: Long): Model {
        return getRecord(getUID(id)!!)
    }

    /**
     * Returns all the records in the database
     * @return List of records in the database
     */
    val allRecords: List<Model>
        get() {
            val modelRecords: MutableList<Model> = ArrayList()
            val c = fetchAllRecords()
            try {
                while (c.moveToNext()) {
                    modelRecords.add(buildModelInstance(c))
                }
            } finally {
                c.close()
            }
            return modelRecords
        }

    /**
     * Extracts the attributes of the base model and adds them to the ContentValues object provided
     * @param contentValues Content values to which to add attributes
     * @param model [org.gnucash.android.model.BaseModel] from which to extract values
     * @return [android.content.ContentValues] with the data to be inserted into the db
     */
    protected fun extractBaseModelAttributes(contentValues: ContentValues, model: Model): ContentValues {
        contentValues.put(CommonColumns.COLUMN_UID, model.mUID)
        contentValues.put(
            CommonColumns.COLUMN_CREATED_AT, TimestampHelper.getUtcStringFromTimestamp(
                model.mCreatedTimestamp
            )
        )
        //there is a trigger in the database for updated the modified_at column
        /* Due to the use of SQL REPLACE syntax, we insert the created_at values each time
        * (maintain the original creation time and not the time of creation of the replacement)
        * The updated_at column has a trigger in the database which will update the column
         */return contentValues
    }

    /**
     * Initializes the model with values from the database record common to all models (i.e. in the BaseModel)
     * @param cursor Cursor pointing to database record
     * @param model Model instance to be initialized
     */
    protected fun populateBaseModelAttributes(cursor: Cursor, model: BaseModel) {
        val uid = cursor.getString(cursor.getColumnIndexOrThrow(CommonColumns.COLUMN_UID))
        val created = cursor.getString(cursor.getColumnIndexOrThrow(CommonColumns.COLUMN_CREATED_AT))
        val modified = cursor.getString(cursor.getColumnIndexOrThrow(CommonColumns.COLUMN_MODIFIED_AT))
        model.mUID = uid
        model.mCreatedTimestamp = TimestampHelper.getTimestampFromUtcString(created)
        model.mModifiedTimestamp = TimestampHelper.getTimestampFromUtcString(modified)
    }

    /**
     * Retrieves record with id `rowId` from database table
     * @param rowId ID of record to be retrieved
     * @return [Cursor] to record retrieved
     */
    fun fetchRecord(rowId: Long): Cursor {
        return mDb.query(
            mTableName, null, CommonColumns._ID + "=" + rowId,
            null, null, null, null
        )
    }

    /**
     * Retrieves record with GUID `uid` from database table
     * @param uid GUID of record to be retrieved
     * @return [Cursor] to record retrieved
     */
    fun fetchRecord(uid: String): Cursor {
        return mDb.query(
            mTableName,
            null,
            CommonColumns.COLUMN_UID + "=?",
            arrayOf(uid),
            null,
            null,
            null
        )
    }

    /**
     * Retrieves all records from database table
     * @return [Cursor] to all records in table `tableName`
     */
    open fun fetchAllRecords(): Cursor {
        return fetchAllRecords(null, null, null)
    }

    /**
     * Fetch all records from database matching conditions
     * @param where SQL where clause
     * @param whereArgs String arguments for where clause
     * @param orderBy SQL orderby clause
     * @return Cursor to records matching conditions
     */
    fun fetchAllRecords(where: String?, whereArgs: Array<String?>?, orderBy: String?): Cursor {
        return mDb.query(mTableName, null, where, whereArgs, null, null, orderBy)
    }

    /**
     * Deletes record with ID `rowID` from database table.
     * @param rowId ID of record to be deleted
     * @return `true` if deletion was successful, `false` otherwise
     */
    open fun deleteRecord(rowId: Long): Boolean {
        Log.d(LOG_TAG, "Deleting record with id $rowId from $mTableName")
        return mDb.delete(mTableName, CommonColumns._ID + "=" + rowId, null) > 0
    }

    /**
     * Deletes all records in the database
     * @return Number of deleted records
     */
    open fun deleteAllRecords(): Int {
        return mDb.delete(mTableName, null, null)
    }

    /**
     * Returns the string unique ID (GUID) of a record in the database
     * @param uid GUID of the record
     * @return Long record ID
     * @throws IllegalArgumentException if the GUID does not exist in the database
     */
    fun getID(uid: String): Long {
        val cursor = mDb.query(
            mTableName, arrayOf(CommonColumns._ID),
            CommonColumns.COLUMN_UID + " = ?", arrayOf(uid),
            null, null, null
        )
        val result: Long = try {
            if (cursor.moveToFirst()) {
                cursor.getLong(cursor.getColumnIndexOrThrow(CommonColumns._ID))
            } else {
                throw IllegalArgumentException("$mTableName with GUID $uid does not exist in the db")
            }
        } finally {
            cursor.close()
        }
        return result
    }

    /**
     * Returns the string unique ID (GUID) of a record in the database
     * @param id long database record ID
     * @return GUID of the record
     * @throws IllegalArgumentException if the record ID does not exist in the database
     */
    fun getUID(id: Long): String? {
        val cursor = mDb.query(
            mTableName, arrayOf(CommonColumns.COLUMN_UID),
            CommonColumns._ID + " = " + id,
            null, null, null, null
        )
        val uid: String? = try {
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(CommonColumns.COLUMN_UID))
            } else {
                throw IllegalArgumentException("$mTableName Record ID $id does not exist in the db")
            }
        } finally {
            cursor.close()
        }
        return uid
    }

    /**
     * Returns the currency code (according to the ISO 4217 standard) of the account
     * with unique Identifier `accountUID`
     * @param accountUID Unique Identifier of the account
     * @return Currency code of the account. "" if accountUID
     * does not exist in DB
     */
    fun getAccountCurrencyCode(accountUID: String): String {
        val cursor = mDb.query(
            AccountEntry.TABLE_NAME, arrayOf(AccountEntry.COLUMN_CURRENCY),
            AccountEntry.COLUMN_UID + "= ?", arrayOf(accountUID), null, null, null
        )
        return try {
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                throw IllegalArgumentException("Account $accountUID does not exist")
            }
        } finally {
            cursor.close()
        }
    }

    /**
     * Returns the commodity GUID for the given ISO 4217 currency code
     * @param currencyCode ISO 4217 currency code
     * @return GUID of commodity
     */
    fun getCommodityUID(currencyCode: String): String {
        val where = CommodityEntry.COLUMN_MNEMONIC + "= ?"
        val whereArgs = arrayOf(currencyCode)
        val cursor = mDb.query(
            CommodityEntry.TABLE_NAME, arrayOf(CommodityEntry.COLUMN_UID),
            where, whereArgs, null, null, null
        )
        return try {
            if (cursor.moveToNext()) {
                cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_UID))
            } else {
                throw IllegalArgumentException("Currency code not found in commodities")
            }
        } finally {
            cursor.close()
        }
    }

    /**
     * Returns the [org.gnucash.android.model.AccountType] of the account with unique ID `uid`
     * @param accountUID Unique ID of the account
     * @return [org.gnucash.android.model.AccountType] of the account.
     * @throws java.lang.IllegalArgumentException if accountUID does not exist in DB,
     */
    fun getAccountType(accountUID: String): AccountType {
        val type: String
        val c = mDb.query(
            AccountEntry.TABLE_NAME, arrayOf(AccountEntry.COLUMN_TYPE),
            AccountEntry.COLUMN_UID + "=?", arrayOf(accountUID), null, null, null
        )
        type = try {
            if (c.moveToFirst()) {
                c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_TYPE))
            } else {
                throw IllegalArgumentException("account $accountUID does not exist in DB")
            }
        } finally {
            c.close()
        }
        return AccountType.valueOf(type)
    }

    /**
     * Updates a record in the table
     * @param recordId Database ID of the record to be updated
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    protected fun updateRecord(tableName: String?, recordId: Long, columnKey: String?, newValue: String?): Int {
        val contentValues = ContentValues()
        if (newValue == null) {
            contentValues.putNull(columnKey)
        } else {
            contentValues.put(columnKey, newValue)
        }
        return mDb.update(
            tableName, contentValues,
            CommonColumns._ID + "=" + recordId, null
        )
    }

    /**
     * Updates a record in the table
     * @param uid GUID of the record
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    fun updateRecord(uid: String, columnKey: String, newValue: String?): Int {
        return updateRecords(CommonColumns.COLUMN_UID + "= ?", arrayOf(uid), columnKey, newValue)
    }

    /**
     * Overloaded method. Updates the record with GUID `uid` with the content values
     * @param uid GUID of the record
     * @param contentValues Content values to update
     * @return Number of records updated
     */
    fun updateRecord(uid: String, contentValues: ContentValues): Int {
        return mDb.update(mTableName, contentValues, CommonColumns.COLUMN_UID + "=?", arrayOf(uid))
    }

    /**
     * Updates all records which match the `where` clause with the `newValue` for the column
     * @param where SQL where clause
     * @param whereArgs String arguments for where clause
     * @param columnKey Name of column to be updated
     * @param newValue New value to be assigned to the columnKey
     * @return Number of records affected
     */
    fun updateRecords(where: String?, whereArgs: Array<String>?, columnKey: String, newValue: String?): Int {
        val contentValues = ContentValues()
        if (newValue == null) {
            contentValues.putNull(columnKey)
        } else {
            contentValues.put(columnKey, newValue)
        }
        return mDb.update(mTableName, contentValues, where, whereArgs)
    }

    /**
     * Deletes a record from the database given its unique identifier.
     *
     * Overload of the method [.deleteRecord]
     * @param uid GUID of the record
     * @return `true` if deletion was successful, `false` otherwise
     * @see .deleteRecord
     */
    open fun deleteRecord(uid: String): Boolean {
        return deleteRecord(getID(uid))
    }

    /**
     * Returns an attribute from a specific column in the database for a specific record.
     *
     * The attribute is returned as a string which can then be converted to another type if
     * the caller was expecting something other type
     * @param recordUID GUID of the record
     * @param columnName Name of the column to be retrieved
     * @return String value of the column entry
     * @throws IllegalArgumentException if either the `recordUID` or `columnName` do not exist in the database
     */
    fun getAttribute(recordUID: String, columnName: String): String {
        return getAttribute(mTableName, recordUID, columnName)
    }

    /**
     * Returns an attribute from a specific column in the database for a specific record and specific table.
     *
     * The attribute is returned as a string which can then be converted to another type if
     * the caller was expecting something other type
     *
     * This method is an override of [.getAttribute] which allows to select a value from a
     * different table than the one of current adapter instance
     *
     * @param tableName Database table name. See [DatabaseSchema]
     * @param recordUID GUID of the record
     * @param columnName Name of the column to be retrieved
     * @return String value of the column entry
     * @throws IllegalArgumentException if either the `recordUID` or `columnName` do not exist in the database
     */
    protected fun getAttribute(tableName: String, recordUID: String, columnName: String): String {
        val cursor = mDb.query(
            tableName, arrayOf(columnName),
            AccountEntry.COLUMN_UID + " = ?", arrayOf(recordUID), null, null, null
        )
        return try {
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(columnName)) else {
                throw IllegalArgumentException(
                    String.format(
                        "Record with GUID %s does not exist in the db",
                        recordUID
                    )
                )
            }
        } finally {
            cursor.close()
        }
    }

    /**
     * Returns the number of records in the database table backed by this adapter
     * @return Total number of records in the database
     */
    open val recordsCount: Long
        get() {
            val sql = "SELECT COUNT(*) FROM $mTableName"
            val statement = mDb.compileStatement(sql)
            return statement.simpleQueryForLong()
        }

    /**
     * Expose mDb.beginTransaction()
     */
    fun beginTransaction() {
        mDb.beginTransaction()
    }

    /**
     * Expose mDb.setTransactionSuccessful()
     */
    fun setTransactionSuccessful() {
        mDb.setTransactionSuccessful()
    }

    /// Foreign key constraits should be enabled in general.
    /// But if it affects speed (check constraints takes time)
    /// and the constrained can be assured by the program,
    /// or if some SQL exec will cause deletion of records
    /// (like use replace in accounts update will delete all transactions)
    /// that need not be deleted, then it can be disabled temporarily
    fun enableForeignKey(enable: Boolean) {
        if (enable) {
            mDb.execSQL("PRAGMA foreign_keys=ON;")
        } else {
            mDb.execSQL("PRAGMA foreign_keys=OFF;")
        }
    }

    /**
     * Expose mDb.endTransaction()
     */
    fun endTransaction() {
        mDb.endTransaction()
    }
}