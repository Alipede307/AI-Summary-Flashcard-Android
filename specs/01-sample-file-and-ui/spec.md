# Feature Specification: Sample File Generator, Direct Storage SAF & UI/UX Polish

## 1. Context & Objective
Facilitate zero-friction testing on Android Emulators and physical devices for the "AI-Powered Document Summary & Flashcard" mid-term project.
Eliminate the need for manual file transfers via `adb push` by providing:
1. An in-app sample `.docx` generator accessible with a single tap.
2. An enhanced SAF `ACTION_OPEN_DOCUMENT` intent that hints directly to local Downloads / Device Storage.
3. UI/UX Pro Max polish adhering to educational study tool design patterns (Claymorphic card, clear hierarchy, 48dp+ touch targets, high contrast).

## 2. Requirements & Acceptance Criteria
- **FR-01 (Sample Doc Generator)**: Tapping "Nạp file mẫu" generates a valid, standard `.docx` (Computer Science & AI study notes) into cache/downloads, binds the `Uri` via `FileProvider`, and updates the UI ready for analysis in < 200ms.
- **FR-02 (Direct Storage SAF)**: Tapping "Chọn từ máy" launches `ACTION_OPEN_DOCUMENT` with `DocumentsContract.EXTRA_INITIAL_URI` pointing to `MediaStore.Downloads.EXTERNAL_CONTENT_URI` / root and `EXTRA_LOCAL_ONLY=true`.
- **FR-03 (UI/UX Pro Max Overhaul)**:
  - File card features two symmetric source buttons ("Chọn từ máy" & "Nạp file mẫu") and a full-width primary CTA ("Phân tích tài liệu").
  - File status box displays file name, size, and source badge ("Mẫu có sẵn" vs "Tệp máy").
  - Onboarding guide explicitly guides new users to test with the sample file in 1 click.
- **NFR-01 (Zero Dependencies)**: `.docx` generation uses Java standard `ZipOutputStream` without external Apache POI or heavy libraries.
- **NFR-02 (Verification)**: Local unit test validates that the generated sample `.docx` can be immediately parsed back by `FileAndAiHelper.extractTextFromDocx`.

