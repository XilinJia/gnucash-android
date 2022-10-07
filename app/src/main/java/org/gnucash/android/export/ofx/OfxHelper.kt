/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.export.ofx

import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper class with collection of useful method and constants for the OFX export
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
object OfxHelper {
    /**
     * A date formatter used when creating file names for the exported data
     */
    val OFX_DATE_FORMATTER = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

    /**
     * The Transaction ID is usually the client ID sent in a request.
     * Since the data exported is not as a result of a request, we use 0
     */
    const val UNSOLICITED_TRANSACTION_ID = "0"

    /**
     * Header for OFX documents
     */
    const val OFX_HEADER = "OFXHEADER=\"200\" VERSION=\"211\" SECURITY=\"NONE\" OLDFILEUID=\"NONE\" NEWFILEUID=\"NONE\""

    /**
     * SGML header for OFX. Used for compatibility with desktop GnuCash
     */
    const val OFX_SGML_HEADER =
        "ENCODING:UTF-8\nOFXHEADER:100\nDATA:OFXSGML\nVERSION:211\nSECURITY:NONE\nCHARSET:UTF-8\nCOMPRESSION:NONE\nOLDFILEUID:NONE\nNEWFILEUID:NONE"

    /*
    * XML tag name constants for the OFX file
     */
    const val TAG_TRANSACTION_UID = "TRNUID"
    const val TAG_BANK_MESSAGES_V1 = "BANKMSGSRSV1"
    const val TAG_CURRENCY_DEF = "CURDEF"
    const val TAG_BANK_ID = "BANKID"
    const val TAG_ACCOUNT_ID = "ACCTID"
    const val TAG_ACCOUNT_TYPE = "ACCTTYPE"
    const val TAG_BANK_ACCOUNT_FROM = "BANKACCTFROM"
    const val TAG_BALANCE_AMOUNT = "BALAMT"
    const val TAG_DATE_AS_OF = "DTASOF"
    const val TAG_LEDGER_BALANCE = "LEDGERBAL"
    const val TAG_DATE_START = "DTSTART"
    const val TAG_DATE_END = "DTEND"
    const val TAG_TRANSACTION_TYPE = "TRNTYPE"
    const val TAG_DATE_POSTED = "DTPOSTED"
    const val TAG_DATE_USER = "DTUSER"
    const val TAG_TRANSACTION_AMOUNT = "TRNAMT"
    const val TAG_TRANSACTION_FITID = "FITID"
    const val TAG_NAME = "NAME"
    const val TAG_MEMO = "MEMO"
    const val TAG_BANK_ACCOUNT_TO = "BANKACCTTO"
    const val TAG_BANK_TRANSACTION_LIST = "BANKTRANLIST"
    const val TAG_STATEMENT_TRANSACTIONS = "STMTRS"
    const val TAG_STATEMENT_TRANSACTION = "STMTTRN"
    const val TAG_STATEMENT_TRANSACTION_RESPONSE = "STMTTRNRS"

    /**
     * ID which will be used as the bank ID for OFX from this app
     */
    var APP_ID = "org.gnucash.android"

    /**
     * Returns the current time formatted using the pattern in [.OFX_DATE_FORMATTER]
     * @return Current time as a formatted string
     * @see .getOfxFormattedTime
     */
    val formattedCurrentTime: String
        get() = getOfxFormattedTime(System.currentTimeMillis())

    /**
     * Returns a formatted string representation of time in `milliseconds`
     * @param milliseconds Long value representing the time to be formatted
     * @return Formatted string representation of time in `milliseconds`
     */
    fun getOfxFormattedTime(milliseconds: Long): String {
        val date = Date(milliseconds)
        val dateString = OFX_DATE_FORMATTER.format(date)
        val tz = Calendar.getInstance().timeZone
        val offset = tz.rawOffset
        val sign = if (offset > 0) "+" else ""
        return dateString + "[" + sign + (offset / (1000 * 60 * 60) % 24) + ":" + tz.getDisplayName(
            false,
            TimeZone.SHORT,
            Locale.getDefault()
        ) + "]"
    }
}