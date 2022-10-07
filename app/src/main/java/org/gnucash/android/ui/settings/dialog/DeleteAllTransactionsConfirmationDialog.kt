/*
 * Copyright (c) 2013 - 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.ui.settings.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.shouldSaveOpeningBalances
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Transaction
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity.Companion.updateAllWidgets
import org.gnucash.android.util.BackupManager.backupActiveBook

/**
 * Confirmation dialog for deleting all transactions
 *
 * @author ngewif <ngewif></ngewif>@gmail.com>
 * @author Yongxin Wang <fefe.wyx></fefe.wyx>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */

class DeleteAllTransactionsConfirmationDialog : DoubleConfirmationDialog() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialogBuilder
            .setIcon(android.R.drawable.ic_delete)
            .setTitle(R.string.title_confirm_delete).setMessage(R.string.msg_delete_all_transactions_confirmation)
            .setPositiveButton(
                R.string.alert_dialog_ok_delete
            ) { _, _ ->
                backupActiveBook()
                val context: Context? = activity
                val accountsDbAdapter = AccountsDbAdapter.instance
                var openingBalances: List<Transaction> = ArrayList()
                val preserveOpeningBalances = shouldSaveOpeningBalances(false)
                if (preserveOpeningBalances) {
                    openingBalances = accountsDbAdapter.allOpeningBalanceTransactions
                }
                val transactionsDbAdapter = TransactionsDbAdapter.instance
                val count = transactionsDbAdapter.deleteAllNonTemplateTransactions()
                Log.i("DeleteDialog", String.format("Deleted %d transactions successfully", count))
                if (preserveOpeningBalances) {
                    transactionsDbAdapter.bulkAddRecords(openingBalances, DatabaseAdapter.UpdateMethod.insert)
                }
                Toast.makeText(context, R.string.toast_all_transactions_deleted, Toast.LENGTH_SHORT).show()
                updateAllWidgets(activity!!)
            }.create()
    }

    companion object {
        @JvmStatic
        fun newInstance(): DeleteAllTransactionsConfirmationDialog {
            return DeleteAllTransactionsConfirmationDialog()
        }
    }
}