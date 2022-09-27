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

import android.graphics.Color

/**
 * A color comparator which compares based on hue, saturation, and value.
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class HsvColorComparator : Comparator<Int?> {
    override fun compare(lhs: Int?, rhs: Int?): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(lhs!!, hsv)
        val hue1 = hsv[0]
        val sat1 = hsv[1]
        val val1 = hsv[2]
        val hsv2 = FloatArray(3)
        Color.colorToHSV(rhs!!, hsv2)
        val hue2 = hsv2[0]
        val sat2 = hsv2[1]
        val val2 = hsv2[2]
        if (hue1 < hue2) {
            return 1
        } else if (hue1 > hue2) {
            return -1
        } else {
            if (sat1 < sat2) {
                return 1
            } else if (sat1 > sat2) {
                return -1
            } else {
                if (val1 < val2) {
                    return 1
                } else if (val1 > val2) {
                    return -1
                }
            }
        }
        return 0
    }
}