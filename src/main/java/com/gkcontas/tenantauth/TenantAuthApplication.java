package com.gkcontas.tenantauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TenantAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenantAuthApplication.class, args);
    }
}
