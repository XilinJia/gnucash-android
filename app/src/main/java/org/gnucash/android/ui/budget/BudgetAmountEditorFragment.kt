/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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
import android.database.Cursor
import android.inputmethodservice.KeyboardView
import android.os.Bundle
import android.view.*
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import butterknife.BindView
import butterknife.ButterKnife
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.instance
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Commodity.Companion.getInstance
import org.gnucash.android.model.Money
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.util.widget.CalculatorEditText
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter

/**
 * Fragment for editing budgeting amounts
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class BudgetAmountEditorFragment : Fragment() {
    private var mAccountCursor: Cursor? = null
    private var mAccountCursorAdapter: QualifiedAccountNameCursorAdapter? = null
    private val mBudgetAmountViews: MutableList<View> = ArrayList()
    private var mAccountsDbAdapter: AccountsDbAdapter? = null

    @JvmField
    @BindView(R.id.budget_amount_layout)
    var mBudgetAmountTableLayout: LinearLayout? = null

    @JvmField
    @BindView(R.id.calculator_keyboard)
    var mKeyboardView: KeyboardView? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_budget_amount_editor, container, false)
        ButterKnife.bind(this, view)
        setupAccountSpinnerAdapter()
        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mAccountsDbAdapter = instance
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar!!
        actionBar.title = "Edit Budget Amounts"
        setHasOptionsMenu(true)
        val budgetAmounts = arguments!!.getParcelableArrayList<BudgetAmount>(UxArgument.BUDGET_AMOUNT_LIST)
        if (budgetAmounts != null) {
            if (budgetAmounts.isEmpty()) {
                val viewHolder = addBudgetAmountView(null).tag as BudgetAmountViewHolder
                viewHolder.removeItemBtn!!.visibility = View.GONE //there should always be at least one
            } else {
                loadBudgetAmountViews(budgetAmounts)
            }
        } else {
            val viewHolder = addBudgetAmountView(null).tag as BudgetAmountViewHolder
            viewHolder.removeItemBtn!!.visibility = View.GONE //there should always be at least one
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.budget_amount_editor_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_add_budget_amount -> {
                addBudgetAmountView(null)
                true
            }

            R.id.menu_save -> {
                saveBudgetAmounts()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Checks if the budget amounts can be saved
     * @return `true` if all amounts a properly entered, `false` otherwise
     */
    private fun canSave(): Boolean {
        for (budgetAmountView in mBudgetAmountViews) {
            val viewHolder = budgetAmountView.tag as BudgetAmountViewHolder
            viewHolder.amountEditText!!.evaluate()
            if (viewHolder.amountEditText!!.error != null) {
                return false
            }
            //at least one account should be loaded (don't create budget with empty account tree
            if (viewHolder.budgetAccountSpinner!!.count == 0) {
                Toast.makeText(
                    activity, "You need an account hierarchy to create a budget!",
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }
        }
        return true
    }

    private fun saveBudgetAmounts() {
        if (canSave()) {
            val budgetAmounts = extractBudgetAmounts() as ArrayList<BudgetAmount?>
            val data = Intent()
            data.putParcelableArrayListExtra(UxArgument.BUDGET_AMOUNT_LIST, budgetAmounts)
            activity!!.setResult(Activity.RESULT_OK, data)
            activity!!.finish()
        }
    }

    /**
     * Load views for the budget amounts
     * @param budgetAmounts List of [BudgetAmount]s
     */
    private fun loadBudgetAmountViews(budgetAmounts: List<BudgetAmount>) {
        for (budgetAmount in budgetAmounts) {
            addBudgetAmountView(budgetAmount)
        }
    }

    /**
     * Inflates a new BudgetAmount item view and adds it to the UI.
     *
     * If the `budgetAmount` is not null, then it is used to initialize the view
     * @param budgetAmount Budget amount
     */
    private fun addBudgetAmountView(budgetAmount: BudgetAmount?): View {
        val layoutInflater = activity!!.layoutInflater
        val budgetAmountView = layoutInflater.inflate(
            R.layout.item_budget_amount,
            mBudgetAmountTableLayout, false
        )
        val viewHolder = BudgetAmountViewHolder(budgetAmountView)
        if (budgetAmount != null) {
            viewHolder.bindViews(budgetAmount)
        }
        mBudgetAmountTableLayout!!.addView(budgetAmountView, 0)
        mBudgetAmountViews.add(budgetAmountView)
        //        mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
        return budgetAmountView
    }

    /**
     * Loads the accounts in the spinner
     */
    private fun setupAccountSpinnerAdapter() {
        val conditions = "(" + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0 )"
        if (mAccountCursor != null) {
            mAccountCursor!!.close()
        }
        mAccountCursor = mAccountsDbAdapter!!.fetchAccountsOrderedByFavoriteAndFullName(conditions, null)
        mAccountCursorAdapter = QualifiedAccountNameCursorAdapter(activity, mAccountCursor)
    }

    /**
     * Extract [BudgetAmount]s from the views
     * @return List of budget amounts
     */
    private fun extractBudgetAmounts(): List<BudgetAmount?> {
        val budgetAmounts: MutableList<BudgetAmount?> = ArrayList()
        for (view in mBudgetAmountViews) {
            val viewHolder = view.tag as BudgetAmountViewHolder
            val amountValue = viewHolder.amountEditText!!.getValue() ?: continue
            val amount = Money(amountValue, Commodity.DEFAULT_COMMODITY)
            val accountUID = mAccountsDbAdapter!!.getUID(viewHolder.budgetAccountSpinner!!.selectedItemId)
            val budgetAmount = BudgetAmount(amount, accountUID)
            budgetAmounts.add(budgetAmount)
        }
        return budgetAmounts
    }

    /**
     * View holder for budget amounts
     */
    internal inner class BudgetAmountViewHolder(var itemView: View) {
        @JvmField
        @BindView(R.id.currency_symbol)
        var currencySymbolTextView: TextView? = null

        @JvmField
        @BindView(R.id.input_budget_amount)
        var amountEditText: CalculatorEditText? = null

        @JvmField
        @BindView(R.id.btn_remove_item)
        var removeItemBtn: ImageView? = null

        @JvmField
        @BindView(R.id.input_budget_account_spinner)
        var budgetAccountSpinner: Spinner? = null

        init {
            ButterKnife.bind(this, itemView)
            itemView.tag = this
            amountEditText!!.bindListeners(mKeyboardView)
            budgetAccountSpinner!!.adapter = mAccountCursorAdapter
            budgetAccountSpinner!!.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                    val currencyCode = mAccountsDbAdapter!!.getMMnemonic(mAccountsDbAdapter!!.getUID(id))
                    val commodity = getInstance(currencyCode)
                    currencySymbolTextView!!.text = commodity.symbol
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    //nothing to see here, move along
                }
            }
            removeItemBtn!!.setOnClickListener {
                mBudgetAmountTableLayout!!.removeView(itemView)
                mBudgetAmountViews.remove(itemView)
            }
        }

        fun bindViews(budgetAmount: BudgetAmount) {
            amountEditText!!.setValue(budgetAmount.mAmount!!.asBigDecimal())
            budgetAccountSpinner!!.setSelection(mAccountCursorAdapter!!.getPosition(budgetAmount.mAccountUID!!))
        }
    }

    companion object {
        fun newInstance(args: Bundle?): BudgetAmountEditorFragment {
            val fragment = BudgetAmountEditorFragment()
            fragment.arguments = args
            return fragment
        }
    }
}