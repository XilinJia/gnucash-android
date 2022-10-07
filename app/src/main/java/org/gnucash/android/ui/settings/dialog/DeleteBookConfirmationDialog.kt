/*
 * Copyright (c) 2017 Àlex Magaz Graça <alexandre.magaz@gmail.com>
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
package org.gnucash.android.ui.settings.dialog

import android.app.Dialog
import android.os.Bundle
import org.gnucash.android.R
import org.gnucash.android.db.adapter.BooksDbAdapter.Companion.instance
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.util.BackupManager.backupBook

/**
 * Confirmation dialog for deleting a book.
 *
 * @author Àlex Magaz <alexandre.magaz></alexandre.magaz>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class DeleteBookConfirmationDialog : DoubleConfirmationDialog() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return dialogBuilder
            .setTitle(R.string.title_confirm_delete_book)
            .setIcon(R.drawable.ic_close_black_24dp)
            .setMessage(R.string.msg_all_book_data_will_be_deleted)
            .setPositiveButton(R.string.btn_delete_book) { _, _ ->
                val bookUID = arguments!!.getString("bookUID")
                backupBook(bookUID!!)
                instance.deleteBook(bookUID)
                (targetFragment as Refreshable?)!!.refresh()
            }
            .create()
    }

    companion object {
        @JvmStatic
        fun newInstance(bookUID: String?): DeleteBookConfirmationDialog {
            val frag = DeleteBookConfirmationDialog()
            val args = Bundle()
            args.putString("bookUID", bookUID)
            frag.arguments = args
            return frag
        }
    }
}