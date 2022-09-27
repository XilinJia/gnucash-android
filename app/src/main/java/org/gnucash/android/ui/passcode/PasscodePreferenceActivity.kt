/*
 * Copyright (c) 2014 - 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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
package org.gnucash.android.ui.passcode

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.gnucash.android.R
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.passcode.KeyboardFragment.OnPasscodeEnteredListener

/**
 * Activity for entering and confirming passcode
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class PasscodePreferenceActivity : AppCompatActivity(), OnPasscodeEnteredListener {
    private var mIsPassEnabled = false
    private var mReenter = false
    private var mPasscode: String? = null
    private var mPassTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.passcode_lockscreen)
        mPassTextView = findViewById<View>(R.id.passcode_label) as TextView
        mIsPassEnabled = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getBoolean(UxArgument.ENABLED_PASSCODE, false)
        if (mIsPassEnabled) {
            mPassTextView!!.setText(R.string.label_old_passcode)
        }
    }

    override fun onPasscodeEntered(pass: String?) {
        val passCode = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getString(UxArgument.PASSCODE, "")
        if (mIsPassEnabled) {
            if (pass == passCode) {
                mIsPassEnabled = false
                mPassTextView!!.setText(R.string.label_new_passcode)
            } else {
                Toast.makeText(this, R.string.toast_wrong_passcode, Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (mReenter) {
            if (mPasscode == pass) {
                setResult(RESULT_OK, Intent().putExtra(UxArgument.PASSCODE, pass))
                finish()
            } else {
                Toast.makeText(this, R.string.toast_invalid_passcode_confirmation, Toast.LENGTH_LONG).show()
            }
        } else {
            mPasscode = pass
            mReenter = true
            mPassTextView!!.setText(R.string.label_confirm_passcode)
        }
    }
}