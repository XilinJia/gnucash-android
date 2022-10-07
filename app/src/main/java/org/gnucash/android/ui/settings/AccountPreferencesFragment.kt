/*
 * Copyright (c) 2013 Ngewi Fet <ngewif@gmail.com>
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
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.crashlytics.android.Crashlytics
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.activeDb
import org.gnucash.android.app.GnuCashApplication.Companion.defaultCurrencyCode
import org.gnucash.android.app.GnuCashApplication.Companion.setDefaultCurrencyCode
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.export.ExportAsyncTask
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter.Companion.buildExportFilename
import org.gnucash.android.model.Money
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.account.AccountsActivity.Companion.createDefaultAccounts
import org.gnucash.android.ui.account.AccountsActivity.Companion.importXmlFileFromIntent
import org.gnucash.android.ui.account.AccountsActivity.Companion.startXmlFileChooser
import org.gnucash.android.ui.settings.dialog.DeleteAllAccountsConfirmationDialog.Companion.newInstance
import java.util.concurrent.ExecutionException

/**
 * Account settings fragment inside the Settings activity
 *
 * @author Ngewi Fet <ngewi.fet></ngewi.fet>@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */

class AccountPreferencesFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener,
    Preference.OnPreferenceClickListener {
    var mCurrencyEntries: MutableList<CharSequence> = ArrayList()
    var mCurrencyEntryValues: MutableList<CharSequence> = ArrayList()
    override fun onCreatePreferences(bundle: Bundle, s: String) {
        addPreferencesFromResource(R.xml.fragment_account_preferences)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar
        actionBar!!.setTitle(R.string.title_account_preferences)
        val cursor =
            CommoditiesDbAdapter.instance.fetchAllRecords(DatabaseSchema.CommodityEntry.COLUMN_MNEMONIC + " ASC")
        while (cursor.moveToNext()) {
            val code = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.CommodityEntry.COLUMN_MNEMONIC))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.CommodityEntry.COLUMN_FULLNAME))
            mCurrencyEntries.add("$code - $name")
            mCurrencyEntryValues.add(code)
        }
        cursor.close()
    }

    override fun onResume() {
        super.onResume()
        val defaultCurrency = defaultCurrencyCode
        val pref = findPreference(getString(R.string.key_default_currency))
        val currencyName = CommoditiesDbAdapter.instance.getCommodity(defaultCurrency!!)!!.mFullname
        pref.summary = currencyName
        pref.onPreferenceChangeListener = this
        val entries = arrayOfNulls<CharSequence>(mCurrencyEntries.size)
        val entryValues = arrayOfNulls<CharSequence>(mCurrencyEntryValues.size)
        (pref as ListPreference).entries = mCurrencyEntries.toTypedArray()
        pref.entryValues = mCurrencyEntryValues.toTypedArray()
//        (pref as ListPreference).entries = mCurrencyEntries.toArray(entries)
//        pref.entryValues = mCurrencyEntryValues.toArray(entryValues)
        var preference = findPreference(getString(R.string.key_import_accounts))
        preference.onPreferenceClickListener = this
        preference = findPreference(getString(R.string.key_export_accounts_csv))
        preference.onPreferenceClickListener = this
        preference = findPreference(getString(R.string.key_delete_all_accounts))
        preference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showDeleteAccountsDialog()
            true
        }
        preference = findPreference(getString(R.string.key_create_default_accounts))
        preference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            AlertDialog.Builder(activity)
                .setTitle(R.string.title_create_default_accounts)
                .setMessage(R.string.msg_confirm_create_default_accounts_setting)
                .setIcon(R.drawable.ic_warning_black_24dp)
                .setPositiveButton(R.string.btn_create_accounts) { _, _ ->
                    createDefaultAccounts(
                        Money.DEFAULT_CURRENCY_CODE,
                        activity
                    )
                }
                .setNegativeButton(R.string.btn_cancel) { dialogInterface, _ -> dialogInterface.dismiss() }
                .create()
                .show()
            true
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val key = preference.key
        if (key == getString(R.string.key_import_accounts)) {
            startXmlFileChooser(this)
            return true
        }
        if (key == getString(R.string.key_export_accounts_csv)) {
            selectExportFile()
            return true
        }
        return false
    }

    /**
     * Open a chooser for user to pick a file to export to
     */
    private fun selectExportFile() {
        val createIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        createIntent.setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
        val bookName = BooksDbAdapter.instance.activeBookDisplayName
        val filename = buildExportFilename(ExportFormat.CSVA, bookName)
        createIntent.type = "application/text"
        createIntent.putExtra(Intent.EXTRA_TITLE, filename)
        startActivityForResult(createIntent, REQUEST_EXPORT_FILE)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (preference.key == getString(R.string.key_default_currency)) {
            setDefaultCurrencyCode(newValue.toString())
            val fullname = CommoditiesDbAdapter.instance.getCommodity(newValue.toString())!!.mFullname
            preference.summary = fullname
            return true
        }
        return false
    }

    /**
     * Show the dialog for deleting accounts
     */
    fun showDeleteAccountsDialog() {
        val deleteConfirmationDialog = newInstance()
        deleteConfirmationDialog.show(activity!!.supportFragmentManager, "account_settings")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE -> if (resultCode == Activity.RESULT_OK && data != null) {
                importXmlFileFromIntent(activity, data, null)
            }

            REQUEST_EXPORT_FILE -> if (resultCode == Activity.RESULT_OK && data != null) {
                val exportParams = ExportParams(ExportFormat.CSVA)
                exportParams.exportTarget = ExportParams.ExportTarget.URI
                exportParams.exportLocation = data.data.toString()
                val exportTask = ExportAsyncTask(activity!!, activeDb)
                try {
                    exportTask.execute(exportParams).get()
                } catch (e: InterruptedException) {
                    Crashlytics.logException(e)
                    Toast.makeText(
                        activity, "An error occurred during the Accounts CSV export",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: ExecutionException) {
                    Crashlytics.logException(e)
                    Toast.makeText(
                        activity, "An error occurred during the Accounts CSV export",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    companion object {
        private const val REQUEST_EXPORT_FILE = 0xC5
    }
}