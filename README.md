# DataverseKGRAGSpringAI

A Spring AI project for generating embeddings from Neo4j node properties and finding similar nodes using vector similarity search with Ollama LLM.

## Features

- **Embedding Generation**: Generate embeddings from Neo4j node properties using Ollama LLM (e.g., llama3.2:latest)
- **Vector Storage**: Store embeddings as node properties in Neo4j database
- **Similarity Search**: Find similar nodes using multiple similarity methods:
  - Cosine similarity
  - Euclidean distance
- **REST API**: Easy-to-use REST endpoints for all operations

## Architecture

- **Spring Boot 3.3.5**: Core framework
- **Spring AI 1.0.0-M3**: AI integration framework
- **Ollama**: Local LLM for embedding generation
- **Neo4j**: Graph database for storing nodes and embeddings

## Project Structure

```
src/main/java/com/dataverse/kgrag/
├── DataverseKGRAGSpringAIApplication.java  # Main application class
├── config/
│   └── Neo4jConfiguration.java             # Neo4j configuration
├── controller/
│   └── EmbeddingController.java            # REST API endpoints
├── model/
│   └── EncodeRequest.java                  # Data model for encoding requests
└── service/
    ├── EmbeddingService.java               # Orchestration service
    ├── Neo4jService.java                   # Neo4j database operations
    └── OllamaEmbeddingService.java         # Ollama embedding generation
```

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Neo4j Configuration
neo4j.uri=neo4j://127.0.0.1:7687
neo4j.user=neo4j
neo4j.password=password
neo4j.database=dv-kg

# Ollama Configuration
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.embedding.options.model=llama3.2:latest
```

## Prerequisites

1. **Neo4j Database**: Running instance with vector support (Neo4j 5.x+)
2. **Ollama**: Installed and running with llama3.2:latest model
   ```bash
   ollama pull llama3.2:latest
   ollama serve
   ```

## Building and Running

1. **Build the project**:
   ```bash
   mvn clean install
   ```

2. **Run the application**:
   ```bash
   mvn spring-boot:run
   ```

3. **Application will start on**: http://localhost:8080

## API Endpoints

### 1. Health Check
```bash
GET http://localhost:8080/api/embeddings/health
```

### 2. Node Statistics
Get the total number of `dataSetMetadata` nodes:

```bash
GET http://localhost:8080/api/embeddings/dataset-metadata/count
```

### 3. Generate Embeddings
Generate and save embeddings for nodes:

```bash
POST http://localhost:8080/api/embeddings/generate
Content-Type: application/json

{
  "nodeLabel": "dataSetMetadata",
  "propertyName": "description",
  "limit": 100,
  "overwrite": false
}
```

**Parameters**:
- `nodeLabel`: Label of nodes to process (e.g., "dataSetMetadata")
- `propertyName`: Property to use for embeddings (e.g., "description")
- `limit`: Maximum number of nodes to process
- `overwrite`: If true, regenerate embeddings for all nodes; if false, only process nodes without embeddings

### 3. Search Similar Nodes (Cosine Similarity)
Find similar nodes using cosine similarity:

```bash
POST http://localhost:8080/api/embeddings/search/cosine
Content-Type: application/json

{
  "nodeLabel": "dataSetMetadata",
  "queryText": "climate change research",
  "topK": 5
}
```

**Parameters**:
- `nodeLabel`: Label of nodes to search
- `queryText`: Text query to find similar nodes
- `topK`: Number of similar nodes to return

### 4. Search Similar Nodes (Euclidean Distance)
Find similar nodes using Euclidean distance:

```bash
POST http://localhost:8080/api/embeddings/search/euclidean
Content-Type: application/json

{
  "nodeLabel": "dataSetMetadata",
  "queryText": "medical research",
  "topK": 5
}
```

### 5. Aggregate Names from Node IDs
Aggregate name properties from nodes related to the given node IDs:

```bash
POST http://localhost:8080/api/embeddings/aggregate/names
Content-Type: application/json

{
  "nodeIds": ["4:abc123:0", "4:def456:1"]
}
```

**Parameters**:
- `nodeIds`: List of node element IDs (from similarity search results)

**Response**:
```json
{
  "success": true,
  "names": ["Author Name 1", "Author Name 2", "Topic Name"],
  "count": 3
}
```

This endpoint finds all outgoing relationships from the specified nodes (where they act as subjects) and collects the 'name' property from the target nodes without duplicates.

### 6. Search and Aggregate Names
Find similar nodes and aggregate names from their related nodes in one operation:

```bash
POST http://localhost:8080/api/embeddings/search/aggregate
Content-Type: application/json

{
  "nodeLabel": "dataSetMetadata",
  "queryText": "climate change research",
  "topK": 5,
  "method": "cosine"
}
```

**Parameters**:
- `nodeLabel`: Label of nodes to search
- `queryText`: Text query to find similar nodes
- `topK`: Number of similar nodes to return
- `method`: Similarity method - "cosine" (default) or "euclidean"

**Response**:
```json
{
  "success": true,
  "similarNodes": [
    {
      "id": "4:abc123:0",
      "properties": {"title": "Dataset 1", "description": "..."},
      "score": 0.95
    }
  ],
  "aggregatedNames": ["Name 1", "Name 2", "Name 3"],
  "count": 5,
  "namesCount": 3,
  "method": "cosine"
}
```

This is a convenience endpoint that combines similarity search with name aggregation in one operation.

## Example Workflow

1. **Ensure Neo4j has nodes with properties**:
   ```cypher
   CREATE (d:dataSetMetadata {
     title: "Climate Research Dataset",
     description: "A comprehensive dataset on global climate patterns"
   })
   ```

2. **Generate embeddings**:
   ```bash
   curl -X POST http://localhost:8080/api/embeddings/generate \
     -H "Content-Type: application/json" \
     -d '{
       "nodeLabel": "dataSetMetadata",
       "propertyName": "description",
       "limit": 100,
       "overwrite": false
     }'
   ```

3. **Search for similar nodes**:
   ```bash
   curl -X POST http://localhost:8080/api/embeddings/search/cosine \
     -H "Content-Type: application/json" \
     -d '{
       "nodeLabel": "dataSetMetadata",
       "queryText": "weather patterns",
       "topK": 5
     }'
   ```

4. **Aggregate names from node IDs** (if you have node IDs from a previous search):
   ```bash
   curl -X POST http://localhost:8080/api/embeddings/aggregate/names \
     -H "Content-Type: application/json" \
     -d '{
       "nodeIds": ["4:abc123:0", "4:def456:1"]
     }'
   ```

5. **Search and aggregate names in one call**:
   ```bash
   curl -X POST http://localhost:8080/api/embeddings/search/aggregate \
     -H "Content-Type: application/json" \
     -d '{
       "nodeLabel": "dataSetMetadata",
       "queryText": "weather patterns",
       "topK": 5,
       "method": "cosine"
     }'
   ```
   **UI Interface**:
   Access the web interface at `http://localhost:8080/index.html` to perform this workflow via a browser.

## Key Components

### OllamaEmbeddingService
- Generates embeddings using Ollama LLM
- Supports single and batch embedding generation

### Neo4jService
- Queries nodes from Neo4j database
- Saves embeddings to nodes using `db.create.setNodeVectorProperty`
- Performs similarity searches using `vector.similarity.cosine` and `vector.similarity.euclidean`
- Aggregates name properties from related nodes via `aggregateNamesFromSubjectRelations` and `aggregateNamesFromSimilarNodesResults`

### EmbeddingService
- Orchestrates the embedding generation and storage workflow
- Provides high-level methods for generating embeddings and finding similar nodes
- Handles conversion between different data formats
- Provides aggregation methods: `aggregateNamesFromNodeIds` and `findSimilarAndAggregateNames`

### EmbeddingController
- Exposes REST API endpoints
- Handles HTTP requests and responses
- Provides proper error handling and validation
- Includes endpoints for name aggregation: `/api/embeddings/aggregate/names` and `/api/embeddings/search/aggregate`

## Technologies Used

- Java 17
- Spring Boot 3.3.5
- Spring AI 1.0.0-M3
- Neo4j Java Driver
- Ollama (via Spring AI)
- Maven

## License

This project started from sample code in 'Building Neo4j-Powered Applications' with code changes to use Ollama and local models instead of OpenAI. It thus inherits the MIT license and Packt copyright


## How application.properties values are loaded

This is a standard Spring Boot application. Spring Boot automatically loads configuration from `application.properties` located on the classpath (here: `src/main/resources/application.properties`). The effective configuration comes from the following sources with the usual Spring precedence (last wins):

- Command-line arguments, e.g. `--neo4j.user=alice`
- OS environment variables, e.g. `NEO4J_USER=alice`
- Profile-specific files, e.g. `application-prod.properties`
- Default `application.properties`

Key points specific to this project:

- Neo4j settings are bound into a typed configuration class:
  - `com.dataverse.kgrag.config.Neo4jConfiguration` is annotated with `@ConfigurationProperties(prefix = "neo4j")`. Fields `uri`, `user`, `password`, and `database` are populated from properties `neo4j.uri`, `neo4j.user`, `neo4j.password`, and `neo4j.database`.
  - Binding is enabled by `@EnableConfigurationProperties(Neo4jConfiguration.class)` on `com.dataverse.kgrag.service.Neo4jService`.
  - The service receives the bound values via `@Autowired private Neo4jConfiguration configuration;` and uses them to create the Neo4j `Driver` and `SessionConfig`.

- Spring AI and server properties are handled by Spring Boot/Spring AI auto-configuration:
  - `spring.ai.ollama.*` properties configure the Ollama Chat and Embedding models.
  - `server.port` and `spring.application.name` are standard Spring Boot properties.

How to override values at runtime:

- Using environment variables (helpful for Docker/CI):
  - `export NEO4J_URI=neo4j://db:7687`
  - `export NEO4J_USER=neo4j`
  - `export NEO4J_PASSWORD=secret`
  - `export NEO4J_DATABASE=dv-kg`
  - `export SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434`
  - Then run the app.

- Using command-line arguments:
  - `mvn spring-boot:run -Dspring-boot.run.arguments="--neo4j.password=secret --server.port=9090"`

- Using profiles:
  - Create `src/main/resources/application-prod.properties` with prod-specific overrides and run with `--spring.profiles.active=prod`.

References in code:

- Neo4j configuration class: `src/main/java/com/dataverse/kgrag/config/Neo4jConfiguration.java`
- Enabling/binding and usage: `src/main/java/com/dataverse/kgrag/service/Neo4jService.java`
- Default properties: `src/main/resources/application.properties`
