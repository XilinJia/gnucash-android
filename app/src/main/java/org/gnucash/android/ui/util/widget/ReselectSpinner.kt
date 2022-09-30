package org.gnucash.android.ui.util.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSpinner

/**
 * Spinner which fires OnItemSelectedListener even when an item is reselected.
 * Normal Spinners only fire item selected notifications when the selected item changes.
 *
 * This is used in `ReportsActivity` for the time range and in the [ExportFormFragment]
 *
 * It could happen that the selected item is fired twice especially if the item is the first in the list.
 * The Android system does this internally. In order to capture the first one, check whether the view parameter
 * of [android.widget.AdapterView.OnItemSelectedListener.onItemSelected] is null.
 * That would represent the first call during initialization of the views. This call can be ignored.
 * See [ExportFormFragment.bindViewListeners] for an example
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 *
 */
class ReselectSpinner : AppCompatSpinner {
    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!, attrs, defStyleAttr
    )

    override fun setSelection(position: Int) {
        val sameSelected = selectedItemPosition == position
        super.setSelection(position)
        if (sameSelected) {
            val listener = onItemSelectedListener
            listener?.onItemSelected(this, selectedView, position, selectedItemId)
        }
    }
}