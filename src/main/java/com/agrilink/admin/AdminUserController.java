package com.agrilink.admin;

import com.agrilink.auth.AuthService;
import com.agrilink.auth.RefreshTokenService;
import com.agrilink.buyer.BuyerService;
import com.agrilink.common.ApiException;
import com.agrilink.common.PageResponse;
import com.agrilink.config.AgriLinkProperties;
import com.agrilink.driver.DriverService;
import com.agrilink.farmer.FarmerService;
import com.agrilink.security.AuthContext;
import com.agrilink.user.AccountStatus;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.user.UserMapper;
import com.agrilink.user.UserRepository;
import com.agrilink.user.UserResponse;
import com.agrilink.user.VerificationStatus;
import com.agrilink.verification.VerificationController.DocumentResponse;
import com.agrilink.verification.VerificationDecision;
import com.agrilink.verification.VerificationReview;
import com.agrilink.verification.VerificationService;
import com.agrilink.wallet.WalletService;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Operations: user management and the farmer / buyer / driver verification queue. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminUserController {

    private final UserRepository users;
    private final UserMapper userMapper;
    private final FarmerService farmers;
    private final BuyerService buyers;
    private final DriverService drivers;
    private final VerificationService verification;
    private final WalletService wallets;
    private final AuthService auth;
    private final RefreshTokenService refreshTokens;
    private final AdminAuditService audit;
    private final String filesBasePath;

    public AdminUserController(UserRepository users, UserMapper userMapper, FarmerService farmers,
                               BuyerService buyers, DriverService drivers, VerificationService verification,
                               WalletService wallets, AuthService auth, RefreshTokenService refreshTokens,
                               AdminAuditService audit, AgriLinkProperties properties) {
        this.users = users;
        this.userMapper = userMapper;
        this.farmers = farmers;
        this.buyers = buyers;
        this.drivers = drivers;
        this.verification = verification;
        this.wallets = wallets;
        this.auth = auth;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.filesBasePath = properties.storage().publicBasePath();
    }

    public record ReviewResponse(UUID id, UUID reviewerId, VerificationDecision decision,
                                 VerificationStatus previousStatus, VerificationStatus newStatus, String note,
                                 Instant at) {}

    /** {@code profile} is the farmer, buyer or driver profile matching the user's role. */
    public record UserDetail(UserResponse user, Object profile, List<DocumentResponse> documents,
                             List<ReviewResponse> reviews, BigDecimal walletBalance, String statusReason) {}

    public record ChangeStatusRequest(@NotNull AccountStatus status, @Size(max = 500) String reason) {}

    public record DecisionRequest(@NotNull VerificationDecision decision, @Size(max = 1000) String note) {}

    public record CreateAdminRequest(@NotBlank String phone, @NotBlank @Size(min = 2, max = 150) String fullName,
                                     @NotBlank @Size(min = 8, max = 72) String password) {}

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(@RequestParam(required = false) Role role,
                                           @RequestParam(required = false) AccountStatus status,
                                           @RequestParam(required = false) VerificationStatus verificationStatus,
                                           @RequestParam(required = false) String q, Pageable pageable) {
        return PageResponse.of(users.findAll(spec(role, status, verificationStatus, q), newest(pageable)),
                userMapper::toResponse);
    }

    @GetMapping("/users/{id}")
    @Transactional(readOnly = true)
    public UserDetail detail(@PathVariable UUID id) {
        return buildDetail(id);
    }

    @PatchMapping("/users/{id}/status")
    @Transactional
    public UserResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getId().equals(AuthContext.userId())) {
            throw ApiException.badRequest("You cannot change your own account status");
        }
        user.setAccountStatus(request.status());
        user.setStatusReason(request.reason());
        if (request.status() != AccountStatus.ACTIVE) {
            refreshTokens.revokeAllForUser(id);
        }
        audit.record(AuthContext.userId(), "USER_STATUS_" + request.status(), "USER", id, request.reason());
        return userMapper.toResponse(user);
    }

    @PostMapping("/users/admins")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserResponse createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        User admin = auth.createVerifiedUser(request.phone(), request.fullName(), Role.ADMIN, request.password());
        audit.record(AuthContext.userId(), "ADMIN_CREATED", "USER", admin.getId(), admin.getPhone());
        return userMapper.toResponse(admin);
    }

    /** Verification queue: defaults to PENDING; filter by role to get the farmer / buyer / driver tabs. */
    @GetMapping("/verifications")
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> verificationQueue(
            @RequestParam(defaultValue = "PENDING") VerificationStatus status,
            @RequestParam(required = false) Role role, Pageable pageable) {
        Pageable oldestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "updatedAt").and(Sort.by("id")));
        return PageResponse.of(users.findAll(spec(role, null, status, null), oldestFirst), userMapper::toResponse);
    }

    @GetMapping("/verifications/{userId}")
    @Transactional(readOnly = true)
    public UserDetail verificationDetail(@PathVariable UUID userId) {
        return buildDetail(userId);
    }

    @PostMapping("/verifications/{userId}/decision")
    @Transactional
    public UserDetail decide(@PathVariable UUID userId, @Valid @RequestBody DecisionRequest request) {
        verification.decide(AuthContext.userId(), userId, request.decision(), request.note());
        return buildDetail(userId);
    }

    private UserDetail buildDetail(UUID id) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        Object profile = switch (user.getRole()) {
            case FARMER -> farmers.getMine(id);
            case BUYER -> buyers.get(id);
            case DRIVER -> drivers.getMine(id);
            case ADMIN -> null;
        };
        List<DocumentResponse> docs = verification.listDocuments(id).stream()
                .map(d -> DocumentResponse.from(d, filesBasePath)).toList();
        List<ReviewResponse> reviews = verification.history(id).stream().map(this::review).toList();
        BigDecimal balance = (user.getRole() == Role.FARMER || user.getRole() == Role.DRIVER)
                ? wallets.summary(id).balance() : null;
        return new UserDetail(userMapper.toResponse(user), profile, docs, reviews, balance, user.getStatusReason());
    }

    private ReviewResponse review(VerificationReview r) {
        return new ReviewResponse(r.getId(), r.getReviewerId(), r.getDecision(), r.getPreviousStatus(),
                r.getNewStatus(), r.getNote(), r.getCreatedAt());
    }

    private static Pageable newest(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
    }

    private static Specification<User> spec(Role role, AccountStatus status, VerificationStatus verification,
                                            String q) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (role != null) {
                p.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                p.add(cb.equal(root.get("accountStatus"), status));
            }
            if (verification != null) {
                p.add(cb.equal(root.get("verificationStatus"), verification));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("fullName")), like), cb.like(root.get("phone"), like),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
