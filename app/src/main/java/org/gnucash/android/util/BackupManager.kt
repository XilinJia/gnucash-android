/* Copyright (c) 2018 Àlex Magaz Graça <alexandre.magaz@gmail.com>
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
package org.gnucash.android.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.crashlytics.android.Crashlytics
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.BooksDbAdapter.Companion.instance
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter.Companion.buildExportFilename
import org.gnucash.android.export.Exporter.ExporterException
import org.gnucash.android.export.xml.GncXmlExporter
import org.gnucash.android.receivers.PeriodicJobReceiver
import org.gnucash.android.ui.settings.PreferenceActivity
import java.io.*
import java.util.*
import java.util.zip.GZIPOutputStream

/**
 * Deals with all backup-related tasks.
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
object BackupManager {
    private const val LOG_TAG = "BackupManager"
    const val KEY_BACKUP_FILE = "book_backup_file_key"

    /**
     * Perform an automatic backup of all books in the database.
     * This method is run every time the service is executed
     */
    @JvmStatic
    fun backupAllBooks() {
        val booksDbAdapter = instance
        val bookUIDs = booksDbAdapter.allBookUIDs
        val context = GnuCashApplication.appContext
        for (bookUID in bookUIDs) {
            val backupFile = getBookBackupFileUri(bookUID)
            if (backupFile == null) {
                backupBook(bookUID)
                continue
            }
            try {
                BufferedOutputStream(context!!.contentResolver.openOutputStream(Uri.parse(backupFile))).use { bufferedOutputStream ->
                    val gzipOutputStream = GZIPOutputStream(bufferedOutputStream)
                    val writer = OutputStreamWriter(gzipOutputStream)
                    val params = ExportParams(ExportFormat.XML)
                    GncXmlExporter(params).generateExport(writer)
                    writer.close()
                }
            } catch (ex: IOException) {
                Log.e(LOG_TAG, "Auto backup failed for book $bookUID")
                ex.printStackTrace()
                Crashlytics.logException(ex)
            }
        }
    }

    /**
     * Backs up the active book to the directory [.getBackupFolderPath].
     *
     * @return `true` if backup was successful, `false` otherwise
     */
    @JvmStatic
    fun backupActiveBook(): Boolean {
        return backupBook(instance.activeBookUID)
    }

    /**
     * Backs up the book with UID `bookUID` to the directory
     * [.getBackupFolderPath].
     *
     * @param bookUID Unique ID of the book
     * @return `true` if backup was successful, `false` otherwise
     */
    @JvmStatic
    fun backupBook(bookUID: String): Boolean {
        val outputStream: OutputStream?
        return try {
            var backupFile = getBookBackupFileUri(bookUID)
            if (backupFile != null) {
                outputStream =
                    GnuCashApplication.appContext!!.contentResolver.openOutputStream(Uri.parse(backupFile))
            } else { //no Uri set by user, use default location on SD card
                backupFile = getBackupFilePath(bookUID)
                outputStream = FileOutputStream(backupFile)
            }
            val bufferedOutputStream = BufferedOutputStream(outputStream)
            val gzipOutputStream = GZIPOutputStream(bufferedOutputStream)
            val writer = OutputStreamWriter(gzipOutputStream)
            val params = ExportParams(ExportFormat.XML)
            GncXmlExporter(params).generateExport(writer)
            writer.close()
            true
        } catch (e: IOException) {
            Crashlytics.logException(e)
            Log.e("GncXmlExporter", "Error creating XML  backup", e)
            false
        } catch (e: ExporterException) {
            Crashlytics.logException(e)
            Log.e("GncXmlExporter", "Error creating XML  backup", e)
            false
        }
    }

    /**
     * Returns the full path of a file to make database backup of the specified book.
     * Backups are done in XML format and are Gzipped (with ".gnca" extension).
     * @param bookUID GUID of the book
     * @return the file path for backups of the database.
     * @see .getBackupFolderPath
     */
    private fun getBackupFilePath(bookUID: String): String {
        val book = instance.getRecord(bookUID)
        return (getBackupFolderPath(book.mUID!!)
                + buildExportFilename(ExportFormat.XML, book.mDisplayName!!))
    }

    /**
     * Returns the path to the backups folder for the book with GUID `bookUID`.
     *
     *
     * Each book has its own backup folder.
     *
     * @return Absolute path to backup folder for the book
     */
    private fun getBackupFolderPath(bookUID: String): String {
        val baseFolderPath = GnuCashApplication.appContext!!
            .getExternalFilesDir(null)!!.absolutePath
        val path = "$baseFolderPath/$bookUID/backups/"
        val file = File(path)
        if (!file.exists()) file.mkdirs()
        return path
    }

    /**
     * Return the user-set backup file URI for the book with UID `bookUID`.
     * @param bookUID Unique ID of the book
     * @return DocumentFile for book backups, or null if the user hasn't set any.
     */
    @JvmStatic
    fun getBookBackupFileUri(bookUID: String?): String? {
        val sharedPreferences = PreferenceActivity.getBookSharedPreferences(bookUID)
        return sharedPreferences.getString(KEY_BACKUP_FILE, null)
    }

    @JvmStatic
    fun getBackupList(bookUID: String): List<File> {
        val backupFiles = File(getBackupFolderPath(bookUID)).listFiles()
        if (backupFiles != null) {
            Arrays.sort(backupFiles)
            val backupFilesList = mutableListOf(*backupFiles)
            backupFilesList.reverse()
            return backupFilesList
        }
        return listOf()
    }

    fun schedulePeriodicBackups(context: Context) {
        Log.i(LOG_TAG, "Scheduling backup job")
        val intent = Intent(context, PeriodicJobReceiver::class.java)
        intent.action = PeriodicJobReceiver.ACTION_BACKUP
        val alarmIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_DAY, alarmIntent
        )
    }
}