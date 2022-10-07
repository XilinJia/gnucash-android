/*
 * Copyright (c) 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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
package org.gnucash.android.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import androidx.slidingpanelayout.widget.SlidingPaneLayout.PanelSlideListener
import butterknife.BindView
import butterknife.ButterKnife
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.appContext
import org.gnucash.android.db.adapter.BooksDbAdapter.Companion.instance
import org.gnucash.android.ui.passcode.PasscodeLockActivity

/**
 * Activity for unified preferences
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class PreferenceActivity : PasscodeLockActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    @JvmField
    @BindView(R.id.slidingpane_layout)
    var mSlidingPaneLayout: SlidingPaneLayout? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        ButterKnife.bind(this)
        mSlidingPaneLayout!!.setPanelSlideListener(object : PanelSlideListener {
            override fun onPanelSlide(panel: View, slideOffset: Float) {
                //nothing to see here, move along
            }

            override fun onPanelOpened(panel: View) {
                val actionBar = supportActionBar!!
                actionBar.setTitle(R.string.title_settings)
            }

            override fun onPanelClosed(panel: View) {
                //nothing to see here, move along
            }
        })
        val action = intent.action
        if (action != null && action == ACTION_MANAGE_BOOKS) {
            loadFragment(BookManagerFragment())
            mSlidingPaneLayout!!.closePane()
        } else {
            mSlidingPaneLayout!!.openPane()
            loadFragment(GeneralPreferenceFragment())
        }
        val actionBar = supportActionBar!!
        actionBar.setTitle(R.string.title_settings)
        actionBar.setHomeButtonEnabled(true)
        actionBar.setDisplayHomeAsUpEnabled(true)
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
//        val key = pref.key
        val fragment: Fragment? = try {
            val clazz = Class.forName(pref.fragment)
            clazz.newInstance() as Fragment
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
            //if we do not have a matching class, do nothing
            return false
        } catch (e: InstantiationException) {
            e.printStackTrace()
            return false
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
            return false
        }
        loadFragment(fragment)
        mSlidingPaneLayout!!.closePane()
        return false
    }

    /**
     * Load the provided fragment into the right pane, replacing the previous one
     * @param fragment BaseReportFragment instance
     */
    private fun loadFragment(fragment: Fragment?) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager
            .beginTransaction()
        fragmentTransaction.replace(R.id.fragment_container, fragment!!)
        fragmentTransaction.commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                val fm = fragmentManager
                if (fm.backStackEntryCount > 0) {
                    fm.popBackStack()
                } else {
                    finish()
                }
                true
            }

            else -> false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (mSlidingPaneLayout!!.isOpen) super.onBackPressed() else mSlidingPaneLayout!!.openPane()
    }

    companion object {
        const val ACTION_MANAGE_BOOKS = "org.gnucash.android.intent.action.MANAGE_BOOKS"

        /**
         * Returns the shared preferences file for the currently active book.
         * Should be used instead of [PreferenceManager.getDefaultSharedPreferences]
         * @return Shared preferences file
         */
        @JvmStatic
        val activeBookSharedPreferences: SharedPreferences
            get() = getBookSharedPreferences(instance.activeBookUID)

        /**
         * Return the [SharedPreferences] for a specific book
         * @param bookUID GUID of the book
         * @return Shared preferences
         */
        fun getBookSharedPreferences(bookUID: String?): SharedPreferences {
            val context = appContext
            return context!!.getSharedPreferences(bookUID, MODE_PRIVATE)
        }
    }
}