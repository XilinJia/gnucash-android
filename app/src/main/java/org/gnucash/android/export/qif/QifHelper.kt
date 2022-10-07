/*
 * Copyright (c) 2013 - 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.export.qif

import org.gnucash.android.model.AccountType
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
object QifHelper {
    /*
    Prefixes for the QIF file
     */
    const val PAYEE_PREFIX = "P"
    const val DATE_PREFIX = "D"
    const val AMOUNT_PREFIX = "T"
    const val MEMO_PREFIX = "M"
    const val CATEGORY_PREFIX = "L"
    const val SPLIT_MEMO_PREFIX = "E"
    const val SPLIT_AMOUNT_PREFIX = "$"
    const val SPLIT_CATEGORY_PREFIX = "S"
    const val SPLIT_PERCENTAGE_PREFIX = "%"
    const val ACCOUNT_HEADER = "!Account"
    const val ACCOUNT_NAME_PREFIX = "N"
    const val INTERNAL_CURRENCY_PREFIX = "*"
    const val ENTRY_TERMINATOR = "^"
    private val QIF_DATE_FORMATTER = SimpleDateFormat("yyyy/M/d")

    /**
     * Formats the date for QIF in the form d MMMM YYYY.
     * For example 25 January 2013
     * @param timeMillis Time in milliseconds since epoch
     * @return Formatted date from the time
     */
    fun formatDate(timeMillis: Long): String {
        val date = Date(timeMillis)
        return QIF_DATE_FORMATTER.format(date)
    }

    /**
     * Returns the QIF header for the transaction based on the account type.
     * By default, the QIF cash header is used
     * @param accountType AccountType of account
     * @return QIF header for the transactions
     */
    fun getQifHeader(accountType: AccountType?): String {
        return when (accountType) {
            AccountType.CASH -> "!Type:Cash"
            AccountType.BANK -> "!Type:Bank"
            AccountType.CREDIT -> "!Type:CCard"
            AccountType.ASSET -> "!Type:Oth A"
            AccountType.LIABILITY -> "!Type:Oth L"
            else -> "!Type:Cash"
        }
    }

    fun getQifHeader(accountType: String?): String {
        return getQifHeader(AccountType.valueOf(accountType!!))
    }
}