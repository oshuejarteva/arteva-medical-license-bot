package com.arteva.medbot.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Парсер документов DOC и DOCX.
 * <p>
 * Читает файлы с локальной файловой системы с помощью Apache POI
 * и преобразует их в объекты {@link Document} (LangChain4j).
 * <p>
 * Поддерживаемые форматы:
 * <ul>
 *   <li><b>DOCX</b> (Office Open XML) — извлекает параграфы и таблицы</li>
 *   <li><b>DOC</b> (устаревший бинарный формат) — извлекает весь текст</li>
 * </ul>
 * <p>
 * Каждый документ сохраняет метаданные {@code source} с именем файла.
 */
@Component
public class DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocumentParser.class);

    /**
     * Парсит все DOC/DOCX-файлы из указанной директории.
     *
     * @param docsPath путь к папке с документами
     * @return список распарсенных документов с метаданными источника.
     *         Пустой список, если папка не существует или файлы не найдены.
     */
    public List<Document> parseAll(String docsPath) {
        List<Document> documents = new ArrayList<>();
        File docsDir = new File(docsPath);

        if (!docsDir.exists() || !docsDir.isDirectory()) {
            log.warn("Documents directory does not exist or is not a directory: {}", docsPath);
            return documents;
        }

        File[] files = docsDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".doc") || lower.endsWith(".docx");
        });

        if (files == null || files.length == 0) {
            log.warn("No DOC/DOCX files found in {}", docsPath);
            return documents;
        }

        for (File file : files) {
            try {
                String text = extractText(file);
                if (text != null && !text.isBlank()) {
                    Metadata metadata = Metadata.from("source", file.getName());
                    documents.add(Document.from(text, metadata));
                    log.info("Parsed document: {} ({} characters)", file.getName(), text.length());
                } else {
                    log.warn("Document is empty or unreadable: {}", file.getName());
                }
            } catch (Exception e) {
                log.error("Failed to parse document: {}", file.getName(), e);
            }
        }

        log.info("Total documents parsed: {}", documents.size());
        return documents;
    }

    private String extractText(File file) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".docx")) {
            return extractDocx(file);
        } else if (name.endsWith(".doc")) {
            return extractDoc(file);
        }
        return null;
    }

    /**
     * Извлекает текст из DOCX-файла (Office Open XML).
     * <p>
     * Извлекает как параграфы, так и содержимое таблиц
     * (ячейки разделяются {@code " | "}).
     */
    private String extractDocx(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder sb = new StringBuilder();

            // Extract paragraphs
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text.strip()).append("\n");
                }
            }

            // Extract tables
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.isBlank()) {
                            cells.add(cellText.strip());
                        }
                    }
                    if (!cells.isEmpty()) {
                        sb.append(String.join(" | ", cells)).append("\n");
                    }
                }
            }

            return sb.toString().strip();
        }
    }

    /**
     * Извлекает текст из DOC-файла (устаревший бинарный формат Microsoft Word).
     */
    private String extractDoc(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             HWPFDocument document = new HWPFDocument(fis)) {

            WordExtractor extractor = new WordExtractor(document);
            String text = extractor.getText();
            extractor.close();
            return text != null ? text.strip() : null;
        }
    }
}
