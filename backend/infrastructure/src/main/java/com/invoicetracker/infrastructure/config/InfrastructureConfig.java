package com.invoicetracker.infrastructure.config;

import com.invoicetracker.application.auth.AuthService;
import com.invoicetracker.application.config.ApplicationSettings;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class InfrastructureConfig {

    /**
     * Real time in the application's time zone. It is never shifted: the demo time machine works
     * per business, through each business's own clock offset (see BusinessCalendar).
     */
    @Bean(AuthService.SYSTEM_CLOCK)
    public Clock systemClock(AppProperties properties) {
        return Clock.system(ZoneId.of(properties.time().zone()));
    }

    @Bean
    public ApplicationSettings applicationSettings(AppProperties p) {
        return new ApplicationSettings(
                p.auth().refreshTokenTtl(),
                p.auth().refreshReuseGrace(),
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
