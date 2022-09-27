/*
 * Copyright (c) 2013 - 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.transaction.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Transaction
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity.Companion.updateAllWidgets
import org.gnucash.android.util.BackupManager

/**
 * Displays a delete confirmation dialog for transactions
 * If the transaction ID parameter is 0, then all transactions will be deleted
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class TransactionsDeleteConfirmationDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val title = arguments!!.getInt("title")
        val rowId = arguments!!.getLong(UxArgument.SELECTED_TRANSACTION_IDS)
        val message =
            if (rowId == 0L) R.string.msg_delete_all_transactions_confirmation else R.string.msg_delete_transaction_confirmation
        return AlertDialog.Builder(activity)
            .setIcon(android.R.drawable.ic_delete)
            .setTitle(title).setMessage(message)
            .setPositiveButton(
                R.string.alert_dialog_ok_delete
            ) { dialog, whichButton ->
                val transactionsDbAdapter = TransactionsDbAdapter.instance
                if (rowId == 0L) {
                    BackupManager.backupActiveBook() //create backup before deleting everything
                    var openingBalances: List<Transaction> = ArrayList()
                    val preserveOpeningBalances = GnuCashApplication.shouldSaveOpeningBalances(false)
                    if (preserveOpeningBalances) {
                        openingBalances = AccountsDbAdapter.instance.allOpeningBalanceTransactions
                    }
                    transactionsDbAdapter.deleteAllRecords()
                    if (preserveOpeningBalances) {
                        transactionsDbAdapter.bulkAddRecords(openingBalances, DatabaseAdapter.UpdateMethod.insert)
                    }
                } else {
                    transactionsDbAdapter.deleteRecord(rowId)
                }
                if (targetFragment is Refreshable) {
                    (targetFragment as Refreshable?)!!.refresh()
                }
                updateAllWidgets(activity!!)
            }
            .setNegativeButton(
                R.string.alert_dialog_cancel
            ) { dialog, whichButton -> dismiss() }
            .create()
    }

    companion object {
        fun newInstance(title: Int, id: Long): TransactionsDeleteConfirmationDialogFragment {
            val frag = TransactionsDeleteConfirmationDialogFragment()
            val args = Bundle()
            args.putInt("title", title)
            args.putLong(UxArgument.SELECTED_TRANSACTION_IDS, id)
            frag.arguments = args
            return frag
        }
    }
}