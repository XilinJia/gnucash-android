/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.Fragment
import butterknife.BindView
import butterknife.ButterKnife
import com.google.android.material.textfield.TextInputLayout
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Money
import org.gnucash.android.ui.colorpicker.ColorPickerDialog
import org.gnucash.android.ui.colorpicker.ColorPickerSwatch.OnColorSelectedListener
import org.gnucash.android.ui.colorpicker.ColorSquare
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.settings.PreferenceActivity
import org.gnucash.android.util.CommoditiesCursorAdapter
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter
import java.util.*

/**
 * Fragment used for creating and editing accounts
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Yongxin Wang <fefe.wyx></fefe.wyx>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class AccountFormFragment
/**
 * Default constructor
 * Required, else the app crashes on screen rotation
 */
    : Fragment() {
    /**
     * EditText for the name of the account to be created/edited
     */
    @JvmField
    @BindView(R.id.input_account_name)
    var mNameEditText: EditText? = null

    @JvmField
    @BindView(R.id.name_text_input_layout)
    var mTextInputLayout: TextInputLayout? = null

    /**
     * Spinner for selecting the currency of the account
     * Currencies listed are those specified by ISO 4217
     */
    @JvmField
    @BindView(R.id.input_currency_spinner)
    var mCurrencySpinner: Spinner? = null

    /**
     * Accounts database adapter
     */
    private var mAccountsDbAdapter: AccountsDbAdapter? = null

    /**
     * GUID of the parent account
     * This value is set to the parent account of the transaction being edited or
     * the account in which a new sub-account is being created
     */
    private var mParentAccountUID: String? = null

    /**
     * Account ID of the root account
     */
    private var mRootAccountId: Long = -1

    /**
     * Account UID of the root account
     */
    private var mRootAccountUID: String? = null

    /**
     * Reference to account object which will be created at end of dialog
     */
    private var mAccount: Account? = null

    /**
     * Unique ID string of account being edited
     */
    private var mAccountUID: String? = null

    /**
     * Cursor which will hold set of eligible parent accounts
     */
    private var mParentAccountCursor: Cursor? = null

    /**
     * List of all descendant Account UIDs, if we are modifying an account
     * null if creating a new account
     */
    private var mDescendantAccountUIDs: List<String?>? = null

    /**
     * SimpleCursorAdapter for the parent account spinner
     * @see QualifiedAccountNameCursorAdapter
     */
    private var mParentAccountCursorAdapter: SimpleCursorAdapter? = null

    /**
     * Spinner for parent account list
     */
    @JvmField
    @BindView(R.id.input_parent_account)
    var mParentAccountSpinner: Spinner? = null

    /**
     * Checkbox which activates the parent account spinner when selected
     * Leaving this unchecked means it is a top-level root account
     */
    @JvmField
    @BindView(R.id.checkbox_parent_account)
    var mParentCheckBox: CheckBox? = null

    /**
     * Spinner for the account type
     * @see org.gnucash.android.model.AccountType
     */
    @JvmField
    @BindView(R.id.input_account_type_spinner)
    var mAccountTypeSpinner: Spinner? = null

    /**
     * Checkbox for activating the default transfer account spinner
     */
    @JvmField
    @BindView(R.id.checkbox_default_transfer_account)
    var mDefaultTransferAccountCheckBox: CheckBox? = null

    /**
     * Spinner for selecting the default transfer account
     */
    @JvmField
    @BindView(R.id.input_default_transfer_account)
    var mDefaultTransferAccountSpinner: Spinner? = null

    /**
     * Account description input text view
     */
    @JvmField
    @BindView(R.id.input_account_description)
    var mDescriptionEditText: EditText? = null

    /**
     * Checkbox indicating if account is a placeholder account
     */
    @JvmField
    @BindView(R.id.checkbox_placeholder_account)
    var mPlaceholderCheckBox: CheckBox? = null

    /**
     * Cursor adapter which binds to the spinner for default transfer account
     */
    private var mDefaultTransferAccountCursorAdapter: SimpleCursorAdapter? = null

    /**
     * Flag indicating if double entry transactions are enabled
     */
    private var mUseDoubleEntry = false
    private var mSelectedColor = Account.DEFAULT_COLOR

    /**
     * Trigger for color picker dialog
     */
    @JvmField
    @BindView(R.id.input_color_picker)
    var mColorSquare: ColorSquare? = null
    private val mColorSelectedListener = object : OnColorSelectedListener {
        override fun onColorSelected(color: Int) {
            mColorSquare!!.setBackgroundColor(color)
            mSelectedColor = color
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        mAccountsDbAdapter = AccountsDbAdapter.instance
        val sharedPrefs = PreferenceActivity.getActiveBookSharedPreferences()
        mUseDoubleEntry = sharedPrefs.getBoolean(getString(R.string.key_use_double_entry), true)
    }

    /**
     * Inflates the dialog view and retrieves references to the dialog elements
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_account_form, container, false)
        ButterKnife.bind(this, view)
        mNameEditText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                //nothing to see here, move along
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                //nothing to see here, move along
            }

            override fun afterTextChanged(s: Editable) {
                if (s.toString().isNotEmpty()) {
                    mTextInputLayout!!.isErrorEnabled = false
                }
            }
        })
        mAccountTypeSpinner!!.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View, position: Int, id: Long) {
                loadParentAccountList(selectedAccountType)
                if (mParentAccountUID != null) setParentAccountSelection(mAccountsDbAdapter!!.getID(mParentAccountUID!!))
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                //nothing to see here, move along
            }
        }
        mParentAccountSpinner!!.isEnabled = false
        mParentCheckBox!!.setOnCheckedChangeListener { _, isChecked ->
            mParentAccountSpinner!!.isEnabled = isChecked
        }
        mDefaultTransferAccountSpinner!!.isEnabled = false
        mDefaultTransferAccountCheckBox!!.setOnCheckedChangeListener { _, isChecked ->
            mDefaultTransferAccountSpinner!!.isEnabled = isChecked
        }
        mColorSquare!!.setOnClickListener { showColorPickerDialog() }
        return view
    }

    /**
     * Initializes the values of the views in the dialog
     */
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val commoditiesAdapter = CommoditiesCursorAdapter(
            activity, android.R.layout.simple_spinner_item
        )
        commoditiesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mCurrencySpinner!!.adapter = commoditiesAdapter
        mAccountUID = arguments!!.getString(UxArgument.SELECTED_ACCOUNT_UID)
        val supportActionBar = (activity as AppCompatActivity?)!!.supportActionBar
        if (mAccountUID != null) {
            mAccount = mAccountsDbAdapter!!.getRecord(mAccountUID!!)
            supportActionBar!!.setTitle(R.string.title_edit_account)
        } else {
            supportActionBar!!.setTitle(R.string.title_create_account)
        }
        mRootAccountUID = mAccountsDbAdapter!!.orCreateGnuCashRootAccountUID
        if (mRootAccountUID != null) mRootAccountId = mAccountsDbAdapter!!.getID(mRootAccountUID!!)

        //need to load the cursor adapters for the spinners before initializing the views
        loadAccountTypesList()
        loadDefaultTransferAccountList()
        setDefaultTransferAccountInputsVisible(mUseDoubleEntry)
        if (mAccount != null) {
            initializeViewsWithAccount(mAccount)
            //do not immediately open the keyboard when editing an account
            activity!!.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        } else {
            initializeViews()
        }
    }

    /**
     * Initialize view with the properties of `account`.
     * This is applicable when editing an account
     * @param account Account whose fields are used to populate the form
     */
    private fun initializeViewsWithAccount(account: Account?) {
        requireNotNull(account) { "Account cannot be null" }
        loadParentAccountList(account.mAccountType)
        mParentAccountUID = account.mParentAccountUID
        if (mParentAccountUID == null) {
            // null parent, set Parent as root
            mParentAccountUID = mRootAccountUID
        }
        if (mParentAccountUID != null) {
            setParentAccountSelection(mAccountsDbAdapter!!.getID(mParentAccountUID!!))
        }
        val currencyCode = account.getMCommodity().mMnemonic
        setSelectedCurrency(currencyCode)
        if (mAccountsDbAdapter!!.getTransactionMaxSplitNum(mAccount!!.mUID!!) > 1) {
            //TODO: Allow changing the currency and effecting the change for all transactions without any currency exchange (purely cosmetic change)
            mCurrencySpinner!!.isEnabled = false
        }
        mNameEditText!!.setText(account.mName)
        mNameEditText!!.setSelection(mNameEditText!!.text.length)
        mDescriptionEditText!!.setText(account.mDescription)
        if (mUseDoubleEntry) {
            if (account.mDefaultTransferAccountUID != null) {
                val doubleDefaultAccountId = mAccountsDbAdapter!!.getID(account.mDefaultTransferAccountUID!!)
                setDefaultTransferAccountSelection(doubleDefaultAccountId, true)
            } else {
                var currentAccountUID = account.mParentAccountUID
                val rootAccountUID = mAccountsDbAdapter!!.orCreateGnuCashRootAccountUID
                while (currentAccountUID != rootAccountUID) {
                    val defaultTransferAccountID = mAccountsDbAdapter!!.getDefaultTransferAccountID(
                        mAccountsDbAdapter!!.getID(currentAccountUID!!)
                    )
                    if (defaultTransferAccountID > 0) {
                        setDefaultTransferAccountSelection(defaultTransferAccountID, false)
                        break //we found a parent with default transfer setting
                    }
                    currentAccountUID = mAccountsDbAdapter!!.getParentAccountUID(currentAccountUID)
                }
            }
        }
        mPlaceholderCheckBox!!.isChecked = account.isPlaceholderAccount
        mSelectedColor = account.getMColor()
        mColorSquare!!.setBackgroundColor(account.getMColor())
        setAccountTypeSelection(account.mAccountType)
    }

    /**
     * Initialize views with defaults for new account
     */
    private fun initializeViews() {
        setSelectedCurrency(Money.DEFAULT_CURRENCY_CODE)
        mColorSquare!!.setBackgroundColor(Color.LTGRAY)
        mParentAccountUID = arguments!!.getString(UxArgument.PARENT_ACCOUNT_UID)
        if (mParentAccountUID != null) {
            val parentAccountType = mAccountsDbAdapter!!.getAccountType(
                mParentAccountUID!!
            )
            setAccountTypeSelection(parentAccountType)
            loadParentAccountList(parentAccountType)
            setParentAccountSelection(mAccountsDbAdapter!!.getID(mParentAccountUID!!))
        }
    }

    /**
     * Selects the corresponding account type in the spinner
     * @param accountType AccountType to be set
     */
    private fun setAccountTypeSelection(accountType: AccountType) {
        val accountTypeEntries = resources.getStringArray(R.array.key_account_type_entries)
        val accountTypeIndex = listOf(*accountTypeEntries).indexOf(accountType.name)
        mAccountTypeSpinner!!.setSelection(accountTypeIndex)
    }

    /**
     * Toggles the visibility of the default transfer account input fields.
     * This field is irrelevant for users who do not use double accounting
     */
    private fun setDefaultTransferAccountInputsVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        val view = view!!
        view.findViewById<View>(R.id.layout_default_transfer_account).visibility = visibility
        view.findViewById<View>(R.id.label_default_transfer_account).visibility = visibility
    }

    /**
     * Selects the currency with code `currencyCode` in the spinner
     * @param currencyCode ISO 4217 currency code to be selected
     */
    private fun setSelectedCurrency(currencyCode: String) {
        val commodityDbAdapter = CommoditiesDbAdapter.instance
        val commodityId = commodityDbAdapter.getID(commodityDbAdapter.getCommodityUID(currencyCode))
        var position = 0
        for (i in 0 until mCurrencySpinner!!.count) {
            if (commodityId == mCurrencySpinner!!.getItemIdAtPosition(i)) {
                position = i
            }
        }
        mCurrencySpinner!!.setSelection(position)
    }

    /**
     * Selects the account with ID `parentAccountId` in the parent accounts spinner
     * @param parentAccountId Record ID of parent account to be selected
     */
    private fun setParentAccountSelection(parentAccountId: Long) {
        if (parentAccountId <= 0 || parentAccountId == mRootAccountId) {
            return
        }
        for (pos in 0 until mParentAccountCursorAdapter!!.count) {
            if (mParentAccountCursorAdapter!!.getItemId(pos) == parentAccountId) {
                mParentCheckBox!!.isChecked = true
                mParentAccountSpinner!!.isEnabled = true
                mParentAccountSpinner!!.setSelection(pos, true)
                break
            }
        }
    }

    /**
     * Selects the account with ID `parentAccountId` in the default transfer account spinner
     * @param defaultTransferAccountId Record ID of parent account to be selected
     */
    private fun setDefaultTransferAccountSelection(defaultTransferAccountId: Long, enableTransferAccount: Boolean) {
        if (defaultTransferAccountId > 0) {
            mDefaultTransferAccountCheckBox!!.isChecked = enableTransferAccount
            mDefaultTransferAccountSpinner!!.isEnabled = enableTransferAccount
        } else return
        for (pos in 0 until mDefaultTransferAccountCursorAdapter!!.count) {
            if (mDefaultTransferAccountCursorAdapter!!.getItemId(pos) == defaultTransferAccountId) {
                mDefaultTransferAccountSpinner!!.setSelection(pos)
                break
            }
        }
    }

    /**
     * Returns an array of colors used for accounts.
     * The array returned has the actual color values and not the resource ID.
     * @return Integer array of colors used for accounts
     */
    private val accountColorOptions: IntArray
        get() {
            val res = resources
            val colorTypedArray = res.obtainTypedArray(R.array.account_colors)
            val colorOptions = IntArray(colorTypedArray.length())
            for (i in 0 until colorTypedArray.length()) {
                val color = colorTypedArray.getColor(
                    i, ContextCompat.getColor(
                        context!!,
                        R.color.title_green
                    )
                )
                colorOptions[i] = color
            }
            colorTypedArray.recycle()
            return colorOptions
        }

    /**
     * Shows the color picker dialog
     */
    private fun showColorPickerDialog() {
        val fragmentManager = activity!!.supportFragmentManager
        var currentColor = Color.LTGRAY
        if (mAccount != null) {
            currentColor = mAccount!!.getMColor()
        }
        val colorPickerDialogFragment = ColorPickerDialog.newInstance(
            R.string.color_picker_default_title,
            accountColorOptions,
            currentColor, 4, 12
        )
        colorPickerDialogFragment.setOnColorSelectedListener(mColorSelectedListener)
        colorPickerDialogFragment.show(fragmentManager, COLOR_PICKER_DIALOG_TAG)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.default_save_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> {
                saveAccount()
                return true
            }

            android.R.id.home -> {
                finishFragment()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Initializes the default transfer account spinner with eligible accounts
     */
    private fun loadDefaultTransferAccountList() {
        val condition =
            (DatabaseSchema.AccountEntry.COLUMN_UID + " != '" + mAccountUID + "' " //when creating a new account mAccountUID is null, so don't use whereArgs
                    + " AND " + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + "=0"
                    + " AND " + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + "=0"
                    + " AND " + DatabaseSchema.AccountEntry.COLUMN_TYPE + " != ?")
        val defaultTransferAccountCursor = mAccountsDbAdapter!!.fetchAccountsOrderedByFullName(
            condition, arrayOf(
                AccountType.ROOT.name
            )
        )
        if (mDefaultTransferAccountSpinner!!.count <= 0) {
            setDefaultTransferAccountInputsVisible(false)
        }
        mDefaultTransferAccountCursorAdapter = QualifiedAccountNameCursorAdapter(
            activity,
            defaultTransferAccountCursor
        )
        mDefaultTransferAccountSpinner!!.adapter = mDefaultTransferAccountCursorAdapter
    }

    /**
     * Loads the list of possible accounts which can be set as a parent account and initializes the spinner.
     * The allowed parent accounts depends on the account type
     * @param accountType AccountType of account whose allowed parent list is to be loaded
     */
    private fun loadParentAccountList(accountType: AccountType) {
        var condition = (DatabaseSchema.SplitEntry.COLUMN_TYPE + " IN ("
                + getAllowedParentAccountTypes(accountType) + ") AND " + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + "!=1 ")
        if (mAccount != null) {  //if editing an account
            mDescendantAccountUIDs = mAccountsDbAdapter!!.getDescendantAccountUIDs(mAccount!!.mUID, null, null)
            val rootAccountUID = mAccountsDbAdapter!!.orCreateGnuCashRootAccountUID
            val descendantAccountUIDs: MutableList<String?> = ArrayList(mDescendantAccountUIDs!!)
            if (rootAccountUID != null) descendantAccountUIDs.add(rootAccountUID)
            // limit cyclic account hierarchies.
            condition += (" AND (" + DatabaseSchema.AccountEntry.COLUMN_UID + " NOT IN ( '"
                    + TextUtils.join("','", descendantAccountUIDs) + "','" + mAccountUID + "' ) )")
        }

        //if we are reloading the list, close the previous cursor first
        if (mParentAccountCursor != null) mParentAccountCursor!!.close()
        mParentAccountCursor = mAccountsDbAdapter!!.fetchAccountsOrderedByFullName(condition, null)
        val view = view!!
        if (mParentAccountCursor!!.count <= 0) {
            mParentCheckBox!!.isChecked = false //disable before hiding, else we can still read it when saving
            view.findViewById<View>(R.id.layout_parent_account).visibility = View.GONE
            view.findViewById<View>(R.id.label_parent_account).visibility = View.GONE
        } else {
            view.findViewById<View>(R.id.layout_parent_account).visibility = View.VISIBLE
            view.findViewById<View>(R.id.label_parent_account).visibility = View.VISIBLE
        }
        mParentAccountCursorAdapter = QualifiedAccountNameCursorAdapter(
            activity, mParentAccountCursor
        )
        mParentAccountSpinner!!.adapter = mParentAccountCursorAdapter
    }

    /**
     * Returns a comma separated list of account types which can be parent accounts for the specified `type`.
     * The strings in the list are the [org.gnucash.android.model.AccountType.name]s of the different types.
     * @param type [org.gnucash.android.model.AccountType]
     * @return String comma separated list of account types
     */
    private fun getAllowedParentAccountTypes(type: AccountType): String {
        return when (type) {
            AccountType.EQUITY -> "'" + AccountType.EQUITY.name + "'"
            AccountType.INCOME, AccountType.EXPENSE -> "'" + AccountType.EXPENSE.name + "', '" + AccountType.INCOME.name + "'"
            AccountType.CASH, AccountType.BANK, AccountType.CREDIT, AccountType.ASSET, AccountType.LIABILITY, AccountType.PAYABLE, AccountType.RECEIVABLE, AccountType.CURRENCY, AccountType.STOCK, AccountType.MUTUAL -> {
                val accountTypeStrings = accountTypeStringList
                accountTypeStrings.remove(AccountType.EQUITY.name)
                accountTypeStrings.remove(AccountType.EXPENSE.name)
                accountTypeStrings.remove(AccountType.INCOME.name)
                accountTypeStrings.remove(AccountType.ROOT.name)
                "'" + TextUtils.join("','", accountTypeStrings) + "'"
            }

            AccountType.TRADING -> "'" + AccountType.TRADING.name + "'"
            AccountType.ROOT -> AccountType.values().contentToString()
                .replace("\\[|]".toRegex(), "")

//            else -> Arrays.toString(AccountType.values()).replace("\\[|]".toRegex(), "")  redundant by XJ
        }
    }

    /**
     * Returns a list of all the available [org.gnucash.android.model.AccountType]s as strings
     * @return String list of all account types
     */
    private val accountTypeStringList: MutableList<String?>
        get() {
            val accountTypes = AccountType.values().contentToString().replace("\\[|]".toRegex(), "").split(",".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
            val accountTypesList: MutableList<String?> = ArrayList()
            for (accountType in accountTypes) {
                accountTypesList.add(accountType.trim { it <= ' ' })
            }
            return accountTypesList
        }

    /**
     * Loads the list of account types into the account type selector spinner
     */
    private fun loadAccountTypesList() {
        val accountTypes = resources.getStringArray(R.array.account_type_entry_values)
        val accountTypesAdapter = ArrayAdapter(
            activity!!, android.R.layout.simple_list_item_1, accountTypes
        )
        accountTypesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mAccountTypeSpinner!!.adapter = accountTypesAdapter
    }

    /**
     * Finishes the fragment appropriately.
     * Depends on how the fragment was loaded, it might have a backstack or not
     */
    private fun finishFragment() {
        val imm = activity!!.getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager
        imm.hideSoftInputFromWindow(mNameEditText!!.windowToken, 0)
        val action = activity!!.intent.action
        if (action != null && action == Intent.ACTION_INSERT_OR_EDIT) {
            activity!!.setResult(Activity.RESULT_OK)
            activity!!.finish()
        } else {
            activity!!.supportFragmentManager.popBackStack()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mParentAccountCursor != null) mParentAccountCursor!!.close()
        if (mDefaultTransferAccountCursorAdapter != null) {
            mDefaultTransferAccountCursorAdapter!!.cursor.close()
        }
    }

    /**
     * Reads the fields from the account form and saves as a new account
     */
    private fun saveAccount() {
        Log.i("AccountFormFragment", "Saving account")
        if (mAccountsDbAdapter == null) mAccountsDbAdapter = AccountsDbAdapter.instance
        // accounts to update, in case we're updating full names of a sub account tree
        val accountsToUpdate = ArrayList<Account>()
        var nameChanged = false
        if (mAccount == null) {
            val name = enteredName
            if (name.isEmpty()) {
                mTextInputLayout!!.isErrorEnabled = true
                mTextInputLayout!!.error = getString(R.string.toast_no_account_name_entered)
                return
            }
            mAccount = Account(enteredName)
            mAccountsDbAdapter!!.addRecord(mAccount!!, DatabaseAdapter.UpdateMethod.insert) //new account, insert it
        } else {
            nameChanged = mAccount!!.mName != enteredName
            mAccount!!.setMName(enteredName)
        }
        val commodityId = mCurrencySpinner!!.selectedItemId
        val commodity = CommoditiesDbAdapter.instance.getRecord(commodityId)
        mAccount!!.setMCommodity(commodity)
        val selectedAccountType = selectedAccountType
        mAccount!!.mAccountType = selectedAccountType
        mAccount!!.mDescription = mDescriptionEditText!!.text.toString()
        mAccount!!.setMIsPlaceHolderAccount(mPlaceholderCheckBox!!.isChecked)
        mAccount!!.setMColor(mSelectedColor)
        val newParentAccountId: Long
        val newParentAccountUID: String?
        if (mParentCheckBox!!.isChecked) {
            newParentAccountId = mParentAccountSpinner!!.selectedItemId
            newParentAccountUID = mAccountsDbAdapter!!.getUID(newParentAccountId)
            mAccount!!.mParentAccountUID = newParentAccountUID
        } else {
            //need to do this explicitly in case user removes parent account
            newParentAccountUID = mRootAccountUID
            newParentAccountId = mRootAccountId
        }
        mAccount!!.mParentAccountUID = newParentAccountUID
        if (mDefaultTransferAccountCheckBox!!.isChecked
            && mDefaultTransferAccountSpinner!!.selectedItemId != Spinner.INVALID_ROW_ID
        ) {
            val id = mDefaultTransferAccountSpinner!!.selectedItemId
            mAccount!!.mDefaultTransferAccountUID = mAccountsDbAdapter!!.getUID(id)
        } else {
            //explicitly set in case of removal of default account
            mAccount!!.mDefaultTransferAccountUID = null
        }
        val parentAccountId = if (mParentAccountUID == null) -1 else mAccountsDbAdapter!!.getID(
            mParentAccountUID!!
        )
        // update full names
        if (nameChanged || mDescendantAccountUIDs == null || newParentAccountId != parentAccountId) {
            // current account name changed or new Account or parent account changed
            val newAccountFullName: String? = if (newParentAccountId == mRootAccountId) {
                mAccount!!.mName
            } else {
                mAccountsDbAdapter!!.getAccountFullName(newParentAccountUID!!) +
                        AccountsDbAdapter.ACCOUNT_NAME_SEPARATOR + mAccount!!.mName
            }
            mAccount!!.mFullName = newAccountFullName
            if (mDescendantAccountUIDs != null) {
                // modifying existing account, e.t. name changed and/or parent changed
                if ((nameChanged || parentAccountId != newParentAccountId) && mDescendantAccountUIDs!!.isNotEmpty()) {
                    // parent change, update all full names of descent accounts
                    accountsToUpdate.addAll(
                        mAccountsDbAdapter!!.getSimpleAccountList(
                            DatabaseSchema.AccountEntry.COLUMN_UID + " IN ('" +
                                    TextUtils.join("','", mDescendantAccountUIDs!!) + "')", null, null
                        )
                    )
                }
                val mapAccount = HashMap<String?, Account>()
                for (acct in accountsToUpdate) mapAccount[acct.mUID] = acct
                for (uid in mDescendantAccountUIDs!!) {
                    // mAccountsDbAdapter.getDescendantAccountUIDs() will ensure a parent-child order
                    val acct = mapAccount[uid]
                    // mAccount cannot be root, so acct here cannot be top level account.
                    if (mAccount!!.mUID == acct!!.mParentAccountUID) {
                        acct.mFullName = mAccount!!.mFullName + AccountsDbAdapter.ACCOUNT_NAME_SEPARATOR + acct.mName
                    } else {
                        acct.mFullName = mapAccount[acct.mParentAccountUID]!!.mFullName +
                                AccountsDbAdapter.ACCOUNT_NAME_SEPARATOR + acct.mName
                    }
                }
            }
        }
        accountsToUpdate.add(mAccount!!)

        // bulk update, will not update transactions
        mAccountsDbAdapter!!.bulkAddRecords(accountsToUpdate, DatabaseAdapter.UpdateMethod.update)
        finishFragment()
    }

    /**
     * Returns the currently selected account type in the spinner
     * @return [org.gnucash.android.model.AccountType] currently selected
     */
    private val selectedAccountType: AccountType
        get() {
            val selectedAccountTypeIndex = mAccountTypeSpinner!!.selectedItemPosition
            val accountTypeEntries = resources.getStringArray(R.array.key_account_type_entries)
            return AccountType.valueOf(accountTypeEntries[selectedAccountTypeIndex])
        }

    /**
     * Retrieves the name of the account which has been entered in the EditText
     * @return Name of the account which has been entered in the EditText
     */
    private val enteredName: String
        get() = mNameEditText!!.text.toString().trim { it <= ' ' }

    companion object {
        /**
         * Tag for the color picker dialog fragment
         */
        private const val COLOR_PICKER_DIALOG_TAG = "color_picker_dialog"

        /**
         * Construct a new instance of the dialog
         * @return New instance of the dialog fragment
         */
        fun newInstance(): AccountFormFragment {
            val f = AccountFormFragment()
            f.mAccountsDbAdapter = AccountsDbAdapter.instance
            return f
        }
    }
}