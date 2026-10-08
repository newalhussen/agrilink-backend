package com.agrilink.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    Optional<OtpCode> findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(String phone, OtpPurpose purpose);

    Optional<OtpCode> findFirstByPhoneAndPurposeOrderByCreatedAtDesc(String phone, OtpPurpose purpose);

    long countByPhoneAndPurposeAndCreatedAtAfter(String phone, OtpPurpose purpose, Instant since);

    @Modifying
    @Query("update OtpCode o set o.consumedAt = :now where o.phone = :phone and o.purpose = :purpose and o.consumedAt is null")
    int invalidateOutstanding(@Param("phone") String phone, @Param("purpose") OtpPurpose purpose,
                              @Param("now") Instant now);

    @Modifying
    @Query("delete from OtpCode o where o.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
