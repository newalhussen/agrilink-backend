package com.agrilink.admin;

import com.agrilink.admin.AdminStatsService.Dashboard;
import com.agrilink.admin.AdminStatsService.Period;
import com.agrilink.admin.AdminStatsService.Report;
import com.agrilink.common.ApiException;
import com.agrilink.common.PageResponse;
import com.agrilink.config.AgriLinkProperties;
import com.agrilink.notification.NotificationController.NotificationResponse;
import com.agrilink.notification.NotificationService;
import com.agrilink.security.AuthContext;
import com.agrilink.user.AccountStatus;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard, reports, announcements, audit trail and read-only platform settings. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminOpsController {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    private final AdminStatsService stats;
    private final NotificationService notifications;
    private final UserRepository users;
    private final AdminAuditLogRepository auditLogs;
    private final AdminAuditService audit;
    private final AgriLinkProperties properties;
    private final Clock clock;

    public AdminOpsController(AdminStatsService stats, NotificationService notifications, UserRepository users,
                              AdminAuditLogRepository auditLogs, AdminAuditService audit,
                              AgriLinkProperties properties, Clock clock) {
        this.stats = stats;
        this.notifications = notifications;
        this.users = users;
        this.auditLogs = auditLogs;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    public record BroadcastRequest(Set<UUID> userIds, Role role, @NotBlank @Size(max = 200) String title,
                                   @NotBlank @Size(max = 1000) String body) {}

    public record BroadcastResponse(int recipients) {}

    public record AuditLogResponse(UUID id, UUID adminId, String adminName, String action, String entityType,
                                   UUID entityId, String details, Instant at) {}

    public record SettingsResponse(Map<String, Object> pricing, Map<String, Object> orders, Map<String, Object> otp,
                                   Map<String, Object> disputes, Map<String, Object> marketplace,
                                   Map<String, Object> payment, String note) {}

    @GetMapping("/dashboard")
    public Dashboard dashboard(@RequestParam(defaultValue = "TODAY") Period period) {
        return stats.dashboard(period);
    }

    /** Daily order volume, top products / farmers / drivers for a date range (defaults to the last 30 days). */
    @GetMapping("/reports/summary")
    public Report report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(clock.withZone(ZONE)) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end) || start.plusDays(366).isBefore(end)) {
            throw ApiException.badRequest("Choose a range of at most one year");
        }
        return stats.report(start, end);
    }

    @GetMapping("/notifications")
    public PageResponse<NotificationResponse> notificationLog(Pageable pageable) {
        return PageResponse.of(notifications.adminLog(pageable), NotificationResponse::from);
    }

    /** Sends an in-app (and push) announcement to chosen users, or to every active user of a role. */
    @PostMapping("/notifications/broadcast")
    @Transactional
    public BroadcastResponse broadcast(@Valid @RequestBody BroadcastRequest request) {
        Collection<UUID> targets;
        if (request.userIds() != null && !request.userIds().isEmpty()) {
            targets = request.userIds();
        } else if (request.role() != null) {
            targets = users.findAll().stream()
                    .filter(u -> u.getRole() == request.role() && u.getAccountStatus() == AccountStatus.ACTIVE)
                    .map(User::getId).collect(Collectors.toList());
        } else {
            throw ApiException.badRequest("Choose userIds or a role");
        }
        int sent = notifications.sendCustomToUsers(targets, request.title(), request.body());
        audit.record(AuthContext.userId(), "BROADCAST", "NOTIFICATION", null,
                request.title() + " -> " + sent + " recipients");
        return new BroadcastResponse(sent);
    }

    @GetMapping("/audit-logs")
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> auditLogs(Pageable pageable) {
        var page = auditLogs.findAll(org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));
        Map<UUID, String> names = users.findAllById(page.getContent().stream().map(AdminAuditLog::getAdminId)
                .distinct().toList()).stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return PageResponse.of(page, l -> new AuditLogResponse(l.getId(), l.getAdminId(),
                names.getOrDefault(l.getAdminId(), "Admin"), l.getAction(), l.getEntityType(), l.getEntityId(),
                l.getDetails(), l.getCreatedAt()));
    }

    /** Effective business rules. They are configured per environment (application.yml / env vars), not editable here. */
    @GetMapping("/settings")
    public SettingsResponse settings() {
        var p = properties;
        return new SettingsResponse(
                Map.of("platformFeePercent", p.pricing().platformFeePercent(),
                        "deliveryBaseFee", p.pricing().deliveryBaseFee(), "deliveryPerKm", p.pricing().deliveryPerKm(),
                        "deliveryPerKg", p.pricing().deliveryPerKg(), "deliveryMinFee", p.pricing().deliveryMinFee(),
                        "roadDistanceFactor", p.pricing().roadDistanceFactor(),
                        "defaultDistanceKm", p.pricing().defaultDistanceKm()),
                Map.of("farmerResponseWindow", p.orders().farmerResponseWindow().toString(),
                        "paymentWindow", p.orders().paymentWindow().toString(),
                        "checkWindow", p.orders().checkWindow().toString(),
                        "maxActiveDeliveriesPerDriver", p.orders().maxActiveDeliveriesPerDriver(),
                        "maxCodeAttempts", p.orders().maxCodeAttempts()),
                Map.of("length", p.otp().length(), "ttl", p.otp().ttl().toString(), "maxAttempts", p.otp().maxAttempts(),
                        "resendCooldown", p.otp().resendCooldown().toString()),
                Map.of("resolutionTarget", p.disputes().resolutionTarget().toString()),
                Map.of("requireVerifiedFarmers", p.marketplace().requireVerifiedFarmers()),
                Map.of("provider", p.payment().defaultProvider()),
                "Read-only. Change these in application.yml or through environment variables and restart.");
    }

    public static List<String> knownRoles() {
        return java.util.Arrays.stream(Role.values()).map(Enum::name).toList();
    }
}
