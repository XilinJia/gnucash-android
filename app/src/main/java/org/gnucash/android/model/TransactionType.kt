/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
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
 * Type of transaction, a credit or a debit
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Jesse Shieh <jesse.shieh.pub></jesse.shieh.pub>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
enum class TransactionType {
    DEBIT, CREDIT;

    private var opposite: TransactionType? = null

    /**
     * Inverts the transaction type.
     *
     * [TransactionType.CREDIT] becomes [TransactionType.DEBIT] and vice versa
     * @return Inverted transaction type
     */
    fun invert(): TransactionType? {
        return opposite
    }

    companion object {
        init {
            DEBIT.opposite = CREDIT
            CREDIT.opposite = DEBIT
        }
    }
}