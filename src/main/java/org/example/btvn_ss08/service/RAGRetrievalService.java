package org.example.btvn_ss08.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RAGRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RAGRetrievalService.class);
    
    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    @Value("${rag.similarity.threshold:0.75}")
    private double similarityThreshold;

    // Constructor Injection
    public RAGRetrievalService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    public String askQuestion(String userQuery) {
        log.info("Received query: {}", userQuery);

        // 1. Cấu hình SearchRequest: Giới hạn Top K = 3 và SimilarityThreshold = 0.75 (cấu hình động)
        SearchRequest searchRequest = SearchRequest.query(userQuery)
                .withTopK(3)
                .withSimilarityThreshold(similarityThreshold);

        // 2. Tìm kiếm các vector tương đồng
        List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);

        // 3. Lập trình phòng thủ: Kiểm tra danh sách tài liệu trả về
        if (relevantDocs == null || relevantDocs.isEmpty()) {
            log.warn("No relevant documents found crossing the similarity threshold of {}. Blocking LLM call.", similarityThreshold);
            return "Xin lỗi, thông tin bạn tìm kiếm không nằm trong tài liệu quy chế của chúng tôi.";
        }

        log.info("Found {} relevant documents. Proceeding to call LLM.", relevantDocs.size());

        // 4. Lắp ráp ngữ cảnh và gọi LLM (giả lập RAG prompt)
        String context = relevantDocs.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n---\n"));
                
        String prompt = String.format(
            "Dựa vào thông tin sau đây, hãy trả lời câu hỏi của người dùng.\n\nThông tin:\n%s\n\nCâu hỏi: %s",
            context, userQuery
        );

        return chatClient.prompt().user(prompt).call().content();
    }
}
