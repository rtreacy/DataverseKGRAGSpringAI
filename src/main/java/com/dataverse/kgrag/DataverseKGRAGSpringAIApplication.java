package com.dataverse.kgrag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DataverseKGRAGSpringAIApplication {
    private static final Logger logger = LoggerFactory.getLogger(DataverseKGRAGSpringAIApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(DataverseKGRAGSpringAIApplication.class, args);
        logger.info("DataverseKGRAGSpringAI application started successfully!");
        logger.info("Access the API at http://localhost:8080/api/embeddings");
    }
}
