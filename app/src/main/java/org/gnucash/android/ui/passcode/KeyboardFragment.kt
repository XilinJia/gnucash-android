/*
 * Copyright (c) 2014 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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
package org.gnucash.android.ui.passcode

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.gnucash.android.R

/**
 * Soft numeric keyboard for lock screen and passcode preference.
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets></olexandr.tyshkovets>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class KeyboardFragment : Fragment() {
    private var pass1: TextView? = null
    private var pass2: TextView? = null
    private var pass3: TextView? = null
    private var pass4: TextView? = null
    private var length = 0

    interface OnPasscodeEnteredListener {
        fun onPasscodeEntered(pass: String?)
    }

    private var listener: OnPasscodeEnteredListener? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_numeric_keyboard, container, false)
        pass1 = rootView.findViewById<View>(R.id.passcode1) as TextView
        pass2 = rootView.findViewById<View>(R.id.passcode2) as TextView
        pass3 = rootView.findViewById<View>(R.id.passcode3) as TextView
        pass4 = rootView.findViewById<View>(R.id.passcode4) as TextView
        rootView.findViewById<View>(R.id.one_btn).setOnClickListener { add("1") }
        rootView.findViewById<View>(R.id.two_btn).setOnClickListener { add("2") }
        rootView.findViewById<View>(R.id.three_btn).setOnClickListener { add("3") }
        rootView.findViewById<View>(R.id.four_btn).setOnClickListener { add("4") }
        rootView.findViewById<View>(R.id.five_btn).setOnClickListener { add("5") }
        rootView.findViewById<View>(R.id.six_btn).setOnClickListener { add("6") }
        rootView.findViewById<View>(R.id.seven_btn).setOnClickListener { add("7") }
        rootView.findViewById<View>(R.id.eight_btn).setOnClickListener { add("8") }
        rootView.findViewById<View>(R.id.nine_btn).setOnClickListener { add("9") }
        rootView.findViewById<View>(R.id.zero_btn).setOnClickListener { add("0") }
        rootView.findViewById<View>(R.id.delete_btn).setOnClickListener {
            when (length) {
                1 -> {
                    pass1?.text = null
                    length--
                }

                2 -> {
                    pass2?.text = null
                    length--
                }

                3 -> {
                    pass3?.text = null
                    length--
                }

                4 -> {
                    pass4?.text = null
                    length--
                }
            }
        }
        return rootView
    }

    @Deprecated("Deprecated in Java")
    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        listener = try {
            activity as OnPasscodeEnteredListener
        } catch (e: ClassCastException) {
            throw ClassCastException(
                activity.toString() + " must implement "
                        + OnPasscodeEnteredListener::class.java
            )
        }
    }

    private fun add(num: String) {
        when (length + 1) {
            1 -> {
                pass1!!.text = num
                length++
            }

            2 -> {
                pass2!!.text = num
                length++
            }

            3 -> {
                pass3!!.text = num
                length++
            }

            4 -> {
                pass4!!.text = num
                length++
                Handler().postDelayed({
                    listener!!.onPasscodeEntered(
                        pass1!!.text.toString() + pass2!!.text
                                + pass3!!.text + pass4!!.text
                    )
                    pass1?.text = null
                    pass2?.text = null
                    pass3?.text = null
                    pass4?.text = null
                    length = 0
                }, DELAY.toLong())
            }
        }
    }

    companion object {
        private const val DELAY = 500
    }
}