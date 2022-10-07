/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.common

/**
 * Collection of constants which are passed across multiple pieces of the UI (fragments, activities, dialogs)
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class UxArgument private constructor() {
    //prevent initialization of instances of this class
    init {
        //prevent even the native class from calling the ctor
        throw AssertionError()
    }

    companion object {
        /**
         * Key for passing the transaction GUID as parameter in a bundle
         */
        const val SELECTED_TRANSACTION_UID = "selected_transaction_uid"

        /**
         * Key for passing list of IDs selected transactions as an argument in a bundle or intent
         */
        const val SELECTED_TRANSACTION_IDS = "selected_transactions"

        /**
         * Key for the origin account as argument when moving accounts
         */
        const val ORIGIN_ACCOUNT_UID = "origin_acccount_uid"

        /**
         * Key for checking whether the passcode is enabled or not
         */
        const val ENABLED_PASSCODE = "enabled_passcode"

        /**
         * Key for disabling the passcode
         */
        const val DISABLE_PASSCODE = "disable_passcode"

        /**
         * Key for storing the passcode
         */
        const val PASSCODE = "passcode"

        /**
         * Key for skipping the passcode screen. Use this only when there is no other choice.
         */
        const val SKIP_PASSCODE_SCREEN = "skip_passcode_screen"

        /**
         * Amount passed as a string
         */
        const val AMOUNT_STRING = "starting_amount"

        /**
         * Class caller, which will be launched after the unlocking
         */
        const val PASSCODE_CLASS_CALLER = "passcode_class_caller"

        /**
         * Key for passing the account unique ID as argument to UI
         */
        const val SELECTED_ACCOUNT_UID = "account_uid"

        /**
         * Key for passing whether a widget should hide the account balance or not
         */
        const val HIDE_ACCOUNT_BALANCE_IN_WIDGET = "hide_account_balance"

        /**
         * Key for passing argument for the parent account GUID.
         */
        const val PARENT_ACCOUNT_UID = "parent_account_uid"

        /**
         * Key for passing the scheduled action UID to the transactions editor
         */
        const val SCHEDULED_ACTION_UID = "scheduled_action_uid"

        /**
         * Type of form displayed in the [FormActivity]
         */
        const val FORM_TYPE = "form_type"

        /**
         * List of splits which have been created using the split editor
         */
        const val SPLIT_LIST = "split_list"

        /**
         * GUID of a budget
         */
        const val BUDGET_UID = "budget_uid"

        /**
         * List of budget amounts (as csv)
         */
        const val BUDGET_AMOUNT_LIST = "budget_amount_list"

        /**
         * GUID of a book which is relevant for a specific action
         */
        const val BOOK_UID = "book_uid"
    }
}