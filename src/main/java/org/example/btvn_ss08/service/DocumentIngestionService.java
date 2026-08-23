package org.example.btvn_ss08.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;

    @Value("classpath:docs/sample.md")
    private Resource resourceFile;

    // Constructor Injection
    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Transactional
    public void ingestDocument() {
        try {
            if (!resourceFile.exists()) {
                log.error("Resource file does not exist: docs/sample.md");
                return;
            }

            log.info("Starting to ingest document: docs/sample.md");

            // Initialize MarkdownDocumentReader
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withAdditionalMetadata("category", "CRM_CSKH")
                    .withAdditionalMetadata("source_file", "sample.md")
                    .build();
            MarkdownDocumentReader reader = new MarkdownDocumentReader(resourceFile, config);

            // Read documents
            List<Document> documents = reader.get();

            // Initialize TokenTextSplitter with specified parameters:
            // TokenTextSplitter(int defaultChunkSize, int minChunkSizeChars, int minChunkLengthToEmbed, int maxNumChunks, boolean keepSeparator)
            // chunkSize = 600, minChunkSizeChars = 120, maxTokens (maxNumChunks roughly) = 10000
            TokenTextSplitter splitter = new TokenTextSplitter(600, 120, 100, 10000, true, java.util.Arrays.asList('\n'));

            
            // Apply split
            List<Document> splitDocuments = splitter.apply(documents);
            log.info("Split into {} chunks.", splitDocuments.size());

            // Add to VectorStore
            vectorStore.add(splitDocuments);
            log.info("Successfully ingested document into VectorStore.");

        } catch (Exception e) {
            log.error("Error occurred while ingesting document: {}", e.getMessage(), e);
        }
    }
}
