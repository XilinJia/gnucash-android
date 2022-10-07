/*
 * Copyright (c) 2016 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.export

import androidx.preference.PreferenceManager
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.DbxClientV2
import org.gnucash.android.BuildConfig
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication

/**
 * Helper class for commonly used DropBox methods
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
object DropboxHelper {
    /**
     * DropBox API v2 client for making requests to DropBox
     */
    private var sDbxClient: DbxClientV2? = null

    /**
     * Retrieves the access token after DropBox OAuth authentication and saves it to preferences file
     *
     * This method should typically by called in the [Activity.onResume] method of the
     * Activity or Fragment which called [Auth.startOAuth2Authentication]
     *
     * @return Retrieved access token. Could be null if authentication failed or was canceled.
     */
    @JvmStatic
    fun retrieveAndSaveToken(): String? {
        val context = GnuCashApplication.appContext
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val keyAccessToken = context?.getString(R.string.key_dropbox_access_token)
        var accessToken = sharedPrefs.getString(keyAccessToken, null)
        if (accessToken != null) return accessToken
        accessToken = Auth.getOAuth2Token()
        sharedPrefs.edit()
            .putString(keyAccessToken, accessToken)
            .apply()
        return accessToken
    }

    /**
     * Return a DropBox client for making requests
     * @return DropBox client for API v2
     */
    @JvmStatic
    val client: DbxClientV2?
        get() {
            if (sDbxClient != null) return sDbxClient
            val context = GnuCashApplication.appContext
            var accessToken = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context?.getString(R.string.key_dropbox_access_token), null)
            if (accessToken == null) accessToken = Auth.getOAuth2Token()
            val config = DbxRequestConfig(BuildConfig.APPLICATION_ID)
            sDbxClient = DbxClientV2(config, accessToken)
            return sDbxClient
        }

    /**
     * Checks if the app holds an access token for dropbox
     * @return `true` if token exists, `false` otherwise
     */
    @JvmStatic
    fun hasToken(): Boolean {
        val context = GnuCashApplication.appContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val accessToken = prefs.getString(context?.getString(R.string.key_dropbox_access_token), null)
        return accessToken != null
    }
}