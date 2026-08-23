package org.example.btvn_ss08.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class TextSplitterConfig {

    private static final Logger log = LoggerFactory.getLogger(TextSplitterConfig.class);

    // Chiến lược 1: Token-based Chunking (Phù hợp cho Loại B - Bài viết văn bản dài, chia đều để nhúng)
    @Bean
    public TokenTextSplitter tokenBasedSplitter() {
        log.info("Registering Token-based TextSplitter bean for Document Type B");
        // Cấu hình: chunkSize=800, minChunkSizeChars=200
        return new TokenTextSplitter(800, 200, 100, 10000, true);
    }

    // Chiến lược 2: Header-based / Structure-based Chunking (Phù hợp cho Loại A - Quy trình các bước)
    @Bean
    public TokenTextSplitter headerBasedSplitter() {
        log.info("Registering Header-based TextSplitter bean for Document Type A");
        // Cấu hình bảo vệ ngữ cảnh: 
        // 1. Min Chunk Size cao (400) để một chunk chứa đủ nhiều bước (Bước 1, Bước 2...) không bị ngắt giữa chừng.
        // 2. Chunk Size lớn (1500) để bao trọn một nhánh quy trình hoàn tiền.
        // 3. Sử dụng separator nhận diện các gạch dòng hoặc header (ví dụ '\n' hoặc '#')
        return new TokenTextSplitter(
                1500, // chunkSize
                400,  // minChunkSizeChars - BẢO VỆ NGỮ CẢNH
                100,  // minChunkLengthToEmbed
                10000, 
                true, 
                Arrays.asList('\n')
        );
    }
}
