package org.example.btvn_ss08.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/crm")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class CrmChatController {

    private static final Logger log = LoggerFactory.getLogger(CrmChatController.class);
    private final ChatClient chatClient;

    public CrmChatController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        // Khởi tạo ChatClient tích hợp QuestionAnswerAdvisor để tự động hóa luồng RAG
        this.chatClient = chatClientBuilder
                .defaultSystem("Bạn là trợ lý ảo hỗ trợ khách hàng của CRM. Nhiệm vụ của bạn là trả lời các thắc mắc của khách hàng dựa TẤT CẢ vào thông tin được cung cấp trong CONTEXT. TUYỆT ĐỐI KHÔNG tự bịa ra thông tin. Nếu không tìm thấy thông tin giải quyết trong CONTEXT, hãy trả lời chính xác câu sau: 'Tôi không tìm thấy thông tin này trong hệ thống quy chế của chúng tôi.'")
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults().withTopK(3)))
                .build();
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chatWithDocument(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query cannot be empty"));
        }

        try {
            log.info("Processing chat request for query: {}", query);
            
            // Gọi LLM. QuestionAnswerAdvisor sẽ tự động hook vào quá trình này, 
            // query vector store, nhúng context vào prompt và đưa cho LLM xử lý.
            String response = chatClient.prompt().user(query).call().content();
            
            log.info("Successfully generated response using QuestionAnswerAdvisor");
            return ResponseEntity.ok(Map.of("answer", response));
            
        } catch (Exception e) {
            log.error("Error occurred while communicating with VectorStore or LLM: {}", e.getMessage(), e);
            // Bắt ngoại lệ để hệ thống không bị crash khi mất kết nối DB/LLM
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Đã xảy ra lỗi khi kết nối với hệ thống AI hoặc Vector Database. Vui lòng thử lại sau."));
        }
    }
}
