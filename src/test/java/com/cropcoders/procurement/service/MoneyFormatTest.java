package com.cropcoders.procurement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Amounts in emails use Indian digit grouping (lakhs and crores). */
class MoneyFormatTest {

    private static String money(String value) {
        return NotificationService.money(new BigDecimal(value));
    }

    @Test
    void groupsDigitsTheIndianWay() {
        assertThat(money("600000")).isEqualTo("₹6,00,000.00");
        assertThat(money("58040.5")).isEqualTo("₹58,040.50");
        assertThat(money("12345678.9")).isEqualTo("₹1,23,45,678.90");
        assertThat(money("1000")).isEqualTo("₹1,000.00");
        assertThat(money("999")).isEqualTo("₹999.00");
        assertThat(money("0.456")).isEqualTo("₹0.46");
    }
}
