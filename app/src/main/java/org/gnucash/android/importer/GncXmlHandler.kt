/*
 * Copyright (c) 2013 - 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 - 2015 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.importer

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.crashlytics.android.Crashlytics
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.adapter.*
import org.gnucash.android.export.xml.GncXmlHelper
import org.gnucash.android.export.xml.GncXmlHelper.parseDate
import org.gnucash.android.export.xml.GncXmlHelper.parseSplitAmount
import org.gnucash.android.model.*
import org.gnucash.android.model.BaseModel.Companion.generateUID
import org.gnucash.android.model.Money.Companion.sDefaultZero
import org.gnucash.android.model.ScheduledAction.Companion.parseScheduledAction
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.lang.Boolean
import java.math.BigDecimal
import java.sql.Timestamp
import java.text.ParseException
import java.util.*
import java.util.regex.Pattern
import kotlin.CharArray
import kotlin.Deprecated
import kotlin.Exception
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.Long
import kotlin.NumberFormatException
import kotlin.String
import kotlin.Throws
import kotlin.also
import kotlin.toString

/**
 * Handler for parsing the GnuCash XML file.
 * The discovered accounts and transactions are automatically added to the database
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Yongxin Wang <fefe.wyx></fefe.wyx>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class GncXmlHandler : DefaultHandler() {
    /**
     * Adapter for saving the imported accounts
     */
    var mAccountsDbAdapter: AccountsDbAdapter? = null

    /**
     * StringBuilder for accumulating characters between XML tags
     */
    var mContent: StringBuilder? = null

    /**
     * Reference to account which is built when each account tag is parsed in the XML file
     */
    var mAccount: Account? = null

    /**
     * All the accounts found in a file to be imported, used for bulk import mode
     */
    var mAccountList: MutableList<Account>? = null

    /**
     * List of all the template accounts found
     */
    var mTemplatAccountList: List<Account>? = null

    /**
     * Map of the tempate accounts to the template transactions UIDs
     */
    var mTemplateAccountToTransactionMap: MutableMap<String, String>? = null

    /**
     * Account map for quick referencing from UID
     */
    var mAccountMap: HashMap<String, Account>? = null

    /**
     * ROOT account of the imported book
     */
    var mRootAccount: Account? = null

    /**
     * Transaction instance which will be built for each transaction found
     */
    var mTransaction: Transaction? = null

    /**
     * All the transaction instances found in a file to be inserted, used in bulk mode
     */
    var mTransactionList: MutableList<Transaction>? = null

    /**
     * All the template transactions found during parsing of the XML
     */
    var mTemplateTransactions: MutableList<Transaction>? = null

    /**
     * Accumulate attributes of splits found in this object
     */
    var mSplit: Split? = null

    /**
     * (Absolute) quantity of the split, which uses split account currency
     */
    var mQuantity: BigDecimal? = null

    /**
     * (Absolute) value of the split, which uses transaction currency
     */
    var mValue: BigDecimal? = null

    /**
     * price table entry
     */
    var mPrice: Price? = null
    var mPriceCommodity = false
    var mPriceCurrency = false
    var mPriceList: MutableList<Price>? = null

    /**
     * Whether the quantity is negative
     */
    var mNegativeQuantity = false

    /**
     * The list for all added split for autobalancing
     */
    var mAutoBalanceSplits: MutableList<Split>? = null

    /**
     * Ignore certain elements in GnuCash XML file, such as "<gnc:template-transactions>"
    </gnc:template-transactions> */
    var mIgnoreElement: String? = null

    /**
     * [ScheduledAction] instance for each scheduled action parsed
     */
    var mScheduledAction: ScheduledAction? = null

    /**
     * List of scheduled actions to be bulk inserted
     */
    var mScheduledActionsList: MutableList<ScheduledAction>? = null

    /**
     * List of budgets which have been parsed from XML
     */
    var mBudgetList: MutableList<Budget>? = null
    var mBudget: Budget? = null
    var mRecurrence: Recurrence? = null
    var mBudgetAmount: BudgetAmount? = null
    var mInColorSlot = false
    var mInPlaceHolderSlot = false
    var mInFavoriteSlot = false
    var mISO4217Currency = false
    var mIsDatePosted = false
    var mIsDateEntered = false
    var mIsNote = false
    var mInDefaultTransferAccount = false
    var mInExported = false
    var mInTemplates = false
    var mInSplitAccountSlot = false
    var mInCreditNumericSlot = false
    var mInDebitNumericSlot = false
    var mIsScheduledStart = false
    var mIsScheduledEnd = false
    var mIsLastRun = false
    var mIsRecurrenceStart = false
    var mInBudgetSlot = false

    /**
     * Saves the attribute of the slot tag
     * Used for determining where we are in the budget amounts
     */
    var mSlotTagAttribute: String? = null
    var mBudgetAmountAccountUID: String? = null

    /**
     * Multiplier for the recurrence period type. e.g. period type of week and multiplier of 2 means bi-weekly
     */
    var mRecurrenceMultiplier = 1

    /**
     * Flag which says to ignore template transactions until we successfully parse a split amount
     * Is updated for each transaction template split parsed
     */
    var mIgnoreTemplateTransaction = true

    /**
     * Flag which notifies the handler to ignore a scheduled action because some error occurred during parsing
     */
    var mIgnoreScheduledAction = false

    /**
     * Used for parsing old backup files where recurrence was saved inside the transaction.
     * Newer backup files will not require this
     */
    @Deprecated("Use the new scheduled action elements instead")
    private var mRecurrencePeriod: Long = 0
    private var mTransactionsDbAdapter: TransactionsDbAdapter? = null
    private var mScheduledActionsDbAdapter: ScheduledActionDbAdapter? = null
    private var mCommoditiesDbAdapter: CommoditiesDbAdapter? = null
    private var mPricesDbAdapter: PricesDbAdapter? = null
    private var mCurrencyCount: MutableMap<String, Int>? = null
    private var mBudgetsDbAdapter: BudgetsDbAdapter? = null
    private var mBook: Book? = null
    private var mainDb: SQLiteDatabase? = null

    /**
     * Creates a handler for handling XML stream events when parsing the XML backup file
     */
    init {
        init()
    }

    /**
     * Initialize the GnuCash XML handler
     */
    private fun init() {
        mBook = Book()
        val databaseHelper = DatabaseHelper(GnuCashApplication.appContext, mBook!!.mUID)
        mainDb = databaseHelper.writableDatabase
        mTransactionsDbAdapter = TransactionsDbAdapter(mainDb, SplitsDbAdapter(mainDb))
        mAccountsDbAdapter = AccountsDbAdapter(mainDb!!, mTransactionsDbAdapter!!)
        val recurrenceDbAdapter = RecurrenceDbAdapter(mainDb)
        mScheduledActionsDbAdapter = ScheduledActionDbAdapter(mainDb, recurrenceDbAdapter)
        mCommoditiesDbAdapter = CommoditiesDbAdapter(mainDb)
        mPricesDbAdapter = PricesDbAdapter(mainDb)
        mBudgetsDbAdapter = BudgetsDbAdapter(mainDb, BudgetAmountsDbAdapter(mainDb), recurrenceDbAdapter)
        mContent = StringBuilder()
        mAccountList = ArrayList()
        mAccountMap = HashMap()
        mTransactionList = ArrayList()
        mScheduledActionsList = ArrayList()
        mBudgetList = ArrayList()
        mTemplatAccountList = ArrayList()
        mTemplateTransactions = ArrayList()
        mTemplateAccountToTransactionMap = HashMap()
        mAutoBalanceSplits = ArrayList()
        mPriceList = ArrayList()
        mCurrencyCount = HashMap()
    }

    @Throws(SAXException::class)
    override fun startElement(
        uri: String, localName: String,
        qualifiedName: String, attributes: Attributes
    ) {
        when (qualifiedName) {
            GncXmlHelper.TAG_ACCOUNT -> {
                mAccount = Account("") // dummy name, will be replaced when we find name tag
                mISO4217Currency = false
            }

            GncXmlHelper.TAG_TRANSACTION -> {
                mTransaction = Transaction("") // dummy name will be replaced
                mTransaction!!.mIsExported = true // default to exported when import transactions
                mISO4217Currency = false
            }

            GncXmlHelper.TAG_TRN_SPLIT -> mSplit = Split(sDefaultZero!!, "")
            GncXmlHelper.TAG_DATE_POSTED -> mIsDatePosted = true
            GncXmlHelper.TAG_DATE_ENTERED -> mIsDateEntered = true
            GncXmlHelper.TAG_TEMPLATE_TRANSACTIONS -> mInTemplates = true
            GncXmlHelper.TAG_SCHEDULED_ACTION ->                 //default to transaction type, will be changed during parsing
                mScheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)

            GncXmlHelper.TAG_SX_START -> mIsScheduledStart = true
            GncXmlHelper.TAG_SX_END -> mIsScheduledEnd = true
            GncXmlHelper.TAG_SX_LAST -> mIsLastRun = true
            GncXmlHelper.TAG_RX_START -> mIsRecurrenceStart = true
            GncXmlHelper.TAG_PRICE -> mPrice = Price()
            GncXmlHelper.TAG_PRICE_CURRENCY -> {
                mPriceCurrency = true
                mPriceCommodity = false
                mISO4217Currency = false
            }

            GncXmlHelper.TAG_PRICE_COMMODITY -> {
                mPriceCurrency = false
                mPriceCommodity = true
                mISO4217Currency = false
            }

            GncXmlHelper.TAG_BUDGET -> mBudget = Budget()
            GncXmlHelper.TAG_GNC_RECURRENCE, GncXmlHelper.TAG_BUDGET_RECURRENCE -> {
                mRecurrenceMultiplier = 1
                mRecurrence = Recurrence(PeriodType.MONTH)
            }

            GncXmlHelper.TAG_BUDGET_SLOTS -> mInBudgetSlot = true
            GncXmlHelper.TAG_SLOT -> if (mInBudgetSlot) {
                mBudgetAmount = BudgetAmount(mBudget!!.mUID, mBudgetAmountAccountUID)
            }

            GncXmlHelper.TAG_SLOT_VALUE -> mSlotTagAttribute = attributes.getValue(GncXmlHelper.ATTR_KEY_TYPE)
        }
    }

    @Throws(SAXException::class)
    override fun endElement(uri: String, localName: String, qualifiedName: String) {
        // FIXME: 22.10.2015 First parse the number of accounts/transactions and use the numer to init the array lists
        val characterString = mContent.toString().trim { it <= ' ' }
        if (mIgnoreElement != null) {
            // Ignore everything inside
            if (qualifiedName == mIgnoreElement) {
                mIgnoreElement = null
            }
            mContent!!.setLength(0)
            return
        }
        when (qualifiedName) {
            GncXmlHelper.TAG_ACCT_NAME -> {
                mAccount!!.setMName(characterString)
                mAccount!!.mFullName = characterString
            }

            GncXmlHelper.TAG_ACCT_ID -> mAccount!!.mUID = characterString
            GncXmlHelper.TAG_ACCT_TYPE -> {
                val accountType = AccountType.valueOf(characterString)
                mAccount!!.mAccountType = accountType
                mAccount!!.setMIsHidden(accountType === AccountType.ROOT) //flag root account as hidden
            }

            GncXmlHelper.TAG_COMMODITY_SPACE -> if (characterString == "ISO4217" || characterString == "CURRENCY") {
                mISO4217Currency = true
            } else {
                // price of non-ISO4217 commodities cannot be handled
                mPrice = null
            }

            GncXmlHelper.TAG_COMMODITY_ID -> {
                val currencyCode = if (mISO4217Currency) characterString else NO_CURRENCY_CODE
                val commodity = mCommoditiesDbAdapter!!.getCommodity(currencyCode)
                if (mAccount != null) {
                    if (commodity != null) {
                        mAccount!!.setMCommodity(commodity)
                    } else {
                        throw SAXException(
                            "Commodity with '" + currencyCode
                                    + "' currency code not found in the database"
                        )
                    }
                    if (mCurrencyCount!!.containsKey(currencyCode)) {
                        mCurrencyCount!![currencyCode] = mCurrencyCount!![currencyCode]!! + 1
                    } else {
                        mCurrencyCount!![currencyCode] = 1
                    }
                }
                if (mTransaction != null) {
                    mTransaction!!.mCommodity = commodity
                }
                if (mPrice != null) {
                    if (mPriceCommodity) {
                        mPrice!!.mCommodityUID = mCommoditiesDbAdapter!!.getCommodityUID(currencyCode)
                        mPriceCommodity = false
                    }
                    if (mPriceCurrency) {
                        mPrice!!.mCurrencyUID = mCommoditiesDbAdapter!!.getCommodityUID(currencyCode)
                        mPriceCurrency = false
                    }
                }
            }

            GncXmlHelper.TAG_ACCT_DESCRIPTION -> mAccount!!.mDescription = characterString
            GncXmlHelper.TAG_PARENT_UID -> mAccount!!.mParentAccountUID = characterString
            GncXmlHelper.TAG_ACCOUNT -> if (!mInTemplates) { //we ignore template accounts, we have no use for them. FIXME someday and import the templates too
                mAccountList!!.add(mAccount!!)
                mAccountMap!![mAccount!!.mUID!!] = mAccount!!
                // check ROOT account
                if (mAccount!!.mAccountType === AccountType.ROOT) {
                    mRootAccount = if (mRootAccount == null) {
                        mAccount
                    } else {
                        throw SAXException("Multiple ROOT accounts exist in book")
                    }
                }
                // prepare for next input
                mAccount = null
                //reset ISO 4217 flag for next account
                mISO4217Currency = false
            }

            GncXmlHelper.TAG_SLOT -> {}
            GncXmlHelper.TAG_SLOT_KEY -> {
                when (characterString) {
                    GncXmlHelper.KEY_PLACEHOLDER -> mInPlaceHolderSlot = true
                    GncXmlHelper.KEY_COLOR -> mInColorSlot = true
                    GncXmlHelper.KEY_FAVORITE -> mInFavoriteSlot = true
                    GncXmlHelper.KEY_NOTES -> mIsNote = true
                    GncXmlHelper.KEY_DEFAULT_TRANSFER_ACCOUNT -> mInDefaultTransferAccount = true
                    GncXmlHelper.KEY_EXPORTED -> mInExported = true
                    GncXmlHelper.KEY_SPLIT_ACCOUNT_SLOT -> mInSplitAccountSlot = true
                    GncXmlHelper.KEY_CREDIT_NUMERIC -> mInCreditNumericSlot = true
                    GncXmlHelper.KEY_DEBIT_NUMERIC -> mInDebitNumericSlot = true
                }
                if (mInBudgetSlot && mBudgetAmountAccountUID == null) {
                    mBudgetAmountAccountUID = characterString
                    mBudgetAmount!!.mAccountUID = characterString
                } else if (mInBudgetSlot) {
                    mBudgetAmount!!.mPeriodNum = characterString.toLong()
                }
            }

            GncXmlHelper.TAG_SLOT_VALUE -> if (mInPlaceHolderSlot) {
                //Log.v(LOG_TAG, "Setting account placeholder flag");
                mAccount!!.setMIsPlaceHolderAccount(Boolean.parseBoolean(characterString))
                mInPlaceHolderSlot = false
            } else if (mInColorSlot) {
                //Log.d(LOG_TAG, "Parsing color code: " + characterString);
                var color = characterString.trim { it <= ' ' }
                //Gnucash exports the account color in format #rrrgggbbb, but we need only #rrggbb.
                //so we trim the last digit in each block, doesn't affect the color much
                if (color != "Not Set") {
                    // avoid known exception, printStackTrace is very time consuming
                    if (!Pattern.matches(ACCOUNT_COLOR_HEX_REGEX, color)) color =
                        "#" + color.replace(".(.)?".toRegex(), "$1").replace("null", "")
                    try {
                        if (mAccount != null) mAccount!!.setMColor(color)
                    } catch (ex: IllegalArgumentException) {
                        //sometimes the color entry in the account file is "Not set" instead of just blank. So catch!
                        Log.e(LOG_TAG, "Invalid color code '" + color + "' for account " + mAccount!!.mName)
                        Crashlytics.logException(ex)
                    }
                }
                mInColorSlot = false
            } else if (mInFavoriteSlot) {
                mAccount!!.setMIsFavorite(Boolean.parseBoolean(characterString))
                mInFavoriteSlot = false
            } else if (mIsNote) {
                if (mTransaction != null) {
                    mTransaction!!.mNotes = characterString
                    mIsNote = false
                }
            } else if (mInDefaultTransferAccount) {
                mAccount!!.mDefaultTransferAccountUID = characterString
                mInDefaultTransferAccount = false
            } else if (mInExported) {
                if (mTransaction != null) {
                    mTransaction!!.mIsExported = Boolean.parseBoolean(characterString)
                    mInExported = false
                }
            } else if (mInTemplates && mInSplitAccountSlot) {
                mSplit!!.mAccountUID = characterString
                mInSplitAccountSlot = false
            } else if (mInTemplates && mInCreditNumericSlot) {
                handleEndOfTemplateNumericSlot(characterString, TransactionType.CREDIT)
            } else if (mInTemplates && mInDebitNumericSlot) {
                handleEndOfTemplateNumericSlot(characterString, TransactionType.DEBIT)
            } else if (mInBudgetSlot) {
                if (mSlotTagAttribute == GncXmlHelper.ATTR_VALUE_NUMERIC) {
                    try {
                        val bigDecimal = parseSplitAmount(characterString)
                        //currency doesn't matter since we don't persist it in the budgets table
                        mBudgetAmount!!.setMAmount(Money(bigDecimal, Commodity.DEFAULT_COMMODITY))
                    } catch (e: ParseException) {
                        mBudgetAmount!!.setMAmount(sDefaultZero!!) //just put zero, in case it was a formula we couldnt parse
                        e.printStackTrace()
                    } finally {
                        mBudget!!.addBudgetAmount(mBudgetAmount!!)
                    }
                    mSlotTagAttribute = GncXmlHelper.ATTR_VALUE_FRAME
                } else {
                    mBudgetAmountAccountUID = null
                }
            }

            GncXmlHelper.TAG_BUDGET_SLOTS -> mInBudgetSlot = false
            GncXmlHelper.TAG_TRX_ID -> mTransaction!!.mUID = characterString
            GncXmlHelper.TAG_TRN_DESCRIPTION -> mTransaction!!.setMDescription(characterString)
            GncXmlHelper.TAG_TS_DATE -> try {
                if (mIsDatePosted && mTransaction != null) {
                    mTransaction!!.setMTimestamp(parseDate(characterString))
                    mIsDatePosted = false
                }
                if (mIsDateEntered && mTransaction != null) {
                    val timestamp = Timestamp(parseDate(characterString))
                    mTransaction!!.mCreatedTimestamp = timestamp
                    mIsDateEntered = false
                }
                if (mPrice != null) {
                    mPrice!!.mDate = Timestamp(parseDate(characterString))
                }
            } catch (e: ParseException) {
                Crashlytics.logException(e)
                val message = "Unable to parse transaction time - $characterString"
                Log.e(
                    LOG_TAG, """
     $message
     ${e.message}
     """.trimIndent()
                )
                Crashlytics.log(message)
                throw SAXException(message, e)
            }

            GncXmlHelper.TAG_RECURRENCE_PERIOD -> {
                mRecurrencePeriod = characterString.toLong()
                mTransaction!!.mIsTemplate = mRecurrencePeriod > 0
            }

            GncXmlHelper.TAG_SPLIT_ID -> mSplit!!.mUID = characterString
            GncXmlHelper.TAG_SPLIT_MEMO -> mSplit!!.mMemo = characterString
            GncXmlHelper.TAG_SPLIT_VALUE -> try {
                // The value and quantity can have different sign for custom currency(stock).
                // Use the sign of value for split, as it would not be custom currency
                var q = characterString
                if (q[0] == '-') {
                    mNegativeQuantity = true
                    q = q.substring(1)
                } else {
                    mNegativeQuantity = false
                }
                mValue = parseSplitAmount(characterString).abs() // use sign from quantity
            } catch (e: ParseException) {
                val msg = "Error parsing split quantity - $characterString"
                Crashlytics.log(msg)
                Crashlytics.logException(e)
                throw SAXException(msg, e)
            }

            GncXmlHelper.TAG_SPLIT_QUANTITY ->                 // delay the assignment of currency when the split account is seen
                mQuantity = try {
                    parseSplitAmount(characterString).abs()
                } catch (e: ParseException) {
                    val msg = "Error parsing split quantity - $characterString"
                    Crashlytics.log(msg)
                    Crashlytics.logException(e)
                    throw SAXException(msg, e)
                }

            GncXmlHelper.TAG_SPLIT_ACCOUNT -> if (!mInTemplates) {
                //this is intentional: GnuCash XML formats split amounts, credits are negative, debits are positive.
                mSplit!!.mSplitType = if (mNegativeQuantity) TransactionType.CREDIT else TransactionType.DEBIT
                //the split amount uses the account currency
                mSplit!!.mQuantity = Money(mQuantity, getCommodityForAccount(characterString))
                //the split value uses the transaction currency
                mSplit!!.setMValue(Money(mValue, mTransaction!!.mCommodity))
                mSplit!!.mAccountUID = characterString
            } else {
                if (!mIgnoreTemplateTransaction) mTemplateAccountToTransactionMap!![characterString] =
                    mTransaction!!.mUID!!
            }

            GncXmlHelper.TAG_TRN_SPLIT -> mTransaction!!.addSplit(mSplit!!)
            GncXmlHelper.TAG_TRANSACTION -> {
                mTransaction!!.mIsTemplate = mInTemplates
                val imbSplit = mTransaction!!.createAutoBalanceSplit()
                if (imbSplit != null) {
                    mAutoBalanceSplits!!.add(imbSplit)
                }
                if (mInTemplates) {
                    if (!mIgnoreTemplateTransaction) mTemplateTransactions!!.add(mTransaction!!)
                } else {
                    mTransactionList!!.add(mTransaction!!)
                }
                if (mRecurrencePeriod > 0) { //if we find an old format recurrence period, parse it
                    mTransaction!!.mIsTemplate = true
                    val scheduledAction = parseScheduledAction(mTransaction!!, mRecurrencePeriod)
                    mScheduledActionsList!!.add(scheduledAction)
                }
                mRecurrencePeriod = 0
                mIgnoreTemplateTransaction = true
                mTransaction = null
            }

            GncXmlHelper.TAG_TEMPLATE_TRANSACTIONS -> mInTemplates = false
            GncXmlHelper.TAG_SX_ID -> mScheduledAction!!.mUID = characterString
            GncXmlHelper.TAG_SX_NAME -> if (characterString == ScheduledAction.ActionType.BACKUP.name) mScheduledAction!!.mActionType =
                ScheduledAction.ActionType.BACKUP else mScheduledAction!!.mActionType =
                ScheduledAction.ActionType.TRANSACTION

            GncXmlHelper.TAG_SX_ENABLED -> mScheduledAction!!.setMIsEnabled(characterString == "y")
            GncXmlHelper.TAG_SX_AUTO_CREATE -> mScheduledAction!!.setMAutoCreate(characterString == "y")
            GncXmlHelper.TAG_SX_NUM_OCCUR -> mScheduledAction!!.mTotalFrequency = characterString.toInt()
            GncXmlHelper.TAG_RX_MULT -> mRecurrenceMultiplier = characterString.toInt()
            GncXmlHelper.TAG_RX_PERIOD_TYPE -> try {
                val periodType = PeriodType.valueOf(characterString.uppercase(Locale.getDefault()))
                mRecurrence!!.mPeriodType = periodType
                mRecurrence!!.mMultiplier = mRecurrenceMultiplier
            } catch (ex: IllegalArgumentException) { //the period type constant is not supported
                val msg = "Unsupported period constant: $characterString"
                Log.e(LOG_TAG, msg)
                Crashlytics.logException(ex)
                mIgnoreScheduledAction = true
            }

            GncXmlHelper.TAG_GDATE -> try {
                val date = GncXmlHelper.DATE_FORMATTER.parse(characterString)!!.time
                if (mIsScheduledStart && mScheduledAction != null) {
                    mScheduledAction!!.mCreatedTimestamp = Timestamp(date)
                    mIsScheduledStart = false
                }
                if (mIsScheduledEnd && mScheduledAction != null) {
                    mScheduledAction!!.setMEndDate(date)
                    mIsScheduledEnd = false
                }
                if (mIsLastRun && mScheduledAction != null) {
                    mScheduledAction!!.mLastRun = date
                    mIsLastRun = false
                }
                if (mIsRecurrenceStart && mScheduledAction != null) {
                    mRecurrence!!.mPeriodStart = Timestamp(date)
                    mIsRecurrenceStart = false
                }
            } catch (e: ParseException) {
                val msg = "Error parsing scheduled action date $characterString"
                Log.e(LOG_TAG, msg + e.message)
                Crashlytics.log(msg)
                Crashlytics.logException(e)
                throw SAXException(msg, e)
            }

            GncXmlHelper.TAG_SX_TEMPL_ACCOUNT -> if (mScheduledAction!!.mActionType === ScheduledAction.ActionType.TRANSACTION) {
                mScheduledAction!!.setMActionUID(mTemplateAccountToTransactionMap!![characterString])
            } else {
                mScheduledAction!!.setMActionUID(generateUID())
            }

            GncXmlHelper.TAG_GNC_RECURRENCE -> if (mScheduledAction != null) {
                mScheduledAction!!.setMRecurrence(mRecurrence!!)
            }

            GncXmlHelper.TAG_SCHEDULED_ACTION -> {
                if (mScheduledAction!!.getMActionUID() != null && !mIgnoreScheduledAction) {
                    if (mScheduledAction!!.mRecurrence!!.mPeriodType === PeriodType.WEEK) {
                        // TODO: implement parsing of by days for scheduled actions
                        setMinimalScheduledActionByDays()
                    }
                    mScheduledActionsList!!.add(mScheduledAction!!)
                    val count = generateMissedScheduledTransactions(mScheduledAction)
                    Log.i(LOG_TAG, String.format("Generated %d transactions from scheduled action", count))
                }
                mIgnoreScheduledAction = false
            }

            GncXmlHelper.TAG_PRICE_ID -> mPrice!!.mUID = characterString
            GncXmlHelper.TAG_PRICE_SOURCE -> if (mPrice != null) {
                mPrice!!.mSource = characterString
            }

            GncXmlHelper.TAG_PRICE_VALUE -> if (mPrice != null) {
                val parts = characterString.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (parts.size != 2) {
                    val message = "Illegal price - $characterString"
                    Log.e(LOG_TAG, message)
                    Crashlytics.log(message)
                    throw SAXException(message)
                } else {
                    mPrice!!.setMValueNum(java.lang.Long.valueOf(parts[0]))
                    mPrice!!.setMValueDenom(java.lang.Long.valueOf(parts[1]))
                    Log.d(
                        javaClass.name, "price " + characterString +
                                " .. " + mPrice!!.getMValueNum() + "/" + mPrice!!.getMValueDenom()
                    )
                }
            }

            GncXmlHelper.TAG_PRICE_TYPE -> if (mPrice != null) {
                mPrice!!.mType = characterString
            }

            GncXmlHelper.TAG_PRICE -> if (mPrice != null) {
                mPriceList!!.add(mPrice!!)
                mPrice = null
            }

            GncXmlHelper.TAG_BUDGET -> if (mBudget!!.getMBudgetAmounts().isNotEmpty()) //ignore if no budget amounts exist for the budget
                mBudgetList!!.add(mBudget!!)

            GncXmlHelper.TAG_BUDGET_NAME -> mBudget!!.setMName(characterString)
            GncXmlHelper.TAG_BUDGET_DESCRIPTION -> mBudget!!.mDescription = characterString
            GncXmlHelper.TAG_BUDGET_NUM_PERIODS -> mBudget!!.mNumberOfPeriods = characterString.toLong()
            GncXmlHelper.TAG_BUDGET_RECURRENCE -> mBudget!!.setMRecurrence(mRecurrence!!)
        }

        //reset the accumulated characters
        mContent!!.setLength(0)
    }

    @Throws(SAXException::class)
    override fun characters(chars: CharArray, start: Int, length: Int) {
        mContent!!.append(chars, start, length)
    }

    @Throws(SAXException::class)
    override fun endDocument() {
        super.endDocument()
        val mapFullName = HashMap<String?, String?>(
            mAccountList!!.size
        )
        val mapImbalanceAccount = HashMap<String?, Account?>()

        // The XML has no ROOT, create one
        if (mRootAccount == null) {
            mRootAccount = Account("ROOT")
            mRootAccount!!.mAccountType = AccountType.ROOT
            mAccountList!!.add(mRootAccount!!)
            mAccountMap!![mRootAccount!!.mUID!!] = mRootAccount!!
        }
        val imbalancePrefix = AccountsDbAdapter.imbalanceAccountPrefix

        // Add all account without a parent to ROOT, and collect top level imbalance accounts
        for (account in mAccountList!!) {
            mapFullName[account.mUID] = null
            var topLevel = false
            if (account.mParentAccountUID == null && account.mAccountType !== AccountType.ROOT) {
                account.mParentAccountUID = mRootAccount!!.mUID
                topLevel = true
            }
            if (topLevel || mRootAccount!!.mUID == account.mParentAccountUID) {
                if (account.mName!!.startsWith(imbalancePrefix)) {
                    mapImbalanceAccount[account.mName!!.substring(imbalancePrefix.length)] = account
                }
            }
        }

        // Set the account for created balancing splits to correct imbalance accounts
        for (split in mAutoBalanceSplits!!) {
            // XXX: yes, getAccountUID() returns a currency code in this case (see Transaction.createAutoBalanceSplit())
            val currencyCode = split.mAccountUID
            var imbAccount = mapImbalanceAccount[currencyCode]
            if (imbAccount == null) {
                imbAccount = Account(imbalancePrefix + currencyCode, mCommoditiesDbAdapter!!.getCommodity(currencyCode!!)!!)
                imbAccount.mParentAccountUID = mRootAccount!!.mUID
                imbAccount.mAccountType = AccountType.BANK
                mapImbalanceAccount[currencyCode] = imbAccount
                mAccountList!!.add(imbAccount)
            }
            split.mAccountUID = imbAccount.mUID
        }
        val stack = Stack<Account?>()
        for (account in mAccountList!!) {
            if (mapFullName[account.mUID] != null) {
                continue
            }
            stack.push(account)
            var parentAccountFullName: String?
            while (!stack.isEmpty()) {
                val acc = stack.peek()
                if (acc!!.mAccountType === AccountType.ROOT) {
                    // ROOT_ACCOUNT_FULL_NAME should ensure ROOT always sorts first
                    mapFullName[acc!!.mUID] = AccountsDbAdapter.ROOT_ACCOUNT_FULL_NAME
                    stack.pop()
                    continue
                }
                val parentUID = acc!!.mParentAccountUID
                val parentAccount = mAccountMap!![parentUID]
                // ROOT account will be added if not exist, so now anly ROOT
                // has an empty parent
                if (parentAccount!!.mAccountType === AccountType.ROOT) {
                    // top level account, full name is the same as its name
                    mapFullName[acc.mUID] = acc.mName
                    stack.pop()
                    continue
                }
                parentAccountFullName = mapFullName[parentUID]
                if (parentAccountFullName == null) {
                    // non-top-level account, parent full name still unknown
                    stack.push(parentAccount)
                    continue
                }
                mapFullName[acc.mUID] = parentAccountFullName +
                        AccountsDbAdapter.ACCOUNT_NAME_SEPARATOR + acc.mName
                stack.pop()
            }
        }
        for (account in mAccountList!!) {
            account.mFullName = mapFullName[account.mUID]
        }
        var mostAppearedCurrency = ""
        var mostCurrencyAppearance = 0
        for ((key, value) in mCurrencyCount!!) {
            if (value > mostCurrencyAppearance) {
                mostCurrencyAppearance = value
                mostAppearedCurrency = key
            }
        }
        if (mostCurrencyAppearance > 0) {
            GnuCashApplication.setDefaultCurrencyCode(mostAppearedCurrency)
        }
        saveToDatabase()
    }

    /**
     * Saves the imported data to the database
     * @return GUID of the newly created book, or null if not successful
     */
    private fun saveToDatabase() {
        val booksDbAdapter = BooksDbAdapter.instance
        mBook!!.mRootAccountUID = mRootAccount!!.mUID
        mBook!!.mDisplayName = booksDbAdapter.generateDefaultBookName()
        //we on purpose do not set the book active. Only import. Caller should handle activation
        val startTime = System.nanoTime()
        mAccountsDbAdapter!!.beginTransaction()
        Log.d(javaClass.simpleName, "bulk insert starts")
        try {
            // disable foreign key. The database structure should be ensured by the data inserted.
            // it will make insertion much faster.
            mAccountsDbAdapter!!.enableForeignKey(false)
            Log.d(javaClass.simpleName, "before clean up db")
            mAccountsDbAdapter!!.deleteAllRecords()
            Log.d(javaClass.simpleName, String.format("deb clean up done %d ns", System.nanoTime() - startTime))
            val nAccounts = mAccountsDbAdapter!!.bulkAddRecords(mAccountList!!.toList(), DatabaseAdapter.UpdateMethod.insert)
            Log.d("Handler:", String.format("%d accounts inserted", nAccounts))
            //We need to add scheduled actions first because there is a foreign key constraint on transactions
            //which are generated from scheduled actions (we do auto-create some transactions during import)
            val nSchedActions = mScheduledActionsDbAdapter!!.bulkAddRecords(
                mScheduledActionsList!!,
                DatabaseAdapter.UpdateMethod.insert
            )
            Log.d("Handler:", String.format("%d scheduled actions inserted", nSchedActions))
            val nTempTransactions =
                mTransactionsDbAdapter!!.bulkAddRecords(mTemplateTransactions!!, DatabaseAdapter.UpdateMethod.insert)
            Log.d("Handler:", String.format("%d template transactions inserted", nTempTransactions))
            val nTransactions =
                mTransactionsDbAdapter!!.bulkAddRecords(mTransactionList!!.toMutableList(), DatabaseAdapter.UpdateMethod.insert)
            Log.d("Handler:", String.format("%d transactions inserted", nTransactions))
            val nPrices = mPricesDbAdapter!!.bulkAddRecords(mPriceList!!, DatabaseAdapter.UpdateMethod.insert)
            Log.d(javaClass.simpleName, String.format("%d prices inserted", nPrices))

            //// TODO: 01.06.2016 Re-enable import of Budget stuff when the UI is complete
//            long nBudgets = mBudgetsDbAdapter.bulkAddRecords(mBudgetList, DatabaseAdapter.UpdateMethod.insert);
//            Log.d(getClass().getSimpleName(), String.format("%d budgets inserted", nBudgets));
            val endTime = System.nanoTime()
            Log.d(javaClass.simpleName, String.format("bulk insert time: %d", endTime - startTime))

            //if all of the import went smoothly, then add the book to the book db
            booksDbAdapter.addRecord(mBook!!, DatabaseAdapter.UpdateMethod.insert)
            mAccountsDbAdapter!!.setTransactionSuccessful()
        } finally {
            mAccountsDbAdapter!!.enableForeignKey(true)
            mAccountsDbAdapter!!.endTransaction()
            mainDb!!.close() //close it after import
        }
    }

    /**
     * Returns the unique identifier of the just-imported book
     * @return GUID of the newly imported book
     */
    val bookUID: String
        get() = mBook!!.mUID!!

    /**
     * Returns the currency for an account which has been parsed (but not yet saved to the db)
     *
     * This is used when parsing splits to assign the right currencies to the splits
     * @param accountUID GUID of the account
     * @return Commodity of the account
     */
    private fun getCommodityForAccount(accountUID: String?): Commodity {
        return try {
            mAccountMap!![accountUID]!!.getMCommodity()
        } catch (e: Exception) {
            Crashlytics.logException(e)
            Commodity.DEFAULT_COMMODITY
        }
    }

    /**
     * Handles the case when we reach the end of the template numeric slot
     * @param characterString Parsed characters containing split amount
     */
    private fun handleEndOfTemplateNumericSlot(characterString: String, splitType: TransactionType) {
        try {
            // HACK: Check for bug #562. If a value has already been set, ignore the one just read
            if (mSplit!!.mValue!! == Money(BigDecimal.ZERO, mSplit!!.mValue!!.mCommodity)
            ) {
                val amountBigD = parseSplitAmount(characterString)
                val amount = Money(amountBigD, getCommodityForAccount(mSplit!!.mAccountUID))
                mSplit!!.setMValue(amount)
                mSplit!!.mSplitType = splitType
                mIgnoreTemplateTransaction = false //we have successfully parsed an amount
            }
        } catch (e: NumberFormatException) {
            val msg = "Error parsing template credit split amount $characterString"
            Log.e(
                LOG_TAG, """
     $msg
     ${e.message}
     """.trimIndent()
            )
            Crashlytics.log(msg)
            Crashlytics.logException(e)
        } catch (e: ParseException) {
            val msg = "Error parsing template credit split amount $characterString"
            Log.e(
                LOG_TAG, """
     $msg
     ${e.message}
     """.trimIndent()
            )
            Crashlytics.log(msg)
            Crashlytics.logException(e)
        } finally {
            if (splitType === TransactionType.CREDIT) mInCreditNumericSlot = false else mInDebitNumericSlot = false
        }
    }

    /**
     * Generates the runs of the scheduled action which have been missed since the file was last opened.
     * @param scheduledAction Scheduled action for transaction
     * @return Number of transaction instances generated
     */
    private fun generateMissedScheduledTransactions(scheduledAction: ScheduledAction?): Int {
        //if this scheduled action should not be run for any reason, return immediately
        if ((scheduledAction!!.mActionType !== ScheduledAction.ActionType.TRANSACTION || !scheduledAction!!.isEnabled || !scheduledAction.shouldAutoCreate() || scheduledAction.getMEndDate() > 0) && scheduledAction!!.getMEndDate() > System.currentTimeMillis() || scheduledAction.mTotalFrequency > 0 && scheduledAction.mExecutionCount >= scheduledAction.mTotalFrequency) {
            return 0
        }
        var lastRuntime = scheduledAction.mStartTime
        if (scheduledAction.mLastRun > 0) {
            lastRuntime = scheduledAction.mLastRun
        }
        var generatedTransactionCount = 0
        val period = scheduledAction.period()
        val actionUID = scheduledAction.getMActionUID()
        while (lastRuntime + period.also { lastRuntime = it } <= System.currentTimeMillis()) {
            for (templateTransaction in mTemplateTransactions!!) {
                if (templateTransaction.mUID == actionUID) {
                    val transaction = Transaction(templateTransaction, true)
                    transaction.setMTimestamp(lastRuntime)
                    transaction.mScheduledActionUID = scheduledAction.mUID
                    mTransactionList!!.add(transaction)
                    //autobalance splits are generated with the currency of the transactions as the GUID
                    //so we add them to the mAutoBalanceSplits which will be updated to real GUIDs before saving
                    val autoBalanceSplits = transaction.getMSplitList(transaction.mMnemonic)
                    mAutoBalanceSplits!!.addAll(autoBalanceSplits)
                    scheduledAction.mExecutionCount = scheduledAction.mExecutionCount + 1
                    ++generatedTransactionCount
                    break
                }
            }
        }
        scheduledAction.mLastRun = lastRuntime
        return generatedTransactionCount
    }

    /**
     * Sets the by days of the scheduled action to the day of the week of the start time.
     *
     *
     * Until we implement parsing of days of the week for scheduled actions,
     * this ensures they are executed at least once per week.
     */
    private fun setMinimalScheduledActionByDays() {
        val calendar = Calendar.getInstance()
        calendar.time = Date(mScheduledAction!!.mStartTime)
        mScheduledAction!!.mRecurrence!!.byDays(listOf(calendar[Calendar.DAY_OF_WEEK]))
    }

    companion object {
        /**
         * ISO 4217 currency code for "No Currency"
         */
        private const val NO_CURRENCY_CODE = "XXX"

        /**
         * Tag for logging
         */
        private const val LOG_TAG = "GnuCashAccountImporter"
        /*
        ^             anchor for start of string
        #             the literal #
        (             start of group
        ?:            indicate a non-capturing group that doesn't generate back-references
        [0-9a-fA-F]   hexadecimal digit
        {3}           three times
        )             end of group
        {2}           repeat twice
        $             anchor for end of string
     */
        /**
         * Regular expression for validating color code strings.
         * Accepts #rgb and #rrggbb
         */
        //TODO: Allow use of #aarrggbb format as well
        const val ACCOUNT_COLOR_HEX_REGEX = "^#(?:[0-9a-fA-F]{3}){2}$"
    }
}