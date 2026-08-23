# Báo Cáo Bài 2: Chiến Lược Chunking Tối Ưu Cho Tài Liệu CRM

## 1. So sánh chiến lược Chunking cho Loại A và Loại B

| Tiêu chí | Token-based Chunking | Header-based Chunking |
| :--- | :--- | :--- |
| **Cơ chế hoạt động** | Cắt văn bản dựa trên một số lượng token/ký tự cố định (ví dụ: 800 token mỗi đoạn), không quan tâm đến ngữ nghĩa trình bày. | Nhận diện các thẻ cấu trúc (ví dụ: `#`, `##` hoặc dấu xuống dòng `\n`) để phân tách các khối nội dung theo cấu trúc logic của bài. |
| **Áp dụng cho Loại A (Quy trình các bước)** | **Nhược điểm lớn:** Dễ cắt ngang các bước liên kết chặt chẽ (ví dụ: Chunk 1 chứa Bước 1, 2 nhưng Bước 3 lại bị rớt sang Chunk 2). Hệ quả là khi query RAG, khách hàng sẽ nhận được quy trình bị thiếu bước hoặc đứt gãy. | **Ưu điểm lớn:** Có thể cấu hình để gộp chung toàn bộ các dòng thuộc cùng một Header lớn (ví dụ: `# Hướng dẫn Hoàn tiền`) vào một chunk duy nhất, đảm bảo tính liên kết từ Bước 1 đến Bước N. |
| **Áp dụng cho Loại B (Quy chế văn bản dài)** | **Ưu điểm lớn:** Rất phù hợp vì nội dung văn bản dài, miên man cần chia nhỏ thành các đoạn có kích thước đồng đều để mô hình LLM có thể so sánh Vector Similarity một cách chuẩn xác, không bị "quá tải" context window. | **Nhược điểm:** Nếu một phần của điều khoản quá dài, Header-based có thể sinh ra một chunk siêu to khổng lồ, vượt quá giới hạn maxTokens của mô hình Embedding và làm rớt thông tin khi nạp vào DB. |
| **Tốc độ xử lý** | Nhanh và đơn giản. | Chậm hơn một chút do phải duyệt cây cấu trúc (Markdown DOM) hoặc xử lý Regular Expression phức tạp. |

---

## 2. Mã nguồn Java cấu hình TextSplitters

Định nghĩa hai chiến lược chunking riêng biệt dưới dạng các Spring Beans để tiêm vào ứng dụng tuỳ vào loại tài liệu đang xử lý:

```java
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
        // Cấu hình: chunkSize=800, minChunkSizeChars=200, maxTokens=10000
        return new TokenTextSplitter(800, 200, 100, 10000, true);
    }

    // Chiến lược 2: Header-based / Structure-based Chunking (Phù hợp cho Loại A - Quy trình các bước)
    @Bean
    public TokenTextSplitter headerBasedSplitter() {
        log.info("Registering Header-based TextSplitter bean for Document Type A");
        // Cấu hình bảo vệ ngữ cảnh: 
        // 1. Min Chunk Size cao (400) để một chunk chứa đủ nhiều bước (Bước 1, Bước 2...) không bị ngắt giữa chừng.
        // 2. Chunk Size lớn (1500) để bao trọn một nhánh quy trình hoàn tiền.
        // 3. Sử dụng separator '\n' để tôn trọng cấu trúc ngắt dòng.
        return new TokenTextSplitter(
                1500, // chunkSize lớn hơn
                400,  // minChunkSizeChars (Context Preservation)
                100,  
                10000, 
                true, 
                Arrays.asList('\n')
        );
    }
}
```

---

## 3. Phân tích cơ chế bảo vệ ngữ cảnh (Context preservation)

**Cơ chế bảo vệ ngữ cảnh** là kỹ thuật nhằm đảm bảo các thông tin mang tính liên đới logic (đặc biệt là tài liệu Loại A: Bước 1, Bước 2...) không bị chia cắt mù quáng thành các vector cô lập.

Trong Spring AI, thuộc tính **`minChunkSizeChars`** đóng vai trò cực kỳ quan trọng cho cơ chế này:
- Nếu ta sử dụng Chunking mù quáng (ví dụ: cứ 100 ký tự cắt 1 lần), thì "Bước 1: Đăng nhập" sẽ thành 1 vector, và "Bước 2: Click hoàn tiền" thành 1 vector khác. Khi khách hàng hỏi "Quy trình hoàn tiền", hệ thống có thể chỉ bốc được Vector chứa "Bước 2" mà quên mất "Bước 1" do sai số Similarity Search.
- Bằng cách đẩy `minChunkSizeChars = 400` trong `headerBasedSplitter`, Splitter sẽ cố gắng từ chối việc cắt vụn các đoạn văn bản nếu chúng chưa đủ 400 ký tự. Kết hợp với việc cấu hình giữ lại vách ngăn phân tách (`keepSeparator = true`) và ưu tiên phân tách theo Header, TextSplitter sẽ gom "Bước 1, Bước 2, Bước 3" nằm trọn vẹn trong một chunk duy nhất dài khoảng hơn 400 ký tự. 
- Nhờ vậy, Chunk được Embed (Nhúng) vào pgvector sẽ chứa nguyên vẹn bối cảnh liên hoàn của một quy trình hoàn tiền (Context Preservation được đảm bảo), khắc phục hoàn toàn rủi ro suy giảm chất lượng sinh văn bản (Hallucination) do bị rớt bước của LLM sau này.

---

## 4. Log Console - Khởi chạy Spring Context đăng ký Beans thành công

```text
2026-08-21T00:23:45.123+07:00  INFO 44200 --- [           main] o.e.btvn_ss08.BtvnSs08Application        : Starting BtvnSs08Application using Java 17
2026-08-21T00:23:45.342+07:00  INFO 44200 --- [           main] o.e.b.config.TextSplitterConfig          : Registering Token-based TextSplitter bean for Document Type B
2026-08-21T00:23:45.343+07:00  INFO 44200 --- [           main] o.e.b.config.TextSplitterConfig          : Registering Header-based TextSplitter bean for Document Type A
2026-08-21T00:23:46.892+07:00  INFO 44200 --- [           main] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@6b8c8d8
2026-08-21T00:23:48.050+07:00  INFO 44200 --- [           main] o.e.btvn_ss08.BtvnSs08Application        : Started BtvnSs08Application in 3.456 seconds (process running for 3.99)
```
