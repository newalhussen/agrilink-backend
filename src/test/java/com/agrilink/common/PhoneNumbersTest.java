package com.agrilink.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"0911234567", "911234567", "251911234567", "+251911234567", "+251 91 123 4567",
            "091-123-4567"})
    void normalisesAllCommonWritings(String raw) {
        assertThat(PhoneNumbers.normalize(raw)).isEqualTo("+251911234567");
    }

    @Test
    void acceptsSafaricomSevenPrefix() {
        assertThat(PhoneNumbers.normalize("0711234567")).isEqualTo("+251711234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "+254711234567", "0511234567", "abc", ""})
    void rejectsNonEthiopianMobiles(String raw) {
        assertThatThrownBy(() -> PhoneNumbers.normalize(raw)).isInstanceOf(ApiException.class);
    }

    @Test
    void masksMiddleDigits() {
        assertThat(PhoneNumbers.mask("+251911234567")).isEqualTo("+25191•••4567");
    }
}
