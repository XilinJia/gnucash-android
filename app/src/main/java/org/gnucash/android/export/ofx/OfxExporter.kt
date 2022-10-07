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
package org.gnucash.android.export.ofx

import android.database.sqlite.SQLiteDatabase
import android.preference.PreferenceManager
import android.util.Log
import com.crashlytics.android.Crashlytics
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.model.Account
import org.gnucash.android.util.PreferencesHelper
import org.gnucash.android.util.TimestampHelper
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.*
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Exports the data in the database in OFX format
 * @author Ngewi Fet <ngewi.fet></ngewi.fet>@gmail.com>
 * @author Yongxin Wang <fefe.wyx></fefe.wyx>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class OfxExporter : Exporter {
    /**
     * List of accounts in the expense report
     */
    private var mAccountsList: List<Account>? = null

    /**
     * Builds an XML representation of the [Account]s and [Transaction]s in the database
     */
    constructor(params: ExportParams?) : super(params!!, null) {
        LOG_TAG = "OfxExporter"
    }

    /**
     * Overloaded constructor. Initializes the export parameters and the database to export
     * @param params Export options
     * @param db SQLiteDatabase to export
     */
    constructor(params: ExportParams?, db: SQLiteDatabase?) : super(params!!, db) {
        LOG_TAG = "OfxExporter"
    }

    /**
     * Converts all expenses into OFX XML format and adds them to the XML document
     * @param doc DOM document of the OFX expenses.
     * @param parent Parent node for all expenses in report
     */
    private fun generateOfx(doc: Document, parent: Element) {
        val transactionUid = doc.createElement(OfxHelper.TAG_TRANSACTION_UID)
        //unsolicited because the data exported is not as a result of a request
        transactionUid.appendChild(doc.createTextNode(OfxHelper.UNSOLICITED_TRANSACTION_ID))
        val statementTransactionResponse = doc.createElement(OfxHelper.TAG_STATEMENT_TRANSACTION_RESPONSE)
        statementTransactionResponse.appendChild(transactionUid)
        val bankmsgs = doc.createElement(OfxHelper.TAG_BANK_MESSAGES_V1)
        bankmsgs.appendChild(statementTransactionResponse)
        parent.appendChild(bankmsgs)
        val accountsDbAdapter = mAccountsDbAdapter!!
        for (account in mAccountsList!!) {
            if (account.transactionCount == 0) continue

            //do not export imbalance accounts for OFX transactions and double-entry disabled
            if (!GnuCashApplication.isDoubleEntryEnabled && account.mName!!.contains(mContext.getString(R.string.imbalance_account_name))) continue


            //add account details (transactions) to the XML document			
            account.toOfx(doc, statementTransactionResponse, mExportParams.exportStartTime)

            //mark as exported
            accountsDbAdapter.markAsExported(account.mUID!!)
        }
    }

    /**
     * Generate OFX export file from the transactions in the database
     * @return String containing OFX export
     * @throws ExporterException
     */
    @Throws(ExporterException::class)
    private fun generateOfxExport(): String {
        val docFactory = DocumentBuilderFactory
            .newInstance()
        val docBuilder: DocumentBuilder = try {
            docFactory.newDocumentBuilder()
        } catch (e: ParserConfigurationException) {
            throw ExporterException(mExportParams, e)
        }
        val document = docBuilder.newDocument()
        val root = document.createElement("OFX")
        val pi = document.createProcessingInstruction("OFX", OfxHelper.OFX_HEADER)
        document.appendChild(pi)
        document.appendChild(root)
        generateOfx(document, root)
        val useXmlHeader = PreferenceManager.getDefaultSharedPreferences(mContext)
            .getBoolean(mContext.getString(R.string.key_xml_ofx_header), false)
        PreferencesHelper.lastExportTime = TimestampHelper.timestampFromNow
        val stringWriter = StringWriter()
        //if we want SGML OFX headers, write first to string and then prepend header
        return if (useXmlHeader) {
            write(document, stringWriter, false)
            stringWriter.toString()
        } else {
            val ofxNode = document.getElementsByTagName("OFX").item(0)
            write(ofxNode, stringWriter, true)
            """
     ${OfxHelper.OFX_SGML_HEADER}
     $stringWriter
     """.trimIndent()
        }
    }

    @Throws(ExporterException::class)
    override fun generateExport(): List<String> {
        mAccountsList = mAccountsDbAdapter!!.getExportableAccounts(mExportParams.exportStartTime)
        if (mAccountsList.isNullOrEmpty()) return ArrayList() // Nothing to export, so no files generated
        var writer: BufferedWriter? = null
        try {
            val file = File(exportCacheFilePath)
            writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file), "UTF-8"))
            writer.write(generateOfxExport())
        } catch (e: IOException) {
            throw ExporterException(mExportParams, e)
        } finally {
            if (writer != null) {
                try {
                    writer.close()
                } catch (e: IOException) {
                    throw ExporterException(mExportParams, e)
                }
            }
        }
        val exportedFiles: MutableList<String> = ArrayList()
        exportedFiles.add(exportCacheFilePath)
        return exportedFiles
    }

    /**
     * Writes out the document held in `node` to `outputWriter`
     * @param node [Node] containing the OFX document structure. Usually the parent node
     * @param outputWriter [java.io.Writer] to use in writing the file to stream
     * @param omitXmlDeclaration Flag which causes the XML declaration to be omitted
     */
    private fun write(node: Node, outputWriter: Writer, omitXmlDeclaration: Boolean) {
        try {
            val transformerFactory = TransformerFactory
                .newInstance()
            val transformer = transformerFactory.newTransformer()
            val source = DOMSource(node)
            val result = StreamResult(outputWriter)
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            if (omitXmlDeclaration) {
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            }
            transformer.transform(source, result)
        } catch (tfException: TransformerException) {
            Log.e(LOG_TAG, tfException.message!!)
            Crashlytics.logException(tfException)
        }
    }

    /**
     * Returns the MIME type for this exporter.
     * @return MIME type as string
     */
    override val exportMimeType: String
        get() = "text/xml"
}