package com.invoicetracker.infrastructure.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** The Spring Data interfaces are nested in {@link SpringDataRepositories}, which needs opting in. */
@Configuration
@EnableJpaRepositories(basePackageClasses = PersistenceConfig.class, considerNestedRepositories = true)
class PersistenceConfig {}
