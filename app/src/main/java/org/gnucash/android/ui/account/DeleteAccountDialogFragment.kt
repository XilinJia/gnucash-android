/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.account

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.DialogFragment
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity
import org.gnucash.android.util.BackupManager
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter

/**
 * Delete confirmation dialog for accounts.
 * It is displayed when deleting an account which has transactions or sub-accounts, and the user
 * has the option to either move the transactions/sub-accounts, or delete them.
 * If an account has no transactions, it is deleted immediately with no confirmation required
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class DeleteAccountDialogFragment : DialogFragment() {
    /**
     * Spinner for selecting the account to move the transactions to
     */
    private var mTransactionsDestinationAccountSpinner: Spinner? = null
    private var mAccountsDestinationAccountSpinner: Spinner? = null

    /**
     * Dialog positive button. Ok to moving the transactions
     */
    private var mOkButton: Button? = null

    /**
     * Cancel button
     */
    private var mCancelButton: Button? = null

    /**
     * GUID of account from which to move the transactions
     */
    private var mOriginAccountUID: String? = null
    private var mMoveAccountsRadioButton: RadioButton? = null
    private var mMoveTransactionsRadioButton: RadioButton? = null
    private var mDeleteAccountsRadioButton: RadioButton? = null
    private var mDeleteTransactionsRadioButton: RadioButton? = null
    private var mTransactionCount = 0
    private var mSubAccountCount = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.CustomDialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_account_delete, container, false)
        val transactionOptionsView = view.findViewById<View>(R.id.transactions_options)
        (transactionOptionsView.findViewById<View>(R.id.title_content) as TextView).setText(R.string.section_header_transactions)
        (transactionOptionsView.findViewById<View>(R.id.description) as TextView).setText(R.string.label_delete_account_transactions_description)
        mDeleteTransactionsRadioButton = transactionOptionsView.findViewById<View>(R.id.radio_delete) as RadioButton
        mDeleteTransactionsRadioButton!!.setText(R.string.label_delete_transactions)
        mMoveTransactionsRadioButton = transactionOptionsView.findViewById<View>(R.id.radio_move) as RadioButton
        mTransactionsDestinationAccountSpinner =
            transactionOptionsView.findViewById<View>(R.id.target_accounts_spinner) as Spinner
        val accountOptionsView = view.findViewById<View>(R.id.accounts_options)
        (accountOptionsView.findViewById<View>(R.id.title_content) as TextView).setText(R.string.section_header_subaccounts)
        (accountOptionsView.findViewById<View>(R.id.description) as TextView).setText(R.string.label_delete_account_subaccounts_description)
        mDeleteAccountsRadioButton = accountOptionsView.findViewById<View>(R.id.radio_delete) as RadioButton
        mDeleteAccountsRadioButton!!.setText(R.string.label_delete_sub_accounts)
        mMoveAccountsRadioButton = accountOptionsView.findViewById<View>(R.id.radio_move) as RadioButton
        mAccountsDestinationAccountSpinner =
            accountOptionsView.findViewById<View>(R.id.target_accounts_spinner) as Spinner
        transactionOptionsView.visibility = if (mTransactionCount > 0) View.VISIBLE else View.GONE
        accountOptionsView.visibility = if (mSubAccountCount > 0) View.VISIBLE else View.GONE
        mCancelButton = view.findViewById<View>(R.id.btn_cancel) as Button
        mOkButton = view.findViewById<View>(R.id.btn_save) as Button
        mOkButton!!.setText(R.string.alert_dialog_ok_delete)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val accountName = AccountsDbAdapter.instance.getAccountName(mOriginAccountUID)
        dialog!!.setTitle(getString(R.string.alert_dialog_ok_delete) + ": " + accountName)
        val accountsDbAdapter = AccountsDbAdapter.instance
        val descendantAccountUIDs: List<String?> =
            accountsDbAdapter.getDescendantAccountUIDs(mOriginAccountUID, null, null)
        val currencyCode = accountsDbAdapter.getMMnemonic(mOriginAccountUID)
        val accountType = accountsDbAdapter.getAccountType(mOriginAccountUID!!)
        val transactionDeleteConditions = ("(" + DatabaseSchema.AccountEntry.COLUMN_UID + " != ? AND "
                + DatabaseSchema.AccountEntry.COLUMN_CURRENCY + " = ? AND "
                + DatabaseSchema.AccountEntry.COLUMN_TYPE + " = ? AND "
                + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0 AND "
                + DatabaseSchema.AccountEntry.COLUMN_UID + " NOT IN ('" + TextUtils.join(
            "','",
            descendantAccountUIDs
        ) + "')"
                + ")")
        var cursor = accountsDbAdapter.fetchAccountsOrderedByFullName(
            transactionDeleteConditions,
            arrayOf(mOriginAccountUID, currencyCode, accountType.name)
        )
        var mCursorAdapter: SimpleCursorAdapter = QualifiedAccountNameCursorAdapter(
            activity, cursor
        )
        mTransactionsDestinationAccountSpinner!!.adapter = mCursorAdapter

        //target accounts for transactions and accounts have different conditions
        val accountMoveConditions = ("(" + DatabaseSchema.AccountEntry.COLUMN_UID + " != ? AND "
                + DatabaseSchema.AccountEntry.COLUMN_CURRENCY + " = ? AND "
                + DatabaseSchema.AccountEntry.COLUMN_TYPE + " = ? AND "
                + DatabaseSchema.AccountEntry.COLUMN_UID + " NOT IN ('" + TextUtils.join(
            "','",
            descendantAccountUIDs
        ) + "')"
                + ")")
        cursor = accountsDbAdapter.fetchAccountsOrderedByFullName(
            accountMoveConditions,
            arrayOf(mOriginAccountUID, currencyCode, accountType.name)
        )
        mCursorAdapter = QualifiedAccountNameCursorAdapter(activity, cursor)
        mAccountsDestinationAccountSpinner!!.adapter = mCursorAdapter
        setListeners()

        //this comes after the listeners because of some useful bindings done there
        if (cursor.count == 0) {
            mMoveAccountsRadioButton!!.isEnabled = false
            mMoveAccountsRadioButton!!.isChecked = false
            mDeleteAccountsRadioButton!!.isChecked = true
            mMoveTransactionsRadioButton!!.isEnabled = false
            mMoveTransactionsRadioButton!!.isChecked = false
            mDeleteTransactionsRadioButton!!.isChecked = true
            mAccountsDestinationAccountSpinner!!.visibility = View.GONE
            mTransactionsDestinationAccountSpinner!!.visibility = View.GONE
        }
    }

    /**
     * Binds click listeners for the dialog buttons
     */
    private fun setListeners() {
        mMoveAccountsRadioButton!!.setOnCheckedChangeListener { _, isChecked ->
            mAccountsDestinationAccountSpinner!!.isEnabled = isChecked
        }
        mMoveTransactionsRadioButton!!.setOnCheckedChangeListener { _, isChecked ->
            mTransactionsDestinationAccountSpinner!!.isEnabled = isChecked
        }
        mCancelButton!!.setOnClickListener { dismiss() }
        mOkButton!!.setOnClickListener {
            BackupManager.backupActiveBook()
            val accountsDbAdapter = AccountsDbAdapter.instance
            if (mTransactionCount > 0 && mMoveTransactionsRadioButton!!.isChecked) {
                val targetAccountId = mTransactionsDestinationAccountSpinner!!.selectedItemId
                //move all the splits
                SplitsDbAdapter.instance.updateRecords(
                    DatabaseSchema.SplitEntry.COLUMN_ACCOUNT_UID + " = ?",
                    arrayOf(mOriginAccountUID!!),
                    DatabaseSchema.SplitEntry.COLUMN_ACCOUNT_UID,
                    accountsDbAdapter.getUID(targetAccountId)
                )
            }
            if (mSubAccountCount > 0 && mMoveAccountsRadioButton!!.isChecked) {
                val targetAccountId = mAccountsDestinationAccountSpinner!!.selectedItemId
                AccountsDbAdapter.instance.reassignDescendantAccounts(
                    mOriginAccountUID!!,
                    accountsDbAdapter.getUID(targetAccountId)!!
                )
            }
            if (GnuCashApplication.isDoubleEntryEnabled) { //reassign splits to imbalance
                TransactionsDbAdapter.instance.deleteTransactionsForAccount(mOriginAccountUID!!)
            }

            //now kill them all!!
            accountsDbAdapter.recursiveDeleteAccount(accountsDbAdapter.getID(mOriginAccountUID!!))
            WidgetConfigurationActivity.updateAllWidgets(activity!!)
            (targetFragment as Refreshable?)!!.refresh()
            dismiss()
        }
    }

    companion object {
        /**
         * Creates new instance of the delete confirmation dialog and provides parameters for it
         * @param accountUID GUID of the account to be deleted
         * @return New instance of the delete confirmation dialog
         */
        fun newInstance(accountUID: String?): DeleteAccountDialogFragment {
            val fragment = DeleteAccountDialogFragment()
            fragment.mOriginAccountUID = accountUID
            fragment.mSubAccountCount = AccountsDbAdapter.instance.getSubAccountCount(accountUID!!)
            fragment.mTransactionCount = TransactionsDbAdapter.instance.getTransactionsCount(accountUID)
            return fragment
        }
    }
}