package com.quasar.quasardemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;


@EnableFeignClients
@SpringBootApplication
public class QuasardemoApplication {

    public static void main(String[] args) {
        //WordExportUtil.exportWord07();
        SpringApplication.run(QuasardemoApplication.class, args);
    }

}
