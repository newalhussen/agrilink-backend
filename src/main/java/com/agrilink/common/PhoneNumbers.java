package com.agrilink.common;

import java.util.regex.Pattern;

/** Normalises Ethiopian mobile numbers to E.164 (+2519XXXXXXXX / +2517XXXXXXXX). */
public final class PhoneNumbers {

    private static final Pattern E164_ET_MOBILE = Pattern.compile("^\\+251[79]\\d{8}$");

    private PhoneNumbers() {
    }

    /**
     * Accepts 0911234567, 911234567, 251911234567, +251911234567 (spaces and dashes ignored).
     *
     * @throws ApiException BAD_REQUEST if the value is not an Ethiopian mobile number
     */
    public static String normalize(String raw) {
        if (raw == null) {
            throw ApiException.badRequest("Phone number is required");
        }
        String digits = raw.replaceAll("[\\s\\-()]", "");
        String candidate;
        if (digits.startsWith("+251")) {
            candidate = digits;
        } else if (digits.startsWith("251")) {
            candidate = "+" + digits;
        } else if (digits.startsWith("0")) {
            candidate = "+251" + digits.substring(1);
        } else {
            candidate = "+251" + digits;
        }
        if (!E164_ET_MOBILE.matcher(candidate).matches()) {
            throw ApiException.badRequest("Enter a valid Ethiopian mobile number, e.g. 0911 234 567");
        }
        return candidate;
    }

    /** Masks the middle of a number for display: +251 91 ••• 2140. */
    public static String mask(String e164) {
        if (e164 == null || e164.length() < 8) {
            return e164;
        }
        return e164.substring(0, 6) + "•••" + e164.substring(e164.length() - 4);
    }
}
