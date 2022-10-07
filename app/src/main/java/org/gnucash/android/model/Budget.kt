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
package org.gnucash.android.model

import android.util.Log
import org.gnucash.android.model.Money.Companion.sDefaultZero
import org.gnucash.android.model.Money.CurrencyMismatchException
import org.joda.time.LocalDateTime
import java.math.BigDecimal

/**
 * Budgets model
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> []Kotlin code created (Copyright (C) 2022)]
 */
class Budget : BaseModel {
    /**
     * Returns the name of the budget
     * @return name of the budget
     */
    var mName: String? = null
        private set
    /**
     * Returns the description of the budget
     * @return String description of budget
     */
    /**
     * Sets the description of the budget
     * @param description String description
     */
    var mDescription: String? = null

    /**
     * Returns the recurrence for this budget
     * @return Recurrence object for this budget
     */
    var mRecurrence: Recurrence? = null
        private set
    /**
     * Returns the number of periods covered by this budget
     * @return Number of periods
     */
    /**
     * Sets the number of periods for the budget
     * @param numberOfPeriods Number of periods as long
     */
    var mNumberOfPeriods: Long = 12 //default to 12 periods per year
    private var mBudgetAmounts: MutableList<BudgetAmount> = ArrayList()

    /**
     * Default constructor
     */
    constructor() {
        //nothing to see here, move along
    }

    /**
     * Overloaded constructor.
     * Initializes the name and amount of this budget
     * @param name String name of the budget
     */
    constructor(name: String) {
        mName = name
    }

    constructor(name: String, recurrence: Recurrence) {
        mName = name
        mRecurrence = recurrence
    }

    /**
     * Sets the name of the budget
     * @param name String name of budget
     */
    fun setMName(name: String) {
        mName = name
    }

    /**
     * Set the recurrence pattern for this budget
     * @param recurrence Recurrence object
     */
    fun setMRecurrence(recurrence: Recurrence) {
        mRecurrence = recurrence
    }

    /**
     * Return list of budget amounts associated with this budget
     * @return List of budget amounts
     */
    fun getMBudgetAmounts(): List<BudgetAmount> {
        return mBudgetAmounts
    }

    /**
     * Set the list of budget amounts
     * @param budgetAmounts List of budget amounts
     */
    fun setMBudgetAmounts(budgetAmounts: MutableList<BudgetAmount>) {
        mBudgetAmounts = budgetAmounts
        for (budgetAmount in mBudgetAmounts) {
            budgetAmount.mBudgetUID = mUID
        }
    }

    /**
     * Adds a BudgetAmount to this budget
     * @param budgetAmount Budget amount
     */
    fun addBudgetAmount(budgetAmount: BudgetAmount) {
        budgetAmount.mBudgetUID = mUID
        mBudgetAmounts.add(budgetAmount)
    }

    /**
     * Returns the budget amount for a specific account
     * @param accountUID GUID of the account
     * @return Money amount of the budget or null if the budget has no amount for the account
     */
    fun amount(accountUID: String): Money? {
        for (budgetAmount in mBudgetAmounts) {
            if (budgetAmount.mAccountUID == accountUID) return budgetAmount.mAmount
        }
        return null
    }

    /**
     * Returns the budget amount for a specific account and period
     * @param accountUID GUID of the account
     * @param periodNum Budgeting period, zero-based index
     * @return Money amount or zero if no matching [BudgetAmount] is found for the period
     */
    fun amount(accountUID: String, periodNum: Int): Money? {
        for (budgetAmount in mBudgetAmounts) {
            if (budgetAmount.mAccountUID == accountUID && (budgetAmount.mPeriodNum == periodNum.toLong() || budgetAmount.mPeriodNum == -1L)) {
                return budgetAmount.mAmount
            }
        }
        return sDefaultZero
    }

    /**
     * Returns the sum of all budget amounts in this budget
     *
     * **NOTE:** This method ignores budgets of accounts which are in different currencies
     * @return Money sum of all amounts
     */
    fun amountSum(): Money? {
        var sum: Money? =
            null //we explicitly allow this null instead of a money instance, because this method should never return null for a budget
        for (budgetAmount in mBudgetAmounts) {
            if (sum == null) {
                sum = budgetAmount.mAmount
            } else {
                try {
                    sum = sum.add(budgetAmount.mAmount!!.abs())
                } catch (ex: CurrencyMismatchException) {
                    Log.i(javaClass.simpleName, "Skip some budget amounts with different currency")
                }
            }
        }
        return sum
    }

    /**
     * Returns the timestamp of the start of current period of the budget
     * @return Start timestamp in milliseconds
     */
    fun startofCurrentPeriod(): Long {
        var localDate = LocalDateTime()
        val interval = mRecurrence!!.mMultiplier
        when (mRecurrence!!.mPeriodType) {
            PeriodType.HOUR -> localDate = localDate.millisOfDay().withMinimumValue().plusHours(interval)
            PeriodType.DAY -> localDate = localDate.millisOfDay().withMinimumValue().plusDays(interval)
            PeriodType.WEEK -> localDate = localDate.dayOfWeek().withMinimumValue().minusDays(interval)
            PeriodType.MONTH -> localDate = localDate.dayOfMonth().withMinimumValue().minusMonths(interval)
            PeriodType.YEAR -> localDate = localDate.dayOfYear().withMinimumValue().minusYears(interval)
            else -> {}
        }
        return localDate.toDate().time
    }

    /**
     * Returns the end timestamp of the current period
     * @return End timestamp in milliseconds
     */
    fun endOfCurrentPeriod(): Long {
        var localDate = LocalDateTime()
        val interval = mRecurrence!!.mMultiplier
        when (mRecurrence!!.mPeriodType) {
            PeriodType.HOUR -> localDate = localDate.millisOfDay().withMaximumValue().plusHours(interval)
            PeriodType.DAY -> localDate = localDate.millisOfDay().withMaximumValue().plusDays(interval)
            PeriodType.WEEK -> localDate = localDate.dayOfWeek().withMaximumValue().plusWeeks(interval)
            PeriodType.MONTH -> localDate = localDate.dayOfMonth().withMaximumValue().plusMonths(interval)
            PeriodType.YEAR -> localDate = localDate.dayOfYear().withMaximumValue().plusYears(interval)
            else -> {}
        }
        return localDate.toDate().time
    }

    fun startOfPeriod(periodNum: Int): Long {
        var localDate = LocalDateTime(mRecurrence!!.mPeriodStart.time)
        val interval = mRecurrence!!.mMultiplier * periodNum
        when (mRecurrence!!.mPeriodType) {
            PeriodType.HOUR -> localDate = localDate.millisOfDay().withMinimumValue().plusHours(interval)
            PeriodType.DAY -> localDate = localDate.millisOfDay().withMinimumValue().plusDays(interval)
            PeriodType.WEEK -> localDate = localDate.dayOfWeek().withMinimumValue().minusDays(interval)
            PeriodType.MONTH -> localDate = localDate.dayOfMonth().withMinimumValue().minusMonths(interval)
            PeriodType.YEAR -> localDate = localDate.dayOfYear().withMinimumValue().minusYears(interval)
            else -> {}
        }
        return localDate.toDate().time
    }

    /**
     * Returns the end timestamp of the period
     * @param periodNum Number of the period
     * @return End timestamp in milliseconds of the period
     */
    fun endOfPeriod(periodNum: Int): Long {
        var localDate = LocalDateTime()
        val interval = mRecurrence!!.mMultiplier * periodNum
        when (mRecurrence!!.mPeriodType) {
            PeriodType.HOUR -> localDate = localDate.plusHours(interval)
            PeriodType.DAY -> localDate = localDate.millisOfDay().withMaximumValue().plusDays(interval)
            PeriodType.WEEK -> localDate = localDate.dayOfWeek().withMaximumValue().plusWeeks(interval)
            PeriodType.MONTH -> localDate = localDate.dayOfMonth().withMaximumValue().plusMonths(interval)
            PeriodType.YEAR -> localDate = localDate.dayOfYear().withMaximumValue().plusYears(interval)
            else -> {}
        }
        return localDate.toDate().time
    }

    /**
     * Returns the number of accounts in this budget
     * @return Number of budgeted accounts
     */
    fun numberOfAccounts(): Int {
        val accountSet: MutableSet<String?> = HashSet()
        for (budgetAmount in mBudgetAmounts) {
            accountSet.add(budgetAmount.mAccountUID)
        }
        return accountSet.size
    }

    /**
     * Returns the list of budget amounts where only one BudgetAmount is present if the amount of the budget amount
     * is the same for all periods in the budget.
     * BudgetAmounts with different amounts per period are still return separately
     *
     *
     * This method is used during import because GnuCash desktop saves one BudgetAmount per period for the whole budgeting period.
     * While this can be easily displayed in a table form on the desktop, it is not feasible in the Android app.
     * So we display only one BudgetAmount if it covers all periods in the budgeting period
     *
     * @return List of [BudgetAmount]s
     */
    fun compactedBudgetAmounts(): List<BudgetAmount> {
        val accountAmountMap: MutableMap<String?, MutableList<BigDecimal>> = HashMap()
        for (budgetAmount in mBudgetAmounts) {
            val accountUID = budgetAmount.mAccountUID
            val amount = budgetAmount.mAmount!!.asBigDecimal()
            if (accountAmountMap.containsKey(accountUID)) {
                accountAmountMap[accountUID]!!.add(amount)
            } else {
                val amounts: MutableList<BigDecimal> = ArrayList()
                amounts.add(amount)
                accountAmountMap[accountUID] = amounts
            }
        }
        val compactBudgetAmounts: MutableList<BudgetAmount> = ArrayList()
        for ((key, amounts) in accountAmountMap) {
            val first = amounts[0]
            var allSame = true
            for (bigDecimal in amounts) {
                allSame = allSame and (bigDecimal == first)
            }
            if (allSame) {
                if (amounts.size == 1) {
                    for (bgtAmount in mBudgetAmounts) {
                        if (bgtAmount.mAccountUID == key) {
                            compactBudgetAmounts.add(bgtAmount)
                            break
                        }
                    }
                } else {
                    val bgtAmount = BudgetAmount(mUID, key)
                    bgtAmount.setMAmount(Money(first, Commodity.DEFAULT_COMMODITY))
                    bgtAmount.mPeriodNum = -1
                    compactBudgetAmounts.add(bgtAmount)
                }
            } else {
                //if not all amounts are the same, then just add them as we read them
                for (bgtAmount in mBudgetAmounts) {
                    if (bgtAmount.mAccountUID == key) {
                        compactBudgetAmounts.add(bgtAmount)
                    }
                }
            }
        }
        return compactBudgetAmounts
    }

    /**
     * Returns a list of budget amounts where each period has it's own budget amount
     *
     * Any budget amounts in the database with a period number of -1 are expanded to individual budget amounts for all periods
     *
     * This method is useful with exporting budget amounts to XML
     * @return List of expande
     */
    fun expandedBudgetAmounts(): List<BudgetAmount> {
        val amountsToAdd: MutableList<BudgetAmount> = ArrayList()
        val amountsToRemove: MutableList<BudgetAmount> = ArrayList()
        for (budgetAmount in mBudgetAmounts) {
            if (budgetAmount.mPeriodNum == -1L) {
                amountsToRemove.add(budgetAmount)
                val accountUID = budgetAmount.mAccountUID
                for (period in 0 until mNumberOfPeriods) {
                    val bgtAmount = BudgetAmount(mUID, accountUID)
                    bgtAmount.setMAmount(budgetAmount.mAmount!!)
                    bgtAmount.mPeriodNum = period
                    amountsToAdd.add(bgtAmount)
                }
            }
        }
        val expandedBudgetAmounts: MutableList<BudgetAmount> = ArrayList(mBudgetAmounts)
        for (bgtAmount in amountsToRemove) {
            expandedBudgetAmounts.remove(bgtAmount)
        }
        for (bgtAmount in amountsToAdd) {
            expandedBudgetAmounts.add(bgtAmount)
        }
        return expandedBudgetAmounts
    }
}