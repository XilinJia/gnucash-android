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
package org.gnucash.android.ui.transaction.dialog

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import butterknife.BindView
import butterknife.ButterKnife
import com.google.android.material.textfield.TextInputLayout
import org.gnucash.android.R
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Price
import org.gnucash.android.ui.transaction.OnTransferFundsListener
import org.gnucash.android.ui.transaction.TransactionsActivity.Companion.displayBalance
import org.gnucash.android.util.AmountParser
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParseException

/**
 * Dialog fragment for handling currency conversions when inputting transactions.
 *
 * This is used whenever a multi-currency transaction is being created.
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class TransferFundsDialogFragment : DialogFragment() {
    @JvmField
    @BindView(R.id.from_currency)
    var mFromCurrencyLabel: TextView? = null

    @JvmField
    @BindView(R.id.to_currency)
    var mToCurrencyLabel: TextView? = null

    @JvmField
    @BindView(R.id.target_currency)
    var mConvertedAmountCurrencyLabel: TextView? = null

    @JvmField
    @BindView(R.id.amount_to_convert)
    var mStartAmountLabel: TextView? = null

    @JvmField
    @BindView(R.id.input_exchange_rate)
    var mExchangeRateInput: EditText? = null

    @JvmField
    @BindView(R.id.input_converted_amount)
    var mConvertedAmountInput: EditText? = null

    @JvmField
    @BindView(R.id.btn_fetch_exchange_rate)
    var mFetchExchangeRateButton: Button? = null

    @JvmField
    @BindView(R.id.radio_exchange_rate)
    var mExchangeRateRadioButton: RadioButton? = null

    @JvmField
    @BindView(R.id.radio_converted_amount)
    var mConvertedAmountRadioButton: RadioButton? = null

    @JvmField
    @BindView(R.id.label_exchange_rate_example)
    var mSampleExchangeRate: TextView? = null

    @JvmField
    @BindView(R.id.exchange_rate_text_input_layout)
    var mExchangeRateInputLayout: TextInputLayout? = null

    @JvmField
    @BindView(R.id.converted_amount_text_input_layout)
    var mConvertedAmountInputLayout: TextInputLayout? = null

    @JvmField
    @BindView(R.id.btn_save)
    var mSaveButton: Button? = null

    @JvmField
    @BindView(R.id.btn_cancel)
    var mCancelButton: Button? = null
    var mOriginAmount: Money? = null
    private var mTargetCommodity: Commodity? = null
    var mConvertedAmount: Money? = null
    var mOnTransferFundsListener: OnTransferFundsListener? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_transfer_funds, container, false)
        ButterKnife.bind(this, view)
        displayBalance(mStartAmountLabel!!, mOriginAmount!!)
        val fromCurrencyCode = mOriginAmount!!.mCommodity!!.mMnemonic
        mFromCurrencyLabel!!.text = fromCurrencyCode
        mToCurrencyLabel!!.text = mTargetCommodity!!.mMnemonic
        mConvertedAmountCurrencyLabel!!.text = mTargetCommodity!!.mMnemonic
        mSampleExchangeRate!!.text = String.format(
            getString(R.string.sample_exchange_rate),
            fromCurrencyCode,
            mTargetCommodity!!.mMnemonic
        )
        val textChangeListener = InputLayoutErrorClearer()
        val commoditiesDbAdapter = CommoditiesDbAdapter.instance
        val commodityUID = commoditiesDbAdapter.getCommodityUID(fromCurrencyCode)
        val currencyUID = mTargetCommodity!!.mUID
        val pricesDbAdapter = PricesDbAdapter.instance
        val pricePair = pricesDbAdapter.getPrice(commodityUID, currencyUID!!)
        if (pricePair.first > 0 && pricePair.second > 0) {
            // a valid price exists
            val price = Price(commodityUID, currencyUID)
            price.setMValueNum(pricePair.first)
            price.setMValueDenom(pricePair.second)
            mExchangeRateInput!!.setText(price.toString())
            val numerator = BigDecimal(pricePair.first)
            val denominator = BigDecimal(pricePair.second)
            // convertedAmount = mOriginAmount * numerator / denominator
            val convertedAmount = mOriginAmount!!.asBigDecimal().multiply(numerator)
                .divide(denominator, mTargetCommodity!!.smallestFractionDigits(), BigDecimal.ROUND_HALF_EVEN)
            val formatter = NumberFormat.getNumberInstance() as DecimalFormat
            mConvertedAmountInput!!.setText(formatter.format(convertedAmount))
        }
        mExchangeRateInput!!.addTextChangedListener(textChangeListener)
        mConvertedAmountInput!!.addTextChangedListener(textChangeListener)
        mConvertedAmountRadioButton!!.setOnCheckedChangeListener { _, isChecked ->
            mConvertedAmountInput!!.isEnabled = isChecked
            mConvertedAmountInputLayout!!.isErrorEnabled = isChecked
            mExchangeRateRadioButton!!.isChecked = !isChecked
            if (isChecked) {
                mConvertedAmountInput!!.requestFocus()
            }
        }
        mExchangeRateRadioButton!!.setOnCheckedChangeListener { _, isChecked ->
            mExchangeRateInput!!.isEnabled = isChecked
            mExchangeRateInputLayout!!.isErrorEnabled = isChecked
            mFetchExchangeRateButton!!.isEnabled = isChecked
            mConvertedAmountRadioButton!!.isChecked = !isChecked
            if (isChecked) {
                mExchangeRateInput!!.requestFocus()
            }
        }
        mFetchExchangeRateButton!!.setOnClickListener {
            //TODO: Pull the exchange rate for the currency here
        }
        mCancelButton!!.setOnClickListener { dismiss() }
        mSaveButton!!.setOnClickListener { transferFunds() }
        return view
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setTitle(R.string.title_transfer_funds)
        return dialog
    }

    /**
     * Converts the currency amount with the given exchange rate and saves the price to the db
     */
    private fun transferFunds() {
        var price: Price? = null
        val originCommodityUID = mOriginAmount!!.mCommodity!!.mUID
        val targetCommodityUID = mTargetCommodity!!.mUID
        if (mExchangeRateRadioButton!!.isChecked) {
            val rate: BigDecimal
            try {
                rate = AmountParser.parse(mExchangeRateInput!!.text.toString())
            } catch (e: ParseException) {
                mExchangeRateInputLayout!!.error = getString(R.string.error_invalid_exchange_rate)
                return
            }
            price = Price(originCommodityUID, targetCommodityUID, rate)
            mConvertedAmount = mOriginAmount!!.multiply(rate).withCurrency(mTargetCommodity!!)
        }
        if (mConvertedAmountRadioButton!!.isChecked) {
            val amount: BigDecimal
            try {
                amount = AmountParser.parse(mConvertedAmountInput!!.text.toString())
            } catch (e: ParseException) {
                mConvertedAmountInputLayout!!.error = getString(R.string.error_invalid_amount)
                return
            }
            mConvertedAmount = Money(amount, mTargetCommodity)
            price = Price(originCommodityUID, targetCommodityUID)
            // fractions cannot be exactly represented by BigDecimal.
            price.setMValueNum(mConvertedAmount!!.numerator() * mOriginAmount!!.denominator())
            price.setMValueDenom(mOriginAmount!!.numerator() * mConvertedAmount!!.denominator())
        }
        price!!.mSource = Price.SOURCE_USER
        PricesDbAdapter.instance.addRecord(price)
        if (mOnTransferFundsListener != null) mOnTransferFundsListener!!.transferComplete(mConvertedAmount)
        dismiss()
    }

    /**
     * Hides the error message from mConvertedAmountInputLayout and mExchangeRateInputLayout
     * when the user edits their content.
     */
    private inner class InputLayoutErrorClearer : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            mConvertedAmountInputLayout!!.isErrorEnabled = false
            mExchangeRateInputLayout!!.isErrorEnabled = false
        }
    }

    companion object {
        fun getInstance(
            transactionAmount: Money?, targetCurrencyCode: String?,
            transferFundsListener: OnTransferFundsListener?
        ): TransferFundsDialogFragment {
            val fragment = TransferFundsDialogFragment()
            fragment.mOriginAmount = transactionAmount
            fragment.mTargetCommodity = CommoditiesDbAdapter.instance.getCommodity(targetCurrencyCode!!)
            fragment.mOnTransferFundsListener = transferFundsListener
            return fragment
        }
    }
}