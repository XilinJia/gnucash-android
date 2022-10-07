/*
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

package org.gnucash.android.model

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import org.gnucash.android.db.adapter.AccountsDbAdapter
import java.sql.Timestamp

/**
 * A split amount in a transaction.
 *
 *
 * Every transaction is made up of at least two splits (representing a double
 * entry transaction)
 *
 *
 * Amounts are always stored unsigned. This is independent of the negative values
 * which are shown in the UI (for user convenience). The actual movement of the
 * balance in the account depends on the type of normal balance of the account
 * and the transaction type of the split (CREDIT/DEBIT).
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> []Kotlin code created (Copyright (C) 2022)]
 */
class Split : BaseModel, Parcelable {
    /**
     * Returns the value amount of the split
     * @return Money amount of the split with the currency of the transaction
     * @see .getMQuantity
     */
    /**
     * Amount value of this split which is in the currency of the transaction
     */
    var mValue: Money? = null
        private set

    /**
     * Amount of the split in the currency of the account to which the split belongs
     */
    var mQuantity: Money? = null
    /**
     * Returns transaction GUID to which the split belongs
     * @return String GUID of the transaction
     */
    /**
     * Sets the transaction to which the split belongs
     * @param transactionUID GUID of transaction
     */
    /**
     * Transaction UID which this split belongs to
     */
    var mTransactionUID: String? = ""
    /**
     * Returns the account GUID of this split
     * @return GUID of the account
     */
    /**
     * Sets the GUID of the account of this split
     * @param accountUID GUID of account
     */
    /**
     * Account UID which this split belongs to
     */
    var mAccountUID: String? = null
    /**
     * Returns the type of the split
     * @return [TransactionType] of the split
     */
    /**
     * Sets the type of this split
     * @param splitType Type of the split
     */
    /**
     * The type of this transaction, credit or debit
     */
    var mSplitType: TransactionType? = TransactionType.CREDIT
    /**
     * Returns the memo of this split
     * @return String memo of this split
     */
    /**
     * Sets this split memo
     * @param memo String memo of this split
     */
    /**
     * Memo associated with this split
     */
    var mMemo: String? = null
    /**
     * Return the reconciled state of this split
     *
     *
     * The reconciled state is one of the following values:
     *
     *  * **y**: means this split has been reconciled
     *  * **n**: means this split is not reconciled
     *  * **c**: means split has been cleared, but not reconciled
     *
     *
     *
     * You can check the return value against the reconciled flags
     * [.FLAG_RECONCILED], [.FLAG_NOT_RECONCILED], [.FLAG_CLEARED]
     *
     * @return Character showing reconciled state
     */
    /**
     * Set reconciled state of this split.
     *
     *
     * The reconciled state is one of the following values:
     *
     *  * **y**: means this split has been reconciled
     *  * **n**: means this split is not reconciled
     *  * **c**: means split has been cleared, but not reconciled
     *
     *
     * @param reconcileState One of the following flags [.FLAG_RECONCILED],
     * [.FLAG_NOT_RECONCILED], [.FLAG_CLEARED]
     */
    var mReconcileState = FLAG_NOT_RECONCILED
    /**
     * Return the date of reconciliation
     * @return Timestamp
     */
    /**
     * Set reconciliation date for this split
     * @param reconcileDate Timestamp of reconciliation
     */
    /**
     * Database required non-null field
     */
    var mReconcileDate = Timestamp(System.currentTimeMillis())

    /**
     * Initialize split with a value and quantity amounts and the owning account
     *
     *
     * The transaction type is set to CREDIT. The amounts are stored unsigned.
     *
     * @param value Money value amount of this split in the currency of the transaction.
     * @param quantity Money value amount of this split in the currency of the
     * owning account.
     * @param accountUID String UID of transfer account
     */
    constructor(value: Money, quantity: Money, accountUID: String?) {
        mQuantity = quantity
        setMValue(value)
        mAccountUID = accountUID
    }

    /**
     * Initialize split with a value amount and the owning account
     *
     *
     * The transaction type is set to CREDIT. The amount is stored unsigned.
     *
     * @param amount Money value amount of this split. Value is always in the
     * currency the owning transaction. This amount will be assigned
     * as both the value and the quantity of this split.
     * @param accountUID String UID of owning account
     */
    constructor(amount: Money, accountUID: String?) : this(amount, Money(amount), accountUID)

    /**
     * Clones the `sourceSplit` to create a new instance with same fields
     * @param sourceSplit Split to be cloned
     * @param generateUID Determines if the clone should have a new UID or should
     * maintain the one from source
     */
    constructor(sourceSplit: Split, generateUID: Boolean) {
        mMemo = sourceSplit.mMemo
        mAccountUID = sourceSplit.mAccountUID
        mSplitType = sourceSplit.mSplitType
        mTransactionUID = sourceSplit.mTransactionUID
        mValue = Money(sourceSplit.mValue!!)
        mQuantity = Money(sourceSplit.mQuantity!!)

        //todo: clone reconciled status
        if (generateUID) {
            generateUID()
        } else {
            mUID = sourceSplit.mUID
        }
    }

    /**
     * Sets the value amount of the split.
     *
     *
     * The value is in the currency of the containing transaction.
     * It's stored unsigned.
     *
     * @param value Money value of this split
     * @see .setMQuantity
     */
    fun setMValue(value: Money) {
        mValue = value.abs()
    }

    /**
     * Returns the quantity amount of the split.
     *
     * The quantity is in the currency of the account to which the split is associated
     * @return Money quantity amount
     * @see .getMValue
     */
//    fun getMQuantity(): Money? {
//        return mQuantity
//    }

    /**
     * Sets the quantity value of the split.
     *
     *
     * The quantity is in the currency of the owning account.
     * It will be stored unsigned.
     *
     * @param quantity Money quantity amount
     * @see .setMValue
     */
//    fun setMQuantity(quantity: Money?) {
//        mQuantity = quantity!!.abs()
//    }

    /**
     * Creates a split which is a pair of this instance.
     * A pair split has all the same attributes except that the SplitType is inverted and it belongs
     * to another account.
     * @param accountUID GUID of account
     * @return New split pair of current split
     * @see TransactionType.invert
     */
    fun createPair(accountUID: String?): Split {
        val pair = Split(mValue!!, accountUID)
        pair.mSplitType = mSplitType!!.invert()
        pair.mMemo = mMemo
        pair.mTransactionUID = mTransactionUID
        pair.mQuantity = mQuantity
        return pair
    }

    /**
     * Clones this split and returns an exact copy.
     * @return New instance of a split which is a copy of the current one
     */
    @Throws(CloneNotSupportedException::class)
    protected fun clone(): Split {
//        super.clone()     not available   by XJ
        val split = Split(mValue!!, mAccountUID)
        split.mUID = mUID
        split.mSplitType = mSplitType
        split.mMemo = mMemo
        split.mTransactionUID = mTransactionUID
        split.mQuantity = mQuantity
        return split
    }

    /**
     * Checks is this `other` is a pair split of this.
     *
     * Two splits are considered a pair if they have the same amount and
     * opposite split types
     * @param other the other split of the pair to be tested
     * @return whether the two splits are a pair
     */
    fun isPairOf(other: Split): Boolean {
        return mValue!!.equals(other.mValue) && mSplitType!!.invert() == other.mSplitType
    }

    /**
     * Returns the formatted amount (with or without negation sign) for the split value
     * @return Money amount of value
     * @see .formattedAmount
     */
    fun formattedValue(): Money {
        return formattedAmount(mValue, mAccountUID, mSplitType)
    }

    /**
     * Returns the formatted amount (with or without negation sign) for the quantity
     * @return Money amount of quantity
     * @see .formattedAmount
     */
    fun formattedQuantity(): Money {
        return formattedAmount(mQuantity, mAccountUID, mSplitType)
    }

    /**
     * Check if this split is reconciled
     * @return `true` if the split is reconciled, `false` otherwise
     */
    val isReconciled: Boolean
        get() = mReconcileState == FLAG_RECONCILED

    override fun toString(): String {
        return mSplitType!!.name + " of " + mValue.toString() + " in account: " + mAccountUID
    }

    /**
     * Returns a string representation of the split which can be parsed again
     * using [org.gnucash.android.model.Split.parseSplit]
     *
     *
     * The string is formatted as:<br></br>
     * "&lt;uid&gt;;&lt;valueNum&gt;;&lt;valueDenom&gt;;&lt;valueCurrencyCode&gt;;&lt;quantityNum&gt;;&lt;quantityDenom&gt;;&lt;quantityCurrencyCode&gt;;&lt;transaction_uid&gt;;&lt;account_uid&gt;;&lt;type&gt;;&lt;memo&gt;"
     *
     *
     *
     * **Only the memo field is allowed to be null**
     *
     * @return the converted CSV string of this split
     */
    fun toCsv(): String {
        val sep = ";"
        //TODO: add reconciled state and date
        var splitString = (mUID + sep + mValue!!.numerator() + sep + mValue!!.denominator()
                + sep + mValue!!.mCommodity!!.mMnemonic + sep + mQuantity!!.numerator()
                + sep + mQuantity!!.denominator() + sep + mQuantity!!.mCommodity!!.mMnemonic
                + sep + mTransactionUID + sep + mAccountUID + sep + mSplitType!!.name)
        if (mMemo != null) {
            splitString = splitString + sep + mMemo
        }
        return splitString
    }

    /**
     * Two splits are considered equivalent if all the fields (excluding GUID
     * and timestamps - created, modified, reconciled) are equal.
     *
     *
     * Any two splits which are equal are also equivalent, but the reverse
     * is not true
     *
     *
     * The difference with to [.equals] is that the GUID of
     * the split is not considered. This is useful in cases where a new split
     * is generated for a transaction with the same properties, but a new GUID
     * is generated e.g. when editing a transaction and modifying the splits
     *
     * @param split Other split for which to test equivalence
     * @return `true` if both splits are equivalent, `false` otherwise
     */
    fun isEquivalentTo(split: Split): Boolean {
        if (this === split) return true
        if (super.equals(split)) return true
        if (mReconcileState != split.mReconcileState) return false
        if (!mValue!!.equals(split.mValue)) return false
        if (!mQuantity!!.equals(split.mQuantity)) return false
        if (mTransactionUID != split.mTransactionUID) return false
        if (mAccountUID != split.mAccountUID) return false
        if (mSplitType !== split.mSplitType) return false
        return if (mMemo != null) mMemo == split.mMemo else split.mMemo == null
    }

    /**
     * Two splits are considered equal if all their properties excluding
     * timestamps (created, modified, reconciled) are equal.
     *
     * @param other Other split to compare for equality
     * @return `true` if this split is equal to `o`, `false` otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        if (!super.equals(other)) return false
        val split = other as Split
        if (mReconcileState != split.mReconcileState) return false
        if (!mValue!!.equals(split.mValue)) return false
        if (!mQuantity!!.equals(split.mQuantity)) return false
        if (mTransactionUID != split.mTransactionUID) return false
        if (mAccountUID != split.mAccountUID) return false
        if (mSplitType !== split.mSplitType) return false
        return if (mMemo != null) mMemo == split.mMemo else split.mMemo == null
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + mValue.hashCode()
        result = 31 * result + mQuantity.hashCode()
        result = 31 * result + mTransactionUID.hashCode()
        result = 31 * result + mAccountUID.hashCode()
        result = 31 * result + mSplitType.hashCode()
        result = 31 * result + if (mMemo != null) mMemo.hashCode() else 0
        result = 31 * result + mReconcileState.code
        return result
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(mUID)
        dest.writeString(mAccountUID)
        dest.writeString(mTransactionUID)
        dest.writeString(mSplitType!!.name)
        dest.writeLong(mValue!!.numerator())
        dest.writeLong(mValue!!.denominator())
        dest.writeString(mValue!!.mCommodity!!.mMnemonic)
        dest.writeLong(mQuantity!!.numerator())
        dest.writeLong(mQuantity!!.denominator())
        dest.writeString(mQuantity!!.mCommodity!!.mMnemonic)
        dest.writeString(if (mMemo == null) "" else mMemo)
        dest.writeString(mReconcileState.toString())
        dest.writeString(mReconcileDate.toString())
    }

    /**
     * Constructor for creating a Split object from a Parcel
     * @param source Source parcel containing the split
     * @see .CREATOR
     */
    private constructor(source: Parcel) {
        mUID = source.readString()
        mAccountUID = source.readString()
        mTransactionUID = source.readString()
        mSplitType = TransactionType.valueOf(source.readString()!!)
        val valueNum = source.readLong()
        val valueDenom = source.readLong()
        val valueCurrency = source.readString()
        mValue = Money(valueNum, valueDenom, valueCurrency!!).abs()
        val qtyNum = source.readLong()
        val qtyDenom = source.readLong()
        val qtyCurrency = source.readString()
        mQuantity = Money(qtyNum, qtyDenom, qtyCurrency!!).abs()
        val memo = source.readString()
        mMemo = if (memo!!.isEmpty()) null else memo
        mReconcileState = source.readString()!![0]
        mReconcileDate = Timestamp.valueOf(source.readString())
    }

    companion object {
        /**
         * Flag indicating that the split has been reconciled
         */
        const val FLAG_RECONCILED = 'y'

        /**
         * Flag indicating that the split has not been reconciled
         */
        const val FLAG_NOT_RECONCILED = 'n'

        /**
         * Flag indicating that the split has been cleared, but not reconciled
         */
        const val FLAG_CLEARED = 'c'

        /**
         * Splits are saved as absolute values to the database, with no negative numbers.
         * The type of movement the split causes to the balance of an account determines
         * its sign, and that depends on the split type and the account type
         * @param amount Money amount to format
         * @param accountUID GUID of the account
         * @param splitType Transaction type of the split
         * @return -`amount` if the amount would reduce the balance of
         * `account`, otherwise +`amount`
         */
        private fun formattedAmount(amount: Money?, accountUID: String?, splitType: TransactionType?): Money {
            val isDebitAccount = AccountsDbAdapter.instance.getAccountType(accountUID!!).hasDebitNormalBalance()
            val absAmount = amount!!.abs()
            val isDebitSplit = splitType === TransactionType.DEBIT
            return if (isDebitAccount) {
                if (isDebitSplit) {
                    absAmount
                } else {
                    absAmount.negate()
                }
            } else {
                if (isDebitSplit) {
                    absAmount.negate()
                } else {
                    absAmount
                }
            }
        }

        /**
         * Parses a split which is in the format:<br></br>
         * "<uid>;<valueNum>;<valueDenom>;<currency_code>;<quantityNum>;<quantityDenom>;<currency_code>;<transaction_uid>;<account_uid>;<type>;<memo>".
         *
         *
         * Also supports parsing of the deprecated format
         * "<amount>;<currency_code>;<transaction_uid>;<account_uid>;<type>;<memo>".
         * The split input string is the same produced by the [Split.toCsv] method.</memo></type></account_uid></transaction_uid></currency_code></amount>
         *
         * @param splitCsvString String containing formatted split
         * @return Split instance parsed from the string
        </memo></type></account_uid></transaction_uid></currency_code></quantityDenom></quantityNum></currency_code></valueDenom></valueNum></uid> */
        @JvmStatic
        fun parseSplit(splitCsvString: String): Split {
            //TODO: parse reconciled state and date
            val tokens = splitCsvString.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            return if (tokens.size < 8) { //old format splits
                val amount = Money(tokens[0], tokens[1])
                val split = Split(amount, tokens[2])
                split.mTransactionUID = tokens[3]
                split.mSplitType = TransactionType.valueOf(tokens[4])
                if (tokens.size == 6) {
                    split.mMemo = tokens[5]
                }
                split
            } else {
                val valueNum = tokens[1].toLong()
                val valueDenom = tokens[2].toLong()
                val valueCurrencyCode = tokens[3]
                val quantityNum = tokens[4].toLong()
                val quantityDenom = tokens[5].toLong()
                val qtyCurrencyCode = tokens[6]
                val value = Money(valueNum, valueDenom, valueCurrencyCode)
                val quantity = Money(quantityNum, quantityDenom, qtyCurrencyCode)
                val split = Split(value, tokens[8])
                split.mUID = tokens[0]
                split.mQuantity = quantity
                split.mTransactionUID = tokens[7]
                split.mSplitType = TransactionType.valueOf(tokens[9])
                if (tokens.size == 11) {
                    split.mMemo = tokens[10]
                }
                split
            }
        }

        /**
         * Creates new Parcels containing the information in this split during serialization
         */
        @JvmField
        val CREATOR: Creator<Split?> = object : Creator<Split?> {
            override fun createFromParcel(source: Parcel): Split {
                return Split(source)
            }

            override fun newArray(size: Int): Array<Split?> {
                return arrayOfNulls(size)
            }
        }
    }
}