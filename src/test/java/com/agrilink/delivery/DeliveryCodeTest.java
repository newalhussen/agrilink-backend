package com.agrilink.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeliveryCodeTest {

    private Delivery delivery;

    @BeforeEach
    void setUp() {
        delivery = new Delivery(null, new BigDecimal("402"), new BigDecimal("3200"), "731052", "pickupqr0000", "482913",
                "deliveryqr000");
    }

    @Test
    void acceptsCodeWithOrWithoutSpacesAndQrToken() {
        assertThatCode(() -> delivery.verifyPickupCode("731052", 3)).doesNotThrowAnyException();
        assertThatCode(() -> delivery.verifyPickupCode(" 731 052 ", 3)).doesNotThrowAnyException();
        assertThatCode(() -> delivery.verifyPickupCode("pickupqr0000", 3)).doesNotThrowAnyException();
        assertThatCode(() -> delivery.verifyDeliveryCode("482 913", 3)).doesNotThrowAnyException();
    }

    @Test
    void pickupAndDeliveryCodesAreNotInterchangeable() {
        assertThatThrownBy(() -> delivery.verifyPickupCode("482913", 3)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> delivery.verifyDeliveryCode("731052", 3)).isInstanceOf(ApiException.class);
    }

    @Test
    void locksAfterTooManyWrongAttempts() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> delivery.verifyDeliveryCode("000000", 3))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_HANDOVER_CODE));
        }
        // Even the right code is refused once locked.
        assertThatThrownBy(() -> delivery.verifyDeliveryCode("482913", 3))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.HANDOVER_LOCKED));
        delivery.resetCodeAttempts();
        assertThatCode(() -> delivery.verifyDeliveryCode("482913", 3)).doesNotThrowAnyException();
    }

    @Test
    void nullCodeIsWrong() {
        assertThatThrownBy(() -> delivery.verifyPickupCode(null, 3)).isInstanceOf(ApiException.class);
    }
}
