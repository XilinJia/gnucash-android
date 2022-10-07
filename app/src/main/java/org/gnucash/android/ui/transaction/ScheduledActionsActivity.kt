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
package org.gnucash.android.ui.transaction

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayout.TabLayoutOnPageChangeListener
import org.gnucash.android.R
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.common.BaseDrawerActivity

/**
 * Activity for displaying scheduled actions
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class ScheduledActionsActivity : BaseDrawerActivity() {
    var mViewPager: ViewPager? = null
    override val contentView: Int
        get() = R.layout.activity_scheduled_events
    override val titleRes: Int
        get() = R.string.nav_menu_scheduled_actions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tabLayout = findViewById<View>(R.id.tab_layout) as TabLayout
        tabLayout.addTab(tabLayout.newTab().setText(R.string.title_scheduled_transactions))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.title_scheduled_exports))
        tabLayout.tabGravity = TabLayout.GRAVITY_FILL
        mViewPager = findViewById<View>(R.id.pager) as ViewPager

        //show the simple accounts list
        val mPagerAdapter: PagerAdapter = ScheduledActionsViewPager(supportFragmentManager)
        mViewPager!!.adapter = mPagerAdapter
        mViewPager!!.addOnPageChangeListener(TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.setOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                mViewPager!!.currentItem = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                //nothing to see here, move along
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                //nothing to see here, move along
            }
        })
    }

    /**
     * View pager adapter for managing the scheduled action views
     */
    private inner class ScheduledActionsViewPager(fm: FragmentManager?) : FragmentStatePagerAdapter(
        fm!!
    ) {
        override fun getPageTitle(position: Int): CharSequence? {
            return when (position) {
                INDEX_SCHEDULED_TRANSACTIONS -> getString(R.string.title_scheduled_transactions)
                INDEX_SCHEDULED_EXPORTS -> getString(R.string.title_scheduled_exports)
                else -> super.getPageTitle(position)
            }
        }

        override fun getItem(position: Int): Fragment {
            when (position) {
                INDEX_SCHEDULED_TRANSACTIONS -> return ScheduledActionsListFragment.getInstance(ScheduledAction.ActionType.TRANSACTION)
                INDEX_SCHEDULED_EXPORTS -> return ScheduledActionsListFragment.getInstance(ScheduledAction.ActionType.BACKUP)
            }
            return ScheduledActionsListFragment.getInstance(ScheduledAction.ActionType.TRANSACTION) // added by XJ
        }

        override fun getCount(): Int {
            return 2
        }
    }

    companion object {
        const val INDEX_SCHEDULED_TRANSACTIONS = 0
        const val INDEX_SCHEDULED_EXPORTS = 1
    }
}