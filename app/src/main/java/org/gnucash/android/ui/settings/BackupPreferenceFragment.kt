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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.dropbox.core.android.Auth
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.drive.Drive
import com.google.android.gms.drive.MetadataChangeSet
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.setDefaultCurrencyCode
import org.gnucash.android.db.adapter.BooksDbAdapter.Companion.instance
import org.gnucash.android.export.Exporter.Companion.getExportTime
import org.gnucash.android.export.Exporter.Companion.sanitizeFilename
import org.gnucash.android.importer.ImportAsyncTask
import org.gnucash.android.ui.settings.PreferenceActivity.Companion.activeBookSharedPreferences
import org.gnucash.android.ui.settings.dialog.OwnCloudDialogFragment.Companion.newInstance
import org.gnucash.android.util.BackupManager
import org.gnucash.android.util.BackupManager.backupActiveBook
import org.gnucash.android.util.BackupManager.getBackupList
import org.gnucash.android.util.BackupManager.getBookBackupFileUri
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment for displaying general preferences
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */

class BackupPreferenceFragment : PreferenceFragmentCompat(), Preference.OnPreferenceClickListener,
    Preference.OnPreferenceChangeListener {
    override fun onCreatePreferences(bundle: Bundle, s: String) {
        addPreferencesFromResource(R.xml.fragment_backup_preferences)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar
        actionBar!!.setHomeButtonEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setTitle(R.string.title_backup_prefs)
        mGoogleApiClient = getGoogleApiClient(
            activity
        )
    }

    override fun onResume() {
        super.onResume()
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity)

        //if we are returning from DropBox authentication, save the key which was generated
        val keyDefaultEmail = getString(R.string.key_default_export_email)
        var pref = findPreference(keyDefaultEmail)
        val defaultEmail = sharedPrefs.getString(keyDefaultEmail, null)
        if (defaultEmail != null && defaultEmail.trim { it <= ' ' }.isNotEmpty()) {
            pref.summary = defaultEmail
        }
        pref.onPreferenceChangeListener = this
        val keyDefaultExportFormat = getString(R.string.key_default_export_format)
        pref = findPreference(keyDefaultExportFormat)
        val defaultExportFormat = sharedPrefs.getString(keyDefaultExportFormat, null)
        if (defaultExportFormat != null && defaultExportFormat.trim { it <= ' ' }.isNotEmpty()) {
            pref.summary = defaultExportFormat
        }
        pref.onPreferenceChangeListener = this
        pref = findPreference(getString(R.string.key_restore_backup))
        pref.onPreferenceClickListener = this
        pref = findPreference(getString(R.string.key_create_backup))
        pref.onPreferenceClickListener = this
        pref = findPreference(getString(R.string.key_backup_location))
        pref.onPreferenceClickListener = this
        val defaultBackupLocation = getBookBackupFileUri(instance.activeBookUID)
        if (defaultBackupLocation != null) {
            pref.summary = Uri.parse(defaultBackupLocation).authority
        }
        pref = findPreference(getString(R.string.key_dropbox_sync))
        pref.onPreferenceClickListener = this
        toggleDropboxPreference(pref)
        pref = findPreference(getString(R.string.key_owncloud_sync))
        pref.onPreferenceClickListener = this
        toggleOwnCloudPreference(pref)
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val key = preference.key
        if (key == getString(R.string.key_restore_backup)) {
            restoreBackup()
        }
        if (key == getString(R.string.key_backup_location)) {
            val createIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            createIntent.type = "*/*"
            createIntent.addCategory(Intent.CATEGORY_OPENABLE)
            val bookName = instance.activeBookDisplayName
            createIntent.putExtra(
                Intent.EXTRA_TITLE,
                sanitizeFilename(bookName) + "_" + getString(R.string.label_backup_filename)
            )
            startActivityForResult(createIntent, REQUEST_BACKUP_FILE)
        }
        if (key == getString(R.string.key_dropbox_sync)) {
            toggleDropboxSync()
            toggleDropboxPreference(preference)
        }
        if (key == getString(R.string.key_owncloud_sync)) {
            toggleOwnCloudSync(preference)
            toggleOwnCloudPreference(preference)
        }
        if (key == getString(R.string.key_create_backup)) {
            val result = backupActiveBook()
            val msg = if (result) R.string.toast_backup_successful else R.string.toast_backup_failed
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
        }
        return false
    }

    /**
     * Listens for changes to the preference and sets the preference summary to the new value
     * @param preference Preference which has been changed
     * @param newValue New value for the changed preference
     * @return `true` if handled, `false` otherwise
     */
    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        preference.summary = newValue.toString()
        if (preference.key == getString(R.string.key_default_currency)) {
            setDefaultCurrencyCode(newValue.toString())
        }
        if (preference.key == getString(R.string.key_default_export_email)) {
            val emailSetting = newValue.toString()
            if (emailSetting.trim { it <= ' ' }.isEmpty()) {
                preference.setSummary(R.string.summary_default_export_email)
            }
        }
        if (preference.key == getString(R.string.key_default_export_format)) {
            val exportFormat = newValue.toString()
            if (exportFormat.trim { it <= ' ' }.isEmpty()) {
                preference.setSummary(R.string.summary_default_export_format)
            }
        }
        return true
    }

    /**
     * Toggles the checkbox of the DropBox Sync preference if a DropBox account is linked
     * @param pref DropBox Sync preference
     */
    fun toggleDropboxPreference(pref: Preference) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val accessToken = prefs.getString(getString(R.string.key_dropbox_access_token), null)
        (pref as CheckBoxPreference).isChecked = accessToken != null
    }

    /**
     * Toggles the checkbox of the ownCloud Sync preference if an ownCloud account is linked
     * @param pref ownCloud Sync preference
     */
    fun toggleOwnCloudPreference(pref: Preference) {
        val mPrefs = activity!!.getSharedPreferences(getString(R.string.owncloud_pref), Context.MODE_PRIVATE)
        (pref as CheckBoxPreference).isChecked =
            mPrefs.getBoolean(getString(R.string.owncloud_sync), false)
    }

    /**
     * Toggles the checkbox of the GoogleDrive Sync preference if a Google Drive account is linked
     * @param pref Google Drive Sync preference
     */
    fun toggleGoogleDrivePreference(pref: Preference) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val appFolderId = sharedPreferences.getString(getString(R.string.key_google_drive_app_folder_id), null)
        (pref as CheckBoxPreference).isChecked = appFolderId != null
    }

    /**
     * Toggles the authorization state of a DropBox account.
     * If a link exists, it is removed else DropBox authorization is started
     */
    private fun toggleDropboxSync() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val accessToken = prefs.getString(getString(R.string.key_dropbox_access_token), null)
        if (accessToken == null) {
            Auth.startOAuth2Authentication(activity, getString(R.string.dropbox_app_key))
        } else {
            prefs.edit().remove(getString(R.string.key_dropbox_access_token)).apply()
        }
    }

    /**
     * Toggles synchronization with Google Drive on or off
     */
    private fun toggleGoogleDriveSync() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val appFolderId = sharedPreferences.getString(getString(R.string.key_google_drive_app_folder_id), null)
        if (appFolderId != null) {
            sharedPreferences.edit().remove(getString(R.string.key_google_drive_app_folder_id))
                .commit() //commit (not apply) because we need it to be saved *now*
            mGoogleApiClient!!.disconnect()
        } else {
            mGoogleApiClient!!.connect()
        }
    }

    /**
     * Toggles synchronization with ownCloud on or off
     */
    private fun toggleOwnCloudSync(pref: Preference) {
        val mPrefs = activity!!.getSharedPreferences(getString(R.string.owncloud_pref), Context.MODE_PRIVATE)
        if (mPrefs.getBoolean(getString(R.string.owncloud_sync), false)) mPrefs.edit()
            .putBoolean(getString(R.string.owncloud_sync), false).apply() else {
            val ocDialog = newInstance(pref)
            ocDialog.show(activity!!.supportFragmentManager, "owncloud_dialog")
        }
    }

    /**
     * Opens a dialog for a user to select a backup to restore and then restores the backup
     */
    private fun restoreBackup() {
        Log.i("Settings", "Opening GnuCash XML backups for restore")
        val bookUID = instance.activeBookUID
        val defaultBackupFile = getBookBackupFileUri(bookUID)
        if (defaultBackupFile != null) {
            val builder = AlertDialog.Builder(activity!!)
                .setTitle(R.string.title_confirm_restore_backup)
                .setMessage(R.string.msg_confirm_restore_backup_into_new_book)
                .setNegativeButton(R.string.btn_cancel) { dialog, _ -> dialog.dismiss() }
                .setPositiveButton(R.string.btn_restore) { _, _ ->
                    ImportAsyncTask(activity!!).execute(
                        Uri.parse(
                            defaultBackupFile
                        )
                    )
                }
            builder.create().show()
            return  //stop here if the default backup file exists
        }

        //If no default location was set, look in the internal SD card location
        if (getBackupList(bookUID).isEmpty()) {
            val builder = AlertDialog.Builder(activity!!)
                .setTitle(R.string.title_no_backups_found)
                .setMessage(R.string.msg_no_backups_to_restore_from)
                .setNegativeButton(R.string.label_dismiss) { dialog, _ -> dialog.dismiss() }
            builder.create().show()
            return
        }
        val arrayAdapter = ArrayAdapter<String>(activity!!, android.R.layout.select_dialog_singlechoice)
        val dateFormatter = SimpleDateFormat.getDateTimeInstance()
        for (backupFile in getBackupList(bookUID)) {
            val time = getExportTime(backupFile.name)
            if (time > 0) arrayAdapter.add(dateFormatter.format(Date(time))) else  //if no timestamp was found in the filename, just use the name
                arrayAdapter.add(backupFile.name)
        }
        val restoreDialogBuilder = android.app.AlertDialog.Builder(activity)
        restoreDialogBuilder.setTitle(R.string.title_select_backup_to_restore)
        restoreDialogBuilder.setNegativeButton(
            R.string.alert_dialog_cancel
        ) { dialog, _ -> dialog.dismiss() }
        restoreDialogBuilder.setAdapter(arrayAdapter) { _, which ->
            val backupFile = getBackupList(bookUID)[which]
            ImportAsyncTask(activity!!).execute(Uri.fromFile(backupFile))
        }
        restoreDialogBuilder.create().show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_LINK_TO_DBX -> {
                val preference = findPreference(getString(R.string.key_dropbox_sync))
                if (preference != null) {
                    //if we are in a preference header fragment, this may return null
                    toggleDropboxPreference(preference)
                }
            }

            REQUEST_RESOLVE_CONNECTION -> if (resultCode == Activity.RESULT_OK) {
                mGoogleApiClient!!.connect()
                val pref = findPreference(getString(R.string.key_dropbox_sync))
                    if (pref != null) {
                        //if we are in a preference header fragment, this may return null
                        toggleDropboxPreference(pref)
                    }
            }

            REQUEST_BACKUP_FILE -> if (resultCode == Activity.RESULT_OK) {
                var backupFileUri: Uri? = null
                if (data != null) {
                    backupFileUri = data.data
                }
                val takeFlags = (data!!.flags
                        and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                activity!!.contentResolver.takePersistableUriPermission(backupFileUri!!, takeFlags)
                activeBookSharedPreferences
                    .edit()
                    .putString(BackupManager.KEY_BACKUP_FILE, backupFileUri.toString())
                    .apply()
                val pref = findPreference(getString(R.string.key_backup_location))
                pref.summary = backupFileUri.authority
            }
        }
    }

    companion object {
        /**
         * Collects references to the UI elements and binds click listeners
         */
        private const val REQUEST_LINK_TO_DBX = 0x11
        const val REQUEST_RESOLVE_CONNECTION = 0x12

        /**
         * Request code for the backup file where to save backups
         */
        private const val REQUEST_BACKUP_FILE = 0x13

        /**
         * Testing app key for DropBox API
         */
        const val DROPBOX_APP_KEY = "dhjh8ke9wf05948"

        /**
         * Testing app secret for DropBox API
         */
        const val DROPBOX_APP_SECRET = "h2t9fphj3nr4wkw"

        /**
         * String for tagging log statements
         */
        const val LOG_TAG = "BackupPrefFragment"

        /**
         * Client for Google Drive Sync
         */
        var mGoogleApiClient: GoogleApiClient? = null

        fun getGoogleApiClient(context: Context?): GoogleApiClient {
            return GoogleApiClient.Builder(context!!)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_APPFOLDER)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(object : ConnectionCallbacks {
                    override fun onConnected(bundle: Bundle?) {
                        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                        val appFolderId = sharedPreferences.getString(
                            context.getString(R.string.key_google_drive_app_folder_id),
                            null
                        )
                        if (appFolderId == null) {
                            val changeSet = MetadataChangeSet.Builder()
                                .setTitle(context.getString(R.string.app_name)).build()
                            Drive.DriveApi.getRootFolder(mGoogleApiClient)!!.createFolder(
                                mGoogleApiClient, changeSet
                            ).setResultCallback(ResultCallback { result ->
                                if (!result.status.isSuccess) {
                                    Log.e(LOG_TAG, "Error creating the application folder")
                                    return@ResultCallback
                                }
                                val folderId = result.driveFolder.driveId.toString()
                                PreferenceManager.getDefaultSharedPreferences(context)
                                    .edit().putString(
                                        context.getString(R.string.key_google_drive_app_folder_id),
                                        folderId
                                    ).commit() //commit because we need it to be saved *now*
                            })
                        }
                        Toast.makeText(context, R.string.toast_connected_to_google_drive, Toast.LENGTH_SHORT).show()
                    }

                    override fun onConnectionSuspended(i: Int) {
                        Toast.makeText(context, "Connection to Google Drive suspended!", Toast.LENGTH_LONG).show()
                    }
                })
                .addOnConnectionFailedListener { connectionResult ->
                    Log.e(PreferenceActivity::class.java.name, "Connection to Google Drive failed")
                    if (connectionResult.hasResolution() && context is Activity) {
                        try {
                            Log.e(
                                BackupPreferenceFragment::class.java.name,
                                "Trying resolution of Google API connection failure"
                            )
                            connectionResult.startResolutionForResult(context as Activity?, REQUEST_RESOLVE_CONNECTION)
                        } catch (e: SendIntentException) {
                            Log.e(BackupPreferenceFragment::class.java.name, e.message!!)
                            Toast.makeText(context, R.string.toast_unable_to_connect_to_google_drive, Toast.LENGTH_LONG)
                                .show()
                        }
                    } else {
                        if (context is Activity) GooglePlayServicesUtil.getErrorDialog(
                            connectionResult.errorCode,
                            context as Activity?,
                            0
                        ).show()
                    }
                }
                .build()
        }
    }
}