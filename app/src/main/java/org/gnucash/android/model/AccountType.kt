/*
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

package org.gnucash.android.model

/**
 * The type of account
 * This are the different types specified by the OFX format and
 * they are currently not used except for exporting
 * @author Xilin Jia <https://github.com/XilinJia> []Kotlin code created (Copyright (C) 2022)]
 */
enum class AccountType {
    CASH(TransactionType.DEBIT), BANK(TransactionType.DEBIT), CREDIT, ASSET(TransactionType.DEBIT), LIABILITY, INCOME, EXPENSE(
        TransactionType.DEBIT
    ),
    PAYABLE, RECEIVABLE(TransactionType.DEBIT), EQUITY, CURRENCY, STOCK(TransactionType.DEBIT), MUTUAL(TransactionType.DEBIT), TRADING, ROOT;
    /**
     * Returns the type of normal balance this account possesses
     * @return TransactionType balance of the account type
     */
    /**
     * Indicates that this type of normal balance the account type has
     *
     * To increase the value of an account with normal balance of credit, one would credit the account.
     * To increase the value of an account with normal balance of debit, one would likewise debit the account.
     */
    var mNormalBalance = TransactionType.CREDIT
        private set

    constructor(normalBalance: TransactionType) {
        mNormalBalance = normalBalance
    }

    constructor() {
        //nothing to see here, move along
    }

    fun hasDebitNormalBalance(): Boolean {
        return mNormalBalance == TransactionType.DEBIT
    }
}