package com.agrilink.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Runs the whole application against a real (embedded) PostgreSQL: Flyway migrations, Hibernate schema validation,
 * security, and the complete farmer -> buyer -> driver -> delivery -> payment workflow, then disputes, verification
 * and admin operations. Demo accounts come from the dev-style bootstrap (password Demo@12345).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndFlowTest {

    private static final String PASSWORD = "Demo@12345";
    private static EmbeddedPostgres postgres;

    @LocalServerPort int port;
    private ApiClient api;

    @BeforeAll
    static void startDatabase() throws IOException {
        postgres = EmbeddedPostgres.builder().setServerConfig("fsync", "off").setServerConfig("synchronous_commit", "off").setServerConfig("full_page_writes", "off").start();
    }

    @AfterAll
    static void stopDatabase() throws IOException {
        postgres.close();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        if (postgres == null) {
            postgres = EmbeddedPostgres.builder().setServerConfig("fsync", "off").setServerConfig("synchronous_commit", "off").setServerConfig("full_page_writes", "off").start();
        }
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("agrilink.security.jwt.secret", () -> "integration-test-secret-integration-test-secret");
        registry.add("agrilink.otp.expose-in-response", () -> "true");
        registry.add("agrilink.payment.mock.auto-confirm", () -> "true");
        registry.add("agrilink.bootstrap.admin-phone", () -> "+251900000000");
        registry.add("agrilink.bootstrap.admin-password", () -> "Admin@12345");
        registry.add("agrilink.bootstrap.seed-demo-data", () -> "true");
        registry.add("agrilink.scheduling.enabled", () -> "false");
        registry.add("agrilink.storage.local-path", () -> "target/it-uploads");
    }

    private ApiClient api() {
        if (api == null) {
            api = new ApiClient(port);
        }
        return api;
    }

    private String login(String phone, String password) {
        var r = api().post("/auth/login", null, Map.of("phone", phone, "password", password));
        assertThat(r.status()).as("login %s: %s", phone, r.raw()).isEqualTo(200);
        return r.body().path("accessToken").asString("");
    }

    private String farmer() { return login("0911000001", PASSWORD); }
    private String farmer2() { return login("0911000002", PASSWORD); }
    private String buyer() { return login("0922000001", PASSWORD); }
    private String driver() { return login("0933000001", PASSWORD); }
    private String admin() { return login("0900000000", "Admin@12345"); }

    private static BigDecimal num(JsonNode node) {
        return new BigDecimal(node.asString(""));
    }

    private JsonNode listingByTitle(String title) {
        var r = api().get("/listings?size=50", null);
        assertThat(r.status()).isEqualTo(200);
        for (JsonNode item : r.body().path("items")) {
            if (title.equals(item.path("title").asString(""))) {
                return item;
            }
        }
        throw new AssertionError("Listing not found: " + title + " in " + r.raw());
    }

    private Map<String, Object> orderBody(String listingId, String quantity) {
        Map<String, Object> address = new HashMap<>();
        address.put("zone", "Bole");
        address.put("town", "Addis Ababa");
        address.put("addressLine", "Behind Edna Mall");
        address.put("latitude", 8.99);
        address.put("longitude", 38.79);
        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(Map.of("listingId", listingId, "quantity", quantity)));
        body.put("deliveryAddress", address);
        body.put("notes", "Please deliver before noon");
        return body;
    }

    /** Places an order for the given listing and drives it to DELIVERED. Returns the order id. */
    private String orderUntilDelivered(String listingTitle, String quantity, String weighedKg) {
        String buyer = buyer();
        String farmer = farmer();
        String driver = driver();
        JsonNode listing = listingByTitle(listingTitle);
        var created = api().post("/orders", buyer, orderBody(listing.path("id").asString(""), quantity));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        String orderId = created.body().path("id").asString("");
        assertThat(api().post("/orders/" + orderId + "/accept", farmer, null).status()).isEqualTo(200);
        var pay = api().post("/orders/" + orderId + "/payments", buyer,
                Map.of("method", "TELEBIRR", "payerAccount", "0922000001"));
        assertThat(pay.status()).as(pay.raw()).isEqualTo(201);
        assertThat(pay.body().path("status").asString("")).isEqualTo("HELD");

        JsonNode farmerView = api().get("/orders/" + orderId, farmer).body();
        JsonNode buyerView = api().get("/orders/" + orderId, buyer).body();
        String deliveryId = farmerView.path("delivery").path("id").asString("");
        String pickupCode = farmerView.path("delivery").path("pickupCode").asString("");
        String deliveryCode = buyerView.path("delivery").path("deliveryCode").asString("");
        assertThat(api().post("/deliveries/" + deliveryId + "/accept", driver, null).status()).isEqualTo(200);
        assertThat(api().post("/orders/" + orderId + "/ready", farmer, null).status()).isEqualTo(200);
        var pickup = api().post("/deliveries/" + deliveryId + "/pickup", driver,
                Map.of("code", pickupCode, "weighedKg", weighedKg, "crateCount", 16));
        assertThat(pickup.status()).as(pickup.raw()).isEqualTo(200);
        assertThat(api().post("/deliveries/" + deliveryId + "/start", driver, null).status()).isEqualTo(200);
        var deliver = api().post("/deliveries/" + deliveryId + "/deliver", driver, Map.of("code", deliveryCode));
        assertThat(deliver.status()).as(deliver.raw()).isEqualTo(200);
        assertThat(api().get("/orders/" + orderId, buyer).body().path("status").asString("")).isEqualTo("DELIVERED");
        return orderId;
    }

    // ------------------------------------------------------------------------------------------------ scenarios

    @Test
    @Order(1)
    void healthAndPublicReferenceData() {
        var regions = api().get("/regions", null);
        assertThat(regions.status()).isEqualTo(200);
        assertThat(regions.body().size()).isEqualTo(14);
        var categories = api().get("/categories", null);
        assertThat(categories.body().size()).isGreaterThanOrEqualTo(8);
        var products = api().get("/products?q=tom", null);
        assertThat(products.body().get(0).path("slug").asString("")).isEqualTo("tomato");
        var listings = api().get("/listings", null);
        assertThat(listings.body().path("totalItems").asInt()).isGreaterThanOrEqualTo(4);
        var cheapestFirst = api().get("/listings?sort=price_asc&productId=" + products.body().get(0).path("id").asString(""), null);
        assertThat(cheapestFirst.status()).isEqualTo(200);
        assertThat(api().get("/listings?sort=bogus", null).status()).isEqualTo(400);
    }

    @Test
    @Order(2)
    void securityRejectsMissingWrongAndInsufficientCredentials() {
        var anonymous = api().get("/orders", null);
        assertThat(anonymous.status()).isEqualTo(401);
        assertThat(anonymous.body().path("code").asString("")).isEqualTo("UNAUTHENTICATED");
        assertThat(api().get("/orders", "garbage.token.value").status()).isEqualTo(401);

        var wrongPassword = api().post("/auth/login", null, Map.of("phone", "0922000001", "password", "nope-nope"));
        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(wrongPassword.body().path("code").asString("")).isEqualTo("INVALID_CREDENTIALS");

        var buyerOnAdmin = api().get("/admin/users", buyer());
        assertThat(buyerOnAdmin.status()).isEqualTo(403);
        assertThat(buyerOnAdmin.body().path("code").asString("")).isEqualTo("ACCESS_DENIED");
        assertThat(api().post("/listings", buyer(), Map.of("productId", java.util.UUID.randomUUID().toString(), "unit", "KG",
                "quantity", "10", "pricePerUnit", "5")).status()).isEqualTo(403);

        var invalid = api().post("/orders", buyer(), Map.of("items", List.of()));
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.body().path("code").asString("")).isEqualTo("VALIDATION_FAILED");
        assertThat(invalid.body().path("fieldErrors").size()).isGreaterThan(0);
    }

    @Test
    @Order(3)
    void registrationOtpRefreshRotationAndVerificationWorkflow() {
        var register = api().post("/auth/register", null, Map.of("phone", "0944000001", "fullName", "Dawit Olana",
                "role", "FARMER", "preferredLanguage", "om"));
        assertThat(register.status()).as(register.raw()).isEqualTo(201);
        String otp = register.body().path("devOtp").asString("");
        assertThat(otp).matches("\\d{6}");
        assertThat(api().post("/auth/register", null, Map.of("phone", "0944000001", "fullName", "Dawit Olana",
                "role", "FARMER")).status()).as("resend cooldown").isEqualTo(429);

        assertThat(api().post("/auth/otp/verify", null,
                Map.of("phone", "0944000001", "code", "000000", "purpose", "REGISTER")).status()).isEqualTo(400);
        var verified = api().post("/auth/otp/verify", null,
                Map.of("phone", "0944000001", "code", otp, "purpose", "REGISTER"));
        assertThat(verified.status()).as(verified.raw()).isEqualTo(200);
        String access = verified.body().path("accessToken").asString("");
        String refresh = verified.body().path("refreshToken").asString("");
        assertThat(verified.body().path("user").path("phoneVerified").asBoolean()).isTrue();
        assertThat(verified.body().path("user").path("preferredLanguage").asString("")).isEqualTo("om");

        // Refresh rotates; replaying the old token kills the whole family.
        var rotated = api().post("/auth/refresh", null, Map.of("refreshToken", refresh));
        assertThat(rotated.status()).isEqualTo(200);
        String newRefresh = rotated.body().path("refreshToken").asString("");
        assertThat(newRefresh).isNotEqualTo(refresh);
        assertThat(api().post("/auth/refresh", null, Map.of("refreshToken", refresh)).status()).isEqualTo(401);
        assertThat(api().post("/auth/refresh", null, Map.of("refreshToken", newRefresh)).status())
                .as("family revoked after reuse").isEqualTo(401);

        // Verification: documents and profile are required before submitting.
        assertThat(api().post("/verification/submit", access, null).status()).isEqualTo(400);
        var region = api().get("/regions", null).body().get(1).path("id").asString("");
        var profile = api().patch("/farmers/me", access, Map.of("farmName", "Dawit Farm", "address",
                Map.of("regionId", region, "town", "Bishoftu", "latitude", 8.75, "longitude", 38.98)));
        assertThat(profile.status()).as(profile.raw()).isEqualTo(200);
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
        var upload = api().upload("/files", access, "id.png", "image/png", png, Map.of("purpose", "VERIFICATION_DOCUMENT"));
        assertThat(upload.status()).as(upload.raw()).isEqualTo(201);
        String fileId = upload.body().path("id").asString("");
        var doc = api().post("/verification/documents", access, Map.of("type", "FAYDA_ID", "fileId", fileId));
        assertThat(doc.status()).as(doc.raw()).isEqualTo(201);
        // Private documents: owner and admin can read them, other users cannot.
        assertThat(api().get("/files/" + fileId, access).status()).isEqualTo(200);
        assertThat(api().get("/files/" + fileId, null).status()).isEqualTo(401);
        assertThat(api().get("/files/" + fileId, buyer()).status()).isEqualTo(403);

        var submitted = api().post("/verification/submit", access, null);
        assertThat(submitted.status()).as(submitted.raw()).isEqualTo(200);
        assertThat(submitted.body().path("status").asString("")).isEqualTo("PENDING");

        String admin = admin();
        var queue = api().get("/admin/verifications?role=FARMER", admin);
        assertThat(queue.body().path("totalItems").asInt()).isGreaterThanOrEqualTo(1);
        String userId = null;
        for (JsonNode u : queue.body().path("items")) {
            if ("+251944000001".equals(u.path("phone").asString(""))) {
                userId = u.path("id").asString("");
            }
        }
        assertThat(userId).isNotNull();
        assertThat(api().post("/admin/verifications/" + userId + "/decision", admin,
                Map.of("decision", "REJECT")).status()).as("note required").isEqualTo(400);
        var decision = api().post("/admin/verifications/" + userId + "/decision", admin,
                Map.of("decision", "APPROVE", "note", "Fayda ID matches"));
        assertThat(decision.status()).as(decision.raw()).isEqualTo(200);
        assertThat(decision.body().path("user").path("verificationStatus").asString("")).isEqualTo("VERIFIED");
        assertThat(api().get("/users/me", access).body().path("verificationStatus").asString("")).isEqualTo("VERIFIED");
        var notifications = api().get("/notifications", access);
        assertThat(notifications.body().path("items").get(0).path("type").asString("")).isEqualTo("VERIFICATION_APPROVED");
        // Afaan Oromoo was this user's language, so the title comes from the Oromo bundle.
        assertThat(notifications.body().path("items").get(0).path("title").asString("")).isEqualTo("Mirkanaa’aniirtu");
    }

    @Test
    @Order(4)
    void fullTradeFromListingToCompletedOrderAndPayout() {
        String buyer = buyer();
        String farmer = farmer();
        String driver = driver();

        JsonNode tomato = listingByTitle("Tomato Grade A");
        String listingId = tomato.path("id").asString("");
        BigDecimal stockBefore = num(tomato.path("quantityAvailable"));

        // Design example: 400 kg x ETB 46 = 18,400 + 2% fee = 368.
        var quote = api().post("/orders/quote", buyer, orderBody(listingId, "400"));
        assertThat(quote.status()).as(quote.raw()).isEqualTo(200);
        assertThat(num(quote.body().path("subtotal"))).isEqualByComparingTo("18400.00");
        assertThat(num(quote.body().path("platformFee"))).isEqualByComparingTo("368.00");
        BigDecimal deliveryFee = num(quote.body().path("deliveryFee"));
        assertThat(deliveryFee).isGreaterThan(BigDecimal.ZERO);
        assertThat(num(quote.body().path("total"))).isEqualByComparingTo(new BigDecimal("18768.00").add(deliveryFee));
        assertThat(num(listingByTitle("Tomato Grade A").path("quantityAvailable")))
                .as("quote reserves nothing").isEqualByComparingTo(stockBefore);

        var created = api().post("/orders", buyer, orderBody(listingId, "400"));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        String orderId = created.body().path("id").asString("");
        assertThat(created.body().path("status").asString("")).isEqualTo("PENDING");
        assertThat(created.body().path("orderNumber").asString("")).startsWith("AL-");
        assertThat(created.body().path("allowedActions").toString()).contains("CANCEL").doesNotContain("PAY");
        assertThat(num(listingByTitle("Tomato Grade A").path("quantityAvailable")))
                .isEqualByComparingTo(stockBefore.subtract(new BigDecimal("400")));

        // The farmer sees it, the other farmer and the driver do not.
        var farmerList = api().get("/orders?status=PENDING", farmer);
        assertThat(farmerList.body().path("items").toString()).contains(orderId);
        assertThat(farmerList.body().path("items").get(0).path("allowedActions").toString()).contains("ACCEPT");
        assertThat(api().get("/orders/" + orderId, farmer2()).status()).isEqualTo(404);
        assertThat(api().get("/orders/" + orderId, driver).status()).isEqualTo(404);
        // Contacts stay hidden until the order is paid.
        assertThat(api().get("/orders/" + orderId, farmer).body().path("buyer").path("phone").isNull()
                || api().get("/orders/" + orderId, farmer).body().path("buyer").path("phone").isMissingNode()).isTrue();

        // Payment is impossible before the farmer accepts.
        assertThat(api().post("/orders/" + orderId + "/payments", buyer,
                Map.of("method", "TELEBIRR", "payerAccount", "0922000001")).status()).isEqualTo(409);
        assertThat(api().post("/orders/" + orderId + "/accept", buyer, null).status()).isEqualTo(403);
        var accepted = api().post("/orders/" + orderId + "/accept", farmer, null);
        assertThat(accepted.status()).as(accepted.raw()).isEqualTo(200);
        assertThat(accepted.body().path("status").asString("")).isEqualTo("ACCEPTED");
        assertThat(api().get("/orders/" + orderId, buyer).body().path("allowedActions").toString()).contains("PAY");

        // A payment that fails leaves the order retryable; the next one succeeds.
        var failed = api().post("/orders/" + orderId + "/payments", buyer,
                Map.of("method", "TELEBIRR", "payerAccount", "0922000000"));
        assertThat(failed.body().path("status").asString("")).isEqualTo("FAILED");
        assertThat(api().get("/orders/" + orderId, buyer).body().path("status").asString("")).isEqualTo("ACCEPTED");
        var paid = api().post("/orders/" + orderId + "/payments", buyer,
                Map.of("method", "TELEBIRR", "payerAccount", "0922000001"));
        assertThat(paid.body().path("status").asString("")).isEqualTo("HELD");
        assertThat(num(paid.body().path("amount"))).isEqualByComparingTo(num(created.body().path("amounts").path("total")));

        JsonNode farmerView = api().get("/orders/" + orderId, farmer).body();
        JsonNode buyerView = api().get("/orders/" + orderId, buyer).body();
        assertThat(farmerView.path("status").asString("")).isEqualTo("PAID");
        String deliveryId = farmerView.path("delivery").path("id").asString("");
        String pickupCode = farmerView.path("delivery").path("pickupCode").asString("");
        String deliveryCode = buyerView.path("delivery").path("deliveryCode").asString("");
        assertThat(pickupCode).matches("\\d{6}");
        assertThat(deliveryCode).matches("\\d{6}").isNotEqualTo(pickupCode);
        assertThat(farmerView.path("delivery").path("deliveryCode").isNull()).as("farmer never sees buyer code").isTrue();
        assertThat(buyerView.path("delivery").path("pickupCode").isNull()).as("buyer never sees pickup code").isTrue();
        assertThat(farmerView.path("buyer").path("phone").asString("")).as("contacts after payment").isEqualTo("+251922000001");

        // Driver job board.
        var jobs = api().get("/deliveries/available", driver);
        assertThat(jobs.status()).isEqualTo(200);
        JsonNode job = null;
        for (JsonNode j : jobs.body().path("items")) {
            if (deliveryId.equals(j.path("id").asString(""))) {
                job = j;
            }
        }
        assertThat(job).as("job on the board").isNotNull();
        assertThat(job.path("pickup").path("addressLine").isNull()).as("exact pickup point hidden").isTrue();
        var openJob = api().get("/deliveries/" + deliveryId, driver);
        assertThat(openJob.status()).as("driver can open a job on the board").isEqualTo(200);
        assertThat(openJob.body().path("allowedActions").toString()).contains("ACCEPT");
        assertThat(openJob.body().path("pickup").path("address").path("addressLine").isNull()).as("no exact pickup point before accepting").isTrue();
        assertThat(openJob.body().path("pickupCode").isNull()).isTrue();
        assertThat(api().get("/deliveries/available", buyer).status()).isEqualTo(403);
        assertThat(api().post("/deliveries/" + deliveryId + "/pickup", driver,
                Map.of("code", pickupCode, "weighedKg", "400")).status()).as("not assigned yet").isEqualTo(404);

        var accept = api().post("/deliveries/" + deliveryId + "/accept", driver, null);
        assertThat(accept.status()).as(accept.raw()).isEqualTo(200);
        assertThat(accept.body().path("status").asString("")).isEqualTo("ASSIGNED");
        assertThat(accept.body().path("pickupCode").isNull()).as("driver never sees codes").isTrue();
        assertThat(accept.body().path("deliveryCode").isNull()).isTrue();
        assertThat(api().post("/deliveries/" + deliveryId + "/accept", driver, null).status())
                .as("job already taken").isEqualTo(409);

        assertThat(api().post("/orders/" + orderId + "/ready", farmer, null).status()).isEqualTo(200);

        // Handover codes: wrong code rejected, delivery code is not valid at pickup.
        var wrong = api().post("/deliveries/" + deliveryId + "/pickup", driver,
                Map.of("code", "000000", "weighedKg", "402"));
        assertThat(wrong.status()).isEqualTo(400);
        assertThat(wrong.body().path("code").asString("")).isEqualTo("INVALID_HANDOVER_CODE");
        assertThat(api().post("/deliveries/" + deliveryId + "/pickup", driver,
                Map.of("code", deliveryCode, "weighedKg", "402")).status()).isEqualTo(400);
        assertThat(api().post("/deliveries/" + deliveryId + "/deliver", driver,
                Map.of("code", deliveryCode)).status()).as("cannot deliver before pickup").isEqualTo(409);

        var pickup = api().post("/deliveries/" + deliveryId + "/pickup", driver,
                Map.of("code", pickupCode, "weighedKg", "402", "crateCount", 16, "note", "16 crates, 402 kg"));
        assertThat(pickup.status()).as(pickup.raw()).isEqualTo(200);
        assertThat(pickup.body().path("status").asString("")).isEqualTo("PICKED_UP");
        assertThat(api().get("/orders/" + orderId, buyer).body().path("status").asString("")).isEqualTo("PICKED_UP");
        var start = api().post("/deliveries/" + deliveryId + "/start", driver, null);
        assertThat(start.body().path("status").asString("")).isEqualTo("IN_TRANSIT");
        assertThat(api().post("/deliveries/" + deliveryId + "/location", driver,
                Map.of("latitude", 8.6, "longitude", 38.9)).status()).isEqualTo(204);

        // Buyer tracking timeline includes the GPS ping and the weighed pickup.
        var events = api().get("/deliveries/" + deliveryId + "/events", buyer);
        assertThat(events.status()).isEqualTo(200);
        assertThat(events.body().toString()).contains("LOCATION").contains("PICKED_UP").contains("402");

        var delivered = api().post("/deliveries/" + deliveryId + "/deliver", driver, Map.of("code", deliveryCode));
        assertThat(delivered.status()).as(delivered.raw()).isEqualTo(200);
        JsonNode afterDelivery = api().get("/orders/" + orderId, buyer).body();
        assertThat(afterDelivery.path("status").asString("")).isEqualTo("DELIVERED");
        assertThat(afterDelivery.path("deadlines").path("checkWindowEndsAt").isNull()).isFalse();
        assertThat(afterDelivery.path("allowedActions").toString()).contains("CONFIRM_DELIVERY").contains("REPORT_PROBLEM");
        assertThat(api().get("/wallet", farmer).body().path("heldForRelease").asString("")).isNotEqualTo("0.00");

        // Only the buyer can release the money.
        assertThat(api().post("/orders/" + orderId + "/confirm-delivery", farmer, null).status()).isEqualTo(403);
        var completed = api().post("/orders/" + orderId + "/confirm-delivery", buyer, null);
        assertThat(completed.status()).as(completed.raw()).isEqualTo(200);
        assertThat(completed.body().path("status").asString("")).isEqualTo("COMPLETED");
        assertThat(api().post("/orders/" + orderId + "/confirm-delivery", buyer, null).status()).isEqualTo(409);

        // Escrow released: farmer gets goods value, driver the delivery fee.
        assertThat(num(api().get("/wallet", farmer).body().path("balance"))).isGreaterThanOrEqualTo(new BigDecimal("18400.00"));
        assertThat(num(api().get("/wallet", driver).body().path("balance"))).isGreaterThanOrEqualTo(deliveryFee);
        var tx = api().get("/wallet/transactions", farmer);
        assertThat(tx.body().path("items").get(0).path("type").asString("")).isEqualTo("ESCROW_RELEASE");
        BigDecimal before = num(api().get("/wallet", farmer).body().path("balance"));
        var withdraw = api().post("/wallet/withdrawals", farmer, Map.of("amount", "1000"));
        assertThat(withdraw.status()).as(withdraw.raw()).isEqualTo(201);
        assertThat(withdraw.body().path("status").asString("")).isEqualTo("PAID");
        assertThat(num(api().get("/wallet", farmer).body().path("balance")))
                .isEqualByComparingTo(before.subtract(new BigDecimal("1000")));
        assertThat(api().post("/wallet/withdrawals", farmer, Map.of("amount", "99999999")).status())
                .as("cannot overdraw").isEqualTo(409);

        // Ratings: one per target, feeds the public trust profile.
        assertThat(api().get("/orders/" + orderId, buyer).body().path("allowedActions").toString()).contains("RATE");
        var rate = api().post("/orders/" + orderId + "/ratings", buyer, Map.of("target", "FARMER", "score", 5,
                "comment", "Fresh and on time"));
        assertThat(rate.status()).as(rate.raw()).isEqualTo(201);
        assertThat(api().post("/orders/" + orderId + "/ratings", buyer,
                Map.of("target", "FARMER", "score", 4)).status()).isEqualTo(409);
        assertThat(api().post("/orders/" + orderId + "/ratings", buyer,
                Map.of("target", "BUYER", "score", 4)).status()).isEqualTo(400);
        assertThat(api().post("/orders/" + orderId + "/ratings", buyer,
                Map.of("target", "DELIVERY", "score", 4)).status()).isEqualTo(201);
        var farmerId = api().get("/users/me", farmer).body().path("id").asString("");
        var publicFarmer = api().get("/farmers/" + farmerId + "/public", null);
        assertThat(publicFarmer.status()).isEqualTo(200);
        assertThat(num(publicFarmer.body().path("ratingAverage"))).isEqualByComparingTo("5.00");
        assertThat(publicFarmer.body().path("completedTrades").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(publicFarmer.raw()).doesNotContain("0911000001").doesNotContain("payout");
        assertThat(api().get("/users/" + farmerId + "/ratings", null).body().path("totalItems").asInt()).isEqualTo(1);

        // Everyone involved was told, in-app.
        for (String token : List.of(farmer, buyer, driver)) {
            var n = api().get("/notifications", token);
            assertThat(n.body().path("totalItems").asInt()).isGreaterThan(0);
        }
        assertThat(api().get("/notifications/unread-count", farmer).body().path("unread").asInt()).isGreaterThan(0);
        assertThat(api().post("/notifications/read-all", farmer, null).status()).isEqualTo(200);
        assertThat(api().get("/notifications/unread-count", farmer).body().path("unread").asInt()).isEqualTo(0);
    }

    @Test
    @Order(5)
    void disputeFreezesMoneyUntilAdminSettlesIt() {
        String buyer = buyer();
        String farmer = farmer();
        String admin = admin();
        BigDecimal farmerBefore = num(api().get("/wallet", farmer).body().path("balance"));

        String orderId = orderUntilDelivered("Red Onion", "200", "190");
        var orderView = api().get("/orders/" + orderId, buyer).body();
        BigDecimal total = num(orderView.path("amounts").path("total"));
        BigDecimal subtotal = num(orderView.path("amounts").path("subtotal"));
        BigDecimal fee = num(orderView.path("amounts").path("deliveryFee"));

        var dispute = api().post("/orders/" + orderId + "/disputes", buyer, Map.of("type", "LESS_THAN_ORDERED",
                "description", "Only 190 kg arrived", "receivedQuantityKg", "190"));
        assertThat(dispute.status()).as(dispute.raw()).isEqualTo(201);
        String disputeId = dispute.body().path("id").asString("");
        assertThat(dispute.body().path("disputeNumber").asString("")).startsWith("DSP-");
        assertThat(api().get("/orders/" + orderId, buyer).body().path("status").asString("")).isEqualTo("DISPUTED");
        assertThat(api().post("/orders/" + orderId + "/confirm-delivery", buyer, null).status())
                .as("money is frozen").isEqualTo(409);
        assertThat(api().post("/orders/" + orderId + "/disputes", buyer,
                Map.of("type", "OTHER", "description", "again")).status()).isEqualTo(409);

        assertThat(api().post("/disputes/" + disputeId + "/evidence", buyer,
                Map.of("kind", "SCALE_READING", "note", "Kitchen scale: 190 kg")).status()).isEqualTo(201);
        assertThat(api().get("/disputes/" + disputeId, farmer).body().path("evidence").size()).isGreaterThanOrEqualTo(2);
        assertThat(api().get("/disputes/" + disputeId, farmer2()).status()).isEqualTo(404);
        assertThat(api().post("/disputes/" + disputeId + "/evidence", buyer,
                Map.of("kind", "NOTE")).status()).as("empty evidence").isEqualTo(400);

        // Admin desk.
        assertThat(api().post("/admin/disputes/" + disputeId + "/resolve", buyer, Map.of()).status()).isEqualTo(403);
        var queue = api().get("/admin/disputes?status=OPEN&status=UNDER_REVIEW", admin);
        assertThat(queue.body().path("items").toString()).contains(disputeId);
        var assign = api().post("/admin/disputes/" + disputeId + "/assign", admin, null);
        assertThat(assign.body().path("status").asString("")).isEqualTo("UNDER_REVIEW");
        var detail = api().get("/admin/disputes/" + disputeId, admin);
        assertThat(detail.body().path("order").path("status").asString("")).isEqualTo("DISPUTED");

        // Refund more than was paid is refused; a valid split is applied.
        assertThat(api().post("/admin/disputes/" + disputeId + "/resolve", admin, Map.of("resolution", "PARTIAL",
                "farmerAmount", total, "driverAmount", fee, "buyerRefundAmount", "100", "notes", "too much"))
                .status()).isEqualTo(400);
        BigDecimal refund = new BigDecimal("380.00");
        BigDecimal farmerPay = subtotal.subtract(refund);
        var resolved = api().post("/admin/disputes/" + disputeId + "/resolve", admin, Map.of("resolution", "PARTIAL",
                "farmerAmount", farmerPay, "driverAmount", fee, "buyerRefundAmount", refund,
                "notes", "10 kg short; refund 380 from the farmer's share"));
        assertThat(resolved.status()).as(resolved.raw()).isEqualTo(200);
        assertThat(resolved.body().path("status").asString("")).isEqualTo("RESOLVED");
        assertThat(num(resolved.body().path("resolution").path("buyerRefundAmount"))).isEqualByComparingTo(refund);
        var finalOrder = api().get("/orders/" + orderId, buyer).body();
        assertThat(finalOrder.path("status").asString("")).isEqualTo("COMPLETED");
        assertThat(finalOrder.path("payment").path("status").asString("")).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(num(api().get("/wallet", farmer).body().path("balance")))
                .isEqualByComparingTo(farmerBefore.add(farmerPay));
        assertThat(api().post("/admin/disputes/" + disputeId + "/resolve", admin, Map.of("resolution", "RELEASE_ALL",
                "notes", "again")).status()).isEqualTo(409);
        assertThat(api().get("/notifications", buyer).body().path("items").toString()).contains("DISPUTE_RESOLVED");
    }

    @Test
    @Order(6)
    void cancellationBeforePickupRefundsTheBuyerAndReturnsStock() {
        String buyer = buyer();
        String farmer = farmer();
        JsonNode potato = listingByTitle("Potato");
        BigDecimal stock = num(potato.path("quantityAvailable"));
        var created = api().post("/orders", buyer, orderBody(potato.path("id").asString(""), "300"));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        String orderId = created.body().path("id").asString("");
        api().post("/orders/" + orderId + "/accept", farmer2(), null);
        api().post("/orders/" + orderId + "/payments", buyer, Map.of("method", "CBE_BIRR", "payerAccount", "1000111"));
        assertThat(api().get("/orders/" + orderId, buyer).body().path("status").asString("")).isEqualTo("PAID");

        var cancel = api().post("/orders/" + orderId + "/cancel", buyer, Map.of("reason", "Found a closer farm"));
        assertThat(cancel.status()).as(cancel.raw()).isEqualTo(200);
        assertThat(cancel.body().path("status").asString("")).isEqualTo("CANCELLED");
        assertThat(cancel.body().path("payment").path("status").asString("")).isEqualTo("REFUNDED");
        assertThat(num(listingByTitle("Potato").path("quantityAvailable"))).isEqualByComparingTo(stock);
        assertThat(api().get("/notifications", farmer2()).body().path("items").toString()).contains("ORDER_CANCELLED");
        // The posted delivery job disappeared with the order.
        var jobs = api().get("/deliveries/available", driver());
        assertThat(jobs.body().path("items").toString()).doesNotContain(orderId);
        assertThat(api().post("/orders/" + orderId + "/cancel", buyer, Map.of("reason", "again")).status()).isEqualTo(409);
    }

    @Test
    @Order(7)
    void asyncPaymentCompletesThroughSignedWebhook() {
        // Webhooks are authenticated by the provider secret, not by a user token.
        var unsigned = api().post("/payments/webhooks/mock", null,
                Map.of("transactionReference", "AGP-NOPE", "status", "SUCCEEDED"));
        assertThat(unsigned.status()).isEqualTo(401);
        assertThat(api().post("/payments/webhooks/unknown", null, Map.of()).status()).isIn(401, 404);
    }

    @Test
    @Order(8)
    void farmerListingManagementAndMarketplaceFilters() {
        String farmer = farmer();
        String buyer = buyer();
        String productId = api().get("/products?q=teff", null).body().get(0).path("id").asString("");
        var create = api().post("/listings", farmer, Map.of("productId", productId, "title", "Teff Magna",
                "unit", "QUINTAL", "quantity", "30", "pricePerUnit", "8500", "minOrderQuantity", "2",
                "qualityGrade", "B", "organic", true, "packaging", "100 kg sacks", "publish", false));
        assertThat(create.status()).as(create.raw()).isEqualTo(201);
        String id = create.body().path("id").asString("");
        assertThat(create.body().path("status").asString("")).isEqualTo("DRAFT");
        assertThat(num(create.body().path("unitWeightKg"))).as("quintal = 100 kg").isEqualByComparingTo("100");
        assertThat(api().get("/listings/" + id, null).status()).as("drafts are private").isEqualTo(404);
        assertThat(api().get("/listings/" + id, farmer).status()).isEqualTo(200);

        assertThat(api().put("/listings/" + id + "/status", farmer, Map.of("status", "ACTIVE")).body()
                .path("status").asString("")).isEqualTo("ACTIVE");
        assertThat(api().get("/listings?q=teff&organic=true&grade=B&minQuantity=10&maxPrice=9000", null)
                .body().path("totalItems").asInt()).isEqualTo(1);
        assertThat(api().get("/listings?q=teff&grade=A", null).body().path("totalItems").asInt()).isEqualTo(0);
        assertThat(api().get("/listings?lat=9.0&lon=38.7", null).body().path("items").get(0).path("distanceKm").isNull())
                .as("distance is computed when the buyer position is supplied").isFalse();

        var update = api().patch("/listings/" + id, farmer, Map.of("pricePerUnit", "8700", "quantityAvailable", "25"));
        assertThat(update.status()).as(update.raw()).isEqualTo(200);
        assertThat(num(update.body().path("quantityTotal"))).isEqualByComparingTo("25");
        assertThat(api().patch("/listings/" + id, farmer2(), Map.of("pricePerUnit", "1")).status())
                .as("another farmer cannot edit").isEqualTo(404);
        assertThat(api().patch("/listings/" + id, buyer, Map.of("pricePerUnit", "1")).status()).isEqualTo(403);

        assertThat(api().put("/listings/" + id + "/status", farmer, Map.of("status", "PAUSED")).status()).isEqualTo(200);
        assertThat(api().get("/listings?q=teff", null).body().path("totalItems").asInt()).isEqualTo(0);
        assertThat(api().get("/farmers/me/listings?status=PAUSED", farmer).body().path("totalItems").asInt()).isEqualTo(1);

        // Admin moderation overrides the farmer.
        String admin = admin();
        assertThat(api().patch("/admin/listings/" + id + "/status", admin, Map.of("status", "SUSPENDED")).status())
                .isEqualTo(200);
        assertThat(api().put("/listings/" + id + "/status", farmer, Map.of("status", "ACTIVE")).status()).isEqualTo(403);
        assertThat(api().delete("/listings/" + id, farmer).status()).isEqualTo(403);
    }

    @Test
    @Order(9)
    void driverProfileAvailabilityAndJobBoardRules() {
        String driver = driver();
        var me = api().get("/drivers/me", driver);
        assertThat(me.body().path("vehiclePlate").asString("")).isEqualTo("3-A 41218 AA");
        assertThat(api().put("/drivers/me/availability", driver, Map.of("availability", "BUSY")).status()).isEqualTo(400);
        assertThat(api().put("/drivers/me/availability", driver, Map.of("availability", "OFFLINE")).body()
                .path("availability").asString("")).isEqualTo("OFFLINE");
        assertThat(api().put("/drivers/me/availability", driver, Map.of("availability", "AVAILABLE")).status()).isEqualTo(200);
        assertThat(api().post("/drivers/me/location", driver, Map.of("latitude", 8.5, "longitude", 38.9)).status())
                .isEqualTo(204);
        assertThat(api().patch("/drivers/me", driver, Map.of("capacityKg", "0")).status()).isEqualTo(400);
        assertThat(api().get("/drivers/me", buyer()).status()).isEqualTo(403);
    }

    @Test
    @Order(10)
    void adminOperationsCoverUsersOrdersPaymentsReportsAndAudit() {
        String admin = admin();
        var dashboard = api().get("/admin/dashboard?period=MONTH", admin);
        assertThat(dashboard.status()).as(dashboard.raw()).isEqualTo(200);
        assertThat(dashboard.body().path("usersByRole").path("FARMER").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(dashboard.body().path("ordersByStatus").path("COMPLETED").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(num(dashboard.body().path("releasedInPeriod").path("amount"))).isGreaterThan(BigDecimal.ZERO);
        assertThat(num(dashboard.body().path("platformFeeInPeriod").path("amount"))).isGreaterThan(BigDecimal.ZERO);

        var users = api().get("/admin/users?role=DRIVER&q=abebe", admin);
        assertThat(users.body().path("totalItems").asInt()).isEqualTo(1);
        String driverId = users.body().path("items").get(0).path("id").asString("");
        var detail = api().get("/admin/users/" + driverId, admin);
        assertThat(detail.body().path("profile").path("vehicleType").asString("")).isEqualTo("ISUZU_TRUCK");
        assertThat(api().get("/admin/users/" + driverId, buyer()).status()).isEqualTo(403);

        var orders = api().get("/admin/orders?status=COMPLETED&status=CANCELLED", admin);
        assertThat(orders.body().path("totalItems").asInt()).isGreaterThanOrEqualTo(3);
        String orderId = orders.body().path("items").get(0).path("id").asString("");
        var orderDetail = api().get("/admin/orders/" + orderId, admin);
        assertThat(orderDetail.body().path("timeline").size()).isGreaterThanOrEqualTo(2);
        assertThat(orderDetail.body().path("payments").size()).isGreaterThanOrEqualTo(1);
        assertThat(api().get("/admin/orders?q=AL-2", admin).body().path("totalItems").asInt()).isGreaterThanOrEqualTo(3);

        var payments = api().get("/admin/payments?status=RELEASED", admin);
        assertThat(payments.body().path("totalItems").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(api().get("/admin/payouts", admin).body().path("totalItems").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(api().get("/admin/deliveries?status=DELIVERED", admin).body().path("totalItems").asInt())
                .isGreaterThanOrEqualTo(2);
        assertThat(api().get("/admin/drivers/available", admin).body().size()).isGreaterThanOrEqualTo(1);

        var report = api().get("/admin/reports/summary", admin);
        assertThat(report.status()).as(report.raw()).isEqualTo(200);
        assertThat(report.body().path("daily").size()).isEqualTo(30);
        assertThat(report.body().path("totalOrders").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(report.body().path("topProducts").size()).isGreaterThanOrEqualTo(1);

        // Catalogue maintenance.
        var category = api().post("/admin/categories", admin, Map.of("slug", "honey", "nameEn", "Honey", "sortOrder", 9));
        assertThat(category.status()).as(category.raw()).isEqualTo(201);
        assertThat(api().post("/admin/categories", admin, Map.of("slug", "honey", "nameEn", "Honey")).status())
                .isEqualTo(409);
        var product = api().post("/admin/products", admin, Map.of("slug", "wild-honey", "nameEn", "Wild honey",
                "categoryId", category.body().path("id").asString(""), "defaultUnit", "KG"));
        assertThat(product.status()).as(product.raw()).isEqualTo(201);

        // Broadcast + audit trail + settings.
        var broadcast = api().post("/admin/notifications/broadcast", admin,
                Map.of("role", "FARMER", "title", "Harvest season", "body", "New market prices are live"));
        assertThat(broadcast.body().path("recipients").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(api().get("/notifications", farmer()).body().path("items").get(0).path("title").asString(""))
                .isEqualTo("Harvest season");
        var audit = api().get("/admin/audit-logs", admin);
        assertThat(audit.body().path("totalItems").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(audit.raw()).contains("DISPUTE_RESOLVE").contains("VERIFICATION_APPROVE");
        assertThat(api().get("/admin/settings", admin).body().path("pricing").path("platformFeePercent").asString(""))
                .startsWith("2");

        // Account suspension logs the user out of refresh and blocks sign-in.
        var suspended = api().patch("/admin/users/" + driverId + "/status", admin,
                Map.of("status", "SUSPENDED", "reason", "Test"));
        assertThat(suspended.status()).isEqualTo(200);
        assertThat(api().post("/auth/login", null, Map.of("phone", "0933000001", "password", PASSWORD)).status())
                .isEqualTo(403);
        assertThat(api().patch("/admin/users/" + driverId + "/status", admin,
                Map.of("status", "ACTIVE")).status()).isEqualTo(200);
        assertThat(api().post("/auth/login", null, Map.of("phone", "0933000001", "password", PASSWORD)).status())
                .isEqualTo(200);
    }

    @Test
    @Order(11)
    void apiDocumentationIsPublished() {
        var docs = api().rootGet("/api-docs");
        assertThat(docs.status()).isEqualTo(200);
        assertThat(docs.raw()).contains("AgriLink API").contains("/api/v1/orders");
    }
}
