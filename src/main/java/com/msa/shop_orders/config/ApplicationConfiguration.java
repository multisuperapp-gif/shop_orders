package com.msa.shop_orders.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableConfigurationProperties(ApplicationProperties.class)
@EntityScan(basePackages = "com.msa.shop_orders.persistence.entity")
@EnableJpaRepositories(basePackages = "com.msa.shop_orders.persistence.repository")
@EnableMongoRepositories(basePackages = {
        "com.msa.shop_orders.consumer.cart.view.repository",
        "com.msa.shop_orders.provider.shop.view.repository"
})
public class ApplicationConfiguration {
}
