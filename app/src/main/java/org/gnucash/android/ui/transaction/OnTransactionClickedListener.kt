/*
 * Copyright (c) 2012 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.transaction

/**
 * Interface for implemented by activities which wish to be notified when
 * an action has been requested on a transaction (either creation or edit)
 * This is typically used for Fragment-to-Activity communication
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
interface OnTransactionClickedListener {
    /**
     * Callback for creating a new transaction
     * @param accountUID GUID of the account in which to create the new transaction
     */
    fun createNewTransaction(accountUID: String)

    /**
     * Callback request to edit a transaction
     * @param transactionUID GUID of the transaction to be edited
     */
    fun editTransaction(transactionUID: String)
}