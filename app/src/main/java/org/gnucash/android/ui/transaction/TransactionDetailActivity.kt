package org.gnucash.android.ui.transaction

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.TableLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.getActiveAccountColorResource
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Split
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.passcode.PasscodeLockActivity
import org.gnucash.android.ui.transaction.TransactionsActivity.Companion.displayBalance
import java.text.DateFormat
import java.util.*

/**
 * Activity for displaying transaction information
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class TransactionDetailActivity : PasscodeLockActivity() {
    @JvmField
    @BindView(R.id.trn_description)
    var mTransactionDescription: TextView? = null

    @JvmField
    @BindView(R.id.trn_time_and_date)
    var mTimeAndDate: TextView? = null

    @JvmField
    @BindView(R.id.trn_recurrence)
    var mRecurrence: TextView? = null

    @JvmField
    @BindView(R.id.trn_notes)
    var mNotes: TextView? = null

    @JvmField
    @BindView(R.id.toolbar)
    var mToolBar: Toolbar? = null

    @JvmField
    @BindView(R.id.transaction_account)
    var mTransactionAccount: TextView? = null

    @JvmField
    @BindView(R.id.balance_debit)
    var mDebitBalance: TextView? = null

    @JvmField
    @BindView(R.id.balance_credit)
    var mCreditBalance: TextView? = null

    @JvmField
    @BindView(R.id.fragment_transaction_details)
    var mDetailTableLayout: TableLayout? = null
    private var mTransactionUID: String? = null
    private var mAccountUID: String? = null
    private var mDetailTableRows = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_detail)
        mTransactionUID = intent.getStringExtra(UxArgument.SELECTED_TRANSACTION_UID)
        mAccountUID = intent.getStringExtra(UxArgument.SELECTED_ACCOUNT_UID)
        if (mTransactionUID == null || mAccountUID == null) {
            throw MissingFormatArgumentException("You must specify both the transaction and account GUID")
        }
        ButterKnife.bind(this)
        setSupportActionBar(mToolBar)
        val actionBar = supportActionBar!!
        actionBar.elevation = 0f
        actionBar.setHomeButtonEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp)
        actionBar.setDisplayShowTitleEnabled(false)
        bindViews()
        val themeColor = getActiveAccountColorResource(mAccountUID!!)
        actionBar.setBackgroundDrawable(ColorDrawable(themeColor))
        mToolBar!!.setBackgroundColor(themeColor)
        if (Build.VERSION.SDK_INT > 20) window.statusBarColor = GnuCashApplication.darken(themeColor)
    }

    internal inner class SplitAmountViewHolder(var itemView: View, split: Split) {
        @JvmField
        @BindView(R.id.split_account_name)
        var accountName: TextView? = null

        @JvmField
        @BindView(R.id.split_debit)
        var splitDebit: TextView? = null

        @JvmField
        @BindView(R.id.split_credit)
        var splitCredit: TextView? = null

        init {
            ButterKnife.bind(this, itemView)
            val accountsDbAdapter = AccountsDbAdapter.instance
            accountName!!.text = accountsDbAdapter.getAccountFullName(split.mAccountUID!!)
            val quantity = split.formattedQuantity()
            val balanceView = if (quantity.isNegative) splitDebit else splitCredit
            displayBalance(balanceView!!, quantity)
        }
    }

    /**
     * Reads the transaction information from the database and binds it to the views
     */
    private fun bindViews() {
        val transactionsDbAdapter = TransactionsDbAdapter.instance
        val transaction = transactionsDbAdapter.getRecord(mTransactionUID!!)
        mTransactionDescription!!.text = transaction.getMDescription()
        mTransactionAccount!!.text = getString(
            R.string.label_inside_account_with_name, AccountsDbAdapter.instance.getAccountFullName(
                mAccountUID!!
            )
        )
        val accountsDbAdapter = AccountsDbAdapter.instance
        val accountBalance = accountsDbAdapter.getAccountBalance(mAccountUID!!, -1, transaction.mTimestamp)
        val balanceTextView = if (accountBalance.isNegative) mDebitBalance else mCreditBalance
        displayBalance(balanceTextView!!, accountBalance)
        mDetailTableRows = mDetailTableLayout!!.childCount
        val useDoubleEntry = GnuCashApplication.isDoubleEntryEnabled
        val inflater = LayoutInflater.from(this)
        var index = 0
        for (split in transaction.getMSplitList()) {
            if (!useDoubleEntry && split.mAccountUID == accountsDbAdapter.getImbalanceAccountUID(split.mValue!!.mCommodity!!)) {
                //do now show imbalance accounts for single entry use case
                continue
            }
            val view = inflater.inflate(R.layout.item_split_amount_info, mDetailTableLayout, false)
            val viewHolder = SplitAmountViewHolder(view, split)
            mDetailTableLayout!!.addView(viewHolder.itemView, index++)
        }
        val trnDate = Date(transaction.mTimestamp)
        val timeAndDate = DateFormat.getDateInstance(DateFormat.FULL).format(trnDate)
        mTimeAndDate!!.text = timeAndDate
        if (transaction.mScheduledActionUID != null) {
            val scheduledAction = ScheduledActionDbAdapter.instance.getRecord(transaction.mScheduledActionUID!!)
            mRecurrence!!.text = scheduledAction.repeatString()
            findViewById<View>(R.id.row_trn_recurrence).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.row_trn_recurrence).visibility = View.GONE
        }
        if (transaction.mNotes != null && transaction.mNotes!!.isNotEmpty()) {
            mNotes!!.text = transaction.mNotes
            findViewById<View>(R.id.row_trn_notes).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.row_trn_notes).visibility = View.GONE
        }
    }

    /**
     * Refreshes the transaction information
     */
    private fun refresh() {
        removeSplitItemViews()
        bindViews()
    }

    /**
     * Remove the split item views from the transaction detail prior to refreshing them
     */
    private fun removeSplitItemViews() {
        // Remove all rows that are not special.
        mDetailTableLayout!!.removeViews(0, mDetailTableLayout!!.childCount - mDetailTableRows)
        mDebitBalance!!.text = ""
        mCreditBalance!!.text = ""
    }

    @OnClick(R.id.fab_edit_transaction)
    fun editTransaction() {
        val createTransactionIntent = Intent(this.applicationContext, FormActivity::class.java)
        createTransactionIntent.action = Intent.ACTION_INSERT_OR_EDIT
        createTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID)
        createTransactionIntent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, mTransactionUID)
        createTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
        startActivityForResult(createTransactionIntent, REQUEST_EDIT_TRANSACTION)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)   // added by XJ
        if (resultCode == RESULT_OK) {
            refresh()
        }
    }

    companion object {
        const val REQUEST_EDIT_TRANSACTION = 0x10
    }
}