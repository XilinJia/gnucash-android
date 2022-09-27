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

import android.text.TextUtils
import androidx.fragment.app.Fragment
import com.tech.freak.wizardpager.model.ModelCallbacks
import com.tech.freak.wizardpager.model.Page
import com.tech.freak.wizardpager.model.ReviewItem
import org.gnucash.android.ui.wizard.CurrencySelectFragment.Companion.newInstance

/**
 * Page displaying all the commodities in the database
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class CurrencySelectPage(callbacks: ModelCallbacks?, title: String?) : Page(callbacks, title) {
    override fun createFragment(): Fragment {
        return newInstance(key)
    }

    override fun getReviewItems(arrayList: ArrayList<ReviewItem>) {
        arrayList.add(ReviewItem(title, mData.getString(CURRENCY_CODE_DATA_KEY), key))
    }

    override fun isCompleted(): Boolean {
        return !TextUtils.isEmpty(mData.getString(CURRENCY_CODE_DATA_KEY))
    }

    companion object {
        const val CURRENCY_CODE_DATA_KEY = "currency_code_data_key"
    }
}