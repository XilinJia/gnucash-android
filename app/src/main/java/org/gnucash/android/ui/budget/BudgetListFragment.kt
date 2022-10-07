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
package org.gnucash.android.ui.budget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.ButterKnife
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseCursorLoader
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.ui.budget.BudgetDetailFragment.Companion.newInstance
import org.gnucash.android.ui.budget.BudgetListFragment.BudgetRecyclerAdapter.BudgetViewHolder
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.util.CursorRecyclerAdapter
import org.gnucash.android.ui.util.widget.EmptyRecyclerView
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Budget list fragment
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class BudgetListFragment : Fragment(), Refreshable, LoaderManager.LoaderCallbacks<Cursor> {
    private var mBudgetRecyclerAdapter: BudgetRecyclerAdapter? = null
    private var mBudgetsDbAdapter: BudgetsDbAdapter? = null

    @JvmField
    @BindView(R.id.budget_recycler_view)
    var mRecyclerView: EmptyRecyclerView? = null

    @JvmField
    @BindView(R.id.empty_view)
    var mProposeBudgets: Button? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_budget_list, container, false)
        ButterKnife.bind(this, view)
        mRecyclerView!!.setHasFixedSize(true)
        mRecyclerView!!.emptyView = mProposeBudgets
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val gridLayoutManager = GridLayoutManager(activity, 2)
            mRecyclerView!!.layoutManager = gridLayoutManager
        } else {
            val mLayoutManager = LinearLayoutManager(activity)
            mRecyclerView!!.layoutManager = mLayoutManager
        }
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mBudgetsDbAdapter = BudgetsDbAdapter.instance
        mBudgetRecyclerAdapter = BudgetRecyclerAdapter(null)
        mRecyclerView!!.adapter = mBudgetRecyclerAdapter
        loaderManager.initLoader(0, null, this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        Log.d(LOG_TAG, "Creating the accounts loader")
        return BudgetsCursorLoader(activity)
    }

    override fun onLoadFinished(loaderCursor: Loader<Cursor>, cursor: Cursor) {
        Log.d(LOG_TAG, "Budget loader finished. Swapping in cursor")
        mBudgetRecyclerAdapter!!.swapCursor(cursor)
        mBudgetRecyclerAdapter!!.notifyDataSetChanged()
    }

    override fun onLoaderReset(arg0: Loader<Cursor>) {
        Log.d(LOG_TAG, "Resetting the accounts loader")
        mBudgetRecyclerAdapter!!.swapCursor(null)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        activity!!.findViewById<View>(R.id.fab_create_budget).visibility = View.VISIBLE
        (activity as AppCompatActivity?)!!.supportActionBar!!.title = "Budgets"
    }

    override fun refresh() {
        loaderManager.restartLoader(0, null, this)
    }

    /**
     * This method does nothing with the GUID.
     * Is equivalent to calling [.refresh]
     * @param uid GUID of relevant item to be refreshed
     */
    override fun refresh(uid: String?) {
        refresh()
    }

    /**
     * Opens the budget detail fragment
     * @param budgetUID GUID of budget
     */
    fun onClickBudget(budgetUID: String?) {
        val fragmentManager = activity!!.supportFragmentManager
        val fragmentTransaction = fragmentManager
            .beginTransaction()
        fragmentTransaction.replace(R.id.fragment_container, newInstance(budgetUID))
        fragmentTransaction.addToBackStack(null)
        fragmentTransaction.commit()
    }

    /**
     * Launches the FormActivity for editing the budget
     * @param budgetId Db record Id of the budget
     */
    private fun editBudget(budgetId: Long) {
        val addAccountIntent = Intent(activity, FormActivity::class.java)
        addAccountIntent.action = Intent.ACTION_INSERT_OR_EDIT
        addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name)
        addAccountIntent.putExtra(UxArgument.BUDGET_UID, mBudgetsDbAdapter!!.getUID(budgetId))
        startActivityForResult(addAccountIntent, REQUEST_EDIT_BUDGET)
    }

    /**
     * Delete the budget from the database
     * @param budgetId Database record ID
     */
    private fun deleteBudget(budgetId: Long) {
        BudgetsDbAdapter.instance.deleteRecord(budgetId)
        refresh()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            refresh()
        }
    }

    internal inner class BudgetRecyclerAdapter(cursor: Cursor?) : CursorRecyclerAdapter<BudgetViewHolder>(cursor) {
        override fun onBindViewHolderCursor(holder: BudgetViewHolder, cursor: Cursor?) {
            val budget = mBudgetsDbAdapter!!.buildModelInstance(cursor!!)
            holder.budgetId = mBudgetsDbAdapter!!.getID(budget.mUID!!)
            holder.budgetName!!.text = budget.mName
            val accountsDbAdapter = AccountsDbAdapter.instance
            val accountString: String
            val numberOfAccounts = budget.numberOfAccounts()
            accountString = if (numberOfAccounts == 1) {
                accountsDbAdapter.getAccountFullName(budget.getMBudgetAmounts()[0].mAccountUID!!)
            } else {
                "$numberOfAccounts budgeted accounts"
            }
            holder.accountName!!.text = accountString
            holder.budgetRecurrence!!.text = (budget.mRecurrence!!.repeatString() + " - "
                    + budget.mRecurrence!!.daysLeftInCurrentPeriod() + " days left")
            var spentAmountValue = BigDecimal.ZERO
            for (budgetAmount in budget.compactedBudgetAmounts()) {
                val balance = accountsDbAdapter.getAccountBalance(
                    budgetAmount.mAccountUID!!,
                    budget.startofCurrentPeriod(), budget.endOfCurrentPeriod()
                )
                spentAmountValue = spentAmountValue.add(balance.asBigDecimal())
            }
            val budgetTotal = budget.amountSum()
            val commodity = budgetTotal!!.mCommodity
            val usedAmount = (commodity!!.symbol + spentAmountValue + " of "
                    + budgetTotal.formattedString())
            holder.budgetAmount!!.text = usedAmount
            val budgetProgress = spentAmountValue.divide(
                budgetTotal.asBigDecimal(),
                commodity.smallestFractionDigits(), RoundingMode.HALF_EVEN
            )
                .toDouble()
            holder.budgetIndicator!!.progress = (budgetProgress * 100).toInt()
            holder.budgetAmount!!.setTextColor(BudgetsActivity.getBudgetProgressColor(1 - budgetProgress))
            holder.itemView.setOnClickListener { onClickBudget(budget.mUID) }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.cardview_budget, parent, false)
            return BudgetViewHolder(v)
        }

        internal inner class BudgetViewHolder(itemView: View?) : RecyclerView.ViewHolder(
            itemView!!
        ), PopupMenu.OnMenuItemClickListener {
            @JvmField
            @BindView(R.id.primary_text)
            var budgetName: TextView? = null

            @JvmField
            @BindView(R.id.secondary_text)
            var accountName: TextView? = null

            @JvmField
            @BindView(R.id.budget_amount)
            var budgetAmount: TextView? = null

            @JvmField
            @BindView(R.id.options_menu)
            var optionsMenu: ImageView? = null

            @JvmField
            @BindView(R.id.budget_indicator)
            var budgetIndicator: ProgressBar? = null

            @JvmField
            @BindView(R.id.budget_recurrence)
            var budgetRecurrence: TextView? = null
            var budgetId: Long = 0

            init {
                ButterKnife.bind(this, itemView!!)
                optionsMenu!!.setOnClickListener { v ->
                    val popup = PopupMenu(activity!!, v)
                    popup.setOnMenuItemClickListener(this@BudgetViewHolder)
                    val inflater = popup.menuInflater
                    inflater.inflate(R.menu.budget_context_menu, popup.menu)
                    popup.show()
                }
            }

            override fun onMenuItemClick(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.context_menu_edit_budget -> {
                        editBudget(budgetId)
                        true
                    }

                    R.id.context_menu_delete -> {
                        deleteBudget(budgetId)
                        true
                    }

                    else -> false
                }
            }
        }
    }

    /**
     * Loads Budgets asynchronously from the database
     */
    private class BudgetsCursorLoader
    /**
     * Constructor
     * Initializes the content observer
     *
     * @param context Application context
     */
        (context: Context?) : DatabaseCursorLoader(context) {
        override fun loadInBackground(): Cursor {
            mDatabaseAdapter = BudgetsDbAdapter.instance
            return mDatabaseAdapter!!.fetchAllRecords(null, null, DatabaseSchema.BudgetEntry.COLUMN_NAME + " ASC")
        }
    }

    companion object {
        private const val LOG_TAG = "BudgetListFragment"
        private const val REQUEST_EDIT_BUDGET = 0xB
        private const val REQUEST_OPEN_ACCOUNT = 0xC
    }
}