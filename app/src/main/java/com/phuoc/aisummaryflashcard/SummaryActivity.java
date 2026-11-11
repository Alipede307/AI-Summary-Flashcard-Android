package com.phuoc.aisummaryflashcard;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;

import com.google.android.material.button.MaterialButton;
import com.phuoc.aisummaryflashcard.db.DatabaseHelper;
import com.phuoc.aisummaryflashcard.helper.ThemeHelper;
import com.phuoc.aisummaryflashcard.model.DocumentRecord;

public class SummaryActivity extends AppCompatActivity {

    private DocumentRecord currentRecord;
    private DatabaseHelper dbHelper;
    private boolean isSavedOffline = false;

    private ImageView ivLogo;
    private TextView tvHeaderSubtitle, tvDocumentMetadata, tvSummaryContent;
    private MaterialButton btnToggleTheme, btnSaved, btnSave, btnGoFlashcards, btnBackHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        dbHelper = new DatabaseHelper(this);
        currentRecord = IntentCompat.getSerializableExtra(getIntent(), "document_record", DocumentRecord.class);

        if (currentRecord == null) {
            Toast.makeText(this, "Không tìm thấy dữ liệu tóm tắt.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (savedInstanceState != null) {
            long savedId = savedInstanceState.getLong("saved_record_id", 0);
            if (savedId > 0) currentRecord.setId(savedId);
            isSavedOffline = savedInstanceState.getBoolean("is_saved_offline", false);
        }

        initViews();
        bindData();
        setupEvents();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("is_saved_offline", isSavedOffline);
        if (currentRecord != null) {
            outState.putLong("saved_record_id", currentRecord.getId());
        }
    }

    private void initViews() {
        ivLogo = findViewById(R.id.ivLogo);
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);
        tvDocumentMetadata = findViewById(R.id.tvDocumentMetadata);
        tvSummaryContent = findViewById(R.id.tvSummaryContent);

        btnToggleTheme = findViewById(R.id.btnToggleTheme);
        btnSaved = findViewById(R.id.btnSaved);
        btnSave = findViewById(R.id.btnSave);
        btnGoFlashcards = findViewById(R.id.btnGoFlashcards);
        btnBackHome = findViewById(R.id.btnBackHome);

        updateThemeIcon();
    }

    private void bindData() {
        tvHeaderSubtitle.setText(currentRecord.getFileName());
        tvDocumentMetadata.setText("Tài liệu: " + currentRecord.getFileName() + " • " + currentRecord.getCreatedAt());
        tvSummaryContent.setText(currentRecord.getSummary());

        // Check if already saved in SQLite
        if (isSavedOffline || currentRecord.getId() > 0) {
            markAsSavedState();
        }
    }

    private void setupEvents() {
        // Logo always navigates home
        ivLogo.setOnClickListener(v -> navigateHome());

        btnToggleTheme.setOnClickListener(v -> ThemeHelper.toggleTheme(this));

        btnSaved.setOnClickListener(v -> startActivity(new Intent(SummaryActivity.this, SavedActivity.class)));

        btnSave.setOnClickListener(v -> saveRecordToDatabase());

        btnGoFlashcards.setOnClickListener(v -> {
            if (currentRecord.getFlashcards() == null || currentRecord.getFlashcards().isEmpty()) {
                Toast.makeText(this, "Tài liệu này không có thẻ Flashcard.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(SummaryActivity.this, FlashcardActivity.class);
            intent.putExtra("document_record", currentRecord);
            startActivity(intent);
        });

        btnBackHome.setOnClickListener(v -> navigateHome());
    }

    private void saveRecordToDatabase() {
        if (isSavedOffline || currentRecord.getId() > 0) {
            Toast.makeText(this, "Tài liệu này đã được lưu trong máy.", Toast.LENGTH_SHORT).show();
            return;
        }

        long id = dbHelper.insertDocumentWithCards(currentRecord);
        if (id != -1) {
            currentRecord.setId(id);
            markAsSavedState();
            Toast.makeText(this, "Đã lưu tài liệu vào máy thành công!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Không thể lưu vào cơ sở dữ liệu.", Toast.LENGTH_SHORT).show();
        }
    }

    private void markAsSavedState() {
        isSavedOffline = true;
        int emerald = ContextCompat.getColor(this, R.color.emerald);
        ColorStateList emeraldTint = ColorStateList.valueOf(emerald);
        btnSave.setText("Đã lưu ✓");
        btnSave.setTextColor(emerald);
        btnSave.setStrokeColor(emeraldTint);
        btnSave.setIconResource(R.drawable.ic_bookmark);
        btnSave.setIconTint(emeraldTint);
    }

    private void updateThemeIcon() {
        boolean isDark = ThemeHelper.isDarkMode(this);
        btnToggleTheme.setIconResource(isDark ? R.drawable.ic_light_mode : R.drawable.ic_dark_mode);
    }

    private void navigateHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) dbHelper.close();
    }
}
