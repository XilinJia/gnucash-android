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

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService

/**
 * Job to back up books periodically.
 *
 *
 * The backups are triggered by an alarm set in
 * [BackupManager.schedulePeriodicBackups]
 * (through [org.gnucash.android.receivers.PeriodicJobReceiver]).
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class BackupJob : JobIntentService() {
    override fun onHandleWork(intent: Intent) {
        Log.i(LOG_TAG, "Doing backup of all books.")
        BackupManager.backupAllBooks()
    }

    companion object {
        private const val LOG_TAG = "BackupJob"
        private const val JOB_ID = 1000
        fun enqueueWork(context: Context?) {
            val intent = Intent(context, BackupJob::class.java)
            enqueueWork(context!!, BackupJob::class.java, JOB_ID, intent)
        }
    }
}