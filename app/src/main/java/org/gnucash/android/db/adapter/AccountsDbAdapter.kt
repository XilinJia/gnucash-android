/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
import android.graphics.Color
import android.text.TextUtils
import android.util.Log
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.model.*
import org.gnucash.android.model.Commodity.Companion.getInstance
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.Money
import org.gnucash.android.model.Transaction.Companion.typeForBalance
import org.gnucash.android.util.TimestampHelper
import java.math.BigDecimal
import java.sql.Timestamp
import java.util.*

/**
 * Manages persistence of [Account]s in the database
 * Handles adding, modifying and deleting of account records.
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Yongxin Wang <fefe.wyx></fefe.wyx>@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class AccountsDbAdapter : DatabaseAdapter<Account> {
    /**
     * Transactions database adapter for manipulating transactions associated with accounts
     */
    private val mTransactionsAdapter: TransactionsDbAdapter

    /**
     * Commodities database adapter for commodity manipulation
     */
    private val mCommoditiesDbAdapter: CommoditiesDbAdapter

    /**
     * Overloaded constructor. Creates an adapter for an already open database
     * @param db SQliteDatabase instance
     */
    constructor(db: SQLiteDatabase, transactionsDbAdapter: TransactionsDbAdapter) : super(
        db, AccountEntry.TABLE_NAME, arrayOf<String>(
            AccountEntry.COLUMN_NAME,
            AccountEntry.COLUMN_DESCRIPTION,
            AccountEntry.COLUMN_TYPE,
            AccountEntry.COLUMN_CURRENCY,
            AccountEntry.COLUMN_COLOR_CODE,
            AccountEntry.COLUMN_FAVORITE,
            AccountEntry.COLUMN_FULL_NAME,
            AccountEntry.COLUMN_PLACEHOLDER,
            AccountEntry.COLUMN_CREATED_AT,
            AccountEntry.COLUMN_HIDDEN,
            AccountEntry.COLUMN_COMMODITY_UID,
            AccountEntry.COLUMN_PARENT_ACCOUNT_UID,
            AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID
        )
    ) {
        mTransactionsAdapter = transactionsDbAdapter
        mCommoditiesDbAdapter = CommoditiesDbAdapter(db)
    }

    /**
     * Convenience overloaded constructor.
     * This is used when an AccountsDbAdapter object is needed quickly. Otherwise, the other
     * constructor [.AccountsDbAdapter]
     * should be used whenever possible
     * @param db Database to create an adapter for
     */
    constructor(db: SQLiteDatabase) : super(
        db, AccountEntry.TABLE_NAME, arrayOf<String>(
            AccountEntry.COLUMN_NAME,
            AccountEntry.COLUMN_DESCRIPTION,
            AccountEntry.COLUMN_TYPE,
            AccountEntry.COLUMN_CURRENCY,
            AccountEntry.COLUMN_COLOR_CODE,
            AccountEntry.COLUMN_FAVORITE,
            AccountEntry.COLUMN_FULL_NAME,
            AccountEntry.COLUMN_PLACEHOLDER,
            AccountEntry.COLUMN_CREATED_AT,
            AccountEntry.COLUMN_HIDDEN,
            AccountEntry.COLUMN_COMMODITY_UID,
            AccountEntry.COLUMN_PARENT_ACCOUNT_UID,
            AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID
        )
    ) {
        mTransactionsAdapter = TransactionsDbAdapter(db, SplitsDbAdapter(db))
        mCommoditiesDbAdapter = CommoditiesDbAdapter(db)
    }

    /**
     * Adds an account to the database.
     * If an account already exists in the database with the same GUID, it is replaced.
     * @param model [Account] to be inserted to database
     */
    override fun addRecord(model: Account, updateMethod: UpdateMethod) {
        Log.d(LOG_TAG, "Replace account to db")
        //in-case the account already existed, we want to update the templates based on it as well
        val templateTransactions = mTransactionsAdapter.getScheduledTransactionsForAccount(model.mUID!!)
        super.addRecord(model, updateMethod)
        val accountUID = model.mUID
        //now add transactions if there are any
        if (model.mAccountType !== AccountType.ROOT) {
            //update the fully qualified account name
            updateRecord(accountUID!!, AccountEntry.COLUMN_FULL_NAME, getFullyQualifiedAccountName(accountUID))
            for (t in model.getMTransactionsList()) {
                t.mCommodity = model.getMCommodity()
                mTransactionsAdapter.addRecord(t, updateMethod)
            }
            for (transaction in templateTransactions) {
                mTransactionsAdapter.addRecord(transaction, UpdateMethod.update)
            }
        }
    }

    /**
     * Adds some accounts and their transactions to the database in bulk.
     *
     * If an account already exists in the database with the same GUID, it is replaced.
     * This function will NOT try to determine the full name
     * of the accounts inserted, full names should be generated prior to the insert.
     * <br></br>All or none of the accounts will be inserted;
     * @param modelList [Account] to be inserted to database
     * @return number of rows inserted
     */
    override fun bulkAddRecords(modelList: List<Account>, updateMethod: UpdateMethod): Long {
        //scheduled transactions are not fetched from the database when getting account transactions
        //so we retrieve those which affect this account and then re-save them later
        //this is necessary because the database has ON DELETE CASCADE between accounts and splits
        //and all accounts are editing via SQL REPLACE

        //// TODO: 20.04.2016 Investigate if we can safely remove updating the transactions when bulk updating accounts
        val transactionList: MutableList<Transaction> = ArrayList(modelList.size * 2)
        for (account in modelList) {
            transactionList.addAll(account.getMTransactionsList())
            transactionList.addAll(mTransactionsAdapter.getScheduledTransactionsForAccount(account.mUID!!))
        }
        val nRow = super.bulkAddRecords(modelList, updateMethod)
        if (nRow > 0 && transactionList.isNotEmpty()) {
            mTransactionsAdapter.bulkAddRecords(transactionList, updateMethod)
        }
        return nRow
    }

    override fun setBindings(stmt: SQLiteStatement, model: Account): SQLiteStatement {
        stmt.clearBindings()
        stmt.bindString(1, model.mName)
        stmt.bindString(2, model.mDescription)
        stmt.bindString(3, model.mAccountType.name)
        stmt.bindString(4, model.getMCommodity().mMnemonic)
        if (model.getMColor() != Account.DEFAULT_COLOR) {
            stmt.bindString(5, model.colorHexString)
        }
        stmt.bindLong(6, (if (model.isFavorite) 1 else 0).toLong())
        stmt.bindString(7, model.mFullName)
        stmt.bindLong(8, (if (model.isPlaceholderAccount) 1 else 0).toLong())
        stmt.bindString(9, TimestampHelper.getUtcStringFromTimestamp(model.mCreatedTimestamp))
        stmt.bindLong(10, (if (model.isHidden) 1 else 0).toLong())
        stmt.bindString(11, model.getMCommodity().mUID)
        var parentAccountUID = model.mParentAccountUID
        if (parentAccountUID == null && model.mAccountType !== AccountType.ROOT) {
            parentAccountUID = orCreateGnuCashRootAccountUID
        }
        if (parentAccountUID != null) {
            stmt.bindString(12, parentAccountUID)
        }
        if (model.mDefaultTransferAccountUID != null) {
            stmt.bindString(13, model.mDefaultTransferAccountUID)
        }
        stmt.bindString(14, model.mUID)
        return stmt
    }

    /**
     * Marks all transactions for a given account as exported
     * @param accountUID Unique ID of the record to be marked as exported
     * @return Number of records marked as exported
     */
    fun markAsExported(accountUID: String): Int {
        val contentValues = ContentValues()
        contentValues.put(TransactionEntry.COLUMN_EXPORTED, 1)
        return mDb.update(
            TransactionEntry.TABLE_NAME,
            contentValues,
            TransactionEntry.COLUMN_UID + " IN ( " +
                    "SELECT DISTINCT " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID +
                    " FROM " + TransactionEntry.TABLE_NAME + " , " + SplitEntry.TABLE_NAME + " ON " +
                    TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = " +
                    SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + " , " +
                    AccountEntry.TABLE_NAME + " ON " + SplitEntry.TABLE_NAME + "." +
                    SplitEntry.COLUMN_ACCOUNT_UID + " = " + AccountEntry.TABLE_NAME + "." +
                    AccountEntry.COLUMN_UID + " WHERE " + AccountEntry.TABLE_NAME + "." +
                    AccountEntry.COLUMN_UID + " = ? "
                    + " ) ", arrayOf(accountUID)
        )
    }

    /**
     * This feature goes through all the rows in the accounts and changes value for `columnKey` to `newValue`<br></br>
     * The `newValue` parameter is taken as string since SQLite typically stores everything as text.
     *
     * **This method affects all rows, exercise caution when using it**
     * @param columnKey Name of column to be updated
     * @param newValue New value to be assigned to the columnKey
     * @return Number of records affected
     */
    fun updateAllAccounts(columnKey: String?, newValue: String?): Int {
        val contentValues = ContentValues()
        if (newValue == null) {
            contentValues.putNull(columnKey)
        } else {
            contentValues.put(columnKey, newValue)
        }
        return mDb.update(AccountEntry.TABLE_NAME, contentValues, null, null)
    }

    /**
     * Updates a specific entry of an account
     * @param accountId Database record ID of the account to be updated
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    fun updateAccount(accountId: Long, columnKey: String?, newValue: String?): Int {
        return updateRecord(AccountEntry.TABLE_NAME, accountId, columnKey, newValue)
    }

    /**
     * This method goes through all the children of `accountUID` and updates the parent account
     * to `newParentAccountUID`. The fully qualified account names for all descendant accounts will also be updated.
     * @param accountUID GUID of the account
     * @param newParentAccountUID GUID of the new parent account
     */
    fun reassignDescendantAccounts(accountUID: String, newParentAccountUID: String) {
        val descendantAccountUIDs: List<String?> = getDescendantAccountUIDs(accountUID, null, null)
        if (descendantAccountUIDs.isNotEmpty()) {
            val descendantAccounts = getSimpleAccountList(
                AccountEntry.COLUMN_UID + " IN ('" + TextUtils.join("','", descendantAccountUIDs) + "')",
                null,
                null
            )
            val mapAccounts = HashMap<String?, Account>()
            for (account in descendantAccounts) mapAccounts[account.mUID] = account
            val parentAccountFullName: String = if (getAccountType(newParentAccountUID) === AccountType.ROOT) {
                ""
            } else {
                getAccountFullName(newParentAccountUID)
            }
            val contentValues = ContentValues()
            for (acctUID in descendantAccountUIDs) {
                val acct = mapAccounts[acctUID]
                if (accountUID == acct!!.mParentAccountUID) {
                    // direct descendant
                    acct.mParentAccountUID = newParentAccountUID
                    if (parentAccountFullName.isEmpty()) {
                        acct.mFullName = acct.mName
                    } else {
                        acct.mFullName = parentAccountFullName + ACCOUNT_NAME_SEPARATOR + acct.mName
                    }
                    // update DB
                    contentValues.clear()
                    contentValues.put(AccountEntry.COLUMN_PARENT_ACCOUNT_UID, newParentAccountUID)
                    contentValues.put(AccountEntry.COLUMN_FULL_NAME, acct.mFullName)
                    mDb.update(
                        AccountEntry.TABLE_NAME, contentValues,
                        AccountEntry.COLUMN_UID + " = ?", arrayOf(acct.mUID)
                    )
                } else {
                    // indirect descendant
                    acct.mFullName = mapAccounts[acct.mParentAccountUID]!!.mFullName +
                            ACCOUNT_NAME_SEPARATOR + acct.mName
                    // update DB
                    contentValues.clear()
                    contentValues.put(AccountEntry.COLUMN_FULL_NAME, acct.mFullName)
                    mDb.update(
                        AccountEntry.TABLE_NAME, contentValues,
                        AccountEntry.COLUMN_UID + " = ?", arrayOf(acct.mUID)
                    )
                }
            }
        }
    }

    /**
     * Deletes an account and its transactions, and all its sub-accounts and their transactions.
     *
     * Not only the splits belonging to the account and its descendants will be deleted, rather,
     * the complete transactions associated with this account and its descendants
     * (i.e. as long as the transaction has at least one split belonging to one of the accounts).
     * This prevents an split imbalance from being caused.
     *
     * If you want to preserve transactions, make sure to first reassign the children accounts (see [.reassignDescendantAccounts]
     * before calling this method. This method will however not delete a root account.
     *
     * **This method does a thorough delete, use with caution!!!**
     * @param accountId Database record ID of account
     * @return `true` if the account and subaccounts were all successfully deleted, `false` if
     * even one was not deleted
     * @see .reassignDescendantAccounts
     */
    fun recursiveDeleteAccount(accountId: Long): Boolean {
        val accountUID = getUID(accountId)
        if (getAccountType(accountUID!!) === AccountType.ROOT) {
            // refuse to delete ROOT
            return false
        }
        Log.d(LOG_TAG, "Delete account with rowId with its transactions and sub-accounts: $accountId")
        val descendantAccountUIDs = getDescendantAccountUIDs(accountUID, null, null)
        mDb.beginTransaction()
        return try {
            descendantAccountUIDs.add(accountUID) //add account to descendants list just for convenience
            for (descendantAccountUID in descendantAccountUIDs) {
                mTransactionsAdapter.deleteTransactionsForAccount(descendantAccountUID!!)
            }
            val accountUIDList = "'" + TextUtils.join("','", descendantAccountUIDs) + "'"

            // delete accounts
            val deletedCount = mDb.delete(
                AccountEntry.TABLE_NAME,
                AccountEntry.COLUMN_UID + " IN (" + accountUIDList + ")",
                null
            ).toLong()

            //if we delete some accounts, reset the default transfer account to NULL
            //there is also a database trigger from db version > 12
            if (deletedCount > 0) {
                val contentValues = ContentValues()
                contentValues.putNull(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID)
                mDb.update(
                    mTableName, contentValues,
                    AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + " IN (" + accountUIDList + ")",
                    null
                )
            }
            mDb.setTransactionSuccessful()
            true
        } finally {
            mDb.endTransaction()
        }
    }

    /**
     * Builds an account instance with the provided cursor and loads its corresponding transactions.
     *
     * @param cursor Cursor pointing to account record in database
     * @return [Account] object constructed from database record
     */
    override fun buildModelInstance(cursor: Cursor): Account {
        val account = buildSimpleAccountInstance(cursor)
        account.setMTransactionsList(mTransactionsAdapter.getAllTransactionsForAccount(account.mUID!!).toMutableList())
        return account
    }

    /**
     * Builds an account instance with the provided cursor and loads its corresponding transactions.
     *
     * The method will not move the cursor position, so the cursor should already be pointing
     * to the account record in the database<br></br>
     * **Note** Unlike [.buildModelInstance] this method will not load transactions
     *
     * @param c Cursor pointing to account record in database
     * @return [Account] object constructed from database record
     */
    private fun buildSimpleAccountInstance(c: Cursor): Account {
        val account = Account(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_NAME)))
        populateBaseModelAttributes(c, account)
        val description = c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_DESCRIPTION))
        account.mDescription = description ?: ""
        account.mParentAccountUID = c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_PARENT_ACCOUNT_UID))
        account.mAccountType = AccountType.valueOf(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_TYPE)))
        val currencyCode = c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_CURRENCY))
        account.setMCommodity(mCommoditiesDbAdapter.getCommodity(currencyCode)!!)
        account.setMIsPlaceHolderAccount(c.getInt(c.getColumnIndexOrThrow(AccountEntry.COLUMN_PLACEHOLDER)) == 1)
        account.mDefaultTransferAccountUID =
            c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID))
        val color = c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_COLOR_CODE))
        if (color != null) account.setMColor(color)
        account.setMIsFavorite(c.getInt(c.getColumnIndexOrThrow(AccountEntry.COLUMN_FAVORITE)) == 1)
        account.mFullName = c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_FULL_NAME))
        account.setMIsHidden(c.getInt(c.getColumnIndexOrThrow(AccountEntry.COLUMN_HIDDEN)) == 1)
        return account
    }

    /**
     * Returns the  unique ID of the parent account of the account with unique ID `uid`
     * If the account has no parent, null is returned
     * @param uid Unique Identifier of account whose parent is to be returned. Should not be null
     * @return DB record UID of the parent account, null if the account has no parent
     */
    fun getParentAccountUID(uid: String): String? {
        val cursor = mDb.query(
            AccountEntry.TABLE_NAME, arrayOf(AccountEntry.COLUMN_PARENT_ACCOUNT_UID),
            AccountEntry.COLUMN_UID + " = ?", arrayOf(uid),
            null, null, null, null
        )
        return try {
            if (cursor.moveToFirst()) {
                Log.d(LOG_TAG, "Found parent account UID, returning value")
                cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_PARENT_ACCOUNT_UID))
            } else {
                null
            }
        } finally {
            cursor.close()
        }
    }

    /**
     * Returns the color code for the account in format #rrggbb
     * @param accountId Database row ID of the account
     * @return String color code of account or null if none
     */
    fun getAccountColorCode(accountId: Long): String? {
        val c = mDb.query(
            AccountEntry.TABLE_NAME, arrayOf(AccountEntry._ID, AccountEntry.COLUMN_COLOR_CODE),
            AccountEntry._ID + "=" + accountId,
            null, null, null, null
        )
        return try {
            if (c.moveToFirst()) {
                c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_COLOR_CODE))
            } else {
                null
            }
        } finally {
            c.close()
        }
    }

    /**
     * Overloaded method. Resolves the account unique ID from the row ID and makes a call to [.getAccountType]
     * @param accountId Database row ID of the account
     * @return [AccountType] of the account
     */
    fun getAccountType(accountId: Long): AccountType {
        return getAccountType(getUID(accountId)!!)
    }

    /**
     * Returns a list of all account entries in the system (includes root account)
     * No transactions are loaded, just the accounts
     * @return List of [Account]s in the database
     */
    val simpleAccountList: List<Account>
        get() {
            val accounts = LinkedList<Account>()
            val c = fetchAccounts(null, null, AccountEntry.COLUMN_FULL_NAME + " ASC")
            try {
                while (c.moveToNext()) {
                    accounts.add(buildSimpleAccountInstance(c))
                }
            } finally {
                c.close()
            }
            return accounts
        }

    /**
     * Returns a list of all account entries in the system (includes root account)
     * No transactions are loaded, just the accounts
     * @return List of [Account]s in the database
     */
    fun getSimpleAccountList(where: String?, whereArgs: Array<String?>?, orderBy: String?): List<Account> {
        val accounts = LinkedList<Account>()
        val c = fetchAccounts(where, whereArgs, orderBy)
        try {
            while (c.moveToNext()) {
                accounts.add(buildSimpleAccountInstance(c))
            }
        } finally {
            c.close()
        }
        return accounts
    }

    /**
     * Returns a list of accounts which have transactions that have not been exported yet
     * @param lastExportTimeStamp Timestamp after which to any transactions created/modified should be exported
     * @return List of [Account]s with unexported transactions
     */
    fun getExportableAccounts(lastExportTimeStamp: Timestamp?): List<Account> {
        val accountsList = LinkedList<Account>()
        val cursor = mDb.query(
            TransactionEntry.TABLE_NAME + " , " + SplitEntry.TABLE_NAME +
                    " ON " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = " +
                    SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + " , " +
                    AccountEntry.TABLE_NAME + " ON " + AccountEntry.TABLE_NAME + "." +
                    AccountEntry.COLUMN_UID + " = " + SplitEntry.TABLE_NAME + "." +
                    SplitEntry.COLUMN_ACCOUNT_UID,
            arrayOf(AccountEntry.TABLE_NAME + ".*"),
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_MODIFIED_AT + " > ?",
            arrayOf(TimestampHelper.getUtcStringFromTimestamp(lastExportTimeStamp!!)),
            AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID,
            null,
            null
        )
        try {
            while (cursor.moveToNext()) {
                accountsList.add(buildModelInstance(cursor))
            }
        } finally {
            cursor.close()
        }
        return accountsList
    }

    /**
     * Retrieves the unique ID of the imbalance account for a particular currency (creates the imbalance account
     * on demand if necessary)
     * @param commodity Commodity for the imbalance account
     * @return String unique ID of the account
     */
    fun getOrCreateImbalanceAccountUID(commodity: Commodity): String? {
        val imbalanceAccountName = getImbalanceAccountName(commodity)
        var uid = findAccountUidByFullName(imbalanceAccountName)
        if (uid == null) {
            val account = Account(imbalanceAccountName, commodity)
            account.mAccountType = AccountType.BANK
            account.mParentAccountUID = orCreateGnuCashRootAccountUID
            account.setMIsHidden(!GnuCashApplication.isDoubleEntryEnabled)
            account.setMColor("#964B00")
            addRecord(account, UpdateMethod.insert)
            uid = account.mUID
        }
        return uid
    }

    /**
     * Returns the GUID of the imbalance account for the commodity
     *
     *
     * This method will not create the imbalance account if it doesn't exist
     *
     * @param commodity Commodity for the imbalance account
     * @return GUID of the account or null if the account doesn't exist yet
     * @see .getOrCreateImbalanceAccountUID
     */
    fun getImbalanceAccountUID(commodity: Commodity): String? {
        val imbalanceAccountName = getImbalanceAccountName(commodity)
        return findAccountUidByFullName(imbalanceAccountName)
    }

    /**
     * Creates the account with the specified name and returns its unique identifier.
     *
     * If a full hierarchical account name is provided, then the whole hierarchy is created and the
     * unique ID of the last account (at bottom) of the hierarchy is returned
     * @param fullName Fully qualified name of the account
     * @param accountType Type to assign to all accounts created
     * @return String unique ID of the account at bottom of hierarchy
     */
    fun createAccountHierarchy(fullName: String, accountType: AccountType?): String? {
        require("" != fullName) { "fullName cannot be empty" }
        val tokens = fullName.trim { it <= ' ' }
            .split(ACCOUNT_NAME_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var uid = orCreateGnuCashRootAccountUID
        var parentName: String? = ""
        val accountsList = ArrayList<Account>()
        for (token in tokens) {
            parentName += token
            val parentUID = findAccountUidByFullName(parentName)
            if (parentUID != null) { //the parent account exists, don't recreate
                uid = parentUID
            } else {
                val account = Account(token)
                account.mAccountType = accountType!!
                account.mParentAccountUID = uid //set its parent
                account.mFullName = parentName
                accountsList.add(account)
                uid = account.mUID
            }
            parentName += ACCOUNT_NAME_SEPARATOR
        }
        if (accountsList.size > 0) {
            bulkAddRecords(accountsList, UpdateMethod.insert)
        }
        // if fullName is not empty, loop will be entered and then uid will never be null
        return uid
    }

    /**
     * Returns the unique ID of the opening balance account or creates one if necessary
     * @return String unique ID of the opening balance account
     */
    val orCreateOpeningBalanceAccountUID: String?
        get() {
            val openingBalanceAccountName = openingBalanceAccountFullName
            var uid = findAccountUidByFullName(openingBalanceAccountName)
            if (uid == null) {
                uid = createAccountHierarchy(openingBalanceAccountName, AccountType.EQUITY)
            }
            return uid
        }

    /**
     * Finds an account unique ID by its full name
     * @param fullName Fully qualified name of the account
     * @return String unique ID of the account or null if no match is found
     */
    fun findAccountUidByFullName(fullName: String?): String? {
        val c = mDb.query(
            AccountEntry.TABLE_NAME, arrayOf(AccountEntry.COLUMN_UID),
            AccountEntry.COLUMN_FULL_NAME + "= ?", arrayOf(fullName),
            null, null, null, "1"
        )
        return try {
            if (c.moveToNext()) {
                c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_UID))
            } else {
                null
            }
        } finally {
            c.close()
        }
    }

    /**
     * Returns a cursor to all account records in the database.
     * GnuCash ROOT accounts and hidden accounts will **not** be included in the result set
     * @return [Cursor] to all account records
     */
    override fun fetchAllRecords(): Cursor {
        Log.v(LOG_TAG, "Fetching all accounts from db")
        val selection = AccountEntry.COLUMN_HIDDEN + " = 0 AND " + AccountEntry.COLUMN_TYPE + " != ?"
        return mDb.query(
            AccountEntry.TABLE_NAME,
            null,
            selection, arrayOf(AccountType.ROOT.name),
            null, null,
            AccountEntry.COLUMN_NAME + " ASC"
        )
    }

    /**
     * Returns a cursor to all account records in the database ordered by full name.
     * GnuCash ROOT accounts and hidden accounts will not be included in the result set.
     * @return [Cursor] to all account records
     */
    fun fetchAllRecordsOrderedByFullName(): Cursor {
        Log.v(LOG_TAG, "Fetching all accounts from db")
        val selection = AccountEntry.COLUMN_HIDDEN + " = 0 AND " + AccountEntry.COLUMN_TYPE + " != ?"
        return mDb.query(
            AccountEntry.TABLE_NAME,
            null,
            selection, arrayOf(AccountType.ROOT.name),
            null, null,
            AccountEntry.COLUMN_FULL_NAME + " ASC"
        )
    }

    /**
     * Returns a Cursor set of accounts which fulfill `where`
     * and ordered by `orderBy`
     * @param where SQL WHERE statement without the 'WHERE' itself
     * @param whereArgs args to where clause
     * @param orderBy orderBy clause
     * @return Cursor set of accounts which fulfill `where`
     */
    fun fetchAccounts(where: String?, whereArgs: Array<String?>?, orderBy: String?): Cursor {
        var orderBy = orderBy
        if (orderBy == null) {
            orderBy = AccountEntry.COLUMN_NAME + " ASC"
        }
        Log.v(LOG_TAG, "Fetching all accounts from db where $where order by $orderBy")
        return mDb.query(
            AccountEntry.TABLE_NAME,
            null, where, whereArgs, null, null,
            orderBy
        )
    }

    /**
     * Returns a Cursor set of accounts which fulfill `where`
     *
     * This method returns the accounts list sorted by the full account name
     * @param where SQL WHERE statement without the 'WHERE' itself
     * @param whereArgs where args
     * @return Cursor set of accounts which fulfill `where`
     */
    fun fetchAccountsOrderedByFullName(where: String, whereArgs: Array<String?>?): Cursor {
        Log.v(LOG_TAG, "Fetching all accounts from db where $where")
        return mDb.query(
            AccountEntry.TABLE_NAME,
            null, where, whereArgs, null, null,
            AccountEntry.COLUMN_FULL_NAME + " ASC"
        )
    }

    /**
     * Returns a Cursor set of accounts which fulfill `where`
     *
     * This method returns the favorite accounts first, sorted by name, and then the other accounts,
     * sorted by name.
     * @param where SQL WHERE statement without the 'WHERE' itself
     * @param whereArgs where args
     * @return Cursor set of accounts which fulfill `where`
     */
    fun fetchAccountsOrderedByFavoriteAndFullName(where: String, whereArgs: Array<String?>?): Cursor {
        Log.v(LOG_TAG, "Fetching all accounts from db where $where order by Favorite then Name")
        return mDb.query(
            AccountEntry.TABLE_NAME,
            null, where, whereArgs, null, null,
            AccountEntry.COLUMN_FAVORITE + " DESC, " + AccountEntry.COLUMN_FULL_NAME + " ASC"
        )
    }

    /**
     * Returns the balance of an account while taking sub-accounts into consideration
     * @return Account Balance of an account including sub-accounts
     */
    fun getAccountBalance(accountUID: String): Money {
        return computeBalance(accountUID, -1, -1)
    }

    /**
     * Returns the balance of an account within the specified time range while taking sub-accounts into consideration
     * @param accountUID the account's UUID
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp the end timestamp of the time range
     * @return the balance of an account within the specified range including sub-accounts
     */
    fun getAccountBalance(accountUID: String, startTimestamp: Long, endTimestamp: Long): Money {
        return computeBalance(accountUID, startTimestamp, endTimestamp)
    }

    /**
     * Compute the account balance for all accounts with the specified type within a specific duration
     * @param accountType Account Type for which to compute balance
     * @param startTimestamp Begin time for the duration in milliseconds
     * @param endTimestamp End time for duration in milliseconds
     * @return Account balance
     */
    fun getAccountBalance(accountType: AccountType, startTimestamp: Long, endTimestamp: Long): Money {
        val cursor = fetchAccounts(AccountEntry.COLUMN_TYPE + "= ?", arrayOf(accountType.name), null)
        val accountUidList: MutableList<String> = ArrayList()
        while (cursor.moveToNext()) {
            val accountUID = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_UID))
            accountUidList.add(accountUID)
        }
        cursor.close()
        val hasDebitNormalBalance = accountType.hasDebitNormalBalance()
        val currencyCode = GnuCashApplication.defaultCurrencyCode!!
        Log.d(LOG_TAG, "all account list : " + accountUidList.size)
        val splitsDbAdapter = mTransactionsAdapter.splitDbAdapter
        return if (startTimestamp == -1L && endTimestamp == -1L)
            splitsDbAdapter.computeSplitBalance(accountUidList, currencyCode, hasDebitNormalBalance)
        else
            splitsDbAdapter.computeSplitBalance(accountUidList, currencyCode, hasDebitNormalBalance, startTimestamp, endTimestamp)
    }

    /**
     * Returns the account balance for all accounts types specified
     * @param accountTypes List of account types
     * @param start Begin timestamp for transactions
     * @param end End timestamp of transactions
     * @return Money balance of the account types
     */
    fun getAccountBalance(accountTypes: List<AccountType>, start: Long, end: Long): Money {
        var balance = createZeroInstance(GnuCashApplication.defaultCurrencyCode!!)
        for (accountType in accountTypes) {
            balance = balance.add(getAccountBalance(accountType, start, end))
        }
        return balance
    }

    private fun computeBalance(accountUID: String, startTimestamp: Long, endTimestamp: Long): Money {
        Log.d(LOG_TAG, "Computing account balance for account ID $accountUID")
        val currencyCode = mTransactionsAdapter.getAccountCurrencyCode(accountUID)
        val hasDebitNormalBalance = getAccountType(accountUID).hasDebitNormalBalance()
        val accountsList: MutableList<String?> = getDescendantAccountUIDs(
            accountUID,
            null, null
        )
        accountsList.add(0, accountUID)
        Log.d(LOG_TAG, "all account list : " + accountsList.size)
        val splitsDbAdapter = mTransactionsAdapter.splitDbAdapter
        return if (startTimestamp == -1L && endTimestamp == -1L) splitsDbAdapter.computeSplitBalance(
            accountsList,
            currencyCode,
            hasDebitNormalBalance
        ) else splitsDbAdapter.computeSplitBalance(
            accountsList,
            currencyCode,
            hasDebitNormalBalance,
            startTimestamp,
            endTimestamp
        )
    }

    /**
     * Returns the balance of account list within the specified time range. The default currency
     * takes as base currency.
     * @param accountUIDList list of account UIDs
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp the end timestamp of the time range
     * @return Money balance of account list
     */
    fun getAccountsBalance(accountUIDList: List<String?>, startTimestamp: Long, endTimestamp: Long): Money {
        val currencyCode = GnuCashApplication.defaultCurrencyCode!!
        val balance = createZeroInstance(currencyCode)
        if (accountUIDList.isEmpty()) return balance
        val hasDebitNormalBalance = getAccountType(accountUIDList[0]!!).hasDebitNormalBalance()
        val splitsDbAdapter = mTransactionsAdapter.splitDbAdapter
        val splitSum = if (startTimestamp == -1L && endTimestamp == -1L) splitsDbAdapter.computeSplitBalance(
            accountUIDList,
            currencyCode,
            hasDebitNormalBalance
        ) else splitsDbAdapter.computeSplitBalance(
            accountUIDList,
            currencyCode,
            hasDebitNormalBalance,
            startTimestamp,
            endTimestamp
        )
        return balance.add(splitSum)
    }

    /**
     * Retrieve all descendant accounts of an account
     * Note, in filtering, once an account is filtered out, all its descendants
     * will also be filtered out, even they don't meet the filter where
     * @param accountUID The account to retrieve descendant accounts
     * @param where      Condition to filter accounts
     * @param whereArgs  Condition args to filter accounts
     * @return The descendant accounts list.
     */
    fun getDescendantAccountUIDs(
        accountUID: String?,
        where: String?,
        whereArgs: Array<String?>?
    ): MutableList<String?> {
        // accountsList will hold accountUID with all descendant accounts.
        // accountsListLevel will hold descendant accounts of the same level
        val accountsList = ArrayList<String?>()
        val accountsListLevel = ArrayList<String?>()
        accountsListLevel.add(accountUID)
        while (true) {
            val cursor = mDb.query(
                AccountEntry.TABLE_NAME, arrayOf(AccountEntry.COLUMN_UID),
                AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " IN ( '" + TextUtils.join(
                    "' , '",
                    accountsListLevel
                ) + "' )" +
                        if (where == null) "" else " AND $where",
                whereArgs, null, null, null
            )
            accountsListLevel.clear()
            if (cursor != null) {
                try {
                    val columnIndex = cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_UID)
                    while (cursor.moveToNext()) {
                        accountsListLevel.add(cursor.getString(columnIndex))
                    }
                } finally {
                    cursor.close()
                }
            }
            if (accountsListLevel.size > 0) {
                accountsList.addAll(accountsListLevel)
            } else {
                break
            }
        }
        return accountsList
    }

    /**
     * Returns a cursor to the dataset containing sub-accounts of the account with record ID `accoundId`
     * @param accountUID GUID of the parent account
     * @return [Cursor] to the sub accounts data set
     */
    fun fetchSubAccounts(accountUID: String): Cursor {
        Log.v(LOG_TAG, "Fetching sub accounts for account id $accountUID")
        val selection = (AccountEntry.COLUMN_HIDDEN + " = 0 AND "
                + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " = ?")
        return mDb.query(
            AccountEntry.TABLE_NAME,
            null,
            selection, arrayOf(accountUID), null, null, AccountEntry.COLUMN_NAME + " ASC"
        )
    }

    /**
     * Returns the top level accounts i.e. accounts with no parent or with the GnuCash ROOT account as parent
     * @return Cursor to the top level accounts
     */
    fun fetchTopLevelAccounts(): Cursor {
        //condition which selects accounts with no parent, whose UID is not ROOT and whose type is not ROOT
        return fetchAccounts(
            "(" + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " IS NULL OR "
                    + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " = ?) AND "
                    + AccountEntry.COLUMN_HIDDEN + " = 0 AND "
                    + AccountEntry.COLUMN_TYPE + " != ?", arrayOf(orCreateGnuCashRootAccountUID, AccountType.ROOT.name),
            AccountEntry.COLUMN_NAME + " ASC"
        )
    }

    /**
     * Returns a cursor to accounts which have recently had transactions added to them
     * @return Cursor to recently used accounts
     */
    fun fetchRecentAccounts(numberOfRecent: Int): Cursor {
        return mDb.query(
            TransactionEntry.TABLE_NAME
                    + " LEFT OUTER JOIN " + SplitEntry.TABLE_NAME + " ON "
                    + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
                    + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID
                    + " , " + AccountEntry.TABLE_NAME + " ON " + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID
                    + " = " + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID,
            arrayOf(AccountEntry.TABLE_NAME + ".*"),
            AccountEntry.COLUMN_HIDDEN + " = 0",
            null,
            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID,  //groupby
            null,  //haveing
            "MAX ( " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " ) DESC",  // order
            numberOfRecent.toString() // limit;
        )
    }

    /**
     * Fetches favorite accounts from the database
     * @return Cursor holding set of favorite accounts
     */
    fun fetchFavoriteAccounts(): Cursor {
        Log.v(LOG_TAG, "Fetching favorite accounts from db")
        val condition = AccountEntry.COLUMN_FAVORITE + " = 1"
        return mDb.query(
            AccountEntry.TABLE_NAME,
            null, condition, null, null, null,
            AccountEntry.COLUMN_NAME + " ASC"
        )
    }// No ROOT exits, create a new one

    /**
     * Returns the GnuCash ROOT account UID if one exists (or creates one if necessary).
     *
     * In GnuCash desktop account structure, there is a root account (which is not visible in the UI) from which
     * other top level accounts derive. GnuCash Android also enforces a ROOT account now
     * @return Unique ID of the GnuCash root account.
     */
    val orCreateGnuCashRootAccountUID: String?
        get() {
            val cursor = fetchAccounts(AccountEntry.COLUMN_TYPE + "= ?", arrayOf(AccountType.ROOT.name), null)
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_UID))
                }
            } finally {
                cursor.close()
            }
            // No ROOT exits, create a new one
            val rootAccount = Account("ROOT Account", CommoditiesDbAdapter(mDb).getCommodity("USD")!!)
            rootAccount.mAccountType = AccountType.ROOT
            rootAccount.mFullName = ROOT_ACCOUNT_FULL_NAME
            rootAccount.setMIsHidden(true)
            rootAccount.setMIsPlaceHolderAccount(true)
            val contentValues = ContentValues()
            contentValues.put(AccountEntry.COLUMN_UID, rootAccount.mUID)
            contentValues.put(AccountEntry.COLUMN_NAME, rootAccount.mName)
            contentValues.put(AccountEntry.COLUMN_FULL_NAME, rootAccount.mFullName)
            contentValues.put(AccountEntry.COLUMN_TYPE, rootAccount.mAccountType.name)
            contentValues.put(AccountEntry.COLUMN_HIDDEN, if (rootAccount.isHidden) 1 else 0)
            val defaultCurrencyCode = GnuCashApplication.defaultCurrencyCode
            contentValues.put(AccountEntry.COLUMN_CURRENCY, defaultCurrencyCode)
            contentValues.put(AccountEntry.COLUMN_COMMODITY_UID, getCommodityUID(defaultCurrencyCode!!))
            Log.i(LOG_TAG, "Creating ROOT account")
            mDb.insert(AccountEntry.TABLE_NAME, null, contentValues)
            return rootAccount.mUID
        }

    /**
     * Returns the number of accounts for which the account with ID `accoundId` is a first level parent
     * @param accountUID String Unique ID (GUID) of the account
     * @return Number of sub accounts
     */
    fun getSubAccountCount(accountUID: String): Int {
        //TODO: at some point when API level 11 and above only is supported, use DatabaseUtils.queryNumEntries
        val queryCount = ("SELECT COUNT(*) FROM " + AccountEntry.TABLE_NAME + " WHERE "
                + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " = ?")
        val cursor = mDb.rawQuery(queryCount, arrayOf(accountUID))
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count
    }

    /**
     * Returns currency code of account with database ID `id`
     * @param uid GUID of the account
     * @return Currency code of the account
     */
    fun getMMnemonic(uid: String?): String {
        return getAccountCurrencyCode(uid!!)
    }

    /**
     * Returns the simple name of the account with unique ID `accountUID`.
     * @param accountUID Unique identifier of the account
     * @return Name of the account as String
     * @throws java.lang.IllegalArgumentException if accountUID does not exist
     * @see .getFullyQualifiedAccountName
     */
    fun getAccountName(accountUID: String?): String {
        return getAttribute(accountUID!!, AccountEntry.COLUMN_NAME)
    }

    /**
     * Returns the default transfer account record ID for the account with UID `accountUID`
     * @param accountID Database ID of the account record
     * @return Record ID of default transfer account
     */
    fun getDefaultTransferAccountID(accountID: Long): Long {
        val cursor = mDb.query(
            AccountEntry.TABLE_NAME, arrayOf(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID),
            AccountEntry._ID + " = " + accountID,
            null, null, null, null
        )
        return try {
            if (cursor.moveToNext()) {
                val uid = cursor.getString(
                    cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID)
                )
                uid?.let { getID(it) } ?: 0
            } else {
                0
            }
        } finally {
            cursor.close()
        }
    }

    /**
     * Returns the full account name including the account hierarchy (parent accounts)
     * @param accountUID Unique ID of account
     * @return Fully qualified (with parent hierarchy) account name
     */
    fun getFullyQualifiedAccountName(accountUID: String?): String {
        val accountName = getAccountName(accountUID)
        val parentAccountUID = getParentAccountUID(accountUID!!)
        if (parentAccountUID == null || parentAccountUID.equals(orCreateGnuCashRootAccountUID, ignoreCase = true)) {
            return accountName
        }
        val parentAccountName = getFullyQualifiedAccountName(parentAccountUID)
        return parentAccountName + ACCOUNT_NAME_SEPARATOR + accountName
    }

    /**
     * get account's full name directly from DB
     * @param accountUID the account to retrieve full name
     * @return full name registered in DB
     */
    fun getAccountFullName(accountUID: String): String {
        val cursor = mDb.query(
            AccountEntry.TABLE_NAME, arrayOf(AccountEntry.COLUMN_FULL_NAME),
            AccountEntry.COLUMN_UID + " = ?", arrayOf(accountUID),
            null, null, null
        )
        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_FULL_NAME))
            }
        } finally {
            cursor.close()
        }
        throw IllegalArgumentException("account UID: $accountUID does not exist")
    }

    /**
     * Returns `true` if the account with unique ID `accountUID` is a placeholder account.
     * @param accountUID Unique identifier of the account
     * @return `true` if the account is a placeholder account, `false` otherwise
     */
    fun isPlaceholderAccount(accountUID: String?): Boolean {
        val isPlaceholder = getAttribute(accountUID!!, AccountEntry.COLUMN_PLACEHOLDER)
        return isPlaceholder.toInt() == 1
    }

    /**
     * Convenience method, resolves the account unique ID and calls [.isPlaceholderAccount]
     * @param accountUID GUID of the account
     * @return `true` if the account is hidden, `false` otherwise
     */
    fun isHiddenAccount(accountUID: String?): Boolean {
        val isHidden = getAttribute(accountUID!!, AccountEntry.COLUMN_HIDDEN)
        return isHidden.toInt() == 1
    }

    /**
     * Returns true if the account is a favorite account, false otherwise
     * @param accountUID GUID of the account
     * @return `true` if the account is a favorite account, `false` otherwise
     */
    fun isFavoriteAccount(accountUID: String?): Boolean {
        val isFavorite = getAttribute(accountUID!!, AccountEntry.COLUMN_FAVORITE)
        return isFavorite.toInt() == 1
    }

    /**
     * Updates all opening balances to the current account balances
     */
    val allOpeningBalanceTransactions: List<Transaction>
        get() {
            val cursor = fetchAccounts(null, null, null)
            val openingTransactions: MutableList<Transaction> = ArrayList()
            try {
                val splitsDbAdapter = mTransactionsAdapter.splitDbAdapter
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(AccountEntry._ID))
                    val accountUID = getUID(id)
                    val currencyCode = getMMnemonic(accountUID)
                    val accountList = ArrayList<String>()
                    accountList.add(accountUID!!)
                    val balance = splitsDbAdapter.computeSplitBalance(
                        accountList,
                        currencyCode, getAccountType(accountUID).hasDebitNormalBalance()
                    )
                    if (balance.asBigDecimal().compareTo(BigDecimal(0)) == 0) continue
                    val transaction = Transaction(
                        GnuCashApplication.appContext?.getString(R.string.account_name_opening_balances)
                    )
                    transaction.mNotes = getAccountName(accountUID)
                    transaction.mCommodity = getInstance(currencyCode)
                    val transactionType = typeForBalance(
                        getAccountType(accountUID),
                        balance.isNegative
                    )
                    val split = Split(balance, accountUID)
                    split.mSplitType = transactionType
                    transaction.addSplit(split)
                    transaction.addSplit(split.createPair(orCreateOpeningBalanceAccountUID))
                    transaction.mIsExported = true
                    openingTransactions.add(transaction)
                }
            } finally {
                cursor.close()
            }
            return openingTransactions
        }

    /**
     * Returns the list of commodities in use in the database.
     *
     *
     * This is not the same as the list of all available commodities.
     *
     * @return List of commodities in use
     */
    val commoditiesInUse: List<Commodity>
        get() {
            val cursor = mDb.query(
                true, AccountEntry.TABLE_NAME, arrayOf(AccountEntry.COLUMN_CURRENCY),
                null, null, null, null, null, null
            )
            val commodityList: MutableList<Commodity> = ArrayList()
            try {
                while (cursor.moveToNext()) {
                    val currencyCode = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_CURRENCY))
                    commodityList.add(mCommoditiesDbAdapter.getCommodity(currencyCode)!!)
                }
            } finally {
                cursor.close()
            }
            return commodityList
        }

    /**
     * Deletes all accounts, transactions (and their splits) from the database.
     * Basically empties all 3 tables, so use with care ;)
     */
    override fun deleteAllRecords(): Int {
        // Relies "ON DELETE CASCADE" takes too much time
        // It take more than 300s to complete the deletion on my dataset without
        // clearing the split table first, but only needs a little more that 1s
        // if the split table is cleared first.
        mDb.delete(DatabaseSchema.PriceEntry.TABLE_NAME, null, null)
        mDb.delete(SplitEntry.TABLE_NAME, null, null)
        mDb.delete(TransactionEntry.TABLE_NAME, null, null)
        mDb.delete(DatabaseSchema.ScheduledActionEntry.TABLE_NAME, null, null)
        mDb.delete(DatabaseSchema.BudgetAmountEntry.TABLE_NAME, null, null)
        mDb.delete(DatabaseSchema.BudgetEntry.TABLE_NAME, null, null)
        mDb.delete(DatabaseSchema.RecurrenceEntry.TABLE_NAME, null, null)
        return mDb.delete(AccountEntry.TABLE_NAME, null, null)
    }

    override fun deleteRecord(uid: String): Boolean {
        val result = super.deleteRecord(uid)
        if (result) {
            val contentValues = ContentValues()
            contentValues.putNull(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID)
            mDb.update(
                mTableName, contentValues,
                AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + "=?", arrayOf(uid)
            )
        }
        return result
    }

    fun getTransactionMaxSplitNum(accountUID: String): Int {
        val cursor = mDb.query(
            "trans_extra_info", arrayOf("MAX(trans_split_count)"),
            "trans_acct_t_uid IN ( SELECT DISTINCT " + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID +
                    " FROM trans_split_acct WHERE " + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_UID +
                    " = ? )", arrayOf(accountUID),
            null,
            null,
            null
        )
        return try {
            if (cursor.moveToFirst()) {
                cursor.getLong(0).toInt()
            } else {
                0
            }
        } finally {
            cursor.close()
        }
    }

    companion object {
        /**
         * Separator used for account name hierarchies between parent and child accounts
         */
        const val ACCOUNT_NAME_SEPARATOR = ":"

        /**
         * ROOT account full name.
         * should ensure the ROOT account's full name will always sort before any other
         * account's full name.
         */
        const val ROOT_ACCOUNT_FULL_NAME = " "

        /**
         * Returns an application-wide instance of this database adapter
         * @return Instance of Accounts db adapter
         */
        @JvmStatic
        val instance: AccountsDbAdapter
            get() = GnuCashApplication.accountsDbAdapter!!
        val imbalanceAccountPrefix: String
            get() = GnuCashApplication.appContext?.getString(R.string.imbalance_account_name) + "-"

        /**
         * Returns the imbalance account where to store transactions which are not double entry.
         *
         * @param commodity Commodity of the transaction
         * @return Imbalance account name
         */
        fun getImbalanceAccountName(commodity: Commodity): String {
            return imbalanceAccountPrefix + commodity.mMnemonic
        }//German locale has no parent Equity account

        /**
         * Get the name of the default account for opening balances for the current locale.
         * For the English locale, it will be "Equity:Opening Balances"
         * @return Fully qualified account name of the opening balances account
         */
        val openingBalanceAccountFullName: String
            get() {
                val context = GnuCashApplication.appContext
                val parentEquity = context?.getString(R.string.account_name_equity)!!.trim { it <= ' ' }
                //German locale has no parent Equity account
                return if (parentEquity.isNotEmpty()) {
                    (parentEquity + ACCOUNT_NAME_SEPARATOR
                            + context.getString(R.string.account_name_opening_balances))
                } else context.getString(R.string.account_name_opening_balances)
            }

        /**
         * Returns the account color for the active account as an Android resource ID.
         *
         *
         * Basically, if we are in a top level account, use the default title color.
         * but propagate a parent account's title color to children who don't have own color
         *
         * @param accountUID GUID of the account
         * @return Android resource ID representing the color which can be directly set to a view
         */
        @JvmStatic
        fun getActiveAccountColorResource(accountUID: String): Int {
            val accountsDbAdapter = instance
            var colorCode: String? = null
            var iColor = -1
            var parentAccountUID: String? = accountUID
            while (parentAccountUID != null) {
                colorCode = accountsDbAdapter.getAccountColorCode(accountsDbAdapter.getID(parentAccountUID))
                if (colorCode != null) {
                    iColor = Color.parseColor(colorCode)
                    break
                }
                parentAccountUID = accountsDbAdapter.getParentAccountUID(parentAccountUID)
            }
            if (colorCode == null) {
                iColor = GnuCashApplication.appContext!!.resources!!.getColor(R.color.theme_primary)
            }
            return iColor
        }
    }
}