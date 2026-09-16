package br.com.conde.tesouraria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TesourariaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TesourariaApplication.class, args);
    }
}
