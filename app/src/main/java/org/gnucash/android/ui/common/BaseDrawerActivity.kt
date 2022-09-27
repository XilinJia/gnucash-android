/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.common

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import butterknife.BindView
import butterknife.ButterKnife
import com.google.android.material.navigation.NavigationView
import com.uservoice.uservoicesdk.UserVoice
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.BooksDbAdapter.Companion.instance
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.passcode.PasscodeLockActivity
import org.gnucash.android.ui.report.ReportsActivity
import org.gnucash.android.ui.settings.PreferenceActivity
import org.gnucash.android.ui.transaction.ScheduledActionsActivity
import org.gnucash.android.util.BookUtils

/**
 * Base activity implementing the navigation drawer, to be extended by all activities requiring one.
 *
 *
 * Each activity inheriting from this class has an indeterminate progress bar at the top,
 * (above the action bar) which can be used to display busy operations. See [.getProgressBar]
 *
 *
 *
 * Sub-classes should simply provide their layout using [.getContentView] and then annotate
 * any variables they wish to use with [ButterKnife.bind] annotations. The view
 * binding will be done in this base abstract class.<br></br>
 * The activity layout of the subclass is expected to contain `DrawerLayout` and
 * a `NavigationView`.<br></br>
 * Sub-class should also consider using the `toolbar.xml` or `toolbar_with_spinner.xml`
 * for the action bar in their XML layout. Otherwise provide another which contains widgets for the
 * toolbar and progress indicator with the IDs `R.id.toolbar` and `R.id.progress_indicator` respectively.
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
abstract class BaseDrawerActivity : PasscodeLockActivity(), PopupMenu.OnMenuItemClickListener {
    @JvmField
    @BindView(R.id.drawer_layout)
    var mDrawerLayout: DrawerLayout? = null

    @JvmField
    @BindView(R.id.nav_view)
    var mNavigationView: NavigationView? = null

    @JvmField
    @BindView(R.id.toolbar)
    var mToolbar: Toolbar? = null

    /**
     * Returns the progress bar for the activity.
     *
     * This progress bar is displayed above the toolbar and should be used to show busy status
     * for long operations.<br></br>
     * The progress bar visibility is set to [View.GONE] by default. Make visible to use
     * @return Indeterminate progress bar.
     */
    @BindView(R.id.toolbar_progress)
    var progressBar: ProgressBar? = null
    protected var mBookNameTextView: TextView? = null
    protected var mDrawerToggle: ActionBarDrawerToggle? = null

    private inner class DrawerItemClickListener : NavigationView.OnNavigationItemSelectedListener {
        override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
            onDrawerMenuItemClicked(menuItem.itemId)
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(contentView)

        //if a parameter was passed to open an account within a specific book, then switch
        val bookUID = intent.getStringExtra(UxArgument.BOOK_UID)
        if (bookUID != null && bookUID != instance.activeBookUID) {
            BookUtils.activateBook(bookUID)
        }
        ButterKnife.bind(this)
        setSupportActionBar(mToolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true)
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setTitle(titleRes)
        }
        progressBar!!.indeterminateDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        val headerView = mNavigationView!!.getHeaderView(0)
        headerView.findViewById<View>(R.id.drawer_title).setOnClickListener { v -> onClickAppTitle(v) }
        mBookNameTextView = headerView.findViewById<View>(R.id.book_name) as TextView
        mBookNameTextView!!.setOnClickListener { v -> onClickBook(v) }
        updateActiveBookName()
        setUpNavigationDrawer()
    }

    override fun onResume() {
        super.onResume()
        updateActiveBookName()
    }

    /**
     * Return the layout to inflate for this activity
     * @return Layout resource identifier
     */
    @get:LayoutRes
    abstract val contentView: Int

    /**
     * Return the title for this activity.
     * This will be displayed in the action bar
     * @return String resource identifier
     */
    @get:StringRes
    abstract val titleRes: Int

    /**
     * Sets up the navigation drawer for this activity.
     */
    private fun setUpNavigationDrawer() {
        mNavigationView!!.setNavigationItemSelectedListener(DrawerItemClickListener())
        mDrawerToggle = object : ActionBarDrawerToggle(
            this,  /* host Activity */
            mDrawerLayout,  /* DrawerLayout object */
            R.string.drawer_open,  /* "open drawer" description */
            R.string.drawer_close /* "close drawer" description */
        ) {
            /** Called when a drawer has settled in a completely closed state.  */
            override fun onDrawerClosed(view: View) {
                super.onDrawerClosed(view)
            }

            /** Called when a drawer has settled in a completely open state.  */
            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
            }
        }
        mDrawerLayout!!.setDrawerListener(mDrawerToggle)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        mDrawerToggle!!.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mDrawerToggle!!.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (!mDrawerLayout!!.isDrawerOpen(mNavigationView!!)) mDrawerLayout!!.openDrawer(mNavigationView!!) else mDrawerLayout!!.closeDrawer(
                mNavigationView!!
            )
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Update the display name of the currently active book
     */
    protected fun updateActiveBookName() {
        mBookNameTextView!!.text = instance.activeBookDisplayName
    }

    /**
     * Handler for the navigation drawer items
     */
    protected fun onDrawerMenuItemClicked(itemId: Int) {
        when (itemId) {
            R.id.nav_item_open -> {
                //Open... files
                //use the storage access framework
                val openDocument = Intent(Intent.ACTION_OPEN_DOCUMENT)
                openDocument.addCategory(Intent.CATEGORY_OPENABLE)
                openDocument.type = "text/*|application/*"
                val mimeTypes = arrayOf("text/*", "application/*")
                openDocument.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                startActivityForResult(openDocument, REQUEST_OPEN_DOCUMENT)
            }

            R.id.nav_item_favorites -> {
                //favorite accounts
                val intent = Intent(this, AccountsActivity::class.java)
                intent.putExtra(
                    AccountsActivity.EXTRA_TAB_INDEX,
                    AccountsActivity.INDEX_FAVORITE_ACCOUNTS_FRAGMENT
                )
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            }

            R.id.nav_item_reports -> {
                val intent = Intent(this, ReportsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            }

            R.id.nav_item_scheduled_actions -> {
                //show scheduled transactions
                val intent = Intent(this, ScheduledActionsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            }

            R.id.nav_item_export -> AccountsActivity.openExportFragment(this)
            R.id.nav_item_settings -> startActivity(Intent(this, PreferenceActivity::class.java))
            R.id.nav_item_help -> {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                prefs.edit().putBoolean(UxArgument.SKIP_PASSCODE_SCREEN, true).apply()
                UserVoice.launchUserVoice(this)
            }
        }
        mDrawerLayout!!.closeDrawer(mNavigationView!!)
    }

    @SuppressLint("WrongConstant")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_CANCELED) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        when (requestCode) {
            AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE -> AccountsActivity.importXmlFileFromIntent(this, data!!, null)
            REQUEST_OPEN_DOCUMENT -> {
                val takeFlags = (data!!.flags
                        and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                AccountsActivity.importXmlFileFromIntent(this, data, null)
                contentResolver.takePersistableUriPermission(data.data!!, takeFlags)
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val id = item.itemId.toLong()
        if (id == ID_MANAGE_BOOKS.toLong()) {
            val intent = Intent(this, PreferenceActivity::class.java)
            intent.action = PreferenceActivity.ACTION_MANAGE_BOOKS
            startActivity(intent)
            mDrawerLayout!!.closeDrawer(mNavigationView!!)
            return true
        }
        val booksDbAdapter = instance
        val bookUID = booksDbAdapter.getUID(id)
        if (bookUID != booksDbAdapter.activeBookUID) {
            BookUtils.loadBook(bookUID!!)
            finish()
        }
        AccountsActivity.start(GnuCashApplication.appContext!!)
        return true
    }

    fun onClickAppTitle(view: View?) {
        mDrawerLayout!!.closeDrawer(mNavigationView!!)
        AccountsActivity.start(this)
    }

    fun onClickBook(view: View?) {
        val popup = PopupMenu(this, view!!)
        popup.setOnMenuItemClickListener(this)
        val menu = popup.menu
        var maxRecent = 0
        val cursor = instance.fetchAllRecords(
            null, null,
            DatabaseSchema.BookEntry.COLUMN_MODIFIED_AT + " DESC"
        )
        while (cursor.moveToNext() && maxRecent++ < 5) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseSchema.BookEntry._ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME))
            menu.add(0, id.toInt(), maxRecent, name)
        }
        menu.add(0, ID_MANAGE_BOOKS, maxRecent, R.string.menu_manage_books)
        popup.show()
    }

    companion object {
        const val ID_MANAGE_BOOKS = 0xB00C
        const val REQUEST_OPEN_DOCUMENT = 0x20
    }
}