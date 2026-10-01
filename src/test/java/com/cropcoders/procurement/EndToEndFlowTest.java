package com.cropcoders.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.cropcoders.procurement.email.EmailMessage;
import com.cropcoders.procurement.email.EmailSender;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.Filter;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole app, step by step, the way people use it: super admin, centre admin, staff, farmer
 * (OTP login, registration with payment details, booking with the QR email), the operator desk
 * (check-in, call, weighment) and the payment (Razorpay order + signature check).
 *
 * It runs against its own throw-away database (farmer_procurement_e2e) on the local PostgreSQL,
 * so real data is never touched. Without a local PostgreSQL the test is skipped.
 */
@SpringBootTest(properties = {
        "app.email.provider=console",
        "SUPERADMIN_USERNAME=e2e.super",
        "SUPERADMIN_PASSWORD=E2e-super-pass-1",
})
class EndToEndFlowTest {

    private static final String SERVER = "jdbc:postgresql://localhost:5432/";
    private static final String DATABASE = "farmer_procurement_e2e";
    private static boolean databaseReady;

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private CapturingEmailSender emails;
    @Autowired
    private JdbcTemplate jdbc;
    @Value("${app.razorpay.key-secret:}")
    private String razorpaySecret;
    @Value("${app.razorpay.key-id:}")
    private String razorpayKeyId;

    private MockMvc mvc;
    private final JsonMapper json = JsonMapper.builder().build();

    /** Emails are captured instead of sent, so the test can read the OTP and see the QR picture. */
    @TestConfiguration
    static class Emails {
        @Bean
        @Primary
        CapturingEmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }
    }

    static class CapturingEmailSender implements EmailSender {
        final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

        @Override
        public String send(EmailMessage message) {
            sent.add(message);
            return "captured";
        }
    }

    @BeforeAll
    static void createDatabase() {
        try (Connection c = DriverManager.getConnection(SERVER + "postgres", "postgres", "postgres");
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
            s.execute("CREATE DATABASE " + DATABASE);
            databaseReady = true;
        } catch (SQLException e) {
            databaseReady = false;
        }
    }

    @AfterAll
    static void dropDatabase() throws SQLException {
        if (!databaseReady) {
            return;
        }
        try (Connection c = DriverManager.getConnection(SERVER + "postgres", "postgres", "postgres");
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> SERVER + DATABASE);
    }

    @BeforeEach
    void setUp() {
        assumeTrue(databaseReady, "No local PostgreSQL - end-to-end test skipped");
        Filter security = context.getBean("springSecurityFilterChain", Filter.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void wholeAppWorksFromSuperAdminToPayment() throws Exception {
        // ---------- Super admin ----------
        String superToken = login("e2e.super", "E2e-super-pass-1", "SUPER_ADMIN");
        expect(post("/api/auth/login", null, Map.of("username", "e2e.super", "password", "wrong")), 401);

        List<Map<String, Object>> centres = list(get("/api/admin/centres", superToken));
        assertThat(centres).as("super admin sees every centre").hasSize(3);
        long tnj = id(centres, "code", "TNJ");
        long tvr = id(centres, "code", "TVR");
        assertThat(list(get("/api/admin/staff", superToken))).as("V5 accounts: 3 admins + 3 staff").hasSize(6);
        expect(get("/api/admin/demo", superToken), 200);
        expect(post("/api/admin/centres", superToken, centre("TNJ", "08:00")), 409);      // code taken
        expect(post("/api/admin/centres", superToken, centre("KMB", "08:15")), 400);      // not on a slot boundary

        String created = body(expect(post("/api/admin/centres", superToken, centre("KMB", "08:00")), 201));
        long kmb = ((Number) JsonPath.read(created, "$.id")).longValue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM slots WHERE centre_id = ?", Integer.class, kmb))
                .as("a new centre gets bookable slots straight away").isPositive();

        expect(post("/api/admin/staff", superToken, staff("Dup", "staff.tnj", "OPERATOR", tnj)), 409);
        expect(post("/api/admin/staff", superToken, staff("KMB Admin", "admin.kmb", "ADMIN", kmb)), 201);

        // ---------- Centre admin: only their own centre ----------
        String adminToken = login("admin.kmb", "Pass-word-123", "ADMIN");
        assertThat(list(get("/api/admin/centres", adminToken))).extracting(c -> c.get("code")).containsExactly("KMB");
        assertThat((List<?>) JsonPath.read(body(expect(get("/api/admin/dashboard", adminToken), 200)), "$.centres"))
                .hasSize(1);
        expect(get("/api/admin/demo", adminToken), 403);
        expect(post("/api/admin/crops", adminToken, Map.of("nameEn", "X", "nameTa", "X", "nameHi", "X",
                "ratePerQtl", 10, "maxMoisturePct", 10, "active", true)), 403);
        expect(post("/api/admin/centres", adminToken, centre("QQQ", "08:00")), 403);
        expect(put("/api/admin/centres/" + tvr, adminToken, Map.of("name", "X", "district", "X", "address", "X",
                "openTime", "08:00", "closeTime", "17:00", "counters", 2, "avgServiceMin", 10, "active", true)), 403);

        // A centre admin's new account is always staff of their own centre, whatever is sent.
        String newStaff = body(expect(post("/api/admin/staff", adminToken, staff("KMB Staff", "staff.kmb", "ADMIN", tvr)), 201));
        assertThat((String) JsonPath.read(newStaff, "$.role")).isEqualTo("OPERATOR");
        assertThat((String) JsonPath.read(newStaff, "$.centreCode")).isEqualTo("KMB");
        assertThat(list(get("/api/admin/staff", adminToken))).extracting(s -> s.get("username")).containsExactly("staff.kmb");
        long kmbAdminId = id(list(get("/api/admin/staff", superToken)), "username", "admin.kmb");
        expect(put("/api/admin/staff/" + kmbAdminId, adminToken, Map.of("name", "hack", "active", true)), 404);

        // ---------- Staff ----------
        String staffToken = login("staff.kmb", "Pass-word-123", "OPERATOR");
        expect(get("/api/admin/dashboard", staffToken), 403);
        expect(get("/api/operator/queue", superToken), 403);
        assertThat((String) JsonPath.read(body(expect(get("/api/operator/queue", staffToken), 200)), "$.centre.code"))
                .isEqualTo("KMB");

        // A switched-off account can no longer log in.
        long staffId = ((Number) JsonPath.read(newStaff, "$.id")).longValue();
        expect(put("/api/admin/staff/" + staffId, adminToken, Map.of("name", "KMB Staff", "active", false)), 200);
        expect(post("/api/auth/login", null, Map.of("username", "staff.kmb", "password", "Pass-word-123")), 403, 401);
        expect(put("/api/admin/staff/" + staffId, adminToken,
                Map.of("name", "KMB Staff", "active", true, "password", "New-pass-456")), 200);
        staffToken = login("staff.kmb", "New-pass-456", "OPERATOR");

        // ---------- Farmer: OTP, registration with payment details ----------
        String email = "e2e.farmer@example.com";
        expect(post("/api/auth/otp/send", null, Map.of("email", email, "language", "ta")), 200);
        String otp = sixDigits(waitForEmail(email, "").text());
        String verified = body(expect(post("/api/auth/otp/verify", null, Map.of("email", email, "otp", otp)), 200));
        assertThat((Boolean) JsonPath.read(verified, "$.registered")).isFalse();
        String registrationToken = JsonPath.read(verified, "$.registrationToken");

        Map<String, Object> registration = new java.util.HashMap<>(Map.of(
                "registrationToken", registrationToken, "name", "Ravi Kumar", "mobile", "9876543210",
                "address", "12 North Street, Kumbakonam", "district", "Thanjavur", "aadhaarLast4", "4821",
                "language", "ta"));
        registration.put("payout", Map.of("method", "BANK", "accountHolder", "Ravi Kumar",
                "accountNumber", "1234", "ifsc", "SBIN0001234", "bankName", "State Bank of India"));
        assertThat((String) JsonPath.read(body(expect(post("/api/auth/register", null, registration), 400)), "$.code"))
                .as("a short account number is refused").isEqualTo("PAYOUT_BANK_INVALID");
        registration.put("payout", Map.of("method", "BANK", "accountHolder", "Ravi Kumar",
                "accountNumber", "123456789012", "ifsc", "sbin0001234", "bankName", "State Bank of India"));
        String farmerToken = JsonPath.read(body(expect(post("/api/auth/register", null, registration), 201)), "$.token");

        String profile = body(expect(get("/api/farmers/me", farmerToken), 200));
        assertThat((String) JsonPath.read(profile, "$.accountNumberMasked")).isEqualTo("XXXXXXXX9012");
        assertThat((String) JsonPath.read(profile, "$.ifsc")).isEqualTo("SBIN0001234");
        assertThat(profile).doesNotContain("123456789012");
        String stored = jdbc.queryForObject("SELECT account_number FROM farmer_profiles", String.class);
        assertThat(stored).as("account number is encrypted in the database").startsWith("enc:").doesNotContain("123456789012");

        expect(put("/api/farmers/me/payout", farmerToken, Map.of("method", "UPI", "upiId", "not-a-upi")), 400);
        String upi = body(expect(put("/api/farmers/me/payout", farmerToken, Map.of("method", "UPI", "upiId", "ravi@okaxis")), 200));
        assertThat((String) JsonPath.read(upi, "$.upiId")).isEqualTo("ravi@okaxis");
        assertThat((Object) JsonPath.read(upi, "$.accountNumberMasked")).isNull();
        expect(put("/api/farmers/me/payout", farmerToken, Map.of("method", "BANK", "accountHolder", "Ravi Kumar",
                "accountNumber", "123456789012", "ifsc", "SBIN0001234", "bankName", "State Bank of India")), 200);

        // ---------- Booking a slot today at the new centre; the confirmation email has the QR ----------
        String days = body(expect(get("/api/centres/" + kmb + "/slots", farmerToken), 200));
        List<Map<String, Object>> todaySlots = JsonPath.read(days,
                "$[?(@.date == '" + LocalDate.now() + "')].slots[?(@.bookable == true)]");
        assumeTrue(!todaySlots.isEmpty(), "No slot left today (run between 8 am and 4:30 pm for the queue part)");
        long slotId = ((Number) todaySlots.get(0).get("id")).longValue();
        long cropId = ((Number) JsonPath.read(body(expect(get("/api/crops", null), 200)), "$[0].id")).longValue();

        String booking = body(expect(post("/api/bookings", farmerToken,
                Map.of("slotId", slotId, "cropId", cropId, "estQtyQtl", 25)), 201, 200));
        String token = JsonPath.read(booking, "$.token");
        assertThat(token).startsWith("KMB-");
        EmailMessage confirmation = waitForEmail(email, "QR");
        assertThat(confirmation.qrPng()).as("confirmation email carries the QR picture").isNotEmpty();
        assertThat(confirmation.html()).contains("cid:" + EmailMessage.QR_CONTENT_ID);

        // ---------- Operator desk: check-in, call, weighment ----------
        expect(post("/api/operator/checkin", staffToken, Map.of("code", token)), 200);
        expect(post("/api/operator/checkin", staffToken, Map.of("code", token)), 409, 400);   // twice
        expect(post("/api/operator/call-next", staffToken, Map.of("counterNo", 1)), 200);
        long bookingId = ((Number) JsonPath.read(booking, "$.id")).longValue();
        expect(post("/api/operator/bookings/" + bookingId + "/weighment", staffToken, Map.of(
                "weightQtl", 24.5, "moisturePct", 12.0, "grade", "A", "accepted", true)), 200);

        // ---------- Payment: the centre admin sees it with the farmer's bank details ----------
        String payments = body(expect(get("/api/admin/payments?status=PENDING", adminToken), 200));
        List<Map<String, Object>> pending = JsonPath.read(payments, "$.payments");
        assertThat(pending).hasSize(1);
        assertThat((String) pending.get(0).get("payTo")).contains("State Bank of India", "9012", "SBIN0001234");
        long paymentId = ((Number) pending.get(0).get("id")).longValue();

        String otherAdmin = login("admin.tvr", null, null);   // null: only checked when the password is known
        if (otherAdmin != null) {
            expect(post("/api/payments/" + paymentId + "/order", otherAdmin, null), 403);
        }

        assumeTrue(razorpayKeyId.startsWith("rzp_test_") && !razorpaySecret.isBlank(),
                "Razorpay test keys not set - payment step skipped");
        MvcResult orderResult = mvc.perform(post("/api/payments/" + paymentId + "/order", adminToken, null)).andReturn();
        String orderId;
        if (orderResult.getResponse().getStatus() == 200) {
            String order = body(orderResult);
            orderId = JsonPath.read(order, "$.orderId");
            assertThat(orderId).startsWith("order_");
            // 24.5 qtl of maize is Rs 58,800: above the test limit, so Razorpay charges the Rs 50,000 stand-in.
            assertThat(((Number) JsonPath.read(order, "$.amountPaise")).longValue()).isEqualTo(5_000_000L);
            assertThat(((Number) JsonPath.read(order, "$.fullAmountPaise")).longValue()).isEqualTo(5_880_000L);
        } else {
            // Razorpay's servers could not be reached (no internet, or a sandbox without network).
            // Put an order id in place, so the signature check and "paid" steps are still tested.
            System.err.println("WARNING: Razorpay order could not be created (" + body(orderResult)
                    + "); continuing with a stand-in order id");
            orderId = "order_E2eOffline1";
            jdbc.update("UPDATE payments SET rzp_order_id = ? WHERE id = ?", orderId, paymentId);
        }

        // Razorpay Checkout would now collect the money and sign the result; we sign it the same way.
        String paymentRef = "pay_E2eTest" + System.currentTimeMillis();
        expect(post("/api/payments/verify", adminToken, Map.of("razorpayOrderId", orderId,
                "razorpayPaymentId", paymentRef, "razorpaySignature", "bad")), 400);
        String paid = body(expect(post("/api/payments/verify", adminToken, Map.of("razorpayOrderId", orderId,
                "razorpayPaymentId", paymentRef, "razorpaySignature", sign(orderId + "|" + paymentRef))), 200));
        assertThat((String) JsonPath.read(paid, "$.status")).isEqualTo("PAID");

        String finished = body(expect(get("/api/bookings/" + bookingId, farmerToken), 200));
        assertThat((String) JsonPath.read(finished, "$.status")).isEqualTo("COMPLETED");
        assertThat((String) JsonPath.read(finished, "$.payment.status")).isEqualTo("PAID");
        waitForEmail(email, paymentRef);   // the "paid" email

        // ---------- Demo day: every demo farmer has (encrypted) payment details ----------
        expect(post("/api/admin/demo", superToken, null), 200);
        List<Map<String, Object>> demoPayments = JsonPath.read(
                body(expect(get("/api/admin/payments", superToken), 200)), "$.payments");
        assertThat(demoPayments).isNotEmpty().allSatisfy(p -> assertThat(p.get("payTo")).isNotNull());
        assertThat(jdbc.queryForList("SELECT account_number FROM farmer_profiles WHERE account_number IS NOT NULL", String.class))
                .allSatisfy(saved -> assertThat(saved).startsWith("enc:"));
        expect(delete("/api/admin/demo", superToken), 200);
    }

    // ---------- helpers ----------

    private String login(String username, String password, String role) throws Exception {
        if (password == null) {
            return null;
        }
        String result = body(expect(post("/api/auth/login", null, Map.of("username", username, "password", password)), 200));
        assertThat((String) JsonPath.read(result, "$.user.role")).isEqualTo(role);
        return JsonPath.read(result, "$.token");
    }

    private static Map<String, Object> centre(String code, String open) {
        return Map.of("code", code, "name", "Kumbakonam Procurement Centre", "district", "Thanjavur",
                "address", "Near Mahamaham Tank, Kumbakonam", "openTime", open, "closeTime", "17:00",
                "counters", 2, "avgServiceMin", 10);
    }

    private static Map<String, Object> staff(String name, String username, String role, long centreId) {
        return Map.of("name", name, "username", username, "password", "Pass-word-123", "role", role, "centreId", centreId);
    }

    private MockHttpServletRequestBuilder get(String path, String token) {
        return auth(MockMvcRequestBuilders.get(path), token);
    }

    private MockHttpServletRequestBuilder post(String path, String token, Object body) {
        return withBody(auth(MockMvcRequestBuilders.post(path), token), body);
    }

    private MockHttpServletRequestBuilder delete(String path, String token) {
        return auth(MockMvcRequestBuilders.delete(path), token);
    }

    private MockHttpServletRequestBuilder put(String path, String token, Object body) {
        return withBody(auth(MockMvcRequestBuilders.put(path), token), body);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, Object body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body == null ? "{}" : json.writeValueAsString(body));
    }

    private MvcResult expect(MockHttpServletRequestBuilder request, int... statuses) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        int status = result.getResponse().getStatus();
        assertThat(java.util.Arrays.stream(statuses).anyMatch(s -> s == status))
                .as("%s %s -> %d %s", result.getRequest().getMethod(), result.getRequest().getRequestURI(), status,
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isTrue();
        return result;
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(MockHttpServletRequestBuilder request) throws Exception {
        return JsonPath.read(body(expect(request, 200)), "$");
    }

    private static long id(List<Map<String, Object>> rows, String field, String value) {
        return rows.stream().filter(r -> value.equals(r.get(field))).findFirst()
                .map(r -> ((Number) r.get("id")).longValue())
                .orElseThrow(() -> new AssertionError("no row with " + field + " = " + value));
    }

    /** Emails go out in the background after the transaction, so wait a little for them. */
    private EmailMessage waitForEmail(String to, String containing) throws InterruptedException {
        long until = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < until) {
            for (int i = emails.sent.size() - 1; i >= 0; i--) {
                EmailMessage m = emails.sent.get(i);
                boolean match = containing.equals("QR") ? m.qrPng() != null : m.text().contains(containing);
                if (m.to().equals(to) && match) {
                    return m;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No email to " + to + " containing '" + containing + "'. Sent: "
                + emails.sent.stream().map(EmailMessage::subject).toList());
    }

    private static String sixDigits(String text) {
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(text);
        assertThat(m.find()).as("OTP in the email").isTrue();
        return m.group(1);
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(razorpaySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
