package com.phuoc.aisummaryflashcard.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.phuoc.aisummaryflashcard.model.DocumentRecord;

import org.junit.Test;

public class GeminiResponseTest {

    private static String wrap(String partsJson, String finishReason) {
        return "{\"candidates\":[{\"content\":{\"parts\":" + partsJson + "},\"finishReason\":\"" + finishReason + "\"}]}";
    }

    @Test
    public void parsesSummaryAndSkipsThoughtsAndEmptyCards() throws Exception {
        String inner = "{\\\"summary\\\":\\\"• Y chinh\\\",\\\"flashcards\\\":["
                + "{\\\"question\\\":\\\"Q1?\\\",\\\"answer\\\":\\\"A1\\\"},"
                + "{\\\"question\\\":\\\" \\\",\\\"answer\\\":\\\"bo qua\\\"}]}";
        String resp = wrap("[{\"text\":\"dang nghi\",\"thought\":true},{\"text\":\"" + inner + "\"}]", "STOP");

        DocumentRecord r = FileAndAiHelper.parseGeminiResponse("a.pdf", "01/11/2026 20:00", resp);

        assertEquals("• Y chinh", r.getSummary());
        assertEquals(1, r.getFlashcards().size());
        assertEquals("Q1?", r.getFlashcards().get(0).getQuestion());
    }

    @Test
    public void parsesTenPlusFlashcardsCorrectly() throws Exception {
        StringBuilder cards = new StringBuilder();
        for (int i = 1; i <= 12; i++) {
            if (i > 1) cards.append(",");
            cards.append("{\\\"question\\\":\\\"Q").append(i).append("?\\\",\\\"answer\\\":\\\"A").append(i).append("\\\"}");
        }
        String inner = "{\\\"summary\\\":\\\"• Y 1\\\\n• Y 2\\\",\\\"flashcards\\\":[" + cards + "]}";
        String resp = wrap("[{\"text\":\"" + inner + "\"}]", "STOP");

        DocumentRecord r = FileAndAiHelper.parseGeminiResponse("bai_giang.docx", "08/11/2026 11:00", resp);
        assertEquals(12, r.getFlashcards().size());
        assertEquals("Q1?", r.getFlashcards().get(0).getQuestion());
        assertEquals("A12", r.getFlashcards().get(11).getAnswer());
    }

    @Test
    public void truncatedAnswerGivesFriendlyError() throws Exception {
        assertErrorContains(wrap("[{\"text\":\"{\\\"summary\\\":\\\"cut\"}]", "MAX_TOKENS"), "quá dài");
    }

    @Test
    public void blockedPromptGivesReason() throws Exception {
        assertErrorContains("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", "SAFETY");
    }

    @Test
    public void mimeIsResolvedByTypeOrExtensionAndRejectsOthers() {
        assertEquals(FileAndAiHelper.MIME_DOCX, FileAndAiHelper.resolveSupportedMime("bai.DOCX", "application/octet-stream"));
        assertEquals(FileAndAiHelper.MIME_PDF, FileAndAiHelper.resolveSupportedMime("noext", "application/pdf"));
        assertEquals(FileAndAiHelper.MIME_JPEG, FileAndAiHelper.resolveSupportedMime("anh.jpeg", null));
        try {
            FileAndAiHelper.resolveSupportedMime("cu.doc", "application/msword");
            fail("old .doc must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains(".docx"));
        }
    }

    private static void assertErrorContains(String resp, String expected) throws Exception {
        try {
            FileAndAiHelper.parseGeminiResponse("a.pdf", "now", resp);
            fail("expected an error");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expected));
        }
    }
}
