package com.agrilink.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agrilink.TestSupport;
import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    private static final String PHONE = "+251911234567";

    @Mock OtpCodeRepository repository;
    OtpService service;

    @BeforeEach
    void setUp() {
        service = new OtpService(repository, TestSupport.properties(), TestSupport.clock());
    }

    @Test
    void issuesSixDigitCodeAndStoresOnlyAHash() {
        when(repository.findFirstByPhoneAndPurposeOrderByCreatedAtDesc(PHONE, OtpPurpose.LOGIN))
                .thenReturn(Optional.empty());

        OtpService.Issued issued = service.issue(PHONE, OtpPurpose.LOGIN);

        assertThat(issued.plainCode()).matches("\\d{6}");
        assertThat(issued.ttl()).isEqualTo(Duration.ofMinutes(5));
        ArgumentCaptor<OtpCode> saved = ArgumentCaptor.forClass(OtpCode.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCodeHash()).doesNotContain(issued.plainCode()).hasSize(64);
        verify(repository).invalidateOutstanding(any(), any(), any());
    }

    @Test
    void verifyAcceptsTheIssuedCodeOnceAndConsumesIt() {
        OtpCode stored = storedFor("123456");
        when(repository.findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(PHONE, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(stored));

        assertThatCode(() -> service.verify(PHONE, OtpPurpose.LOGIN, "123456")).doesNotThrowAnyException();
        assertThat(stored.isConsumed()).isTrue();
    }

    @Test
    void wrongCodeIsCountedAndRejected() {
        OtpCode stored = storedFor("123456");
        when(repository.findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(PHONE, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.verify(PHONE, OtpPurpose.LOGIN, "000000"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OTP_INVALID));
        assertThat(stored.getAttempts()).isEqualTo(1);
        assertThat(stored.isConsumed()).isFalse();
    }

    @Test
    void codeIsBurnedAfterTooManyAttempts() {
        OtpCode stored = storedFor("123456");
        when(repository.findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(PHONE, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(stored));
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.verify(PHONE, OtpPurpose.LOGIN, "000000")).isInstanceOf(ApiException.class);
        }
        // Max attempts is 3: even the correct code no longer works.
        assertThatThrownBy(() -> service.verify(PHONE, OtpPurpose.LOGIN, "123456")).isInstanceOf(ApiException.class);
        assertThat(stored.isConsumed()).isTrue();
    }

    @Test
    void expiredCodeIsRefused() {
        OtpCode expired = new OtpCode(PHONE, OtpPurpose.LOGIN, hashOf("123456"), TestSupport.NOW.minusSeconds(1));
        when(repository.findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(PHONE, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.verify(PHONE, OtpPurpose.LOGIN, "123456"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OTP_EXPIRED));
    }

    @Test
    void resendCooldownIsEnforced() {
        OtpCode recent = TestSupport.withId(new OtpCode(PHONE, OtpPurpose.LOGIN, "x", TestSupport.NOW.plusSeconds(300)));
        setCreatedAt(recent, TestSupport.NOW.minusSeconds(10));
        when(repository.findFirstByPhoneAndPurposeOrderByCreatedAtDesc(PHONE, OtpPurpose.LOGIN))
                .thenReturn(Optional.of(recent));
        assertThatThrownBy(() -> service.issue(PHONE, OtpPurpose.LOGIN)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OTP_RATE_LIMITED));
    }

    @Test
    void hourlyRequestLimitIsEnforced() {
        when(repository.findFirstByPhoneAndPurposeOrderByCreatedAtDesc(PHONE, OtpPurpose.LOGIN))
                .thenReturn(Optional.empty());
        when(repository.countByPhoneAndPurposeAndCreatedAtAfter(any(), any(), any())).thenReturn(5L);
        assertThatThrownBy(() -> service.issue(PHONE, OtpPurpose.LOGIN)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OTP_RATE_LIMITED));
    }

    private OtpCode storedFor(String code) {
        return new OtpCode(PHONE, OtpPurpose.LOGIN, hashOf(code), TestSupport.NOW.plusSeconds(300));
    }

    /** Re-issues a code through the service to learn the hash format instead of duplicating it here. */
    private String hashOf(String code) {
        try {
            var m = OtpService.class.getDeclaredMethod("hash", String.class, String.class);
            m.setAccessible(true);
            return (String) m.invoke(null, PHONE, code);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setCreatedAt(OtpCode code, java.time.Instant at) {
        try {
            var f = com.agrilink.common.BaseEntity.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(code, at);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
