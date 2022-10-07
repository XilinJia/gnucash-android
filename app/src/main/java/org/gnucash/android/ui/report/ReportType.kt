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
package org.gnucash.android.ui.report

import android.content.Context
import androidx.annotation.ColorRes
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication

/**
 * Different types of reports
 *
 * This class also contains mappings for the reports of the different types which are available
 * in the system. When adding a new report, make sure to add a mapping in the constructor
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
enum class ReportType(index: Int) {
    PIE_CHART(0), BAR_CHART(1), LINE_CHART(2), TEXT(3), NONE(4);

    var mReportTypeMap: MutableMap<String, Class<*>> = HashMap()
    var mValue = 4

    init {
        mValue = index
        val context: Context = GnuCashApplication.appContext!!
        when (index) {
            0 -> mReportTypeMap[context.getString(R.string.title_pie_chart)] = PieChartFragment::class.java
            1 -> mReportTypeMap[context.getString(R.string.title_bar_chart)] = StackedBarChartFragment::class.java
            2 -> mReportTypeMap[context.getString(R.string.title_cash_flow_report)] =
                CashFlowLineChartFragment::class.java

            3 -> mReportTypeMap[context.getString(R.string.title_balance_sheet_report)] =
                BalanceSheetFragment::class.java

            4 -> {}
        }
    }

    /**
     * Returns the toolbar color to be used for this report type
     * @return Color resource
     */
    @get:ColorRes
    val titleColor: Int
        get() = when (mValue) {
            0 -> R.color.account_green
            1 -> R.color.account_red
            2 -> R.color.account_blue
            3 -> R.color.account_purple
            4 -> R.color.theme_primary
            else -> R.color.theme_primary
        }
    val reportNames: List<String>
        get() = ArrayList(mReportTypeMap.keys)

    fun getFragment(name: String): BaseReportFragment? {
        var fragment: BaseReportFragment? = null
        try {
            fragment = mReportTypeMap[name]!!.newInstance() as BaseReportFragment
        } catch (e: InstantiationException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        }
        return fragment
    }
}