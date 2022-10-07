/*
 * Copyright (c) 2013 Ngewi Fet <ngewif@gmail.com>
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
import android.os.Bundle
import android.widget.Toast
import org.gnucash.android.R
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.instance
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity.Companion.updateAllWidgets
import org.gnucash.android.util.BackupManager.backupActiveBook

/**
 * Confirmation dialog for deleting all accounts from the system.
 * This class currently only works with HONEYCOMB and above.
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class DeleteAllAccountsConfirmationDialog : DoubleConfirmationDialog() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialogBuilder
            .setIcon(android.R.drawable.ic_delete)
            .setTitle(R.string.title_confirm_delete).setMessage(R.string.confirm_delete_all_accounts)
            .setPositiveButton(
                R.string.alert_dialog_ok_delete
            ) { _, _ ->
                val context = dialog!!.context
                backupActiveBook()
                instance.deleteAllRecords()
                Toast.makeText(context, R.string.toast_all_accounts_deleted, Toast.LENGTH_SHORT).show()
                updateAllWidgets(context)
            }
            .create()
    }

    companion object {
        @JvmStatic
        fun newInstance(): DeleteAllAccountsConfirmationDialog {
            return DeleteAllAccountsConfirmationDialog()
        }
    }
}