/*
 * Copyright (c) 2012 Ngewi Fet <ngewif@gmail.com>
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

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.gnucash.android.BuildConfig
import org.gnucash.android.R
import org.gnucash.android.ui.account.AccountsActivity.Companion.showWhatsNewDialog

/**
 * Fragment for displaying information about the application
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */

class AboutPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(bundle: Bundle, s: String) {
        addPreferencesFromResource(R.xml.fragment_about_preferences)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar
        actionBar!!.setHomeButtonEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setTitle(R.string.title_about_gnucash)
    }

    override fun onResume() {
        super.onResume()
        val pref = findPreference(getString(R.string.key_about_gnucash))
        if (BuildConfig.FLAVOR == "development") {  // TODO: always fake? by XJ
            pref.summary = pref.summary.toString() + " - Built: " + BuildConfig.BUILD_TIME
        }
        pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showWhatsNewDialog(activity!!)
            true
        }
    }
}