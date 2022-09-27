/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
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
import com.crashlytics.android.Crashlytics
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.*
import org.gnucash.android.model.Commodity.Companion.getInstance
import org.gnucash.android.model.Split.Companion.parseSplit
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.math.BigDecimal
import java.math.MathContext

/**
 * Broadcast receiver responsible for creating transactions received through [Intent]s
 * In order to create a transaction through Intents, broadcast an intent with the arguments needed to
 * create the transaction. Transactions are strongly bound to [Account]s and it is recommended to
 * create an Account for your transaction splits.
 *
 * Remember to declare the appropriate permissions in order to create transactions with Intents.
 * The required permission is "org.gnucash.android.permission.RECORD_TRANSACTION"
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 * @see AccountCreator
 *
 * @see org.gnucash.android.model.Transaction.createIntent
 */
class TransactionRecorder : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(this.javaClass.name, "Received transaction recording intent")
        val args = intent.extras
        val name = args!!.getString(Intent.EXTRA_TITLE)
        val note = args.getString(Intent.EXTRA_TEXT)
        var currencyCode = args.getString(Account.EXTRA_CURRENCY_CODE)
        if (currencyCode == null) currencyCode = Money.DEFAULT_CURRENCY_CODE
        val transaction = Transaction(name)
        transaction.setMTimestamp(System.currentTimeMillis())
        transaction.mNotes = note
        transaction.mCommodity = getInstance(currencyCode)

        //Parse deprecated args for compatibility. Transactions were bound to accounts, now only splits are
        val accountUID = args.getString(Transaction.EXTRA_ACCOUNT_UID)
        if (accountUID != null) {
            val type = TransactionType.valueOf(args.getString(Transaction.EXTRA_TRANSACTION_TYPE)!!)
            var amountBigDecimal = args.getSerializable(Transaction.EXTRA_AMOUNT) as BigDecimal?
            val commodity = CommoditiesDbAdapter.instance.getCommodity(currencyCode)
            amountBigDecimal =
                amountBigDecimal!!.setScale(commodity!!.smallestFractionDigits(), BigDecimal.ROUND_HALF_EVEN).round(
                    MathContext.DECIMAL128
                )
            val amount = Money(amountBigDecimal, getInstance(currencyCode))
            val split = Split(amount, accountUID)
            split.mSplitType = type
            transaction.addSplit(split)
            val transferAccountUID = args.getString(Transaction.EXTRA_DOUBLE_ACCOUNT_UID)
            if (transferAccountUID != null) {
                transaction.addSplit(split.createPair(transferAccountUID))
            }
        }
        val splits = args.getString(Transaction.EXTRA_SPLITS)
        if (splits != null) {
            val stringReader = StringReader(splits)
            val bufferedReader = BufferedReader(stringReader)
            var line: String? = null
            try {
                while (bufferedReader.readLine().also { line = it } != null) {
                    val split = parseSplit(line!!)
                    transaction.addSplit(split)
                }
            } catch (e: IOException) {
                Crashlytics.logException(e)
            }
        }
        TransactionsDbAdapter.instance.addRecord(transaction, DatabaseAdapter.UpdateMethod.insert)
        WidgetConfigurationActivity.updateAllWidgets(context)
    }
}