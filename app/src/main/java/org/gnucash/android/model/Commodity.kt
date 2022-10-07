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

import org.gnucash.android.db.adapter.CommoditiesDbAdapter

/**
 * Commodities are the currencies used in the application.
 * At the moment only ISO4217 currencies are supported
 * @author Xilin Jia <https://github.com/XilinJia> []Kotlin code created (Copyright (C) 2022)]
 */
class Commodity(
    var mFullname: String?,
    /**
     * This is the currency code for ISO4217 currencies
     */
    var mMnemonic: String, smallestFraction: Int
) : BaseModel() {
    enum class Namespace {
        ISO4217
    } //Namespace for commodities

    var mNamespace = Namespace.ISO4217
    /**
     * Returns the mnemonic, or currency code for ISO4217 currencies
     * @return Mnemonic of the commodity
     */
    /**
     * Alias for [.getMMnemonic]
     * @return ISO 4217 code for this commodity
     */
    //    public String getMMnemonic(){
    //        return getMMnemonic();
    //    }
    var mCusip: String? = null
    var mLocalSymbol: String? = ""
    /**
     * Returns the smallest fraction supported by the commodity as a power of 10.
     *
     * i.e. for commodities with no fractions, 1 is returned, for commodities with 2 fractions, 100 is returned
     * @return Smallest fraction as power of 10
     */
    /**
     * Sets the smallest fraction for the commodity.
     *
     * The fraction is a power of 10. So commodities with 2 fraction digits, have fraction of 10^2 = 100.<br></br>
     * If the parameter is any other value, a default fraction of 100 will be set
     * @param smallestFraction Smallest fraction as power of ten
     * @throws IllegalArgumentException if the smallest fraction is not a power of 10
     */
    var mSmallestFraction = 0
    var mQuoteFlag = 0

    /**
     * Create a new commodity
     * @param fullname Official full name of the currency
     * @param mnemonic Official abbreviated designation for the currency
     * @param smallestFraction Number of sub-units that the basic commodity can be divided into, as power of 10. e.g. 10^&lt;number_of_fraction_digits&gt;
     */
    init {
        mSmallestFraction = smallestFraction
    }

    /**
     * Returns the symbol for this commodity.
     *
     * Normally this would be the local symbol, but in it's absence, the mnemonic (currency code)
     * is returned.
     * @return
     */
    val symbol: String
        get() = if (mLocalSymbol == null || mLocalSymbol!!.isEmpty()) {
            mMnemonic
        } else mLocalSymbol!!

    /**
     * Returns the (minimum) number of digits that this commodity supports in its fractional part
     *
     * For any unsupported values for the smallest fraction, a default value of 2 is returned.
     * Supported values for the smallest fraction are powers of 10 i.e. 1, 10, 100 etc
     * @return Number of digits in fraction
     * @see .getMSmallestFraction
     */
    fun smallestFractionDigits(): Int {
        return if (mSmallestFraction == 0) {
            0
        } else {
            Integer.numberOfTrailingZeros(mSmallestFraction)
        }
    }

    /**
     * Returns the full name of the currency, or the currency code if there is no full name
     * @return String representation of the commodity
     */
    override fun toString(): String {
        return if (mFullname == null || mFullname!!.isEmpty()) mMnemonic else mFullname!!
    }

    /**
     * Overrides [BaseModel.equals] to compare only the currency codes of the commodity.
     *
     * Two commodities are considered equal if they have the same currency code
     * @param other Commodity instance to compare
     * @return `true` if both instances have same currency code, `false` otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val commodity = other as Commodity
        return mMnemonic == commodity.mMnemonic
    }

    override fun hashCode(): Int {
        return mMnemonic.hashCode()
    }

    companion object {
        /**
         * Default commodity for device locale
         *
         * This value is set when a new application instance is created in [GnuCashApplication.onCreate].
         * The value initialized here is just a placeholder for unit tests
         */
        @JvmField
        var DEFAULT_COMMODITY =
            Commodity("US Dollars", "USD", 100) //this value is a stub. Will be overwritten when the app is launched
        @JvmField
        var USD = Commodity("", "USD", 100)
        @JvmField
        var EUR = Commodity("", "EUR", 100)
        @JvmField
        var GBP = Commodity("", "GBP", 100)
        @JvmField
        var CHF = Commodity("", "CHF", 100)
        @JvmField
        var CAD = Commodity("", "CAD", 100)
        @JvmField
        var JPY = Commodity("", "JPY", 1)
        @JvmField
        var AUD = Commodity("", "AUD", 100)

        /**
         * Returns an instance of commodity for the specified currencyCode
         * @param currencyCode ISO 4217 currency code (3-letter)
         */
        @JvmStatic
        fun getInstance(currencyCode: String?): Commodity {
            return when (currencyCode) {
                "USD" -> USD
                "EUR" -> EUR
                "GBP" -> GBP
                "CHF" -> CHF
                "JPY" -> JPY
                "AUD" -> AUD
                "CAD" -> CAD
                else -> CommoditiesDbAdapter.instance.getCommodity(currencyCode!!)!!
            }
        }
    }
}