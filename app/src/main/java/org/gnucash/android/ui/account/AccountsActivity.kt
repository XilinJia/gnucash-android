/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.ui.account

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.SparseArray
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import butterknife.BindView
import com.crashlytics.android.Crashlytics
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayout.TabLayoutOnPageChangeListener
import com.kobakei.ratethisapp.RateThisApp
import org.gnucash.android.BuildConfig
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.importer.ImportAsyncTask
import org.gnucash.android.ui.common.BaseDrawerActivity
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.transaction.TransactionsActivity
import org.gnucash.android.ui.util.TaskDelegate
import org.gnucash.android.ui.wizard.FirstRunWizardActivity
import org.gnucash.android.util.BackupManager

/**
 * Manages actions related to accounts, displaying, exporting and creating new accounts
 * The various actions are implemented as Fragments which are then added to this activity
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class AccountsActivity : BaseDrawerActivity(), OnAccountClickedListener {
    /**
     * Map containing fragments for the different tabs
     */
    private val mFragmentPageReferenceMap = SparseArray<Refreshable>()

    /**
     * ViewPager which manages the different tabs
     */
    @JvmField
    @BindView(R.id.pager)
    var mViewPager: ViewPager? = null

    @JvmField
    @BindView(R.id.fab_create_account)
    var mFloatingActionButton: FloatingActionButton? = null

    @JvmField
    @BindView(R.id.coordinatorLayout)
    var mCoordinatorLayout: CoordinatorLayout? = null
    private var mPagerAdapter: AccountViewPagerAdapter? = null

    /**
     * Adapter for managing the sub-account and transaction fragment pages in the accounts view
     */
    private inner class AccountViewPagerAdapter(fm: FragmentManager?) : FragmentPagerAdapter(
        fm!!
    ) {
        override fun getItem(i: Int): Fragment {
            var currentFragment = mFragmentPageReferenceMap[i] as AccountsListFragment
            if (currentFragment == null) {  // TODO: this is always false? XJ
                currentFragment = when (i) {
                    INDEX_RECENT_ACCOUNTS_FRAGMENT -> AccountsListFragment.newInstance(
                        AccountsListFragment.DisplayMode.RECENT
                    )

                    INDEX_FAVORITE_ACCOUNTS_FRAGMENT -> AccountsListFragment.newInstance(
                        AccountsListFragment.DisplayMode.FAVORITES
                    )

                    INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT -> AccountsListFragment.newInstance(
                        AccountsListFragment.DisplayMode.TOP_LEVEL
                    )

                    else -> AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.TOP_LEVEL)
                }
                mFragmentPageReferenceMap.put(i, currentFragment)
            }
            return currentFragment
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            super.destroyItem(container, position, `object`)
            mFragmentPageReferenceMap.remove(position)
        }

        override fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                INDEX_RECENT_ACCOUNTS_FRAGMENT -> getString(R.string.title_recent_accounts)
                INDEX_FAVORITE_ACCOUNTS_FRAGMENT -> getString(R.string.title_favorite_accounts)
                INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT -> getString(R.string.title_all_accounts)
                else -> getString(R.string.title_all_accounts)
            }
        }

        override fun getCount(): Int {
            return DEFAULT_NUM_PAGES
        }
    }

    val currentAccountListFragment: AccountsListFragment
        get() {
            val index = mViewPager!!.currentItem
            var fragment = mFragmentPageReferenceMap[index] as Fragment
            if (fragment == null) // TODO: always false?  XJ
                fragment = mPagerAdapter!!.getItem(index)
            return fragment as AccountsListFragment
        }
    override val contentView: Int
        get() = R.layout.activity_accounts
    override val titleRes: Int
        get() = R.string.title_accounts

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        handleOpenFileIntent(intent)
        init()
        val tabLayout = findViewById<View>(R.id.tab_layout) as TabLayout
        tabLayout.addTab(tabLayout.newTab().setText(R.string.title_recent_accounts))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.title_all_accounts))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.title_favorite_accounts))
        tabLayout.tabGravity = TabLayout.GRAVITY_FILL

        //show the simple accounts list
        mPagerAdapter = AccountViewPagerAdapter(supportFragmentManager)
        mViewPager!!.adapter = mPagerAdapter
        mViewPager!!.addOnPageChangeListener(TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.setOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                mViewPager!!.currentItem = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                //nothing to see here, move along
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                //nothing to see here, move along
            }
        })
        setCurrentTab()
        mFloatingActionButton!!.setOnClickListener {
            val addAccountIntent = Intent(this@AccountsActivity, FormActivity::class.java)
            addAccountIntent.action = Intent.ACTION_INSERT_OR_EDIT
            addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name)
            startActivityForResult(addAccountIntent, REQUEST_EDIT_ACCOUNT)
        }
    }

    override fun onStart() {
        super.onStart()
        if (BuildConfig.CAN_REQUEST_RATING) {
            RateThisApp.init(rateAppConfig)
            RateThisApp.onStart(this)
            RateThisApp.showRateDialogIfNeeded(this)
        }
    }

    /**
     * Handles the case where another application has selected to open a (.gnucash or .gnca) file with this app
     * @param intent Intent containing the data to be imported
     */
    private fun handleOpenFileIntent(intent: Intent) {
        //when someone launches the app to view a (.gnucash or .gnca) file
        val data = intent.data
        if (data != null) {
            BackupManager.backupActiveBook()
            intent.data = null
            ImportAsyncTask(this).execute(data)
            removeFirstRunFlag()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setCurrentTab()
        val index = mViewPager!!.currentItem
        val fragment = mFragmentPageReferenceMap[index] as Fragment
        if (fragment != null) (fragment as Refreshable).refresh()
        handleOpenFileIntent(intent)
    }

    /**
     * Sets the current tab in the ViewPager
     */
    fun setCurrentTab() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val lastTabIndex = preferences.getInt(LAST_OPEN_TAB_INDEX, INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT)
        val index = intent.getIntExtra(EXTRA_TAB_INDEX, lastTabIndex)
        mViewPager!!.currentItem = index
    }

    /**
     * Loads default setting for currency and performs app first-run initialization.
     *
     * Also handles displaying the What's New dialog
     */
    private fun init() {
        PreferenceManager.setDefaultValues(
            this, BooksDbAdapter.instance.activeBookUID,
            MODE_PRIVATE, R.xml.fragment_transaction_preferences, true
        )
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val firstRun = prefs.getBoolean(getString(R.string.key_first_run), true)
        if (firstRun) {
            startActivity(Intent(GnuCashApplication.appContext, FirstRunWizardActivity::class.java))

            //default to using double entry and save the preference explicitly
            prefs.edit().putBoolean(getString(R.string.key_use_double_entry), true).apply()
            finish()
            return
        }
        if (hasNewFeatures()) {
            showWhatsNewDialog(this)
        }
        GnuCashApplication.startScheduledActionExecutionService(this)
        BackupManager.schedulePeriodicBackups(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.edit().putInt(LAST_OPEN_TAB_INDEX, mViewPager!!.currentItem).apply()
    }

    /**
     * Checks if the minor version has been increased and displays the What's New dialog box.
     * This is the minor version as per semantic versioning.
     * @return `true` if the minor version has been increased, `false` otherwise.
     */
    private fun hasNewFeatures(): Boolean {
        val minorVersion = resources.getString(R.string.app_minor_version)
        val currentMinor = minorVersion.toInt()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val previousMinor = prefs.getInt(getString(R.string.key_previous_minor_version), 0)
        if (currentMinor > previousMinor) {
            val editor = prefs.edit()
            editor.putInt(getString(R.string.key_previous_minor_version), currentMinor)
            editor.apply()
            return true
        }
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.global_actions, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> super.onOptionsItemSelected(item)
            else -> false
        }
    }

    override fun accountSelected(accountUID: String?) {
        val intent = Intent(this, TransactionsActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
        startActivity(intent)
    }

    companion object {
        /**
         * Request code for GnuCash account structure file to import
         */
        const val REQUEST_PICK_ACCOUNTS_FILE = 0x1

        /**
         * Request code for opening the account to edit
         */
        const val REQUEST_EDIT_ACCOUNT = 0x10

        /**
         * Logging tag
         */
        private const val LOG_TAG = "AccountsActivity"

        /**
         * Number of pages to show
         */
        private const val DEFAULT_NUM_PAGES = 3

        /**
         * Index for the recent accounts tab
         */
        const val INDEX_RECENT_ACCOUNTS_FRAGMENT = 0

        /**
         * Index of the top level (all) accounts tab
         */
        const val INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT = 1

        /**
         * Index of the favorite accounts tab
         */
        const val INDEX_FAVORITE_ACCOUNTS_FRAGMENT = 2

        /**
         * Used to save the index of the last open tab and restore the pager to that index
         */
        const val LAST_OPEN_TAB_INDEX = "last_open_tab"

        /**
         * Key for putting argument for tab into bundle arguments
         */
        const val EXTRA_TAB_INDEX = "org.gnucash.android.extra.TAB_INDEX"

        /**
         * Configuration for rating the app
         */
        @JvmField
        var rateAppConfig = RateThisApp.Config(14, 100)

        /**
         * Show dialog with new features for this version
         */
        @JvmStatic
        fun showWhatsNewDialog(context: Context): AlertDialog {
            val resources = context.resources
            val releaseTitle = StringBuilder(resources.getString(R.string.title_whats_new))
            val packageInfo: PackageInfo
            try {
                packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                releaseTitle.append(" - v").append(packageInfo.versionName)
            } catch (e: PackageManager.NameNotFoundException) {
                Crashlytics.logException(e)
                Log.e(LOG_TAG, "Error displaying 'Whats new' dialog")
            }
            return AlertDialog.Builder(context)
                .setTitle(releaseTitle.toString())
                .setMessage(R.string.whats_new)
                .setPositiveButton(R.string.label_dismiss) { dialog, _ -> dialog.dismiss() }.show()
        }

        /**
         * Displays the dialog for exporting transactions
         */
        fun openExportFragment(activity: AppCompatActivity) {
            val intent = Intent(activity, FormActivity::class.java)
            intent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.EXPORT.name)
            activity.startActivity(intent)
        }

        /**
         * Creates default accounts with the specified currency code.
         * If the currency parameter is null, then locale currency will be used if available
         *
         * @param currencyCode Currency code to assign to the imported accounts
         * @param activity Activity for providing context and displaying dialogs
         */
        @JvmStatic
        fun createDefaultAccounts(currencyCode: String?, activity: Activity?) {
            var delegate: TaskDelegate? = null
            if (currencyCode != null) {
                delegate = object : TaskDelegate {
                    override fun onTaskComplete() {
                        AccountsDbAdapter.instance.updateAllAccounts(
                            DatabaseSchema.AccountEntry.COLUMN_CURRENCY,
                            currencyCode
                        )
                        GnuCashApplication.setDefaultCurrencyCode(currencyCode)
                    }
                }
            }
            val uri = Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.raw.default_accounts)
            ImportAsyncTask(activity!!, delegate).execute(uri)
        }

        /**
         * Starts Intent chooser for selecting a GnuCash accounts file to import.
         *
         * The `activity` is responsible for the actual import of the file and can do so by calling [.importXmlFileFromIntent]<br></br>
         * The calling class should respond to the request code [AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE] in its [.onActivityResult] method
         * @param activity Activity starting the request and will also handle the response
         * @see .importXmlFileFromIntent
         */
        fun startXmlFileChooser(activity: Activity) {
            val pickIntent = Intent(Intent.ACTION_GET_CONTENT)
            pickIntent.addCategory(Intent.CATEGORY_OPENABLE)
            pickIntent.type = "*/*"
            val chooser = Intent.createChooser(pickIntent, "Select GnuCash account file") //todo internationalize string
            try {
                activity.startActivityForResult(chooser, REQUEST_PICK_ACCOUNTS_FILE)
            } catch (ex: ActivityNotFoundException) {
                Crashlytics.log("No file manager for selecting files available")
                Crashlytics.logException(ex)
                Toast.makeText(activity, R.string.toast_install_file_manager, Toast.LENGTH_LONG).show()
            }
        }

        /**
         * Overloaded method.
         * Starts chooser for selecting a GnuCash account file to import
         * @param fragment Fragment creating the chooser and which will also handle the result
         * @see .startXmlFileChooser
         */
        @JvmStatic
        fun startXmlFileChooser(fragment: Fragment) {
            val pickIntent = Intent(Intent.ACTION_GET_CONTENT)
            pickIntent.addCategory(Intent.CATEGORY_OPENABLE)
            pickIntent.type = "*/*"
            val chooser = Intent.createChooser(pickIntent, "Select GnuCash account file") //todo internationalize string
            try {
                fragment.startActivityForResult(chooser, REQUEST_PICK_ACCOUNTS_FILE)
            } catch (ex: ActivityNotFoundException) {
                Crashlytics.log("No file manager for selecting files available")
                Crashlytics.logException(ex)
                Toast.makeText(fragment.activity, R.string.toast_install_file_manager, Toast.LENGTH_LONG).show()
            }
        }

        /**
         * Reads and XML file from an intent and imports it into the database
         *
         * This method is usually called in response to [AccountsActivity.startXmlFileChooser]
         * @param context Activity context
         * @param data Intent data containing the XML uri
         * @param onFinishTask Task to be executed when import is complete
         */
        @JvmStatic
        fun importXmlFileFromIntent(context: Activity?, data: Intent, onFinishTask: TaskDelegate?) {
            BackupManager.backupActiveBook()
            ImportAsyncTask(context!!, onFinishTask).execute(data.data)
        }

        /**
         * Starts the AccountsActivity and clears the activity stack
         * @param context Application context
         */
        @JvmStatic
        fun start(context: Context) {
            val accountsActivityIntent = Intent(context, AccountsActivity::class.java)
            accountsActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            accountsActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(accountsActivityIntent)
        }

        /**
         * Removes the flag indicating that the app is being run for the first time.
         * This is called every time the app is started because the next time won't be the first time
         */
        @JvmStatic
        fun removeFirstRunFlag() {
            val context = GnuCashApplication.appContext
            val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
            editor.putBoolean(context?.getString(R.string.key_first_run), false)
            editor.commit()
        }
    }
}