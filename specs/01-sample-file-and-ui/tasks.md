# Implementation Tasks

- [x] Task 1: Setup FileProvider & Paths
  - Create `app/src/main/res/xml/file_paths.xml`
  - Register `FileProvider` in `app/src/main/AndroidManifest.xml`
- [x] Task 2: Implement Sample Docx Generator in `FileAndAiHelper`
  - Create zero-dependency generator writing valid OpenXML docx archive
  - Expose `createSampleDocx(Context context)` returning content Uri
  - Add auxiliary method to save sample file to Downloads
- [x] Task 3: Enhance SAF Intent in `MainActivity`
  - Configure `EXTRA_INITIAL_URI` and `EXTRA_LOCAL_ONLY`
  - Unify file selection callback for both SAF picker and sample generator
- [x] Task 4: UI/UX Pro Max Redesign in `activity_main.xml`
  - Add `btnSampleFile` alongside `btnSelectFile`
  - Redesign `btnProcess` as prominent full-width CTA
  - Enhance file info box with visual status chip
  - Update welcome guide card
- [x] Task 5: Unit Testing & Verification
  - Add test in `app/src/test/java/com/phuoc/aisummaryflashcard/DocxAndModelTest.java`
  - Execute `./gradlew testDebugUnitTest assembleDebug`
