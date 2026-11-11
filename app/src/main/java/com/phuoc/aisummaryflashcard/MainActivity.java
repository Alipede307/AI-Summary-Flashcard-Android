package com.phuoc.aisummaryflashcard;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.os.BundleCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.phuoc.aisummaryflashcard.helper.FileAndAiHelper;
import com.phuoc.aisummaryflashcard.helper.ThemeHelper;
import com.phuoc.aisummaryflashcard.model.DocumentRecord;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Uri selectedFileUri;

    // Header views
    private ImageView ivLogo;
    private MaterialButton btnToggleTheme, btnSaved;

    // Upload & action views
    private TextView tvSelectedFile, tvFileSubtitle, tvFileBadge, tvStatus;
    private MaterialButton btnSelectFile, btnSampleFile, btnProcess;
    private ProgressBar progressBar;

    // Full Content Preview views
    private MaterialCardView cardPreviewContainer, cardWelcomeGuide;
    private TextView tvPreviewContent, tvPreviewCharCount;

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        applyDeviceFile(uri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupEvents();

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        }
    }

    private void initViews() {
        ivLogo = findViewById(R.id.ivLogo);
        btnToggleTheme = findViewById(R.id.btnToggleTheme);
        btnSaved = findViewById(R.id.btnSaved);

        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        tvFileSubtitle = findViewById(R.id.tvFileSubtitle);
        tvFileBadge = findViewById(R.id.tvFileBadge);
        tvStatus = findViewById(R.id.tvStatus);

        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnSampleFile = findViewById(R.id.btnSampleFile);
        btnProcess = findViewById(R.id.btnProcess);
        progressBar = findViewById(R.id.progressBar);

        cardPreviewContainer = findViewById(R.id.cardPreviewContainer);
        tvPreviewContent = findViewById(R.id.tvPreviewContent);
        tvPreviewCharCount = findViewById(R.id.tvPreviewCharCount);
        cardWelcomeGuide = findViewById(R.id.cardWelcomeGuide);

        updateThemeIcon();
    }

    private void setupEvents() {
        // Logo click always returns or refreshes Home
        ivLogo.setOnClickListener(v -> {
            Toast.makeText(this, "Bạn đang ở Trang chủ.", Toast.LENGTH_SHORT).show();
        });

        // Theme Toggle (Dark / Light)
        btnToggleTheme.setOnClickListener(v -> {
            ThemeHelper.toggleTheme(this);
        });

        // Saved documents button ("Đã Lưu")
        btnSaved.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SavedActivity.class));
        });

        // Select file from device (Direct SAF storage hint)
        btnSelectFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            String[] mimeTypes = {
                    "application/pdf",
                    "image/jpeg",
                    "image/png",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Uri initialUri = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        ? MediaStore.Downloads.EXTERNAL_CONTENT_URI
                        : DocumentsContract.buildRootUri("com.android.externalstorage.documents", "root");
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
            }
            filePickerLauncher.launch(intent);
        });

        // Load diverse sample document (random across 5 diverse domains)
        btnSampleFile.setOnClickListener(v -> {
            try {
                FileAndAiHelper.SampleResult sample = FileAndAiHelper.createRandomSampleDocx(this);
                selectedFileUri = sample.uri;

                tvSelectedFile.setText(sample.fileName);
                tvFileSubtitle.setText("Ngành: " + sample.domain + " • " + sample.title);

                tvFileBadge.setText("Mẫu thử");
                tvFileBadge.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.emerald_light));
                tvFileBadge.setTextColor(ContextCompat.getColor(this, R.color.emerald_dark));

                // Always replace guide box with full preview content
                cardWelcomeGuide.setVisibility(View.GONE);
                cardPreviewContainer.setVisibility(View.VISIBLE);
                tvPreviewContent.setText(sample.fullContent);
                tvPreviewCharCount.setText(sample.fullContent.length() + " ký tự");

                btnProcess.setEnabled(true);
                Toast.makeText(this, "Đã nạp: " + sample.title, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Không thể tạo file mẫu: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        // Analyze Document CTA -> Navigates to SummaryActivity
        btnProcess.setOnClickListener(v -> processDocument());
    }

    private void applyDeviceFile(Uri uri) {
        selectedFileUri = uri;
        FileAndAiHelper.FileInfo info = FileAndAiHelper.getFileInfo(this, uri);
        tvSelectedFile.setText(info.name);
        String sizeFormatted = (info.size > 0) ? (info.size / 1024) + " KB" : "< 1 KB";
        tvFileSubtitle.setText("Tệp từ máy • " + sizeFormatted);

        tvFileBadge.setText("Tệp máy");
        tvFileBadge.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary_container));
        tvFileBadge.setTextColor(ContextCompat.getColor(this, R.color.primary));

        // Always replace guide box with full preview content
        String previewText = FileAndAiHelper.extractPreviewText(this, uri);
        cardWelcomeGuide.setVisibility(View.GONE);
        cardPreviewContainer.setVisibility(View.VISIBLE);
        tvPreviewContent.setText(previewText);
        tvPreviewCharCount.setText(previewText.length() + " ký tự");

        btnProcess.setEnabled(true);
    }

    private void processDocument() {
        if (selectedFileUri == null) {
            Toast.makeText(this, "Vui lòng chọn hoặc nạp một tài liệu trước", Toast.LENGTH_SHORT).show();
            return;
        }

        String apiKey = FileAndAiHelper.getEffectiveApiKey(this);
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Chưa tìm thấy Gemini API Key trong cấu hình hệ thống.", Toast.LENGTH_LONG).show();
            return;
        }

        setLoadingState(true, "Gemini đang đọc và phân tích tài liệu...");

        executor.execute(() -> {
            try {
                DocumentRecord record = FileAndAiHelper.analyzeDocumentWithGemini(
                        MainActivity.this,
                        selectedFileUri,
                        apiKey
                );

                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setLoadingState(false, null);
                    // Navigate to SummaryActivity
                    Intent intent = new Intent(MainActivity.this, SummaryActivity.class);
                    intent.putExtra("document_record", record);
                    startActivity(intent);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setLoadingState(false, null);
                    handleProcessingError(e);
                });
            }
        });
    }

    private void setLoadingState(boolean loading, String statusText) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        tvStatus.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (statusText != null) tvStatus.setText(statusText);
        btnProcess.setEnabled(!loading);
        btnSelectFile.setEnabled(!loading);
        btnSampleFile.setEnabled(!loading);
    }

    private void handleProcessingError(Exception e) {
        String msg;
        if (e instanceof UnknownHostException) {
            msg = "Không có kết nối mạng. Vui lòng kiểm tra WiFi hoặc dữ liệu di động.";
        } else if (e instanceof SocketTimeoutException) {
            msg = "Quá thời gian kết nối (Timeout). Tài liệu quá dài hoặc mạng yếu.";
        } else {
            msg = e.getMessage() != null ? e.getMessage() : "Đã xảy ra lỗi không xác định.";
        }

        new AlertDialog.Builder(this)
                .setTitle("Phân tích tài liệu thất bại")
                .setMessage(msg)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void updateThemeIcon() {
        boolean isDark = ThemeHelper.isDarkMode(this);
        btnToggleTheme.setIconResource(isDark ? R.drawable.ic_light_mode : R.drawable.ic_dark_mode);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedFileUri != null) {
            outState.putParcelable("saved_uri", selectedFileUri);
        }
        outState.putString("saved_name", tvSelectedFile.getText().toString());
        outState.putString("saved_subtitle", tvFileSubtitle.getText().toString());
        outState.putString("saved_badge", tvFileBadge.getText().toString());
        outState.putString("saved_preview", tvPreviewContent.getText().toString());
        outState.putBoolean("saved_preview_visible", cardPreviewContainer.getVisibility() == View.VISIBLE);
        outState.putBoolean("saved_process_enabled", btnProcess.isEnabled());
    }

    private void restoreInstanceState(@NonNull Bundle savedInstanceState) {
        selectedFileUri = BundleCompat.getParcelable(savedInstanceState, "saved_uri", Uri.class);
        boolean isVisible = savedInstanceState.getBoolean("saved_preview_visible", false);
        String preview = savedInstanceState.getString("saved_preview");
        if (isVisible && preview != null && !preview.isEmpty()) {
            tvSelectedFile.setText(savedInstanceState.getString("saved_name"));
            tvFileSubtitle.setText(savedInstanceState.getString("saved_subtitle"));
            String badge = savedInstanceState.getString("saved_badge", "");
            tvFileBadge.setText(badge);
            if ("Mẫu thử".equals(badge)) {
                tvFileBadge.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.emerald_light));
                tvFileBadge.setTextColor(ContextCompat.getColor(this, R.color.emerald_dark));
            } else if ("Tệp máy".equals(badge)) {
                tvFileBadge.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary_container));
                tvFileBadge.setTextColor(ContextCompat.getColor(this, R.color.primary));
            }
            tvPreviewContent.setText(preview);
            tvPreviewCharCount.setText(preview.length() + " ký tự");

            cardPreviewContainer.setVisibility(View.VISIBLE);
            cardWelcomeGuide.setVisibility(View.GONE);
            btnProcess.setEnabled(savedInstanceState.getBoolean("saved_process_enabled", true));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
