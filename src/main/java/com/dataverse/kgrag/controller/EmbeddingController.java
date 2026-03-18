package com.dataverse.kgrag.controller;

import com.dataverse.kgrag.service.EmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/embeddings")
public class EmbeddingController {
    
    @Autowired
    private EmbeddingService embeddingService;
    
    /**
     * Generate and save embeddings for nodes.
     * 
     * Example: POST /api/embeddings/generate
     * Body: {
     *   "nodeLabel": "dataSetMetadata",
     *   "propertyName": "description",
     *   "limit": 100,
     *   "overwrite": false
     * }
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateEmbeddings(
            @RequestBody Map<String, Object> request) {
        
        String nodeLabel = (String) request.getOrDefault("nodeLabel", "dataSetMetadata");
        String propertyName = (String) request.getOrDefault("propertyName", "description");
        int limit = (Integer) request.getOrDefault("limit", 100);
        boolean overwrite = (Boolean) request.getOrDefault("overwrite", false);
        
        try {
            int count = embeddingService.generateAndSaveEmbeddings(nodeLabel, propertyName, limit, overwrite);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Successfully generated and saved " + count + " embeddings");
            response.put("count", count);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating embeddings: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Find similar nodes based on text query using cosine similarity.
     * 
     * Example: POST /api/embeddings/search/cosine
     * Body: {
     *   "nodeLabel": "dataSetMetadata",
     *   "queryText": "climate change research",
     *   "topK": 5
     * }
     */
    @PostMapping("/search/cosine")
    public ResponseEntity<Map<String, Object>> searchSimilarCosine(
            @RequestBody Map<String, Object> request) {
        
        String nodeLabel = (String) request.getOrDefault("nodeLabel", "dataSetMetadata");
        String queryText = (String) request.get("queryText");
        int topK = (Integer) request.getOrDefault("topK", 5);
        
        if (queryText == null || queryText.trim().isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "queryText is required");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            List<Map<String, Object>> results = embeddingService.findSimilarNodesByText(nodeLabel, queryText, topK);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("results", results);
            response.put("count", results.size());
            response.put("method", "cosine");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error searching similar nodes: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Find similar nodes based on text query using Euclidean similarity.
     * 
     * Example: POST /api/embeddings/search/euclidean
     * Body: {
     *   "nodeLabel": "dataSetMetadata",
     *   "queryText": "medical research",
     *   "topK": 5
     * }
     */
    @PostMapping("/search/euclidean")
    public ResponseEntity<Map<String, Object>> searchSimilarEuclidean(
            @RequestBody Map<String, Object> request) {
        
        String nodeLabel = (String) request.getOrDefault("nodeLabel", "dataSetMetadata");
        String queryText = (String) request.get("queryText");
        int topK = (Integer) request.getOrDefault("topK", 5);
        
        if (queryText == null || queryText.trim().isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "queryText is required");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            List<Map<String, Object>> results = embeddingService.findSimilarNodesByTextEuclidean(nodeLabel, queryText, topK);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("results", results);
            response.put("count", results.size());
            response.put("method", "euclidean");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error searching similar nodes: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Aggregate name properties from nodes related to the given node IDs.
     * 
     * Example: POST /api/embeddings/aggregate/names
     * Body: {
     *   "nodeIds": ["4:abc123:0", "4:def456:1"]
     * }
     */
    @PostMapping("/aggregate/names")
    public ResponseEntity<Map<String, Object>> aggregateNames(
            @RequestBody Map<String, Object> request) {
        
        @SuppressWarnings("unchecked")
        List<String> nodeIds = (List<String>) request.get("nodeIds");
        
        if (nodeIds == null || nodeIds.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "nodeIds is required and must not be empty");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            Set<String> names = embeddingService.aggregateNamesFromNodeIds(nodeIds);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("names", names);
            response.put("count", names.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error aggregating names: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Find similar nodes and aggregate names from their related nodes.
     * This combines similarity search with name aggregation in one operation.
     * 
     * Example: POST /api/embeddings/search/aggregate
     * Body: {
     *   "nodeLabel": "dataSetMetadata",
     *   "queryText": "climate change research",
     *   "topK": 5,
     *   "method": "cosine"
     * }
     */
    @PostMapping("/search/aggregate")
    public ResponseEntity<Map<String, Object>> searchAndAggregateNames(
            @RequestBody Map<String, Object> request) {
        
        String nodeLabel = (String) request.getOrDefault("nodeLabel", "dataSetMetadata");
        String queryText = (String) request.get("queryText");
        int topK = (Integer) request.getOrDefault("topK", 5);
        String method = (String) request.getOrDefault("method", "cosine");
        
        if (queryText == null || queryText.trim().isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "queryText is required");
            return ResponseEntity.badRequest().body(response);
        }
        
        boolean useCosine = "cosine".equalsIgnoreCase(method);
        
        try {
            Map<String, Object> result = embeddingService.findSimilarAndAggregateNames(nodeLabel, queryText, topK, useCosine);
            result.put("success", true);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error searching and aggregating names: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get the total count of dataSetMetadata nodes.
     * 
     * Example: GET /api/embeddings/dataset-metadata/count
     */
    @GetMapping("/dataset-metadata/count")
    public ResponseEntity<Map<String, Object>> getDataSetMetadataCount() {
        try {
            long count = embeddingService.getNodeCount("dataSetMetadata");
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", count);
            response.put("nodeLabel", "dataSetMetadata");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error getting node count: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Root endpoint - API information.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> index() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "DataverseKGRAGSpringAI");
        response.put("status", "UP");
        response.put("endpoints", Map.of(
            "health", "GET /api/embeddings/health",
            "generate", "POST /api/embeddings/generate",
            "searchCosine", "POST /api/embeddings/search/cosine",
            "searchEuclidean", "POST /api/embeddings/search/euclidean",
            "aggregateNames", "POST /api/embeddings/aggregate/names",
            "searchAggregate", "POST /api/embeddings/search/aggregate"
        ));
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "DataverseKGRAGSpringAI");
        return ResponseEntity.ok(response);
    }
}
