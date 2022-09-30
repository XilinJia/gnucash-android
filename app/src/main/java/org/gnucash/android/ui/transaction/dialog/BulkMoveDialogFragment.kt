/*
 * Copyright (c) 2012 Ngewi Fet <ngewif@gmail.com>
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.DialogFragment
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity.Companion.updateAllWidgets
import org.gnucash.android.ui.transaction.TransactionsActivity
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter

/**
 * Dialog fragment for moving transactions from one account to another
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class BulkMoveDialogFragment : DialogFragment() {
    /**
     * Spinner for selecting the account to move the transactions to
     */
    var mDestinationAccountSpinner: Spinner? = null

    /**
     * Dialog positive button. Ok to moving the transactions
     */
    var mOkButton: Button? = null

    /**
     * Cancel button
     */
    var mCancelButton: Button? = null

    /**
     * Record IDs of the transactions to be moved
     */
    var mTransactionIds: LongArray? = null

    /**
     * GUID of account from which to move the transactions
     */
    var mOriginAccountUID: String? = null

    /**
     * Creates the view and retrieves references to the dialog elements
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.dialog_bulk_move, container, false)
        mDestinationAccountSpinner = v.findViewById<View>(R.id.accounts_list_spinner) as Spinner
        mOkButton = v.findViewById<View>(R.id.btn_save) as Button
        mOkButton!!.setText(R.string.btn_move)
        mCancelButton = v.findViewById<View>(R.id.btn_cancel) as Button
        return v
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.CustomDialog)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        dialog!!.window!!.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        val args = arguments
        mTransactionIds = args!!.getLongArray(UxArgument.SELECTED_TRANSACTION_IDS)
        mOriginAccountUID = args.getString(UxArgument.ORIGIN_ACCOUNT_UID)
        val title = activity!!.getString(
            R.string.title_move_transactions,
            mTransactionIds!!.size
        )
        dialog!!.setTitle(title)
        val accountsDbAdapter = AccountsDbAdapter.instance
        val conditions = ("(" + DatabaseSchema.AccountEntry.COLUMN_UID + " != ? AND "
                + DatabaseSchema.AccountEntry.COLUMN_CURRENCY + " = ? AND "
                + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0 AND "
                + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                + ")")
        val cursor = accountsDbAdapter.fetchAccountsOrderedByFullName(
            conditions,
            arrayOf(mOriginAccountUID, accountsDbAdapter.getMMnemonic(mOriginAccountUID))
        )
        val mCursorAdapter: SimpleCursorAdapter = QualifiedAccountNameCursorAdapter(
            activity, cursor
        )
        mDestinationAccountSpinner!!.adapter = mCursorAdapter
        setListeners()
    }

    /**
     * Binds click listeners for the dialog buttons
     */
    private fun setListeners() {
        mCancelButton!!.setOnClickListener { dismiss() }
        mOkButton!!.setOnClickListener(View.OnClickListener {
            if (mTransactionIds == null) {
                dismiss()
            }
            val dstAccountId = mDestinationAccountSpinner!!.selectedItemId
            val dstAccountUID = AccountsDbAdapter.instance.getUID(dstAccountId)
            val trxnAdapter = TransactionsDbAdapter.instance
            if (trxnAdapter.getAccountCurrencyCode(dstAccountUID!!) != trxnAdapter.getAccountCurrencyCode(
                    mOriginAccountUID!!
                )
            ) {
                Toast.makeText(activity, R.string.toast_incompatible_currency, Toast.LENGTH_LONG).show()
                return@OnClickListener
            }
            val srcAccountUID = (activity as TransactionsActivity?)!!.currentAccountUID
            for (trxnId in mTransactionIds!!) {
                trxnAdapter.moveTransaction(trxnAdapter.getUID(trxnId)!!, srcAccountUID!!, dstAccountUID)
            }
            updateAllWidgets(activity!!)
            (targetFragment as Refreshable?)!!.refresh()
            dismiss()
        })
    }

    companion object {
        /**
         * Create new instance of the bulk move dialog
         * @param transactionIds Array of transaction database record IDs
         * @param originAccountUID Account from which to move the transactions
         * @return BulkMoveDialogFragment instance with arguments set
         */
        fun newInstance(transactionIds: LongArray?, originAccountUID: String?): BulkMoveDialogFragment {
            val args = Bundle()
            args.putLongArray(UxArgument.SELECTED_TRANSACTION_IDS, transactionIds)
            args.putString(UxArgument.ORIGIN_ACCOUNT_UID, originAccountUID)
            val bulkMoveDialogFragment = BulkMoveDialogFragment()
            bulkMoveDialogFragment.arguments = args
            return bulkMoveDialogFragment
        }
    }
}