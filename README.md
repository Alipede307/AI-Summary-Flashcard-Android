# AISummaryFlashcard

Ứng dụng Android native (Java, Android SDK thuần) đọc tài liệu học tập, gọi Google Gemini REST API để tóm tắt ý chính, tạo bộ thẻ flashcard ôn tập và lưu trữ ngoại tuyến bằng SQLite.

## Tính năng chính

- Đọc tệp Word (`.docx`), PDF (`.pdf`) và ảnh chụp bài giảng (`.jpg`, `.png`) qua Android Storage Access Framework.
- Tự tạo tệp mẫu thử nghiệm: đóng gói trực tiếp tệp `.docx` chuẩn OpenXML vào bộ nhớ tạm với 5 chủ đề (CNTT, Y Dược, Kinh tế, Luật, Môi trường) để kiểm thử ngay trên máy ảo hoặc thiết bị thật.
- Xem trước toàn văn nội dung tài liệu `.docx` ngay sau khi chọn tệp.
- Tóm tắt nội dung theo từng ý gạch đầu dòng (`• `), loại bỏ các ký tự định dạng Markdown gây vỡ giao diện trên `TextView`.
- Tự động sinh tối thiểu 10 thẻ Flashcard cho mỗi tài liệu, hỗ trợ lật thẻ 3D hai mặt câu hỏi và đáp án.
- Lưu toàn bộ bản tóm tắt và danh sách thẻ vào cơ sở dữ liệu SQLite cục bộ để mở xem lại khi không có mạng.
- Chuyển đổi giao diện Sáng/Tối (Day/Night mode) đạt độ tương phản WCAG AAA, giữ nguyên trạng thái dữ liệu khi xoay màn hình.

## Kiến trúc và kỹ thuật xử lý

Ứng dụng gồm 4 Activity độc lập, sử dụng trực tiếp thư viện chuẩn của Android SDK thay vì các thư viện bên thứ ba như Retrofit, Gson hay Apache POI nhằm giữ dung lượng APK ở mức 6.2 MB và kiểm soát trực tiếp bộ nhớ đệm.

### 1. Các màn hình
- `MainActivity`: chọn tệp từ thiết bị, nạp tệp mẫu, hiển thị văn bản xem trước và gửi yêu cầu phân tích.
- `SummaryActivity`: hiển thị bản tóm tắt, lưu bản ghi vào SQLite và chuyển sang màn hình học thẻ.
- `FlashcardActivity`: xử lý hiệu ứng lật thẻ 3D hai pha (`rotationY`), hiển thị tiến độ và chuyển câu hỏi trước/sau.
- `SavedActivity`: liệt kê danh sách tài liệu đã lưu trong máy (sử dụng `ViewHolder`), cho phép mở lại hoặc xóa bản ghi.

### 2. Đọc tệp và quản lý bộ nhớ
- Tệp Word (`.docx`): `FileAndAiHelper` dùng `ZipInputStream` tìm mục `word/document.xml`, sau đó truyền qua `SAXParser` để đọc tuần tự các thẻ `<w:t>`, `<w:p>`, `<w:br>`, `<w:tab>`. Bộ phân tích ghi đè `resolveEntity()` trả về luồng rỗng nhằm chặn lỗi bảo mật XXE và giới hạn tối đa 1.000.000 ký tự để tránh tràn bộ nhớ do Zip Bomb.
- Tệp PDF và ảnh: mã hóa Base64 trực tiếp trên luồng ghi mạng bằng `Base64OutputStream` (bộ đệm 8 KB) kết hợp `HttpURLConnection.setChunkedStreamingMode(0)`, tránh việc phải nạp toàn bộ mảng byte của tệp lớn vào RAM.

### 3. Giao tiếp Google Gemini REST API
- Endpoint: `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent`
- Khóa API được truyền qua HTTP header `x-goog-api-key` thay vì gắn trên URL.
- Yêu cầu gửi kèm `responseMimeType = "application/json"`, `responseSchema` bắt buộc trả về hai trường `summary` và `flashcards`, cùng tham số `maxOutputTokens = 8192`.

### 4. Cơ sở dữ liệu SQLite (`study_assistant.db`)
- Bảng `documents`: `id`, `file_name`, `summary`, `created_at`.
- Bảng `flashcards`: `id`, `document_id`, `question`, `answer`.
- Thiết lập khóa ngoại `FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE` và chỉ mục `idx_flashcards_doc_id` trên cột `document_id`. Thao tác lưu tài liệu kèm bộ thẻ được gom trong một giao dịch (`beginTransaction`).

## Cấu trúc thư mục

```text
app/src/
├── main/
│   ├── AndroidManifest.xml
│   ├── java/com/phuoc/aisummaryflashcard/
│   │   ├── MainActivity.java              # Màn hình chính, nạp tệp, xem trước
│   │   ├── SummaryActivity.java           # Màn hình tóm tắt nội dung
│   │   ├── FlashcardActivity.java         # Màn hình lật thẻ flashcard 3D
│   │   ├── SavedActivity.java             # Danh sách tài liệu lưu ngoại tuyến
│   │   ├── db/
│   │   │   └── DatabaseHelper.java        # SQLiteOpenHelper, quản lý bảng và khóa ngoại
│   │   ├── helper/
│   │   │   ├── FileAndAiHelper.java       # Đọc docx bằng SAX, stream Base64, gọi Gemini
│   │   │   └── ThemeHelper.java           # Lưu cấu hình chế độ Sáng/Tối
│   │   └── model/
│   │       ├── DocumentRecord.java        # Thực thể tài liệu và danh sách flashcard
│   │       └── Flashcard.java             # Thực thể câu hỏi và đáp án
│   └── res/
│       ├── drawable/                      # Vector icon và nền bo góc
│       ├── layout/                        # Giao diện XML của 4 Activity và item list
│       ├── values/                        # Bảng màu Light mode và theme
│       └── values-night/                  # Bảng màu Dark mode
└── test/
    └── java/com/phuoc/aisummaryflashcard/
        ├── DocxAndModelTest.java          # 5 bài test trích xuất Word, chặn XXE, tạo 5 mẫu thử
        └── helper/GeminiResponseTest.java # 5 bài test phân tích JSON, bắt lỗi token/safety, lọc MIME
```

## Cài đặt và chạy dự án

### Yêu cầu môi trường
- Android Studio Ladybug trở lên.
- JDK 17 hoặc JDK 21 (có sẵn trong thư mục `jbr` của Android Studio).
- Android SDK (`minSdk = 24`, `targetSdk = 37`).

### Cấu hình API Key
Tạo hoặc mở tệp `local.properties` tại thư mục gốc của dự án (tệp này đã được loại trừ trong `.gitignore`) và thêm khóa Gemini API:

```properties
GEMINI_API_KEY=AIzaSy...
```

### Lệnh biên dịch và kiểm thử
Mở PowerShell tại thư mục gốc của dự án và chạy:

1. Chạy toàn bộ 10 bài Unit Test:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew testDebugUnitTest
```

2. Kiểm tra phân tích tĩnh (Android Lint):
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew lintDebug
```

3. Đóng gói tệp APK Debug:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew assembleDebug
```
