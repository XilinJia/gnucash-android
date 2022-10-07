/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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
import androidx.preference.SwitchPreferenceCompat
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.ui.settings.dialog.DeleteAllTransactionsConfirmationDialog.Companion.newInstance

/**
 * Fragment for displaying transaction preferences
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */

class TransactionsPreferenceFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.sharedPreferencesName = BooksDbAdapter.instance.activeBookUID
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar
        actionBar!!.setHomeButtonEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setTitle(R.string.title_transaction_preferences)
    }

    override fun onCreatePreferences(bundle: Bundle, s: String) {
        addPreferencesFromResource(R.xml.fragment_transaction_preferences)
    }

    override fun onResume() {
        super.onResume()
        val sharedPreferences = preferenceManager.sharedPreferences
        val defaultTransactionType = sharedPreferences.getString(
            getString(R.string.key_default_transaction_type),
            getString(R.string.label_debit)
        )
        var pref = findPreference(getString(R.string.key_default_transaction_type))
        setLocalizedSummary(pref, defaultTransactionType)
        pref.onPreferenceChangeListener = this
        pref = findPreference(getString(R.string.key_use_double_entry))
        pref.onPreferenceChangeListener = this
        val keyCompactView = getString(R.string.key_use_compact_list)
        var switchPref = findPreference(keyCompactView) as SwitchPreferenceCompat
        switchPref.isChecked = sharedPreferences.getBoolean(keyCompactView, false)
        val keySaveBalance = getString(R.string.key_save_opening_balances)
        switchPref = findPreference(keySaveBalance) as SwitchPreferenceCompat
        switchPref.isChecked = sharedPreferences.getBoolean(keySaveBalance, false)
        val keyDoubleEntry = getString(R.string.key_use_double_entry)
        switchPref = findPreference(keyDoubleEntry) as SwitchPreferenceCompat
        switchPref.isChecked = sharedPreferences.getBoolean(keyDoubleEntry, true)
        val preference = findPreference(getString(R.string.key_delete_all_transactions))
        preference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showDeleteTransactionsDialog()
            true
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (preference.key == getString(R.string.key_use_double_entry)) {
            val useDoubleEntry = newValue as Boolean
            setImbalanceAccountsHidden(useDoubleEntry)
        } else {
            setLocalizedSummary(preference, newValue.toString())
        }
        return true
    }

    /**
     * Deletes all transactions in the system
     */
    fun showDeleteTransactionsDialog() {
        val deleteTransactionsConfirmationDialog = newInstance()
        deleteTransactionsConfirmationDialog.show(activity!!.supportFragmentManager, "transaction_settings")
    }

    /**
     * Hide all imbalance accounts when double-entry mode is disabled
     * @param useDoubleEntry flag if double entry is enabled or not
     */
    private fun setImbalanceAccountsHidden(useDoubleEntry: Boolean) {
        val isHidden = if (useDoubleEntry) "0" else "1"
        val accountsDbAdapter = AccountsDbAdapter.instance
        val commodities = accountsDbAdapter.commoditiesInUse
        for (commodity in commodities) {
            val uid = accountsDbAdapter.getImbalanceAccountUID(commodity)
            if (uid != null) {
                accountsDbAdapter.updateRecord(uid, DatabaseSchema.AccountEntry.COLUMN_HIDDEN, isHidden)
            }
        }
    }

    /**
     * Localizes the label for DEBIT/CREDIT in the settings summary
     * @param preference Preference whose summary is to be localized
     * @param value New value for the preference summary
     */
    private fun setLocalizedSummary(preference: Preference, value: String?) {
        val localizedLabel =
            if (value == "DEBIT") getString(R.string.label_debit) else activity!!.getString(R.string.label_credit)
        preference.summary = localizedLabel
    }
}