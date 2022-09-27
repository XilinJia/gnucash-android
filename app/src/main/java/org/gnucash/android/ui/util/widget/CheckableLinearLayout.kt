/*
 * Copyright (c) 2013 - 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.util.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.Checkable
import android.widget.LinearLayout

/**
 * An implementation of [android.widget.LinearLayout] which implements the [android.widget.Checkable] interface.
 * This layout keeps track of its checked state or alternatively queries its child views for any [View] which is Checkable.
 * If there is a Checkable child view, then that child view determines the check state of the whole layout.
 *
 *
 * This layout is designed for use with ListViews with a choice mode other than [android.widget.ListView.CHOICE_MODE_NONE].
 * Android requires the parent view of the row items in the list to be checkable in order to take advantage of the APIs
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class CheckableLinearLayout : LinearLayout, Checkable {
    /**
     * Checkable view which holds the checked state of the linear layout
     */
    private val mCheckable: Checkable? = null

    /**
     * Fallback check state of the linear layout if there is no [Checkable] amongst its child views.
     */
    private var mIsChecked = false

    constructor(context: Context?) : super(context) {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {}

    /**
     * Find any instance of a [Checkable] amongst the children of the linear layout and store a reference to it
     */
    override fun onFinishInflate() {
        super.onFinishInflate()

        //this prevents us from opening transactions since simply clicking on the item checks the checkable and
        //activates action mode.
//        mCheckable = findCheckableView(this);
    }

    /**
     * Iterates through the child views of `parent` to an arbitrary depth and returns the first
     * [Checkable] view found
     * @param parent ViewGroup in which to search for Checkable children
     * @return First [Checkable] child view of parent found
     */
    private fun findCheckableView(parent: ViewGroup): Checkable? {
        for (i in 0 until parent.childCount) {
            val childView = parent.getChildAt(i)
            if (childView is Checkable) return childView
            if (childView is ViewGroup) {
                val checkable = findCheckableView(childView)
                if (checkable != null) {
                    return checkable
                }
            }
        }
        return null
    }

    override fun setChecked(b: Boolean) {
        if (mCheckable != null) {
            mCheckable.isChecked = b
        } else {
            mIsChecked = b
        }
        refreshDrawableState()
    }

    override fun isChecked(): Boolean {
        return mCheckable?.isChecked ?: mIsChecked
    }

    override fun toggle() {
        if (mCheckable != null) {
            mCheckable.toggle()
        } else {
            mIsChecked = !mIsChecked
        }
        refreshDrawableState()
    }
}