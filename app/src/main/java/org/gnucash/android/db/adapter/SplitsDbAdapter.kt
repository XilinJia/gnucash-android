/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.db.adapter

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQueryBuilder
import android.database.sqlite.SQLiteStatement
import android.text.TextUtils
import android.util.Log
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.Money.Companion.getBigDecimal
import org.gnucash.android.model.Split
import org.gnucash.android.model.TransactionType
import org.gnucash.android.util.TimestampHelper
import java.math.BigDecimal

/**
 * Database adapter for managing transaction splits in the database
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Yongxin Wang <fefe.wyx></fefe.wyx>@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class SplitsDbAdapter(db: SQLiteDatabase?) : DatabaseAdapter<Split>(
    db!!, SplitEntry.TABLE_NAME, arrayOf(
        SplitEntry.COLUMN_MEMO,
        SplitEntry.COLUMN_TYPE,
        SplitEntry.COLUMN_VALUE_NUM,
        SplitEntry.COLUMN_VALUE_DENOM,
        SplitEntry.COLUMN_QUANTITY_NUM,
        SplitEntry.COLUMN_QUANTITY_DENOM,
        SplitEntry.COLUMN_CREATED_AT,
        SplitEntry.COLUMN_RECONCILE_STATE,
        SplitEntry.COLUMN_RECONCILE_DATE,
        SplitEntry.COLUMN_ACCOUNT_UID,
        SplitEntry.COLUMN_TRANSACTION_UID
    )
) {
    /**
     * Adds a split to the database.
     * The transactions belonging to the split are marked as exported
     * @param model [org.gnucash.android.model.Split] to be recorded in DB
     */
    override fun addRecord(model: Split, updateMethod: UpdateMethod) {
        Log.d(LOG_TAG, "Replace transaction split in db")
        super.addRecord(model, updateMethod)
        val transactionId = getTransactionID(model.mTransactionUID)
        //when a split is updated, we want mark the transaction as not exported
        updateRecord(
            TransactionEntry.TABLE_NAME, transactionId,
            TransactionEntry.COLUMN_EXPORTED, 0.toString()
        )

        //modifying a split means modifying the accompanying transaction as well
        updateRecord(
            TransactionEntry.TABLE_NAME,
            transactionId,
            TransactionEntry.COLUMN_MODIFIED_AT,
            TimestampHelper.getUtcStringFromTimestamp(TimestampHelper.timestampFromNow)
        )
    }

    override fun setBindings(stmt: SQLiteStatement, model: Split): SQLiteStatement {
        stmt.clearBindings()
        if (model.mMemo != null) {
            stmt.bindString(1, model.mMemo)
        }
        stmt.bindString(2, model.mSplitType!!.name)
        stmt.bindLong(3, model.mValue!!.numerator())
        stmt.bindLong(4, model.mValue!!.denominator())
        stmt.bindLong(5, model.mQuantity!!.numerator())
        stmt.bindLong(6, model.mQuantity!!.denominator())
        stmt.bindString(7, model.mCreatedTimestamp.toString())
        stmt.bindString(8, model.mReconcileState.toString())
        stmt.bindString(9, model.mReconcileDate.toString())
        stmt.bindString(10, model.mAccountUID)
        stmt.bindString(11, model.mTransactionUID)
        stmt.bindString(12, model.mUID)
        return stmt
    }

    /**
     * Builds a split instance from the data pointed to by the cursor provided
     *
     * This method will not move the cursor in any way. So the cursor should already by pointing to the correct entry
     * @param cursor Cursor pointing to transaction record in database
     * @return [org.gnucash.android.model.Split] instance
     */
    override fun buildModelInstance(cursor: Cursor): Split {
        val valueNum = cursor.getLong(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_VALUE_NUM))
        val valueDenom = cursor.getLong(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_VALUE_DENOM))
        val quantityNum = cursor.getLong(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_QUANTITY_NUM))
        val quantityDenom = cursor.getLong(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_QUANTITY_DENOM))
        val typeName = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_TYPE))
        val accountUID = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_ACCOUNT_UID))
        val transxUID = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_TRANSACTION_UID))
        val memo = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_MEMO))
        val reconcileState = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_RECONCILE_STATE))
        val reconcileDate = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_RECONCILE_DATE))
        val transactionCurrency = getAttribute(TransactionEntry.TABLE_NAME, transxUID, TransactionEntry.COLUMN_CURRENCY)
        val value = Money(valueNum, valueDenom, transactionCurrency)
        val currencyCode = getAccountCurrencyCode(accountUID)
        val quantity = Money(quantityNum, quantityDenom, currencyCode)
        val split = Split(value, accountUID)
        split.mQuantity = quantity
        populateBaseModelAttributes(cursor, split)
        split.mTransactionUID = transxUID
        split.mSplitType = TransactionType.valueOf(typeName)
        split.mMemo = memo
        split.mReconcileState = reconcileState[0]
        if (reconcileDate != null && reconcileDate.isNotEmpty()) split.mReconcileDate =
            TimestampHelper.getTimestampFromUtcString(reconcileDate)
        return split
    }

    /**
     * Returns the sum of the splits for given set of accounts.
     * This takes into account the kind of movement caused by the split in the account (which also depends on account type)
     * The Caller must make sure all accounts have the currency, which is passed in as currencyCode
     * @param accountUIDList List of String unique IDs of given set of accounts
     * @param currencyCode currencyCode for all the accounts in the list
     * @param hasDebitNormalBalance Does the final balance has normal debit credit meaning
     * @return Balance of the splits for this account
     */
    fun computeSplitBalance(
        accountUIDList: List<String?>,
        currencyCode: String,
        hasDebitNormalBalance: Boolean
    ): Money {
        return calculateSplitBalance(accountUIDList, currencyCode, hasDebitNormalBalance, -1, -1)
    }

    /**
     * Returns the sum of the splits for given set of accounts within the specified time range.
     * This takes into account the kind of movement caused by the split in the account (which also depends on account type)
     * The Caller must make sure all accounts have the currency, which is passed in as currencyCode
     * @param accountUIDList List of String unique IDs of given set of accounts
     * @param currencyCode currencyCode for all the accounts in the list
     * @param hasDebitNormalBalance Does the final balance has normal debit credit meaning
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp the end timestamp of the time range
     * @return Balance of the splits for this account within the specified time range
     */
    fun computeSplitBalance(
        accountUIDList: List<String?>, currencyCode: String, hasDebitNormalBalance: Boolean,
        startTimestamp: Long, endTimestamp: Long
    ): Money {
        return calculateSplitBalance(accountUIDList, currencyCode, hasDebitNormalBalance, startTimestamp, endTimestamp)
    }

    private fun calculateSplitBalance(
        accountUIDList: List<String?>, currencyCode: String, hasDebitNormalBalance: Boolean,
        startTimestamp: Long, endTimestamp: Long
    ): Money {
        if (accountUIDList.isEmpty()) {
            return Money("0", currencyCode)
        }
        val cursor: Cursor
        var selectionArgs: Array<String>? = null
        var selection =
            DatabaseSchema.AccountEntry.TABLE_NAME + "_" + DatabaseSchema.CommonColumns.COLUMN_UID + " in ( '" + TextUtils.join(
                "' , '",
                accountUIDList
            ) + "' ) AND " +
                    TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TEMPLATE + " = 0"
        if (startTimestamp != -1L && endTimestamp != -1L) {
            selection += " AND " + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TIMESTAMP + " BETWEEN ? AND ? "
            selectionArgs = arrayOf(startTimestamp.toString(), endTimestamp.toString())
        } else if (startTimestamp == -1L && endTimestamp != -1L) {
            selection += " AND " + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TIMESTAMP + " <= ?"
            selectionArgs = arrayOf(endTimestamp.toString())
        } else if (startTimestamp != -1L /* && endTimestamp == -1*/) {
            selection += " AND " + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TIMESTAMP + " >= ?"
            selectionArgs = arrayOf(startTimestamp.toString())
        }
        cursor = mDb.query(
            "trans_split_acct",
            arrayOf(
                "TOTAL ( CASE WHEN " + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_TYPE + " = 'DEBIT' THEN " +
                        SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_QUANTITY_NUM + " ELSE - " +
                        SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_QUANTITY_NUM + " END )",
                SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_QUANTITY_DENOM,
                DatabaseSchema.AccountEntry.TABLE_NAME + "_" + DatabaseSchema.AccountEntry.COLUMN_CURRENCY
            ),
            selection,
            selectionArgs,
            DatabaseSchema.AccountEntry.TABLE_NAME + "_" + DatabaseSchema.AccountEntry.COLUMN_CURRENCY,
            null,
            null
        )
        return try {
            var total = createZeroInstance(currencyCode)
            var commoditiesDbAdapter: CommoditiesDbAdapter? = null
            var pricesDbAdapter: PricesDbAdapter? = null
            var commodity: Commodity? = null
            var currencyUID: String? = null
            while (cursor.moveToNext()) {
                var amount_num = cursor.getLong(0)
                val amount_denom = cursor.getLong(1)
                val commodityCode = cursor.getString(2)
                //Log.d(getClass().getName(), commodity + " " + amount_num + "/" + amount_denom);
                if (commodityCode == "XXX" || amount_num == 0L) {
                    // ignore custom currency
                    continue
                }
                if (!hasDebitNormalBalance) {
                    amount_num = -amount_num
                }
                if (commodityCode == currencyCode) {
                    // currency matches
                    total = total.add(Money(amount_num, amount_denom, currencyCode))
                    //Log.d(getClass().getName(), "currency " + commodity + " sub - total " + total);
                } else {
                    // there is a second currency involved
                    if (commoditiesDbAdapter == null) {
                        commoditiesDbAdapter = CommoditiesDbAdapter(mDb)
                        pricesDbAdapter = PricesDbAdapter(mDb)
                        commodity = commoditiesDbAdapter.getCommodity(currencyCode)
                        currencyUID = commoditiesDbAdapter.getCommodityUID(currencyCode)
                    }
                    // get price
                    val commodityUID = commoditiesDbAdapter.getCommodityUID(commodityCode)
                    val price = pricesDbAdapter!!.getPrice(commodityUID, currencyUID!!)
                    if (price.first <= 0 || price.second <= 0) {
                        // no price exists, just ignore it
                        continue
                    }
                    val amount = getBigDecimal(amount_num, amount_denom)
                    val amountConverted = amount.multiply(BigDecimal(price.first))
                        .divide(
                            BigDecimal(price.second),
                            commodity!!.smallestFractionDigits(),
                            BigDecimal.ROUND_HALF_EVEN
                        )
                    total = total.add(Money(amountConverted, commodity))
                    //Log.d(getClass().getName(), "currency " + commodity + " sub - total " + total);
                }
            }
            total
        } finally {
            cursor.close()
        }
    }

    /**
     * Returns the list of splits for a transaction
     * @param transactionUID String unique ID of transaction
     * @return List of [org.gnucash.android.model.Split]s
     */
    fun getSplitsForTransaction(transactionUID: String?): List<Split> {
        val cursor = fetchSplitsForTransaction(transactionUID)
        val splitList: MutableList<Split> = ArrayList()
        try {
            while (cursor.moveToNext()) {
                splitList.add(buildModelInstance(cursor))
            }
        } finally {
            cursor.close()
        }
        return splitList
    }

    /**
     * Returns the list of splits for a transaction
     * @param transactionID DB record ID of the transaction
     * @return List of [org.gnucash.android.model.Split]s
     * @see .getSplitsForTransaction
     * @see .getTransactionUID
     */
    fun getSplitsForTransaction(transactionID: Long): List<Split> {
        return getSplitsForTransaction(getTransactionUID(transactionID))
    }

    /**
     * Fetch splits for a given transaction within a specific account
     * @param transactionUID String unique ID of transaction
     * @param accountUID String unique ID of account
     * @return List of splits
     */
    fun getSplitsForTransactionInAccount(transactionUID: String?, accountUID: String?): List<Split> {
        val cursor = fetchSplitsForTransactionAndAccount(transactionUID, accountUID)
        val splitList: MutableList<Split> = ArrayList()
        if (cursor != null) {
            while (cursor.moveToNext()) {
                splitList.add(buildModelInstance(cursor))
            }
            cursor.close()
        }
        return splitList
    }

    /**
     * Fetches a collection of splits for a given condition and sorted by `sortOrder`
     * @param where String condition, formatted as SQL WHERE clause
     * @param whereArgs where args
     * @param sortOrder Sort order for the returned records
     * @return Cursor to split records
     */
    fun fetchSplits(where: String?, whereArgs: Array<String?>?, sortOrder: String?): Cursor {
        return mDb.query(
            SplitEntry.TABLE_NAME,
            null, where, whereArgs, null, null, sortOrder
        )
    }

    /**
     * Returns a Cursor to a dataset of splits belonging to a specific transaction
     * @param transactionUID Unique idendtifier of the transaction
     * @return Cursor to splits
     */
    fun fetchSplitsForTransaction(transactionUID: String?): Cursor {
        Log.v(LOG_TAG, "Fetching all splits for transaction UID $transactionUID")
        return mDb.query(
            SplitEntry.TABLE_NAME,
            null, SplitEntry.COLUMN_TRANSACTION_UID + " = ?", arrayOf(transactionUID),
            null, null, null
        )
    }

    /**
     * Fetches splits for a given account
     * @param accountUID String unique ID of account
     * @return Cursor containing splits dataset
     */
    fun fetchSplitsForAccount(accountUID: String): Cursor {
        Log.d(LOG_TAG, "Fetching all splits for account UID $accountUID")

        //This is more complicated than a simple "where account_uid=?" query because
        // we need to *not* return any splits which belong to recurring transactions
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.tables = (TransactionEntry.TABLE_NAME
                + " INNER JOIN " + SplitEntry.TABLE_NAME + " ON "
                + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
                + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID)
        queryBuilder.isDistinct = true
        val projectionIn = arrayOf(SplitEntry.TABLE_NAME + ".*")
        val selection = (SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
                + " AND " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " = 0")
        val selectionArgs = arrayOf(accountUID)
        val sortOrder = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " DESC"
        return queryBuilder.query(mDb, projectionIn, selection, selectionArgs, null, null, sortOrder)
    }

    /**
     * Returns a cursor to splits for a given transaction and account
     * @param transactionUID Unique idendtifier of the transaction
     * @param accountUID String unique ID of account
     * @return Cursor to splits data set
     */
    fun fetchSplitsForTransactionAndAccount(transactionUID: String?, accountUID: String?): Cursor? {
        if (transactionUID == null || accountUID == null) return null
        Log.v(
            LOG_TAG, "Fetching all splits for transaction ID " + transactionUID
                    + "and account ID " + accountUID
        )
        return mDb.query(
            SplitEntry.TABLE_NAME,
            null, SplitEntry.COLUMN_TRANSACTION_UID + " = ? AND "
                    + SplitEntry.COLUMN_ACCOUNT_UID + " = ?", arrayOf(transactionUID, accountUID),
            null, null, SplitEntry.COLUMN_VALUE_NUM + " ASC"
        )
    }

    /**
     * Returns the unique ID of a transaction given the database record ID of same
     * @param transactionId Database record ID of the transaction
     * @return String unique ID of the transaction or null if transaction with the ID cannot be found.
     */
    fun getTransactionUID(transactionId: Long): String {
        val cursor = mDb.query(
            TransactionEntry.TABLE_NAME, arrayOf(TransactionEntry.COLUMN_UID),
            TransactionEntry._ID + " = " + transactionId,
            null, null, null, null
        )
        return try {
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_UID))
            } else {
                throw IllegalArgumentException("transaction $transactionId does not exist")
            }
        } finally {
            cursor.close()
        }
    }

    override fun deleteRecord(rowId: Long): Boolean {
        val split = getRecord(rowId)
        val transactionUID = split.mTransactionUID
        var result = mDb.delete(SplitEntry.TABLE_NAME, SplitEntry._ID + "=" + rowId, null) > 0
        if (!result) //we didn't delete for whatever reason, invalid rowId etc
            return false

        //if we just deleted the last split, then remove the transaction from db
        val cursor = fetchSplitsForTransaction(transactionUID)
        try {
            if (cursor.count > 0) {
                val transactionID = getTransactionID(transactionUID)
                result = mDb.delete(
                    TransactionEntry.TABLE_NAME,
                    TransactionEntry._ID + "=" + transactionID, null
                ) > 0
            }
        } finally {
            cursor.close()
        }
        return result
    }

    /**
     * Returns the database record ID for the specified transaction UID
     * @param transactionUID Unique idendtifier of the transaction
     * @return Database record ID for the transaction
     */
    fun getTransactionID(transactionUID: String?): Long {
        val c = mDb.query(
            TransactionEntry.TABLE_NAME, arrayOf(TransactionEntry._ID),
            TransactionEntry.COLUMN_UID + "=?", arrayOf(transactionUID), null, null, null
        )
        return try {
            if (c.moveToFirst()) {
                c.getLong(0)
            } else {
                throw IllegalArgumentException("transaction $transactionUID does not exist")
            }
        } finally {
            c.close()
        }
    }

    companion object {
        /**
         * Returns application-wide instance of the database adapter
         * @return SplitsDbAdapter instance
         */
        @JvmStatic
        val instance: SplitsDbAdapter
            get() = GnuCashApplication.splitsDbAdapter!!
    }
}