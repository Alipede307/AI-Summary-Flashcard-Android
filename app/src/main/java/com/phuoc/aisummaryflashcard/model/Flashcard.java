package com.phuoc.aisummaryflashcard.model;

import java.io.Serializable;

public class Flashcard implements Serializable {
    private final String question;
    private final String answer;

    public Flashcard(String question, String answer) {
        this.question = question;
        this.answer = answer;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }
}
