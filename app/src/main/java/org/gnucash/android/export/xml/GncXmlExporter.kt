/*
 * Copyright (c) 2014 - 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.export.xml

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.crashlytics.android.Crashlytics
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.DatabaseSchema.*
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.export.Exporter.ExporterException
import org.gnucash.android.model.*
import org.gnucash.android.model.BaseModel.Companion.generateUID
import org.gnucash.android.model.Money.Companion.getBigDecimal
import org.gnucash.android.model.ScheduledAction.ActionType
import org.gnucash.android.util.TimestampHelper
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.*
import java.util.*

/**
 * Creates a GnuCash XML representation of the accounts and transactions
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Yongxin Wang <fefe.wyx></fefe.wyx>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class GncXmlExporter : Exporter {
    /**
     * Root account for template accounts
     */
    private var mRootTemplateAccount: Account? = null
    private val mTransactionToTemplateAccountMap: MutableMap<String?, Account> = TreeMap()

    /**
     * Construct a new exporter with export parameters
     * @param params Parameters for the export
     */
    constructor(params: ExportParams?) : super(params!!, null) {
        LOG_TAG = "GncXmlExporter"
    }

    /**
     * Overloaded constructor.
     * Creates an exporter with an already open database instance.
     * @param params Parameters for the export
     * @param db SQLite database
     */
    constructor(params: ExportParams?, db: SQLiteDatabase?) : super(params!!, db) {
        LOG_TAG = "GncXmlExporter"
    }

    @Throws(IOException::class)
    private fun exportSlots(
        xmlSerializer: XmlSerializer,
        slotKey: List<String>?,
        slotType: List<String>?,
        slotValue: List<String>?
    ) {
        if (slotKey == null || slotType == null || slotValue == null || slotKey.size == 0 || slotType.size != slotKey.size || slotValue.size != slotKey.size) {
            return
        }
        for (i in slotKey.indices) {
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SLOT)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SLOT_KEY)
            xmlSerializer.text(slotKey[i])
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SLOT_KEY)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SLOT_VALUE)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, slotType[i])
            xmlSerializer.text(slotValue[i])
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SLOT_VALUE)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SLOT)
        }
    }

    @Throws(IOException::class)
    private fun exportAccounts(xmlSerializer: XmlSerializer) {
        // gnucash desktop requires that parent account appears before its descendants.
        // sort by full-name to fulfill the request
        val cursor =
            mAccountsDbAdapter!!.fetchAccounts(null, null, DatabaseSchema.AccountEntry.COLUMN_FULL_NAME + " ASC")
        while (cursor.moveToNext()) {
            // write account
            xmlSerializer.startTag(null, GncXmlHelper.TAG_ACCOUNT)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_VERSION, GncXmlHelper.BOOK_VERSION)
            // account name
            xmlSerializer.startTag(null, GncXmlHelper.TAG_ACCT_NAME)
            xmlSerializer.text(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_NAME)))
            xmlSerializer.endTag(null, GncXmlHelper.TAG_ACCT_NAME)
            // account guid
            xmlSerializer.startTag(null, GncXmlHelper.TAG_ACCT_ID)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, GncXmlHelper.ATTR_VALUE_GUID)
            xmlSerializer.text(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_UID)))
            xmlSerializer.endTag(null, GncXmlHelper.TAG_ACCT_ID)
            // account type
            xmlSerializer.startTag(null, GncXmlHelper.TAG_ACCT_TYPE)
            val acct_type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_TYPE))
            xmlSerializer.text(acct_type)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_ACCT_TYPE)
            // commodity
            xmlSerializer.startTag(null, GncXmlHelper.TAG_ACCT_COMMODITY)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY_SPACE)
            xmlSerializer.text("ISO4217")
            xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY_SPACE)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY_ID)
            val acctCurrencyCode =
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_CURRENCY))
            xmlSerializer.text(acctCurrencyCode)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY_ID)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_ACCT_COMMODITY)
            // commodity scu
            val commodity = CommoditiesDbAdapter.instance.getCommodity(acctCurrencyCode)!!
            xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY_SCU)
            xmlSerializer.text(Integer.toString(commodity.mSmallestFraction))
            xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY_SCU)
            // account description
            val description =
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_DESCRIPTION))
            if (description != null && description != "") {
                xmlSerializer.startTag(null, GncXmlHelper.TAG_ACCT_DESCRIPTION)
                xmlSerializer.text(description)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_ACCT_DESCRIPTION)
            }
            // account slots, color, placeholder, default transfer account, favorite
            val slotKey = ArrayList<String>()
            val slotType = ArrayList<String>()
            val slotValue = ArrayList<String>()
            slotKey.add(GncXmlHelper.KEY_PLACEHOLDER)
            slotType.add(GncXmlHelper.ATTR_VALUE_STRING)
            slotValue.add(java.lang.Boolean.toString(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER)) != 0))
            val color = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_COLOR_CODE))
            if (color != null && color.length > 0) {
                slotKey.add(GncXmlHelper.KEY_COLOR)
                slotType.add(GncXmlHelper.ATTR_VALUE_STRING)
                slotValue.add(color)
            }
            val defaultTransferAcctUID =
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID))
            if (defaultTransferAcctUID != null && defaultTransferAcctUID.length > 0) {
                slotKey.add(GncXmlHelper.KEY_DEFAULT_TRANSFER_ACCOUNT)
                slotType.add(GncXmlHelper.ATTR_VALUE_STRING)
                slotValue.add(defaultTransferAcctUID)
            }
            slotKey.add(GncXmlHelper.KEY_FAVORITE)
            slotType.add(GncXmlHelper.ATTR_VALUE_STRING)
            slotValue.add(java.lang.Boolean.toString(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_FAVORITE)) != 0))
            xmlSerializer.startTag(null, GncXmlHelper.TAG_ACCT_SLOTS)
            exportSlots(xmlSerializer, slotKey, slotType, slotValue)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_ACCT_SLOTS)

            // parent uid
            val parentUID =
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_PARENT_ACCOUNT_UID))
            if (acct_type != "ROOT" && parentUID != null && parentUID.length > 0) {
                xmlSerializer.startTag(null, GncXmlHelper.TAG_PARENT_UID)
                xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, GncXmlHelper.ATTR_VALUE_GUID)
                xmlSerializer.text(parentUID)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_PARENT_UID)
            } else {
                Log.d(
                    "export",
                    "root account : " + cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_UID))
                )
            }
            xmlSerializer.endTag(null, GncXmlHelper.TAG_ACCOUNT)
        }
        cursor.close()
    }

    /**
     * Exports template accounts
     *
     * Template accounts are just dummy accounts created for use with template transactions
     * @param xmlSerializer XML serializer
     * @param accountList List of template accounts
     * @throws IOException if could not write XML to output stream
     */
    @Throws(IOException::class)
    private fun exportTemplateAccounts(xmlSerializer: XmlSerializer, accountList: Collection<Account>) {
        for (account in accountList) {
            xmlSerializer.startTag(null, GncXmlHelper.TAG_ACCOUNT)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_VERSION, GncXmlHelper.BOOK_VERSION)
            // account name
            xmlSerializer.startTag(null, GncXmlHelper.TAG_ACCT_NAME)
            xmlSerializer.text(account.mName)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_ACCT_NAME)
            // account guid
            xmlSerializer.startTag(null, GncXmlHelper.TAG_ACCT_ID)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, GncXmlHelper.ATTR_VALUE_GUID)
            xmlSerializer.text(account.mUID)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_ACCT_ID)
            // account type
            xmlSerializer.startTag(null, GncXmlHelper.TAG_ACCT_TYPE)
            xmlSerializer.text(account.mAccountType.name)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_ACCT_TYPE)
            // commodity
            xmlSerializer.startTag(null, GncXmlHelper.TAG_ACCT_COMMODITY)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY_SPACE)
            xmlSerializer.text("template")
            xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY_SPACE)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY_ID)
            val acctCurrencyCode = "template"
            xmlSerializer.text(acctCurrencyCode)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY_ID)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_ACCT_COMMODITY)
            // commodity scu
            xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY_SCU)
            xmlSerializer.text("1")
            xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY_SCU)
            if (account.mAccountType !== AccountType.ROOT && mRootTemplateAccount != null) {
                xmlSerializer.startTag(null, GncXmlHelper.TAG_PARENT_UID)
                xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, GncXmlHelper.ATTR_VALUE_GUID)
                xmlSerializer.text(mRootTemplateAccount!!.mUID)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_PARENT_UID)
            }
            xmlSerializer.endTag(null, GncXmlHelper.TAG_ACCOUNT)
        }
    }

    /**
     * Serializes transactions from the database to XML
     * @param xmlSerializer XML serializer
     * @param exportTemplates Flag whether to export templates or normal transactions
     * @throws IOException if the XML serializer cannot be written to
     */
    @Throws(IOException::class)
    private fun exportTransactions(xmlSerializer: XmlSerializer, exportTemplates: Boolean) {
        var where = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + "=0"
        if (exportTemplates) {
            where = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + "=1"
        }
        val cursor = mTransactionsDbAdapter!!.fetchTransactionsWithSplits(
            arrayOf(
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " AS trans_uid",
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_DESCRIPTION + " AS trans_desc",
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_NOTES + " AS trans_notes",
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " AS trans_time",
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_EXPORTED + " AS trans_exported",
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_CURRENCY + " AS trans_currency",
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_CREATED_AT + " AS trans_date_posted",
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_SCHEDX_ACTION_UID + " AS trans_from_sched_action",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_UID + " AS split_uid",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_MEMO + " AS split_memo",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TYPE + " AS split_type",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_VALUE_NUM + " AS split_value_num",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_VALUE_DENOM + " AS split_value_denom",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_QUANTITY_NUM + " AS split_quantity_num",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_QUANTITY_DENOM + " AS split_quantity_denom",
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + " AS split_acct_uid"
            ),
            where, null,
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " ASC , " +
                    TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " ASC "
        )
        var lastTrxUID = ""
        var trnCommodity: Commodity? = null
        val denomString = "100"
        if (exportTemplates) {
            mRootTemplateAccount = Account("Template Root")
            mRootTemplateAccount!!.mAccountType = AccountType.ROOT
            mTransactionToTemplateAccountMap[" "] = mRootTemplateAccount!!

            //FIXME: Retrieve the template account GUIDs from the scheduled action table and create accounts with that
            //this will allow use to maintain the template account GUID when we import from the desktop and also use the same for the splits
            while (cursor.moveToNext()) {
                val account = Account(generateUID())
                account.mAccountType = AccountType.BANK
                val trnUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_uid"))
                mTransactionToTemplateAccountMap[trnUID] = account
            }
            exportTemplateAccounts(xmlSerializer, mTransactionToTemplateAccountMap.values)
            //push cursor back to before the beginning
            cursor.moveToFirst()
            cursor.moveToPrevious()
        }

        //// FIXME: 12.10.2015 export split reconciled_state and reconciled_date to the export
        while (cursor.moveToNext()) {
            val curTrxUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_uid"))
            if (lastTrxUID != curTrxUID) { // new transaction starts
                if (lastTrxUID != "") { // there's an old transaction, close it
                    xmlSerializer.endTag(null, GncXmlHelper.TAG_TRN_SPLITS)
                    xmlSerializer.endTag(null, GncXmlHelper.TAG_TRANSACTION)
                }
                // new transaction
                xmlSerializer.startTag(null, GncXmlHelper.TAG_TRANSACTION)
                xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_VERSION, GncXmlHelper.BOOK_VERSION)
                // transaction id
                xmlSerializer.startTag(null, GncXmlHelper.TAG_TRX_ID)
                xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, GncXmlHelper.ATTR_VALUE_GUID)
                xmlSerializer.text(curTrxUID)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_TRX_ID)
                // currency
                val currencyCode = cursor.getString(cursor.getColumnIndexOrThrow("trans_currency"))
                trnCommodity =
                    CommoditiesDbAdapter.instance.getCommodity(currencyCode) //Currency.getInstance(currencyCode);
                xmlSerializer.startTag(null, GncXmlHelper.TAG_TRX_CURRENCY)
                xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY_SPACE)
                xmlSerializer.text("ISO4217")
                xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY_SPACE)
                xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY_ID)
                xmlSerializer.text(currencyCode)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY_ID)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_TRX_CURRENCY)
                // date posted, time which user put on the transaction
                val strDate = GncXmlHelper.formatDate(cursor.getLong(cursor.getColumnIndexOrThrow("trans_time")))
                xmlSerializer.startTag(null, GncXmlHelper.TAG_DATE_POSTED)
                xmlSerializer.startTag(null, GncXmlHelper.TAG_TS_DATE)
                xmlSerializer.text(strDate)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_TS_DATE)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_DATE_POSTED)

                // date entered, time when the transaction was actually created
                val timeEntered =
                    TimestampHelper.getTimestampFromUtcString(cursor.getString(cursor.getColumnIndexOrThrow("trans_date_posted")))
                val dateEntered = GncXmlHelper.formatDate(timeEntered.time)
                xmlSerializer.startTag(null, GncXmlHelper.TAG_DATE_ENTERED)
                xmlSerializer.startTag(null, GncXmlHelper.TAG_TS_DATE)
                xmlSerializer.text(dateEntered)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_TS_DATE)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_DATE_ENTERED)

                // description
                xmlSerializer.startTag(null, GncXmlHelper.TAG_TRN_DESCRIPTION)
                xmlSerializer.text(cursor.getString(cursor.getColumnIndexOrThrow("trans_desc")))
                xmlSerializer.endTag(null, GncXmlHelper.TAG_TRN_DESCRIPTION)
                lastTrxUID = curTrxUID
                // slots
                val slotKey = ArrayList<String>()
                val slotType = ArrayList<String>()
                val slotValue = ArrayList<String>()
                val notes = cursor.getString(cursor.getColumnIndexOrThrow("trans_notes"))
                if (notes != null && notes.length > 0) {
                    slotKey.add(GncXmlHelper.KEY_NOTES)
                    slotType.add(GncXmlHelper.ATTR_VALUE_STRING)
                    slotValue.add(notes)
                }
                val scheduledActionUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_from_sched_action"))
                if (scheduledActionUID != null && !scheduledActionUID.isEmpty()) {
                    slotKey.add(GncXmlHelper.KEY_FROM_SCHED_ACTION)
                    slotType.add(GncXmlHelper.ATTR_VALUE_GUID)
                    slotValue.add(scheduledActionUID)
                }
                xmlSerializer.startTag(null, GncXmlHelper.TAG_TRN_SLOTS)
                exportSlots(xmlSerializer, slotKey, slotType, slotValue)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_TRN_SLOTS)

                // splits start
                xmlSerializer.startTag(null, GncXmlHelper.TAG_TRN_SPLITS)
            }
            xmlSerializer.startTag(null, GncXmlHelper.TAG_TRN_SPLIT)
            // split id
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SPLIT_ID)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, GncXmlHelper.ATTR_VALUE_GUID)
            xmlSerializer.text(cursor.getString(cursor.getColumnIndexOrThrow("split_uid")))
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SPLIT_ID)
            // memo
            val memo = cursor.getString(cursor.getColumnIndexOrThrow("split_memo"))
            if (memo != null && memo.length > 0) {
                xmlSerializer.startTag(null, GncXmlHelper.TAG_SPLIT_MEMO)
                xmlSerializer.text(memo)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_SPLIT_MEMO)
            }
            // reconciled
            xmlSerializer.startTag(null, GncXmlHelper.TAG_RECONCILED_STATE)
            xmlSerializer.text("n") //fixme: retrieve reconciled state from the split in the db
            xmlSerializer.endTag(null, GncXmlHelper.TAG_RECONCILED_STATE)
            //todo: if split is reconciled, add reconciled date
            // value, in the transaction's currency
            val trxType = cursor.getString(cursor.getColumnIndexOrThrow("split_type"))
            val splitValueNum = cursor.getInt(cursor.getColumnIndexOrThrow("split_value_num"))
            val splitValueDenom = cursor.getInt(cursor.getColumnIndexOrThrow("split_value_denom"))
            val splitAmount = getBigDecimal(splitValueNum.toLong(), splitValueDenom.toLong())
            var strValue = "0/$denomString"
            if (!exportTemplates) { //when doing normal transaction export
                strValue = (if (trxType == "CREDIT") "-" else "") + splitValueNum + "/" + splitValueDenom
            }
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SPLIT_VALUE)
            xmlSerializer.text(strValue)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SPLIT_VALUE)
            // quantity, in the split account's currency
            val splitQuantityNum = cursor.getString(cursor.getColumnIndexOrThrow("split_quantity_num"))
            val splitQuantityDenom = cursor.getString(cursor.getColumnIndexOrThrow("split_quantity_denom"))
            if (!exportTemplates) {
                strValue = (if (trxType == "CREDIT") "-" else "") + splitQuantityNum + "/" + splitQuantityDenom
            }
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SPLIT_QUANTITY)
            xmlSerializer.text(strValue)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SPLIT_QUANTITY)
            // account guid
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SPLIT_ACCOUNT)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, GncXmlHelper.ATTR_VALUE_GUID)
            var splitAccountUID: String?
            splitAccountUID = if (exportTemplates) {
                //get the UID of the template account
                mTransactionToTemplateAccountMap[curTrxUID]!!.mUID
            } else {
                cursor.getString(cursor.getColumnIndexOrThrow("split_acct_uid"))
            }
            xmlSerializer.text(splitAccountUID)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SPLIT_ACCOUNT)

            //if we are exporting a template transaction, then we need to add some extra slots
            if (exportTemplates) {
                xmlSerializer.startTag(null, GncXmlHelper.TAG_SPLIT_SLOTS)
                xmlSerializer.startTag(null, GncXmlHelper.TAG_SLOT)
                xmlSerializer.startTag(null, GncXmlHelper.TAG_SLOT_KEY)
                xmlSerializer.text(GncXmlHelper.KEY_SCHEDX_ACTION) //FIXME: not all templates may be scheduled actions
                xmlSerializer.endTag(null, GncXmlHelper.TAG_SLOT_KEY)
                xmlSerializer.startTag(null, GncXmlHelper.TAG_SLOT_VALUE)
                xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, "frame")
                val slotKeys: MutableList<String> = ArrayList()
                val slotTypes: MutableList<String> = ArrayList()
                val slotValues: MutableList<String> = ArrayList()
                slotKeys.add(GncXmlHelper.KEY_SPLIT_ACCOUNT_SLOT)
                slotTypes.add(GncXmlHelper.ATTR_VALUE_GUID)
                slotValues.add(cursor.getString(cursor.getColumnIndexOrThrow("split_acct_uid")))
                val type = TransactionType.valueOf(trxType)
                if (type === TransactionType.CREDIT) {
                    slotKeys.add(GncXmlHelper.KEY_CREDIT_FORMULA)
                    slotTypes.add(GncXmlHelper.ATTR_VALUE_STRING)
                    slotValues.add(GncXmlHelper.formatTemplateSplitAmount(splitAmount))
                    slotKeys.add(GncXmlHelper.KEY_CREDIT_NUMERIC)
                    slotTypes.add(GncXmlHelper.ATTR_VALUE_NUMERIC)
                    slotValues.add(GncXmlHelper.formatSplitAmount(splitAmount, trnCommodity!!))
                } else {
                    slotKeys.add(GncXmlHelper.KEY_DEBIT_FORMULA)
                    slotTypes.add(GncXmlHelper.ATTR_VALUE_STRING)
                    slotValues.add(GncXmlHelper.formatTemplateSplitAmount(splitAmount))
                    slotKeys.add(GncXmlHelper.KEY_DEBIT_NUMERIC)
                    slotTypes.add(GncXmlHelper.ATTR_VALUE_NUMERIC)
                    slotValues.add(GncXmlHelper.formatSplitAmount(splitAmount, trnCommodity!!))
                }
                exportSlots(xmlSerializer, slotKeys, slotTypes, slotValues)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_SLOT_VALUE)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_SLOT)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_SPLIT_SLOTS)
            }
            xmlSerializer.endTag(null, GncXmlHelper.TAG_TRN_SPLIT)
        }
        if (lastTrxUID != "") { // there's an unfinished transaction, close it
            xmlSerializer.endTag(null, GncXmlHelper.TAG_TRN_SPLITS)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_TRANSACTION)
        }
        cursor.close()
    }

    /**
     * Serializes [ScheduledAction]s from the database to XML
     * @param xmlSerializer XML serializer
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun exportScheduledTransactions(xmlSerializer: XmlSerializer) {
        //for now we will export only scheduled transactions to XML
        val cursor = mScheduledActionDbAdapter!!.fetchAllRecords(
            ScheduledActionEntry.COLUMN_TYPE + "=?", arrayOf(ActionType.TRANSACTION.name), null
        )
        while (cursor.moveToNext()) {
            val scheduledAction = mScheduledActionDbAdapter!!.buildModelInstance(cursor)
            val actionUID = scheduledAction.getMActionUID()
            val accountUID = mTransactionToTemplateAccountMap[actionUID]
                ?: //if the action UID does not belong to a transaction we've seen before, skip it
                continue
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SCHEDULED_ACTION)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_VERSION, GncXmlHelper.BOOK_VERSION)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SX_ID)
            val nameUID = accountUID.mName
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, GncXmlHelper.ATTR_VALUE_GUID)
            xmlSerializer.text(nameUID)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SX_ID)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SX_NAME)
            val actionType = scheduledAction.mActionType
            if (actionType === ActionType.TRANSACTION) {
                val description =
                    TransactionsDbAdapter.instance.getAttribute(actionUID!!, TransactionEntry.COLUMN_DESCRIPTION)
                xmlSerializer.text(description)
            } else {
                xmlSerializer.text(actionType.name)
            }
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SX_NAME)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SX_ENABLED)
            xmlSerializer.text(if (scheduledAction.isEnabled) "y" else "n")
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SX_ENABLED)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SX_AUTO_CREATE)
            xmlSerializer.text(if (scheduledAction.shouldAutoCreate()) "y" else "n")
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SX_AUTO_CREATE)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SX_AUTO_CREATE_NOTIFY)
            xmlSerializer.text(if (scheduledAction.shouldAutoNotify()) "y" else "n")
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SX_AUTO_CREATE_NOTIFY)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SX_ADVANCE_CREATE_DAYS)
            xmlSerializer.text(Integer.toString(scheduledAction.mAdvanceCreateDays))
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SX_ADVANCE_CREATE_DAYS)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SX_ADVANCE_REMIND_DAYS)
            xmlSerializer.text(Integer.toString(scheduledAction.mAdvanceNotifyDays))
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SX_ADVANCE_REMIND_DAYS)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SX_INSTANCE_COUNT)
            val scheduledActionUID = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_UID))
            val instanceCount = mScheduledActionDbAdapter!!.getActionInstanceCount(scheduledActionUID)
            xmlSerializer.text(java.lang.Long.toString(instanceCount))
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SX_INSTANCE_COUNT)

            //start date
            val createdTimestamp =
                cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_CREATED_AT))
            val scheduleStartTime = TimestampHelper.getTimestampFromUtcString(createdTimestamp).time
            serializeDate(xmlSerializer, GncXmlHelper.TAG_SX_START, scheduleStartTime)
            val lastRunTime = cursor.getLong(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_LAST_RUN))
            if (lastRunTime > 0) {
                serializeDate(xmlSerializer, GncXmlHelper.TAG_SX_LAST, lastRunTime)
            }
            val endTime = cursor.getLong(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_END_TIME))
            if (endTime > 0) {
                //end date
                serializeDate(xmlSerializer, GncXmlHelper.TAG_SX_END, endTime)
            } else { //add number of occurrences
                val totalFrequency =
                    cursor.getInt(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY))
                xmlSerializer.startTag(null, GncXmlHelper.TAG_SX_NUM_OCCUR)
                xmlSerializer.text(Integer.toString(totalFrequency))
                xmlSerializer.endTag(null, GncXmlHelper.TAG_SX_NUM_OCCUR)

                //remaining occurrences
                val executionCount =
                    cursor.getInt(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_EXECUTION_COUNT))
                xmlSerializer.startTag(null, GncXmlHelper.TAG_SX_REM_OCCUR)
                xmlSerializer.text(Integer.toString(totalFrequency - executionCount))
                xmlSerializer.endTag(null, GncXmlHelper.TAG_SX_REM_OCCUR)
            }
            val tag = cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_TAG))
            if (tag != null && !tag.isEmpty()) {
                xmlSerializer.startTag(null, GncXmlHelper.TAG_SX_TAG)
                xmlSerializer.text(tag)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_SX_TAG)
            }
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SX_TEMPL_ACCOUNT)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, GncXmlHelper.ATTR_VALUE_GUID)
            xmlSerializer.text(accountUID.mUID)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SX_TEMPL_ACCOUNT)

            //// FIXME: 11.10.2015 Retrieve the information for this section from the recurrence table
            xmlSerializer.startTag(null, GncXmlHelper.TAG_SX_SCHEDULE)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_GNC_RECURRENCE)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_VERSION, GncXmlHelper.RECURRENCE_VERSION)
            val recurrenceUID =
                cursor.getString(cursor.getColumnIndexOrThrow(ScheduledActionEntry.COLUMN_RECURRENCE_UID))
            val recurrence = RecurrenceDbAdapter.instance.getRecord(recurrenceUID)
            exportRecurrence(xmlSerializer, recurrence)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_GNC_RECURRENCE)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SX_SCHEDULE)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_SCHEDULED_ACTION)
        }
    }

    /**
     * Serializes a date as a `tag` which has a nested [GncXmlHelper.TAG_GDATE] which
     * has the date as a text element formatted using [GncXmlHelper.DATE_FORMATTER]
     * @param xmlSerializer XML serializer
     * @param tag Enclosing tag
     * @param timeMillis Date to be formatted and output
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun serializeDate(xmlSerializer: XmlSerializer, tag: String, timeMillis: Long) {
        xmlSerializer.startTag(null, tag)
        xmlSerializer.startTag(null, GncXmlHelper.TAG_GDATE)
        xmlSerializer.text(GncXmlHelper.DATE_FORMATTER.format(timeMillis))
        xmlSerializer.endTag(null, GncXmlHelper.TAG_GDATE)
        xmlSerializer.endTag(null, tag)
    }

    @Throws(IOException::class)
    private fun exportCommodities(xmlSerializer: XmlSerializer, commodities: List<Commodity>) {
        for (commodity in commodities) {
            xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_VERSION, GncXmlHelper.BOOK_VERSION)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY_SPACE)
            xmlSerializer.text("ISO4217")
            xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY_SPACE)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY_ID)
            xmlSerializer.text(commodity.mMnemonic)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY_ID)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY)
        }
    }

    @Throws(IOException::class)
    private fun exportPrices(xmlSerializer: XmlSerializer) {
        xmlSerializer.startTag(null, GncXmlHelper.TAG_PRICEDB)
        xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_VERSION, "1")
        val cursor = mPricesDbAdapter!!.fetchAllRecords()
        try {
            while (cursor.moveToNext()) {
                xmlSerializer.startTag(null, GncXmlHelper.TAG_PRICE)
                // GUID
                xmlSerializer.startTag(null, GncXmlHelper.TAG_PRICE_ID)
                xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, GncXmlHelper.ATTR_VALUE_GUID)
                xmlSerializer.text(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.CommonColumns.COLUMN_UID)))
                xmlSerializer.endTag(null, GncXmlHelper.TAG_PRICE_ID)
                // commodity
                xmlSerializer.startTag(null, GncXmlHelper.TAG_PRICE_COMMODITY)
                xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY_SPACE)
                xmlSerializer.text("ISO4217")
                xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY_SPACE)
                xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY_ID)
                xmlSerializer.text(
                    mCommoditiesDbAdapter!!.getMMnemonic(
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                DatabaseSchema.PriceEntry.COLUMN_COMMODITY_UID
                            )
                        )
                    )
                )
                xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY_ID)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_PRICE_COMMODITY)
                // currency
                xmlSerializer.startTag(null, GncXmlHelper.TAG_PRICE_CURRENCY)
                xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY_SPACE)
                xmlSerializer.text("ISO4217")
                xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY_SPACE)
                xmlSerializer.startTag(null, GncXmlHelper.TAG_COMMODITY_ID)
                xmlSerializer.text(
                    mCommoditiesDbAdapter!!.getMMnemonic(
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                DatabaseSchema.PriceEntry.COLUMN_CURRENCY_UID
                            )
                        )
                    )
                )
                xmlSerializer.endTag(null, GncXmlHelper.TAG_COMMODITY_ID)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_PRICE_CURRENCY)
                // time
                val strDate = GncXmlHelper.formatDate(
                    TimestampHelper.getTimestampFromUtcString(
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(PriceEntry.COLUMN_DATE)
                        )
                    ).time
                )
                xmlSerializer.startTag(null, GncXmlHelper.TAG_PRICE_TIME)
                xmlSerializer.startTag(null, GncXmlHelper.TAG_TS_DATE)
                xmlSerializer.text(strDate)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_TS_DATE)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_PRICE_TIME)
                // source
                xmlSerializer.startTag(null, GncXmlHelper.TAG_PRICE_SOURCE)
                xmlSerializer.text(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.PriceEntry.COLUMN_SOURCE)))
                xmlSerializer.endTag(null, GncXmlHelper.TAG_PRICE_SOURCE)
                // type, optional
                val type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.PriceEntry.COLUMN_TYPE))
                if (type != null && type != "") {
                    xmlSerializer.startTag(null, GncXmlHelper.TAG_PRICE_TYPE)
                    xmlSerializer.text(type)
                    xmlSerializer.endTag(null, GncXmlHelper.TAG_PRICE_TYPE)
                }
                // value
                xmlSerializer.startTag(null, GncXmlHelper.TAG_PRICE_VALUE)
                xmlSerializer.text(
                    cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseSchema.PriceEntry.COLUMN_VALUE_NUM))
                        .toString() + "/" + cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseSchema.PriceEntry.COLUMN_VALUE_DENOM))
                )
                xmlSerializer.endTag(null, GncXmlHelper.TAG_PRICE_VALUE)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_PRICE)
            }
        } finally {
            cursor.close()
        }
        xmlSerializer.endTag(null, GncXmlHelper.TAG_PRICEDB)
    }

    /**
     * Exports the recurrence to GnuCash XML, except the recurrence tags itself i.e. the actual recurrence attributes only
     *
     * This is because there are different recurrence start tags for transactions and budgets.<br></br>
     * So make sure to write the recurrence start/closing tags before/after calling this method.
     * @param xmlSerializer XML serializer
     * @param recurrence Recurrence object
     */
    @Throws(IOException::class)
    private fun exportRecurrence(xmlSerializer: XmlSerializer, recurrence: Recurrence?) {
        val periodType = recurrence!!.mPeriodType
        xmlSerializer.startTag(null, GncXmlHelper.TAG_RX_MULT)
        xmlSerializer.text(recurrence.mMultiplier.toString())
        xmlSerializer.endTag(null, GncXmlHelper.TAG_RX_MULT)
        xmlSerializer.startTag(null, GncXmlHelper.TAG_RX_PERIOD_TYPE)
        xmlSerializer.text(periodType!!.name.lowercase(Locale.getDefault()))
        xmlSerializer.endTag(null, GncXmlHelper.TAG_RX_PERIOD_TYPE)
        val recurrenceStartTime = recurrence.mPeriodStart.time
        serializeDate(xmlSerializer, GncXmlHelper.TAG_RX_START, recurrenceStartTime)
    }

    @Throws(IOException::class)
    private fun exportBudgets(xmlSerializer: XmlSerializer) {
        val cursor = mBudgetsDbAdapter!!.fetchAllRecords()
        while (cursor.moveToNext()) {
            val budget = mBudgetsDbAdapter!!.buildModelInstance(cursor)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_BUDGET)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_VERSION, GncXmlHelper.BOOK_VERSION)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_BUDGET_ID)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, GncXmlHelper.ATTR_VALUE_GUID)
            xmlSerializer.text(budget.mUID)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_BUDGET_ID)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_BUDGET_NAME)
            xmlSerializer.text(budget.mName)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_BUDGET_NAME)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_BUDGET_DESCRIPTION)
            xmlSerializer.text(if (budget.mDescription == null) "" else budget.mDescription)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_BUDGET_DESCRIPTION)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_BUDGET_NUM_PERIODS)
            xmlSerializer.text(java.lang.Long.toString(budget.mNumberOfPeriods))
            xmlSerializer.endTag(null, GncXmlHelper.TAG_BUDGET_NUM_PERIODS)
            xmlSerializer.startTag(null, GncXmlHelper.TAG_BUDGET_RECURRENCE)
            exportRecurrence(xmlSerializer, budget.mRecurrence)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_BUDGET_RECURRENCE)

            //export budget slots
            val slotKey = ArrayList<String>()
            val slotType = ArrayList<String>()
            val slotValue = ArrayList<String>()
            xmlSerializer.startTag(null, GncXmlHelper.TAG_BUDGET_SLOTS)
            for (budgetAmount in budget.expandedBudgetAmounts()) {
                xmlSerializer.startTag(null, GncXmlHelper.TAG_SLOT)
                xmlSerializer.startTag(null, GncXmlHelper.TAG_SLOT_KEY)
                xmlSerializer.text(budgetAmount.mAccountUID)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_SLOT_KEY)
                val amount = budgetAmount.mAmount
                slotKey.clear()
                slotType.clear()
                slotValue.clear()
                for (period in 0 until budget.mNumberOfPeriods) {
                    slotKey.add(period.toString())
                    slotType.add(GncXmlHelper.ATTR_VALUE_NUMERIC)
                    slotValue.add(amount!!.numerator().toString() + "/" + amount.denominator())
                }
                //budget slots
                xmlSerializer.startTag(null, GncXmlHelper.TAG_SLOT_VALUE)
                xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, GncXmlHelper.ATTR_VALUE_FRAME)
                exportSlots(xmlSerializer, slotKey, slotType, slotValue)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_SLOT_VALUE)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_SLOT)
            }
            xmlSerializer.endTag(null, GncXmlHelper.TAG_BUDGET_SLOTS)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_BUDGET)
        }
        cursor.close()
    }

    @Throws(ExporterException::class)
    override fun generateExport(): List<String?>? {
        var writer: OutputStreamWriter? = null
        val outputFile = exportCacheFilePath
        try {
            val fileOutputStream = FileOutputStream(outputFile)
            val bufferedOutputStream = BufferedOutputStream(fileOutputStream)
            writer = OutputStreamWriter(bufferedOutputStream)
            generateExport(writer)
        } catch (ex: IOException) {
            Crashlytics.log("Error exporting XML")
            Crashlytics.logException(ex)
        } finally {
            if (writer != null) {
                try {
                    writer.close()
                } catch (e: IOException) {
                    throw ExporterException(mExportParams, e)
                }
            }
        }
        val exportedFiles: MutableList<String?> = ArrayList()
        exportedFiles.add(outputFile)
        return exportedFiles
    }

    /**
     * Generates an XML export of the database and writes it to the `writer` output stream
     * @param writer Output stream
     * @throws ExporterException
     */
    @Throws(ExporterException::class)
    fun generateExport(writer: Writer?) {
        try {
            val namespaces = arrayOf(
                "gnc", "act", "book", "cd", "cmdty", "price", "slot",
                "split", "trn", "ts", "sx", "bgt", "recurrence"
            )
            val xmlSerializer = XmlPullParserFactory.newInstance().newSerializer()
            try {
                xmlSerializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
            } catch (e: IllegalStateException) {
                // Feature not supported. No problem
            }
            xmlSerializer.setOutput(writer)
            xmlSerializer.startDocument("utf-8", true)
            // root tag
            xmlSerializer.startTag(null, GncXmlHelper.TAG_ROOT)
            for (ns in namespaces) {
                xmlSerializer.attribute(null, "xmlns:$ns", "http://www.gnucash.org/XML/$ns")
            }
            // book count
            xmlSerializer.startTag(null, GncXmlHelper.TAG_COUNT_DATA)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_CD_TYPE, GncXmlHelper.ATTR_VALUE_BOOK)
            xmlSerializer.text("1")
            xmlSerializer.endTag(null, GncXmlHelper.TAG_COUNT_DATA)
            // book
            xmlSerializer.startTag(null, GncXmlHelper.TAG_BOOK)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_VERSION, GncXmlHelper.BOOK_VERSION)
            // book_id
            xmlSerializer.startTag(null, GncXmlHelper.TAG_BOOK_ID)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_TYPE, GncXmlHelper.ATTR_VALUE_GUID)
            xmlSerializer.text(generateUID())
            xmlSerializer.endTag(null, GncXmlHelper.TAG_BOOK_ID)
            //commodity count
            val commodities = mAccountsDbAdapter!!.commoditiesInUse.toMutableList()
            for (i in commodities.indices) {
                if (commodities[i].mMnemonic == "XXX") {
                    commodities.removeAt(i)
                }
            }
            xmlSerializer.startTag(null, GncXmlHelper.TAG_COUNT_DATA)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_CD_TYPE, "commodity")
            xmlSerializer.text(commodities.size.toString() + "")
            xmlSerializer.endTag(null, GncXmlHelper.TAG_COUNT_DATA)
            //account count
            xmlSerializer.startTag(null, GncXmlHelper.TAG_COUNT_DATA)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_CD_TYPE, "account")
            xmlSerializer.text(mAccountsDbAdapter!!.recordsCount.toString() + "")
            xmlSerializer.endTag(null, GncXmlHelper.TAG_COUNT_DATA)
            //transaction count
            xmlSerializer.startTag(null, GncXmlHelper.TAG_COUNT_DATA)
            xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_CD_TYPE, "transaction")
            xmlSerializer.text(mTransactionsDbAdapter!!.recordsCount.toString() + "")
            xmlSerializer.endTag(null, GncXmlHelper.TAG_COUNT_DATA)
            //price count
            val priceCount = mPricesDbAdapter!!.recordsCount
            if (priceCount > 0) {
                xmlSerializer.startTag(null, GncXmlHelper.TAG_COUNT_DATA)
                xmlSerializer.attribute(null, GncXmlHelper.ATTR_KEY_CD_TYPE, "price")
                xmlSerializer.text(priceCount.toString() + "")
                xmlSerializer.endTag(null, GncXmlHelper.TAG_COUNT_DATA)
            }
            // export the commodities used in the DB
            exportCommodities(xmlSerializer, commodities)
            // prices
            if (priceCount > 0) {
                exportPrices(xmlSerializer)
            }
            // accounts.
            exportAccounts(xmlSerializer)
            // transactions.
            exportTransactions(xmlSerializer, false)

            //transaction templates
            if (mTransactionsDbAdapter!!.templateTransactionsCount > 0) {
                xmlSerializer.startTag(null, GncXmlHelper.TAG_TEMPLATE_TRANSACTIONS)
                exportTransactions(xmlSerializer, true)
                xmlSerializer.endTag(null, GncXmlHelper.TAG_TEMPLATE_TRANSACTIONS)
            }
            //scheduled actions
            exportScheduledTransactions(xmlSerializer)

            //budgets
            exportBudgets(xmlSerializer)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_BOOK)
            xmlSerializer.endTag(null, GncXmlHelper.TAG_ROOT)
            xmlSerializer.endDocument()
            xmlSerializer.flush()
        } catch (e: Exception) {
            Crashlytics.logException(e)
            throw ExporterException(mExportParams, e)
        }
    }

    /**
     * Returns the MIME type for this exporter.
     * @return MIME type as string
     */
    override val exportMimeType: String
        get() = "text/xml"
}