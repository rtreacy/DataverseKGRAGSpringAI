package com.dataverse.kgrag.service;

import com.dataverse.kgrag.config.Neo4jConfiguration;
import com.dataverse.kgrag.model.EncodeRequest;
import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Configuration
@EnableConfigurationProperties(Neo4jConfiguration.class)
public class Neo4jService {
    @Autowired
    private Neo4jConfiguration configuration;

    private Driver driver;

    public synchronized void setup() {
        if (driver == null) {
            driver = GraphDatabase.driver(
                    configuration.getUri(),
                    AuthTokens.basic(
                            configuration.getUser(),
                            configuration.getPassword()));
            driver.verifyConnectivity();
        }
    }

    /**
     * Get nodes from Neo4j database that need embeddings.
     * 
     * @param nodeLabel The label of the nodes to query (e.g., "dataSetMetadata")
     * @param propertyName The property to use for generating embeddings (e.g., "description")
     * @param limit Maximum number of nodes to retrieve
     * @param overwrite If true, retrieve all nodes; if false, only retrieve nodes without embeddings
     * @return List of EncodeRequest objects containing text and node IDs
     */
    public List<EncodeRequest> getNodesForEmbedding(String nodeLabel, String propertyName, int limit, boolean overwrite) {
        setup();
        
        // Validate inputs to prevent Cypher injection
        if (!nodeLabel.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid node label");
        }
        if (!propertyName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid property name");
        }
        
        String whereEmbedding = overwrite ? "TRUE" : "n.embedding IS NULL";
        String cypher = String.format("""
                MATCH (n:%s)
                WHERE %s AND n.%s IS NOT NULL AND trim(n.%s) <> ''
                RETURN elementId(n) AS id, n.%s AS text
                LIMIT $limit
                """, nodeLabel, whereEmbedding, propertyName, propertyName, propertyName);

        SessionConfig config = SessionConfig.builder().withDatabase(configuration.getDatabase()).build();
        try (Session session = driver.session(config)) {
            return session.executeRead(tx -> {
                List<EncodeRequest> out = new ArrayList<>();
                var result = tx.run(cypher, Map.of("limit", limit <= 0 ? 1000 : limit));
                while (result.hasNext()) {
                    var rec = result.next();
                    String id = rec.get("id").asString();
                    String text = rec.get("text").asString("");
                    out.add(new EncodeRequest(text, id));
                }
                return out;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return List.of();
    }

    /**
     * Save embeddings to Neo4j nodes.
     * 
     * @param embeddings List of maps containing node IDs and their embeddings
     */
    public void saveNodeEmbeddings(List<Map<String, Object>> embeddings) {
        setup();
        String cypher = """
            UNWIND $data AS row
            WITH row
            MATCH (n) WHERE elementId(n) = row.id
            CALL db.create.setNodeVectorProperty(n, 'embedding', row.embedding)
        """;
        SessionConfig config = SessionConfig.builder().withDatabase(configuration.getDatabase()).build();
        try (Session session = driver.session(config)) {
            session.executeWriteWithoutResult(tx -> tx.run(cypher, Map.of("data", embeddings)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Extract metadata for extractioncitationmetadata nodes.
     * 
     * @param limitPerSubject Number of rows to retrieve per subject
     * @param minDescriptionLength Minimum length of the description
     * @return List of maps containing doi, title, description, and subject
     */
    public List<Map<String, Object>> extractCitationMetadata(int limitPerSubject, int minDescriptionLength) {
        setup();
        
        // We first try to check if 'subject' is a property or a relationship.
        // For this task, we will assume it's a property first, as it's common in flat metadata.
        // If it was a relationship, the query would look like (n)-[:HAS_SUBJECT]->(s)
        
        String cypher = String.format("""
                MATCH (n:extractioncitationmetadata)
                WHERE size(n.description) > %d AND n.subject IS NOT NULL
                WITH n.subject AS subject, n
                ORDER BY subject, n.doi
                WITH subject, collect({doi: n.doi, title: n.title, description: n.description})[..%d] AS nodes
                UNWIND nodes AS node
                RETURN node.doi AS doi, node.title AS title, node.description AS description, subject
                """, minDescriptionLength, limitPerSubject);

        SessionConfig config = SessionConfig.builder().withDatabase(configuration.getDatabase()).build();
        try (Session session = driver.session(config)) {
            return session.executeRead(tx -> {
                List<Map<String, Object>> out = new ArrayList<>();
                var result = tx.run(cypher);
                while (result.hasNext()) {
                    out.add(result.next().asMap());
                }
                return out;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return List.of();
    }

    /**
     * Count total nodes with a specific label.
     * 
     * @param nodeLabel The label of the nodes to count
     * @return Total count of nodes
     */
    public long countNodes(String nodeLabel) {
        setup();
        
        // Validate inputs to prevent Cypher injection
        if (!nodeLabel.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid node label");
        }
        
        String cypher = String.format("MATCH (n:%s) RETURN count(n) AS count", nodeLabel);

        SessionConfig config = SessionConfig.builder().withDatabase(configuration.getDatabase()).build();
        try (Session session = driver.session(config)) {
            return session.executeRead(tx -> {
                var result = tx.run(cypher);
                if (result.hasNext()) {
                    return result.next().get("count").asLong();
                }
                return 0L;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0L;
    }

    /**
     * Find similar nodes using cosine similarity.
     * 
     * @param nodeLabel The label of nodes to search
     * @param embedding The query embedding vector
     * @param topK Number of similar nodes to return
     * @return List of maps containing node information and similarity scores
     */
    public List<Map<String, Object>> findSimilarNodesCosine(String nodeLabel, float[] embedding, int topK) {
        setup();
        
        if (!nodeLabel.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid node label");
        }
        
        // Convert float[] to List<Double> for Neo4j parameter
        List<Double> embeddingList = new ArrayList<>();
        for (float f : embedding) {
            embeddingList.add((double) f);
        }
        
        String cypher = String.format("""
                MATCH (n:%s)
                WHERE n.embedding IS NOT NULL
                WITH n, vector.similarity.cosine(n.embedding, $embedding) AS score
                ORDER BY score DESC
                LIMIT $topK
                WITH elementId(n) AS id, properties(n) AS props, score
                RETURN id, apoc.map.removeKey(props, 'embedding') AS properties, score
                """, nodeLabel);

        SessionConfig config = SessionConfig.builder().withDatabase(configuration.getDatabase()).build();
        try (Session session = driver.session(config)) {
            return session.executeRead(tx -> {
                List<Map<String, Object>> results = new ArrayList<>();
                var result = tx.run(cypher, Map.of("embedding", embeddingList, "topK", topK));
                while (result.hasNext()) {
                    var rec = result.next();
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", rec.get("id").asString());
                    item.put("properties", rec.get("properties").asMap());
                    item.put("score", rec.get("score").asDouble());
                    results.add(item);
                }
                return results;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return List.of();
    }

    /**
     * Find similar nodes using Euclidean distance.
     * 
     * @param nodeLabel The label of nodes to search
     * @param embedding The query embedding vector
     * @param topK Number of similar nodes to return
     * @return List of maps containing node information and similarity scores
     */
    public List<Map<String, Object>> findSimilarNodesEuclidean(String nodeLabel, float[] embedding, int topK) {
        setup();
        
        if (!nodeLabel.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid node label");
        }
        
        List<Double> embeddingList = new ArrayList<>();
        for (float f : embedding) {
            embeddingList.add((double) f);
        }
        
        String cypher = String.format("""
                MATCH (n:%s)
                WHERE n.embedding IS NOT NULL
                WITH n, vector.similarity.euclidean(n.embedding, $embedding) AS score
                ORDER BY score DESC
                LIMIT $topK
                WITH elementId(n) AS id, properties(n) AS props, score
                RETURN id, apoc.map.removeKey(props, 'embedding') AS properties, score
                """, nodeLabel);

        SessionConfig config = SessionConfig.builder().withDatabase(configuration.getDatabase()).build();
        try (Session session = driver.session(config)) {
            return session.executeRead(tx -> {
                List<Map<String, Object>> results = new ArrayList<>();
                var result = tx.run(cypher, Map.of("embedding", embeddingList, "topK", topK));
                while (result.hasNext()) {
                    var rec = result.next();
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", rec.get("id").asString());
                    item.put("properties", rec.get("properties").asMap());
                    item.put("score", rec.get("score").asDouble());
                    results.add(item);
                }
                return results;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return List.of();
    }

    /**
     * Aggregate name properties from nodes related to the given node IDs.
     * This method finds all outgoing relationships from the specified nodes (where they act as subjects)
     * and collects the 'name' property from the target nodes without duplicates.
     * 
     * @param nodeIds List of node element IDs to look up
     * @return Set of unique name values from related nodes
     */
    public Set<String> aggregateNamesFromSubjectRelations(List<String> nodeIds) {
        setup();
        
        if (nodeIds == null || nodeIds.isEmpty()) {
            return new HashSet<>();
        }
        
        String cypher = """
                UNWIND $nodeIds AS nodeId
                MATCH (n)-[r]->(m)
                WHERE elementId(n) = nodeId AND m.name IS NOT NULL
                RETURN DISTINCT m.name AS name
                """;
        
        SessionConfig config = SessionConfig.builder().withDatabase(configuration.getDatabase()).build();
        try (Session session = driver.session(config)) {
            return session.executeRead(tx -> {
                Set<String> names = new HashSet<>();
                var result = tx.run(cypher, Map.of("nodeIds", nodeIds));
                while (result.hasNext()) {
                    var rec = result.next();
                    String name = rec.get("name").asString("");
                    if (!name.isEmpty()) {
                        names.add(name);
                    }
                }
                return names;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new HashSet<>();
    }

    /**
     * Aggregate name properties from nodes related to the results of findSimilarNodes* methods.
     * This is a convenience method that extracts node IDs from similarity search results
     * and aggregates names from their related nodes.
     * 
     * @param similarNodesResults Results from findSimilarNodesCosine or findSimilarNodesEuclidean
     * @return Set of unique name values from related nodes
     */
    public Set<String> aggregateNamesFromSimilarNodesResults(List<Map<String, Object>> similarNodesResults) {
        if (similarNodesResults == null || similarNodesResults.isEmpty()) {
            return new HashSet<>();
        }
        
        List<String> nodeIds = new ArrayList<>();
        for (Map<String, Object> result : similarNodesResults) {
            Object id = result.get("id");
            if (id != null) {
                nodeIds.add(id.toString());
            }
        }
        
        return aggregateNamesFromSubjectRelations(nodeIds);
    }

    /**
     * Close the Neo4j driver connection.
     */
    public void close() {
        if (driver != null) {
            driver.close();
        }
    }
}
