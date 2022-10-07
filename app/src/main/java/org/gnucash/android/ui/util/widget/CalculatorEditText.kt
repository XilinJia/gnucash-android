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
package org.gnucash.android.ui.util.widget

import android.content.Context
import android.inputmethodservice.KeyboardView
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import androidx.annotation.XmlRes
import androidx.appcompat.widget.AppCompatEditText
import com.crashlytics.android.Crashlytics
import net.objecthunter.exp4j.Expression
import net.objecthunter.exp4j.ExpressionBuilder
import org.gnucash.android.R
import org.gnucash.android.model.Commodity
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.util.AmountParser
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParseException
import java.util.*

/**
 * A custom EditText which supports computations and uses a custom calculator keyboard.
 *
 * After the view is inflated, make sure to call [.bindListeners]
 * with the view from your layout where the calculator keyboard should be displayed.
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class CalculatorEditText : AppCompatEditText {
    /**
     * Returns the calculator keyboard instantiated by this EditText
     * @return CalculatorKeyboard
     */
    /**
     * Sets the calculator keyboard to use for this EditText
     * @param keyboard Properly intialized calculator keyobard
     */
    var calculatorKeyboard: CalculatorKeyboard? = null
    /**
     * Returns the currency used for computations
     * @return ISO 4217 currency
     */
    /**
     * Sets the commodity to use for calculations
     * The commodity determines the number of decimal places used
     * @param commodity ISO 4217 currency
     */
    var commodity = Commodity.DEFAULT_COMMODITY
    private var mContext: Context? = null
    /**
     * Returns true if the content of this view has been modified
     * @return `true` if content has changed, `false` otherwise
     */
    /**
     * Flag which is set if the contents of this view have been modified
     */
    var isInputModified = false
        private set
    private var mCalculatorKeysLayout = 0
    private var mCalculatorKeyboardView: KeyboardView? = null

    constructor(context: Context?) : super((context)!!) {
        mContext = context
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    /**
     * Overloaded constructor
     * Reads any attributes which are specified in XML and applies them
     * @param context Activity context
     * @param attrs View attributes
     */
    private fun init(context: Context, attrs: AttributeSet?) {
        mContext = context
        val a = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.CalculatorEditText,
            0, 0
        )
        try {
            mCalculatorKeysLayout =
                a.getResourceId(R.styleable.CalculatorEditText_keyboardKeysLayout, R.xml.calculator_keyboard)
        } finally {
            a.recycle()
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                isInputModified = true
            }
        })
    }

    fun bindListeners(calculatorKeyboard: CalculatorKeyboard) {
        this.calculatorKeyboard = calculatorKeyboard
        mContext = calculatorKeyboard.context
        onFocusChangeListener = OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                setSelection(text!!.length)
                calculatorKeyboard.showCustomKeyboard(v)
            } else {
                calculatorKeyboard.hideCustomKeyboard()
                evaluate()
            }
        }
        setOnClickListener(OnClickListener { v ->
            // NOTE By setting the on click listener we can show the custom keyboard again,
            // by tapping on an edit box that already had focus (but that had the keyboard hidden).
            calculatorKeyboard.showCustomKeyboard(v)
        })

        // Disable spell check (hex strings look like words to Android)
        inputType = inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        // Disable system keyboard appearing on long-press, but for some reason, this prevents the text selection from working.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            showSoftInputOnFocus = false
        } else {
            setRawInputType(InputType.TYPE_CLASS_NUMBER)
        }

        // Although this handler doesn't make sense, if removed, the standard keyboard
        // shows up in addition to the calculator one when the EditText gets a touch event.
        setOnTouchListener { _, event -> // XXX: Use dispatchTouchEvent()?
            onTouchEvent(event)
            false
        }
        (mContext as FormActivity?)!!.setOnBackListener(this.calculatorKeyboard)
    }

    /**
     * Initializes listeners on the EditText
     */
    fun bindListeners(keyboardView: KeyboardView?) {
        bindListeners(CalculatorKeyboard(mContext!!, keyboardView!!, mCalculatorKeysLayout))
    }
    /**
     * Returns the view Id of the keyboard view
     * @return Keyboard view
     */
    /**
     * Set the keyboard view used for displaying the keyboard
     * @param calculatorKeyboardView Calculator keyboard view
     */
    var calculatorKeyboardView: KeyboardView?
        get() = mCalculatorKeyboardView
        set(calculatorKeyboardView) {
            mCalculatorKeyboardView = calculatorKeyboardView
            bindListeners(calculatorKeyboardView)
        }
    /**
     * Returns the XML resource ID describing the calculator keys layout
     * @return XML resource ID
     */
    /**
     * Sets the XML resource describing the layout of the calculator keys
     * @param calculatorKeysLayout XML resource ID
     */
    @get:XmlRes
    var calculatorKeysLayout: Int
        get() = mCalculatorKeysLayout
        set(calculatorKeysLayout) {
            mCalculatorKeysLayout = calculatorKeysLayout
            bindListeners(mCalculatorKeyboardView)
        }

    /**
     * Evaluates the arithmetic expression in the EditText and sets the text property
     * @return Result of arithmetic evaluation which is same as text displayed in EditText
     */
    fun evaluate(): String {
        val amountString = cleanString
        if (amountString.isEmpty()) return amountString
        val expressionBuilder = ExpressionBuilder(amountString)
        val expression: Expression?
        try {
            expression = expressionBuilder.build()
        } catch (e: RuntimeException) {
            error = context.getString(R.string.label_error_invalid_expression)
            val msg = "Invalid expression: $amountString"
            Log.e(this.javaClass.simpleName, msg)
            Crashlytics.log(msg)
            return ""
        }
        if (expression != null && expression.validate().isValid) {
            val result = BigDecimal(expression.evaluate())
            setValue(result)
        } else {
            error = context.getString(R.string.label_error_invalid_expression)
            Log.w(VIEW_LOG_TAG, "Expression is null or invalid: $expression")
        }
        return text.toString()
    }

    /**
     * Evaluates the expression in the text and returns true if the result is valid
     * @return @{code true} if the input is valid, `false` otherwise
     */
    val isInputValid: Boolean
        get() {
            val text = evaluate()
            return text.isNotEmpty() && error == null
        }

    /**
     * Returns the amount string formatted as a decimal in Locale.US and trimmed.
     * This also converts decimal operators from other locales into a period (.)
     * @return String with the amount in the EditText or empty string if there is no input
     */
    val cleanString: String
        get() = text.toString().replace(",".toRegex(), ".")
            .trim { it <= ' ' }//catch any exceptions in the conversion e.g. if a string with only "-" is entered

    /**
     * Returns the value of the amount in the edit text or null if the field is empty.
     * Performs an evaluation of the expression first
     * @return BigDecimal value
     */
    fun getValue(): BigDecimal? {
            evaluate()
        return try { //catch any exceptions in the conversion e.g. if a string with only "-" is entered
            AmountParser.parse(text.toString())
        } catch (e: ParseException) {
            val msg = "Error parsing amount string $text from CalculatorEditText"
            Log.i(javaClass.simpleName, msg, e)
            null
        }
        }

    /**
     * Set the text to the value of `amount` formatted according to the locale.
     *
     * The number of decimal places are determined by the currency set to the view, and the
     * decimal separator is determined by the device locale. There are no thousandths separators.
     * @param amount BigDecimal amount
     */
    fun setValue(amount: BigDecimal) {
        val newAmount = amount.setScale(commodity.smallestFractionDigits(), BigDecimal.ROUND_HALF_EVEN)
        val formatter = NumberFormat.getInstance(Locale.getDefault()) as DecimalFormat
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = commodity.smallestFractionDigits()
        formatter.isGroupingUsed = false
        val resultString = formatter.format(newAmount.toDouble())
        super.setText(resultString)
        setSelection(resultString.length)
    }
}