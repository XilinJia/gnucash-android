/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import android.widget.Toast
import com.crashlytics.android.Crashlytics
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema.*
import org.gnucash.android.model.Commodity
import org.xml.sax.SAXException
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import javax.xml.parsers.ParserConfigurationException

/**
 * Helper class for managing the SQLite database.
 * Creates the database and handles upgrades
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class DatabaseHelper
/**
 * Constructor
 * @param context Application context
 * @param databaseName Name of the database
 */
    (context: Context?, databaseName: String?) :
    SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        createDatabaseTables(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys=ON")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var oldVersion = oldVersion
        Log.i(
            LOG_TAG, "Upgrading database from version "
                    + oldVersion + " to " + newVersion
        )
        Toast.makeText(GnuCashApplication.appContext, "Upgrading GnuCash database", Toast.LENGTH_SHORT).show()
        /*
        * NOTE: In order to modify the database, create a new static method in the MigrationHelper class
        * called upgradeDbToVersion<#>, e.g. int upgradeDbToVersion10(SQLiteDatabase) in order to upgrade to version 10.
        * The upgrade method should return the new (upgraded) database version as the return value.
        * Then all you need to do is increment the DatabaseSchema.DATABASE_VERSION to the appropriate number to trigger an upgrade.
        */require(oldVersion <= newVersion) { "Database downgrades are not supported at the moment" }
        while (oldVersion < newVersion) {
            oldVersion = try {
                val method = MigrationHelper::class.java.getDeclaredMethod(
                    "upgradeDbToVersion" + (oldVersion + 1),
                    SQLiteDatabase::class.java
                )
                val result = method.invoke(null, db)
                result.toString().toInt()
            } catch (e: NoSuchMethodException) {
                val msg = String.format(
                    "Database upgrade method upgradeToVersion%d(SQLiteDatabase) definition not found ",
                    newVersion
                )
                Log.e(LOG_TAG, msg, e)
                Crashlytics.log(msg)
                Crashlytics.logException(e)
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                val msg = String.format(
                    "Database upgrade to version %d failed. The upgrade method is inaccessible ",
                    newVersion
                )
                Log.e(LOG_TAG, msg, e)
                Crashlytics.log(msg)
                Crashlytics.logException(e)
                throw RuntimeException(e)
            } catch (e: InvocationTargetException) {
                Crashlytics.logException(e.targetException)
                throw RuntimeException(e.targetException)
            }
        }
    }

    /**
     * Creates the tables in the database and import default commodities into the database
     * @param db Database instance
     */
    private fun createDatabaseTables(db: SQLiteDatabase) {
        Log.i(LOG_TAG, "Creating database tables")
        db.execSQL(ACCOUNTS_TABLE_CREATE)
        db.execSQL(TRANSACTIONS_TABLE_CREATE)
        db.execSQL(SPLITS_TABLE_CREATE)
        db.execSQL(SCHEDULED_ACTIONS_TABLE_CREATE)
        db.execSQL(COMMODITIES_TABLE_CREATE)
        db.execSQL(PRICES_TABLE_CREATE)
        db.execSQL(RECURRENCE_TABLE_CREATE)
        db.execSQL(BUDGETS_TABLE_CREATE)
        db.execSQL(BUDGET_AMOUNTS_TABLE_CREATE)
        val createAccountUidIndex = ("CREATE UNIQUE INDEX '" + AccountEntry.INDEX_UID + "' ON "
                + AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_UID + ")")
        val createTransactionUidIndex = ("CREATE UNIQUE INDEX '" + TransactionEntry.INDEX_UID + "' ON "
                + TransactionEntry.TABLE_NAME + "(" + TransactionEntry.COLUMN_UID + ")")
        val createSplitUidIndex = ("CREATE UNIQUE INDEX '" + SplitEntry.INDEX_UID + "' ON "
                + SplitEntry.TABLE_NAME + "(" + SplitEntry.COLUMN_UID + ")")
        val createScheduledEventUidIndex = ("CREATE UNIQUE INDEX '" + ScheduledActionEntry.INDEX_UID
                + "' ON " + ScheduledActionEntry.TABLE_NAME + "(" + ScheduledActionEntry.COLUMN_UID + ")")
        val createCommodityUidIndex = ("CREATE UNIQUE INDEX '" + CommodityEntry.INDEX_UID
                + "' ON " + CommodityEntry.TABLE_NAME + "(" + CommodityEntry.COLUMN_UID + ")")
        val createPriceUidIndex = ("CREATE UNIQUE INDEX '" + PriceEntry.INDEX_UID
                + "' ON " + PriceEntry.TABLE_NAME + "(" + PriceEntry.COLUMN_UID + ")")
        val createBudgetUidIndex = ("CREATE UNIQUE INDEX '" + BudgetEntry.INDEX_UID
                + "' ON " + BudgetEntry.TABLE_NAME + "(" + BudgetEntry.COLUMN_UID + ")")
        val createBudgetAmountUidIndex = ("CREATE UNIQUE INDEX '" + BudgetAmountEntry.INDEX_UID
                + "' ON " + BudgetAmountEntry.TABLE_NAME + "(" + BudgetAmountEntry.COLUMN_UID + ")")
        val createRecurrenceUidIndex = ("CREATE UNIQUE INDEX '" + RecurrenceEntry.INDEX_UID
                + "' ON " + RecurrenceEntry.TABLE_NAME + "(" + RecurrenceEntry.COLUMN_UID + ")")
        db.execSQL(createAccountUidIndex)
        db.execSQL(createTransactionUidIndex)
        db.execSQL(createSplitUidIndex)
        db.execSQL(createScheduledEventUidIndex)
        db.execSQL(createCommodityUidIndex)
        db.execSQL(createPriceUidIndex)
        db.execSQL(createBudgetUidIndex)
        db.execSQL(createRecurrenceUidIndex)
        db.execSQL(createBudgetAmountUidIndex)
        try {
            MigrationHelper.importCommodities(db)
        } catch (e: SAXException) {
            Log.e(LOG_TAG, "Error loading currencies into the database")
            e.printStackTrace()
            throw RuntimeException(e)
        } catch (e: ParserConfigurationException) {
            Log.e(LOG_TAG, "Error loading currencies into the database")
            e.printStackTrace()
            throw RuntimeException(e)
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Error loading currencies into the database")
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }

    companion object {
        /**
         * Tag for logging
         */
        @JvmField
        val LOG_TAG = DatabaseHelper::class.java.name

        /**
         * SQL statement to create the accounts table in the database
         */
        private val ACCOUNTS_TABLE_CREATE = ("create table " + AccountEntry.TABLE_NAME + " ("
                + AccountEntry._ID + " integer primary key autoincrement, "
                + AccountEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                + AccountEntry.COLUMN_NAME + " varchar(255) not null, "
                + AccountEntry.COLUMN_TYPE + " varchar(255) not null, "
                + AccountEntry.COLUMN_CURRENCY + " varchar(255) not null, "
                + AccountEntry.COLUMN_COMMODITY_UID + " varchar(255) not null, "
                + AccountEntry.COLUMN_DESCRIPTION + " varchar(255), "
                + AccountEntry.COLUMN_COLOR_CODE + " varchar(255), "
                + AccountEntry.COLUMN_FAVORITE + " tinyint default 0, "
                + AccountEntry.COLUMN_HIDDEN + " tinyint default 0, "
                + AccountEntry.COLUMN_FULL_NAME + " varchar(255), "
                + AccountEntry.COLUMN_PLACEHOLDER + " tinyint default 0, "
                + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " varchar(255), "
                + AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + " varchar(255), "
                + AccountEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + AccountEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " //            + "FOREIGN KEY (" 	+ AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + ") REFERENCES " + AccountEntry.TABLE_NAME + " (" + AccountEntry.COLUMN_UID + ") ON DELETE SET NULL, "
                + "FOREIGN KEY (" + AccountEntry.COLUMN_COMMODITY_UID + ") REFERENCES " + CommodityEntry.TABLE_NAME + " (" + CommodityEntry.COLUMN_UID + ") "
                + ");" + createUpdatedAtTrigger(AccountEntry.TABLE_NAME))

        /**
         * SQL statement to create the transactions table in the database
         */
        private val TRANSACTIONS_TABLE_CREATE = ("create table " + TransactionEntry.TABLE_NAME + " ("
                + TransactionEntry._ID + " integer primary key autoincrement, "
                + TransactionEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                + TransactionEntry.COLUMN_DESCRIPTION + " varchar(255), "
                + TransactionEntry.COLUMN_NOTES + " text, "
                + TransactionEntry.COLUMN_TIMESTAMP + " integer not null, "
                + TransactionEntry.COLUMN_EXPORTED + " tinyint default 0, "
                + TransactionEntry.COLUMN_TEMPLATE + " tinyint default 0, "
                + TransactionEntry.COLUMN_CURRENCY + " varchar(255) not null, "
                + TransactionEntry.COLUMN_COMMODITY_UID + " varchar(255) not null, "
                + TransactionEntry.COLUMN_SCHEDX_ACTION_UID + " varchar(255), "
                + TransactionEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + TransactionEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "FOREIGN KEY (" + TransactionEntry.COLUMN_SCHEDX_ACTION_UID + ") REFERENCES " + ScheduledActionEntry.TABLE_NAME + " (" + ScheduledActionEntry.COLUMN_UID + ") ON DELETE SET NULL, "
                + "FOREIGN KEY (" + TransactionEntry.COLUMN_COMMODITY_UID + ") REFERENCES " + CommodityEntry.TABLE_NAME + " (" + CommodityEntry.COLUMN_UID + ") "
                + ");" + createUpdatedAtTrigger(TransactionEntry.TABLE_NAME))

        /**
         * SQL statement to create the transaction splits table
         */
        private val SPLITS_TABLE_CREATE = ("CREATE TABLE " + SplitEntry.TABLE_NAME + " ("
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
                + ");" + createUpdatedAtTrigger(SplitEntry.TABLE_NAME))
        val SCHEDULED_ACTIONS_TABLE_CREATE = ("CREATE TABLE " + ScheduledActionEntry.TABLE_NAME + " ("
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
                + ");" + createUpdatedAtTrigger(ScheduledActionEntry.TABLE_NAME))
        val COMMODITIES_TABLE_CREATE = ("CREATE TABLE " + CommodityEntry.TABLE_NAME + " ("
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
                + ");" + createUpdatedAtTrigger(CommodityEntry.TABLE_NAME))

        /**
         * SQL statement to create the commodity prices table
         */
        private val PRICES_TABLE_CREATE = ("CREATE TABLE " + PriceEntry.TABLE_NAME + " ("
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
                + ");" + createUpdatedAtTrigger(PriceEntry.TABLE_NAME))
        private val BUDGETS_TABLE_CREATE = ("CREATE TABLE " + BudgetEntry.TABLE_NAME + " ("
                + BudgetEntry._ID + " integer primary key autoincrement, "
                + BudgetEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                + BudgetEntry.COLUMN_NAME + " varchar(255) not null, "
                + BudgetEntry.COLUMN_DESCRIPTION + " varchar(255), "
                + BudgetEntry.COLUMN_RECURRENCE_UID + " varchar(255) not null, "
                + BudgetEntry.COLUMN_NUM_PERIODS + " integer, "
                + BudgetEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + BudgetEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "FOREIGN KEY (" + BudgetEntry.COLUMN_RECURRENCE_UID + ") REFERENCES " + RecurrenceEntry.TABLE_NAME + " (" + RecurrenceEntry.COLUMN_UID + ") "
                + ");" + createUpdatedAtTrigger(BudgetEntry.TABLE_NAME))
        private val BUDGET_AMOUNTS_TABLE_CREATE = ("CREATE TABLE " + BudgetAmountEntry.TABLE_NAME + " ("
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
                + ");" + createUpdatedAtTrigger(BudgetAmountEntry.TABLE_NAME))
        private val RECURRENCE_TABLE_CREATE = ("CREATE TABLE " + RecurrenceEntry.TABLE_NAME + " ("
                + RecurrenceEntry._ID + " integer primary key autoincrement, "
                + RecurrenceEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                + RecurrenceEntry.COLUMN_MULTIPLIER + " integer not null default 1, "
                + RecurrenceEntry.COLUMN_PERIOD_TYPE + " varchar(255) not null, "
                + RecurrenceEntry.COLUMN_BYDAY + " varchar(255), "
                + RecurrenceEntry.COLUMN_PERIOD_START + " timestamp not null, "
                + RecurrenceEntry.COLUMN_PERIOD_END + " timestamp, "
                + RecurrenceEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + RecurrenceEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP); "
                + createUpdatedAtTrigger(RecurrenceEntry.TABLE_NAME))

        /**
         * Creates an update trigger to update the updated_at column for all records in the database.
         * This has to be run per table, and is currently appended to the create table statement.
         * @param tableName Name of table on which to create trigger
         * @return SQL statement for creating trigger
         */
        @JvmStatic
        fun createUpdatedAtTrigger(tableName: String): String {
            return ("CREATE TRIGGER update_time_trigger "
                    + "  AFTER UPDATE ON " + tableName + " FOR EACH ROW"
                    + "  BEGIN " + "UPDATE " + tableName
                    + "  SET " + CommonColumns.COLUMN_MODIFIED_AT + " = CURRENT_TIMESTAMP"
                    + "  WHERE OLD." + CommonColumns.COLUMN_UID + " = NEW." + CommonColumns.COLUMN_UID + ";"
                    + "  END;")
        }
    }
}