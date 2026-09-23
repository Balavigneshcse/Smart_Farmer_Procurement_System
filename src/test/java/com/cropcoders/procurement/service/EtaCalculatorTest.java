package com.cropcoders.procurement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class EtaCalculatorTest {

    @Test
    void usesConfiguredTimeUntilEnoughFarmersServed() {
        assertThat(EtaCalculator.averageServiceMinutes(List.of(), 10)).isEqualTo(10);
        assertThat(EtaCalculator.averageServiceMinutes(List.of(4.0, 6.0), 10)).isEqualTo(10);
    }

    @Test
    void usesRealAverageOnceEnoughFarmersServed() {
        assertThat(EtaCalculator.averageServiceMinutes(List.of(6.0, 8.0, 7.0), 10)).isEqualTo(7);
        assertThat(EtaCalculator.averageServiceMinutes(List.of(0.2, 0.3, 0.1), 10)).isEqualTo(1);   // never 0
    }

    @Test
    void firstFarmersGoStraightToFreeCounters() {
        // 3 counters, 2 free: positions 1 and 2 are called straight away
        assertThat(EtaCalculator.etaMinutes(1, 3, 2, 10)).isZero();
        assertThat(EtaCalculator.etaMinutes(2, 3, 2, 10)).isZero();
        assertThat(EtaCalculator.etaMinutes(3, 3, 2, 10)).isEqualTo(10);
    }

    @Test
    void allCountersBusy() {
        // 3 counters all busy, 10 minutes each: positions 1-3 wait one round, 4-6 two rounds
        assertThat(EtaCalculator.etaMinutes(1, 3, 0, 10)).isEqualTo(10);
        assertThat(EtaCalculator.etaMinutes(3, 3, 0, 10)).isEqualTo(10);
        assertThat(EtaCalculator.etaMinutes(4, 3, 0, 10)).isEqualTo(20);
        assertThat(EtaCalculator.etaMinutes(7, 3, 0, 10)).isEqualTo(30);
    }

    @Test
    void singleCounter() {
        assertThat(EtaCalculator.etaMinutes(5, 1, 0, 12)).isEqualTo(60);
    }
}
