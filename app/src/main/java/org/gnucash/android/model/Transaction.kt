/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.model

import android.content.Intent
import org.gnucash.android.BuildConfig
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.export.ofx.OfxHelper
import org.gnucash.android.model.Account.Companion.convertToOfxAccountType
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.*

/**
 * Represents a financial transaction, either credit or debit.
 * Transactions belong to accounts and each have the unique identifier of the account to which they belong.
 * The default type is a debit, unless otherwise specified.
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class Transaction : BaseModel {
    /**
     * GUID of commodity associated with this transaction
     */
    var mCommodity: Commodity? = null

    /**
     * The splits making up this transaction
     */
    private var mSplitList: MutableList<Split> = ArrayList()

    /**
     * Name describing the transaction
     */
    private var mDescription: String? = null
    /**
     * Returns the transaction notes
     * @return String notes of transaction
     */
    /**
     * Add notes to the transaction
     * @param notes String containing notes for the transaction
     */
    /**
     * An extra note giving details about the transaction
     */
    var mNotes: String? = ""
    /**
     * Returns `true` if the transaction has been exported, `false` otherwise
     * @return `true` if the transaction has been exported, `false` otherwise
     */
    /**
     * Sets the exported flag on the transaction
     * @param isExported `true` if the transaction has been exported, `false` otherwise
     */
    /**
     * Flag indicating if this transaction has been exported before or not
     * The transactions are typically exported as bank statement in the OFX format
     */
    var mIsExported = false
    /**
     * Returns the time of transaction in milliseconds
     * @return Time when transaction occurred in milliseconds
     */
    /**
     * Timestamp when this transaction occurred
     */
    var mTimestamp: Long = 0
        private set
    /**
     * Returns `true` if this transaction is a template, `false` otherwise
     * @return `true` if this transaction is a template, `false` otherwise
     */
    /**
     * Sets flag indicating whether this transaction is a template or not
     * @param isTemplate Flag indicating if transaction is a template or not
     */
    /**
     * Flag indicating that this transaction is a template
     */
    var mIsTemplate = false
    /**
     * Returns the GUID of the [org.gnucash.android.model.ScheduledAction] which created this transaction
     * @return GUID of scheduled action
     */
    /**
     * Sets the GUID of the [org.gnucash.android.model.ScheduledAction] which created this transaction
     * @param scheduledActionUID GUID of the scheduled action
     */
    /**
     * GUID of ScheduledAction which created this transaction
     */
    var mScheduledActionUID: String? = null

    /**
     * Overloaded constructor. Creates a new transaction instance with the
     * provided data and initializes the rest to default values.
     * @param name Name of the transaction
     */
    constructor(name: String?) {
        initDefaults()
        setMDescription(name)
    }

    /**
     * Copy constructor.
     * Creates a new transaction object which is a clone of the parameter.
     *
     * **Note:** The unique ID of the transaction is not cloned if the parameter `generateNewUID`,
     * is set to false. Otherwise, a new one is generated.<br></br>
     * The export flag and the template flag are not copied from the old transaction to the new.
     * @param transaction Transaction to be cloned
     * @param generateNewUID Flag to determine if new UID should be assigned or not
     */
    constructor(transaction: Transaction, generateNewUID: Boolean) {
        initDefaults()
        setMDescription(transaction.getMDescription())
        mNotes = transaction.mNotes
        setMTimestamp(transaction.mTimestamp)
        mCommodity = transaction.mCommodity
        //exported flag is left at default value of false
        for (split in transaction.mSplitList) {
            addSplit(Split(split, generateNewUID))
        }
        if (!generateNewUID) {
            mUID = transaction.mUID
        }
    }

    /**
     * Initializes the different fields to their default values.
     */
    private fun initDefaults() {
        mCommodity = Commodity.DEFAULT_COMMODITY
        mTimestamp = System.currentTimeMillis()
    }

    /**
     * Creates a split which will balance the transaction, in value.
     *
     * **Note:**If a transaction has splits with different currencies, no auto-balancing will be performed.
     *
     *
     * The added split will not use any account in db, but will use currency code as account UID.
     * The added split will be returned, to be filled with proper account UID later.
     * @return Split whose amount is the imbalance of this transaction
     */
    fun createAutoBalanceSplit(): Split? {
        val imbalance = imbalance() //returns imbalance of 0 for multicurrency transactions
        if (!imbalance.isAmountZero) {
            // yes, this is on purpose the account UID is set to the currency.
            // This should be overridden before saving to db
            val split = Split(imbalance, mCommodity!!.mMnemonic)
            split.mSplitType = if (imbalance.isNegative) TransactionType.CREDIT else TransactionType.DEBIT
            addSplit(split)
            return split
        }
        return null
    }

    /**
     * Set the GUID of the transaction
     * If the transaction has Splits, their transactionGUID will be updated as well
     * @param uid String unique ID
     */
    override var mUID: String?
        get() = super.mUID
        set(uid) {
            super.mUID = uid
            for (split in mSplitList) {
                split.mTransactionUID = uid
            }
        }

    /**
     * Returns list of splits for this transaction
     * @return [java.util.List] of splits in the transaction
     */
    fun getMSplitList(): List<Split> {
        return mSplitList
    }

    /**
     * Returns the list of splits belonging to a specific account
     * @param accountUID Unique Identifier of the account
     * @return List of [org.gnucash.android.model.Split]s
     */
    fun getMSplitList(accountUID: String): List<Split> {
        val splits: MutableList<Split> = ArrayList()
        for (split in mSplitList) {
            if (split.mAccountUID == accountUID) {
                splits.add(split)
            }
        }
        return splits
    }

    /**
     * Sets the splits for this transaction
     *
     * All the splits in the list will have their transaction UID set to this transaction
     * @param splitList List of splits for this transaction
     */
    fun setMSplitList(splitList: MutableList<Split>) {
        mSplitList = splitList
        for (split in splitList) {
            split.mTransactionUID = mUID
        }
    }

    /**
     * Add a split to the transaction.
     *
     * Sets the split UID and currency to that of this transaction
     * @param split Split for this transaction
     */
    fun addSplit(split: Split) {
        //sets the currency of the split to the currency of the transaction
        split.mTransactionUID = mUID
        mSplitList.add(split)
    }

    /**
     * Returns the balance of this transaction for only those splits which relate to the account.
     *
     * Uses a call to [.getBalance] with the appropriate parameters
     * @param accountUID Unique Identifier of the account
     * @return Money balance of the transaction for the specified account
     * @see .computeBalance
     */
    fun computeBalance(accountUID: String?): Money {
        return computeBalance(accountUID!!, mSplitList)
    }


    /**
     * Computes the imbalance amount for the given transaction.
     * In double entry, all transactions should resolve to zero. But imbalance occurs when there are unresolved splits.
     *
     * **Note:** If this is a multi-currency transaction, an imbalance of zero will be returned
     * @return Money imbalance of the transaction or zero if it is a multi-currency transaction
     */
    private fun imbalance(): Money {
        var imbalance = createZeroInstance(mCommodity!!.mMnemonic)
        for (split in mSplitList) {
            if (split.mQuantity!!.mCommodity!! != mCommodity) {
                // this may happen when importing XML exported from GNCA before 2.0.0
                // these transactions should only be imported from XML exported from GNC desktop
                // so imbalance split should not be generated for them
                return createZeroInstance(mCommodity!!.mMnemonic)
            }
            val amount = split.mValue!!
            imbalance = if (split.mSplitType === TransactionType.DEBIT) imbalance.subtract(amount)
            else imbalance.add(amount)
        }
        return imbalance
    }

    /**
     * Returns the currency code of this transaction.
     * @return ISO 4217 currency code string
     */
    val mMnemonic: String
        get() = mCommodity!!.mMnemonic

    /**
     * Returns the  commodity for this transaction
     * @return Commodity of the transaction
     */
//    fun getMCommodity(): Commodity {
//        return mCommodity!!
//    }

    /**
     * Sets the commodity for this transaction
     * @param commodity Commodity instance
     */
//    fun setMCommodity(commodity: Commodity) {
//        mCommodity = commodity
//    }

    /**
     * Returns the description of the transaction
     * @return Transaction description
     */
    fun getMDescription(): String? {
        return mDescription
    }

    /**
     * Sets the transaction description
     * @param description String description
     */
    fun setMDescription(description: String?) {
        mDescription = description!!.trim { it <= ' ' }
    }

    /**
     * Set the time of the transaction
     * @param timestamp Time when transaction occurred as [Date]
     */
    fun setMTimestamp(timestamp: Date) {
        mTimestamp = timestamp.time
    }

    /**
     * Sets the time when the transaction occurred
     * @param timeInMillis Time in milliseconds
     */
    fun setMTimestamp(timeInMillis: Long) {
        mTimestamp = timeInMillis
    }

    /**
     * Converts transaction to XML DOM corresponding to OFX Statement transaction and
     * returns the element node for the transaction.
     * The Unique ID of the account is needed in order to properly export double entry transactions
     * @param doc XML document to which transaction should be added
     * @param accountUID Unique Identifier of the account which called the method.  @return Element in DOM corresponding to transaction
     */
    fun toOFX(doc: Document, accountUID: String): Element {
        val balance = computeBalance(accountUID)
        val transactionType = if (balance.isNegative) TransactionType.DEBIT else TransactionType.CREDIT
        val transactionNode = doc.createElement(OfxHelper.TAG_STATEMENT_TRANSACTION)
        val typeNode = doc.createElement(OfxHelper.TAG_TRANSACTION_TYPE)
        typeNode.appendChild(doc.createTextNode(transactionType.toString()))
        transactionNode.appendChild(typeNode)
        val datePosted = doc.createElement(OfxHelper.TAG_DATE_POSTED)
        datePosted.appendChild(doc.createTextNode(OfxHelper.getOfxFormattedTime(mTimestamp)))
        transactionNode.appendChild(datePosted)
        val dateUser = doc.createElement(OfxHelper.TAG_DATE_USER)
        dateUser.appendChild(
            doc.createTextNode(
                OfxHelper.getOfxFormattedTime(mTimestamp)
            )
        )
        transactionNode.appendChild(dateUser)
        val amount = doc.createElement(OfxHelper.TAG_TRANSACTION_AMOUNT)
        amount.appendChild(doc.createTextNode(balance.toPlainString()))
        transactionNode.appendChild(amount)
        val transID = doc.createElement(OfxHelper.TAG_TRANSACTION_FITID)
        transID.appendChild(doc.createTextNode(mUID))
        transactionNode.appendChild(transID)
        val name = doc.createElement(OfxHelper.TAG_NAME)
        name.appendChild(doc.createTextNode(mDescription))
        transactionNode.appendChild(name)
        if (mNotes != null && mNotes!!.isNotEmpty()) {
            val memo = doc.createElement(OfxHelper.TAG_MEMO)
            memo.appendChild(doc.createTextNode(mNotes))
            transactionNode.appendChild(memo)
        }
        if (mSplitList.size == 2) { //if we have exactly one other split, then treat it like a transfer
            var transferAccountUID = accountUID
            for (split in mSplitList) {
                if (split.mAccountUID != accountUID) {
                    transferAccountUID = split.mAccountUID!!
                    break
                }
            }
            val bankId = doc.createElement(OfxHelper.TAG_BANK_ID)
            bankId.appendChild(doc.createTextNode(OfxHelper.APP_ID))
            val acctId = doc.createElement(OfxHelper.TAG_ACCOUNT_ID)
            acctId.appendChild(doc.createTextNode(transferAccountUID))
            val accttype = doc.createElement(OfxHelper.TAG_ACCOUNT_TYPE)
            val acctDbAdapter = AccountsDbAdapter.instance
            val ofxAccountType = convertToOfxAccountType(
                acctDbAdapter.getAccountType(
                    transferAccountUID
                )
            )
            accttype.appendChild(doc.createTextNode(ofxAccountType.toString()))
            val bankAccountTo = doc.createElement(OfxHelper.TAG_BANK_ACCOUNT_TO)
            bankAccountTo.appendChild(bankId)
            bankAccountTo.appendChild(acctId)
            bankAccountTo.appendChild(accttype)
            transactionNode.appendChild(bankAccountTo)
        }
        return transactionNode
    }

    companion object {
        /**
         * Mime type for transactions in Gnucash.
         * Used for recording transactions through intents
         */
        const val MIME_TYPE = "vnd.android.cursor.item/vnd." + BuildConfig.APPLICATION_ID + ".transaction"

        /**
         * Key for passing the account unique Identifier as an argument through an [Intent]
         */
        @Deprecated("use {@link Split}s instead")
        const val EXTRA_ACCOUNT_UID = "org.gnucash.android.extra.account_uid"

        /**
         * Key for specifying the double entry account
         */
        @Deprecated("use {@link Split}s instead")
        const val EXTRA_DOUBLE_ACCOUNT_UID = "org.gnucash.android.extra.double_account_uid"

        /**
         * Key for identifying the amount of the transaction through an Intent
         */
        @Deprecated("use {@link Split}s instead")
        const val EXTRA_AMOUNT = "org.gnucash.android.extra.amount"

        /**
         * Extra key for the transaction type.
         * This value should typically be set by calling [TransactionType.name]
         */
        @Deprecated("use {@link Split}s instead")
        const val EXTRA_TRANSACTION_TYPE = "org.gnucash.android.extra.transaction_type"

        /**
         * Argument key for passing splits as comma-separated multi-line list and each line is a split.
         * The line format is: <type>;<amount>;<account_uid>
         * The amount should be formatted in the US Locale
        </account_uid></amount></type> */
        const val EXTRA_SPLITS = "org.gnucash.android.extra.transaction.splits"
        /**
         * Computes the balance of the splits belonging to a particular account.
         *
         * Only those splits which belong to the account will be considered.
         * If the `accountUID` is null, then the imbalance of the transaction is computed. This means that either
         * zero is returned (for balanced transactions) or the imbalance amount will be returned.
         * @param accountUID Unique Identifier of the account
         * @param splitList List of splits
         * @return Money list of splits
         */
        /**
         * Returns the balance of this transaction for only those splits which relate to the account.
         *
         * Uses a call to [.computeBalance] with the appropriate parameters
         * @param accountUID Unique Identifier of the account
         * @return Money balance of the transaction for the specified account
         * @see .computeBalance
         */
        @JvmStatic
        fun computeBalance(accountUID: String, splitList: List<Split>): Money {
            val accountsDbAdapter = AccountsDbAdapter.instance
            val accountType = accountsDbAdapter.getAccountType(accountUID)
            val accountCurrencyCode = accountsDbAdapter.getAccountCurrencyCode(accountUID)
            val isDebitAccount = accountType.hasDebitNormalBalance()
            var balance = createZeroInstance(accountCurrencyCode)
            for (split in splitList) {
                if (split.mAccountUID != accountUID) continue
                val amount: Money = if (split.mValue!!.mCommodity!!.mMnemonic == accountCurrencyCode) {
                    split.mValue!!
                } else {
                    //if this split belongs to the account, then either its value or quantity is in the account currency
                    split.mQuantity!!
                }
                val isDebitSplit = split.mSplitType === TransactionType.DEBIT
                balance = if (isDebitAccount) {
                    if (isDebitSplit) {
                        balance.add(amount)
                    } else {
                        balance.subtract(amount)
                    }
                } else {
                    if (isDebitSplit) {
                        balance.subtract(amount)
                    } else {
                        balance.add(amount)
                    }
                }
            }
            return balance
        }

        /**
         * Returns the corresponding [TransactionType] given the accounttype and the effect which the transaction
         * type should have on the account balance
         * @param accountType Type of account
         * @param shouldReduceBalance `true` if type should reduce balance, `false` otherwise
         * @return TransactionType for the account
         */
        @JvmStatic
        fun typeForBalance(accountType: AccountType, shouldReduceBalance: Boolean): TransactionType {
            val type: TransactionType = if (accountType.hasDebitNormalBalance()) {
                if (shouldReduceBalance) TransactionType.CREDIT else TransactionType.DEBIT
            } else {
                if (shouldReduceBalance) TransactionType.DEBIT else TransactionType.CREDIT
            }
            return type
        }

        /**
         * Returns true if the transaction type represents a decrease for the account balance for the `accountType`, false otherwise
         * @return true if the amount represents a decrease in the account balance, false otherwise
         * @see .typeForBalance
         */
        @JvmStatic
        fun shouldDecreaseBalance(accountType: AccountType, transactionType: TransactionType): Boolean {
            return if (accountType.hasDebitNormalBalance()) {
                transactionType === TransactionType.CREDIT
            } else transactionType === TransactionType.DEBIT
        }

        /**
         * Creates an Intent with arguments from the `transaction`.
         * This intent can be broadcast to create a new transaction
         * @param transaction Transaction used to create intent
         * @return Intent with transaction details as extras
         */
        @JvmStatic
        fun createIntent(transaction: Transaction): Intent {
            val intent = Intent(Intent.ACTION_INSERT)
            intent.type = MIME_TYPE
            intent.putExtra(Intent.EXTRA_TITLE, transaction.getMDescription())
            intent.putExtra(Intent.EXTRA_TEXT, transaction.mNotes)
            intent.putExtra(Account.EXTRA_CURRENCY_CODE, transaction.mMnemonic)
            val stringBuilder = StringBuilder()
            for (split in transaction.getMSplitList()) {
                stringBuilder.append(split.toCsv()).append("\n")
            }
            intent.putExtra(EXTRA_SPLITS, stringBuilder.toString())
            return intent
        }
    }
}