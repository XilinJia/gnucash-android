/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.importer

import android.util.Log
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.util.PreferencesHelper
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

/**
 * Importer for Gnucash XML files and GNCA (GnuCash Android) XML files
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
object GncXmlImporter {
    /**
     * Parse GnuCash XML input and populates the database
     * @param gncXmlInputStream InputStream source of the GnuCash XML file
     * @return GUID of the book into which the XML was imported
     */
    @JvmStatic
    @Throws(ParserConfigurationException::class, SAXException::class, IOException::class)
    fun parse(gncXmlInputStream: InputStream?): String {
        val spf = SAXParserFactory.newInstance()
        val sp = spf.newSAXParser()
        val xr = sp.xmlReader
        val bos: BufferedInputStream
        val pb = PushbackInputStream(gncXmlInputStream, 2) //we need a pushbackstream to look ahead
        val signature = ByteArray(2)
        pb.read(signature) //read the signature
        pb.unread(signature) //push back the signature to the stream
        bos =
            if (signature[0] == 0x1f.toByte() && signature[1] == 0x8b.toByte()) //check if matches standard gzip magic number
                BufferedInputStream(GZIPInputStream(pb)) else BufferedInputStream(pb)

        //TODO: Set an error handler which can log errors
        Log.d(GncXmlImporter::class.java.simpleName, "Start import")
        val handler = GncXmlHandler()
        xr.contentHandler = handler
        val startTime = System.nanoTime()
        xr.parse(InputSource(bos))
        val endTime = System.nanoTime()
        Log.d(
            GncXmlImporter::class.java.simpleName,
            String.format("%d ns spent on importing the file", endTime - startTime)
        )
        val bookUID = handler.bookUID
        PreferencesHelper.setLastExportTime(
            TransactionsDbAdapter.instance.timestampOfLastModification,
            bookUID
        )
        return bookUID
    }
}