package com.dataverse.kgrag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.dataverse.kgrag.config.Neo4jConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SpringBootTest
public class ExtractionTest {

    @Autowired
    private Neo4jConfiguration configuration;

    @Test
    public void extractMetadata() throws Exception {
        try (Driver driver = GraphDatabase.driver(
                configuration.getUri(),
                AuthTokens.basic(configuration.getUser(), configuration.getPassword()))) {
            
            SessionConfig config = SessionConfig.builder()
                    .withDatabase(configuration.getDatabase())
                    .build();
            
            try (Session session = driver.session(config)) {
                // First, let's explore the nodes to see properties and relationships
                System.out.println("[DEBUG_LOG] Exploring extractioncitationmetadata nodes...");
                String exploreCypher = "MATCH (n:extractioncitationmetadata) RETURN keys(n) AS keys, labels(n) AS labels LIMIT 1";
                session.executeRead(tx -> {
                    var result = tx.run(exploreCypher);
                    if (result.hasNext()) {
                        var record = result.next();
                        System.out.println("[DEBUG_LOG] Keys: " + record.get("keys").asList());
                        System.out.println("[DEBUG_LOG] Labels: " + record.get("labels").asList());
                    } else {
                        System.out.println("[DEBUG_LOG] No nodes found with label extractioncitationmetadata");
                    }
                    return null;
                });

                // Let's also check for 'subject' property or relationship
                String subjectCypher = "MATCH (n:extractioncitationmetadata) WHERE n.subject IS NOT NULL RETURN n.subject AS subject LIMIT 5";
                session.executeRead(tx -> {
                    var result = tx.run(subjectCypher);
                    while (result.hasNext()) {
                        System.out.println("[DEBUG_LOG] Found subject property: " + result.next().get("subject").asString());
                    }
                    return null;
                });
                
                String relCypher = "MATCH (n:extractioncitationmetadata)-[:HAS_SUBJECT|RELATED_TO|SUBJECT]-(s) RETURN labels(s) AS subjectLabels LIMIT 5";
                session.executeRead(tx -> {
                    var result = tx.run(relCypher);
                    while (result.hasNext()) {
                        System.out.println("[DEBUG_LOG] Found related subject node labels: " + result.next().get("subjectLabels").asList());
                    }
                    return null;
                });

                // Query for the actual extraction
                // Assuming 'subject' is a property for now based on common patterns in such datasets
                // If it's a relationship, I'll need to adjust.
                String cypher = """
                    MATCH (n:extractioncitationmetadata)
                    WHERE size(n.description) > 100 AND n.subject IS NOT NULL
                    WITH n.subject AS subject, n
                    ORDER BY subject, n.doi
                    WITH subject, collect(n)[..3] AS nodes
                    UNWIND nodes AS n
                    RETURN n.doi AS doi, n.title AS title, n.description AS description, subject
                """;

                List<Map<String, Object>> results = session.executeRead(tx -> {
                    var res = tx.run(cypher);
                    List<Map<String, Object>> list = new ArrayList<>();
                    while (res.hasNext()) {
                        list.add(res.next().asMap());
                    }
                    return list;
                });

                System.out.println("[DEBUG_LOG] Extracted " + results.size() + " rows.");

                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT);
                mapper.writeValue(new File("testmetadata.json"), results);
                System.out.println("[DEBUG_LOG] Saved to testmetadata.json");
            }
        }
    }
}
