package com.agrilink.user;

import java.util.Locale;

public enum Language {
    EN("en"), AM("am"), OM("om");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public Locale locale() {
        return Locale.forLanguageTag(code);
    }

    public static Language fromCode(String code) {
        for (Language l : values()) {
            if (l.code.equalsIgnoreCase(code)) {
                return l;
            }
        }
        return EN;
    }
}
