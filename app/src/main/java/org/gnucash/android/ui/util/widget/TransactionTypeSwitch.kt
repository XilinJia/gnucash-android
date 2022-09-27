/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.util.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.CompoundButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import org.gnucash.android.R
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Transaction.Companion.shouldDecreaseBalance
import org.gnucash.android.model.TransactionType

/**
 * A special type of [android.widget.ToggleButton] which displays the appropriate CREDIT/DEBIT labels for the
 * different account types.
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class TransactionTypeSwitch : SwitchCompat {
    private var mAccountType = AccountType.EXPENSE
    var mOnCheckedChangeListeners: MutableList<OnCheckedChangeListener> = ArrayList()

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context!!, attrs, defStyle
    ) {
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {}
    constructor(context: Context?) : super(context!!) {}

    /**
     * Set a checked change listener to monitor the amount view and currency views and update the display (color & balance accordingly)
     * @param amoutView Amount string [android.widget.EditText]
     * @param currencyTextView Currency symbol text view
     */
    fun setAmountFormattingListener(amoutView: CalculatorEditText, currencyTextView: TextView) {
        setOnCheckedChangeListener(OnTypeChangedListener(amoutView, currencyTextView))
    }

    /**
     * Add listeners to be notified when the checked status changes
     * @param checkedChangeListener Checked change listener
     */
    fun addOnCheckedChangeListener(checkedChangeListener: OnCheckedChangeListener) {
        mOnCheckedChangeListeners.add(checkedChangeListener)
    }

    /**
     * Toggles the button checked based on the movement caused by the transaction type for the specified account
     * @param transactionType [org.gnucash.android.model.TransactionType] of the split
     */
    fun setChecked(transactionType: TransactionType?) {
        isChecked = shouldDecreaseBalance(mAccountType, transactionType!!)
    }

    /**
     * Returns the account type associated with this button
     * @return Type of account
     */
    var accountType: AccountType
        get() = mAccountType
        set(accountType) {
            mAccountType = accountType
            val context = context.applicationContext
            when (mAccountType) {
                AccountType.CASH -> {
                    textOn = context.getString(R.string.label_spend)
                    textOff = context.getString(R.string.label_receive)
                }

                AccountType.BANK -> {
                    textOn = context.getString(R.string.label_withdrawal)
                    textOff = context.getString(R.string.label_deposit)
                }

                AccountType.CREDIT -> {
                    textOn = context.getString(R.string.label_payment)
                    textOff = context.getString(R.string.label_charge)
                }

                AccountType.ASSET, AccountType.EQUITY, AccountType.LIABILITY -> {
                    textOn = context.getString(R.string.label_decrease)
                    textOff = context.getString(R.string.label_increase)
                }

                AccountType.INCOME -> {
                    textOn = context.getString(R.string.label_charge)
                    textOff = context.getString(R.string.label_income)
                }

                AccountType.EXPENSE -> {
                    textOn = context.getString(R.string.label_rebate)
                    textOff = context.getString(R.string.label_expense)
                }

                AccountType.PAYABLE -> {
                    textOn = context.getString(R.string.label_payment)
                    textOff = context.getString(R.string.label_bill)
                }

                AccountType.RECEIVABLE -> {
                    textOn = context.getString(R.string.label_payment)
                    textOff = context.getString(R.string.label_invoice)
                }

                AccountType.STOCK, AccountType.MUTUAL -> {
                    textOn = context.getString(R.string.label_buy)
                    textOff = context.getString(R.string.label_sell)
                }

                AccountType.CURRENCY, AccountType.ROOT -> {
                    textOn = context.getString(R.string.label_debit)
                    textOff = context.getString(R.string.label_credit)
                }

                else -> {
                    textOn = context.getString(R.string.label_debit)
                    textOff = context.getString(R.string.label_credit)
                }
            }
            text = if (isChecked) textOn else textOff
            invalidate()
        }
    val transactionType: TransactionType
        get() = if (mAccountType.hasDebitNormalBalance()) {
            if (isChecked) TransactionType.CREDIT else TransactionType.DEBIT
        } else {
            if (isChecked) TransactionType.DEBIT else TransactionType.CREDIT
        }

    private inner class OnTypeChangedListener
    /**
     * Constructor with the amount view
     * @param amountEditText EditText displaying the amount value
     * @param currencyTextView Currency symbol text view
     */(private val mAmountEditText: CalculatorEditText, private val mCurrencyTextView: TextView) :
        OnCheckedChangeListener {
        override fun onCheckedChanged(compoundButton: CompoundButton, isChecked: Boolean) {
            text = if (isChecked) textOn else textOff
            if (isChecked) {
                val red = ContextCompat.getColor(context, R.color.debit_red)
                this@TransactionTypeSwitch.setTextColor(red)
                mAmountEditText.setTextColor(red)
                mCurrencyTextView.setTextColor(red)
            } else {
                val green = ContextCompat.getColor(context, R.color.credit_green)
                this@TransactionTypeSwitch.setTextColor(green)
                mAmountEditText.setTextColor(green)
                mCurrencyTextView.setTextColor(green)
            }
            val amount = mAmountEditText.getValue()
            if (amount != null) {
                if (isChecked && amount.signum() > 0 || !isChecked && amount.signum() < 0) { //credit but amount is -ve
                    mAmountEditText.setValue(amount.negate())
                }
            }
            for (listener in mOnCheckedChangeListeners) {
                listener.onCheckedChanged(compoundButton, isChecked)
            }
        }
    }
}