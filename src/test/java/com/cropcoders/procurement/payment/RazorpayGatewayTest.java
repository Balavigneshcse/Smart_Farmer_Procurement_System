package com.cropcoders.procurement.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.cropcoders.procurement.TestProperties;
import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.exception.ApiException;

class RazorpayGatewayTest {

    private static final String SECRET = "test_secret_key";
    private static final String ORDER = "order_TEST123";
    private static final String PAYMENT = "pay_TEST456";

    // Computed independently with: echo -n "order_TEST123|pay_TEST456" | openssl dgst -sha256 -hmac "test_secret_key"
    private static final String OPENSSL_SIGNATURE = "72c9623af2f6e48431d8ea9e8def333302776510a283061c77986c69b1052cdd";

    @Test
    void hmacMatchesOpenssl() {
        assertThat(RazorpayGateway.hmacSha256Hex(ORDER + "|" + PAYMENT, SECRET)).isEqualTo(OPENSSL_SIGNATURE);
    }

    @Test
    void acceptsGenuineSignature() {
        assertThat(RazorpayGateway.signatureMatches(ORDER, PAYMENT, OPENSSL_SIGNATURE, SECRET)).isTrue();
        assertThat(RazorpayGateway.signatureMatches(ORDER, PAYMENT, OPENSSL_SIGNATURE.toUpperCase(), SECRET)).isTrue();
    }

    @Test
    void rejectsTamperedData() {
        // someone changes the payment id, the order id, the signature, or uses another secret
        assertThat(RazorpayGateway.signatureMatches(ORDER, "pay_OTHER", OPENSSL_SIGNATURE, SECRET)).isFalse();
        assertThat(RazorpayGateway.signatureMatches("order_OTHER", PAYMENT, OPENSSL_SIGNATURE, SECRET)).isFalse();
        assertThat(RazorpayGateway.signatureMatches(ORDER, PAYMENT, "0" + OPENSSL_SIGNATURE.substring(1), SECRET)).isFalse();
        assertThat(RazorpayGateway.signatureMatches(ORDER, PAYMENT, OPENSSL_SIGNATURE, "wrong_secret")).isFalse();
        assertThat(RazorpayGateway.signatureMatches(ORDER, PAYMENT, null, SECRET)).isFalse();
    }

    @Test
    void refusesLiveKeysAndMissingKeys() {
        assertThat(gateway("", "").isConfigured()).isFalse();
        assertThat(gateway("rzp_live_abc", "secret").isConfigured()).isFalse();
        assertThat(gateway("rzp_test_abc", "secret").isConfigured()).isTrue();

        assertThatThrownBy(() -> gateway("rzp_live_abc", "secret").createOrder(100, "r", java.util.Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not set");
    }

    private static RazorpayGateway gateway(String keyId, String secret) {
        AppProperties base = TestProperties.withJwtSecret("test-secret-that-is-long-enough-0123456789");
        return new RazorpayGateway(new AppProperties(base.name(), base.jwt(), base.otp(), base.staffLogin(), base.email(), base.booking(),
                base.queue(), new AppProperties.Razorpay(keyId, secret, "https://api.razorpay.com/v1", 5_000_000)));
    }
}
