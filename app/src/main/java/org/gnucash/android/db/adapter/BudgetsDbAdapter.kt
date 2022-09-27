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
package org.gnucash.android.db.adapter

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQueryBuilder
import android.database.sqlite.SQLiteStatement
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema.BudgetAmountEntry
import org.gnucash.android.db.DatabaseSchema.BudgetEntry
import org.gnucash.android.model.Budget
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.model.Money
import org.gnucash.android.model.Recurrence

/**
 * Database adapter for accessing [org.gnucash.android.model.Budget] records
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class BudgetsDbAdapter
/**
 * Opens the database adapter with an existing database
 *
 * @param db        SQLiteDatabase object
 */(
    db: SQLiteDatabase?, private val mBudgetAmountsDbAdapter: BudgetAmountsDbAdapter,
    private val mRecurrenceDbAdapter: RecurrenceDbAdapter
) : DatabaseAdapter<Budget>(
    db!!, BudgetEntry.TABLE_NAME, arrayOf(
        BudgetEntry.COLUMN_NAME,
        BudgetEntry.COLUMN_DESCRIPTION,
        BudgetEntry.COLUMN_RECURRENCE_UID,
        BudgetEntry.COLUMN_NUM_PERIODS
    )
) {
    override fun addRecord(budget: Budget, updateMethod: UpdateMethod) {
        require(budget.getMBudgetAmounts().size != 0) { "Budgets must have budget amounts" }
        mRecurrenceDbAdapter.addRecord(budget.mRecurrence!!, updateMethod)
        super.addRecord(budget, updateMethod)
        mBudgetAmountsDbAdapter.deleteBudgetAmountsForBudget(budget.mUID!!)
        for (budgetAmount in budget.getMBudgetAmounts()) {
            mBudgetAmountsDbAdapter.addRecord(budgetAmount, updateMethod)
        }
    }

    override fun bulkAddRecords(budgetList: List<Budget>, updateMethod: UpdateMethod): Long {
        val budgetAmountList: MutableList<BudgetAmount> = ArrayList(budgetList.size * 2)
        for (budget in budgetList) {
            budgetAmountList.addAll(budget.getMBudgetAmounts())
        }

        //first add the recurrences, they have no dependencies (foreign key constraints)
        val recurrenceList: MutableList<Recurrence> = ArrayList(budgetList.size)
        for (budget in budgetList) {
            recurrenceList.add(budget.mRecurrence!!)
        }
        mRecurrenceDbAdapter.bulkAddRecords(recurrenceList.toList(), updateMethod)

        //now add the budgets themselves
        val nRow = super.bulkAddRecords(budgetList, updateMethod)

        //then add the budget amounts, they require the budgets to exist
        if (nRow > 0 && !budgetAmountList.isEmpty()) {
            mBudgetAmountsDbAdapter.bulkAddRecords(budgetAmountList, updateMethod)
        }
        return nRow
    }

    override fun buildModelInstance(cursor: Cursor): Budget {
        val name = cursor.getString(cursor.getColumnIndexOrThrow(BudgetEntry.COLUMN_NAME))
        val description = cursor.getString(cursor.getColumnIndexOrThrow(BudgetEntry.COLUMN_DESCRIPTION))
        val recurrenceUID = cursor.getString(cursor.getColumnIndexOrThrow(BudgetEntry.COLUMN_RECURRENCE_UID))
        val numPeriods = cursor.getLong(cursor.getColumnIndexOrThrow(BudgetEntry.COLUMN_NUM_PERIODS))
        val budget = Budget(name)
        budget.mDescription = description
        budget.setMRecurrence(mRecurrenceDbAdapter.getRecord(recurrenceUID))
        budget.mNumberOfPeriods = numPeriods
        populateBaseModelAttributes(cursor, budget)
        budget.setMBudgetAmounts(mBudgetAmountsDbAdapter.getBudgetAmountsForBudget(budget.mUID).toMutableList())
        return budget
    }

    protected override fun setBindings(stmt: SQLiteStatement, budget: Budget): SQLiteStatement {
        stmt.clearBindings()
        stmt.bindString(1, budget.mName)
        if (budget.mDescription != null) stmt.bindString(2, budget.mDescription)
        stmt.bindString(3, budget.mRecurrence!!.mUID)
        stmt.bindLong(4, budget.mNumberOfPeriods)
        stmt.bindString(5, budget.mUID)
        return stmt
    }

    /**
     * Fetch all budgets which have an amount specified for the account
     * @param accountUID GUID of account
     * @return Cursor with budgets data
     */
    fun fetchBudgetsForAccount(accountUID: String): Cursor {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.tables = (BudgetEntry.TABLE_NAME + "," + BudgetAmountEntry.TABLE_NAME
                + " ON " + BudgetEntry.TABLE_NAME + "." + BudgetEntry.COLUMN_UID + " = "
                + BudgetAmountEntry.TABLE_NAME + "." + BudgetAmountEntry.COLUMN_BUDGET_UID)
        queryBuilder.isDistinct = true
        val projectionIn = arrayOf(BudgetEntry.TABLE_NAME + ".*")
        val selection = BudgetAmountEntry.TABLE_NAME + "." + BudgetAmountEntry.COLUMN_ACCOUNT_UID + " = ?"
        val selectionArgs = arrayOf(accountUID)
        val sortOrder = BudgetEntry.TABLE_NAME + "." + BudgetEntry.COLUMN_NAME + " ASC"
        return queryBuilder.query(mDb, projectionIn, selection, selectionArgs, null, null, sortOrder)
    }

    /**
     * Returns the budgets associated with a specific account
     * @param accountUID GUID of the account
     * @return List of budgets for the account
     */
    fun getAccountBudgets(accountUID: String): List<Budget> {
        val cursor = fetchBudgetsForAccount(accountUID)
        val budgets: MutableList<Budget> = ArrayList()
        while (cursor.moveToNext()) {
            budgets.add(buildModelInstance(cursor))
        }
        cursor.close()
        return budgets
    }

    /**
     * Returns the sum of the account balances for all accounts in a budget for a specified time period
     *
     * This represents the total amount spent within the account of this budget in a given period
     * @param budgetUID GUID of budget
     * @param periodStart Start of the budgeting period in millis
     * @param periodEnd End of the budgeting period in millis
     * @return Balance of all the accounts
     */
    fun getAccountSum(budgetUID: String?, periodStart: Long, periodEnd: Long): Money {
        val budgetAmounts = mBudgetAmountsDbAdapter.getBudgetAmountsForBudget(budgetUID)
        val accountUIDs: MutableList<String?> = ArrayList()
        for (budgetAmount in budgetAmounts) {
            accountUIDs.add(budgetAmount.mAccountUID)
        }
        return AccountsDbAdapter(mDb).getAccountsBalance(accountUIDs, periodStart, periodEnd)
    }

    companion object {
        /**
         * Returns an instance of the budget database adapter
         * @return BudgetsDbAdapter instance
         */
        @JvmStatic
        val instance: BudgetsDbAdapter
            get() = GnuCashApplication.budgetDbAdapter!!
    }
}