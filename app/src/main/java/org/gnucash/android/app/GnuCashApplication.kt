/*
 * Copyright (c) 2013 - 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.uservoice.uservoicesdk.Config
import com.uservoice.uservoicesdk.UserVoice
import io.fabric.sdk.android.Fabric
import org.gnucash.android.BuildConfig
import org.gnucash.android.R
import org.gnucash.android.db.BookDbHelper
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.adapter.*
import org.gnucash.android.db.adapter.BooksDbAdapter.NoActiveBookFoundException
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.receivers.PeriodicJobReceiver
import org.gnucash.android.service.ScheduledActionService.Companion.enqueueWork
import org.gnucash.android.ui.settings.PreferenceActivity
import java.util.*

/**
 * An [Application] subclass for retrieving static context
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class GnuCashApplication : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        Fabric.with(
            this, Crashlytics.Builder().core(
                CrashlyticsCore.Builder().disabled(!isCrashlyticsEnabled).build()
            )
                .build()
        )
        setUpUserVoice()
        val bookDbHelper = BookDbHelper(applicationContext)
        booksDbAdapter = BooksDbAdapter(bookDbHelper.writableDatabase)
        initializeDatabaseAdapters()
        setDefaultCurrencyCode(defaultCurrencyCode!!)
        StethoUtils.install(this)
    }

    /**
     * Sets up UserVoice.
     *
     *
     * Allows users to contact with us and access help topics.
     */
    private fun setUpUserVoice() {
        // Set this up once when your application launches
        val config = Config("gnucash.uservoice.com")
        config.topicId = 107400
        config.forumId = 320493
        config.putUserTrait("app_version_name", BuildConfig.VERSION_NAME)
        config.putUserTrait("app_version_code", BuildConfig.VERSION_CODE)
        config.putUserTrait("android_version", Build.VERSION.RELEASE)
        // config.identifyUser("USER_ID", "User Name", "email@example.com");
        UserVoice.init(config, this)
    }

    companion object {
        /**
         * Authority (domain) for the file provider. Also used in the app manifest
         */
        const val FILE_PROVIDER_AUTHORITY = BuildConfig.APPLICATION_ID + ".fileprovider"

        /**
         * Lifetime of passcode session
         */
        const val SESSION_TIMEOUT = (5 * 1000).toLong()

        /**
         * Init time of passcode session
         */
        var PASSCODE_SESSION_INIT_TIME = 0L

        /**
         * Returns the application context
         * @return Application [Context] object
         */
        var appContext: Context? = null
            private set
        var accountsDbAdapter: AccountsDbAdapter? = null
            private set
        var transactionDbAdapter: TransactionsDbAdapter? = null
            private set
        var splitsDbAdapter: SplitsDbAdapter? = null
            private set
        var scheduledEventDbAdapter: ScheduledActionDbAdapter? = null
            private set
        var commoditiesDbAdapter: CommoditiesDbAdapter? = null
            private set
        var pricesDbAdapter: PricesDbAdapter? = null
            private set
        var budgetDbAdapter: BudgetsDbAdapter? = null
            private set
        var budgetAmountsDbAdapter: BudgetAmountsDbAdapter? = null
            private set
        var recurrenceDbAdapter: RecurrenceDbAdapter? = null
            private set
        var booksDbAdapter: BooksDbAdapter? = null
            private set
        private var mDbHelper: DatabaseHelper? = null

        /**
         * Returns darker version of specified `color`.
         * Use for theming the status bar color when setting the color of the actionBar
         */
        fun darken(color: Int): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[2] *= 0.8f // value component
            return Color.HSVToColor(hsv)
        }

        /**
         * Initialize database adapter singletons for use in the application
         * This method should be called every time a new book is opened
         */
        fun initializeDatabaseAdapters() {
            if (mDbHelper != null) { //close if open
                mDbHelper!!.readableDatabase.close()
            }
            mDbHelper = try {
                DatabaseHelper(
                    appContext,
                    booksDbAdapter!!.activeBookUID
                )
            } catch (e: NoActiveBookFoundException) {
                booksDbAdapter!!.fixBooksDatabase()
                DatabaseHelper(
                    appContext,
                    booksDbAdapter!!.activeBookUID
                )
            }
            val mainDb: SQLiteDatabase? = try {
                mDbHelper!!.writableDatabase
            } catch (e: SQLException) {
                Crashlytics.logException(e)
                Log.e("GnuCashApplication", "Error getting database: " + e.message)
                mDbHelper!!.readableDatabase
            }
            splitsDbAdapter = SplitsDbAdapter(mainDb)
            transactionDbAdapter = TransactionsDbAdapter(mainDb, splitsDbAdapter!!)
            accountsDbAdapter = AccountsDbAdapter(mainDb!!, transactionDbAdapter!!)
            recurrenceDbAdapter = RecurrenceDbAdapter(mainDb)
            scheduledEventDbAdapter = ScheduledActionDbAdapter(mainDb, recurrenceDbAdapter!!)
            pricesDbAdapter = PricesDbAdapter(mainDb)
            commoditiesDbAdapter = CommoditiesDbAdapter(mainDb)
            budgetAmountsDbAdapter = BudgetAmountsDbAdapter(mainDb)
            budgetDbAdapter = BudgetsDbAdapter(mainDb, budgetAmountsDbAdapter!!, recurrenceDbAdapter!!)
        }

        /**
         * Returns the currently active database in the application
         * @return Currently active [SQLiteDatabase]
         */
        @JvmStatic
        val activeDb: SQLiteDatabase
            get() = mDbHelper!!.writableDatabase

        /**
         * Checks if crashlytics is enabled
         * @return `true` if crashlytics is enabled, `false` otherwise
         */
        val isCrashlyticsEnabled: Boolean
            get() = PreferenceManager.getDefaultSharedPreferences(appContext).getBoolean(
                appContext!!.getString(R.string.key_enable_crashlytics), false
            )

        /**
         * Returns `true` if double entry is enabled in the app settings, `false` otherwise.
         * If the value is not set, the default value can be specified in the parameters.
         * @return `true` if double entry is enabled, `false` otherwise
         */
        val isDoubleEntryEnabled: Boolean
            get() {
                val sharedPrefs = PreferenceActivity.activeBookSharedPreferences
                return sharedPrefs.getBoolean(appContext!!.getString(R.string.key_use_double_entry), true)
            }

        /**
         * Returns `true` if setting is enabled to save opening balances after deleting transactions,
         * `false` otherwise.
         * @param defaultValue Default value to return if double entry is not explicitly set
         * @return `true` if opening balances should be saved, `false` otherwise
         */
        @JvmStatic
        fun shouldSaveOpeningBalances(defaultValue: Boolean): Boolean {
            val sharedPrefs = PreferenceActivity.activeBookSharedPreferences
            return sharedPrefs.getBoolean(appContext!!.getString(R.string.key_save_opening_balances), defaultValue)
        }//there are some strange locales out there//start with USD as the default

        /**
         * Returns the default currency code for the application. <br></br>
         * What value is actually returned is determined in this order of priority:
         *  * User currency preference (manually set be user in the app)
         *  * Default currency for the device locale
         *  * United States Dollars
         *
         *
         * @return Default currency code string for the application
         */
        @JvmStatic
        val defaultCurrencyCode: String?
            get() {
                val locale = defaultLocale
                var currencyCode: String? = "USD" //start with USD as the default
                val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
                try { //there are some strange locales out there
                    currencyCode = Currency.getInstance(locale).currencyCode
                } catch (e: Throwable) {
                    Crashlytics.logException(e)
                    Log.e(appContext!!.getString(R.string.app_name), "" + e.message)
                } finally {
                    currencyCode = prefs.getString(appContext!!.getString(R.string.key_default_currency), currencyCode)
                }
                return currencyCode
            }

        /**
         * Sets the default currency for the application in all relevant places:
         *
         *  * Shared preferences
         *  * [Money.DEFAULT_CURRENCY_CODE]
         *  * [Commodity.DEFAULT_COMMODITY]
         *
         * @param currencyCode ISO 4217 currency code
         * @see .getDefaultCurrencyCode
         */
        @JvmStatic
        fun setDefaultCurrencyCode(currencyCode: String) {
            PreferenceManager.getDefaultSharedPreferences(appContext).edit()
                .putString(appContext!!.getString(R.string.key_default_currency), currencyCode)
                .apply()
            Money.DEFAULT_CURRENCY_CODE = currencyCode
            Commodity.DEFAULT_COMMODITY = commoditiesDbAdapter!!.getCommodity(currencyCode)!!
        }//sometimes the locale en_UK is returned which causes a crash with Currency

        //for unsupported locale es_LG
        /**
         * Returns the default locale which is used for currencies, while handling special cases for
         * locales which are not supported for currency such as en_GB
         * @return The default locale for this device
         */
        val defaultLocale: Locale
            get() {
                var locale = Locale.getDefault()
                //sometimes the locale en_UK is returned which causes a crash with Currency
                if (locale.country == "UK") {
                    locale = Locale(locale.language, "GB")
                }

                //for unsupported locale es_LG
                if (locale.country == "LG") {
                    locale = Locale(locale.language, "ES")
                }
                if (locale.country == "en") {
                    locale = Locale.US
                }
                return locale
            }

        /**
         * Starts the service for scheduled events and schedules an alarm to call the service twice daily.
         *
         * If the alarm already exists, this method does nothing. If not, the alarm will be created
         * Hence, there is no harm in calling the method repeatedly
         * @param context Application context
         */
        fun startScheduledActionExecutionService(context: Context) {
            val alarmIntent = Intent(context, PeriodicJobReceiver::class.java)
            alarmIntent.action = PeriodicJobReceiver.ACTION_SCHEDULED_ACTIONS
            var pendingIntent = PendingIntent.getBroadcast(
                context, 0, alarmIntent,
                PendingIntent.FLAG_NO_CREATE
            )
            pendingIntent = if (pendingIntent != null) //if service is already scheduled, just return
                return else PendingIntent.getBroadcast(context, 0, alarmIntent, 0)
            val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_HOUR, pendingIntent
            )
            enqueueWork(context)
        }
    }
}