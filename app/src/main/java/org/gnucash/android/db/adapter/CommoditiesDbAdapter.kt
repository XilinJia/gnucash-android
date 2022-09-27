package org.gnucash.android.db.adapter

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.util.Log
import com.crashlytics.android.Crashlytics
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.DatabaseSchema.CommodityEntry
import org.gnucash.android.model.Commodity

/**
 * Database adapter for [org.gnucash.android.model.Commodity]
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class CommoditiesDbAdapter(db: SQLiteDatabase?) : DatabaseAdapter<Commodity>(
    db!!, CommodityEntry.TABLE_NAME, arrayOf(
        CommodityEntry.COLUMN_FULLNAME,
        CommodityEntry.COLUMN_NAMESPACE,
        CommodityEntry.COLUMN_MNEMONIC,
        CommodityEntry.COLUMN_LOCAL_SYMBOL,
        CommodityEntry.COLUMN_CUSIP,
        CommodityEntry.COLUMN_SMALLEST_FRACTION,
        CommodityEntry.COLUMN_QUOTE_FLAG
    )
) {
    /**
     * Opens the database adapter with an existing database
     *
     * @param db        SQLiteDatabase object
     */
    init {
        /**
         * initialize commonly used commodities
         */
        Commodity.USD = getCommodity("USD")!!
        Commodity.EUR = getCommodity("EUR")!!
        Commodity.GBP = getCommodity("GBP")!!
        Commodity.CHF = getCommodity("CHF")!!
        Commodity.CAD = getCommodity("CAD")!!
        Commodity.JPY = getCommodity("JPY")!!
        Commodity.AUD = getCommodity("AUD")!!
        Commodity.DEFAULT_COMMODITY = getCommodity(GnuCashApplication.defaultCurrencyCode!!)!!
    }

    protected override fun setBindings(stmt: SQLiteStatement, commodity: Commodity): SQLiteStatement {
        stmt.clearBindings()
        stmt.bindString(1, commodity.mFullname)
        stmt.bindString(2, commodity.mNamespace.name)
        stmt.bindString(3, commodity.mMnemonic)
        stmt.bindString(4, commodity.mLocalSymbol)
        stmt.bindString(5, commodity.mCusip)
        stmt.bindLong(6, commodity.mSmallestFraction.toLong())
        stmt.bindLong(7, commodity.mQuoteFlag.toLong())
        stmt.bindString(8, commodity.mUID)
        return stmt
    }

    override fun buildModelInstance(cursor: Cursor): Commodity {
        val fullname = cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_FULLNAME))
        val mnemonic = cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_MNEMONIC))
        val namespace = cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_NAMESPACE))
        val cusip = cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_CUSIP))
        val localSymbol = cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_LOCAL_SYMBOL))
        val fraction = cursor.getInt(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_SMALLEST_FRACTION))
        val quoteFlag = cursor.getInt(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_QUOTE_FLAG))
        val commodity = Commodity(fullname, mnemonic, fraction)
        commodity.mNamespace = Commodity.Namespace.valueOf(namespace)
        commodity.mCusip = cusip
        commodity.mQuoteFlag = quoteFlag
        commodity.mLocalSymbol = localSymbol
        populateBaseModelAttributes(cursor, commodity)
        return commodity
    }

    override fun fetchAllRecords(): Cursor {
        return mDb.query(
            mTableName, null, null, null, null, null,
            CommodityEntry.COLUMN_FULLNAME + " ASC"
        )
    }

    /**
     * Fetches all commodities in the database sorted in the specified order
     * @param orderBy SQL statement for orderBy without the ORDER_BY itself
     * @return Cursor holding all commodity records
     */
    fun fetchAllRecords(orderBy: String?): Cursor {
        return mDb.query(
            mTableName, null, null, null, null, null,
            orderBy
        )
    }

    /**
     * Returns the commodity associated with the ISO4217 currency code
     * @param currencyCode 3-letter currency code
     * @return Commodity associated with code or null if none is found
     */
    fun getCommodity(currencyCode: String): Commodity? {
        val cursor = fetchAllRecords(CommodityEntry.COLUMN_MNEMONIC + "=?", arrayOf(currencyCode), null)
        var commodity: Commodity? = null
        if (cursor.moveToNext()) {
            commodity = buildModelInstance(cursor)
        } else {
            val msg = "Commodity not found in the database: $currencyCode"
            Log.e(LOG_TAG, msg)
            Crashlytics.log(msg)
        }
        cursor.close()
        return commodity
    }

    fun getMMnemonic(guid: String): String {
        val cursor = mDb.query(
            mTableName, arrayOf(CommodityEntry.COLUMN_MNEMONIC),
            DatabaseSchema.CommonColumns.COLUMN_UID + " = ?", arrayOf(guid),
            null, null, null
        )
        return try {
            if (cursor.moveToNext()) {
                cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_MNEMONIC))
            } else {
                throw IllegalArgumentException("guid $guid not exits in commodity db")
            }
        } finally {
            cursor.close()
        }
    }

    companion object {
        @JvmStatic
        val instance: CommoditiesDbAdapter
            get() = GnuCashApplication.commoditiesDbAdapter!!
    }
}