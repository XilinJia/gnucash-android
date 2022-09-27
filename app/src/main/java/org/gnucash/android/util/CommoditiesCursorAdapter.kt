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
package org.gnucash.android.util

import android.R
import android.content.Context
import android.database.Cursor
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.cursoradapter.widget.SimpleCursorAdapter
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.CommoditiesDbAdapter.Companion.instance

/**
 * Cursor adapter for displaying list of commodities.
 *
 * You should provide the layout and the layout should contain a view with the id `android:id/text1`,
 * which is where the name of the commodity will be displayed
 *
 * The list is sorted by the currency code (which is also displayed first before the full name)
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class CommoditiesCursorAdapter(context: Context?, @LayoutRes itemLayoutResource: Int) : SimpleCursorAdapter(
    context,
    itemLayoutResource,
    instance.fetchAllRecords(DatabaseSchema.CommodityEntry.COLUMN_MNEMONIC + " ASC"),
    arrayOf(DatabaseSchema.CommodityEntry.COLUMN_FULLNAME),
    intArrayOf(
        R.id.text1
    ),
    0
) {
    override fun bindView(view: View, context: Context, cursor: Cursor) {
        val textView = view.findViewById<View>(R.id.text1) as TextView
        textView.ellipsize = TextUtils.TruncateAt.MIDDLE
        val currencyName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.CommodityEntry.COLUMN_FULLNAME))
        val currencyCode = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.CommodityEntry.COLUMN_MNEMONIC))
        textView.text = "$currencyCode - $currencyName"
    }
}