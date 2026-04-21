package com.msa.shop_orders.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableConfigurationProperties(ApplicationProperties.class)
@EnableJpaRepositories(basePackages = "com.msa.shop_orders.persistence.repository")
@EnableMongoRepositories(basePackages = "com.msa.shop_orders.persistence.mongo.repository")
public class ApplicationConfiguration {
}
