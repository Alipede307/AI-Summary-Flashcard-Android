# Feature Specification: Multi-Screen Architecture, 5 Diverse Industry Samples, Full Text Preview & Theme Toggle

## 1. Context & Objectives
Upgrade the Android application from a single-screen layout into a clean, modular multi-screen architecture conforming to mobile app development best practices and the user's specific mid-term project requirements:
1. **Navigation Architecture**:
   - `MainActivity`: Home screen with file source selection, 5-industry random sample generator, and immediate full-document text preview.
   - `SummaryActivity`: Dedicated "Tóm tắt ý chính" screen with clean executive summary, "Lưu" action, and entry to Flashcards.
   - `FlashcardActivity`: Dedicated interactive 3D Flashcard screen.
   - `SavedActivity`: Dedicated screen for viewing and managing offline saved documents.
   - Global Logo click: Returns immediately to `MainActivity`.
2. **5-Industry Sample Documents**:
   - Random generator with 5 diverse real-world domains: Computer Science & AI, Pharmacy & Medicine, Economics & Finance, Law & Contracts, Environmental Science & Energy.
3. **Full Content Preview**:
   - Scrollable preview card on Home screen displaying extracted document text immediately upon picking or generating.
4. **Theme Toggle (Dark / Light)**:
   - Dynamic Day/Night theme switching button on the top app bar across all screens, persisted in `SharedPreferences`.
5. **API Key & Action Rename**:
   - Remove manual API Key UI; use default background `BuildConfig.GEMINI_API_KEY`.
   - Top-right action becomes "Đã Lưu".
   - "Lưu Máy" renamed to "Lưu".

## 2. Acceptance Criteria
- **AC-01 (Top Bar Navigation)**: Logo click on any screen returns to `MainActivity`. Top right button is "Đã Lưu", opening `SavedActivity`.
- **AC-02 (5 Sample Topics)**: Tapping "Nạp file mẫu" selects from 5 distinct disciplines, creates a valid `.docx`, and immediately renders full text in the preview container.
- **AC-03 (Immediate Text Preview)**: `.docx` and sample files show full text inside a styled scrollable card before clicking "Phân tích tài liệu".
- **AC-04 (Multi-Screen Flow)**: Clicking "Phân tích tài liệu" navigates to `SummaryActivity`. In `SummaryActivity`, clicking "Luyện tập Flashcard" navigates to `FlashcardActivity`.
- **AC-05 (Offline Saving)**: Button labeled "Lưu" saves the record and toggles to "Đã lưu ✓".
- **AC-06 (Theme Toggle)**: Tapping the theme icon instantly toggles light/dark mode and remembers user preference.
- **AC-07 (Automated Tests)**: Unit tests verify all 5 sample document archives generate valid, extractable OpenXML text.

