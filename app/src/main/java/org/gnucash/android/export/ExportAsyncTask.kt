/*
 * Copyright (c) 2013 - 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.export

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.AsyncTask
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.crashlytics.android.Crashlytics
import com.dropbox.core.DbxException
import com.google.android.gms.drive.Drive
import com.google.android.gms.drive.DriveId
import com.google.android.gms.drive.MetadataChangeSet
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.export.DropboxHelper.client
import org.gnucash.android.export.ExportParams.ExportTarget
import org.gnucash.android.export.Exporter.Companion.getExportFolderPath
import org.gnucash.android.export.Exporter.ExporterException
import org.gnucash.android.export.csv.CsvAccountExporter
import org.gnucash.android.export.csv.CsvTransactionsExporter
import org.gnucash.android.export.ofx.OfxExporter
import org.gnucash.android.export.qif.QifExporter
import org.gnucash.android.export.xml.GncXmlExporter
import org.gnucash.android.model.Transaction
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.settings.BackupPreferenceFragment
import org.gnucash.android.ui.transaction.TransactionsActivity
import org.gnucash.android.util.BackupManager
import org.gnucash.android.util.FileUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Asynchronous task for exporting transactions.
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class ExportAsyncTask(
    /**
     * App context
     */
    private val mContext: Context, private val mDb: SQLiteDatabase
) : AsyncTask<ExportParams, Void, Boolean>() {
    private var mProgressDialog: ProgressDialog? = null

    /**
     * Export parameters
     */
    private var mExportParams: ExportParams? = null

    // File paths generated by the exporter
    private var mExportedFiles: List<String>? = emptyList()
    private var mExporter: Exporter? = null
    @Deprecated("Deprecated in Java")
    override fun onPreExecute() {
        super.onPreExecute()
        if (mContext is Activity) {
            mProgressDialog = ProgressDialog(mContext)
            mProgressDialog!!.setTitle(R.string.title_progress_exporting_transactions)
            mProgressDialog!!.isIndeterminate = true
            mProgressDialog!!.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            mProgressDialog!!.setProgressNumberFormat(null)
            mProgressDialog!!.setProgressPercentFormat(null)
            mProgressDialog!!.show()
        }
    }

    /**
     * Generates the appropriate exported transactions file for the given parameters
     * @param params Export parameters
     * @return `true` if export was successful, `false` otherwise
     */
    @Deprecated("Deprecated in Java")
    override fun doInBackground(vararg params: ExportParams?): Boolean {
        mExportParams = params[0]
        mExporter = exporter
        mExportedFiles = try {
            mExporter!!.generateExport()
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting: " + e.message)
            Crashlytics.logException(e)
            e.printStackTrace()
            if (mContext is Activity) {
                mContext.runOnUiThread {
                    Toast.makeText(
                        mContext,
                        """
                        ${mContext.getString(R.string.toast_export_error, mExportParams!!.exportFormat.name)}
                        ${e.message}
                        """.trimIndent(),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            return false
        }
        if (mExportedFiles!!.isEmpty()) return false
        try {
            moveToTarget()
        } catch (e: ExporterException) {
            Crashlytics.log(Log.ERROR, TAG, "Error sending exported files to target: " + e.message)
            return false
        }
        return true
    }

    /**
     * Transmits the exported transactions to the designated location, either SD card or third-party application
     * Finishes the activity if the export was starting  in the context of an activity
     * @param exportSuccessful Result of background export execution
     */
    @Deprecated("Deprecated in Java")
    override fun onPostExecute(exportSuccessful: Boolean) {
        if (exportSuccessful) {
            if (mContext is Activity) reportSuccess()
            if (mExportParams!!.shouldDeleteTransactionsAfterExport()) {
                backupAndDeleteTransactions()
                refreshViews()
            }
        } else {
            if (mContext is Activity) {
                dismissProgressDialog()
                if (mExportedFiles!!.isEmpty()) {
                    Toast.makeText(
                        mContext,
                        R.string.toast_no_transactions_to_export,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        mContext,
                        mContext.getString(R.string.toast_export_error, mExportParams!!.exportFormat.name),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        dismissProgressDialog()
    }

    private fun dismissProgressDialog() {
        if (mContext is Activity) {
            if (mProgressDialog != null && mProgressDialog!!.isShowing) mProgressDialog!!.dismiss()
            mContext.finish()
        }
    }

    /**
     * Returns an exporter corresponding to the user settings.
     * @return Object of one of [QifExporter], [OfxExporter] or [GncXmlExporter], {@Link CsvAccountExporter} or {@Link CsvTransactionsExporter}
     */
    private val exporter: Exporter
        get() = when (mExportParams!!.exportFormat) {
            ExportFormat.QIF -> QifExporter(mExportParams, mDb)
            ExportFormat.OFX -> OfxExporter(mExportParams, mDb)
            ExportFormat.CSVA -> CsvAccountExporter(mExportParams!!, mDb)
            ExportFormat.CSVT -> CsvTransactionsExporter(mExportParams!!, mDb)
            ExportFormat.XML -> GncXmlExporter(mExportParams, mDb)
        }

    /**
     * Moves the generated export files to the target specified by the user
     * @throws Exporter.ExporterException if the move fails
     */
    @Throws(ExporterException::class)
    private fun moveToTarget() {
        when (mExportParams!!.exportTarget) {
            ExportTarget.SHARING -> shareFiles(mExportedFiles)
            ExportTarget.DROPBOX -> moveExportToDropbox()
            ExportTarget.GOOGLE_DRIVE -> moveExportToGoogleDrive()
            ExportTarget.OWNCLOUD -> moveExportToOwnCloud()
            ExportTarget.SD_CARD -> moveExportToSDCard()
            ExportTarget.URI -> moveExportToUri()
        }
    }

    /**
     * Move the exported files to a specified URI.
     * This URI could be a Storage Access Framework file
     * @throws Exporter.ExporterException if something failed while moving the exported file
     */
    @Throws(ExporterException::class)
    private fun moveExportToUri() {
        val exportUri = Uri.parse(mExportParams!!.exportLocation)
        if (exportUri == null) {
            Log.w(TAG, "No URI found for export destination")
            return
        }
        if (mExportedFiles!!.isNotEmpty()) {
            try {
                val outputStream = mContext.contentResolver.openOutputStream(exportUri)
                // Now we always get just one file exported (multi-currency QIFs are zipped)
                FileUtils.moveFile(mExportedFiles!![0]!!, outputStream!!)
            } catch (ex: IOException) {
                throw ExporterException(mExportParams!!, "Error when moving file to URI")
            }
        }
    }

    /**
     * Move the exported files to a GnuCash folder on Google Drive
     * @throws Exporter.ExporterException if something failed while moving the exported file
     */
    @Deprecated("Explicit Google Drive integration is deprecated, use Storage Access Framework. See {@link #moveExportToUri()}")
    @Throws(
        ExporterException::class
    )
    private fun moveExportToGoogleDrive() {
        Log.i(TAG, "Moving exported file to Google Drive")
        val googleApiClient = BackupPreferenceFragment.getGoogleApiClient(GnuCashApplication.appContext)
        googleApiClient.blockingConnect()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext)
        val folderId = sharedPreferences.getString(mContext.getString(R.string.key_google_drive_app_folder_id), "")
        val folder = DriveId.decodeFromString(folderId).asDriveFolder()
        try {
            for (exportedFilePath in mExportedFiles!!) {
                val driveContentsResult = Drive.DriveApi.newDriveContents(googleApiClient).await(1, TimeUnit.MINUTES)
                if (!driveContentsResult.status.isSuccess) {
                    throw ExporterException(
                        mExportParams!!,
                        "Error while trying to create new file contents"
                    )
                }
                val driveContents = driveContentsResult.driveContents
                val outputStream = driveContents.outputStream
                val exportedFile = File(exportedFilePath)
                val fileInputStream = FileInputStream(exportedFile)
                val buffer = ByteArray(1024)
                var count: Int
                while (fileInputStream.read(buffer).also { count = it } >= 0) {
                    outputStream.write(buffer, 0, count)
                }
                fileInputStream.close()
                outputStream.flush()
                exportedFile.delete()
                val changeSet = MetadataChangeSet.Builder()
                    .setTitle(exportedFile.name)
                    .setMimeType(mExporter!!.exportMimeType!!)
                    .build()
                // create a file on root folder
                val driveFileResult = folder.createFile(googleApiClient, changeSet, driveContents)
                    .await(1, TimeUnit.MINUTES)
                if (!driveFileResult.status.isSuccess) throw ExporterException(
                    mExportParams!!,
                    "Error creating file in Google Drive"
                )
                Log.i(TAG, "Created file with id: " + driveFileResult.driveFile.driveId)
            }
        } catch (e: IOException) {
            throw ExporterException(mExportParams!!, e)
        }
    }

    /**
     * Move the exported files (in the cache directory) to Dropbox
     */
    private fun moveExportToDropbox() {
        Log.i(TAG, "Uploading exported files to DropBox")
        val dbxClient = client
        for (exportedFilePath in mExportedFiles!!) {
            val exportedFile = File(exportedFilePath)
            try {
                val inputStream = FileInputStream(exportedFile)
                val metadata = dbxClient!!.files()
                    .uploadBuilder("/" + exportedFile.name)
                    .uploadAndFinish(inputStream)
                Log.i(TAG, "Successfully uploaded file " + metadata.name + " to DropBox")
                inputStream.close()
                exportedFile.delete() //delete file to prevent cache accumulation
            } catch (e: IOException) {
                Crashlytics.logException(e)
                Log.e(TAG, e.message!!)
            } catch (e: DbxException) {
                e.printStackTrace()
            }
        }
    }

    @Throws(ExporterException::class)
    private fun moveExportToOwnCloud() {
    }

    /**
     * Moves the exported files from the internal storage where they are generated to
     * external storage, which is accessible to the user.
     * @return The list of files moved to the SD card.
     */
    @Deprecated("Use the Storage Access Framework to save to SD card. See {@link #moveExportToUri()}")
    @Throws(
        ExporterException::class
    )
    private fun moveExportToSDCard(): List<String> {
        Log.i(TAG, "Moving exported file to external storage")
        File(getExportFolderPath(mExporter!!.mBookUID))
        val dstFiles: MutableList<String> = ArrayList()
        for (src in mExportedFiles!!) {
            val dst = getExportFolderPath(mExporter!!.mBookUID) + stripPathPart(src)
            try {
                FileUtils.moveFile(src, dst)
                dstFiles.add(dst)
            } catch (e: IOException) {
                throw ExporterException(mExportParams!!, e)
            }
        }
        return dstFiles
    }

    // "/some/path/filename.ext" -> "filename.ext"
    private fun stripPathPart(fullPathName: String): String {
        return File(fullPathName).name
    }

    /**
     * Backups of the database, saves opening balances (if necessary)
     * and deletes all non-template transactions in the database.
     */
    private fun backupAndDeleteTransactions() {
        Log.i(TAG, "Backup and deleting transactions after export")
        BackupManager.backupActiveBook() //create backup before deleting everything
        var openingBalances: List<Transaction>? = ArrayList()
        val preserveOpeningBalances = GnuCashApplication.shouldSaveOpeningBalances(false)
        val transactionsDbAdapter = TransactionsDbAdapter(mDb, SplitsDbAdapter(mDb))
        if (preserveOpeningBalances) {
            openingBalances = AccountsDbAdapter(mDb, transactionsDbAdapter).allOpeningBalanceTransactions
        }
        transactionsDbAdapter.deleteAllNonTemplateTransactions()
        if (preserveOpeningBalances) {
            transactionsDbAdapter.bulkAddRecords(openingBalances!!, DatabaseAdapter.UpdateMethod.insert)
        }
    }

    /**
     * Starts an intent chooser to allow the user to select an activity to receive
     * the exported files.
     * @param paths list of full paths of the files to send to the activity.
     */
    private fun shareFiles(paths: List<String>?) {
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
        shareIntent.type = "text/xml"
        val exportFiles = convertFilePathsToUris(paths)
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, exportFiles)
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        shareIntent.putExtra(
            Intent.EXTRA_SUBJECT, mContext.getString(
                R.string.title_export_email,
                mExportParams!!.exportFormat.name
            )
        )
        val defaultEmail = PreferenceManager.getDefaultSharedPreferences(mContext)
            .getString(mContext.getString(R.string.key_default_export_email), null)
        if (defaultEmail != null && defaultEmail.trim { it <= ' ' }.isNotEmpty()) shareIntent.putExtra(
            Intent.EXTRA_EMAIL,
            arrayOf(defaultEmail)
        )
        val formatter = SimpleDateFormat.getDateTimeInstance() as SimpleDateFormat
        val extraText = (mContext.getString(R.string.description_export_email)
                + " " + formatter.format(Date(System.currentTimeMillis())))
        shareIntent.putExtra(Intent.EXTRA_TEXT, extraText)
        if (mContext is Activity) {
            val activities = mContext.getPackageManager().queryIntentActivities(shareIntent, 0)
            if (activities.isNotEmpty()) {
                mContext.startActivity(
                    Intent.createChooser(
                        shareIntent,
                        mContext.getString(R.string.title_select_export_destination)
                    )
                )
            } else {
                Toast.makeText(
                    mContext, R.string.toast_no_compatible_apps_to_receive_export,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Convert file paths to URIs by adding the file// prefix
     *
     * e.g. /some/path/file.ext --> file:///some/path/file.ext
     * @param paths List of file paths to convert
     * @return List of file URIs
     */
    private fun convertFilePathsToUris(paths: List<String>?): ArrayList<Uri> {
        val exportFiles = ArrayList<Uri>()
        for (path in paths!!) {
            val file = File(path)
            val contentUri = FileProvider.getUriForFile(
                GnuCashApplication.appContext!!,
                GnuCashApplication.FILE_PROVIDER_AUTHORITY,
                file
            )
            exportFiles.add(contentUri)
        }
        return exportFiles
    }

    private fun reportSuccess() {
        val targetLocation: String = when (mExportParams!!.exportTarget) {
            ExportTarget.SD_CARD -> "SD card"
            ExportTarget.DROPBOX -> "DropBox -> Apps -> GnuCash"
            ExportTarget.GOOGLE_DRIVE -> "Google Drive -> " + mContext.getString(R.string.app_name)
            ExportTarget.OWNCLOUD -> if (mContext.getSharedPreferences(
                    mContext.getString(R.string.owncloud_pref),
                    Context.MODE_PRIVATE
                ).getBoolean(
                    mContext.getString(R.string.owncloud_sync), false
                )
            ) "ownCloud -> " +
                    mContext.getSharedPreferences(
                        mContext.getString(R.string.owncloud_pref),
                        Context.MODE_PRIVATE
                    ).getString(
                        mContext.getString(R.string.key_owncloud_dir), null
                    ) else "ownCloud sync not enabled"

            else -> mContext.getString(R.string.label_export_target_external_service)
        }
        Toast.makeText(
            mContext, String.format(mContext.getString(R.string.toast_exported_to), targetLocation),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun refreshViews() {
        if (mContext is AccountsActivity) {
            val fragment = mContext.currentAccountListFragment
            fragment?.refresh()
        }
        if (mContext is TransactionsActivity) {
            mContext.refresh()
        }
    }

    companion object {
        /**
         * Log tag
         */
        const val TAG = "ExportAsyncTask"

        //  by XJ
        //    private void moveExportToOwnCloud0() throws Exporter.ExporterException {
        //        Log.i(TAG, "Copying exported file to ownCloud");
        //
        //        SharedPreferences mPrefs = mContext.getSharedPreferences(mContext.getString(R.string.owncloud_pref), Context.MODE_PRIVATE);
        //
        //        Boolean mOC_sync = mPrefs.getBoolean(mContext.getString(R.string.owncloud_sync), false);
        //
        //        if (!mOC_sync) {
        //            throw new Exporter.ExporterException(mExportParams, "ownCloud not enabled.");
        //        }
        //
        //        String mOC_server = mPrefs.getString(mContext.getString(R.string.key_owncloud_server), null);
        //        String mOC_username = mPrefs.getString(mContext.getString(R.string.key_owncloud_username), null);
        //        String mOC_password = mPrefs.getString(mContext.getString(R.string.key_owncloud_password), null);
        //        String mOC_dir = mPrefs.getString(mContext.getString(R.string.key_owncloud_dir), null);
        //
        //        Uri serverUri = Uri.parse(mOC_server);
        //        OwnCloudClient mClient = OwnCloudClientFactory.createOwnCloudClient(serverUri, this.mContext, true);
        //        mClient.setCredentials(
        //                OwnCloudCredentialsFactory.newBasicCredentials(mOC_username, mOC_password)
        //        );
        //
        //        if (mOC_dir.length() != 0) {
        //            RemoteOperationResult dirResult = new CreateRemoteFolderOperation(
        //                    mOC_dir, true).execute(mClient);
        //            if (!dirResult.isSuccess()) {
        //                Log.w(TAG, "Error creating folder (it may happen if it already exists): "
        //                           + dirResult.getLogMessage());
        //            }
        //        }
        //        for (String exportedFilePath : mExportedFiles) {
        //            String remotePath = mOC_dir + FileUtils.PATH_SEPARATOR + stripPathPart(exportedFilePath);
        //            String mimeType = mExporter.getExportMimeType();
        //
        //            RemoteOperationResult result = new UploadRemoteFileOperation(
        //                    exportedFilePath, remotePath, mimeType,
        //                    getFileLastModifiedTimestamp(exportedFilePath))
        //                    .execute(mClient);
        //            if (!result.isSuccess())
        //                throw new Exporter.ExporterException(mExportParams, result.getLogMessage());
        //
        //            new File(exportedFilePath).delete();
        //        }
        //    }
        private fun getFileLastModifiedTimestamp(path: String): String {
            val timeStampLong = File(path).lastModified() / 1000
            return timeStampLong.toString()
        }
    }
}