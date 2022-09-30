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
package org.gnucash.android.ui.wizard

import android.content.Context
import com.tech.freak.wizardpager.model.AbstractWizardModel
import com.tech.freak.wizardpager.model.BranchPage
import com.tech.freak.wizardpager.model.PageList
import com.tech.freak.wizardpager.model.SingleFixedChoicePage
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import java.util.*

/**
 * Wizard displayed upon first run of the application for setup
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class FirstRunWizardModel(context: Context?) : AbstractWizardModel(context) {
    override fun onNewRootPageList(): PageList {
        val defaultCurrencyCode = GnuCashApplication.defaultCurrencyCode
        val defaultCurrencyPage = BranchPage(this, mContext.getString(R.string.wizard_title_default_currency))
        val currencies = arrayOf(defaultCurrencyCode, "CHF", "EUR", "GBP", "USD")
        val currencySet: Set<String> = TreeSet(mutableListOf(*currencies))
        defaultCurrencyPage.setChoices(*currencySet.toTypedArray())
        defaultCurrencyPage.isRequired = true
        defaultCurrencyPage.setValue(defaultCurrencyCode)
        val defaultAccountsPage = SingleFixedChoicePage(this, mContext.getString(R.string.wizard_title_account_setup))
            .setChoices(
                mContext.getString(R.string.wizard_option_create_default_accounts),
                mContext.getString(R.string.wizard_option_import_my_accounts),
                mContext.getString(R.string.wizard_option_let_me_handle_it)
            )
            .setValue(mContext.getString(R.string.wizard_option_create_default_accounts))
            .setRequired(true)
        for (currency in currencySet) {
            defaultCurrencyPage.addBranch(currency, defaultAccountsPage)
        }
        defaultCurrencyPage.addBranch(
            mContext.getString(R.string.wizard_option_currency_other),
            CurrencySelectPage(this, mContext.getString(R.string.wizard_title_select_currency)), defaultAccountsPage
        ).isRequired = true
        return PageList(
            WelcomePage(this, mContext.getString(R.string.wizard_title_welcome_to_gnucash)),
            defaultCurrencyPage,
            SingleFixedChoicePage(this, mContext.getString(R.string.wizard_title_feedback_options))
                .setChoices(
                    mContext.getString(R.string.wizard_option_auto_send_crash_reports),
                    mContext.getString(R.string.wizard_option_disable_crash_reports)
                )
                .setRequired(true)
        )
    }
}