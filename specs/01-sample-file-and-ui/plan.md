# Technical Architecture & Implementation Plan

## 1. Component Design
- **`file_paths.xml` & `AndroidManifest.xml`**:
  - Configure `androidx.core.content.FileProvider` mapped to `${applicationId}.fileprovider`.
  - Expose `<cache-path name="cached_files" path="."/>` and `<files-path name="app_files" path="."/>`.
- **`FileAndAiHelper.java`**:
  - Implement `createSampleDocx(Context context)`:
    - Generates `sample_study_notes.docx` using `ZipOutputStream`.
    - Writes `[Content_Types].xml`, `_rels/.rels`, and `word/document.xml`.
    - Content: Comprehensive revision lecture note covering Machine Learning, Deep Learning, Transformer models, and Supervised vs Unsupervised learning.
    - Also writes or updates into `Environment.DIRECTORY_DOWNLOADS` (via MediaStore or File) as an offline copy.
    - Returns `Uri` from `FileProvider.getUriForFile`.
- **`MainActivity.java`**:
  - Replace `OpenDocument` default contract with `StartActivityForResult` passing `DocumentsContract.EXTRA_INITIAL_URI` & `Intent.EXTRA_LOCAL_ONLY`.
  - Add `btnSampleFile` listener calling `createSampleDocx`, updating UI immediately, and enabling `btnProcess`.
  - Display file source badges and clear status feedback.
- **`activity_main.xml`**:
  - Dual action buttons for file selection ("Chọn từ máy" & "Nạp file mẫu").
  - Full-width MaterialButton for "Phân tích AI".
  - Refined layout spacing, elevation, and onboarding copy.

## 2. Verification Strategy
- **Unit Testing**:
  - Test `SampleDocxGenerationTest` on JVM: invoke `FileAndAiHelper.writeSampleDocxToStream(...)` and assert `extractTextFromDocx` extracts all paragraphs cleanly.
- **Build & Assemble**:
  - Run `./gradlew testDebugUnitTest assembleDebug`.

