/**
 * Copyright 2013 Maarten Pennings extended by SimplicityApks
 *
 * Modified by:
 * Copyright 2015 Àlex Magaz Graça <rivaldi8></rivaldi8>@gmail.com>
 * Copyright 2015 Ngewi Fet <ngewif></ngewif>@gmail.com>
 *
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * If you use this software in a product, an acknowledgment in the product
 * documentation would be appreciated but is not required.
 */
package org.gnucash.android.ui.util.widget

import android.app.Activity
import android.content.Context
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.XmlRes
import java.text.DecimalFormatSymbols

/**
 * When an activity hosts a keyboardView, this class allows several EditText's to register for it.
 *
 * Known issues:
 * - It's not possible to select text.
 * - When in landscape, the EditText is covered by the keyboard.
 * - No i18n.
 *
 * @author Maarten Pennings, extended by SimplicityApks
 * @date 2012 December 23
 *
 * @author Àlex Magaz Graça <rivaldi8></rivaldi8>@gmail.com>
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class CalculatorKeyboard(
    /**
     * Returns the context of this keyboard
     * @return Context
     */
    val context: Context,
    /** A link to the KeyboardView that is used to render this CalculatorKeyboard.  */
    private val mKeyboardView: KeyboardView, @XmlRes keyboardLayoutResId: Int
) {
    private val hapticFeedback = false
    private val mOnKeyboardActionListener: OnKeyboardActionListener = object : OnKeyboardActionListener {
        @Deprecated("Deprecated in Java")
        override fun onKey(primaryCode: Int, keyCodes: IntArray) {
            val focusCurrent = (context as Activity).window.currentFocus!! as? CalculatorEditText ?: return

            /*
            if (focusCurrent == null || focusCurrent.getClass() != EditText.class)
                return;
            */
            val editable = focusCurrent.text
            val start = focusCurrent.selectionStart
            val end = focusCurrent.selectionEnd

            // FIXME: use replace() down
            // delete the selection, if chars are selected:
            if (end > start) editable!!.delete(start, end)
            when (primaryCode) {
                KEY_CODE_DECIMAL_SEPARATOR -> editable!!.insert(start, LOCALE_DECIMAL_SEPARATOR)
                42, 43, 45, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57 ->                     //editable.replace(start, end, Character.toString((char) primaryCode));
                    // XXX: could be android:keyOutputText attribute used instead of this?
                    editable!!.insert(start, primaryCode.toChar().toString())

                -5 -> {
                    val deleteStart = if (start > 0) start - 1 else 0
                    editable!!.delete(deleteStart, end)
                }

                1003 -> editable!!.clear()
                1001 -> focusCurrent.evaluate()
                1002 -> {
                    focusCurrent.focusSearch(View.FOCUS_DOWN).requestFocus()
                    hideCustomKeyboard()
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onPress(primaryCode: Int) {
            if (isHapticFeedbackEnabled && primaryCode != 0) mKeyboardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        @Deprecated("Deprecated in Java")
        override fun onRelease(primaryCode: Int) {}
        @Deprecated("Deprecated in Java")
        override fun onText(text: CharSequence) {}
        @Deprecated("Deprecated in Java")
        override fun swipeLeft() {}
        @Deprecated("Deprecated in Java")
        override fun swipeRight() {}
        @Deprecated("Deprecated in Java")
        override fun swipeDown() {}
        @Deprecated("Deprecated in Java")
        override fun swipeUp() {}
    }

    /**
     * Returns true if the haptic feedback is enabled.
     *
     * @return true if the haptic feedback is enabled in the system settings.
     */
    private val isHapticFeedbackEnabled: Boolean
        get() {
            val value = Settings.System.getInt(
                mKeyboardView.context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 0
            )
            return value != 0
        }

    /**
     * Create a custom keyboard, that uses the KeyboardView (with resource id <var>viewid</var>) of the <var>host</var> activity,
     * and load the keyboard layout from xml file <var>layoutid</var> (see [Keyboard] for description).
     * Note that the <var>host</var> activity must have a <var>KeyboardView</var> in its layout (typically aligned with the bottom of the activity).
     * Note that the keyboard layout xml file may include key codes for navigation; see the constants in this class for their values.
     *
     * @param context Context within with the calculator is created
     * @param keyboardView KeyboardView in the layout
     * @param keyboardLayoutResId The id of the xml file containing the keyboard layout.
     */
    init {
        val keyboard = Keyboard(context, keyboardLayoutResId)
        for (key in keyboard.keys) {
            if (key.codes[0] == KEY_CODE_DECIMAL_SEPARATOR) {
                key.label = LOCALE_DECIMAL_SEPARATOR
                break
            }
        }
        mKeyboardView.keyboard = keyboard
        mKeyboardView.isPreviewEnabled = false // NOTE Do not show the preview balloons
        mKeyboardView.setOnKeyboardActionListener(mOnKeyboardActionListener)
        // Hide the standard keyboard initially
        (context as Activity).window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }

    /** Returns whether the CalculatorKeyboard is visible.  */
    val isCustomKeyboardVisible: Boolean
        get() = mKeyboardView.visibility == View.VISIBLE

    /** Make the CalculatorKeyboard visible, and hide the system keyboard for view v.  */
    fun showCustomKeyboard(v: View?) {
        if (v != null) (context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
            v.windowToken,
            0
        )
        mKeyboardView.visibility = View.VISIBLE
        mKeyboardView.isEnabled = true
    }

    /** Make the CalculatorKeyboard invisible.  */
    fun hideCustomKeyboard() {
        mKeyboardView.visibility = View.GONE
        mKeyboardView.isEnabled = false
    }

    fun onBackPressed(): Boolean {
        return if (isCustomKeyboardVisible) {
            hideCustomKeyboard()
            true
        } else false
    }

    companion object {
        const val KEY_CODE_DECIMAL_SEPARATOR = 46
        val LOCALE_DECIMAL_SEPARATOR = DecimalFormatSymbols.getInstance().decimalSeparator.toString()
    }
}