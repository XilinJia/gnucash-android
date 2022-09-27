/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.inputmethodservice.KeyboardView
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AppCompatActivity
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.Fragment
import butterknife.BindView
import butterknife.ButterKnife
import com.codetroopers.betterpickers.calendardatepicker.CalendarDatePickerDialogFragment
import com.codetroopers.betterpickers.radialtimepicker.RadialTimePickerDialogFragment
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrenceFormatter
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment.OnRecurrenceSetListener
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.*
import org.gnucash.android.model.*
import org.gnucash.android.model.Commodity.Companion.getInstance
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.Money
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction.Companion.computeBalance
import org.gnucash.android.model.Transaction
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity.Companion.updateAllWidgets
import org.gnucash.android.ui.settings.PreferenceActivity
import org.gnucash.android.ui.transaction.dialog.TransferFundsDialogFragment
import org.gnucash.android.ui.util.RecurrenceParser
import org.gnucash.android.ui.util.RecurrenceViewClickListener
import org.gnucash.android.ui.util.widget.CalculatorEditText
import org.gnucash.android.ui.util.widget.TransactionTypeSwitch
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter
import java.math.BigDecimal
import java.text.DateFormat
import java.text.ParseException
import java.util.*

/**
 * Fragment for creating or editing transactions
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class TransactionFormFragment : Fragment(), CalendarDatePickerDialogFragment.OnDateSetListener,
    RadialTimePickerDialogFragment.OnTimeSetListener, OnRecurrenceSetListener, OnTransferFundsListener {
    /**
     * Transactions database adapter
     */
    private var mTransactionsDbAdapter: TransactionsDbAdapter? = null

    /**
     * Accounts database adapter
     */
    private var mAccountsDbAdapter: AccountsDbAdapter? = null

    /**
     * Adapter for transfer account spinner
     */
    private var mAccountCursorAdapter: QualifiedAccountNameCursorAdapter? = null

    /**
     * Cursor for transfer account spinner
     */
    private var mCursor: Cursor? = null

    /**
     * Transaction to be created/updated
     */
    private var mTransaction: Transaction? = null

    /**
     * Button for setting the transaction type, either credit or debit
     */
    @JvmField
    @BindView(R.id.input_transaction_type)
    var mTransactionTypeSwitch: TransactionTypeSwitch? = null

    /**
     * Input field for the transaction name (description)
     */
    @JvmField
    @BindView(R.id.input_transaction_name)
    var mDescriptionEditText: AutoCompleteTextView? = null

    /**
     * Input field for the transaction amount
     */
    @JvmField
    @BindView(R.id.input_transaction_amount)
    var mAmountEditText: CalculatorEditText? = null

    /**
     * Field for the transaction currency.
     * The transaction uses the currency of the account
     */
    @JvmField
    @BindView(R.id.currency_symbol)
    var mCurrencyTextView: TextView? = null

    /**
     * Input field for the transaction description (note)
     */
    @JvmField
    @BindView(R.id.input_description)
    var mNotesEditText: EditText? = null

    /**
     * Input field for the transaction date
     */
    @JvmField
    @BindView(R.id.input_date)
    var mDateTextView: TextView? = null

    /**
     * Input field for the transaction time
     */
    @JvmField
    @BindView(R.id.input_time)
    var mTimeTextView: TextView? = null

    /**
     * Spinner for selecting the transfer account
     */
    @JvmField
    @BindView(R.id.input_transfer_account_spinner)
    var mTransferAccountSpinner: Spinner? = null

    /**
     * Checkbox indicating if this transaction should be saved as a template or not
     */
    @JvmField
    @BindView(R.id.checkbox_save_template)
    var mSaveTemplateCheckbox: CheckBox? = null

    @JvmField
    @BindView(R.id.input_recurrence)
    var mRecurrenceTextView: TextView? = null

    /**
     * View which displays the calculator keyboard
     */
    @JvmField
    @BindView(R.id.calculator_keyboard)
    var mKeyboardView: KeyboardView? = null

    /**
     * Open the split editor
     */
    @JvmField
    @BindView(R.id.btn_split_editor)
    var mOpenSplitEditor: ImageView? = null

    /**
     * Layout for transfer account and associated views
     */
    @JvmField
    @BindView(R.id.layout_double_entry)
    var mDoubleEntryLayout: View? = null

    /**
     * Flag to note if double entry accounting is in use or not
     */
    private var mUseDoubleEntry = false

    /**
     * [Calendar] for holding the set date
     */
    private var mDate: Calendar? = null

    /**
     * [Calendar] object holding the set time
     */
    private var mTime: Calendar? = null

    /**
     * The AccountType of the account to which this transaction belongs.
     * Used for determining the accounting rules for credits and debits
     */
    var mAccountType: AccountType? = null
    private var mRecurrenceRule: String? = null
    private val mEventRecurrence = EventRecurrence()
    private var mAccountUID: String? = null
    private var mSplitsList: MutableList<Split> = ArrayList()
    private var mEditMode = false

    /**
     * Flag which is set if another action is triggered during a transaction save (which interrrupts the save process).
     * Allows the fragment to check and resume the save operation.
     * Primarily used for multicurrency transactions when the currency transfer dialog is opened during save
     */
    private var onSaveAttempt = false

    /**
     * Split quantity which will be set from the funds transfer dialog
     */
    private var mSplitQuantity: Money? = null

    /**
     * Create the view and retrieve references to the UI elements
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_transaction_form, container, false)
        ButterKnife.bind(this, v)
        mAmountEditText!!.bindListeners(mKeyboardView)
        mOpenSplitEditor!!.setOnClickListener { openSplitEditor() }
        return v
    }

    /**
     * Starts the transfer of funds from one currency to another
     */
    private fun startTransferFunds() {
        val fromCommodity = getInstance(mTransactionsDbAdapter!!.getAccountCurrencyCode(mAccountUID!!))
        val id = mTransferAccountSpinner!!.selectedItemId
        val targetCurrencyCode = mAccountsDbAdapter!!.getMMnemonic(mAccountsDbAdapter!!.getUID(id))
        if ((fromCommodity.equals(getInstance(targetCurrencyCode))
                    || !mAmountEditText!!.isInputModified) || mSplitQuantity != null
        ) //if both accounts have same currency
            return
        val amountBigd = mAmountEditText!!.getValue()
        if (amountBigd == null || amountBigd == BigDecimal.ZERO) return
        val amount = Money(amountBigd, fromCommodity).abs()
        val fragment = TransferFundsDialogFragment.getInstance(amount, targetCurrencyCode, this)
        fragment.show(fragmentManager!!, "transfer_funds_editor")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mAmountEditText!!.bindListeners(mKeyboardView)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
        val sharedPrefs = PreferenceActivity.getActiveBookSharedPreferences()
        mUseDoubleEntry = sharedPrefs.getBoolean(getString(R.string.key_use_double_entry), false)
        if (!mUseDoubleEntry) {
            mDoubleEntryLayout!!.visibility = View.GONE
            mOpenSplitEditor!!.visibility = View.GONE
        }
        mAccountUID = arguments!!.getString(UxArgument.SELECTED_ACCOUNT_UID)
        assert(mAccountUID != null)
        mAccountsDbAdapter = AccountsDbAdapter.instance
        mAccountType = mAccountsDbAdapter!!.getAccountType(mAccountUID!!)
        val transactionUID = arguments!!.getString(UxArgument.SELECTED_TRANSACTION_UID)
        mTransactionsDbAdapter = TransactionsDbAdapter.instance
        if (transactionUID != null) {
            mTransaction = mTransactionsDbAdapter!!.getRecord(transactionUID)
        }
        setListeners()
        //updateTransferAccountsList must only be called after initializing mAccountsDbAdapter
        updateTransferAccountsList()
        mTransferAccountSpinner!!.onItemSelectedListener = object : OnItemSelectedListener {
            /**
             * Flag for ignoring first call to this listener.
             * The first call is during layout, but we want it called only in response to user interaction
             */
            var userInteraction = false
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, position: Int, id: Long) {
                removeFavoriteIconFromSelectedView(view as TextView)
                if (mSplitsList!!.size == 2) { //when handling simple transfer to one account
                    for (split in mSplitsList!!) {
                        if (split.mAccountUID != mAccountUID) {
                            split.mAccountUID = mAccountsDbAdapter!!.getUID(id)
                        }
                        // else case is handled when saving the transactions
                    }
                }
                if (!userInteraction) {
                    userInteraction = true
                    return
                }
                startTransferFunds()
            }

            // Removes the icon from view to avoid visual clutter
            private fun removeFavoriteIconFromSelectedView(view: TextView?) {
                view?.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                //nothing to see here, move along
            }
        }
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar!!
        //        actionBar.setSubtitle(mAccountsDbAdapter.getFullyQualifiedAccountName(mAccountUID));
        if (mTransaction == null) {
            actionBar.setTitle(R.string.title_add_transaction)
            initalizeViews()
            initTransactionNameAutocomplete()
        } else {
            actionBar.setTitle(R.string.title_edit_transaction)
            initializeViewsWithTransaction()
            mEditMode = true
        }
        activity!!.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    /**
     * Extension of SimpleCursorAdapter which is used to populate the fields for the list items
     * in the transactions suggestions (auto-complete transaction description).
     */
    private inner class DropDownCursorAdapter(
        context: Context?,
        layout: Int,
        c: Cursor?,
        from: Array<String>?,
        to: IntArray?
    ) : SimpleCursorAdapter(context, layout, c, from, to, 0) {
        override fun bindView(view: View, context: Context, cursor: Cursor) {
            super.bindView(view, context, cursor)
            val transactionUID =
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_UID))
            val balance = TransactionsDbAdapter.instance.getBalance(transactionUID, mAccountUID)
            val timestamp =
                cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_TIMESTAMP))
            val dateString = DateUtils.formatDateTime(
                activity, timestamp,
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR
            )
            val secondaryTextView = view.findViewById<View>(R.id.secondary_text) as TextView
            secondaryTextView.text = balance.formattedString() + " on " + dateString //TODO: Extract string
        }
    }

    /**
     * Initializes the transaction name field for autocompletion with existing transaction names in the database
     */
    private fun initTransactionNameAutocomplete() {
        val to = intArrayOf(R.id.primary_text)
        val from = arrayOf(DatabaseSchema.TransactionEntry.COLUMN_DESCRIPTION)
        val adapter: SimpleCursorAdapter = DropDownCursorAdapter(
            activity, R.layout.dropdown_item_2lines, null, from, to
        )
        adapter.cursorToStringConverter = SimpleCursorAdapter.CursorToStringConverter { cursor ->
            val colIndex = cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_DESCRIPTION)
            cursor.getString(colIndex)
        }
        adapter.filterQueryProvider = FilterQueryProvider { name ->
            mTransactionsDbAdapter!!.fetchTransactionSuggestions(
                name?.toString() ?: "",
                mAccountUID!!
            )
        }
        mDescriptionEditText!!.onItemClickListener = OnItemClickListener { adapterView, view, position, id ->
            mTransaction = Transaction(mTransactionsDbAdapter!!.getRecord(id), true)
            mTransaction!!.setMTimestamp(System.currentTimeMillis())
            //we check here because next method will modify it and we want to catch user-modification
            val amountEntered = mAmountEditText!!.isInputModified
            initializeViewsWithTransaction()
            val splitList = mTransaction!!.getMSplitList()
            val isSplitPair = splitList.size == 2 && splitList[0].isPairOf(splitList[1])
            if (isSplitPair) {
                mSplitsList!!.clear()
                if (!amountEntered) //if user already entered an amount
                    mAmountEditText!!.setValue(splitList[0].mValue!!.asBigDecimal())
            } else {
                if (amountEntered) { //if user entered own amount, clear loaded splits and use the user value
                    mSplitsList!!.clear()
                    setDoubleEntryViewsVisibility(View.VISIBLE)
                } else {
                    if (mUseDoubleEntry) { //don't hide the view in single entry mode
                        setDoubleEntryViewsVisibility(View.GONE)
                    }
                }
            }
            mTransaction = null //we are creating a new transaction after all
        }
        mDescriptionEditText!!.setAdapter(adapter)
    }

    /**
     * Initialize views in the fragment with information from a transaction.
     * This method is called if the fragment is used for editing a transaction
     */
    private fun initializeViewsWithTransaction() {
        mDescriptionEditText!!.setText(mTransaction!!.getMDescription())
        mDescriptionEditText!!.setSelection(mDescriptionEditText!!.text.length)
        mTransactionTypeSwitch!!.accountType = mAccountType!!
        mTransactionTypeSwitch!!.isChecked = mTransaction!!.computeBalance(mAccountUID).isNegative
        if (!mAmountEditText!!.isInputModified) {
            //when autocompleting, only change the amount if the user has not manually changed it already
            mAmountEditText!!.setValue(mTransaction!!.computeBalance(mAccountUID).asBigDecimal())
        }
        mCurrencyTextView!!.text = mTransaction!!.mCommodity!!.symbol
        mNotesEditText!!.setText(mTransaction!!.mNotes)
        mDateTextView!!.text = DATE_FORMATTER.format(mTransaction!!.mTimestamp)
        mTimeTextView!!.text = TIME_FORMATTER.format(mTransaction!!.mTimestamp)
        val cal = GregorianCalendar.getInstance()
        cal.timeInMillis = mTransaction!!.mTimestamp
        mTime = cal
        mDate = mTime

        //TODO: deep copy the split list. We need a copy so we can modify with impunity
        mSplitsList = ArrayList(mTransaction!!.getMSplitList())
        toggleAmountInputEntryMode(mSplitsList!!.size <= 2)
        if (mSplitsList!!.size == 2) {
            for (split in mSplitsList!!) {
                if (split.mAccountUID == mAccountUID) {
                    if (!split.mQuantity!!.mCommodity!!.equals(mTransaction!!.mCommodity)) {
                        mSplitQuantity = split.mQuantity
                    }
                }
            }
        }
        //if there are more than two splits (which is the default for one entry), then
        //disable editing of the transfer account. User should open editor
        if (mSplitsList.size == 2 && mSplitsList.get(0).isPairOf(mSplitsList.get(1))) {
            for (split in mTransaction!!.getMSplitList()) {
                //two splits, one belongs to this account and the other to another account
                if (mUseDoubleEntry && split.mAccountUID != mAccountUID) {
                    setSelectedTransferAccount(mAccountsDbAdapter!!.getID(split.mAccountUID!!))
                }
            }
        } else {
            setDoubleEntryViewsVisibility(View.GONE)
        }
        val currencyCode = mTransactionsDbAdapter!!.getAccountCurrencyCode(mAccountUID!!)
        val accountCommodity = getInstance(currencyCode)
        mCurrencyTextView!!.text = accountCommodity.symbol
        val commodity = getInstance(currencyCode)
        mAmountEditText!!.commodity = commodity
        mSaveTemplateCheckbox!!.isChecked = mTransaction!!.mIsTemplate
        val scheduledActionUID = arguments!!.getString(UxArgument.SCHEDULED_ACTION_UID)
        if (scheduledActionUID != null && !scheduledActionUID.isEmpty()) {
            val scheduledAction = ScheduledActionDbAdapter.instance.getRecord(scheduledActionUID)
            mRecurrenceRule = scheduledAction.ruleString()
            mEventRecurrence.parse(mRecurrenceRule)
            mRecurrenceTextView!!.text = scheduledAction.repeatString()
        }
    }

    private fun setDoubleEntryViewsVisibility(visibility: Int) {
        mDoubleEntryLayout!!.visibility = visibility
        mTransactionTypeSwitch!!.visibility = visibility
    }

    private fun toggleAmountInputEntryMode(enabled: Boolean) {
        if (enabled) {
            mAmountEditText!!.isFocusable = true
            mAmountEditText!!.bindListeners(mKeyboardView)
        } else {
            mAmountEditText!!.isFocusable = false
            mAmountEditText!!.setOnClickListener { openSplitEditor() }
        }
    }

    /**
     * Initialize views with default data for new transactions
     */
    private fun initalizeViews() {
        val time = Date(System.currentTimeMillis())
        mDateTextView!!.text = DATE_FORMATTER.format(time)
        mTimeTextView!!.text = TIME_FORMATTER.format(time)
        mDate = Calendar.getInstance()
        mTime = mDate
        mTransactionTypeSwitch!!.accountType = mAccountType!!
        val typePref = PreferenceActivity.getActiveBookSharedPreferences()
            .getString(getString(R.string.key_default_transaction_type), "DEBIT")
        mTransactionTypeSwitch!!.setChecked(TransactionType.valueOf(typePref!!))
        var code = GnuCashApplication.defaultCurrencyCode
        if (mAccountUID != null) {
            code = mTransactionsDbAdapter!!.getAccountCurrencyCode(mAccountUID!!)
        }
        val commodity = getInstance(code)
        mCurrencyTextView!!.text = commodity.symbol
        mAmountEditText!!.commodity = commodity
        if (mUseDoubleEntry) {
            var currentAccountUID = mAccountUID
            var defaultTransferAccountID: Long
            val rootAccountUID = mAccountsDbAdapter!!.orCreateGnuCashRootAccountUID
            do {
                defaultTransferAccountID = mAccountsDbAdapter!!.getDefaultTransferAccountID(
                    mAccountsDbAdapter!!.getID(
                        currentAccountUID!!
                    )
                )
                if (defaultTransferAccountID > 0) {
                    setSelectedTransferAccount(defaultTransferAccountID)
                    break //we found a parent with default transfer setting
                }
                currentAccountUID = mAccountsDbAdapter!!.getParentAccountUID(currentAccountUID)
            } while (currentAccountUID != rootAccountUID)
        }
    }

    /**
     * Updates the list of possible transfer accounts.
     * Only accounts with the same currency can be transferred to
     */
    private fun updateTransferAccountsList() {
        val conditions = ("(" + DatabaseSchema.AccountEntry.COLUMN_UID + " != ?"
                + " AND " + DatabaseSchema.AccountEntry.COLUMN_TYPE + " != ?"
                + " AND " + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                + ")")
        if (mCursor != null) {
            mCursor!!.close()
        }
        mCursor = mAccountsDbAdapter!!.fetchAccountsOrderedByFavoriteAndFullName(
            conditions,
            arrayOf(mAccountUID, AccountType.ROOT.name)
        )
        mAccountCursorAdapter = QualifiedAccountNameCursorAdapter(activity, mCursor)
        mTransferAccountSpinner!!.adapter = mAccountCursorAdapter
    }

    /**
     * Opens the split editor dialog
     */
    private fun openSplitEditor() {
        if (mAmountEditText!!.getValue() == null) {
            Toast.makeText(activity, R.string.toast_enter_amount_to_split, Toast.LENGTH_SHORT).show()
            return
        }
        val baseAmountString: String
        if (mTransaction == null) { //if we are creating a new transaction (not editing an existing one)
            val enteredAmount = mAmountEditText!!.getValue()
            baseAmountString = enteredAmount!!.toPlainString()
        } else {
            var biggestAmount = createZeroInstance(mTransaction!!.mMnemonic)
            for (split in mTransaction!!.getMSplitList()) {
                if (split.mValue!!.asBigDecimal().compareTo(biggestAmount.asBigDecimal()) > 0)
                    biggestAmount = split.mValue!!
            }
            baseAmountString = biggestAmount.toPlainString()
        }
        val intent = Intent(activity, FormActivity::class.java)
        intent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.SPLIT_EDITOR.name)
        intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID)
        intent.putExtra(UxArgument.AMOUNT_STRING, baseAmountString)
        intent.putParcelableArrayListExtra(UxArgument.SPLIT_LIST, extractSplitsFromView() as ArrayList<Split>?)
        startActivityForResult(intent, REQUEST_SPLIT_EDITOR)
    }

    /**
     * Sets click listeners for the dialog buttons
     */
    private fun setListeners() {
        mTransactionTypeSwitch!!.setAmountFormattingListener(mAmountEditText!!, mCurrencyTextView!!)
        mDateTextView!!.setOnClickListener {
            var dateMillis: Long = 0
            try {
                val date = DATE_FORMATTER.parse(mDateTextView!!.text.toString())
                dateMillis = date.time
            } catch (e: ParseException) {
                Log.e(tag, "Error converting input time to Date object")
            }
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dateMillis
            val year = calendar[Calendar.YEAR]
            val monthOfYear = calendar[Calendar.MONTH]
            val dayOfMonth = calendar[Calendar.DAY_OF_MONTH]
            val datePickerDialog = CalendarDatePickerDialogFragment()
                .setOnDateSetListener(this@TransactionFormFragment)
                .setPreselectedDate(year, monthOfYear, dayOfMonth)
            datePickerDialog.show(fragmentManager!!, "date_picker_fragment")
        }
        mTimeTextView!!.setOnClickListener {
            var timeMillis: Long = 0
            try {
                val date = TIME_FORMATTER.parse(mTimeTextView!!.text.toString())
                timeMillis = date.time
            } catch (e: ParseException) {
                Log.e(tag, "Error converting input time to Date object")
            }
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timeMillis
            val timePickerDialog = RadialTimePickerDialogFragment()
                .setOnTimeSetListener(this@TransactionFormFragment)
                .setStartTime(
                    calendar[Calendar.HOUR_OF_DAY],
                    calendar[Calendar.MINUTE]
                )
            timePickerDialog.show(fragmentManager!!, "time_picker_dialog_fragment")
        }
        mRecurrenceTextView!!.setOnClickListener(
            RecurrenceViewClickListener(
                activity as AppCompatActivity,
                mRecurrenceRule!!,
                this
            )
        )
    }

    /**
     * Updates the spinner to the selected transfer account
     * @param accountId Database ID of the transfer account
     */
    private fun setSelectedTransferAccount(accountId: Long) {
        val position = mAccountCursorAdapter!!.getPosition(mAccountsDbAdapter!!.getUID(accountId)!!)
        if (position >= 0) mTransferAccountSpinner!!.setSelection(position)
    }

    /**
     * Returns a list of splits based on the input in the transaction form.
     * This only gets the splits from the simple view, and not those from the Split Editor.
     * If the Split Editor has been used and there is more than one split, then it returns [.mSplitsList]
     * @return List of splits in the view or [.mSplitsList] is there are more than 2 splits in the transaction
     */
    private fun extractSplitsFromView(): List<Split>? {
        if (mTransactionTypeSwitch!!.visibility != View.VISIBLE) {
            return mSplitsList
        }
        val amountBigd = mAmountEditText!!.getValue()
        val baseCurrencyCode = mTransactionsDbAdapter!!.getAccountCurrencyCode(mAccountUID!!)
        val value = Money(amountBigd, getInstance(baseCurrencyCode))
        var quantity = Money(value)
        val transferAcctUID = transferAccountUID
        val cmdtyDbAdapter = CommoditiesDbAdapter.instance
        if (isMultiCurrencyTransaction) { //if multi-currency transaction
            val transferCurrencyCode = mAccountsDbAdapter!!.getMMnemonic(transferAcctUID)
            val commodityUID = cmdtyDbAdapter.getCommodityUID(baseCurrencyCode)
            val targetCmdtyUID = cmdtyDbAdapter.getCommodityUID(transferCurrencyCode)
            val pricePair = PricesDbAdapter.instance
                .getPrice(commodityUID, targetCmdtyUID)
            if (pricePair.first > 0 && pricePair.second > 0) {
                quantity = quantity.multiply(pricePair.first.toInt())
                    .divide(pricePair.second.toInt())
                    .withCurrency(cmdtyDbAdapter.getRecord(targetCmdtyUID))
            }
        }
        val split1: Split
        val split2: Split
        // Try to preserve the other split attributes.
        if (mSplitsList!!.size >= 2) {
            split1 = mSplitsList!![0]
            split1.setMValue(value)
            split1.mQuantity = value
            split1.mAccountUID = mAccountUID
            split2 = mSplitsList!![1]
            split2.setMValue(value)
            split2.mQuantity = quantity
            split2.mAccountUID = transferAcctUID
        } else {
            split1 = Split(value, mAccountUID)
            split2 = Split(value, quantity, transferAcctUID)
        }
        split1.mSplitType = mTransactionTypeSwitch!!.transactionType
        split2.mSplitType = mTransactionTypeSwitch!!.transactionType.invert()
        val splitList: MutableList<Split> = ArrayList()
        splitList.add(split1)
        splitList.add(split2)
        return splitList
    }

    /**
     * Returns the GUID of the currently selected transfer account.
     * If double-entry is disabled, this method returns the GUID of the imbalance account for the currently active account
     * @return GUID of transfer account
     */
    private val transferAccountUID: String
        private get() {
            val transferAcctUID: String
            transferAcctUID = if (mUseDoubleEntry) {
                val transferAcctId = mTransferAccountSpinner!!.selectedItemId
                mAccountsDbAdapter!!.getUID(transferAcctId)!!
            } else {
                val baseCommodity = mAccountsDbAdapter!!.getRecord(mAccountUID!!).getMCommodity()
                mAccountsDbAdapter!!.getOrCreateImbalanceAccountUID(baseCommodity)!!
            }
            return transferAcctUID
        }

    /**
     * Extracts a transaction from the input in the form fragment
     * @return New transaction object containing all info in the form
     */
    private fun extractTransactionFromView(): Transaction {
        val cal: Calendar = GregorianCalendar(
            mDate!![Calendar.YEAR],
            mDate!![Calendar.MONTH],
            mDate!![Calendar.DAY_OF_MONTH],
            mTime!![Calendar.HOUR_OF_DAY],
            mTime!![Calendar.MINUTE],
            mTime!![Calendar.SECOND]
        )
        val description = mDescriptionEditText!!.text.toString()
        val notes = mNotesEditText!!.text.toString()
        val currencyCode = mAccountsDbAdapter!!.getAccountCurrencyCode(mAccountUID!!)
        val commodity = CommoditiesDbAdapter.instance.getCommodity(currencyCode)
        val splits = extractSplitsFromView()
        val transaction = Transaction(description)
        transaction.setMTimestamp(cal.timeInMillis)
        transaction.mCommodity = commodity
        transaction.mNotes = notes
        transaction.setMSplitList(splits!!.toMutableList())
        transaction.mIsExported = false //not necessary as exports use timestamps now. Because, legacy
        return transaction
    }

    /**
     * Checks whether the split editor has been used for editing this transaction.
     *
     * The Split Editor is considered to have been used if the transaction type switch is not visible
     * @return `true` if split editor was used, `false` otherwise
     */
    private fun splitEditorUsed(): Boolean {
        return mTransactionTypeSwitch!!.visibility != View.VISIBLE
    }

    /**
     * Checks if this is a multi-currency transaction being created/edited
     *
     * A multi-currency transaction is one in which the main account and transfer account have different currencies. <br></br>
     * Single-entry transactions cannot be multi-currency
     * @return `true` if multi-currency transaction, `false` otherwise
     */
    private val isMultiCurrencyTransaction: Boolean
        private get() {
            if (!mUseDoubleEntry) return false
            val transferAcctUID = mAccountsDbAdapter!!.getUID(mTransferAccountSpinner!!.selectedItemId)
            val currencyCode = mAccountsDbAdapter!!.getAccountCurrencyCode(mAccountUID!!)
            val transferCurrencyCode = mAccountsDbAdapter!!.getMMnemonic(transferAcctUID)
            return currencyCode != transferCurrencyCode
        }

    /**
     * Collects information from the fragment views and uses it to create
     * and save a transaction
     */
    private fun saveNewTransaction() {
        mAmountEditText!!.calculatorKeyboard!!.hideCustomKeyboard()

        //determine whether we need to do currency conversion
        if (isMultiCurrencyTransaction && !splitEditorUsed() && !mCurrencyConversionDone) {
            onSaveAttempt = true
            startTransferFunds()
            return
        }
        val transaction = extractTransactionFromView()
        if (mEditMode) { //if editing an existing transaction
            transaction.mUID = mTransaction!!.mUID
        }
        mTransaction = transaction
        mAccountsDbAdapter!!.beginTransaction()
        try {
            // 1) mTransactions may be existing or non-existing
            // 2) when mTransactions exists in the db, the splits may exist or not exist in the db
            // So replace is chosen.
            mTransactionsDbAdapter!!.addRecord(mTransaction!!, DatabaseAdapter.UpdateMethod.replace)
            if (mSaveTemplateCheckbox!!.isChecked) { //template is automatically checked when a transaction is scheduled
                if (!mEditMode) { //means it was new transaction, so a new template
                    val templateTransaction = Transaction(
                        mTransaction!!, true
                    )
                    templateTransaction.mIsTemplate = true
                    mTransactionsDbAdapter!!.addRecord(templateTransaction, DatabaseAdapter.UpdateMethod.replace)
                    scheduleRecurringTransaction(templateTransaction.mUID)
                } else scheduleRecurringTransaction(mTransaction!!.mUID)
            } else {
                val scheduledActionUID = arguments!!.getString(UxArgument.SCHEDULED_ACTION_UID)
                if (scheduledActionUID != null) { //we were editing a schedule and it was turned off
                    ScheduledActionDbAdapter.instance.deleteRecord(scheduledActionUID)
                }
            }
            mAccountsDbAdapter!!.setTransactionSuccessful()
        } finally {
            mAccountsDbAdapter!!.endTransaction()
        }

        //update widgets, if any
        updateAllWidgets(activity!!.applicationContext)
        finish(Activity.RESULT_OK)
    }

    /**
     * Schedules a recurring transaction (if necessary) after the transaction has been saved
     * @see .saveNewTransaction
     */
    private fun scheduleRecurringTransaction(transactionUID: String?) {
        val scheduledActionDbAdapter = ScheduledActionDbAdapter.instance
        val recurrence = RecurrenceParser.parse(mEventRecurrence)
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        scheduledAction.setMRecurrence(recurrence!!)
        val scheduledActionUID = arguments!!.getString(UxArgument.SCHEDULED_ACTION_UID)
        if (scheduledActionUID != null) { //if we are editing an existing schedule
            if (recurrence == null) {
                scheduledActionDbAdapter.deleteRecord(scheduledActionUID)
            } else {
                scheduledAction.mUID = scheduledActionUID
                scheduledActionDbAdapter.updateRecurrenceAttributes(scheduledAction)
                Toast.makeText(activity, R.string.toast_updated_transaction_recurring_schedule, Toast.LENGTH_SHORT)
                    .show()
            }
        } else {
            if (recurrence != null) {
                scheduledAction.setMActionUID(transactionUID)
                scheduledActionDbAdapter.addRecord(scheduledAction, DatabaseAdapter.UpdateMethod.replace)
                Toast.makeText(activity, R.string.toast_scheduled_recurring_transaction, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (mCursor != null) mCursor!!.close()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.default_save_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        //hide the keyboard if it is visible
        val imm = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(mDescriptionEditText!!.applicationWindowToken, 0)
        return when (item.itemId) {
            android.R.id.home -> {
                finish(Activity.RESULT_CANCELED)
                true
            }

            R.id.menu_save -> {
                if (canSave()) {
                    saveNewTransaction()
                } else {
                    if (mAmountEditText!!.getValue() == null) {
                        Toast.makeText(activity, R.string.toast_transanction_amount_required, Toast.LENGTH_SHORT).show()
                    }
                    if (mUseDoubleEntry && mTransferAccountSpinner!!.count == 0) {
                        Toast.makeText(
                            activity,
                            R.string.toast_disable_double_entry_to_save_transaction,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Checks if the pre-requisites for saving the transaction are fulfilled
     *
     * The conditions checked are that a valid amount is entered and that a transfer account is set (where applicable)
     * @return `true` if the transaction can be saved, `false` otherwise
     */
    private fun canSave(): Boolean {
        return mUseDoubleEntry && mAmountEditText!!.isInputValid && mTransferAccountSpinner!!.count > 0 || !mUseDoubleEntry && mAmountEditText!!.isInputValid
    }

    /**
     * Called by the split editor fragment to notify of finished editing
     * @param splitList List of splits produced in the fragment
     */
    fun setSplitList(splitList: MutableList<Split>) {
        mSplitsList = splitList
        val balance = computeBalance(mAccountUID!!, mSplitsList!!)
        mAmountEditText!!.setValue(balance.asBigDecimal())
        mTransactionTypeSwitch!!.isChecked = balance.isNegative
    }

    /**
     * Finishes the fragment appropriately.
     * Depends on how the fragment was loaded, it might have a backstack or not
     */
    private fun finish(resultCode: Int) {
        if (activity!!.supportFragmentManager.backStackEntryCount == 0) {
            activity!!.setResult(resultCode)
            //means we got here directly from the accounts list activity, need to finish
            activity!!.finish()
        } else {
            //go back to transactions list
            activity!!.supportFragmentManager.popBackStack()
        }
    }

    override fun onDateSet(
        calendarDatePickerDialog: CalendarDatePickerDialogFragment,
        year: Int,
        monthOfYear: Int,
        dayOfMonth: Int
    ) {
        val cal: Calendar = GregorianCalendar(year, monthOfYear, dayOfMonth)
        mDateTextView!!.text = DATE_FORMATTER.format(cal.time)
        mDate!![Calendar.YEAR] = year
        mDate!![Calendar.MONTH] = monthOfYear
        mDate!![Calendar.DAY_OF_MONTH] = dayOfMonth
    }

    override fun onTimeSet(radialTimePickerDialog: RadialTimePickerDialogFragment, hourOfDay: Int, minute: Int) {
        val cal: Calendar = GregorianCalendar(0, 0, 0, hourOfDay, minute)
        mTimeTextView!!.text = TIME_FORMATTER.format(cal.time)
        mTime!![Calendar.HOUR_OF_DAY] = hourOfDay
        mTime!![Calendar.MINUTE] = minute
    }

    /**
     * Flag for checking where the TransferFunds dialog has already been displayed to the user
     */
    var mCurrencyConversionDone = false
    override fun transferComplete(amount: Money?) {
        mCurrencyConversionDone = true
        mSplitQuantity = amount

        //The transfer dialog was called while attempting to save. So try saving again
        if (onSaveAttempt) saveNewTransaction()
        onSaveAttempt = false
    }

    override fun onRecurrenceSet(rrule: String) {
        mRecurrenceRule = rrule
        var repeatString: String? = getString(R.string.label_tap_to_create_schedule)
        if (mRecurrenceRule != null) {
            mEventRecurrence.parse(mRecurrenceRule)
            repeatString = EventRecurrenceFormatter.getRepeatString(activity, resources, mEventRecurrence, true)

            //when recurrence is set, we will definitely be saving a template
            mSaveTemplateCheckbox!!.isChecked = true
            mSaveTemplateCheckbox!!.isEnabled = false
        } else {
            mSaveTemplateCheckbox!!.isEnabled = true
            mSaveTemplateCheckbox!!.isChecked = false
        }
        mRecurrenceTextView!!.text = repeatString
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val splitList: MutableList<Split> = data!!.getParcelableArrayListExtra(UxArgument.SPLIT_LIST)!!
            setSplitList(splitList)

            //once split editor has been used and saved, only allow editing through it
            toggleAmountInputEntryMode(false)
            setDoubleEntryViewsVisibility(View.GONE)
            mOpenSplitEditor!!.visibility = View.VISIBLE
        }
    }

    companion object {
        private const val REQUEST_SPLIT_EDITOR = 0x11

        /**
         * Formats a [Date] object into a date string of the format dd MMM yyyy e.g. 18 July 2012
         */
        @JvmField
        val DATE_FORMATTER = DateFormat.getDateInstance()

        /**
         * Formats a [Date] object to time string of format HH:mm e.g. 15:25
         */
        @JvmField
        val TIME_FORMATTER = DateFormat.getTimeInstance()

        /**
         * Strips formatting from a currency string.
         * All non-digit information is removed, but the sign is preserved.
         * @param s String to be stripped
         * @return Stripped string with all non-digits removed
         */
        fun stripCurrencyFormatting(s: String): String {
            if (s.length == 0) return s
            //remove all currency formatting and anything else which is not a number
            val sign = s.trim { it <= ' ' }.substring(0, 1)
            var stripped = s.trim { it <= ' ' }.replace("\\D*".toRegex(), "")
            if (stripped.length == 0) return ""
            if (sign == "+" || sign == "-") {
                stripped = sign + stripped
            }
            return stripped
        }
    }
}