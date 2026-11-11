package com.phuoc.aisummaryflashcard.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DocumentRecord implements Serializable {
    private long id;
    private final String fileName;
    private final String summary;
    private final String createdAt;
    private List<Flashcard> flashcards = new ArrayList<>();

    public DocumentRecord(String fileName, String summary, String createdAt) {
        this(0, fileName, summary, createdAt);
    }

    public DocumentRecord(long id, String fileName, String summary, String createdAt) {
        this.id = id;
        this.fileName = fileName;
        this.summary = summary;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getSummary() {
        return summary;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public List<Flashcard> getFlashcards() {
        return flashcards;
    }

    public void setFlashcards(List<Flashcard> flashcards) {
        this.flashcards = flashcards;
    }
}
