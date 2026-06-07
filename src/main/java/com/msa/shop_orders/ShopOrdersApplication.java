package com.msa.shop_orders;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class ShopOrdersApplication {

	public static void main(String[] args) {
		// The platform runs in India — pin the whole JVM to IST so every
		// now()/date/time calculation uses Asia/Kolkata consistently.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(ShopOrdersApplication.class, args);
	}

}
