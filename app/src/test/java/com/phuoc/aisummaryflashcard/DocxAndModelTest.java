package com.phuoc.aisummaryflashcard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.phuoc.aisummaryflashcard.helper.FileAndAiHelper;
import com.phuoc.aisummaryflashcard.model.DocumentRecord;
import com.phuoc.aisummaryflashcard.model.Flashcard;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DocxAndModelTest {

    @Test
    public void testModelDataBinding() {
        DocumentRecord doc = new DocumentRecord("test.pdf", "Day la ban tom tat", "01/11/2026 20:00");
        doc.getFlashcards().add(new Flashcard("Question 1?", "Answer 1"));
        doc.getFlashcards().add(new Flashcard("Question 2?", "Answer 2"));

        assertEquals("test.pdf", doc.getFileName());
        assertEquals(2, doc.getFlashcards().size());
        assertEquals("Question 1?", doc.getFlashcards().get(0).getQuestion());
        assertEquals("Answer 1", doc.getFlashcards().get(0).getAnswer());
    }

    @Test
    public void testDocxTextExtraction() throws Exception {
        // Create an in-memory sample docx zip structure with word/document.xml
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("word/document.xml");
            zos.putNextEntry(entry);
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                    "<w:body>" +
                    "<w:p><w:r><w:t>Hello Mobile</w:t><w:tab/><w:t>2026</w:t></w:r></w:p>" +
                    "<w:p><w:r><w:t>Midterm Project</w:t></w:r></w:p>" +
                    "</w:body>" +
                    "</w:document>";
            zos.write(xml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        String extracted = FileAndAiHelper.extractTextFromDocx(bais);

        assertTrue(extracted.contains("Hello Mobile\t2026"));
        assertTrue(extracted.contains("Midterm Project"));
    }

    @Test
    public void testDocxExternalEntityIsNotResolved() throws Exception {
        java.io.File secret = java.io.File.createTempFile("secret", ".txt");
        secret.deleteOnExit();
        java.nio.file.Files.write(secret.toPath(), "TOP_SECRET".getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            String xml = "<?xml version=\"1.0\"?>" +
                    "<!DOCTYPE w:document [<!ENTITY xxe SYSTEM \"" + secret.toURI() + "\">]>" +
                    "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                    "<w:body><w:p><w:r><w:t>A&xxe;B</w:t></w:r></w:p></w:body></w:document>";
            zos.write(xml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        String extracted = FileAndAiHelper.extractTextFromDocx(new ByteArrayInputStream(baos.toByteArray()));
        assertEquals("AB", extracted);
    }

    @Test
    public void testSampleDocxArchiveGenerationAndExtraction() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            FileAndAiHelper.writeSampleDocxArchive(zos);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        String extracted = FileAndAiHelper.extractTextFromDocx(bais);

        assertTrue(extracted.contains("TRÍ TUỆ NHÂN TẠO & HỌC MÁY CƠ BẢN"));
        assertTrue(extracted.contains("Supervised Learning"));
        assertTrue(extracted.contains("Transformer"));
    }

    @Test
    public void testAllFiveDiverseSampleTemplates() throws Exception {
        assertEquals(5, FileAndAiHelper.SAMPLE_TEMPLATES.length);

        for (FileAndAiHelper.SampleTemplate template : FileAndAiHelper.SAMPLE_TEMPLATES) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                FileAndAiHelper.writeDocxArchive(zos, template.paragraphs);
            }

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            String extracted = FileAndAiHelper.extractTextFromDocx(bais);

            assertTrue("Should contain first paragraph of " + template.title,
                    extracted.contains(template.paragraphs[0]));
            assertTrue("Full content should contain domain",
                    template.getFullContent().contains(template.domain));
        }
    }
}
