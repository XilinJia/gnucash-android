package org.gnucash.android.db.adapter

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.util.Pair
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema.PriceEntry
import org.gnucash.android.model.Price
import org.gnucash.android.util.TimestampHelper

/**
 * Database adapter for prices
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class PricesDbAdapter
/**
 * Opens the database adapter with an existing database
 * @param db SQLiteDatabase object
 */
    (db: SQLiteDatabase?) : DatabaseAdapter<Price>(
    db!!, PriceEntry.TABLE_NAME, arrayOf(
        PriceEntry.COLUMN_COMMODITY_UID,
        PriceEntry.COLUMN_CURRENCY_UID,
        PriceEntry.COLUMN_DATE,
        PriceEntry.COLUMN_SOURCE,
        PriceEntry.COLUMN_TYPE,
        PriceEntry.COLUMN_VALUE_NUM,
        PriceEntry.COLUMN_VALUE_DENOM
    )
) {
    override fun setBindings(stmt: SQLiteStatement, model: Price): SQLiteStatement {
        stmt.clearBindings()
        stmt.bindString(1, model.mCommodityUID)
        stmt.bindString(2, model.mCurrencyUID)
        stmt.bindString(3, model.mDate.toString())
        if (model.mSource != null) {
            stmt.bindString(4, model.mSource)
        }
        if (model.mType != null) {
            stmt.bindString(5, model.mType)
        }
        stmt.bindLong(6, model.getMValueNum())
        stmt.bindLong(7, model.getMValueDenom())
        stmt.bindString(8, model.mUID)
        return stmt
    }

    override fun buildModelInstance(cursor: Cursor): Price {
        val commodityUID = cursor.getString(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_COMMODITY_UID))
        val currencyUID = cursor.getString(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_CURRENCY_UID))
        val dateString = cursor.getString(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_DATE))
        val source = cursor.getString(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_SOURCE))
        val type = cursor.getString(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_TYPE))
        val valueNum = cursor.getLong(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_VALUE_NUM))
        val valueDenom = cursor.getLong(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_VALUE_DENOM))
        val price = Price(commodityUID, currencyUID)
        price.mDate = TimestampHelper.getTimestampFromUtcString(dateString)
        price.mSource = source
        price.mType = type
        price.setMValueNum(valueNum)
        price.setMValueDenom(valueDenom)
        populateBaseModelAttributes(cursor, price)
        return price
    }

    /**
     * Get the price for commodity / currency pair.
     * The price can be used to convert from one commodity to another. The 'commodity' is the origin and the 'currency' is the target for the conversion.
     *
     *
     * Pair is used instead of Price object because we must sometimes invert the commodity/currency in DB,
     * rendering the Price UID invalid.
     *
     * @param commodityUID GUID of the commodity which is starting point for conversion
     * @param currencyUID GUID of target commodity for the conversion
     *
     * @return The numerator/denominator pair for commodity / currency pair
     */
    fun getPrice(commodityUID: String, currencyUID: String): Pair<Long, Long> {
        val pairZero = Pair(0L, 0L)
        if (commodityUID == currencyUID) {
            return Pair(1L, 1L)
        }
        val cursor = mDb.query(
            PriceEntry.TABLE_NAME,
            null,  // the commodity and currency can be swapped
            "( " + PriceEntry.COLUMN_COMMODITY_UID + " = ? AND " + PriceEntry.COLUMN_CURRENCY_UID + " = ? ) OR ( "
                    + PriceEntry.COLUMN_COMMODITY_UID + " = ? AND " + PriceEntry.COLUMN_CURRENCY_UID + " = ? )",
            arrayOf(commodityUID, currencyUID, currencyUID, commodityUID),
            null,
            null,  // only get the latest price
            PriceEntry.COLUMN_DATE + " DESC",
            "1"
        )
        return try {
            if (cursor.moveToNext()) {
                val commodityUIDdb = cursor.getString(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_COMMODITY_UID))
                var valueNum = cursor.getLong(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_VALUE_NUM))
                var valueDenom = cursor.getLong(cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_VALUE_DENOM))
                if (valueNum < 0 || valueDenom < 0) {
                    // this should not happen
                    return pairZero
                }
                if (commodityUIDdb != commodityUID) {
                    // swap Num and denom
                    val t = valueNum
                    valueNum = valueDenom
                    valueDenom = t
                }
                Pair(valueNum, valueDenom)
            } else {
                pairZero
            }
        } finally {
            cursor.close()
        }
    }

    companion object {
        @JvmStatic
        val instance: PricesDbAdapter
            get() = GnuCashApplication.pricesDbAdapter!!
    }
}