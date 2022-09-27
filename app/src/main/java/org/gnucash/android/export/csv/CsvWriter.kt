/*
 * Copyright (c) 2018 Semyannikov Gleb <nightdevgame@gmail.com>
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
package org.gnucash.android.export.csv

import java.io.BufferedWriter
import java.io.IOException
import java.io.Writer

/**
 * Format data to be CSV-compatible
 *
 * @author Semyannikov Gleb <nightdevgame></nightdevgame>@gmail.com>
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class CsvWriter : BufferedWriter {
    private var separator = ","

    constructor(writer: Writer?) : super(writer) {}
    constructor(writer: Writer?, separator: String) : super(writer) {
        this.separator = separator
    }

    @Throws(IOException::class)
    override fun write(str: String) {
        this.write(str, 0, str.length)
    }

    /**
     * Writes a CSV token and the separator to the underlying output stream.
     *
     * The token **MUST NOT** not contain the CSV separator. If the separator is found in the token, then
     * the token will be escaped as specified by RFC 4180
     * @param token Token to be written to file
     * @throws IOException if the token could not be written to the underlying stream
     */
    @Throws(IOException::class)
    fun writeToken(token: String?) {
        var token = token
        if (token == null || token.isEmpty()) {
            write(separator)
        } else {
            token = escape(token)
            write(token + separator)
        }
    }

    /**
     * Escape any CSV separators by surrounding the token in double quotes
     * @param token String token to be written to CSV
     * @return Escaped CSV token
     */
    private fun escape(token: String): String {
        return if (token.contains(separator)) {
            "\"" + token + "\""
        } else token
    }

    /**
     * Writes a token to the CSV file and appends end of line to it.
     *
     * The token **MUST NOT** not contain the CSV separator. If the separator is found in the token, then
     * the token will be escaped as specified by RFC 4180
     * @param token The token to be written to the file
     * @throws IOException if token could not be written to underlying writer
     */
    @Throws(IOException::class)
    fun writeEndToken(token: String?) {
        if (token != null && !token.isEmpty()) {
            write(escape(token))
        }
        newLine()
    }
}