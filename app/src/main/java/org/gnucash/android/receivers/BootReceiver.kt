/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (C) 2020 Xilin Jia https://github.com/XilinJia
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
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.util.BackupManager

/**
 * Receiver which is called when the device finishes booting.
 * It schedules periodic jobs.
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GnuCashApplication.startScheduledActionExecutionService(context)
        BackupManager.schedulePeriodicBackups(context)
    }
}