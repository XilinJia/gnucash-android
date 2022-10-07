/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.model.Commodity
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/**
 * XML stream handler for parsing currencies to add to the database
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class CommoditiesXmlHandler(db: SQLiteDatabase?) : DefaultHandler() {
    /**
     * List of commodities parsed from the XML file.
     * They will be all added to db at once at the end of the document
     */
    private val mCommodities: MutableList<Commodity>
    private var mCommoditiesDbAdapter: CommoditiesDbAdapter? = null

    init {
        mCommoditiesDbAdapter = db?.let { CommoditiesDbAdapter(it) } ?: GnuCashApplication.commoditiesDbAdapter
        mCommodities = ArrayList()
    }

    @Throws(SAXException::class)
    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
        if (qName == TAG_CURRENCY) {
            val isoCode = attributes.getValue(ATTR_ISO_CODE)
            val fullname = attributes.getValue(ATTR_FULL_NAME)
            val namespace = attributes.getValue(ATTR_NAMESPACE)
            val cusip = attributes.getValue(ATTR_EXCHANGE_CODE)
            //TODO: investigate how up-to-date the currency XML list is and use of parts-per-unit vs smallest-fraction.
            //some currencies like XAF have smallest fraction 100, but parts-per-unit of 1.
            // However java.util.Currency agrees only with the parts-per-unit although we use smallest-fraction in the app
            // This could lead to inconsistencies over time
            val smallestFraction = attributes.getValue(ATTR_SMALLEST_FRACTION)
            val localSymbol = attributes.getValue(ATTR_LOCAL_SYMBOL)
            val commodity = Commodity(fullname, isoCode, smallestFraction.toInt())
            commodity.mNamespace = Commodity.Namespace.valueOf(namespace)
            commodity.mCusip = cusip
            commodity.mLocalSymbol = localSymbol
            mCommodities.add(commodity)
        }
    }

    @Throws(SAXException::class)
    override fun endDocument() {
        mCommoditiesDbAdapter!!.bulkAddRecords(mCommodities, DatabaseAdapter.UpdateMethod.insert)
    }

    companion object {
        const val TAG_CURRENCY = "currency"
        const val ATTR_ISO_CODE = "isocode"
        const val ATTR_FULL_NAME = "fullname"
        const val ATTR_NAMESPACE = "namespace"
        const val ATTR_EXCHANGE_CODE = "exchange-code"
        const val ATTR_SMALLEST_FRACTION = "smallest-fraction"
        const val ATTR_LOCAL_SYMBOL = "local-symbol"
    }
}