/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.homescreen

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.core.content.ContextCompat
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.preference.PreferenceManager
import butterknife.BindView
import butterknife.ButterKnife
import org.gnucash.android.R
import org.gnucash.android.db.BookDbHelper.Companion.getDatabase
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.receivers.TransactionAppWidgetProvider
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.settings.PreferenceActivity
import org.gnucash.android.ui.transaction.TransactionsActivity
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter
import java.util.*

/**
 * Activity for configuration which account to display on a widget.
 * The activity is opened each time a widget is added to the homescreen
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class WidgetConfigurationActivity : Activity() {
    private var mAccountsDbAdapter: AccountsDbAdapter? = null
    private var mAppWidgetId = 0

    @JvmField
	@BindView(R.id.input_accounts_spinner)
    var mAccountsSpinner: Spinner? = null

    @JvmField
	@BindView(R.id.input_books_spinner)
    var mBooksSpinner: Spinner? = null

    @JvmField
	@BindView(R.id.input_hide_account_balance)
    var mHideAccountBalance: CheckBox? = null

    @JvmField
	@BindView(R.id.btn_save)
    var mOkButton: Button? = null

    @JvmField
	@BindView(R.id.btn_cancel)
    var mCancelButton: Button? = null
    private var mAccountsCursorAdapter: SimpleCursorAdapter? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_configuration)
        setResult(RESULT_CANCELED)
        ButterKnife.bind(this)
        val booksDbAdapter = BooksDbAdapter.instance
        val booksCursor = booksDbAdapter.fetchAllRecords()
        val currentBookUID = booksDbAdapter.activeBookUID

        //determine the position of the currently active book in the cursor
        var position = 0
        while (booksCursor.moveToNext()) {
            val bookUID = booksCursor.getString(booksCursor.getColumnIndexOrThrow(DatabaseSchema.BookEntry.COLUMN_UID))
            if (bookUID == currentBookUID) break
            ++position
        }
        val booksCursorAdapter = SimpleCursorAdapter(
            this,
            android.R.layout.simple_spinner_item,
            booksCursor,
            arrayOf(DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME),
            intArrayOf(android.R.id.text1),
            0
        )
        booksCursorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mBooksSpinner!!.adapter = booksCursorAdapter
        mBooksSpinner!!.setSelection(position)
        mAccountsDbAdapter = AccountsDbAdapter.instance
        val cursor = mAccountsDbAdapter!!.fetchAllRecordsOrderedByFullName()
        if (cursor.count <= 0) {
            Toast.makeText(this, R.string.error_no_accounts, Toast.LENGTH_LONG).show()
            finish()
        }
        mAccountsCursorAdapter = QualifiedAccountNameCursorAdapter(this, cursor)
        //without this line, the app crashes when a user tries to select an account
        mAccountsCursorAdapter?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mAccountsSpinner!!.adapter = mAccountsCursorAdapter
        val passcodeEnabled = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getBoolean(UxArgument.ENABLED_PASSCODE, false)
        mHideAccountBalance!!.isChecked = passcodeEnabled
        bindListeners()
    }

    /**
     * Sets click listeners for the buttons in the dialog
     */
    private fun bindListeners() {
        mBooksSpinner!!.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                val book = BooksDbAdapter.instance.getRecord(id)
                val db = DatabaseHelper(this@WidgetConfigurationActivity, book.mUID).writableDatabase
                mAccountsDbAdapter = AccountsDbAdapter(db)
                val cursor = mAccountsDbAdapter!!.fetchAllRecordsOrderedByFullName()
                mAccountsCursorAdapter!!.swapCursor(cursor)
                mAccountsCursorAdapter!!.notifyDataSetChanged()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                //nothing to see here, move along
            }
        }
        mOkButton!!.setOnClickListener(View.OnClickListener {
            val intent = intent
            val extras = intent.extras
            if (extras != null) {
                mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
            }
            if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                finish()
                return@OnClickListener
            }
            val bookUID = BooksDbAdapter.instance.getUID(mBooksSpinner!!.selectedItemId)
            val accountUID = mAccountsDbAdapter!!.getUID(mAccountsSpinner!!.selectedItemId)
            val hideAccountBalance = mHideAccountBalance!!.isChecked
            configureWidget(this@WidgetConfigurationActivity, mAppWidgetId, bookUID, accountUID, hideAccountBalance)
            updateWidget(this@WidgetConfigurationActivity, mAppWidgetId)
            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        })
        mCancelButton!!.setOnClickListener { finish() }
    }

    companion object {
        /**
         * Configure a given widget with the given parameters.
         * @param context The current context
         * @param appWidgetId ID of the widget to configure
         * @param bookUID UID of the book for this widget
         * @param accountUID UID of the account for this widget
         * @param hideAccountBalance `true` if the account balance should be hidden,
         * `false` otherwise
         */
        fun configureWidget(
            context: Context,
            appWidgetId: Int,
            bookUID: String?,
            accountUID: String?,
            hideAccountBalance: Boolean
        ) {
            context.getSharedPreferences("widget:$appWidgetId", MODE_PRIVATE).edit()
                .putString(UxArgument.BOOK_UID, bookUID)
                .putString(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
                .putBoolean(UxArgument.HIDE_ACCOUNT_BALANCE_IN_WIDGET, hideAccountBalance)
                .apply()
        }

        /**
         * Remove the configuration for a widget. Primarily this should be called when a widget is
         * destroyed.
         * @param context The current context
         * @param appWidgetId ID of the widget whose configuration should be removed
         */
        fun removeWidgetConfiguration(context: Context, appWidgetId: Int) {
            context.getSharedPreferences("widget:$appWidgetId", MODE_PRIVATE).edit()
                .clear()
                .apply()
        }

        /**
         * Load obsolete preferences for a widget, if they exist, and save them using the new widget
         * configuration format.
         * @param context The current context
         * @param appWidgetId ID of the widget whose configuration to load/save
         */
        private fun loadOldPreferences(context: Context, appWidgetId: Int) {
            val preferences = PreferenceActivity.activeBookSharedPreferences
            val accountUID = preferences.getString(UxArgument.SELECTED_ACCOUNT_UID + appWidgetId, null)
            if (accountUID != null) {
                val bookUID = BooksDbAdapter.instance.activeBookUID
                val hideAccountBalance =
                    preferences.getBoolean(UxArgument.HIDE_ACCOUNT_BALANCE_IN_WIDGET + appWidgetId, false)
                configureWidget(context, appWidgetId, bookUID, accountUID, hideAccountBalance)
                preferences.edit()
                    .remove(UxArgument.SELECTED_ACCOUNT_UID + appWidgetId)
                    .remove(UxArgument.HIDE_ACCOUNT_BALANCE_IN_WIDGET + appWidgetId)
                    .apply()
            }
        }

        /**
         * Updates the widget with id `appWidgetId` with information from the
         * account with record ID `accountId`
         * If the account has been deleted, then a notice is posted in the widget
         * @param appWidgetId ID of the widget to be updated
         */
        fun updateWidget(context: Context, appWidgetId: Int) {
            Log.i("WidgetConfiguration", "Updating widget: $appWidgetId")
            val appWidgetManager = AppWidgetManager.getInstance(context)
            loadOldPreferences(context, appWidgetId)
            val preferences = context.getSharedPreferences("widget:$appWidgetId", MODE_PRIVATE)
            val bookUID = preferences.getString(UxArgument.BOOK_UID, null)
            val accountUID = preferences.getString(UxArgument.SELECTED_ACCOUNT_UID, null)
            val hideAccountBalance = preferences.getBoolean(UxArgument.HIDE_ACCOUNT_BALANCE_IN_WIDGET, false)
            if (bookUID == null || accountUID == null) {
                return
            }
            val accountsDbAdapter = AccountsDbAdapter(getDatabase(bookUID))
            val account: Account = try {
                accountsDbAdapter.getRecord(accountUID)
            } catch (e: IllegalArgumentException) {
                Log.i("WidgetConfiguration", "Account not found, resetting widget $appWidgetId")
                //if account has been deleted, let the user know
                val views = RemoteViews(
                    context.packageName,
                    R.layout.widget_4x1
                )
                views.setTextViewText(R.id.account_name, context.getString(R.string.toast_account_deleted))
                views.setTextViewText(R.id.transactions_summary, "")
                //set it to simply open the app
                val pendingIntent = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, AccountsActivity::class.java), 0
                )
                views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent)
                views.setOnClickPendingIntent(R.id.btn_new_transaction, pendingIntent)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                val editor = PreferenceActivity.activeBookSharedPreferences
                    .edit() //PreferenceManager.getDefaultSharedPreferences(context).edit();
                editor.remove(UxArgument.SELECTED_ACCOUNT_UID + appWidgetId)
                editor.apply()
                return
            }
            val views = RemoteViews(
                context.packageName,
                R.layout.widget_4x1
            )
            views.setTextViewText(R.id.account_name, account.mName)
            val accountBalance = accountsDbAdapter.getAccountBalance(accountUID, -1, System.currentTimeMillis())
            if (hideAccountBalance) {
                views.setViewVisibility(R.id.transactions_summary, View.GONE)
            } else {
                views.setTextViewText(
                    R.id.transactions_summary,
                    accountBalance.formattedString(Locale.getDefault())
                )
                val color = if (accountBalance.isNegative) R.color.debit_red else R.color.credit_green
                views.setTextColor(R.id.transactions_summary, ContextCompat.getColor(context, color))
            }
            val accountViewIntent = Intent(context, TransactionsActivity::class.java)
            accountViewIntent.action = Intent.ACTION_VIEW
            accountViewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            accountViewIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            accountViewIntent.putExtra(UxArgument.BOOK_UID, bookUID)
            val accountPendingIntent = PendingIntent
                .getActivity(context, appWidgetId, accountViewIntent, 0)
            views.setOnClickPendingIntent(R.id.widget_layout, accountPendingIntent)
            if (accountsDbAdapter.isPlaceholderAccount(accountUID)) {
                views.setOnClickPendingIntent(R.id.btn_view_account, accountPendingIntent)
                views.setViewVisibility(R.id.btn_new_transaction, View.GONE)
            } else {
                val newTransactionIntent = Intent(context, FormActivity::class.java)
                newTransactionIntent.action = Intent.ACTION_INSERT_OR_EDIT
                newTransactionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                newTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
                newTransactionIntent.putExtra(UxArgument.BOOK_UID, bookUID)
                newTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
                val pendingIntent = PendingIntent
                    .getActivity(context, appWidgetId, newTransactionIntent, 0)
                views.setOnClickPendingIntent(R.id.btn_new_transaction, pendingIntent)
                views.setViewVisibility(R.id.btn_view_account, View.GONE)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Updates all widgets belonging to the application
         * @param context Application context
         */
		@JvmStatic
		fun updateAllWidgets(context: Context) {
            Log.i("WidgetConfiguration", "Updating all widgets")
            val widgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, TransactionAppWidgetProvider::class.java)
            val appWidgetIds = widgetManager.getAppWidgetIds(componentName)

            //update widgets asynchronously so as not to block method which called the update
            //inside the computation of the account balance
            Thread {
                for (widgetId in appWidgetIds) {
                    updateWidget(context, widgetId)
                }
            }.start()
        }
    }
}