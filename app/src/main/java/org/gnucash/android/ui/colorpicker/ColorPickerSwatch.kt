/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.ui.colorpicker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import org.gnucash.android.R

/**
 * Creates a circular swatch of a specified color.  Adds a checkmark if marked as checked.
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class ColorPickerSwatch(
    context: Context?, private val mColor: Int, checked: Boolean,
    private val mOnColorSelectedListener: OnColorSelectedListener?
) : FrameLayout(
    context!!
), View.OnClickListener {
    private val mSwatchImage: ImageView
    private val mCheckmarkImage: ImageView

    /**
     * Interface for a callback when a color square is selected.
     */
    interface OnColorSelectedListener {
        /**
         * Called when a specific color square has been selected.
         */
        fun onColorSelected(color: Int)
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.color_picker_swatch, this)
        mSwatchImage = findViewById<View>(R.id.color_picker_swatch) as ImageView
        mCheckmarkImage = findViewById<View>(R.id.color_picker_checkmark) as ImageView
        setColor(mColor)
        setChecked(checked)
        setOnClickListener(this)
    }

    protected fun setColor(color: Int) {
        val colorDrawable = arrayOf(context.resources.getDrawable(R.drawable.color_picker_swatch))
        mSwatchImage.setImageDrawable(ColorStateDrawable(colorDrawable, color))
    }

    private fun setChecked(checked: Boolean) {
        if (checked) {
            mCheckmarkImage.visibility = VISIBLE
        } else {
            mCheckmarkImage.visibility = GONE
        }
    }

    override fun onClick(v: View) {
        mOnColorSelectedListener?.onColorSelected(mColor)
    }
}