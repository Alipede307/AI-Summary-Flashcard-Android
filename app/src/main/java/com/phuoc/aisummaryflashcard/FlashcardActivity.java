package com.phuoc.aisummaryflashcard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.phuoc.aisummaryflashcard.helper.ThemeHelper;
import com.phuoc.aisummaryflashcard.model.DocumentRecord;
import com.phuoc.aisummaryflashcard.model.Flashcard;

import java.util.List;

public class FlashcardActivity extends AppCompatActivity {

    private DocumentRecord currentRecord;
    private List<Flashcard> flashcards;
    private int currentCardIndex = 0;
    private boolean isShowingFront = true;
    private boolean isFlipping = false;

    private ImageView ivLogo;
    private TextView tvHeaderSubtitle, tvCardProgress, tvCardSideTag, tvCardText;
    private MaterialCardView cardFlashcard;
    private MaterialButton btnToggleTheme, btnSaved, btnPrevCard, btnNextCard, btnBackToSummary, btnBackHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard);

        currentRecord = IntentCompat.getSerializableExtra(getIntent(), "document_record", DocumentRecord.class);
        if (currentRecord == null || currentRecord.getFlashcards() == null || currentRecord.getFlashcards().isEmpty()) {
            Toast.makeText(this, "Không tìm thấy bộ thẻ Flashcard.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        flashcards = currentRecord.getFlashcards();

        if (savedInstanceState != null) {
            currentCardIndex = Math.max(0, Math.min(savedInstanceState.getInt("current_card_index", 0), flashcards.size() - 1));
            isShowingFront = savedInstanceState.getBoolean("is_showing_front", true);
        }

        initViews();
        displayCurrentCard();
        setupEvents();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current_card_index", currentCardIndex);
        outState.putBoolean("is_showing_front", isShowingFront);
    }

    private void initViews() {
        ivLogo = findViewById(R.id.ivLogo);
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);
        tvCardProgress = findViewById(R.id.tvCardProgress);
        tvCardSideTag = findViewById(R.id.tvCardSideTag);
        tvCardText = findViewById(R.id.tvCardText);

        cardFlashcard = findViewById(R.id.cardFlashcard);
        btnToggleTheme = findViewById(R.id.btnToggleTheme);
        btnSaved = findViewById(R.id.btnSaved);
        btnPrevCard = findViewById(R.id.btnPrevCard);
        btnNextCard = findViewById(R.id.btnNextCard);
        btnBackToSummary = findViewById(R.id.btnBackToSummary);
        btnBackHome = findViewById(R.id.btnBackHome);

        tvHeaderSubtitle.setText(currentRecord.getFileName());

        // Adjust 3D perspective to avoid ballooning over surrounding views mid-flip
        cardFlashcard.setCameraDistance(8000 * getResources().getDisplayMetrics().density);

        updateThemeIcon();
    }

    private void setupEvents() {
        // Logo always navigates home
        ivLogo.setOnClickListener(v -> navigateHome());

        btnToggleTheme.setOnClickListener(v -> ThemeHelper.toggleTheme(this));

        btnSaved.setOnClickListener(v -> startActivity(new Intent(FlashcardActivity.this, SavedActivity.class)));

        cardFlashcard.setOnClickListener(v -> flipCard());
        btnPrevCard.setOnClickListener(v -> stepCard(-1));
        btnNextCard.setOnClickListener(v -> stepCard(1));

        btnBackToSummary.setOnClickListener(v -> finish());
        btnBackHome.setOnClickListener(v -> navigateHome());
    }

    private void displayCurrentCard() {
        if (flashcards == null || flashcards.isEmpty()) return;

        Flashcard card = flashcards.get(currentCardIndex);
        tvCardProgress.setText("Thẻ " + (currentCardIndex + 1) + " / " + flashcards.size());

        if (isShowingFront) {
            tvCardSideTag.setText("MẶT TRƯỚC • CÂU HỎI");
            tvCardSideTag.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary_light));
            tvCardSideTag.setTextColor(ContextCompat.getColor(this, R.color.primary));
            tvCardText.setText(card.getQuestion());
        } else {
            tvCardSideTag.setText("MẶT SAU • ĐÁP ÁN");
            tvCardSideTag.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.emerald_light));
            tvCardSideTag.setTextColor(ContextCompat.getColor(this, R.color.emerald_dark));
            tvCardText.setText(card.getAnswer());
        }

        btnPrevCard.setEnabled(currentCardIndex > 0);
        btnNextCard.setEnabled(currentCardIndex < flashcards.size() - 1);
    }

    private void stepCard(int delta) {
        int nextIndex = currentCardIndex + delta;
        if (nextIndex >= 0 && nextIndex < flashcards.size()) {
            currentCardIndex = nextIndex;
            isShowingFront = true;
            cardFlashcard.setRotationY(0f);
            displayCurrentCard();
        }
    }

    private void flipCard() {
        if (isFlipping) return;
        isFlipping = true;

        cardFlashcard.animate()
                .rotationY(90f)
                .setDuration(160)
                .withEndAction(() -> {
                    isShowingFront = !isShowingFront;
                    displayCurrentCard();
                    cardFlashcard.setRotationY(-90f);
                    cardFlashcard.animate()
                            .rotationY(0f)
                            .setDuration(160)
                            .withEndAction(() -> isFlipping = false)
                            .start();
                })
                .start();
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
        if (cardFlashcard != null) cardFlashcard.animate().cancel();
    }
}

