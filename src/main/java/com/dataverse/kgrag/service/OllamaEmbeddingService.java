package com.dataverse.kgrag.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OllamaEmbeddingService {
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    public OllamaEmbeddingService(ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.embeddingModelProvider = embeddingModelProvider;
    }

    public float[] generateEmbedding(String text) {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            throw new IllegalStateException("No EmbeddingModel bean available. Ensure an embedding model (e.g., Ollama) is configured.");
        }
        return embeddingModel.embed(text);
    }

    public List<float[]> generateEmbeddingBatch(List<String> textList) {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            throw new IllegalStateException("No EmbeddingModel bean available. Ensure an embedding model (e.g., Ollama) is configured.");
        }
        return embeddingModel.embed(textList);
    }
}
