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
package org.gnucash.android.ui.wizard

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.ListFragment
import butterknife.ButterKnife
import com.tech.freak.wizardpager.ui.PageFragmentCallbacks
import org.gnucash.android.R
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter.Companion.instance
import org.gnucash.android.util.CommoditiesCursorAdapter

/**
 * Displays a list of all currencies in the database and allows selection of one
 *
 * This fragment is intended for use with the first run wizard
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 * @see CurrencySelectPage
 *
 * @see FirstRunWizardActivity
 *
 * @see FirstRunWizardModel
 */
class CurrencySelectFragment : ListFragment() {
    private var mPage: CurrencySelectPage? = null
    private var mCallbacks: PageFragmentCallbacks? = null
    private var mCommoditiesDbAdapter: CommoditiesDbAdapter? = null
    var mPageKey: String? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_wizard_currency_select_page, container, false)
        ButterKnife.bind(this, view)
        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mPage = mCallbacks!!.onGetPage(mPageKey) as CurrencySelectPage
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val commoditiesCursorAdapter = CommoditiesCursorAdapter(activity, R.layout.list_item_commodity)
        listAdapter = commoditiesCursorAdapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        mCommoditiesDbAdapter = instance
    }

    @Deprecated("Deprecated in Java")
    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        if (activity !is PageFragmentCallbacks) {
            throw ClassCastException("Activity must implement PageFragmentCallbacks")
        }
        mCallbacks = activity
    }

    override fun onDetach() {
        super.onDetach()
        mCallbacks = null
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)
        val currencyCode = mCommoditiesDbAdapter!!.getMMnemonic(mCommoditiesDbAdapter!!.getUID(id)!!)
        mPage!!.data.putString(CurrencySelectPage.CURRENCY_CODE_DATA_KEY, currencyCode)
    }

    companion object {
        @JvmStatic
        fun newInstance(key: String?): CurrencySelectFragment {
            val fragment = CurrencySelectFragment()
            fragment.mPageKey = key
            return fragment
        }
    }
}