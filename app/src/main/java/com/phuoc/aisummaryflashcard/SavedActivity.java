package com.phuoc.aisummaryflashcard;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.phuoc.aisummaryflashcard.db.DatabaseHelper;
import com.phuoc.aisummaryflashcard.helper.ThemeHelper;
import com.phuoc.aisummaryflashcard.model.DocumentRecord;
import com.phuoc.aisummaryflashcard.model.Flashcard;

import java.util.ArrayList;
import java.util.List;

public class SavedActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private final List<DocumentRecord> savedDocs = new ArrayList<>();
    private SavedDocsAdapter adapter;

    private ImageView ivLogo;
    private TextView tvSavedCount;
    private MaterialButton btnToggleTheme;
    private LinearLayout layoutEmptyState;
    private ListView lvSavedDocs;
    private MaterialButton btnEmptyGoHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved);

        dbHelper = new DatabaseHelper(this);

        initViews();
        setupEvents();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSavedDocuments();
    }

    private void initViews() {
        ivLogo = findViewById(R.id.ivLogo);
        tvSavedCount = findViewById(R.id.tvSavedCount);
        btnToggleTheme = findViewById(R.id.btnToggleTheme);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        lvSavedDocs = findViewById(R.id.lvSavedDocs);
        btnEmptyGoHome = findViewById(R.id.btnEmptyGoHome);

        adapter = new SavedDocsAdapter(this, savedDocs);
        lvSavedDocs.setAdapter(adapter);

        updateThemeIcon();
    }

    private void setupEvents() {
        ivLogo.setOnClickListener(v -> navigateHome());

        btnToggleTheme.setOnClickListener(v -> ThemeHelper.toggleTheme(this));

        btnEmptyGoHome.setOnClickListener(v -> navigateHome());
    }

    private void loadSavedDocuments() {
        savedDocs.clear();
        savedDocs.addAll(dbHelper.getAllDocuments());
        adapter.notifyDataSetChanged();

        if (savedDocs.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            lvSavedDocs.setVisibility(View.GONE);
            tvSavedCount.setText("0 tài liệu lưu trữ");
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            lvSavedDocs.setVisibility(View.VISIBLE);
            tvSavedCount.setText(savedDocs.size() + " tài liệu học tập ngoại tuyến");
        }
    }

    private void openDocument(DocumentRecord doc) {
        List<Flashcard> cards = dbHelper.getFlashcardsForDocument(doc.getId());
        doc.setFlashcards(cards);

        Intent intent = new Intent(SavedActivity.this, SummaryActivity.class);
        intent.putExtra("document_record", doc);
        startActivity(intent);
    }

    private void confirmDeleteDocument(DocumentRecord doc) {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa vĩnh viễn tài liệu '" + doc.getFileName() + "' khỏi máy?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    dbHelper.deleteDocument(doc.getId());
                    Toast.makeText(this, "Đã xóa tài liệu.", Toast.LENGTH_SHORT).show();
                    loadSavedDocuments();
                })
                .setNegativeButton("Hủy", null)
                .show();
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

    private static class ViewHolder {
        final TextView tvTitle;
        final TextView tvSubtitle;
        final MaterialButton btnOpen;
        final MaterialButton btnDelete;

        ViewHolder(View root) {
            tvTitle = root.findViewById(R.id.tvSavedItemTitle);
            tvSubtitle = root.findViewById(R.id.tvSavedItemSubtitle);
            btnOpen = root.findViewById(R.id.btnSavedItemOpen);
            btnDelete = root.findViewById(R.id.btnSavedItemDelete);
        }
    }

    private class SavedDocsAdapter extends ArrayAdapter<DocumentRecord> {

        public SavedDocsAdapter(@NonNull Context context, @NonNull List<DocumentRecord> objects) {
            super(context, 0, objects);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_saved_document, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            DocumentRecord doc = getItem(position);
            if (doc != null) {
                holder.tvTitle.setText(doc.getFileName());
                holder.tvSubtitle.setText(doc.getCreatedAt() + " • Bản lưu ngoại tuyến");

                View.OnClickListener openListener = v -> openDocument(doc);
                convertView.setOnClickListener(openListener);
                holder.btnOpen.setOnClickListener(openListener);
                holder.btnDelete.setOnClickListener(v -> confirmDeleteDocument(doc));
            }

            return convertView;
        }
    }
}

