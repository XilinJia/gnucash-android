package org.gnucash.android.util

import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.ui.account.AccountsActivity.Companion.start

/**
 * Utility class for common operations involving books
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
object BookUtils {
    /**
     * Activates the book with unique identifer `bookUID`, and refreshes the database adapters
     * @param bookUID GUID of the book to be activated
     */
    @JvmStatic
    fun activateBook(bookUID: String) {
        GnuCashApplication.booksDbAdapter!!.setActive(bookUID)
        GnuCashApplication.initializeDatabaseAdapters()
    }

    /**
     * Loads the book with GUID `bookUID` and opens the AccountsActivity
     * @param bookUID GUID of the book to be loaded
     */
    @JvmStatic
    fun loadBook(bookUID: String) {
        activateBook(bookUID)
        start(GnuCashApplication.appContext!!)
    }
}