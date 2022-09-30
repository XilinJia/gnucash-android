/*
 * Copyright 2012 Roman Nurik
 * Copyright 2012 Ngewi Fet
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

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener
import butterknife.BindView
import butterknife.ButterKnife
import com.tech.freak.wizardpager.model.AbstractWizardModel
import com.tech.freak.wizardpager.model.ModelCallbacks
import com.tech.freak.wizardpager.model.Page
import com.tech.freak.wizardpager.model.ReviewItem
import com.tech.freak.wizardpager.ui.PageFragmentCallbacks
import com.tech.freak.wizardpager.ui.ReviewFragment
import com.tech.freak.wizardpager.ui.StepPagerStrip
import com.tech.freak.wizardpager.ui.StepPagerStrip.OnPageSelectedListener
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.BooksDbAdapter.Companion.instance
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.account.AccountsActivity.Companion.createDefaultAccounts
import org.gnucash.android.ui.account.AccountsActivity.Companion.importXmlFileFromIntent
import org.gnucash.android.ui.account.AccountsActivity.Companion.removeFirstRunFlag
import org.gnucash.android.ui.account.AccountsActivity.Companion.start
import org.gnucash.android.ui.account.AccountsActivity.Companion.startXmlFileChooser
import org.gnucash.android.ui.util.TaskDelegate

/**
 * Activity for managing the wizard displayed upon first run of the application
 * @author Xilin Jia <https:></https:>//github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class FirstRunWizardActivity : AppCompatActivity(), PageFragmentCallbacks, ReviewFragment.Callbacks, ModelCallbacks {
    @JvmField
    @BindView(R.id.pager)
    var mPager: ViewPager? = null
    private var mPagerAdapter: MyPagerAdapter? = null
    private var mEditingAfterReview = false
    private var mWizardModel: AbstractWizardModel? = null
    private var mConsumePageSelectedEvent = false

    @JvmField
    @BindView(R.id.btn_save)
    var mNextButton: AppCompatButton? = null

    @JvmField
    @BindView(R.id.btn_cancel)
    var mPrevButton: Button? = null

    @JvmField
    @BindView(R.id.strip)
    var mStepPagerStrip: StepPagerStrip? = null
    private var mCurrentPageSequence: List<Page>? = null
    private var mAccountOptions: String? = null
    private var mCurrencyCode: String? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        // we need to construct the wizard model before we call super.onCreate, because it's used in
        // onGetPage (which is indirectly called through super.onCreate if savedInstanceState is not
        // null)
        mWizardModel = createWizardModel(savedInstanceState)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_first_run_wizard)
        ButterKnife.bind(this)
        title = getString(R.string.title_setup_gnucash)
        mPagerAdapter = MyPagerAdapter(supportFragmentManager)
        mPager!!.adapter = mPagerAdapter
        mStepPagerStrip!!.setOnPageSelectedListener(OnPageSelectedListener { position ->
                var position = position
                position = (mPagerAdapter!!.count - 1).coerceAtMost(position)
                if (mPager!!.currentItem != position) {
                    mPager!!.currentItem = position
                }
            })
        mPager!!.setOnPageChangeListener(object : SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                mStepPagerStrip!!.setCurrentPage(position)
                if (mConsumePageSelectedEvent) {
                    mConsumePageSelectedEvent = false
                    return
                }
                mEditingAfterReview = false
                updateBottomBar()
            }
        })
        mNextButton!!.setOnClickListener {
            if (mPager!!.currentItem == mCurrentPageSequence!!.size) {
                val reviewItems = ArrayList<ReviewItem>()
                for (page in mCurrentPageSequence!!) {
                    page.getReviewItems(reviewItems)
                }
                mCurrencyCode = GnuCashApplication.defaultCurrencyCode
                mAccountOptions = getString(R.string.wizard_option_let_me_handle_it) //default value, do nothing
                var feedbackOption = getString(R.string.wizard_option_disable_crash_reports)
                for (reviewItem in reviewItems) {
                    when (reviewItem.title) {
                        getString(R.string.wizard_title_default_currency) -> {
                            mCurrencyCode = reviewItem.displayValue
                        }
                        getString(R.string.wizard_title_select_currency) -> {
                            mCurrencyCode = reviewItem.displayValue
                        }
                        getString(R.string.wizard_title_account_setup) -> {
                            mAccountOptions = reviewItem.displayValue
                        }
                        getString(R.string.wizard_title_feedback_options) -> {
                            feedbackOption = reviewItem.displayValue
                        }
                    }
                }
                GnuCashApplication.setDefaultCurrencyCode(mCurrencyCode!!)
                val preferences = PreferenceManager.getDefaultSharedPreferences(this@FirstRunWizardActivity)
                val preferenceEditor = preferences.edit()
                if (feedbackOption == getString(R.string.wizard_option_auto_send_crash_reports)) {
                    preferenceEditor.putBoolean(getString(R.string.key_enable_crashlytics), true)
                } else {
                    preferenceEditor.putBoolean(getString(R.string.key_enable_crashlytics), false)
                }
                preferenceEditor.apply()
                createAccountsAndFinish()
            } else {
                if (mEditingAfterReview) {
                    mPager!!.currentItem = mPagerAdapter!!.count - 1
                } else {
                    mPager!!.currentItem = mPager!!.currentItem + 1
                }
            }
        }
        mPrevButton!!.setText(R.string.wizard_btn_back)
        val v = TypedValue()
        theme.resolveAttribute(
            android.R.attr.textAppearanceMedium, v,
            true
        )
        mPrevButton!!.setTextAppearance(this, v.resourceId)
        mNextButton!!.setTextAppearance(this, v.resourceId)
        mPrevButton!!.setOnClickListener { mPager!!.currentItem = mPager!!.currentItem - 1 }
        onPageTreeChanged()
        updateBottomBar()
    }

    /**
     * Create the wizard model for the activity, taking into accoun the savedInstanceState if it
     * exists (and if it contains a "model" key that we can use).
     * @param savedInstanceState    the instance state available in {[.onCreate]}
     * @return  an appropriate wizard model for this activity
     */
    private fun createWizardModel(savedInstanceState: Bundle?): AbstractWizardModel {
        val model: AbstractWizardModel = FirstRunWizardModel(this)
        if (savedInstanceState != null) {
            val wizardModel = savedInstanceState.getBundle("model")
            if (wizardModel != null) {
                model.load(wizardModel)
            }
        }
        model.registerListener(this)
        return model
    }

    /**
     * Create accounts depending on the user preference (import or default set) and finish this activity
     *
     * This method also removes the first run flag from the application
     */
    private fun createAccountsAndFinish() {
        removeFirstRunFlag()
        when (mAccountOptions) {
            getString(R.string.wizard_option_create_default_accounts) -> {
                //save the UID of the active book, and then delete it after successful import
                val bookUID = instance.activeBookUID
                createDefaultAccounts(mCurrencyCode, this@FirstRunWizardActivity)
                instance.deleteBook(bookUID) //a default book is usually created
                finish()
            }
            getString(R.string.wizard_option_import_my_accounts) -> {
                startXmlFileChooser(this)
            }
            else -> { //user prefers to handle account creation themselves
                start(this)
                finish()
            }
        }
    }

    override fun onPageTreeChanged() {
        mCurrentPageSequence = mWizardModel!!.currentPageSequence
        recalculateCutOffPage()
        mStepPagerStrip!!.setPageCount(mCurrentPageSequence!!.size + 1) // + 1 =
        // review
        // step
        mPagerAdapter!!.notifyDataSetChanged()
        updateBottomBar()
    }

    private fun updateBottomBar() {
        val position = mPager!!.currentItem
        val res = resources
        if (position == mCurrentPageSequence!!.size) {
            mNextButton!!.setText(R.string.btn_wizard_finish)
            mNextButton!!.setBackgroundDrawable(
                ColorDrawable(ContextCompat.getColor(this, R.color.theme_accent))
            )
            mNextButton!!.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        } else {
            mNextButton!!.setText(if (mEditingAfterReview) R.string.review else R.string.btn_wizard_next)
            mNextButton!!.setBackgroundDrawable(
                ColorDrawable(ContextCompat.getColor(this, android.R.color.transparent))
            )
            mNextButton!!.setTextColor(ContextCompat.getColor(this, R.color.theme_accent))
            mNextButton!!.isEnabled = position != mPagerAdapter!!.cutOffPage
        }
        mPrevButton!!.visibility = if (position <= 0) View.INVISIBLE else View.VISIBLE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)   // by XJ
        when (requestCode) {
            AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE -> if (resultCode == RESULT_OK && data != null) {
//                importXmlFileFromIntent(this, data) { finish() }
                importXmlFileFromIntent(this, data,
                    object : TaskDelegate {
                        override fun onTaskComplete() {
                            finish()
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mWizardModel!!.unregisterListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("model", mWizardModel!!.save())
    }

    override fun onGetModel(): AbstractWizardModel {
        return mWizardModel!!
    }

    override fun onEditScreenAfterReview(key: String) {
        for (i in mCurrentPageSequence!!.indices.reversed()) {
            if (mCurrentPageSequence!![i].key == key) {
                mConsumePageSelectedEvent = true
                mEditingAfterReview = true
                mPager!!.currentItem = i
                updateBottomBar()
                break
            }
        }
    }

    override fun onPageDataChanged(page: Page) {
        if (page.isRequired) {
            if (recalculateCutOffPage()) {
                mPagerAdapter!!.notifyDataSetChanged()
                updateBottomBar()
            }
        }
    }

    override fun onGetPage(key: String): Page {
        return mWizardModel!!.findByKey(key)
    }

    private fun recalculateCutOffPage(): Boolean {
        // Cut off the pager adapter at first required page that isn't completed
        var cutOffPage = mCurrentPageSequence!!.size + 1
        for (i in mCurrentPageSequence!!.indices) {
            val page = mCurrentPageSequence!![i]
            if (page.isRequired && !page.isCompleted) {
                cutOffPage = i
                break
            }
        }
        if (mPagerAdapter!!.cutOffPage != cutOffPage) {
            mPagerAdapter!!.cutOffPage = cutOffPage
            return true
        }
        return false
    }

    inner class MyPagerAdapter(fm: FragmentManager?) : FragmentStatePagerAdapter(
        fm!!
    ) {
        private var mCutOffPage = 0
        private var mPrimaryItem: Fragment? = null
        override fun getItem(i: Int): Fragment {
            return if (i >= mCurrentPageSequence!!.size) {
                ReviewFragment()
            } else mCurrentPageSequence!![i].createFragment()
        }

        override fun getItemPosition(`object`: Any): Int {
            // TODO: be smarter about this
            return if (`object` === mPrimaryItem) {
                // Re-use the current fragment (its position never changes)
                POSITION_UNCHANGED
            } else POSITION_NONE
        }

        override fun setPrimaryItem(
            container: ViewGroup, position: Int,
            `object`: Any
        ) {
            super.setPrimaryItem(container, position, `object`)
            mPrimaryItem = `object` as Fragment
        }

        override fun getCount(): Int {
            return (mCutOffPage + 1).coerceAtMost(if (mCurrentPageSequence == null) 1 else mCurrentPageSequence!!.size + 1)
        }

        var cutOffPage: Int
            get() = mCutOffPage
            set(cutOffPage) {
                var cutOffPage = cutOffPage
                if (cutOffPage < 0) {
                    cutOffPage = Int.MAX_VALUE
                }
                mCutOffPage = cutOffPage
            }
    }
}