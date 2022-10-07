
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

package org.gnucash.android.model

import org.gnucash.android.util.TimestampHelper
import java.math.BigDecimal
import java.math.MathContext
import java.sql.Timestamp
import java.text.DecimalFormat
import java.text.NumberFormat

/**
 * Model for commodity prices
 * @author Xilin Jia <https://github.com/XilinJia> []Kotlin code created (Copyright (C) 2022)]
 */
class Price : BaseModel {
    var mCommodityUID: String? = null
    var mCurrencyUID: String? = null
    var mDate: Timestamp
    var mSource: String? = null
    var mType: String? = null
    private var mValueNum: Long = 0
    private var mValueDenom: Long = 0

    constructor() {
        mDate = TimestampHelper.timestampFromNow
    }

    /**
     * Create new instance with the GUIDs of the commodities
     * @param commodityUID GUID of the origin commodity
     * @param currencyUID GUID of the target commodity
     */
    constructor(commodityUID: String?, currencyUID: String?) {
        mCommodityUID = commodityUID
        mCurrencyUID = currencyUID
        mDate = TimestampHelper.timestampFromNow
    }

    /**
     * Create new instance with the GUIDs of the commodities and the specified exchange rate.
     * @param commodity1UID GUID of the origin commodity
     * @param commodity2UID GUID of the target commodity
     * @param exchangeRate exchange rate between the commodities
     */
    constructor(commodity1UID: String?, commodity2UID: String?, exchangeRate: BigDecimal) : this(
        commodity1UID,
        commodity2UID
    ) {
        // Store 0.1234 as 1234/10000
        setMValueNum(exchangeRate.unscaledValue().toLong())
        setMValueDenom(BigDecimal.ONE.scaleByPowerOfTen(exchangeRate.scale()).toLong())
    }

    fun getMValueNum(): Long {
        reduce()
        return mValueNum
    }

    fun setMValueNum(valueNum: Long) {
        mValueNum = valueNum
    }

    fun getMValueDenom(): Long {
        reduce()
        return mValueDenom
    }

    fun setMValueDenom(valueDenom: Long) {
        mValueDenom = valueDenom
    }

    private fun reduce() {
        if (mValueDenom < 0) {
            mValueDenom = -mValueDenom
            mValueNum = -mValueNum
        }
        if (mValueDenom != 0L && mValueNum != 0L) {
            var num1 = mValueNum
            if (num1 < 0) {
                num1 = -num1
            }
            var num2 = mValueDenom
            val commonDivisor: Long
            while (true) {
                var r = num1 % num2
                if (r == 0L) {
                    commonDivisor = num2
                    break
                }
                num1 = r
                r = num2 % num1
                if (r == 0L) {
                    commonDivisor = num1
                    break
                }
                num2 = r
            }
            mValueNum /= commonDivisor
            mValueDenom /= commonDivisor
        }
    }

    /**
     * Returns the exchange rate as a string formatted with the default locale.
     *
     *
     * It will have up to 6 decimal places.
     *
     *
     * Example: "0.123456"
     */
    override fun toString(): String {
        val numerator = BigDecimal(mValueNum)
        val denominator = BigDecimal(mValueDenom)
        val formatter = NumberFormat.getNumberInstance() as DecimalFormat
        formatter.maximumFractionDigits = 6
        return formatter.format(numerator.divide(denominator, MathContext.DECIMAL32))
    }

    companion object {
        /**
         * String indicating that the price was provided by the user
         */
        const val SOURCE_USER = "user:xfer-dialog"
    }
}