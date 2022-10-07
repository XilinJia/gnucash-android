/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2022 Xilin Jia https://github.com/XilinJia
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
import android.util.AttributeSet
import android.widget.QuickContactBadge
import org.gnucash.android.R


/**
 * The color square used as an entry point to launching the [ColorPickerDialog].
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class ColorSquare : QuickContactBadge {
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    override fun setBackgroundColor(color: Int) {
        val colorDrawable = arrayOf(
            context.resources.getDrawable(R.drawable.color_square)
        )
        setImageDrawable(ColorStateDrawable(colorDrawable, color))
    }
}