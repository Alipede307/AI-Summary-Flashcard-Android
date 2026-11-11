# Technical Architecture & Implementation Plan: Multi-Screen & Theme System

## 1. Architecture Overview
- **Data Model**:
  - `DocumentRecord` & `Flashcard` implement `java.io.Serializable` for standard Intent parameter passing.
- **Theme Management**:
  - `ThemeHelper`: manages `AppCompatDelegate.setDefaultNightMode()` and saves preference in `SharedPreferences("app_theme")`.
  - Add dark mode color overrides in `values-night/colors.xml` and update `Theme.AISummaryFlashcard` to inherit from `Theme.MaterialComponents.DayNight.NoActionBar`.
- **Top Bar Component**:
  - Common top bar layout pattern included in all 4 activities:
    - Clickable Logo (returns to `MainActivity` with `FLAG_ACTIVITY_CLEAR_TOP`).
    - Title & Subtitle.
    - Theme toggle icon button (☀️/🌙).
    - "Đã Lưu" button (navigates to `SavedActivity`).
- **Sample Document Engine (`FileAndAiHelper.java`)**:
  - Array of 5 sample profiles:
    1. AI & Machine Learning (CNTT)
    2. Kháng sinh & Dược lý (Y Dược)
    3. Cung - Cầu & Lạm phát (Kinh tế)
    4. Pháp luật đại cương & Hợp đồng (Luật học)
    5. Biến đổi khí hậu & Năng lượng tái tạo (Môi trường)
  - `createRandomSampleDocx(Context)` randomly picks one, creates `.docx`, returns `Uri` and extracted preview string.
  - `extractPreviewText(Context, Uri)` allows immediate text extraction on `MainActivity` for any `.docx`.
- **Activities & Navigation**:
  - `MainActivity`: File selection, preview card, analysis trigger.
  - `SummaryActivity`: Displays document summary, "Lưu" button, "Luyện tập Flashcard" button.
  - `FlashcardActivity`: 3D card flip viewer with progress tracking.
  - `SavedActivity`: Offline history list from SQLite with "Xem lại" and "Xóa" actions.

## 2. Testing & Verification
- Unit test all 5 sample generators with `FileAndAiHelper.extractTextFromDocx`.
- Run JVM unit test suite and assemble debug APK.

