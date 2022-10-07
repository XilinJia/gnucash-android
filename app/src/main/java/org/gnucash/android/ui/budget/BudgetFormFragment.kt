/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (C) 2020 Xilin Jia https://github.com/XilinJia
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
package org.gnucash.android.ui.budget

import android.app.Activity
import android.content.Intent
import android.inputmethodservice.KeyboardView
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.codetroopers.betterpickers.calendardatepicker.CalendarDatePickerDialogFragment
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrenceFormatter
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment.OnRecurrenceSetListener
import com.google.android.material.textfield.TextInputLayout
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.model.Budget
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.transaction.TransactionFormFragment
import org.gnucash.android.ui.util.RecurrenceParser.parse
import org.gnucash.android.ui.util.RecurrenceViewClickListener
import org.gnucash.android.ui.util.widget.CalculatorEditText
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter
import java.sql.Timestamp
import java.text.ParseException
import java.util.*

/**
 * Fragment for creating or editing Budgets
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class BudgetFormFragment : Fragment(), OnRecurrenceSetListener, CalendarDatePickerDialogFragment.OnDateSetListener {
    @JvmField
    @BindView(R.id.input_budget_name)
    var mBudgetNameInput: EditText? = null

    @JvmField
    @BindView(R.id.input_description)
    var mDescriptionInput: EditText? = null

    @JvmField
    @BindView(R.id.input_recurrence)
    var mRecurrenceInput: TextView? = null

    @JvmField
    @BindView(R.id.name_text_input_layout)
    var mNameTextInputLayout: TextInputLayout? = null

    @JvmField
    @BindView(R.id.calculator_keyboard)
    var mKeyboardView: KeyboardView? = null

    @JvmField
    @BindView(R.id.input_budget_amount)
    var mBudgetAmountInput: CalculatorEditText? = null

    @JvmField
    @BindView(R.id.input_budget_account_spinner)
    var mBudgetAccountSpinner: Spinner? = null

    @JvmField
    @BindView(R.id.btn_add_budget_amount)
    var mAddBudgetAmount: Button? = null

    @JvmField
    @BindView(R.id.input_start_date)
    var mStartDateInput: TextView? = null

    @JvmField
    @BindView(R.id.budget_amount_layout)
    var mBudgetAmountLayout: View? = null
    var mEventRecurrence = EventRecurrence()
    var mRecurrenceRule: String? = null
    private var mBudgetsDbAdapter: BudgetsDbAdapter? = null
    private var mBudget: Budget? = null
    private var mStartDate: Calendar? = null
    private var mBudgetAmounts: ArrayList<BudgetAmount>? = null
    private var mAccountsDbAdapter: AccountsDbAdapter? = null
    private var mAccountsCursorAdapter: QualifiedAccountNameCursorAdapter? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_budget_form, container, false)
        ButterKnife.bind(this, view)
        view.findViewById<View>(R.id.btn_remove_item).visibility = View.GONE
        mBudgetAmountInput!!.bindListeners(mKeyboardView)
        mStartDateInput!!.text = TransactionFormFragment.DATE_FORMATTER.format(mStartDate!!.time)
        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBudgetsDbAdapter = BudgetsDbAdapter.instance
        mStartDate = Calendar.getInstance()
        mBudgetAmounts = ArrayList()
        val conditions = "(" + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0 )"
        mAccountsDbAdapter = AccountsDbAdapter.instance
        val accountCursor = mAccountsDbAdapter!!.fetchAccountsOrderedByFavoriteAndFullName(conditions, null)
        mAccountsCursorAdapter = QualifiedAccountNameCursorAdapter(activity, accountCursor)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
        mBudgetAccountSpinner!!.adapter = mAccountsCursorAdapter
        val budgetUID = arguments!!.getString(UxArgument.BUDGET_UID)
        if (budgetUID != null) { //if we are editing the budget
            initViews(mBudgetsDbAdapter!!.getRecord(budgetUID).also { mBudget = it })
        }
        val actionbar = (activity as AppCompatActivity?)!!.supportActionBar!!
        if (mBudget == null) actionbar.title = "Create Budget" else actionbar.title = "Edit Budget"
        mRecurrenceInput!!.setOnClickListener(
            RecurrenceViewClickListener((activity as AppCompatActivity?)!!, mRecurrenceRule!!, this)
        )
    }

    /**
     * Initialize views when editing an existing budget
     * @param budget Budget to use to initialize the views
     */
    private fun initViews(budget: Budget) {
        mBudgetNameInput!!.setText(budget.mName)
        mDescriptionInput!!.setText(budget.mDescription)
        val recurrenceRuleString = budget.mRecurrence!!.ruleString()
        mRecurrenceRule = recurrenceRuleString
        mEventRecurrence.parse(recurrenceRuleString)
        mRecurrenceInput!!.text = budget.mRecurrence!!.repeatString()
        mBudgetAmounts = budget.compactedBudgetAmounts() as ArrayList<BudgetAmount>
        toggleAmountInputVisibility()
    }

    /**
     * Extracts the budget amounts from the form
     *
     * If the budget amount was input using the simple form, then read the values.<br></br>
     * Else return the values gotten from the BudgetAmountEditor
     * @return List of budget amounts
     */
    private fun extractBudgetAmounts(): ArrayList<BudgetAmount>? {
        val value = mBudgetAmountInput!!.getValue() ?: return mBudgetAmounts
        return if (mBudgetAmounts!!.isEmpty()) { //has not been set in budget amounts editor
            val budgetAmounts = ArrayList<BudgetAmount>()
            val amount = Money(value, Commodity.DEFAULT_COMMODITY)
            val accountUID = mAccountsDbAdapter!!.getUID(mBudgetAccountSpinner!!.selectedItemId)
            val budgetAmount = BudgetAmount(amount, accountUID)
            budgetAmounts.add(budgetAmount)
            budgetAmounts
        } else {
            mBudgetAmounts
        }
    }

    /**
     * Checks that this budget can be saved
     * Also sets the appropriate error messages on the relevant views
     *
     * For a budget to be saved, it needs to have a name, an amount and a schedule
     * @return `true` if the budget can be saved, `false` otherwise
     */
    private fun canSave(): Boolean {
        if (mEventRecurrence.until != null && mEventRecurrence.until.isNotEmpty()
            || mEventRecurrence.count <= 0
        ) {
            Toast.makeText(
                activity,
                "Set a number periods in the recurrence dialog to save the budget",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        mBudgetAmounts = extractBudgetAmounts()
        val budgetName = mBudgetNameInput!!.text.toString()
        val canSave = (mRecurrenceRule != null && budgetName.isNotEmpty()
                && mBudgetAmounts!!.isNotEmpty())
        if (!canSave) {
            if (budgetName.isEmpty()) {
                mNameTextInputLayout!!.error = "A name is required"
                mNameTextInputLayout!!.isErrorEnabled = true
            } else {
                mNameTextInputLayout!!.isErrorEnabled = false
            }
            if (mBudgetAmounts!!.isEmpty()) {
                mBudgetAmountInput!!.error = "Enter an amount for the budget"
                Toast.makeText(
                    activity, "Add budget amounts in order to save the budget",
                    Toast.LENGTH_SHORT
                ).show()
            }
            if (mRecurrenceRule == null) {
                Toast.makeText(
                    activity, "Set a repeat pattern to create a budget!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        return canSave
    }

    /**
     * Extracts the information from the form and saves the budget
     */
    private fun saveBudget() {
        if (!canSave()) return
        val name = mBudgetNameInput!!.text.toString().trim { it <= ' ' }
        if (mBudget == null) {
            mBudget = Budget(name)
        } else {
            mBudget!!.setMName(name)
        }

        // TODO: 22.10.2015 set the period num of the budget amount
        extractBudgetAmounts()
        mBudget!!.setMBudgetAmounts(mBudgetAmounts!!)
        mBudget!!.mDescription = mDescriptionInput!!.text.toString().trim { it <= ' ' }
        val recurrence = parse(mEventRecurrence)
        recurrence!!.mPeriodStart = Timestamp(mStartDate!!.timeInMillis)
        mBudget!!.setMRecurrence(recurrence)
        mBudgetsDbAdapter!!.addRecord(mBudget!!, DatabaseAdapter.UpdateMethod.insert)
        activity!!.finish()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.default_save_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> {
                saveBudget()
                return true
            }
        }
        return false
    }

    @OnClick(R.id.input_start_date)
    fun onClickBudgetStartDate(v: View) {
        var dateMillis: Long = 0
        try {
            val date = TransactionFormFragment.DATE_FORMATTER.parse((v as TextView).text.toString())
            dateMillis = date!!.time
        } catch (e: ParseException) {
            Log.e(tag, "Error converting input time to Date object")
        }
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dateMillis
        val year = calendar[Calendar.YEAR]
        val monthOfYear = calendar[Calendar.MONTH]
        val dayOfMonth = calendar[Calendar.DAY_OF_MONTH]
        val datePickerDialog = CalendarDatePickerDialogFragment()
        datePickerDialog.setOnDateSetListener(this@BudgetFormFragment)
        datePickerDialog.setPreselectedDate(year, monthOfYear, dayOfMonth)
        datePickerDialog.show(fragmentManager!!, "date_picker_fragment")
    }

    @OnClick(R.id.btn_add_budget_amount)
    fun onOpenBudgetAmountEditor(v: View?) {
        val intent = Intent(activity, FormActivity::class.java)
        intent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET_AMOUNT_EDITOR.name)
        mBudgetAmounts = extractBudgetAmounts()
        intent.putParcelableArrayListExtra(UxArgument.BUDGET_AMOUNT_LIST, mBudgetAmounts)
        startActivityForResult(intent, REQUEST_EDIT_BUDGET_AMOUNTS)
    }

    override fun onRecurrenceSet(rrule: String) {
        mRecurrenceRule = rrule
        var repeatString: String? = getString(R.string.label_tap_to_create_schedule)
        if (mRecurrenceRule != null) {
            mEventRecurrence.parse(mRecurrenceRule)
            repeatString = EventRecurrenceFormatter.getRepeatString(activity, resources, mEventRecurrence, true)
        }
        mRecurrenceInput!!.text = repeatString
    }

    override fun onDateSet(dialog: CalendarDatePickerDialogFragment, year: Int, monthOfYear: Int, dayOfMonth: Int) {
        val cal: Calendar = GregorianCalendar(year, monthOfYear, dayOfMonth)
        mStartDateInput!!.text = TransactionFormFragment.DATE_FORMATTER.format(cal.time)
        mStartDate!![Calendar.YEAR] = year
        mStartDate!![Calendar.MONTH] = monthOfYear
        mStartDate!![Calendar.DAY_OF_MONTH] = dayOfMonth
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_EDIT_BUDGET_AMOUNTS) {
            if (resultCode == Activity.RESULT_OK) {
                val budgetAmounts = data!!.getParcelableArrayListExtra<BudgetAmount>(UxArgument.BUDGET_AMOUNT_LIST)
                if (budgetAmounts != null) {
                    mBudgetAmounts = budgetAmounts
                    toggleAmountInputVisibility()
                }
                return
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Toggles the visibility of the amount input based on [.mBudgetAmounts]
     */
    private fun toggleAmountInputVisibility() {
        if (mBudgetAmounts!!.size > 1) {
            mBudgetAmountLayout!!.visibility = View.GONE
            mAddBudgetAmount!!.text = "Edit Budget Amounts"
        } else {
            mAddBudgetAmount!!.text = "Add Budget Amounts"
            mBudgetAmountLayout!!.visibility = View.VISIBLE
            if (mBudgetAmounts!!.isNotEmpty()) {
                val budgetAmount = mBudgetAmounts!![0]
                mBudgetAmountInput!!.setValue(budgetAmount.mAmount!!.asBigDecimal())
                mBudgetAccountSpinner!!.setSelection(mAccountsCursorAdapter!!.getPosition(budgetAmount.mAccountUID!!))
            }
        }
    }

    companion object {
        const val REQUEST_EDIT_BUDGET_AMOUNTS = 0xBA
    }
}