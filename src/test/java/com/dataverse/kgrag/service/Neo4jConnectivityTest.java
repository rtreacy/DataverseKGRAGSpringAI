package com.dataverse.kgrag.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
public class Neo4jConnectivityTest {

    @Autowired
    private Neo4jService neo4jService;

    @Test
    public void testNeo4jConnection() {
        assertDoesNotThrow(() -> {
            neo4jService.setup();
        }, "Neo4j connection failed. Check your credentials in application.properties.");
    }
}
