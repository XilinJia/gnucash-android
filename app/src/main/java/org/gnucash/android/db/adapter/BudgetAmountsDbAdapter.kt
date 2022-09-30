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
import android.database.sqlite.SQLiteStatement
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema.BudgetAmountEntry
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance

/**
 * Database adapter for [BudgetAmount]s
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class BudgetAmountsDbAdapter
/**
 * Opens the database adapter with an existing database
 *
 * @param db        SQLiteDatabase object
 */
    (db: SQLiteDatabase?) : DatabaseAdapter<BudgetAmount>(
    db!!, BudgetAmountEntry.TABLE_NAME, arrayOf(
        BudgetAmountEntry.COLUMN_BUDGET_UID,
        BudgetAmountEntry.COLUMN_ACCOUNT_UID,
        BudgetAmountEntry.COLUMN_AMOUNT_NUM,
        BudgetAmountEntry.COLUMN_AMOUNT_DENOM,
        BudgetAmountEntry.COLUMN_PERIOD_NUM
    )
) {
    override fun buildModelInstance(cursor: Cursor): BudgetAmount {
        val budgetUID = cursor.getString(cursor.getColumnIndexOrThrow(BudgetAmountEntry.COLUMN_BUDGET_UID))
        val accountUID = cursor.getString(cursor.getColumnIndexOrThrow(BudgetAmountEntry.COLUMN_ACCOUNT_UID))
        val amountNum = cursor.getLong(cursor.getColumnIndexOrThrow(BudgetAmountEntry.COLUMN_AMOUNT_NUM))
        val amountDenom = cursor.getLong(cursor.getColumnIndexOrThrow(BudgetAmountEntry.COLUMN_AMOUNT_DENOM))
        val periodNum = cursor.getLong(cursor.getColumnIndexOrThrow(BudgetAmountEntry.COLUMN_PERIOD_NUM))
        val budgetAmount = BudgetAmount(budgetUID, accountUID)
        budgetAmount.setMAmount(Money(amountNum, amountDenom, getAccountCurrencyCode(accountUID)))
        budgetAmount.mPeriodNum = periodNum
        populateBaseModelAttributes(cursor, budgetAmount)
        return budgetAmount
    }

    override fun setBindings(stmt: SQLiteStatement, model: BudgetAmount): SQLiteStatement {
        stmt.clearBindings()
        stmt.bindString(1, model.mBudgetUID)
        stmt.bindString(2, model.mAccountUID)
        stmt.bindLong(3, model.mAmount!!.numerator())
        stmt.bindLong(4, model.mAmount!!.denominator())
        stmt.bindLong(5, model.mPeriodNum)
        stmt.bindString(6, model.mUID)
        return stmt
    }

    /**
     * Return budget amounts for the specific budget
     * @param budgetUID GUID of the budget
     * @return List of budget amounts
     */
    fun getBudgetAmountsForBudget(budgetUID: String?): List<BudgetAmount> {
        val cursor = fetchAllRecords(BudgetAmountEntry.COLUMN_BUDGET_UID + "=?", arrayOf(budgetUID), null)
        val budgetAmounts: MutableList<BudgetAmount> = ArrayList()
        while (cursor.moveToNext()) {
            budgetAmounts.add(buildModelInstance(cursor))
        }
        cursor.close()
        return budgetAmounts
    }

    /**
     * Delete all the budget amounts for a budget
     * @param budgetUID GUID of the budget
     * @return Number of records deleted
     */
    fun deleteBudgetAmountsForBudget(budgetUID: String): Int {
        return mDb.delete(mTableName, BudgetAmountEntry.COLUMN_BUDGET_UID + "=?", arrayOf(budgetUID))
    }

    /**
     * Returns the budgets associated with a specific account
     * @param accountUID GUID of the account
     * @return List of [BudgetAmount]s for the account
     */
    fun getBudgetAmounts(accountUID: String?): List<BudgetAmount> {
        val cursor = fetchAllRecords(BudgetAmountEntry.COLUMN_ACCOUNT_UID + " = ?", arrayOf(accountUID), null)
        val budgetAmounts: MutableList<BudgetAmount> = ArrayList()
        while (cursor.moveToNext()) {
            budgetAmounts.add(buildModelInstance(cursor))
        }
        cursor.close()
        return budgetAmounts
    }

    /**
     * Returns the sum of the budget amounts for a particular account
     * @param accountUID GUID of the account
     * @return Sum of the budget amounts
     */
    fun getBudgetAmountSum(accountUID: String?): Money {
        val budgetAmounts = getBudgetAmounts(accountUID)
        var sum = createZeroInstance(getAccountCurrencyCode(accountUID!!))
        for (budgetAmount in budgetAmounts) {
            sum = sum.add(budgetAmount.mAmount!!)
        }
        return sum
    }

    companion object {
        @JvmStatic
        val instance: BudgetAmountsDbAdapter
            get() = GnuCashApplication.budgetAmountsDbAdapter!!
    }
}