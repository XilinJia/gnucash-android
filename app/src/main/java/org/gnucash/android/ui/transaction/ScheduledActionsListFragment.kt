/*
 * Copyright (c) 2013 - 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.transaction

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.ListFragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseCursorLoader
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.ExportParams.Companion.parseCsv
import org.gnucash.android.model.ScheduledAction.ActionType
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.util.BackupManager
import java.text.DateFormat
import java.util.*

/**
 * Fragment which displays the scheduled actions in the system
 *
 * Currently, it handles the display of scheduled transactions and scheduled exports
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class ScheduledActionsListFragment : ListFragment(), LoaderManager.LoaderCallbacks<Cursor> {
    private var mTransactionsDbAdapter: TransactionsDbAdapter? = null
    private var mCursorAdapter: SimpleCursorAdapter? = null
    private var mActionMode: ActionMode? = null

    /**
     * Flag which is set when a transaction is selected
     */
    private var mInEditMode = false
    private var mActionType = ActionType.TRANSACTION

    /**
     * Callbacks for the menu items in the Context ActionBar (CAB) in action mode
     */
    private val mActionModeCallbacks: ActionMode.Callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val inflater = mode.menuInflater
            inflater.inflate(R.menu.schedxactions_context_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            //nothing to see here, move along
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            finishEditMode()
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.context_menu_delete -> {
                    BackupManager.backupActiveBook()
                    for (id in listView.checkedItemIds) {
                        if (mActionType === ActionType.TRANSACTION) {
                            Log.i(TAG, "Cancelling scheduled transaction(s)")
                            val trnUID = mTransactionsDbAdapter!!.getUID(id)
                            val scheduledActionDbAdapter = GnuCashApplication.scheduledEventDbAdapter
                            val actions = scheduledActionDbAdapter!!.getScheduledActionsWithUID(
                                trnUID!!
                            )
                            if (mTransactionsDbAdapter!!.deleteRecord(id)) {
                                Toast.makeText(
                                    activity,
                                    R.string.toast_recurring_transaction_deleted,
                                    Toast.LENGTH_SHORT
                                ).show()
                                for (action in actions) {
                                    scheduledActionDbAdapter.deleteRecord(action.mUID!!)
                                }
                            }
                        } else if (mActionType === ActionType.BACKUP) {
                            Log.i(TAG, "Removing scheduled exports")
                            ScheduledActionDbAdapter.instance.deleteRecord(id)
                        }
                    }
                    mode.finish()
                    setDefaultStatusBarColor()
                    loaderManager.destroyLoader(0)
                    refreshList()
                    true
                }

                else -> {
                    setDefaultStatusBarColor()
                    false
                }
            }
        }
    }

    private fun setDefaultStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity!!.window.statusBarColor = ContextCompat.getColor(context!!, R.color.theme_primary_dark)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mTransactionsDbAdapter = TransactionsDbAdapter.instance
        mCursorAdapter = when (mActionType) {
            ActionType.TRANSACTION -> ScheduledTransactionsCursorAdapter(
                activity!!.applicationContext,
                R.layout.list_item_scheduled_trxn,
                null,
                arrayOf(DatabaseSchema.TransactionEntry.COLUMN_DESCRIPTION),
                intArrayOf(R.id.primary_text)
            )

            ActionType.BACKUP -> ScheduledExportCursorAdapter(
                activity!!.applicationContext,
                R.layout.list_item_scheduled_trxn, null, arrayOf(), intArrayOf()
            )

        }
        listAdapter = mCursorAdapter
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_scheduled_events_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar
        actionBar!!.setDisplayShowTitleEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setHomeButtonEnabled(true)
        setHasOptionsMenu(true)
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        (listView.emptyView as TextView)
            .setTextColor(ContextCompat.getColor(context!!, R.color.theme_accent))
        if (mActionType === ActionType.TRANSACTION) {
            (listView.emptyView as TextView).setText(R.string.label_no_recurring_transactions)
        } else if (mActionType === ActionType.BACKUP) {
            (listView.emptyView as TextView).setText(R.string.label_no_scheduled_exports_to_display)
        }
    }

    /**
     * Reload the list of transactions and recompute account balances
     */
    fun refreshList() {
        loaderManager.restartLoader(0, null, this)
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (mActionType === ActionType.BACKUP) inflater.inflate(R.menu.scheduled_export_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_add_scheduled_export -> {
                val intent = Intent(activity, FormActivity::class.java)
                intent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.EXPORT.name)
                startActivityForResult(intent, 0x1)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)
        if (mActionMode != null) {
            val checkbox = v.findViewById<View>(R.id.checkbox) as CheckBox
            checkbox.isChecked = !checkbox.isChecked
            return
        }
        if (mActionType === ActionType.BACKUP) //nothing to do for export actions
            return
        val transaction = mTransactionsDbAdapter!!.getRecord(id)

        //this should actually never happen, but has happened once. So perform check for the future
        if (transaction.getMSplitList().isEmpty()) {
            Toast.makeText(activity, R.string.toast_transaction_has_no_splits_and_cannot_open, Toast.LENGTH_SHORT)
                .show()
            return
        }
        val accountUID = transaction.getMSplitList()[0].mAccountUID
        openTransactionForEdit(
            accountUID, mTransactionsDbAdapter!!.getUID(id),
            v.tag.toString()
        )
    }

    /**
     * Opens the transaction editor to enable editing of the transaction
     * @param accountUID GUID of account to which transaction belongs
     * @param transactionUID GUID of transaction to be edited
     */
    fun openTransactionForEdit(accountUID: String?, transactionUID: String?, scheduledActionUid: String?) {
        val createTransactionIntent = Intent(activity, FormActivity::class.java)
        createTransactionIntent.action = Intent.ACTION_INSERT_OR_EDIT
        createTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
        createTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
        createTransactionIntent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID)
        createTransactionIntent.putExtra(UxArgument.SCHEDULED_ACTION_UID, scheduledActionUid)
        startActivity(createTransactionIntent)
    }

    override fun onCreateLoader(arg0: Int, arg1: Bundle?): Loader<Cursor> {
        Log.d(TAG, "Creating transactions loader")
        if (mActionType === ActionType.TRANSACTION)
            return ScheduledTransactionsCursorLoader(activity)
        else if (mActionType === ActionType.BACKUP) {
            return ScheduledExportCursorLoader(activity)
        }
        return ScheduledTransactionsCursorLoader(activity)  // by XJ
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor) {
        Log.d(TAG, "Transactions loader finished. Swapping in cursor")
        mCursorAdapter!!.swapCursor(cursor)
        mCursorAdapter!!.notifyDataSetChanged()
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        Log.d(TAG, "Resetting transactions loader")
        mCursorAdapter!!.swapCursor(null)
    }

    /**
     * Finishes the edit mode in the transactions list.
     * Edit mode is started when at least one transaction is selected
     */
    fun finishEditMode() {
        mInEditMode = false
        uncheckAllItems()
        mActionMode = null
    }

    /**
     * Sets the title of the Context ActionBar when in action mode.
     * It sets the number highlighted items
     */
    fun setActionModeTitle() {
        val count = listView.checkedItemIds.size //mSelectedIds.size();
        if (count > 0) {
            mActionMode!!.title = resources.getString(R.string.title_selected, count)
        }
    }

    /**
     * Unchecks all the checked items in the list
     */
    private fun uncheckAllItems() {
        val checkedPositions = listView.checkedItemPositions
        val listView = listView
        for (i in 0 until checkedPositions.size()) {
            val position = checkedPositions.keyAt(i)
            listView.setItemChecked(position, false)
        }
    }

    /**
     * Starts action mode and activates the Context ActionBar (CAB)
     * Action mode is initiated as soon as at least one transaction is selected (highlighted)
     */
    private fun startActionMode() {
        if (mActionMode != null) {
            return
        }
        mInEditMode = true
        // Start the CAB using the ActionMode.Callback defined above
        mActionMode = (activity as AppCompatActivity?)?.startSupportActionMode(mActionModeCallbacks)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity!!.window.statusBarColor = ContextCompat.getColor(context!!, android.R.color.darker_gray)
        }
    }

    /**
     * Stops action mode and deselects all selected transactions.
     * This method only has effect if the number of checked items is greater than 0 and [.mActionMode] is not null
     */
    private fun stopActionMode() {
        val checkedCount = listView.checkedItemIds.size
        if (checkedCount <= 0 && mActionMode != null) {
            mActionMode!!.finish()
            setDefaultStatusBarColor()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            refreshList()
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /**
     * Extends a simple cursor adapter to bind transaction attributes to views
     * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
     */
    private inner class ScheduledTransactionsCursorAdapter(
        context: Context?, layout: Int, c: Cursor?,
        from: Array<String?>?, to: IntArray?
    ) : SimpleCursorAdapter(context, layout, c, from, to, 0) {
        override fun getView(
            position: Int,
            convertView: View,
            parent: ViewGroup
        ): View {
            val view = super.getView(position, convertView, parent)
            val checkbox = view.findViewById<View>(R.id.checkbox) as CheckBox
            //TODO: Revisit this if we ever change the application theme
            val id =
                Resources.getSystem().getIdentifier("btn_check_holo_light", "drawable", "android")
            checkbox.setButtonDrawable(id)
            val secondaryText = view.findViewById<View>(R.id.secondary_text) as TextView
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                listView.setItemChecked(position, isChecked)
                if (isChecked) {
                    startActionMode()
                } else {
                    stopActionMode()
                }
                setActionModeTitle()
            }
            val listView = parent as ListView
            if (mInEditMode && listView.isItemChecked(position)) {
                view.setBackgroundColor(ContextCompat.getColor(context!!, R.color.abs__holo_blue_light))
                secondaryText.setTextColor(ContextCompat.getColor(context!!, android.R.color.white))
            } else {
                view.setBackgroundColor(ContextCompat.getColor(context!!, android.R.color.transparent))
                secondaryText.setTextColor(
                    ContextCompat.getColor(
                        context!!,
                        android.R.color.secondary_text_light_nodisable
                    )
                )
                checkbox.isChecked = false
            }
            val checkBoxView: View = checkbox
            view.post {
                if (isAdded) { //may be run when fragment has been unbound from activity
                    val extraPadding = resources.getDimension(R.dimen.edge_padding)
                    val hitRect = Rect()
                    checkBoxView.getHitRect(hitRect)
                    hitRect.right += extraPadding.toInt()
                    hitRect.bottom += (3 * extraPadding).toInt()
                    hitRect.top -= extraPadding.toInt()
                    hitRect.left -= (2 * extraPadding).toInt()
                    view.touchDelegate = TouchDelegate(hitRect, checkBoxView)
                }
            }
            return view
        }

        override fun bindView(view: View, context: Context, cursor: Cursor) {
            super.bindView(view, context, cursor)
            val transaction = mTransactionsDbAdapter!!.buildModelInstance(cursor)
            val amountTextView = view.findViewById<View>(R.id.right_text) as TextView
            if (transaction.getMSplitList().size == 2) {
                if (transaction.getMSplitList()[0].isPairOf(transaction.getMSplitList()[1])) {
                    amountTextView.text = transaction.getMSplitList()[0].mValue!!.formattedString()
                }
            } else {
                amountTextView.text = getString(R.string.label_split_count, transaction.getMSplitList().size)
            }
            val descriptionTextView = view.findViewById<View>(R.id.secondary_text) as TextView
            val scheduledActionDbAdapter = ScheduledActionDbAdapter.instance
            val scheduledActionUID =
                cursor.getString(cursor.getColumnIndexOrThrow("origin_scheduled_action_uid")) //column created from join when fetching scheduled transactions
            view.tag = scheduledActionUID
            val scheduledAction = scheduledActionDbAdapter.getRecord(scheduledActionUID)
            val endTime = scheduledAction.getMEndDate()
            if (endTime > 0 && endTime < System.currentTimeMillis()) {
                (view.findViewById<View>(R.id.primary_text) as TextView).setTextColor(
                    ContextCompat.getColor(getContext()!!, android.R.color.darker_gray)
                )
                descriptionTextView.text = getString(
                    R.string.label_scheduled_action_ended,
                    DateFormat.getInstance().format(Date(scheduledAction.mLastRun))
                )
            } else {
                descriptionTextView.text = scheduledAction.repeatString()
            }
        }
    }

    /**
     * Extends a simple cursor adapter to bind transaction attributes to views
     * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
     */
    private inner class ScheduledExportCursorAdapter(
        context: Context?, layout: Int, c: Cursor?,
        from: Array<String?>?, to: IntArray?
    ) : SimpleCursorAdapter(context, layout, c, from, to, 0) {
        override fun getView(
            position: Int,
            convertView: View,
            parent: ViewGroup
        ): View {
            val view = super.getView(position, convertView, parent)
            val checkbox = view.findViewById<View>(R.id.checkbox) as CheckBox
            //TODO: Revisit this if we ever change the application theme
            val id =
                Resources.getSystem().getIdentifier("btn_check_holo_light", "drawable", "android")
            checkbox.setButtonDrawable(id)
            val secondaryText = view.findViewById<View>(R.id.secondary_text) as TextView
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                listView.setItemChecked(position, isChecked)
                if (isChecked) {
                    startActionMode()
                } else {
                    stopActionMode()
                }
                setActionModeTitle()
            }
            val listView = parent as ListView
            if (mInEditMode && listView.isItemChecked(position)) {
                view.setBackgroundColor(ContextCompat.getColor(context!!, R.color.abs__holo_blue_light))
                secondaryText.setTextColor(ContextCompat.getColor(context!!, android.R.color.white))
            } else {
                view.setBackgroundColor(ContextCompat.getColor(context!!, android.R.color.transparent))
                secondaryText.setTextColor(
                    ContextCompat.getColor(context!!, android.R.color.secondary_text_light_nodisable)
                )
                checkbox.isChecked = false
            }
            val checkBoxView: View = checkbox
            view.post {
                if (isAdded) { //may be run when fragment has been unbound from activity
                    val extraPadding = resources.getDimension(R.dimen.edge_padding)
                    val hitRect = Rect()
                    checkBoxView.getHitRect(hitRect)
                    hitRect.right += extraPadding.toInt()
                    hitRect.bottom += (3 * extraPadding).toInt()
                    hitRect.top -= extraPadding.toInt()
                    hitRect.left -= (2 * extraPadding).toInt()
                    view.touchDelegate = TouchDelegate(hitRect, checkBoxView)
                }
            }
            return view
        }

        override fun bindView(view: View, context: Context, cursor: Cursor) {
            super.bindView(view, context, cursor)
            val mScheduledActionDbAdapter = ScheduledActionDbAdapter.instance
            val scheduledAction = mScheduledActionDbAdapter.buildModelInstance(cursor)
            val primaryTextView = view.findViewById<View>(R.id.primary_text) as TextView
            val params = parseCsv(scheduledAction.mTag!!)
            var exportDestination = params.exportTarget.description
            if (params.exportTarget === ExportParams.ExportTarget.URI) {
                exportDestination = exportDestination + " (" + Uri.parse(params.exportLocation).host + ")"
            }
            primaryTextView.text = (params.exportFormat.name + " "
                    + scheduledAction.mActionType.name.lowercase(Locale.getDefault()) + " to "
                    + exportDestination)
            view.findViewById<View>(R.id.right_text).visibility = View.GONE
            val descriptionTextView = view.findViewById<View>(R.id.secondary_text) as TextView
            descriptionTextView.text = scheduledAction.repeatString()
            val endTime = scheduledAction.getMEndDate()
            if (endTime > 0 && endTime < System.currentTimeMillis()) {
                (view.findViewById<View>(R.id.primary_text) as TextView)
                    .setTextColor(ContextCompat.getColor(getContext()!!, android.R.color.darker_gray))
                descriptionTextView.text = getString(
                    R.string.label_scheduled_action_ended,
                    DateFormat.getInstance().format(Date(scheduledAction.mLastRun))
                )
            } else {
                descriptionTextView.text = scheduledAction.repeatString()
            }
        }
    }

    /**
     * [DatabaseCursorLoader] for loading recurring transactions asynchronously from the database
     * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
     */
    private class ScheduledTransactionsCursorLoader(context: Context?) : DatabaseCursorLoader(context) {
        override fun loadInBackground(): Cursor {
            mDatabaseAdapter = TransactionsDbAdapter.instance
            val c = (mDatabaseAdapter as TransactionsDbAdapter).fetchAllScheduledTransactions()
            registerContentObserver(c)
            return c
        }
    }

    /**
     * [DatabaseCursorLoader] for loading recurring transactions asynchronously from the database
     * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
     */
    private class ScheduledExportCursorLoader(context: Context?) : DatabaseCursorLoader(context) {
        override fun loadInBackground(): Cursor {
            mDatabaseAdapter = ScheduledActionDbAdapter.instance
            val c = mDatabaseAdapter!!.fetchAllRecords(
                DatabaseSchema.ScheduledActionEntry.COLUMN_TYPE + "=?", arrayOf(ActionType.BACKUP.name), null
            )
            registerContentObserver(c)
            return c
        }
    }

    companion object {
        /**
         * Logging tag
         */
        private const val TAG = "ScheduledActionFragment"

        /**
         * Returns a new instance of the fragment for displayed the scheduled action
         * @param actionType Type of scheduled action to be displayed
         * @return New instance of fragment
         */
        fun getInstance(actionType: ActionType): Fragment {
            val fragment = ScheduledActionsListFragment()
            fragment.mActionType = actionType
            return fragment
        }
    }
}