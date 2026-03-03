package com.bnpp.releasenotes.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts strict CSV test-case rows into an Excel workbook.
 */
public class FunctionalTestCaseExcelService {

    private static final String[] REQUIRED_HEADERS = {
            "Test_Case_Number", "Test_Case_Scenario", "Input", "Output", "Description"
    };

    public void generateExcelFromCsv(String csvContent, String outputPath) throws IOException {
        List<List<String>> rows = parseCsv(csvContent);
        if (rows.isEmpty()) {
            throw new IOException("LLM returned empty test case output.");
        }

        List<String> header = rows.get(0);
        validateHeader(header);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Functional Test Cases");

            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(i);
                List<String> cols = rows.get(i);
                for (int c = 0; c < REQUIRED_HEADERS.length; c++) {
                    String value = c < cols.size() ? cols.get(c) : "";
                    row.createCell(c).setCellValue(value);
                }
            }

            for (int c = 0; c < REQUIRED_HEADERS.length; c++) {
                sheet.autoSizeColumn(c);
            }

            try (FileOutputStream out = new FileOutputStream(outputPath)) {
                workbook.write(out);
            }
        }
    }

    public String toMarkdownTable(String csvContent) throws IOException {
        List<List<String>> rows = parseCsv(csvContent);
        if (rows.isEmpty()) {
            throw new IOException("No rows found for markdown conversion.");
        }

        StringBuilder md = new StringBuilder();
        md.append("## 🧪 Generated UI Functional Test Cases\n\n");

        List<String> header = rows.get(0);
        md.append("| ").append(String.join(" | ", header)).append(" |\n");
        md.append("|");
        for (int i = 0; i < header.size(); i++) {
            md.append("---|");
        }
        md.append("\n");

        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            List<String> normalized = new ArrayList<>();
            for (int c = 0; c < header.size(); c++) {
                String value = c < row.size() ? row.get(c) : "";
                normalized.add(value.replace("|", "\\|"));
            }
            md.append("| ").append(String.join(" | ", normalized)).append(" |\n");
        }

        return md.toString();
    }

    private void validateHeader(List<String> header) throws IOException {
        if (header.size() < REQUIRED_HEADERS.length) {
            throw new IOException("Invalid CSV header: expected 5 columns.");
        }
        for (int i = 0; i < REQUIRED_HEADERS.length; i++) {
            if (!REQUIRED_HEADERS[i].equals(header.get(i).trim())) {
                throw new IOException("Invalid CSV header. Expected: " + String.join(", ", REQUIRED_HEADERS));
            }
        }
    }

    private List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return rows;
        }

        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);

            if (ch == '"') {
                if (inQuotes && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                row.add(cell.toString().trim());
                cell.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !inQuotes) {
                if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString().trim());
                cell.setLength(0);
                if (!row.isEmpty() && row.stream().anyMatch(v -> !v.isBlank())) {
                    rows.add(new ArrayList<>(row));
                }
                row.clear();
            } else {
                cell.append(ch);
            }
        }

        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString().trim());
            if (row.stream().anyMatch(v -> !v.isBlank())) {
                rows.add(row);
            }
        }

        return rows;
    }
}
