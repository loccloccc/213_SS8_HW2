package org.example.btvn_ss08;

import org.example.btvn_ss08.service.DocumentIngestionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BtvnSs08Application {

    public static void main(String[] args) {
        SpringApplication.run(BtvnSs08Application.class, args);
    }

    @Bean
    public CommandLineRunner runIngestion(DocumentIngestionService documentIngestionService) {
        return args -> {
            documentIngestionService.ingestDocument();
            System.out.println("Ingestion completed.");
            System.exit(0);
        };
    }
}
