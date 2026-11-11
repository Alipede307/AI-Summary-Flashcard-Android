package com.phuoc.aisummaryflashcard.helper;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Base64OutputStream;

import androidx.core.content.FileProvider;

import com.phuoc.aisummaryflashcard.model.DocumentRecord;
import com.phuoc.aisummaryflashcard.model.Flashcard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class FileAndAiHelper {

    private static final String MODEL_NAME = "gemini-3.5-flash-lite";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_NAME + ":generateContent";
    private static final long MAX_FILE_SIZE_BYTES = 15 * 1024 * 1024; // 15MB inline data limit
    // Caps memory if a .docx is huge or a zip bomb; ~1M chars is far more than any lecture note.
    private static final int MAX_DOCX_CHARS = 1_000_000;
    private static final String DATA_PLACEHOLDER = "__FILE_BASE64__";

    static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String MIME_PDF = "application/pdf";
    static final String MIME_JPEG = "image/jpeg";
    static final String MIME_PNG = "image/png";

    private static final String PROMPT =
            "Bạn là một trợ lý học tập thông minh. Hãy đọc kỹ tài liệu đính kèm và thực hiện 2 nhiệm vụ cốt lõi sau:\n\n" +
            "1. TÓM TẮT NỘI DUNG RÕ RÀNG & VỪA ĐỦ:\n" +
            "- Trích xuất các ý chính quan trọng nhất của bài học một cách rõ ràng, mạch lạc, dễ hiểu.\n" +
            "- Dung lượng vừa đủ: không quá ít sơ sài nhưng cũng không quá dài hay đi sâu lan man. Mỗi luận điểm viết từ 1 đến 2 câu súc tích.\n" +
            "- Trình bày thành danh sách các gạch đầu dòng, mỗi ý một dòng bắt đầu bằng ký tự \"• \".\n" +
            "- Tuyệt đối KHÔNG dùng ký tự Markdown (không dùng **, #, _, `) trong chuỗi văn bản tóm tắt.\n\n" +
            "2. BỘ THẺ FLASHCARD ÔN TẬP (TỐI THIỂU 10 THẺ):\n" +
            "- Tự động soạn ÍT NHẤT 10 THẺ GHI NHỚ (từ 10 đến 15 thẻ Flashcard) bao quát toàn bộ nội dung tài liệu. TUYỆT ĐỐI KHÔNG TẠO DƯỚI 10 THẺ.\n" +
            "- 'question': Câu hỏi ôn tập rõ ràng, trọng tâm, gợi mở kiến thức.\n" +
            "- 'answer': Câu trả lời chính xác, ngắn gọn, súc tích, dễ nhớ.\n\n" +
            "Trả lời bằng ngôn ngữ của tài liệu. Trả về đúng định dạng JSON có hai trường: 'summary' (chuỗi văn bản tóm tắt) và 'flashcards' (mảng đối tượng gồm 'question' và 'answer').";

    public static class FileInfo {
        public final String name;
        public final long size;
        public final String mimeType;

        public FileInfo(String name, long size, String mimeType) {
            this.name = name;
            this.size = size;
            this.mimeType = mimeType;
        }
    }

    public static FileInfo getFileInfo(Context context, Uri uri) {
        String name = "document";
        long size = 0;
        ContentResolver cr = context.getContentResolver();
        String mimeType = cr.getType(uri);

        try (Cursor cursor = cr.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIdx != -1 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx);
                int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx);
            }
        }
        return new FileInfo(name, size, mimeType);
    }

    /** Maps a picked file to one of the 4 supported MIME types, or throws a user-facing error. */
    static String resolveSupportedMime(String fileName, String mimeType) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (MIME_DOCX.equals(mimeType) || lower.endsWith(".docx")) return MIME_DOCX;
        if (MIME_PDF.equals(mimeType) || lower.endsWith(".pdf")) return MIME_PDF;
        if (MIME_JPEG.equals(mimeType) || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MIME_JPEG;
        if (MIME_PNG.equals(mimeType) || lower.endsWith(".png")) return MIME_PNG;
        throw new IllegalArgumentException("Định dạng tệp không được hỗ trợ. Chỉ nhận .docx, .pdf, .jpg, .png.");
    }

    public static String extractTextFromDocx(InputStream is) throws Exception {
        final StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
                    DefaultHandler handler = new DefaultHandler() {
                        private boolean inText = false;

                        @Override
                        public InputSource resolveEntity(String publicId, String systemId) {
                            // Block external entities (XXE): a crafted .docx must not read local files.
                            return new InputSource(new StringReader(""));
                        }

                        @Override
                        public void startElement(String uri, String localName, String qName, Attributes attributes) {
                            if (tagMatches(localName, qName, "t")) {
                                inText = true;
                            } else if (tagMatches(localName, qName, "tab")) {
                                sb.append('\t');
                            } else if (tagMatches(localName, qName, "br")) {
                                sb.append('\n');
                            }
                        }

                        @Override
                        public void characters(char[] ch, int start, int length) {
                            if (inText && sb.length() < MAX_DOCX_CHARS) {
                                sb.append(ch, start, length);
                            }
                        }

                        @Override
                        public void endElement(String uri, String localName, String qName) {
                            if (tagMatches(localName, qName, "t")) {
                                inText = false;
                            } else if (tagMatches(localName, qName, "tc")) {
                                sb.append('\t');
                            } else if (tagMatches(localName, qName, "p") || tagMatches(localName, qName, "tr")) {
                                sb.append('\n');
                            }
                        }
                    };
                    saxParser.parse(new InputSource(zis), handler);
                    break;
                }
            }
        }
        return sb.toString().trim();
    }

    private static boolean tagMatches(String localName, String qName, String tag) {
        return tag.equals(localName) || tag.equals(qName) || ("w:" + tag).equals(qName);
    }

    public static DocumentRecord analyzeDocumentWithGemini(Context context, Uri uri, String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng cung cấp Gemini API Key trước khi phân tích!");
        }

        FileInfo info = getFileInfo(context, uri);
        if (info.size > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Dung lượng tệp (" + (info.size / 1024 / 1024) + "MB) vượt quá giới hạn 15MB.");
        }
        String mime = resolveSupportedMime(info.name, info.mimeType);
        boolean isDocx = MIME_DOCX.equals(mime);
        ContentResolver cr = context.getContentResolver();

        JSONArray partsArray = new JSONArray();
        partsArray.put(new JSONObject().put("text", PROMPT));

        if (isDocx) {
            String docxText;
            try (InputStream is = cr.openInputStream(uri)) {
                if (is == null) throw new IllegalStateException("Không thể đọc tệp từ thiết bị.");
                docxText = extractTextFromDocx(is);
            }
            if (docxText.isEmpty()) {
                throw new IllegalArgumentException("Không tìm thấy văn bản trong tệp .docx (có thể là tệp rỗng hoặc scan ảnh).");
            }
            partsArray.put(new JSONObject().put("text", "Nội dung tài liệu:\n" + docxText));
        } else {
            // File bytes are streamed into this placeholder later, so the whole file never sits in RAM.
            JSONObject inlineData = new JSONObject().put("mimeType", mime).put("data", DATA_PLACEHOLDER);
            partsArray.put(new JSONObject().put("inlineData", inlineData));
        }

        JSONObject requestBody = new JSONObject();
        requestBody.put("contents", new JSONArray().put(new JSONObject().put("parts", partsArray)));
        requestBody.put("generationConfig", buildGenerationConfig());

        // Call Gemini REST API via HttpURLConnection
        HttpURLConnection conn = (HttpURLConnection) new URL(GEMINI_API_URL).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("x-goog-api-key", apiKey.trim()); // header, not URL, so the key is not logged with the URL
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(true);
            conn.setChunkedStreamingMode(0); // without this HttpURLConnection buffers the whole body in memory

            String json = requestBody.toString();
            try (OutputStream os = new BufferedOutputStream(conn.getOutputStream())) {
                if (isDocx) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                } else {
                    int idx = json.indexOf(DATA_PLACEHOLDER);
                    os.write(json.substring(0, idx).getBytes(StandardCharsets.UTF_8));
                    try (InputStream is = cr.openInputStream(uri)) {
                        if (is == null) throw new IllegalStateException("Không thể đọc tệp từ thiết bị.");
                        streamBase64(is, os);
                    }
                    os.write(json.substring(idx + DATA_PLACEHOLDER.length()).getBytes(StandardCharsets.UTF_8));
                }
            }

            int responseCode = conn.getResponseCode();
            boolean ok = responseCode >= 200 && responseCode < 300;
            String responseText = readAll(ok ? conn.getInputStream() : conn.getErrorStream());

            if (!ok) {
                String errDetail = responseText;
                try {
                    JSONObject errJson = new JSONObject(responseText);
                    if (errJson.has("error") && errJson.getJSONObject("error").has("message")) {
                        errDetail = errJson.getJSONObject("error").getString("message");
                    }
                } catch (Exception ignored) {}
                throw new IOException("Lỗi gọi Gemini API (HTTP " + responseCode + "): " + errDetail);
            }

            String createdAt = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
            return parseGeminiResponse(info.name, createdAt, responseText);
        } finally {
            conn.disconnect();
        }
    }

    private static JSONObject buildGenerationConfig() throws JSONException {
        // Strict Structured JSON schema
        JSONObject stringType = new JSONObject().put("type", "STRING");

        JSONObject itemSchema = new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject().put("question", stringType).put("answer", stringType))
                .put("required", new JSONArray().put("question").put("answer"));

        JSONObject schema = new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject()
                        .put("summary", stringType)
                        .put("flashcards", new JSONObject().put("type", "ARRAY").put("items", itemSchema)))
                .put("required", new JSONArray().put("summary").put("flashcards"));

        return new JSONObject()
                .put("responseMimeType", "application/json")
                .put("responseSchema", schema)
                .put("maxOutputTokens", 8192);
    }

    /** Base64-encodes `in` straight into `out`, enforcing the size limit while reading. */
    private static void streamBase64(InputStream in, OutputStream out) throws IOException {
        // Base64OutputStream.close() would also close `out`; this guard only flushes.
        OutputStream guard = new FilterOutputStream(out) {
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }

            @Override
            public void close() throws IOException {
                flush();
            }
        };
        long total = 0;
        try (Base64OutputStream b64 = new Base64OutputStream(guard, Base64.NO_WRAP)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > MAX_FILE_SIZE_BYTES) {
                    throw new IllegalArgumentException("Dung lượng tệp vượt quá giới hạn 15MB.");
                }
                b64.write(buffer, 0, n);
            }
        }
        if (total == 0) {
            throw new IllegalArgumentException("Tệp đã chọn rỗng (0 bytes).");
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /** Turns a generateContent response into a record; throws a user-facing error when blocked or cut off. */
    static DocumentRecord parseGeminiResponse(String fileName, String createdAt, String responseText) throws JSONException {
        JSONObject root = new JSONObject(responseText);
        JSONArray candidates = root.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            JSONObject feedback = root.optJSONObject("promptFeedback");
            String reason = feedback != null ? feedback.optString("blockReason", "") : "";
            throw new IllegalStateException(reason.isEmpty()
                    ? "Gemini không trả về kết quả."
                    : "Gemini từ chối xử lý tài liệu (" + reason + ").");
        }

        JSONObject candidate = candidates.getJSONObject(0);
        String finishReason = candidate.optString("finishReason", "");
        if ("MAX_TOKENS".equals(finishReason)) {
            throw new IllegalStateException("Tài liệu quá dài nên câu trả lời của AI bị cắt giữa chừng. Hãy thử tài liệu ngắn hơn.");
        }

        JSONObject content = candidate.optJSONObject("content");
        JSONArray parts = content != null ? content.optJSONArray("parts") : null;
        StringBuilder jsonText = new StringBuilder();
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                if (!part.optBoolean("thought", false)) {
                    jsonText.append(part.optString("text", ""));
                }
            }
        }
        if (jsonText.length() == 0) {
            throw new IllegalStateException("Gemini không trả về nội dung"
                    + (finishReason.isEmpty() ? "." : " (" + finishReason + ")."));
        }

        JSONObject parsedResult = new JSONObject(jsonText.toString());
        String summary = parsedResult.optString("summary", "Không có tóm tắt.");
        JSONArray cardsArray = parsedResult.optJSONArray("flashcards");

        List<Flashcard> flashcardList = new ArrayList<>();
        if (cardsArray != null) {
            for (int i = 0; i < cardsArray.length(); i++) {
                JSONObject cardObj = cardsArray.optJSONObject(i);
                if (cardObj == null) continue;
                String q = cardObj.optString("question", "").trim();
                String a = cardObj.optString("answer", "").trim();
                if (!q.isEmpty() && !a.isEmpty()) {
                    flashcardList.add(new Flashcard(q, a));
                }
            }
        }

        DocumentRecord record = new DocumentRecord(fileName, summary, createdAt);
        record.setFlashcards(flashcardList);
        return record;
    }

    /**
     * Represents a pre-authored diverse study template across different domains.
     */
    public static class SampleTemplate {
        public final String domain;
        public final String title;
        public final String fileName;
        public final String[] paragraphs;

        public SampleTemplate(String domain, String title, String fileName, String[] paragraphs) {
            this.domain = domain;
            this.title = title;
            this.fileName = fileName;
            this.paragraphs = paragraphs;
        }

        public String getFullContent() {
            StringBuilder sb = new StringBuilder();
            sb.append("【").append(domain).append("】\n");
            sb.append(title.toUpperCase(Locale.ROOT)).append("\n\n");
            for (String p : paragraphs) {
                sb.append(p).append("\n\n");
            }
            return sb.toString().trim();
        }
    }

    public static class SampleResult {
        public final Uri uri;
        public final String fileName;
        public final String domain;
        public final String title;
        public final String fullContent;

        public SampleResult(Uri uri, String fileName, String domain, String title, String fullContent) {
            this.uri = uri;
            this.fileName = fileName;
            this.domain = domain;
            this.title = title;
            this.fullContent = fullContent;
        }
    }

    public static final SampleTemplate[] SAMPLE_TEMPLATES = new SampleTemplate[] {
            // 1. Công nghệ Thông tin & Trí tuệ Nhân tạo
            new SampleTemplate(
                    "Công nghệ Thông tin & AI",
                    "Tổng quan về Trí tuệ Nhân tạo & Mạng Nơ-ron",
                    "mau_cntt_ai.docx",
                    new String[] {
                            "TÀI LIỆU ÔN TẬP: TRÍ TUỆ NHÂN TẠO & HỌC MÁY CƠ BẢN",
                            "Chương 1: Tổng quan về Trí tuệ Nhân tạo (AI)",
                            "Trí tuệ nhân tạo (AI) là ngành khoa học máy tính hướng tới việc xây dựng các phần mềm và hệ thống thông minh, có khả năng suy luận, học hỏi từ dữ liệu và tự động giải quyết các bài toán phức tạp thay con người.",
                            "Các phân ngành tiêu biểu gồm Học máy (Machine Learning), Xử lý ngôn ngữ tự nhiên (NLP), Thị giác máy tính (Computer Vision) và Robotics.",
                            "Chương 2: Các phương pháp Học máy cốt lõi",
                            "1. Học có giám sát (Supervised Learning): Thuật toán được huấn luyện trên tập dữ liệu đã có sẵn nhãn đầu vào và kết quả đầu ra mong muốn. Ứng dụng tiêu biểu gồm nhận diện thư rác (spam email), dự đoán giá bất động sản, phân loại ảnh y khoa.",
                            "2. Học không giám sát (Unsupervised Learning): Thuật toán khám phá các cấu trúc ẩn hoặc phân cụm dữ liệu mà không cần nhãn định trước. Ứng dụng gồm phân khúc khách hàng (K-Means Clustering) và nén giảm chiều dữ liệu (PCA).",
                            "3. Học tăng cường (Reinforcement Learning): Tác tử (Agent) học thông qua tương tác với môi trường, nhận phần thưởng (Reward) khi hành động đúng và điểm phạt khi hành động sai. Ứng dụng gồm robot tự hành và thuật toán AlphaGo.",
                            "Chương 3: Mạng Nơ-ron và AI Tạo sinh (Generative AI)",
                            "Mạng nơ-ron nhân tạo lấy cảm hứng từ cấu trúc tế bào thần kinh sinh học. Học sâu (Deep Learning) xếp chồng nhiều lớp nơ-ron ẩn để tự động trích xuất các đặc trưng phân cấp từ dữ liệu lớn.",
                            "Kiến trúc Transformer ra đời năm 2017 với cơ chế Attention (Tập trung chú ý) đã tạo nên bước ngoặt đột phá cho các mô hình ngôn ngữ lớn (LLMs) như Gemini, Claude và GPT.",
                            "Trong giáo dục, AI tạo sinh giúp sinh viên tóm tắt bài giảng tức thì, tự động sinh thẻ Flashcard ôn tập thông minh và cá nhân hóa quá trình tự học theo năng lực của từng học viên."
                    }
            ),
            // 2. Y Dược & Dược lý học
            new SampleTemplate(
                    "Y Dược & Dược lý học",
                    "Dược lý học Cơ bản: Cơ chế Tác dụng & Đề kháng Kháng sinh",
                    "mau_y_duoc_khang_sinh.docx",
                    new String[] {
                            "TÀI LIỆU ÔN TẬP: ĐẠI CƯƠNG KHÁNG SINH TRONG Y KHOA",
                            "Chương 1: Khái niệm & Phân loại Kháng sinh",
                            "Kháng sinh là những hợp chất sinh học hoặc tổng hợp hóa học có khả năng tiêu diệt vi khuẩn (Bactericidal) hoặc ức chế sự nhân lên của vi khuẩn (Bacteriostatic) ở nồng độ an toàn cho người bệnh.",
                            "Các nhóm kháng sinh chủ đạo gồm: Nhóm Beta-lactam (Penicillin, Cephalosporin), nhóm Aminoglycoside (Gentamicin, Amikacin), nhóm Macrolide (Azithromycin, Clarithromycin) và nhóm Fluoroquinolone (Ciprofloxacin).",
                            "Chương 2: Cơ chế Tác động Tế bào",
                            "1. Ức chế sinh tổng hợp thành tế bào: Các kháng sinh Beta-lactam gắn đặc hiệu vào protein liên kết penicillin (PBP), ngăn chặn liên kết chéo peptidoglycan làm vách tế bào mỏng manh và vỡ do áp suất thẩm thấu.",
                            "2. Ức chế sinh tổng hợp protein: Aminoglycoside gắn vào tiểu đơn vị ribosome 30S gây đọc sai mã di truyền, trong khi Macrolide gắn vào ribosome 50S ngăn chặn phản ứng kéo dài chuỗi peptide.",
                            "3. Ức chế tổng hợp acid nucleic: Quinolone ức chế enzyme DNA gyrase và Topoisomerase IV, khiến sợi DNA vi khuẩn không thể tháo xoắn để sao chép.",
                            "Chương 3: Hiện tượng Kháng thuốc & Nguyên tắc Điều trị",
                            "Vi khuẩn đề kháng thông qua: Tiết enzyme phân hủy thuốc (như beta-lactamase), biến đổi điểm gắn đích, giảm tính thấm màng ngoài hoặc kích hoạt bơm tống thuốc (efflux pump).",
                            "Nguyên tắc sử dụng: Chỉ dùng khi có bằng chứng nhiễm khuẩn, chọn đúng kháng sinh phổ hẹp phù hợp, đủ liều lượng và tuân thủ liệu trình tối thiểu để ngăn ngừa hình thành chủng vi khuẩn siêu kháng thuốc."
                    }
            ),
            // 3. Kinh tế & Tài chính Doanh nghiệp
            new SampleTemplate(
                    "Kinh tế & Tài chính",
                    "Kinh tế Vi mô: Quy luật Cung Cầu & Kiểm soát Lạm phát",
                    "mau_kinh_te_cung_cau.docx",
                    new String[] {
                            "TÀI LIỆU ÔN TẬP: NGUYÊN LÝ KINH TẾ HỌC CĂN BẢN",
                            "Chương 1: Thị trường & Quy luật Cung - Cầu",
                            "Quy luật Cung Cầu là nền tảng điều tiết phân bổ nguồn lực trong nền kinh tế thị trường. Cầu đại diện cho số lượng hàng hóa người mua sẵn sàng và có khả năng mua ở các mức giá khác nhau; Cung đại diện cho lượng hàng hóa người bán sẵn lòng cung ứng.",
                            "Điểm cân bằng thị trường (Equilibrium) là mức giá tại đó lượng cung bằng lượng cầu. Khi giá cao hơn mức cân bằng sẽ xảy ra thặng dư (Surplus), ngược lại khi giá thấp hơn sẽ xuất hiện tình trạng thiếu hụt (Shortage).",
                            "Chương 2: Độ co giãn của Cầu theo Giá",
                            "Độ co giãn (Price Elasticity of Demand - PED) đo lường mức độ phản ứng của lượng cầu khi giá hàng hóa biến động.",
                            "Hàng hóa thiết yếu (lương thực, thuốc men) có độ co giãn thấp (ít co giãn); hàng hóa xa xỉ hoặc có nhiều sản phẩm thay thế có độ co giãn cao. Doanh nghiệp dựa vào PED để quyết định chiến lược định giá tối đa hóa doanh thu.",
                            "Chương 3: Lạm phát & Chính sách Điều tiết Tiền tệ",
                            "Lạm phát là sự gia tăng liên tục của mức giá chung trong nền kinh tế theo thời gian, làm suy giảm sức mua của đồng tiền nội tệ.",
                            "Hai nguyên nhân chính: Lạm phát do cầu kéo (tổng cầu tăng quá nhanh vượt năng lực cung ứng) và Lạm phát do chi phí đẩy (giá dầu mỏ, logistics leo thang làm tăng chi phí sản xuất).",
                            "Biện pháp kiềm chế: Ngân hàng Trung ương thực thi chính sách tiền tệ thắt chặt, tăng lãi suất tái cấp vốn nhằm giảm cung tiền lưu thông kết hợp chính sách tài khóa cắt giảm chi tiêu công."
                    }
            ),
            // 4. Pháp luật & Luật học
            new SampleTemplate(
                    "Luật học & Pháp lý",
                    "Pháp luật Đại cương: Hợp đồng Dân sự & Biện pháp Bảo đảm",
                    "mau_luat_hop_dong.docx",
                    new String[] {
                            "TÀI LIỆU ÔN TẬP: PHÁP LUẬT DÂN SỰ & HỢP ĐỒNG",
                            "Chương 1: Bản chất của Hợp đồng Dân sự",
                            "Hợp đồng dân sự là sự thỏa thuận giữa các bên về việc xác lập, thay đổi hoặc chấm dứt quyền và nghĩa vụ dân sự. Các nguyên tắc cơ bản gồm: Tự do giao kết nhưng không trái pháp luật, tự nguyện, bình đẳng và trung thực.",
                            "Chương 2: Điều kiện Có hiệu lực của Hợp đồng",
                            "1. Chủ thể tham gia phải có năng lực pháp luật dân sự và năng lực hành vi dân sự phù hợp với giao dịch.",
                            "2. Chủ thể xác lập hợp đồng hoàn toàn tự nguyện, không bị đe dọa, lừa dối hay nhầm lẫn.",
                            "3. Mục đích và nội dung của giao dịch không vi phạm điều cấm của luật và không trái đạo đức xã hội.",
                            "4. Hình thức hợp đồng tuân thủ đúng luật định (bằng văn bản, công chứng chứng thực nếu luật có yêu cầu bắt buộc).",
                            "Chương 3: Các Biện pháp Bảo đảm Nghĩa vụ & Chế tài Vi phạm",
                            "Các biện pháp bảo đảm phổ biến: Cầm cố tài sản (bên có nghĩa vụ giao tài sản cho bên có quyền giữ), Thế chấp tài sản (bên thế chấp giữ lại tài sản và giấy tờ), Đặt cọc, Ký quỹ và Bảo lãnh từ bên thứ ba.",
                            "Khi một bên vi phạm hợp đồng, bên bị thiệt hại có quyền áp dụng các chế tài: Buộc thực hiện đúng hợp đồng, phạt vi phạm (theo tỷ lệ thỏa thuận hợp pháp) và yêu cầu bồi thường toàn bộ thiệt hại thực tế phát sinh."
                    }
            ),
            // 5. Môi trường & Năng lượng tái tạo
            new SampleTemplate(
                    "Môi trường & Năng lượng",
                    "Khoa học Môi trường: Biến đổi Khí hậu & Chuyển dịch Năng lượng Xanh",
                    "mau_moi_truong_nang_luong.docx",
                    new String[] {
                            "TÀI LIỆU ÔN TẬP: BIẾN ĐỔI KHÍ HẬU & PHÁT TRIỂN BỀN VỮNG",
                            "Chương 1: Cơ chế Biến đổi Khí hậu Toàn cầu",
                            "Biến đổi khí hậu là sự thay đổi dài hạn của khí hậu toàn cầu, chủ yếu bắt nguồn từ các hoạt động nhân sinh phát thải ồ ạt khí nhà kính (CO2, CH4, N2O) do đốt nhiên liệu hóa thạch, thâm canh nông nghiệp và nạn chặt phá rừng nhiệt đới.",
                            "Hiệu ứng nhà kính nhân tạo giữ lại bức xạ nhiệt trong bầu khí quyển, làm tăng nhiệt độ bề mặt Trái Đất, gây tan băng hai cực, dâng cao mực nước biển và kích hoạt chuỗi hình thái thời tiết cực đoan như bão lũ, hạn hán khốc liệt.",
                            "Chương 2: Chuyển dịch Năng lượng Tái tạo (Energy Transition)",
                            "Chuyển dịch năng lượng là quá trình thay thế cấu trúc năng lượng dựa vào than đá, dầu khí bằng các nguồn năng lượng sạch, phát thải thấp.",
                            "Các trụ cột công nghệ xanh: Điện mặt trời (Photovoltaic), Điện gió trên bờ và ngoài khơi (Offshore Wind), Thủy điện tích năng và công nghệ Hydro xanh (Green Hydrogen) dùng làm nhiên liệu lưu trữ mật độ cao.",
                            "Chương 3: Mục tiêu Net Zero & Kinh tế Tuần hoàn",
                            "Mục tiêu Net Zero (Phát thải ròng bằng 0) vào năm 2050 đòi hỏi cắt giảm tối đa khí thải kết hợp các giải pháp hấp thụ carbon qua rừng tự nhiên và công nghệ thu giữ carbon (CCUS).",
                            "Kinh tế tuần hoàn (Circular Economy) tập trung giảm thiểu tiêu thụ tài nguyên thô, tái chế tối đa vật liệu và kéo dài vòng đời sản phẩm, tạo nền tảng cho sự phát triển bền vững của nhân loại."
                    }
            )
    };

    private static int lastSampleIndex = -1;

    /**
     * Randomly picks one of the 5 diverse domain sample templates, writes to .docx,
     * exports a copy to Downloads (Android 10+), and returns the SampleResult with full content.
     */
    public static SampleResult createRandomSampleDocx(Context context) throws IOException {
        lastSampleIndex = (lastSampleIndex + 1) % SAMPLE_TEMPLATES.length;
        SampleTemplate template = SAMPLE_TEMPLATES[lastSampleIndex];

        File sampleFile = new File(context.getCacheDir(), template.fileName);
        try (FileOutputStream fos = new FileOutputStream(sampleFile);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos))) {
            writeDocxArchive(zos, template.paragraphs);
        }

        saveSampleToDownloadsBestEffort(context, sampleFile, template.fileName);
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", sampleFile);

        return new SampleResult(uri, template.fileName, template.domain, template.title, template.getFullContent());
    }

    /** Writes a full OpenXML .docx archive containing the provided paragraphs. */
    public static void writeDocxArchive(ZipOutputStream zos, String[] paragraphs) throws IOException {
        // 1. [Content_Types].xml
        zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
        byte[] contentTypes = (
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                "  <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>\n" +
                "</Types>"
        ).getBytes(StandardCharsets.UTF_8);
        zos.write(contentTypes);
        zos.closeEntry();

        // 2. _rels/.rels
        zos.putNextEntry(new ZipEntry("_rels/.rels"));
        byte[] rels = (
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>\n" +
                "</Relationships>"
        ).getBytes(StandardCharsets.UTF_8);
        zos.write(rels);
        zos.closeEntry();

        // 3. word/document.xml
        zos.putNextEntry(new ZipEntry("word/document.xml"));
        String documentXml = buildDocxXml(paragraphs);
        zos.write(documentXml.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    /** Unit-test accessible alias for template 0. */
    public static void writeSampleDocxArchive(ZipOutputStream zos) throws IOException {
        writeDocxArchive(zos, SAMPLE_TEMPLATES[0].paragraphs);
    }

    static String buildDocxXml(String[] paragraphs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n");
        sb.append("<w:body>\n");

        for (String p : paragraphs) {
            sb.append("<w:p><w:r><w:t>").append(escapeXml(p)).append("</w:t></w:r></w:p>\n");
        }

        sb.append("</w:body>\n</w:document>");
        return sb.toString();
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Extracts full preview text from any given content Uri.
     * For .docx: returns complete extracted document content.
     * For PDF / Image: returns structured format summary and readiness details.
     */
    public static String extractPreviewText(Context context, Uri uri) {
        ContentResolver cr = context.getContentResolver();
        FileInfo info = getFileInfo(context, uri);
        String mime = resolveSupportedMime(info.name, info.mimeType);
        if (MIME_DOCX.equals(mime)) {
            try (InputStream is = cr.openInputStream(uri)) {
                if (is != null) {
                    return extractTextFromDocx(is);
                }
            } catch (Exception e) {
                return "Không thể trích xuất văn bản: " + e.getMessage();
            }
        } else if (MIME_PDF.equals(mime)) {
            String sizeText = (info.size > 0) ? (info.size / 1024) + " KB" : "Đã nhận";
            return "Định dạng: Tài liệu PDF bài giảng (" + sizeText + ")\n\n"
                    + "Tên tệp: " + info.name + "\n\n"
                    + "Trạng thái: Tệp PDF đã sẵn sàng. Khi bạn bấm 'Phân tích tài liệu', nội dung toàn bộ văn bản sẽ được truyền bảo mật tới Gemini 3.5 Flash Lite để sinh Tóm tắt ý chính và Thẻ Flashcard học tập.";
        } else if (MIME_JPEG.equals(mime) || MIME_PNG.equals(mime)) {
            String sizeText = (info.size > 0) ? (info.size / 1024) + " KB" : "Đã nhận";
            return "Định dạng: Tệp ảnh chụp tài liệu (" + mime + ", " + sizeText + ")\n\n"
                    + "Tên tệp: " + info.name + "\n\n"
                    + "Trạng thái: Ảnh chụp bài giảng đã sẵn sàng. Khi bạn bấm 'Phân tích tài liệu', Gemini Vision sẽ nhận diện chữ viết (OCR) và tự động trích xuất ý chính học phần.";
        }
        return "Tài liệu: " + info.name;
    }

    /**
     * Returns the effective Gemini API Key. Prioritizes BuildConfig.GEMINI_API_KEY,
     * then falls back to project local property value so user never needs manual input.
     */
    public static String getEffectiveApiKey(Context context) {
        String key = com.phuoc.aisummaryflashcard.BuildConfig.GEMINI_API_KEY;
        if (key != null) key = key.trim();
        if (key != null && !key.isEmpty()) {
            return key;
        }
        return "";
    }

    private static void saveSampleToDownloadsBestEffort(Context context, File sourceFile, String displayName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
                values.put(MediaStore.Downloads.MIME_TYPE, MIME_DOCX);
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (InputStream in = new FileInputStream(sourceFile);
                         OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                        if (out != null) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                        }
                    }
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    context.getContentResolver().update(uri, values, null, null);
                }
            } catch (Exception ignored) {
                // Background export is purely convenience; cache FileProvider is primary.
            }
        }
    }
}
