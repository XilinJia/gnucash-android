/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.ui.util

import android.os.AsyncTask
import android.util.Log
import android.widget.TextView
import com.crashlytics.android.Crashlytics
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.instance
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.sDefaultZero
import org.gnucash.android.ui.transaction.TransactionsActivity.Companion.displayBalance
import java.lang.ref.WeakReference

/**
 * An asynchronous task for computing the account balance of an account.
 * This is done asynchronously because in cases of deeply nested accounts,
 * it can take some time and would block the UI thread otherwise.
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class AccountBalanceTask(balanceTextView: TextView?) : AsyncTask<String, Void, Money>() {
    private val accountBalanceTextViewReference: WeakReference<TextView?>
    private val accountsDbAdapter: AccountsDbAdapter

    init {
        accountBalanceTextViewReference = WeakReference(balanceTextView)
        accountsDbAdapter = instance
    }

    @Deprecated("Deprecated in Java")
    override fun doInBackground(vararg params: String): Money? {
        //if the view for which we are doing this job is dead, kill the job as well
        if (accountBalanceTextViewReference.get() == null) {
            cancel(true)
            return sDefaultZero
        }
        var balance = sDefaultZero
        try {
            balance = accountsDbAdapter.getAccountBalance(params[0], -1, -1)
        } catch (ex: Exception) {
            Log.e(LOG_TAG, "Error computing account balance ", ex)
            Crashlytics.logException(ex)
        }
        return balance
    }

    @Deprecated("Deprecated in Java")
    override fun onPostExecute(balance: Money?) {
        if (accountBalanceTextViewReference.get() != null && balance != null) {
            val balanceTextView = accountBalanceTextViewReference.get()
            if (balanceTextView != null) {
                displayBalance(balanceTextView, balance)
            }
        }
    }

    companion object {
        val LOG_TAG = AccountBalanceTask::class.java.name
    }
}