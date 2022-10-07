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
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.ButterKnife
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.model.Budget
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.transaction.TransactionsActivity
import org.gnucash.android.ui.util.widget.EmptyRecyclerView
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Fragment for displaying budget details
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class BudgetDetailFragment : Fragment(), Refreshable {
    @JvmField
    @BindView(R.id.primary_text)
    var mBudgetNameTextView: TextView? = null

    @JvmField
    @BindView(R.id.secondary_text)
    var mBudgetDescriptionTextView: TextView? = null

    @JvmField
    @BindView(R.id.budget_recurrence)
    var mBudgetRecurrence: TextView? = null

    @JvmField
    @BindView(R.id.budget_amount_recycler)
    var mRecyclerView: EmptyRecyclerView? = null
    private var mBudgetUID: String? = null
    private var mBudgetsDbAdapter: BudgetsDbAdapter? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_budget_detail, container, false)
        ButterKnife.bind(this, view)
        mBudgetDescriptionTextView!!.maxLines = 3
        mRecyclerView!!.setHasFixedSize(true)
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
        mBudgetUID = arguments!!.getString(UxArgument.BUDGET_UID)
        bindViews()
        setHasOptionsMenu(true)
    }

    private fun bindViews() {
        val budget = mBudgetsDbAdapter!!.getRecord(mBudgetUID!!)
        mBudgetNameTextView!!.text = budget.mName
        val description = budget.mDescription
        if (!description.isNullOrEmpty()) mBudgetDescriptionTextView!!.text = description else {
            mBudgetDescriptionTextView!!.visibility = View.GONE
        }
        mBudgetRecurrence!!.text = budget.mRecurrence!!.repeatString()
        mRecyclerView!!.adapter = BudgetAmountAdapter()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        val view = activity!!.findViewById<View>(R.id.fab_create_budget)
        if (view != null) {
            view.visibility = View.GONE
        }
    }

    override fun refresh() {
        bindViews()
        val budgetName = mBudgetsDbAdapter!!.getAttribute(mBudgetUID!!, DatabaseSchema.BudgetEntry.COLUMN_NAME)
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar!!
        actionBar.title = "Budget: $budgetName"
    }

    override fun refresh(uid: String?) {
        mBudgetUID = uid
        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.budget_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_edit_budget -> {
                val addAccountIntent = Intent(activity, FormActivity::class.java)
                addAccountIntent.action = Intent.ACTION_INSERT_OR_EDIT
                addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name)
                addAccountIntent.putExtra(UxArgument.BUDGET_UID, mBudgetUID)
                startActivityForResult(addAccountIntent, 0x11)
                true
            }

            else -> false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            refresh()
        }
    }

    inner class BudgetAmountAdapter : RecyclerView.Adapter<BudgetAmountAdapter.BudgetAmountViewHolder>() {
        private val mBudgetAmounts: List<BudgetAmount>
        private val mBudget: Budget = mBudgetsDbAdapter!!.getRecord(mBudgetUID!!)

        init {
            mBudgetAmounts = mBudget.compactedBudgetAmounts()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetAmountViewHolder {
            val view = LayoutInflater.from(activity).inflate(R.layout.cardview_budget_amount, parent, false)
            return BudgetAmountViewHolder(view)
        }

        override fun onBindViewHolder(holder: BudgetAmountViewHolder, position: Int) {
            val budgetAmount = mBudgetAmounts[position]
            val projectedAmount = budgetAmount.mAmount
            val accountsDbAdapter = AccountsDbAdapter.instance
            holder.budgetAccount!!.text = accountsDbAdapter.getAccountFullName(budgetAmount.mAccountUID!!)
            holder.budgetAmount!!.text = projectedAmount!!.formattedString()
            val spentAmount = accountsDbAdapter.getAccountBalance(
                budgetAmount.mAccountUID!!,
                mBudget.startofCurrentPeriod(), mBudget.endOfCurrentPeriod()
            )
            holder.budgetSpent!!.text = spentAmount.abs().formattedString()
            holder.budgetLeft!!.text = projectedAmount.subtract(spentAmount.abs()).formattedString()
            var budgetProgress = 0.0
            if (projectedAmount.asDouble() != 0.0) {
                budgetProgress = spentAmount.asBigDecimal().divide(
                    projectedAmount.asBigDecimal(),
                    spentAmount.mCommodity!!.smallestFractionDigits(),
                    RoundingMode.HALF_EVEN
                ).toDouble()
            }
            holder.budgetIndicator!!.progress = (budgetProgress * 100).toInt()
            holder.budgetSpent!!.setTextColor(BudgetsActivity.getBudgetProgressColor(1 - budgetProgress))
            holder.budgetLeft!!.setTextColor(BudgetsActivity.getBudgetProgressColor(1 - budgetProgress))
            generateChartData(holder.budgetChart, budgetAmount)
            holder.itemView.setOnClickListener {
                val intent = Intent(activity, TransactionsActivity::class.java)
                intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mBudgetAmounts[position].mAccountUID)
                startActivityForResult(intent, 0x10)
            }
        }

        /**
         * Generate the chart data for the chart
         * @param barChart View where to display the chart
         * @param budgetAmount BudgetAmount to visualize
         */
        fun generateChartData(barChart: BarChart?, budgetAmount: BudgetAmount) {
            // FIXME: 25.10.15 chart is broken
            val accountsDbAdapter = AccountsDbAdapter.instance
            val barEntries: MutableList<BarEntry> = ArrayList()
            val xVals: MutableList<String> = ArrayList()

            //todo: refactor getNumberOfPeriods into budget
            var budgetPeriods = mBudget.mNumberOfPeriods.toInt()
            budgetPeriods = if (budgetPeriods == 0) 12 else budgetPeriods
            val periods =
                mBudget.mRecurrence!!.numberOfPeriods(budgetPeriods) //// FIXME: 15.08.2016 why do we need number of periods
            for (periodNum in 1..periods) {
                val amount = accountsDbAdapter.getAccountBalance(
                    budgetAmount.mAccountUID!!,
                    mBudget.startOfPeriod(periodNum), mBudget.endOfPeriod(periodNum)
                )
                    .asBigDecimal()
                if (amount == BigDecimal.ZERO) continue
                barEntries.add(BarEntry(amount.toFloat(), periodNum))
                xVals.add(mBudget.mRecurrence!!.textOfCurrentPeriod(periodNum))
            }
            val label = accountsDbAdapter.getAccountName(budgetAmount.mAccountUID)
            val barDataSet = BarDataSet(barEntries, label)
            val barData = BarData(xVals, barDataSet)
            val limitLine = LimitLine(budgetAmount.mAmount!!.asBigDecimal().toFloat())
            limitLine.lineWidth = 2f
            limitLine.lineColor = Color.RED
            barChart!!.data = barData
            barChart.axisLeft.addLimitLine(limitLine)
            val maxValue = budgetAmount.mAmount!!.add(budgetAmount.mAmount!!.multiply(BigDecimal("0.2"))).asBigDecimal()
            barChart.axisLeft.axisMaxValue = maxValue.toFloat()
            barChart.animateX(1000)
            barChart.isAutoScaleMinMaxEnabled = true
            barChart.setDrawValueAboveBar(true)
            barChart.invalidate()
        }

        override fun getItemCount(): Int {
            return mBudgetAmounts.size
        }

        inner class BudgetAmountViewHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!) {
            @JvmField
            @BindView(R.id.budget_account)
            var budgetAccount: TextView? = null

            @JvmField
            @BindView(R.id.budget_amount)
            var budgetAmount: TextView? = null

            @JvmField
            @BindView(R.id.budget_spent)
            var budgetSpent: TextView? = null

            @JvmField
            @BindView(R.id.budget_left)
            var budgetLeft: TextView? = null

            @JvmField
            @BindView(R.id.budget_indicator)
            var budgetIndicator: ProgressBar? = null

            @JvmField
            @BindView(R.id.budget_chart)
            var budgetChart: BarChart? = null

            init {
                ButterKnife.bind(this, itemView!!)
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(budgetUID: String?): BudgetDetailFragment {
            val fragment = BudgetDetailFragment()
            val args = Bundle()
            args.putString(UxArgument.BUDGET_UID, budgetUID)
            fragment.arguments = args
            return fragment
        }
    }
}