/*
 * Copyright (c) 2018 Àlex Magaz Graça <alexandre.magaz@gmail.com>
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
package org.gnucash.android.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.gnucash.android.service.ScheduledActionService
import org.gnucash.android.util.BackupJob

/**
 * Receiver to run periodic jobs.
 *
 *
 * For now, backups and scheduled actions.
 *
 * @author Àlex Magaz Graça <alexandre.magaz></alexandre.magaz>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class PeriodicJobReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) {
            Log.w(LOG_TAG, "No action was set in the intent. Ignoring...")
            return
        }
        if (intent.action == ACTION_BACKUP) {
            BackupJob.enqueueWork(context)
        } else if (intent.action == ACTION_SCHEDULED_ACTIONS) {
            ScheduledActionService.enqueueWork(context)
        }
    }

    companion object {
        private const val LOG_TAG = "PeriodicJobReceiver"
        const val ACTION_BACKUP = "org.gnucash.android.action_backup"
        const val ACTION_SCHEDULED_ACTIONS = "org.gnucash.android.action_scheduled_actions"
    }
}