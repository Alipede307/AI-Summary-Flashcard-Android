package com.phuoc.aisummaryflashcard.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.phuoc.aisummaryflashcard.model.DocumentRecord;
import com.phuoc.aisummaryflashcard.model.Flashcard;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "study_assistant.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_DOCUMENTS = "documents";
    private static final String COL_DOC_ID = "id";
    private static final String COL_DOC_FILE_NAME = "file_name";
    private static final String COL_DOC_SUMMARY = "summary";
    private static final String COL_DOC_CREATED_AT = "created_at";

    private static final String TABLE_FLASHCARDS = "flashcards";
    private static final String COL_CARD_ID = "id";
    private static final String COL_CARD_DOC_ID = "document_id";
    private static final String COL_CARD_QUESTION = "question";
    private static final String COL_CARD_ANSWER = "answer";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_DOCUMENTS + " (" +
                COL_DOC_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_DOC_FILE_NAME + " TEXT NOT NULL, " +
                COL_DOC_SUMMARY + " TEXT, " +
                COL_DOC_CREATED_AT + " TEXT);");

        db.execSQL("CREATE TABLE " + TABLE_FLASHCARDS + " (" +
                COL_CARD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_CARD_DOC_ID + " INTEGER NOT NULL, " +
                COL_CARD_QUESTION + " TEXT NOT NULL, " +
                COL_CARD_ANSWER + " TEXT NOT NULL, " +
                "FOREIGN KEY (" + COL_CARD_DOC_ID + ") REFERENCES " + TABLE_DOCUMENTS + "(" + COL_DOC_ID + ") ON DELETE CASCADE);");

        db.execSQL("CREATE INDEX idx_flashcards_doc_id ON " + TABLE_FLASHCARDS + "(" + COL_CARD_DOC_ID + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FLASHCARDS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DOCUMENTS);
        onCreate(db);
    }

    public long insertDocumentWithCards(DocumentRecord doc) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        long docId = -1;
        try {
            ContentValues docValues = new ContentValues();
            docValues.put(COL_DOC_FILE_NAME, doc.getFileName());
            docValues.put(COL_DOC_SUMMARY, doc.getSummary());
            docValues.put(COL_DOC_CREATED_AT, doc.getCreatedAt());

            docId = db.insert(TABLE_DOCUMENTS, null, docValues);
            if (docId != -1 && doc.getFlashcards() != null) {
                for (Flashcard card : doc.getFlashcards()) {
                    ContentValues cardValues = new ContentValues();
                    cardValues.put(COL_CARD_DOC_ID, docId);
                    cardValues.put(COL_CARD_QUESTION, card.getQuestion());
                    cardValues.put(COL_CARD_ANSWER, card.getAnswer());
                    db.insert(TABLE_FLASHCARDS, null, cardValues);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return docId;
    }

    public List<DocumentRecord> getAllDocuments() {
        List<DocumentRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_DOCUMENTS, null, null, null, null, null, COL_DOC_ID + " DESC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DOC_ID));
                    String fileName = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_FILE_NAME));
                    String summary = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_SUMMARY));
                    String createdAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_DOC_CREATED_AT));
                    list.add(new DocumentRecord(id, fileName, summary, createdAt));
                }
            }
        }
        return list;
    }

    public List<Flashcard> getFlashcardsForDocument(long documentId) {
        List<Flashcard> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_FLASHCARDS, null, COL_CARD_DOC_ID + "=?",
                new String[]{String.valueOf(documentId)}, null, null, COL_CARD_ID + " ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String q = cursor.getString(cursor.getColumnIndexOrThrow(COL_CARD_QUESTION));
                    String a = cursor.getString(cursor.getColumnIndexOrThrow(COL_CARD_ANSWER));
                    list.add(new Flashcard(q, a));
                }
            }
        }
        return list;
    }

    public boolean deleteDocument(long documentId) {
        SQLiteDatabase db = getWritableDatabase();
        int affected = db.delete(TABLE_DOCUMENTS, COL_DOC_ID + "=?", new String[]{String.valueOf(documentId)});
        return affected > 0;
    }
}
