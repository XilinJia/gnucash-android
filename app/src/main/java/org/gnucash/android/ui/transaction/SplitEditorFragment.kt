/*
 * Copyright (c) 2014 - 2016 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.transaction

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.inputmethodservice.KeyboardView
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AppCompatActivity
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.Fragment
import butterknife.BindView
import butterknife.ButterKnife
import net.objecthunter.exp4j.Expression
import net.objecthunter.exp4j.ExpressionBuilder
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.model.BaseModel.Companion.generateUID
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Commodity.Companion.getInstance
import org.gnucash.android.model.Money
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction.Companion.typeForBalance
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.transaction.TransactionsActivity.Companion.displayBalance
import org.gnucash.android.ui.transaction.dialog.TransferFundsDialogFragment
import org.gnucash.android.ui.util.widget.CalculatorEditText
import org.gnucash.android.ui.util.widget.CalculatorKeyboard
import org.gnucash.android.ui.util.widget.TransactionTypeSwitch
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter
import java.math.BigDecimal

/**
 * Dialog for editing the splits in a transaction
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class SplitEditorFragment : Fragment() {
    @JvmField
    @BindView(R.id.split_list_layout)
    var mSplitsLinearLayout: LinearLayout? = null

    @JvmField
    @BindView(R.id.calculator_keyboard)
    var mKeyboardView: KeyboardView? = null

    @JvmField
    @BindView(R.id.imbalance_textview)
    var mImbalanceTextView: TextView? = null
    private var mAccountsDbAdapter: AccountsDbAdapter? = null
    private var mCursor: Cursor? = null
    private var mCursorAdapter: SimpleCursorAdapter? = null
    private var mSplitItemViewList: MutableList<View>? = null
    private var mAccountUID: String? = null
    private var mCommodity: Commodity? = null
    private var mBaseAmount = BigDecimal.ZERO
    var mCalculatorKeyboard: CalculatorKeyboard? = null
    var mImbalanceWatcher = BalanceTextWatcher()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_split_editor, container, false)
        ButterKnife.bind(this, view)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar!!
        actionBar.setTitle(R.string.title_split_editor)
        setHasOptionsMenu(true)
        mCalculatorKeyboard = CalculatorKeyboard(activity!!, mKeyboardView!!, R.xml.calculator_keyboard)
        mSplitItemViewList = ArrayList()

        //we are editing splits for a new transaction.
        // But the user may have already created some splits before. Let's check
        val splitList: List<Split> = arguments!!.getParcelableArrayList(UxArgument.SPLIT_LIST)!!
        initArgs()
        if (!splitList.isEmpty()) {
            //aha! there are some splits. Let's load those instead
            loadSplitViews(splitList)
            mImbalanceWatcher.afterTextChanged(Editable.Factory.getInstance().newEditable(""))
        } else {
            val currencyCode = mAccountsDbAdapter!!.getAccountCurrencyCode(mAccountUID!!)
            val split = Split(Money(mBaseAmount, getInstance(currencyCode)), mAccountUID)
            val accountType = mAccountsDbAdapter!!.getAccountType(mAccountUID!!)
            val transactionType = typeForBalance(accountType, mBaseAmount.signum() < 0)
            split.mSplitType = transactionType
            val view = addSplitView(split)
            view.findViewById<View>(R.id.input_accounts_spinner).isEnabled = false
            view.findViewById<View>(R.id.btn_remove_split).visibility = View.GONE
            displayBalance(mImbalanceTextView!!, Money(mBaseAmount.negate(), mCommodity))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mCalculatorKeyboard = CalculatorKeyboard(activity!!, mKeyboardView!!, R.xml.calculator_keyboard)
    }

    private fun loadSplitViews(splitList: List<Split>?) {
        for (split in splitList!!) {
            addSplitView(split)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.split_editor_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                activity!!.setResult(Activity.RESULT_CANCELED)
                activity!!.finish()
                true
            }

            R.id.menu_save -> {
                saveSplits()
                true
            }

            R.id.menu_add_split -> {
                addSplitView(null)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Add a split view and initialize it with `split`
     * @param split Split to initialize the contents to
     * @return Returns the split view which was added
     */
    private fun addSplitView(split: Split?): View {
        val layoutInflater = activity!!.layoutInflater
        val splitView = layoutInflater.inflate(R.layout.item_split_entry, mSplitsLinearLayout, false)
        mSplitsLinearLayout!!.addView(splitView, 0)
        val viewHolder = SplitViewHolder(splitView, split)
        splitView.tag = viewHolder
        mSplitItemViewList!!.add(splitView)
        return splitView
    }

    /**
     * Extracts arguments passed to the view and initializes necessary adapters and cursors
     */
    private fun initArgs() {
        mAccountsDbAdapter = AccountsDbAdapter.instance
        val args = arguments
        mAccountUID = (activity as FormActivity?)!!.currentAccountUID
        mBaseAmount = BigDecimal(args!!.getString(UxArgument.AMOUNT_STRING))
        val conditions = ("("
                + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0 AND "
                + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                + ")")
        mCursor = mAccountsDbAdapter!!.fetchAccountsOrderedByFullName(conditions, null)
        mCommodity = CommoditiesDbAdapter.instance.getCommodity(mAccountsDbAdapter!!.getMMnemonic(mAccountUID))
    }

    /**
     * Holds a split item view and binds the items in it
     */
    internal inner class SplitViewHolder(splitView: View, split: Split?) : OnTransferFundsListener {
        @JvmField
        @BindView(R.id.input_split_memo)
        var splitMemoEditText: EditText? = null

        @JvmField
        @BindView(R.id.input_split_amount)
        var splitAmountEditText: CalculatorEditText? = null

        @JvmField
        @BindView(R.id.btn_remove_split)
        var removeSplitButton: ImageView? = null

        @JvmField
        @BindView(R.id.input_accounts_spinner)
        var accountsSpinner: Spinner? = null

        @JvmField
        @BindView(R.id.split_currency_symbol)
        var splitCurrencyTextView: TextView? = null

        @JvmField
        @BindView(R.id.split_uid)
        var splitUidTextView: TextView? = null

        @JvmField
        @BindView(R.id.btn_split_type)
        var splitTypeSwitch: TransactionTypeSwitch? = null
        var splitView: View
        var quantity: Money? = null

        init {
            ButterKnife.bind(this, splitView)
            this.splitView = splitView
            if (split != null && !split.mQuantity!!.equals(split.mValue)) quantity = split.mQuantity
            setListeners(split)
        }

        override fun transferComplete(amount: Money?) {
            quantity = amount
        }

        private fun setListeners(split: Split?) {
            splitAmountEditText!!.bindListeners(mCalculatorKeyboard!!)
            removeSplitButton!!.setOnClickListener {
                mSplitsLinearLayout!!.removeView(splitView)
                mSplitItemViewList!!.remove(splitView)
                mImbalanceWatcher.afterTextChanged(Editable.Factory.getInstance().newEditable(""))
            }
            updateTransferAccountsList(accountsSpinner)
            splitCurrencyTextView!!.text = mCommodity!!.symbol
            splitTypeSwitch!!.setAmountFormattingListener(splitAmountEditText!!, splitCurrencyTextView!!)
            splitTypeSwitch!!.isChecked = mBaseAmount.signum() > 0
            splitUidTextView!!.text = generateUID()
            if (split != null) {
                splitAmountEditText!!.commodity = split.mValue!!.mCommodity!!
                splitAmountEditText!!.setValue(split.formattedValue().asBigDecimal())
                splitCurrencyTextView!!.text = split.mValue!!.mCommodity!!.symbol
                splitMemoEditText!!.setText(split.mMemo)
                splitUidTextView!!.text = split.mUID
                val splitAccountUID = split.mAccountUID
                setSelectedTransferAccount(mAccountsDbAdapter!!.getID(splitAccountUID!!), accountsSpinner)
                splitTypeSwitch!!.accountType = mAccountsDbAdapter!!.getAccountType(splitAccountUID)
                splitTypeSwitch!!.setChecked(split.mSplitType)
            }
            accountsSpinner!!.onItemSelectedListener = SplitAccountListener(splitTypeSwitch, this)
            splitTypeSwitch!!.addOnCheckedChangeListener { buttonView, isChecked ->
                mImbalanceWatcher.afterTextChanged(Editable.Factory.getInstance().newEditable(""))
            }
            splitAmountEditText!!.addTextChangedListener(mImbalanceWatcher)
        }

        /**
         * Returns the value of the amount in the splitAmountEditText field without setting the value to the view
         *
         * If the expression in the view is currently incomplete or invalid, null is returned.
         * This method is used primarily for computing the imbalance
         * @return Value in the split item amount field, or [BigDecimal.ZERO] if the expression is empty or invalid
         */
        val amountValue: BigDecimal
            get() {
                val amountString = splitAmountEditText!!.cleanString
                if (amountString.isEmpty()) return BigDecimal.ZERO
                val expressionBuilder = ExpressionBuilder(amountString)
                val expression: Expression?
                expression = try {
                    expressionBuilder.build()
                } catch (e: RuntimeException) {
                    return BigDecimal.ZERO
                }
                return if (expression != null && expression.validate().isValid) {
                    BigDecimal(expression.evaluate())
                } else {
                    Log.v(
                        this@SplitEditorFragment.javaClass.simpleName,
                        "Incomplete expression for updating imbalance: $expression"
                    )
                    BigDecimal.ZERO
                }
            }
    }

    /**
     * Updates the spinner to the selected transfer account
     * @param accountId Database ID of the transfer account
     */
    private fun setSelectedTransferAccount(accountId: Long, accountsSpinner: Spinner?) {
        for (pos in 0 until mCursorAdapter!!.count) {
            if (mCursorAdapter!!.getItemId(pos) == accountId) {
                accountsSpinner!!.setSelection(pos)
                break
            }
        }
    }

    /**
     * Updates the list of possible transfer accounts.
     * Only accounts with the same currency can be transferred to
     */
    private fun updateTransferAccountsList(transferAccountSpinner: Spinner?) {
        mCursorAdapter = QualifiedAccountNameCursorAdapter(activity, mCursor)
        transferAccountSpinner!!.adapter = mCursorAdapter
    }

    /**
     * Check if all the split amounts have valid values that can be saved
     * @return `true` if splits can be saved, `false` otherwise
     */
    private fun canSave(): Boolean {
        for (splitView in mSplitItemViewList!!) {
            val viewHolder = splitView.tag as SplitViewHolder
            viewHolder.splitAmountEditText!!.evaluate()
            if (viewHolder.splitAmountEditText!!.error != null) {
                return false
            }
            //TODO: also check that multicurrency splits have a conversion amount present
        }
        return true
    }

    /**
     * Save all the splits from the split editor
     */
    private fun saveSplits() {
        if (!canSave()) {
            Toast.makeText(
                activity, R.string.toast_error_check_split_amounts,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val data = Intent()
        data.putParcelableArrayListExtra(UxArgument.SPLIT_LIST, extractSplitsFromView())
        activity!!.setResult(Activity.RESULT_OK, data)
        activity!!.finish()
    }

    /**
     * Extracts the input from the views and builds [org.gnucash.android.model.Split]s to correspond to the input.
     * @return List of [org.gnucash.android.model.Split]s represented in the view
     */
    private fun extractSplitsFromView(): ArrayList<Split> {
        val splitList = ArrayList<Split>()
        for (splitView in mSplitItemViewList!!) {
            val viewHolder = splitView.tag as SplitViewHolder
            if (viewHolder.splitAmountEditText!!.getValue() == null) continue
            val amountBigDecimal = viewHolder.splitAmountEditText!!.getValue()
            val currencyCode = mAccountsDbAdapter!!.getMMnemonic(mAccountUID)
            val valueAmount = Money(amountBigDecimal!!.abs(), getInstance(currencyCode))
            val accountUID = mAccountsDbAdapter!!.getUID(viewHolder.accountsSpinner!!.selectedItemId)
            val split = Split(valueAmount, accountUID)
            split.mMemo = viewHolder.splitMemoEditText!!.text.toString()
            split.mSplitType = viewHolder.splitTypeSwitch!!.transactionType
            split.mUID = viewHolder.splitUidTextView!!.text.toString().trim { it <= ' ' }
            if (viewHolder.quantity != null) split.mQuantity = viewHolder.quantity!!.abs()
            splitList.add(split)
        }
        return splitList
    }

    /**
     * Updates the displayed balance of the accounts when the amount of a split is changed
     */
    inner class BalanceTextWatcher : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
            //nothing to see here, move along
        }

        override fun onTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
            //nothing to see here, move along
        }

        override fun afterTextChanged(editable: Editable) {
            var imbalance = BigDecimal.ZERO
            for (splitItem in mSplitItemViewList!!) {
                val viewHolder = splitItem.tag as SplitViewHolder
                val amount = viewHolder.amountValue.abs()
                val accountId = viewHolder.accountsSpinner!!.selectedItemId
                val hasDebitNormalBalance = AccountsDbAdapter.instance
                    .getAccountType(accountId).hasDebitNormalBalance()
                imbalance = if (viewHolder.splitTypeSwitch!!.isChecked) {
                    if (hasDebitNormalBalance) imbalance.add(amount) else imbalance.subtract(amount)
                } else {
                    if (hasDebitNormalBalance) imbalance.subtract(amount) else imbalance.add(amount)
                }
            }
            displayBalance(mImbalanceTextView!!, Money(imbalance, mCommodity))
        }
    }

    /**
     * Listens to changes in the transfer account and updates the currency symbol, the label of the
     * transaction type and if neccessary
     */
    private inner class SplitAccountListener(
        var mTypeToggleButton: TransactionTypeSwitch?,
        var mSplitViewHolder: SplitViewHolder
    ) : OnItemSelectedListener {
        /**
         * Flag to know when account spinner callback is due to user interaction or layout of components
         */
        var userInteraction = false
        override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View, position: Int, id: Long) {
            val accountType = mAccountsDbAdapter!!.getAccountType(id)
            mTypeToggleButton!!.accountType = accountType

            //refresh the imbalance amount if we change the account
            mImbalanceWatcher.afterTextChanged(Editable.Factory.getInstance().newEditable(""))
            val fromCurrencyCode = mAccountsDbAdapter!!.getMMnemonic(mAccountUID)
            val targetCurrencyCode = mAccountsDbAdapter!!.getMMnemonic(mAccountsDbAdapter!!.getUID(id))
            if (!userInteraction || fromCurrencyCode == targetCurrencyCode) {
                //first call is on layout, subsequent calls will be true and transfer will work as usual
                userInteraction = true
                return
            }
            val amountBigD = mSplitViewHolder.splitAmountEditText!!.getValue() ?: return
            val amount = Money(amountBigD, getInstance(fromCurrencyCode))
            val fragment = TransferFundsDialogFragment.getInstance(amount, targetCurrencyCode, mSplitViewHolder)
            fragment.show(fragmentManager!!, "tranfer_funds_editor")
        }

        override fun onNothingSelected(adapterView: AdapterView<*>?) {
            //nothing to see here, move along
        }
    }

    companion object {
        /**
         * Create and return a new instance of the fragment with the appropriate paramenters
         * @param args Arguments to be set to the fragment. <br></br>
         * See [UxArgument.AMOUNT_STRING] and [UxArgument.SPLIT_LIST]
         * @return New instance of SplitEditorFragment
         */
        fun newInstance(args: Bundle?): SplitEditorFragment {
            val fragment = SplitEditorFragment()
            fragment.arguments = args
            return fragment
        }
    }
}