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

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import androidx.fragment.app.DialogFragment
import org.gnucash.android.R
import org.gnucash.android.ui.colorpicker.ColorPickerSwatch.OnColorSelectedListener

/**
 * A dialog which takes in as input an array of colors and creates a palette allowing the user to
 * select a specific color swatch, which invokes a listener.
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class ColorPickerDialog : DialogFragment(), OnColorSelectedListener {
    private var mAlertDialog: AlertDialog? = null
    private var mTitleResId = R.string.color_picker_default_title
    var colors: IntArray? = null
        private set
    private var mSelectedColor = 0
    private var mColumns = 0
    private var mSize = 0
    private var mPalette: ColorPickerPalette? = null
    private var mProgress: ProgressBar? = null
    private var mListener: OnColorSelectedListener? = null
    fun initialize(titleResId: Int, colors: IntArray, selectedColor: Int, columns: Int, size: Int) {
        setArguments(titleResId, columns, size)
        setColors(colors, selectedColor)
    }

    fun setArguments(titleResId: Int, columns: Int, size: Int) {
        val bundle = Bundle()
        bundle.putInt(KEY_TITLE_ID, titleResId)
        bundle.putInt(KEY_COLUMNS, columns)
        bundle.putInt(KEY_SIZE, size)
        arguments = bundle
    }

    fun setOnColorSelectedListener(listener: OnColorSelectedListener?) {
        mListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            mTitleResId = arguments!!.getInt(KEY_TITLE_ID)
            mColumns = arguments!!.getInt(KEY_COLUMNS)
            mSize = arguments!!.getInt(KEY_SIZE)
        }
        if (savedInstanceState != null) {
            colors = savedInstanceState.getIntArray(KEY_COLORS)
            mSelectedColor = (savedInstanceState.getSerializable(KEY_SELECTED_COLOR) as Int?)!!
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity: Activity? = activity
        val view = LayoutInflater.from(getActivity()).inflate(R.layout.color_picker_dialog, null)
        mProgress = view.findViewById<View>(android.R.id.progress) as ProgressBar
        mPalette = view.findViewById<View>(R.id.color_picker) as ColorPickerPalette
        mPalette!!.init(mSize, mColumns, this)
        if (colors != null) {
            showPaletteView()
        }
        mAlertDialog = AlertDialog.Builder(activity)
            .setTitle(mTitleResId)
            .setView(view)
            .create()
        return mAlertDialog!!
    }

    override fun onColorSelected(color: Int) {
        if (mListener != null) {
            mListener!!.onColorSelected(color)
        }
        if (targetFragment is OnColorSelectedListener) {
            val listener = targetFragment as OnColorSelectedListener?
            listener!!.onColorSelected(color)
        }
        if (color != mSelectedColor) {
            mSelectedColor = color
            // Redraw palette to show checkmark on newly selected color before dismissing.
            mPalette!!.drawPalette(colors, mSelectedColor)
        }
        dismiss()
    }

    fun showPaletteView() {
        if (mProgress != null && mPalette != null) {
            mProgress!!.visibility = View.GONE
            refreshPalette()
            mPalette!!.visibility = View.VISIBLE
        }
    }

    fun showProgressBarView() {
        if (mProgress != null && mPalette != null) {
            mProgress!!.visibility = View.VISIBLE
            mPalette!!.visibility = View.GONE
        }
    }

    fun setColors(colors: IntArray, selectedColor: Int) {
        if (!this.colors.contentEquals(colors) || mSelectedColor != selectedColor) {
            this.colors = colors
            mSelectedColor = selectedColor
            refreshPalette()
        }
    }

    @JvmName("setColors1")
    fun setColors(colors: IntArray) {
        if (!this.colors.contentEquals(colors)) {
            this.colors = colors
            refreshPalette()
        }
    }

    private fun refreshPalette() {
        if (mPalette != null && colors != null) {
            mPalette!!.drawPalette(colors, mSelectedColor)
        }
    }

    var selectedColor: Int
        get() = mSelectedColor
        set(color) {
            if (mSelectedColor != color) {
                mSelectedColor = color
                refreshPalette()
            }
        }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(KEY_COLORS, colors)
        outState.putSerializable(KEY_SELECTED_COLOR, mSelectedColor)
    }

    companion object {
        const val SIZE_LARGE = 1
        const val SIZE_SMALL = 2
        private const val KEY_TITLE_ID = "title_id"
        private const val KEY_COLORS = "colors"
        private const val KEY_SELECTED_COLOR = "selected_color"
        private const val KEY_COLUMNS = "columns"
        private const val KEY_SIZE = "size"
        fun newInstance(
            titleResId: Int, colors: IntArray, selectedColor: Int,
            columns: Int, size: Int
        ): ColorPickerDialog {
            val ret = ColorPickerDialog()
            ret.initialize(titleResId, colors, selectedColor, columns, size)
            return ret
        }
    }
}