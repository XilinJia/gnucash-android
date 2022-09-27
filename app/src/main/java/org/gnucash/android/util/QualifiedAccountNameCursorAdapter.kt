/*
 * Copyright (c) 2013 - 2014 Ngewi Fet <ngewif@gmail.com>
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

import android.content.Context
import android.database.Cursor
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.cursoradapter.widget.SimpleCursorAdapter
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.instance

/**
 * Cursor adapter which looks up the fully qualified account name and returns that instead of just the simple name.
 *
 * The fully qualified account name includes the parent hierarchy
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class QualifiedAccountNameCursorAdapter : SimpleCursorAdapter {
    /**
     * Initialize the Cursor adapter for account names using default spinner views
     * @param context Application context
     * @param cursor Cursor to accounts
     */
    constructor(context: Context?, cursor: Cursor?) : super(
        context,
        android.R.layout.simple_spinner_item,
        cursor,
        arrayOf<String>(DatabaseSchema.AccountEntry.COLUMN_FULL_NAME),
        intArrayOf(android.R.id.text1),
        0
    ) {
        setDropDownViewResource(R.layout.account_spinner_dropdown_item)
    }

    /**
     * Overloaded constructor. Specifies the view to use for displaying selected spinner text
     * @param context Application context
     * @param cursor Cursor to account data
     * @param selectedSpinnerItem Layout resource for selected item text
     */
    constructor(
        context: Context?, cursor: Cursor?,
        @LayoutRes selectedSpinnerItem: Int
    ) : super(
        context,
        selectedSpinnerItem,
        cursor,
        arrayOf<String>(DatabaseSchema.AccountEntry.COLUMN_FULL_NAME),
        intArrayOf(android.R.id.text1),
        0
    ) {
        setDropDownViewResource(R.layout.account_spinner_dropdown_item)
    }

    override fun bindView(view: View, context: Context, cursor: Cursor) {
        super.bindView(view, context, cursor)
        val textView = view.findViewById<View>(android.R.id.text1) as TextView
        textView.ellipsize = TextUtils.TruncateAt.MIDDLE
        val favIndex = cursor.getColumnIndex(DatabaseSchema.AccountEntry.COLUMN_FAVORITE)
        if (favIndex >= 0) {
            val isFavorite = cursor.getInt(favIndex)
            if (isFavorite == 0) {
                textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            } else {
                textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_star_black_18dp, 0)
            }
        }
    }

    /**
     * Returns the position of a given account in the adapter
     * @param accountUID GUID of the account
     * @return Position of the account or -1 if the account is not found
     */
    fun getPosition(accountUID: String): Int {
        val accountId = instance.getID(accountUID)
        for (pos in 0 until count) {
            if (getItemId(pos) == accountId) {
                return pos
            }
        }
        return -1
    }
}