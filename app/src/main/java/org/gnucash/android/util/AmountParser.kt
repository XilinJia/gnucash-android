package org.gnucash.android.util

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParseException
import java.text.ParsePosition

/**
 * Parses amounts as String into BigDecimal.
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
object AmountParser {
    /**
     * Parses `amount` and returns it as a BigDecimal.
     *
     * @param amount String with the amount to parse.
     * @return The amount parsed as a BigDecimal.
     * @throws ParseException if the full string couldn't be parsed as an amount.
     */
    @JvmStatic
    @Throws(ParseException::class)
    fun parse(amount: String): BigDecimal {
        val formatter = NumberFormat.getNumberInstance() as DecimalFormat
        formatter.isParseBigDecimal = true
        val parsePosition = ParsePosition(0)
        val parsedAmount = formatter.parse(amount, parsePosition) as BigDecimal

        // Ensure any mistyping by the user is caught instead of partially parsed
        if (parsePosition.index < amount.length) throw ParseException(
            "Parse error",
            parsePosition.errorIndex
        )
        return parsedAmount
    }
}