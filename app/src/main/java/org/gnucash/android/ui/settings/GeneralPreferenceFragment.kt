/*
 * Copyright (c) 2014 - 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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
package org.gnucash.android.ui.settings

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.gnucash.android.R
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.passcode.PasscodeLockScreenActivity
import org.gnucash.android.ui.passcode.PasscodePreferenceActivity

/**
 * Fragment for general preferences. Currently caters to the passcode and reporting preferences
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */

class GeneralPreferenceFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener,
    Preference.OnPreferenceClickListener {
    private var mEditor: SharedPreferences.Editor? = null
    private var mCheckBoxPreference: CheckBoxPreference? = null
    override fun onCreatePreferences(bundle: Bundle, s: String) {
        addPreferencesFromResource(R.xml.fragment_general_preferences)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar
        actionBar!!.setHomeButtonEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setTitle(R.string.title_general_prefs)
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(activity, PasscodePreferenceActivity::class.java)
        mCheckBoxPreference = findPreference(getString(R.string.key_enable_passcode)) as CheckBoxPreference
        mCheckBoxPreference!!.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    startActivityForResult(intent, PASSCODE_REQUEST_CODE)
                } else {
                    val passIntent = Intent(activity, PasscodeLockScreenActivity::class.java)
                    passIntent.putExtra(UxArgument.DISABLE_PASSCODE, UxArgument.DISABLE_PASSCODE)
                    startActivityForResult(passIntent, REQUEST_DISABLE_PASSCODE)
                }
                true
            }
        findPreference(getString(R.string.key_change_passcode)).onPreferenceClickListener = this
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val key = preference.key
        if (key == getString(R.string.key_change_passcode)) {
            startActivityForResult(
                Intent(activity, PasscodePreferenceActivity::class.java),
                REQUEST_CHANGE_PASSCODE
            )
            return true
        }
        return false
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (preference.key == getString(R.string.key_enable_passcode)) {
            if (newValue as Boolean) {
                startActivityForResult(
                    Intent(activity, PasscodePreferenceActivity::class.java),
                    PASSCODE_REQUEST_CODE
                )
            } else {
                val passIntent = Intent(activity, PasscodeLockScreenActivity::class.java)
                passIntent.putExtra(UxArgument.DISABLE_PASSCODE, UxArgument.DISABLE_PASSCODE)
                startActivityForResult(passIntent, REQUEST_DISABLE_PASSCODE)
            }
        }
        if (preference.key == getString(R.string.key_use_account_color)) {
            preferenceManager.sharedPreferences
                .edit()
                .putBoolean(getString(R.string.key_use_account_color), java.lang.Boolean.valueOf(newValue.toString()))
                .commit()
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (mEditor == null) {
            mEditor = preferenceManager.sharedPreferences.edit()
        }
        when (requestCode) {
            PASSCODE_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    mEditor!!.putString(UxArgument.PASSCODE, data.getStringExtra(UxArgument.PASSCODE))
                    mEditor!!.putBoolean(UxArgument.ENABLED_PASSCODE, true)
                    Toast.makeText(activity, R.string.toast_passcode_set, Toast.LENGTH_SHORT).show()
                }
                if (resultCode == Activity.RESULT_CANCELED) {
                    mEditor!!.putBoolean(UxArgument.ENABLED_PASSCODE, false)
                    mCheckBoxPreference!!.isChecked = false
                }
            }

            REQUEST_DISABLE_PASSCODE -> {
                val flag = resultCode != Activity.RESULT_OK
                mEditor!!.putBoolean(UxArgument.ENABLED_PASSCODE, flag)
                mCheckBoxPreference!!.isChecked = flag
            }

            REQUEST_CHANGE_PASSCODE -> if (resultCode == Activity.RESULT_OK && data != null) {
                mEditor!!.putString(UxArgument.PASSCODE, data.getStringExtra(UxArgument.PASSCODE))
                mEditor!!.putBoolean(UxArgument.ENABLED_PASSCODE, true)
                Toast.makeText(activity, R.string.toast_passcode_set, Toast.LENGTH_SHORT).show()
            }
        }
        mEditor!!.commit()
    }

    companion object {
        /**
         * Request code for retrieving passcode to store
         */
        const val PASSCODE_REQUEST_CODE = 0x2

        /**
         * Request code for disabling passcode
         */
        const val REQUEST_DISABLE_PASSCODE = 0x3

        /**
         * Request code for changing passcode
         */
        const val REQUEST_CHANGE_PASSCODE = 0x4
    }
}