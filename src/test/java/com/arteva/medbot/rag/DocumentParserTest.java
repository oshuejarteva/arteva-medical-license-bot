package com.arteva.medbot.rag;

import dev.langchain4j.data.document.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentParserTest {

    private DocumentParser parser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new DocumentParser();
    }

    @Test
    void parseAll_withDocxFile_shouldExtractText() throws IOException {
        // given
        createDocxFile("test.docx", "Содержимое первого параграфа", "Содержимое второго параграфа");

        // when
        List<Document> documents = parser.parseAll(tempDir.toString());

        // then
        assertEquals(1, documents.size());
        Document doc = documents.get(0);
        assertTrue(doc.text().contains("Содержимое первого параграфа"));
        assertTrue(doc.text().contains("Содержимое второго параграфа"));
        assertEquals("test.docx", doc.metadata().getString("source"));
    }

    @Test
    void parseAll_withMultipleDocxFiles_shouldParseAll() throws IOException {
        // given
        createDocxFile("doc1.docx", "Текст документа один");
        createDocxFile("doc2.docx", "Текст документа два");

        // when
        List<Document> documents = parser.parseAll(tempDir.toString());

        // then
        assertEquals(2, documents.size());
    }

    @Test
    void parseAll_withDocxContainingTable_shouldExtractTableContent() throws IOException {
        // given
        createDocxWithTable("table.docx",
                new String[][]{
                        {"Колонка1", "Колонка2"},
                        {"Значение1", "Значение2"}
                });

        // when
        List<Document> documents = parser.parseAll(tempDir.toString());

        // then
        assertEquals(1, documents.size());
        String text = documents.get(0).text();
        assertTrue(text.contains("Колонка1"));
        assertTrue(text.contains("Колонка2"));
        assertTrue(text.contains("Значение1"));
        assertTrue(text.contains("Значение2"));
    }

    @Test
    void parseAll_withEmptyDirectory_shouldReturnEmptyList() {
        // when
        List<Document> documents = parser.parseAll(tempDir.toString());

        // then
        assertTrue(documents.isEmpty());
    }

    @Test
    void parseAll_withNonExistentDirectory_shouldReturnEmptyList() {
        // when
        List<Document> documents = parser.parseAll("/nonexistent/path");

        // then
        assertTrue(documents.isEmpty());
    }

    @Test
    void parseAll_withNonDocFiles_shouldIgnoreThem() throws IOException {
        // given: create a .txt file
        File txtFile = tempDir.resolve("readme.txt").toFile();
        try (FileOutputStream fos = new FileOutputStream(txtFile)) {
            fos.write("This is a text file".getBytes());
        }

        // when
        List<Document> documents = parser.parseAll(tempDir.toString());

        // then
        assertTrue(documents.isEmpty());
    }

    @Test
    void parseAll_withEmptyDocxFile_shouldSkipIt() throws IOException {
        // given: create docx with no content
        XWPFDocument doc = new XWPFDocument();
        File file = tempDir.resolve("empty.docx").toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            doc.write(fos);
        }
        doc.close();

        // when
        List<Document> documents = parser.parseAll(tempDir.toString());

        // then
        assertTrue(documents.isEmpty());
    }

    @Test
    void parseAll_setSourceMetadata() throws IOException {
        // given
        createDocxFile("medical-license.docx", "Медицинская лицензия");

        // when
        List<Document> documents = parser.parseAll(tempDir.toString());

        // then
        assertEquals(1, documents.size());
        assertEquals("medical-license.docx", documents.get(0).metadata().getString("source"));
    }

    @Test
    void parseAll_withMixedCaseExtension_shouldParse() throws IOException {
        // given
        createDocxFile("UPPERCASE.DOCX", "Верхний регистр");

        // when
        List<Document> documents = parser.parseAll(tempDir.toString());

        // then
        assertEquals(1, documents.size());
        assertTrue(documents.get(0).text().contains("Верхний регистр"));
    }

    // --- helpers ---

    private void createDocxFile(String fileName, String... paragraphs) throws IOException {
        XWPFDocument doc = new XWPFDocument();
        for (String text : paragraphs) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText(text);
        }
        File file = tempDir.resolve(fileName).toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            doc.write(fos);
        }
        doc.close();
    }

    private void createDocxWithTable(String fileName, String[][] rows) throws IOException {
        XWPFDocument doc = new XWPFDocument();
        XWPFTable table = doc.createTable(rows.length, rows[0].length);
        for (int i = 0; i < rows.length; i++) {
            XWPFTableRow row = table.getRow(i);
            for (int j = 0; j < rows[i].length; j++) {
                row.getCell(j).setText(rows[i][j]);
            }
        }
        File file = tempDir.resolve(fileName).toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            doc.write(fos);
        }
        doc.close();
    }
}
