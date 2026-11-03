package com.invoicetracker.infrastructure.config;

import com.invoicetracker.application.auth.AuthService;
import com.invoicetracker.application.config.ApplicationSettings;
import com.invoicetracker.infrastructure.time.AdjustableClock;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class InfrastructureConfig {

    /** The business clock used for due dates and overdue detection. Shiftable by the dev tools. */
    @Bean
    @Primary
    public AdjustableClock businessClock(AppProperties properties) {
        return new AdjustableClock(Clock.system(ZoneId.of(properties.time().zone())));
    }

    /** Real time, for security concerns such as token expiry. Never shifted. */
    @Bean(AuthService.SYSTEM_CLOCK)
    public Clock systemClock(AppProperties properties) {
        return Clock.system(ZoneId.of(properties.time().zone()));
    }

    @Bean
    public ApplicationSettings applicationSettings(AppProperties p) {
        return new ApplicationSettings(
                p.auth().refreshTokenTtl(),
                p.invoices().defaultVatRate(),
                p.reminders().offsetsDays(),
                p.reminders().smsEnabled(),
                p.reminders().maxDeliveryAttempts(),
                p.reminders().dispatchBatchSize());
    }

    @Bean
    public TransactionOperations transactionOperations(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
