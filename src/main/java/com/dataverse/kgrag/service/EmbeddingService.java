package com.dataverse.kgrag.service;

import com.dataverse.kgrag.model.EncodeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EmbeddingService {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);
    
    @Autowired
    private OllamaEmbeddingService ollamaEmbeddingService;
    
    @Autowired
    private Neo4jService neo4jService;
    
    /**
     * Generate and save embeddings for nodes from a specific property.
     * 
     * @param nodeLabel The label of nodes to process (e.g., "dataSetMetadata")
     * @param propertyName The property to use for generating embeddings (e.g., "description")
     * @param limit Maximum number of nodes to process
     * @param overwrite If true, regenerate embeddings for all nodes; if false, only process nodes without embeddings
     * @return Number of embeddings generated and saved
     */
    public int generateAndSaveEmbeddings(String nodeLabel, String propertyName, int limit, boolean overwrite) {
        // Get nodes that need embeddings
        List<EncodeRequest> nodesToEmbed = neo4jService.getNodesForEmbedding(nodeLabel, propertyName, limit, overwrite);
        
        if (nodesToEmbed.isEmpty()) {
            logger.info("No nodes found to generate embeddings for.");
            return 0;
        }
        
        logger.info("Generating embeddings for {} nodes...", nodesToEmbed.size());
        
        // Generate embeddings
        List<Map<String, Object>> embeddingsToSave = new ArrayList<>();
        for (EncodeRequest request : nodesToEmbed) {
            try {
                float[] embedding = ollamaEmbeddingService.generateEmbedding(request.getText());
                
                // Convert float[] to List<Double> for Neo4j
                List<Double> embeddingList = new ArrayList<>();
                for (float f : embedding) {
                    embeddingList.add((double) f);
                }
                
                Map<String, Object> embeddingData = new HashMap<>();
                embeddingData.put("id", request.getId());
                embeddingData.put("embedding", embeddingList);
                embeddingsToSave.add(embeddingData);
                
                logger.info("Generated embedding for node: {}", request.getId());
            } catch (Exception e) {
                logger.error("Failed to generate embedding for node {}: {}", request.getId(), e.getMessage());
            }
        }
        
        // Save embeddings to Neo4j
        if (!embeddingsToSave.isEmpty()) {
            neo4jService.saveNodeEmbeddings(embeddingsToSave);
            logger.info("Successfully saved {} embeddings to Neo4j.", embeddingsToSave.size());
        }
        
        return embeddingsToSave.size();
    }
    
    /**
     * Find similar nodes based on a text query using cosine similarity.
     * 
     * @param nodeLabel The label of nodes to search
     * @param queryText The text to find similar nodes for
     * @param topK Number of similar nodes to return
     * @return List of similar nodes with their properties and similarity scores
     */
    public List<Map<String, Object>> findSimilarNodesByText(String nodeLabel, String queryText, int topK) {
        // Generate embedding for the query text
        float[] queryEmbedding = ollamaEmbeddingService.generateEmbedding(queryText);
        
        // Find similar nodes using cosine similarity
        return neo4jService.findSimilarNodesCosine(nodeLabel, queryEmbedding, topK);
    }
    
    /**
     * Find similar nodes based on a text query using Euclidean similarity.
     * 
     * @param nodeLabel The label of nodes to search
     * @param queryText The text to find similar nodes for
     * @param topK Number of similar nodes to return
     * @return List of similar nodes with their properties and similarity scores
     */
    public List<Map<String, Object>> findSimilarNodesByTextEuclidean(String nodeLabel, String queryText, int topK) {
        // Generate embedding for the query text
        float[] queryEmbedding = ollamaEmbeddingService.generateEmbedding(queryText);
        
        // Find similar nodes using Euclidean similarity
        return neo4jService.findSimilarNodesEuclidean(nodeLabel, queryEmbedding, topK);
    }
    
    /**
     * Find similar nodes based on an existing node's embedding using cosine similarity.
     * 
     * @param nodeLabel The label of nodes to search
     * @param embedding The embedding vector to compare against
     * @param topK Number of similar nodes to return
     * @return List of similar nodes with their properties and similarity scores
     */
    public List<Map<String, Object>> findSimilarNodesByEmbedding(String nodeLabel, float[] embedding, int topK) {
        return neo4jService.findSimilarNodesCosine(nodeLabel, embedding, topK);
    }
    
    /**
     * Find similar nodes based on an existing node's embedding using Euclidean similarity.
     * 
     * @param nodeLabel The label of nodes to search
     * @param embedding The embedding vector to compare against
     * @param topK Number of similar nodes to return
     * @return List of similar nodes with their properties and similarity scores
     */
    public List<Map<String, Object>> findSimilarNodesByEmbeddingEuclidean(String nodeLabel, float[] embedding, int topK) {
        return neo4jService.findSimilarNodesEuclidean(nodeLabel, embedding, topK);
    }
    
    /**
     * Aggregate name properties from nodes related to the given node IDs.
     * This method finds all outgoing relationships from the specified nodes (where they act as subjects)
     * and collects the 'name' property from the target nodes without duplicates.
     * 
     * @param nodeIds List of node element IDs to look up
     * @return Set of unique name values from related nodes
     */
    public Set<String> aggregateNamesFromNodeIds(List<String> nodeIds) {
        return neo4jService.aggregateNamesFromSubjectRelations(nodeIds);
    }
    
    /**
     * Find similar nodes and aggregate names from their related nodes in one operation.
     * This is a convenience method that performs similarity search and then aggregates
     * name properties from related nodes.
     * 
     * @param nodeLabel The label of nodes to search
     * @param queryText The text to find similar nodes for
     * @param topK Number of similar nodes to return
     * @param useCosine If true, use cosine similarity; if false, use Euclidean distance
     * @return Map containing similarity results and aggregated names
     */
    public Map<String, Object> findSimilarAndAggregateNames(String nodeLabel, String queryText, int topK, boolean useCosine) {
        // Find similar nodes
        List<Map<String, Object>> similarNodes = useCosine ? 
            findSimilarNodesByText(nodeLabel, queryText, topK) : 
            findSimilarNodesByTextEuclidean(nodeLabel, queryText, topK);
        
        // Aggregate names from related nodes
        Set<String> aggregatedNames = neo4jService.aggregateNamesFromSimilarNodesResults(similarNodes);
        
        // Build response
        Map<String, Object> result = new HashMap<>();
        result.put("similarNodes", similarNodes);
        result.put("aggregatedNames", new ArrayList<>(aggregatedNames));
        result.put("count", similarNodes.size());
        result.put("namesCount", aggregatedNames.size());
        result.put("method", useCosine ? "cosine" : "euclidean");
        
        return result;
    }

    /**
     * Get the total count of nodes with a specific label.
     * 
     * @param nodeLabel The label of the nodes to count
     * @return Total count of nodes
     */
    public long getNodeCount(String nodeLabel) {
        return neo4jService.countNodes(nodeLabel);
    }
}
