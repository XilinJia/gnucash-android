/*
 * Copyright (c) 2014 - 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.export.xml

import org.gnucash.android.model.Commodity
import org.gnucash.android.ui.transaction.TransactionFormFragment
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Collection of helper tags and methods for Gnc XML export
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Yongxin Wang <fefe.wyx></fefe.wyx>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
object GncXmlHelper {
    const val TAG_GNC_PREFIX = "gnc:"
    const val ATTR_KEY_CD_TYPE = "cd:type"
    const val ATTR_KEY_TYPE = "type"
    const val ATTR_KEY_VERSION = "version"
    const val ATTR_VALUE_STRING = "string"
    const val ATTR_VALUE_NUMERIC = "numeric"
    const val ATTR_VALUE_GUID = "guid"
    const val ATTR_VALUE_BOOK = "book"
    const val ATTR_VALUE_FRAME = "frame"
    const val TAG_GDATE = "gdate"

    /*
    Qualified GnuCash XML tag names
     */
    const val TAG_ROOT = "gnc-v2"
    const val TAG_BOOK = "gnc:book"
    const val TAG_BOOK_ID = "book:id"
    const val TAG_COUNT_DATA = "gnc:count-data"
    const val TAG_COMMODITY = "gnc:commodity"
    const val TAG_COMMODITY_ID = "cmdty:id"
    const val TAG_COMMODITY_SPACE = "cmdty:space"
    const val TAG_ACCOUNT = "gnc:account"
    const val TAG_ACCT_NAME = "act:name"
    const val TAG_ACCT_ID = "act:id"
    const val TAG_ACCT_TYPE = "act:type"
    const val TAG_ACCT_COMMODITY = "act:commodity"
    const val TAG_COMMODITY_SCU = "act:commodity-scu"
    const val TAG_PARENT_UID = "act:parent"
    const val TAG_SLOT_KEY = "slot:key"
    const val TAG_SLOT_VALUE = "slot:value"
    const val TAG_ACCT_SLOTS = "act:slots"
    const val TAG_SLOT = "slot"
    const val TAG_ACCT_DESCRIPTION = "act:description"
    const val TAG_TRANSACTION = "gnc:transaction"
    const val TAG_TRX_ID = "trn:id"
    const val TAG_TRX_CURRENCY = "trn:currency"
    const val TAG_DATE_POSTED = "trn:date-posted"
    const val TAG_TS_DATE = "ts:date"
    const val TAG_DATE_ENTERED = "trn:date-entered"
    const val TAG_TRN_DESCRIPTION = "trn:description"
    const val TAG_TRN_SPLITS = "trn:splits"
    const val TAG_TRN_SPLIT = "trn:split"
    const val TAG_TRN_SLOTS = "trn:slots"
    const val TAG_TEMPLATE_TRANSACTIONS = "gnc:template-transactions"
    const val TAG_SPLIT_ID = "split:id"
    const val TAG_SPLIT_MEMO = "split:memo"
    const val TAG_RECONCILED_STATE = "split:reconciled-state"
    const val TAG_RECONCILED_DATE = "split:recondiled-date"
    const val TAG_SPLIT_ACCOUNT = "split:account"
    const val TAG_SPLIT_VALUE = "split:value"
    const val TAG_SPLIT_QUANTITY = "split:quantity"
    const val TAG_SPLIT_SLOTS = "split:slots"
    const val TAG_PRICEDB = "gnc:pricedb"
    const val TAG_PRICE = "price"
    const val TAG_PRICE_ID = "price:id"
    const val TAG_PRICE_COMMODITY = "price:commodity"
    const val TAG_PRICE_CURRENCY = "price:currency"
    const val TAG_PRICE_TIME = "price:time"
    const val TAG_PRICE_SOURCE = "price:source"
    const val TAG_PRICE_TYPE = "price:type"
    const val TAG_PRICE_VALUE = "price:value"

    /**
     * Periodicity of the recurrence.
     *
     * Only currently used for reading old backup files. May be removed in the future.
     */
    @Deprecated("Use {@link #TAG_GNC_RECURRENCE} instead")
    const val TAG_RECURRENCE_PERIOD = "trn:recurrence_period"
    const val TAG_SCHEDULED_ACTION = "gnc:schedxaction"
    const val TAG_SX_ID = "sx:id"
    const val TAG_SX_NAME = "sx:name"
    const val TAG_SX_ENABLED = "sx:enabled"
    const val TAG_SX_AUTO_CREATE = "sx:autoCreate"
    const val TAG_SX_AUTO_CREATE_NOTIFY = "sx:autoCreateNotify"
    const val TAG_SX_ADVANCE_CREATE_DAYS = "sx:advanceCreateDays"
    const val TAG_SX_ADVANCE_REMIND_DAYS = "sx:advanceRemindDays"
    const val TAG_SX_INSTANCE_COUNT = "sx:instanceCount"
    const val TAG_SX_START = "sx:start"
    const val TAG_SX_LAST = "sx:last"
    const val TAG_SX_END = "sx:end"
    const val TAG_SX_NUM_OCCUR = "sx:num-occur"
    const val TAG_SX_REM_OCCUR = "sx:rem-occur"
    const val TAG_SX_TAG = "sx:tag"
    const val TAG_SX_TEMPL_ACCOUNT = "sx:templ-acct"
    const val TAG_SX_SCHEDULE = "sx:schedule"
    const val TAG_GNC_RECURRENCE = "gnc:recurrence"
    const val TAG_RX_MULT = "recurrence:mult"
    const val TAG_RX_PERIOD_TYPE = "recurrence:period_type"
    const val TAG_RX_START = "recurrence:start"
    const val TAG_BUDGET = "gnc:budget"
    const val TAG_BUDGET_ID = "bgt:id"
    const val TAG_BUDGET_NAME = "bgt:name"
    const val TAG_BUDGET_DESCRIPTION = "bgt:description"
    const val TAG_BUDGET_NUM_PERIODS = "bgt:num-periods"
    const val TAG_BUDGET_RECURRENCE = "bgt:recurrence"
    const val TAG_BUDGET_SLOTS = "bgt:slots"
    const val RECURRENCE_VERSION = "1.0.0"
    const val BOOK_VERSION = "2.0.0"
    val TIME_FORMATTER = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
    @JvmField
    val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    const val KEY_PLACEHOLDER = "placeholder"
    const val KEY_COLOR = "color"
    const val KEY_FAVORITE = "favorite"
    const val KEY_NOTES = "notes"
    const val KEY_EXPORTED = "exported"
    const val KEY_SCHEDX_ACTION = "sched-xaction"
    const val KEY_SPLIT_ACCOUNT_SLOT = "account"
    const val KEY_DEBIT_FORMULA = "debit-formula"
    const val KEY_CREDIT_FORMULA = "credit-formula"
    const val KEY_DEBIT_NUMERIC = "debit-numeric"
    const val KEY_CREDIT_NUMERIC = "credit-numeric"
    const val KEY_FROM_SCHED_ACTION = "from-sched-xaction"
    const val KEY_DEFAULT_TRANSFER_ACCOUNT = "default_transfer_account"

    /**
     * Formats dates for the GnuCash XML format
     * @param milliseconds Milliseconds since epoch
     */
    fun formatDate(milliseconds: Long): String {
        return TIME_FORMATTER.format(Date(milliseconds))
    }

    /**
     * Parses a date string formatted in the format "yyyy-MM-dd HH:mm:ss Z"
     * @param dateString String date representation
     * @return Time in milliseconds since epoch
     * @throws ParseException if the date string could not be parsed e.g. because of different format
     */
    @JvmStatic
    @Throws(ParseException::class)
    fun parseDate(dateString: String): Long {
        val date = TIME_FORMATTER.parse(dateString)
        return date!!.time
    }

    /**
     * Parses amount strings from GnuCash XML into [java.math.BigDecimal]s.
     * The amounts are formatted as 12345/100
     * @param amountString String containing the amount
     * @return BigDecimal with numerical value
     * @throws ParseException if the amount could not be parsed
     */
    @JvmStatic
    @Throws(ParseException::class)
    fun parseSplitAmount(amountString: String): BigDecimal {
        val pos = amountString.indexOf("/")
        if (pos < 0) {
            throw ParseException("Cannot parse money string : $amountString", 0)
        }
        val scale = amountString.length - pos - 2 //do this before, because we could modify the string
        //String numerator = TransactionFormFragment.stripCurrencyFormatting(amountString.substring(0, pos));
        var numerator: String? = amountString.substring(0, pos)
        numerator = TransactionFormFragment.stripCurrencyFormatting(numerator!!)
        val numeratorInt = BigInteger(numerator)
        return BigDecimal(numeratorInt, scale)
    }

    /**
     * Formats money amounts for splits in the format 2550/100
     * @param amount Split amount as BigDecimal
     * @param commodity Commodity of the transaction
     * @return Formatted split amount
     */
    @JvmStatic
    @Deprecated("Just use the values for numerator and denominator which are saved in the database")
    fun formatSplitAmount(amount: BigDecimal, commodity: Commodity): String {
        val denomInt = commodity.mSmallestFraction
        val denom = BigDecimal(denomInt)
        val denomString = denomInt.toString()
        val numerator =
            TransactionFormFragment.stripCurrencyFormatting(amount.multiply(denom).stripTrailingZeros().toPlainString())
        return "$numerator/$denomString"
    }

    /**
     * Format the amount in template transaction splits.
     *
     * GnuCash desktop always formats with a locale dependent format, and that varies per user.<br></br>
     * So we will use the device locale here and hope that the user has the same locale on the desktop GnuCash
     * @param amount Amount to be formatted
     * @return String representation of amount
     */
    fun formatTemplateSplitAmount(amount: BigDecimal?): String {
        //TODO: If we ever implement an application-specific locale setting, use it here as well
        return NumberFormat.getNumberInstance().format(amount)
    }
}