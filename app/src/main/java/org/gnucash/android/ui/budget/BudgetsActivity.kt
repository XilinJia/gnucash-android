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
package org.gnucash.android.ui.budget

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.ui.common.BaseDrawerActivity
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument

/**
 * Activity for managing display and editing of budgets
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class BudgetsActivity : BaseDrawerActivity() {
    override val contentView: Int
        get() = R.layout.activity_budgets
    override val titleRes: Int
        get() = R.string.title_budgets

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val fragmentManager = supportFragmentManager
            val fragmentTransaction = fragmentManager
                .beginTransaction()
            fragmentTransaction.replace(R.id.fragment_container, BudgetListFragment())
            fragmentTransaction.commit()
        }
    }

    /**
     * Callback when create budget floating action button is clicked
     * @param view View which was clicked
     */
    fun onCreateBudgetClick(view: View?) {
        val addAccountIntent = Intent(this@BudgetsActivity, FormActivity::class.java)
        addAccountIntent.action = Intent.ACTION_INSERT_OR_EDIT
        addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name)
        startActivityForResult(addAccountIntent, REQUEST_CREATE_BUDGET)
    }

    companion object {
        const val REQUEST_CREATE_BUDGET = 0xA

        /**
         * Returns a color between red and green depending on the value parameter
         * @param value Value between 0 and 1 indicating the red to green ratio
         * @return Color between red and green
         */
        fun getBudgetProgressColor(value: Double): Int {
            return GnuCashApplication.darken(Color.HSVToColor(floatArrayOf(value.toFloat() * 120f, 1f, 1f)))
        }
    }
}