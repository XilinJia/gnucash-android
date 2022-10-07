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
package org.gnucash.android.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.model.Commodity.Companion.getInstance

/**
 * Broadcast receiver responsible for creating [Account]s received through intents.
 * In order to create an `Account`, you need to broadcast an [Intent] with arguments
 * for the name, currency and optionally, a unique identifier for the account (which should be unique to Gnucash)
 * of the Account to be created. Also remember to set the right mime type so that Android can properly route the Intent
 * **Note** This Broadcast receiver requires the permission "org.gnucash.android.permission.CREATE_ACCOUNT"
 * in order to be able to use Intents to create accounts. So remember to declare it in your manifest
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 * @see {@link Account.EXTRA_CURRENCY_CODE}, {@link Account.MIME_TYPE} {@link Intent.EXTRA_TITLE}, {@link Intent.EXTRA_UID}
 */
class AccountCreator : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("Gnucash", "Received account creation intent")
        val args = intent.extras
        val account = Account(args!!.getString(Intent.EXTRA_TITLE)!!)
        account.mParentAccountUID = args.getString(Account.EXTRA_PARENT_UID)
        val currencyCode = args.getString(Account.EXTRA_CURRENCY_CODE)
        if (currencyCode != null) {
            val commodity = getInstance(currencyCode)
            account.setMCommodity(commodity)
        }
        val uid = args.getString(Intent.EXTRA_UID)
        if (uid != null) account.mUID = uid
        AccountsDbAdapter.instance.addRecord(account, DatabaseAdapter.UpdateMethod.insert)
    }
}