/*
 * Copyright (c) 2014-2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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
import android.preference.PreferenceManager
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.ui.common.UxArgument

/**
 * This activity used as the parent class for enabling passcode lock
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 * @see org.gnucash.android.ui.account.AccountsActivity
 *
 * @see org.gnucash.android.ui.transaction.TransactionsActivity
 */
open class PasscodeLockActivity : AppCompatActivity() {
    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val isPassEnabled = prefs.getBoolean(UxArgument.ENABLED_PASSCODE, false)
        if (isPassEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        // Only for Android Lollipop that brings a few changes to the recent apps feature
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
            GnuCashApplication.PASSCODE_SESSION_INIT_TIME = 0
        }

        // see ExportFormFragment.onPause()
        val skipPasscode = prefs.getBoolean(UxArgument.SKIP_PASSCODE_SCREEN, false)
        prefs.edit().remove(UxArgument.SKIP_PASSCODE_SCREEN).apply()
        val passCode = prefs.getString(UxArgument.PASSCODE, "")
        if (isPassEnabled && !isSessionActive && passCode!!.trim { it <= ' ' }.isNotEmpty() && !skipPasscode) {
            Log.v(TAG, "Show passcode screen")
            val intent = Intent(this, PasscodeLockScreenActivity::class.java)
                .setAction(intent.action)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(UxArgument.PASSCODE_CLASS_CALLER, this.javaClass.name)
            if (getIntent().extras != null) intent.putExtras(getIntent().extras!!)
            startActivity(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        GnuCashApplication.PASSCODE_SESSION_INIT_TIME = System.currentTimeMillis()
    }

    /**
     * @return `true` if passcode session is active, and `false` otherwise
     */
    private val isSessionActive: Boolean
        get() = (System.currentTimeMillis() - GnuCashApplication.PASSCODE_SESSION_INIT_TIME
                < GnuCashApplication.SESSION_TIMEOUT)

    companion object {
        private const val TAG = "PasscodeLockActivity"
    }
}