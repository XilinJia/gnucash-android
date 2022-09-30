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
package org.gnucash.android.model

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import java.math.BigDecimal

/**
 * Budget amounts for the different accounts.
 * The [Money] amounts are absolute values
 * @author Xilin Jia <https://github.com/XilinJia> []Kotlin code created (Copyright (C) 2022)]
 * @see Budget
 */
class BudgetAmount : BaseModel, Parcelable {
    var mBudgetUID: String? = null
    var mAccountUID: String?
    /**
     * Returns the period number of this budget amount
     *
     * The period is zero-based index, and a value of -1 indicates that this budget amount is applicable to all budgeting periods
     * @return Period number
     */
    /**
     * Set the period number for this budget amount
     *
     * A value of -1 indicates that this BudgetAmount is for all periods
     * @param periodNum Zero-based period number of the budget amount
     */
    /**
     * Period number for this budget amount
     * A value of -1 indicates that this budget amount applies to all periods
     */
    var mPeriodNum: Long = 0

    /**
     * Returns the Money amount of this budget amount
     * @return Money amount
     */
    var mAmount: Money? = null
        private set

    /**
     * Create a new budget amount
     * @param budgetUID GUID of the budget
     * @param accountUID GUID of the account
     */
    constructor(budgetUID: String?, accountUID: String?) {
        mBudgetUID = budgetUID
        mAccountUID = accountUID
    }

    /**
     * Creates a new budget amount with the absolute value of `amount`
     * @param amount Money amount of the budget
     * @param accountUID GUID of the account
     */
    constructor(amount: Money, accountUID: String?) {
        mAmount = amount.abs()
        mAccountUID = accountUID
    }

    /**
     * Sets the amount for the budget
     *
     * The absolute value of the amount is used
     * @param amount Money amount
     */
    fun setMAmount(amount: Money) {
        mAmount = amount.abs()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(mUID)
        dest.writeString(mBudgetUID)
        dest.writeString(mAccountUID)
        dest.writeString(mAmount!!.toPlainString())
        dest.writeLong(mPeriodNum)
    }

    /**
     * Private constructor for creating new BudgetAmounts from a Parcel
     * @param source Parcel
     */
    private constructor(source: Parcel) {
        mUID = source.readString()
        mBudgetUID = source.readString()
        mAccountUID = source.readString()
        mAmount = Money(BigDecimal(source.readString()), Commodity.DEFAULT_COMMODITY)
        mPeriodNum = source.readLong()
    }

    companion object {
        @JvmField
        val CREATOR: Creator<BudgetAmount?> = object : Creator<BudgetAmount?> {
            override fun createFromParcel(source: Parcel): BudgetAmount {
                return BudgetAmount(source)
            }

            override fun newArray(size: Int): Array<BudgetAmount?> {
                return arrayOfNulls(size)
            }
        }
    }
}