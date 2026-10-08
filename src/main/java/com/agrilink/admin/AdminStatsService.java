package com.agrilink.admin;

import com.agrilink.delivery.DeliveryRepository;
import com.agrilink.delivery.DeliveryStatus;
import com.agrilink.dispute.DisputeRepository;
import com.agrilink.dispute.DisputeService;
import com.agrilink.marketplace.ListingRepository;
import com.agrilink.marketplace.ListingStatus;
import com.agrilink.order.OrderRepository;
import com.agrilink.order.OrderStatus;
import com.agrilink.payment.PaymentRepository;
import com.agrilink.payment.PaymentStatus;
import com.agrilink.user.Role;
import com.agrilink.user.UserRepository;
import com.agrilink.user.VerificationStatus;
import com.agrilink.wallet.PayoutRepository;
import com.agrilink.wallet.PayoutStatus;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only aggregates for the operations dashboard and the reports page. */
@Service
public class AdminStatsService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    public enum Period {
        TODAY(0), WEEK(6), MONTH(29);

        final int daysBack;

        Period(int daysBack) {
            this.daysBack = daysBack;
        }
    }

    public record Money(BigDecimal amount, String currency) {}

    public record AttentionItem(String kind, String title, String detail, String referenceType, UUID referenceId,
                                long count) {}

    public record Dashboard(
            Period period,
            Instant since,
            Map<String, Long> usersByRole,
            Map<String, Long> pendingVerifications,
            long activeListings,
            Map<String, Long> ordersByStatus,
            long ordersInPeriod,
            Money fundedOrderValueInPeriod,
            Money heldInEscrow,
            Money releasedInPeriod,
            Money platformFeeInPeriod,
            long openDeliveryJobs,
            long deliveriesOnTheRoad,
            long openDisputes,
            long overdueDisputes,
            long failedPayouts,
            List<AttentionItem> needsAttention) {}

    public record DailyPoint(LocalDate date, long orders, BigDecimal orderValue, long completedOrders) {}

    public record TopEntry(UUID id, String name, long count, BigDecimal value) {}

    public record Report(LocalDate from, LocalDate to, List<DailyPoint> daily, List<TopEntry> topProducts,
                         List<TopEntry> topFarmers, List<TopEntry> topDrivers, Map<String, Long> ordersByStatus,
                         BigDecimal totalOrderValue, long totalOrders, long completedOrders,
                         BigDecimal averageOrderValue) {}

    private final UserRepository users;
    private final ListingRepository listings;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PayoutRepository payouts;
    private final DeliveryRepository deliveries;
    private final DisputeRepository disputes;
    private final JdbcClient jdbc;
    private final Clock clock;

    public AdminStatsService(UserRepository users, ListingRepository listings, OrderRepository orders,
                             PaymentRepository payments, PayoutRepository payouts, DeliveryRepository deliveries,
                             DisputeRepository disputes, JdbcClient jdbc, Clock clock) {
        this.users = users;
        this.listings = listings;
        this.orders = orders;
        this.payments = payments;
        this.payouts = payouts;
        this.deliveries = deliveries;
        this.disputes = disputes;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(Period period) {
        Instant now = Instant.now(clock);
        Instant since = LocalDate.now(clock.withZone(ZONE)).minusDays(period.daysBack).atStartOfDay(ZONE).toInstant();

        Map<String, Long> usersByRole = new LinkedHashMap<>();
        Map<String, Long> pending = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            usersByRole.put(role.name(), users.countByRole(role));
            if (role != Role.ADMIN) {
                pending.put(role.name(), users.countByVerificationStatusAndRole(VerificationStatus.PENDING, role));
            }
        }
        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        for (Object[] row : orders.countGroupedByStatus()) {
            byStatus.put((OrderStatus) row[0], (Long) row[1]);
        }
        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            ordersByStatus.put(s.name(), byStatus.getOrDefault(s, 0L));
        }
        long inPeriod = jdbc.sql("select count(*) from orders where created_at >= :since")
                .param("since", java.sql.Timestamp.from(since)).query(Long.class).single();

        long openDisputes = disputes.countByStatusIn(DisputeService.openStatuses());
        long overdue = disputes.countOverdue(DisputeService.openStatuses(), now);
        long failedPayouts = payouts.countByStatus(PayoutStatus.FAILED);
        long openJobs = deliveries.countByStatus(DeliveryStatus.OPEN);
        long onRoad = deliveries.countByStatus(DeliveryStatus.PICKED_UP)
                + deliveries.countByStatus(DeliveryStatus.IN_TRANSIT);

        List<AttentionItem> attention = new ArrayList<>();
        if (overdue > 0) {
            attention.add(new AttentionItem("DISPUTE_OVERDUE", overdue + " dispute(s) past the 24 h target",
                    "Open the dispute desk to resolve them", "DISPUTE", null, overdue));
        }
        long pendingTotal = pending.values().stream().mapToLong(Long::longValue).sum();
        if (pendingTotal > 0) {
            attention.add(new AttentionItem("VERIFICATION_QUEUE", pendingTotal + " people waiting for verification",
                    pending.get("FARMER") + " farmers, " + pending.get("BUYER") + " buyers, "
                            + pending.get("DRIVER") + " drivers", "VERIFICATION", null, pendingTotal));
        }
        if (failedPayouts > 0) {
            attention.add(new AttentionItem("PAYOUT_FAILED", failedPayouts + " failed payout(s)",
                    "Withdrawals bounced; contact the account holders", "PAYOUT", null, failedPayouts));
        }
        long staleOpenJobs = jdbc.sql("select count(*) from deliveries where status = 'OPEN' and created_at < :t")
                .param("t", java.sql.Timestamp.from(now.minusSeconds(2 * 3600))).query(Long.class).single();
        if (staleOpenJobs > 0) {
            attention.add(new AttentionItem("JOBS_UNASSIGNED", staleOpenJobs + " delivery job(s) waiting over 2 hours",
                    "Assign a driver manually", "DELIVERY", null, staleOpenJobs));
        }

        return new Dashboard(period, since, usersByRole, pending, listings.countByStatus(ListingStatus.ACTIVE),
                ordersByStatus, inPeriod,
                new Money(orders.sumFundedOrderValueSince(since), "ETB"),
                new Money(payments.sumHeld(), "ETB"),
                new Money(payments.sumReleasedSince(since), "ETB"),
                new Money(payments.sumPlatformFeeSince(since), "ETB"),
                openJobs, onRoad, openDisputes, overdue, failedPayouts, attention);
    }

    @Transactional(readOnly = true)
    public Report report(LocalDate from, LocalDate to) {
        Instant f = from.atStartOfDay(ZONE).toInstant();
        Instant t = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        var fts = java.sql.Timestamp.from(f);
        var tts = java.sql.Timestamp.from(t);

        Map<LocalDate, DailyPoint> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d, new DailyPoint(d, 0, BigDecimal.ZERO, 0));
        }
        jdbc.sql("""
                select (created_at at time zone 'Africa/Addis_Ababa')::date as day, count(*) as n,
                       coalesce(sum(total_amount), 0) as value,
                       count(*) filter (where status = 'COMPLETED') as done
                from orders where created_at >= :f and created_at < :t group by 1 order by 1
                """).param("f", fts).param("t", tts).query((rs, i) -> {
            LocalDate day = rs.getObject("day", Date.class).toLocalDate();
            byDay.put(day, new DailyPoint(day, rs.getLong("n"), rs.getBigDecimal("value"), rs.getLong("done")));
            return null;
        }).list();

        List<TopEntry> products = jdbc.sql("""
                select oi.product_id as id, oi.product_name as name, count(distinct o.id) as n,
                       coalesce(sum(oi.line_total), 0) as value
                from order_items oi join orders o on o.id = oi.order_id
                where o.status = 'COMPLETED' and o.created_at >= :f and o.created_at < :t
                group by oi.product_id, oi.product_name order by value desc limit 10
                """).param("f", fts).param("t", tts).query(this::topEntry).list();
        List<TopEntry> farmers = jdbc.sql("""
                select u.id as id, u.full_name as name, count(*) as n, coalesce(sum(o.subtotal_amount), 0) as value
                from orders o join users u on u.id = o.farmer_id
                where o.status = 'COMPLETED' and o.created_at >= :f and o.created_at < :t
                group by u.id, u.full_name order by value desc limit 10
                """).param("f", fts).param("t", tts).query(this::topEntry).list();
        List<TopEntry> drivers = jdbc.sql("""
                select u.id as id, u.full_name as name, count(*) as n, coalesce(sum(o.delivery_fee), 0) as value
                from orders o join users u on u.id = o.driver_id
                where o.status = 'COMPLETED' and o.created_at >= :f and o.created_at < :t
                group by u.id, u.full_name order by n desc limit 10
                """).param("f", fts).param("t", tts).query(this::topEntry).list();

        Map<String, Long> statuses = new LinkedHashMap<>();
        jdbc.sql("select status, count(*) as n from orders where created_at >= :f and created_at < :t group by status")
                .param("f", fts).param("t", tts).query((rs, i) -> {
                    statuses.put(rs.getString("status"), rs.getLong("n"));
                    return null;
                }).list();

        long total = byDay.values().stream().mapToLong(DailyPoint::orders).sum();
        long completed = byDay.values().stream().mapToLong(DailyPoint::completedOrders).sum();
        BigDecimal value = byDay.values().stream().map(DailyPoint::orderValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = total == 0 ? BigDecimal.ZERO
                : value.divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);
        return new Report(from, to, List.copyOf(byDay.values()), products, farmers, drivers, statuses, value, total,
                completed, avg);
    }

    private TopEntry topEntry(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new TopEntry(rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("n"),
                rs.getBigDecimal("value"));
    }

    /** Statuses counted as "on the road" in labels; exposed for clients that want to mirror the definition. */
    public static java.util.Set<DeliveryStatus> onTheRoad() {
        return EnumSet.of(DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT);
    }

    public static PaymentStatus held() {
        return PaymentStatus.HELD;
    }
}
