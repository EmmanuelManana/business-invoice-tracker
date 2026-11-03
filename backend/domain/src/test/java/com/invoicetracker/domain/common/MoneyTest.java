package com.invoicetracker.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest(name = "{0} x {1} = {2}")
    @CsvSource({"1, 100.00, 100.00", "3, 33.333, 100.00", "2.5, 19.99, 49.98", "0.333, 10.00, 3.33", "1.005, 1.00, 1.01"
    })
    void lineNetIsRoundedHalfUpToCents(String qty, String price, String expected) {
        assertThat(Money.lineNet(new BigDecimal(qty), new BigDecimal(price))).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "VAT on {0} at {1} = {2}")
    @CsvSource({"100.00, 0.15, 15.00", "99.99, 0.15, 15.00", "33.33, 0.15, 5.00", "10.10, 0.15, 1.52", "250.00, 0, 0.00"
    })
    void vatIsRoundedHalfUpToCents(String net, String rate, String expected) {
        assertThat(Money.vat(new BigDecimal(net), new BigDecimal(rate))).isEqualByComparingTo(expected);
    }

    @Test
    void ofNormalisesScale() {
        assertThat(Money.of("10").scale()).isEqualTo(2);
        assertThat(Money.of("10.005")).isEqualByComparingTo("10.01");
    }
}
