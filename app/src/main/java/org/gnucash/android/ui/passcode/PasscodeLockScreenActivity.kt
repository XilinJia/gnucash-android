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
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.passcode.KeyboardFragment.OnPasscodeEnteredListener

/**
 * Activity for displaying and managing the passcode lock screen.
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class PasscodeLockScreenActivity : AppCompatActivity(), OnPasscodeEnteredListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.passcode_lockscreen)
    }

    override fun onPasscodeEntered(pass: String?) {
        val passcode = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getString(UxArgument.PASSCODE, "")
        Log.d(TAG, "Passcode: $passcode")
        if (pass == passcode) {
            if (UxArgument.DISABLE_PASSCODE == intent.getStringExtra(UxArgument.DISABLE_PASSCODE)) {
                setResult(RESULT_OK)
                finish()
                return
            }
            GnuCashApplication.PASSCODE_SESSION_INIT_TIME = System.currentTimeMillis()
            startActivity(
                Intent()
                    .setClassName(this, intent.getStringExtra(UxArgument.PASSCODE_CLASS_CALLER)!!)
                    .setAction(intent.action)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtras(intent.extras!!)
            )
        } else {
            Toast.makeText(this, R.string.toast_wrong_passcode, Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        if (UxArgument.DISABLE_PASSCODE == intent.getStringExtra(UxArgument.DISABLE_PASSCODE)) {
            finish()
            return
        }
        GnuCashApplication.PASSCODE_SESSION_INIT_TIME = System.currentTimeMillis() - GnuCashApplication.SESSION_TIMEOUT
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        private const val TAG = "PassLockScreenActivity"
    }
}