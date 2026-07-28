package com.empmgmt.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Indian digit grouping (12,50,000.00 style) for amounts shown in Thymeleaf templates.
 * <p>
 * Called two ways: {@code ${T(com.empmgmt.util.AmountFormat).format(x)}} (SpringEL static-method
 * syntax) works in ordinary {@code th:text}/{@code th:value} contexts - but Thymeleaf treats
 * {@code th:data-*} custom-attribute expressions as "restricted", where {@code T(...)} static-class
 * access throws {@code org.attoparser.ParseException: Instantiation of new objects and access to
 * static classes or parameters is forbidden in this context} the moment there's at least one row
 * to evaluate it against (an empty th:each never trips it, which is why this hid during testing
 * with empty/near-empty tables and only surfaced against real data). For any {@code th:data-*}
 * attribute, use the bean reference instead: {@code ${@amountFormat.format(x)}} - bean-method
 * calls aren't subject to that restriction.
 * <p>
 * Grouped manually rather than via {@code java.text.DecimalFormat}/{@code NumberFormat} - neither
 * supports mixed grouping sizes (3 digits, then 2s) even when constructed for an en_IN locale,
 * since the JDK's built-in number formatters only support a single uniform grouping interval.
 * Doing this via ICU4J's DecimalFormat would work too, but pulls in a dependency this app doesn't
 * otherwise need for one cosmetic formatting rule.
 */
@Component("amountFormat")
public final class AmountFormat {

    /** Instance method for use in restricted expression contexts (th:data-*) via ${@amountFormat.format(x)}. */
    public String formatInstance(Number value) {
        return format(value);
    }

    public static String format(Number value) {
        if (value == null) return "0.00";
        BigDecimal amount = (value instanceof BigDecimal bd) ? bd : new BigDecimal(value.toString());
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        boolean negative = amount.signum() < 0;
        String plain = amount.abs().toPlainString();
        int dot = plain.indexOf('.');
        String integerPart = plain.substring(0, dot);
        String fractionPart = plain.substring(dot + 1);

        return (negative ? "-" : "") + groupIndian(integerPart) + "." + fractionPart;
    }

    private static String groupIndian(String integerPart) {
        if (integerPart.length() <= 3) return integerPart;

        String lastThree = integerPart.substring(integerPart.length() - 3);
        String remainder = integerPart.substring(0, integerPart.length() - 3);

        StringBuilder grouped = new StringBuilder();
        int i = remainder.length();
        while (i > 2) {
            grouped.insert(0, "," + remainder.substring(i - 2, i));
            i -= 2;
        }
        grouped.insert(0, remainder.substring(0, i));

        return grouped + "," + lastThree;
    }
}
