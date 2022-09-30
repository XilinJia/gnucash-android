/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.importer

import android.app.Activity
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import com.crashlytics.android.Crashlytics
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.importer.GncXmlImporter.parse
import org.gnucash.android.ui.util.TaskDelegate
import org.gnucash.android.util.BookUtils

/**
 * Imports a GnuCash (desktop) account file and displays a progress dialog.
 * The AccountsActivity is opened when importing is done.
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class ImportAsyncTask : AsyncTask<Uri, Void, Boolean> {
    private val mContext: Activity
    private var mDelegate: TaskDelegate? = null
    private var mProgressDialog: ProgressDialog? = null
    private var mImportedBookUID: String? = null

    constructor(context: Activity) {
        mContext = context
    }

    constructor(context: Activity, delegate: TaskDelegate?) {
        mContext = context
        mDelegate = delegate
    }

    override fun onPreExecute() {
        super.onPreExecute()
        mProgressDialog = ProgressDialog(mContext)
        mProgressDialog!!.setTitle(R.string.title_progress_importing_accounts)
        mProgressDialog!!.isIndeterminate = true
        mProgressDialog!!.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        mProgressDialog!!.show()

        //these methods must be called after progressDialog.show()
        mProgressDialog!!.setProgressNumberFormat(null)
        mProgressDialog!!.setProgressPercentFormat(null)
    }

    @Deprecated("Deprecated in Java")
    override fun doInBackground(vararg uris: Uri): Boolean {
        mImportedBookUID = try {
            val accountInputStream = mContext.contentResolver.openInputStream(uris[0])
            parse(accountInputStream)
        } catch (exception: Exception) {
            Log.e(ImportAsyncTask::class.java.name, "" + exception.message)
            Crashlytics.log("Could not open: " + uris[0].toString())
            Crashlytics.logException(exception)
            exception.printStackTrace()
            val err_msg = exception.localizedMessage
            Crashlytics.log(err_msg)
            mContext.runOnUiThread {
                Toast.makeText(
                    mContext,
                    """
                    ${mContext.getString(R.string.toast_error_importing_accounts)}
                    $err_msg
                    """.trimIndent(),
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        }
        val cursor = mContext.contentResolver.query(uris[0], null, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val displayName = cursor.getString(nameIndex)
            val contentValues = ContentValues()
            contentValues.put(DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME, displayName)
            contentValues.put(DatabaseSchema.BookEntry.COLUMN_SOURCE_URI, uris[0].toString())
            BooksDbAdapter.instance.updateRecord(mImportedBookUID!!, contentValues)
            cursor.close()
        }

        //set the preferences to their default values
        mContext.getSharedPreferences(mImportedBookUID, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(mContext.getString(R.string.key_use_double_entry), true)
            .apply()
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onPostExecute(importSuccess: Boolean) {
        try {
            if (mProgressDialog != null && mProgressDialog!!.isShowing) mProgressDialog!!.dismiss()
        } catch (ex: IllegalArgumentException) {
            //TODO: This is a hack to catch "View not attached to window" exceptions
            //FIXME by moving the creation and display of the progress dialog to the Fragment
        } finally {
            mProgressDialog = null
        }
        val message =
            if (importSuccess) R.string.toast_success_importing_accounts else R.string.toast_error_importing_accounts
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show()
        if (mImportedBookUID != null) BookUtils.loadBook(mImportedBookUID!!)
        if (mDelegate != null) mDelegate!!.onTaskComplete()
    }
}