package com.msa.shop_orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ShopOrdersApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShopOrdersApplication.class, args);
	}

}
