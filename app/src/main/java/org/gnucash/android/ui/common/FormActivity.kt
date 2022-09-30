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

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.getActiveAccountColorResource
import org.gnucash.android.db.adapter.BooksDbAdapter.Companion.instance
import org.gnucash.android.ui.account.AccountFormFragment
import org.gnucash.android.ui.budget.BudgetAmountEditorFragment
import org.gnucash.android.ui.budget.BudgetFormFragment
import org.gnucash.android.ui.export.ExportFormFragment
import org.gnucash.android.ui.passcode.PasscodeLockActivity
import org.gnucash.android.ui.transaction.SplitEditorFragment
import org.gnucash.android.ui.transaction.TransactionFormFragment
import org.gnucash.android.ui.util.widget.CalculatorKeyboard
import org.gnucash.android.util.BookUtils

/**
 * Activity for displaying forms in the application.
 * The activity provides the standard close button, but it is up to the form fragments to display
 * menu options (e.g. for saving etc)
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class FormActivity : PasscodeLockActivity() {
    /**
     * Return the GUID of the account for which the form is displayed.
     * If the form is a transaction form, the transaction is created within that account. If it is
     * an account form, then the GUID is the parent account
     * @return GUID of account
     */
    var currentAccountUID: String? = null
        private set
    private var mOnBackListener: CalculatorKeyboard? = null

    enum class FormType {
        ACCOUNT, TRANSACTION, EXPORT, SPLIT_EDITOR, BUDGET, BUDGET_AMOUNT_EDITOR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form)

        //if a parameter was passed to open an account within a specific book, then switch
        val bookUID = intent.getStringExtra(UxArgument.BOOK_UID)
        if (bookUID != null && bookUID != instance.activeBookUID) {
            BookUtils.activateBook(bookUID)
        }
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar!!
        actionBar.setHomeButtonEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp)
        val intent = intent
        val formtypeString = intent.getStringExtra(UxArgument.FORM_TYPE)
        val formType = FormType.valueOf(formtypeString!!)
        currentAccountUID = intent.getStringExtra(UxArgument.SELECTED_ACCOUNT_UID)
        if (currentAccountUID == null) {
            currentAccountUID = intent.getStringExtra(UxArgument.PARENT_ACCOUNT_UID)
        }
        if (currentAccountUID != null) {
            val colorCode = getActiveAccountColorResource(currentAccountUID!!)
            actionBar.setBackgroundDrawable(ColorDrawable(colorCode))
            if (Build.VERSION.SDK_INT > 20) window.statusBarColor = GnuCashApplication.darken(colorCode)
        }
        when (formType) {
            FormType.ACCOUNT -> showAccountFormFragment(intent.extras)
            FormType.TRANSACTION -> showTransactionFormFragment(intent.extras)
            FormType.EXPORT -> showExportFormFragment(null)
            FormType.SPLIT_EDITOR -> showSplitEditorFragment(intent.extras)
            FormType.BUDGET -> showBudgetFormFragment(intent.extras)
            FormType.BUDGET_AMOUNT_EDITOR -> showBudgetAmountEditorFragment(intent.extras)
//            else -> throw IllegalArgumentException("No form display type specified") redundant by XJ
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                setResult(RESULT_CANCELED)
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Shows the form for creating/editing accounts
     * @param args Arguments to use for initializing the form.
     * This could be an account to edit or a preset for the parent account
     */
    private fun showAccountFormFragment(args: Bundle?) {
        val accountFormFragment = AccountFormFragment.newInstance()
        accountFormFragment.arguments = args
        showFormFragment(accountFormFragment)
    }

    /**
     * Loads the transaction insert/edit fragment and passes the arguments
     * @param args Bundle arguments to be passed to the fragment
     */
    private fun showTransactionFormFragment(args: Bundle?) {
        val transactionFormFragment = TransactionFormFragment()
        transactionFormFragment.arguments = args
        showFormFragment(transactionFormFragment)
    }

    /**
     * Loads the export form fragment and passes the arguments
     * @param args Bundle arguments
     */
    private fun showExportFormFragment(args: Bundle?) {
        val exportFragment = ExportFormFragment()
        exportFragment.arguments = args
        showFormFragment(exportFragment)
    }

    /**
     * Load the split editor fragment
     * @param args View arguments
     */
    private fun showSplitEditorFragment(args: Bundle?) {
        val splitEditor = SplitEditorFragment.newInstance(args)
        showFormFragment(splitEditor)
    }

    /**
     * Load the budget form
     * @param args View arguments
     */
    private fun showBudgetFormFragment(args: Bundle?) {
        val budgetFormFragment = BudgetFormFragment()
        budgetFormFragment.arguments = args
        showFormFragment(budgetFormFragment)
    }

    /**
     * Load the budget amount editor fragment
     * @param args Arguments
     */
    private fun showBudgetAmountEditorFragment(args: Bundle?) {
        val fragment = BudgetAmountEditorFragment.newInstance(args)
        showFormFragment(fragment)
    }

    /**
     * Loads the fragment into the fragment container, replacing whatever was there before
     * @param fragment Fragment to be displayed
     */
    private fun showFormFragment(fragment: Fragment) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager
            .beginTransaction()
        fragmentTransaction.add(R.id.fragment_container, fragment)
        fragmentTransaction.commit()
    }

    fun setOnBackListener(keyboard: CalculatorKeyboard?) {
        mOnBackListener = keyboard
    }

    override fun onBackPressed() {
        var eventProcessed = false
        if (mOnBackListener != null) eventProcessed = mOnBackListener!!.onBackPressed()
        if (!eventProcessed) super.onBackPressed()
    }
}