package com.bnpp.releasenotes.service;

import com.bnpp.releasenotes.model.LlmSlideResponse;
import com.bnpp.releasenotes.model.LlmSlideResponse.SlideData;
import com.bnpp.releasenotes.model.SlideRow;
import org.apache.poi.sl.usermodel.TableCell.BorderEdge;
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.util.Units;
import org.apache.poi.xslf.usermodel.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Generates a .pptx file from LlmSlideResponse using Apache POI (XSLF).
 *
 * ── Table Schema ──────────────────────────────────────────────────────────────
 * Col  | Header               | Width  | Source
 * ─────┼──────────────────────┼────────┼────────────────────────────────
 *  0   | Sr                   |  4%    | SlideRow.sr
 *  1   | JIRA No              |  9%    | SlideRow.jiraNo
 *  2   | JIRA Description     | 17%    | SlideRow.jiraDescription
 *  3   | Change Type          |  9%    | SlideRow.changeType
 *  4   | Why Change Required  | 34%    | SlideRow.whyChangeRequired
 *  5   | Requirement Type     | 12%    | SlideRow.requirementType
 *  6   | Signoff By           | 15%    | SlideRow.signoffBy (= "Chetan")
 *
 * ── Master Slide ──────────────────────────────────────────────────────────────
 * If masterSlidePath is set, the app loads the .pptx, removes its own slides,
 * and uses its "Blank" layout as background for each generated slide.
 * This preserves logos, brand colours, and background images.
 */
public class PptGenerationService {

    // ── Column config ──────────────────────────────────────────────────────────
    private static final String[] HEADERS = {
        "Sr", "JIRA No", "JIRA Description", "Change Type",
        "Why Change Required", "Requirement Type", "Signoff By"
    };

    // Proportions — must sum to exactly 1.0
    private static final double[] COL_RATIOS = {
        0.04,  // Sr
        0.09,  // JIRA No
        0.17,  // JIRA Description
        0.09,  // Change Type
        0.34,  // Why Change Required
        0.12,  // Requirement Type
        0.15   // Signoff By
    };

    // ── Slide geometry (inches, widescreen 13.33 x 7.5) ───────────────────────
    private static final double SLIDE_W      = 13.33;
    private static final double SLIDE_H      = 7.5;
    private static final double MARGIN_X     = 0.40;
    private static final double TITLE_Y      = 0.20;
    private static final double TITLE_H      = 0.65;
    private static final double TABLE_Y      = TITLE_Y + TITLE_H + 0.15;
    private static final double TABLE_W      = SLIDE_W - MARGIN_X * 2;
    private static final double HEADER_ROW_H = 0.40;
    private static final double DATA_ROW_H   = 0.80;   // generous height for wrapped text

    // ── Colour palette ─────────────────────────────────────────────────────────
    private static final Color C_HEADER_BG      = new Color(31,  73, 125);  // BNP navy
    private static final Color C_HEADER_FG      = Color.WHITE;
    private static final Color C_ROW_ODD        = new Color(214, 227, 243); // pale blue
    private static final Color C_ROW_EVEN       = new Color(242, 246, 252); // near-white
    private static final Color C_BORDER         = new Color(166, 185, 210);
    private static final Color C_TITLE          = new Color(31,  73, 125);
    private static final Color C_TEXT_DARK      = new Color(25,  25,  25);

    // Status colours for Change Type cell
    private static final Color C_FUNCTIONAL     = new Color(0,  112,   0);  // green
    private static final Color C_BUG            = new Color(192,  0,   0);  // red
    private static final Color C_TECHNICAL      = new Color(31,  73, 125);  // navy

    // ── Public ─────────────────────────────────────────────────────────────────

    public void generate(LlmSlideResponse response, String masterSlidePath, String outputPath)
            throws IOException {

        XMLSlideShow ppt = buildPresentation(masterSlidePath);

        List<SlideData> slides = response.getSlides();
        for (SlideData slideData : slides) {
            XSLFSlide slide = createSlide(ppt, masterSlidePath);
            addTitle(slide, slideData.getTitle());
            addTable(slide, slideData.getRows());
        }

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            ppt.write(fos);
        }
        ppt.close();
    }

    // ── Presentation Setup ─────────────────────────────────────────────────────

    private XMLSlideShow buildPresentation(String masterPath) throws IOException {
        XMLSlideShow ppt;
        if (hasMaster(masterPath)) {
            try (FileInputStream fis = new FileInputStream(masterPath)) {
                ppt = new XMLSlideShow(fis);
            }
            while (!ppt.getSlides().isEmpty()) {
                ppt.removeSlide(0);
            }
        } else {
            ppt = new XMLSlideShow();
        }
        // Widescreen 16:9
        ppt.setPageSize(new Dimension((int) emu(SLIDE_W), (int) emu(SLIDE_H)));
        return ppt;
    }

    private XSLFSlide createSlide(XMLSlideShow ppt, String masterPath) {
        if (hasMaster(masterPath) && !ppt.getSlideLayouts().isEmpty()) {
            XSLFSlideLayout layout = findBlankLayout(ppt);
            XSLFSlide slide = ppt.createSlide(layout);
            // Remove editable placeholders — keep only master background
            slide.getShapes().stream()
                 .filter(s -> s instanceof XSLFTextShape)
                 .toList()
                 .forEach(slide::removeShape);
            return slide;
        }
        return ppt.createSlide();
    }

    private XSLFSlideLayout findBlankLayout(XMLSlideShow ppt) {
        for (XSLFSlideMaster master : ppt.getSlideMasters()) {
            for (XSLFSlideLayout layout : master.getSlideLayouts()) {
                if (layout.getName() != null
                        && layout.getName().toLowerCase().contains("blank")) {
                    return layout;
                }
            }
        }
        return ppt.getSlideLayouts().get(0);
    }

    // ── Title ──────────────────────────────────────────────────────────────────

    private void addTitle(XSLFSlide slide, String titleText) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(rect(MARGIN_X, TITLE_Y, TABLE_W, TITLE_H));

        XSLFTextParagraph para = box.addNewTextParagraph();
        para.setTextAlign(TextAlign.LEFT);

        XSLFTextRun run = para.addNewTextRun();
        run.setText(titleText);
        run.setFontSize(18.0);
        run.setBold(true);
        run.setFontColor(C_TITLE);
        run.setFontFamily("Calibri");
    }

    // ── Table ──────────────────────────────────────────────────────────────────

    private void addTable(XSLFSlide slide, List<SlideRow> rows) {
        int numData   = rows != null ? rows.size() : 0;
        int totalRows = 1 + numData;

        XSLFTable table = slide.createTable(totalRows, HEADERS.length);

        // Column widths
        for (int c = 0; c < HEADERS.length; c++) {
            table.setColumnWidth(c, emu(TABLE_W * COL_RATIOS[c]));
        }

        // Row heights
        table.getRows().get(0).setHeight(emu(HEADER_ROW_H));
        for (int r = 1; r < totalRows; r++) {
            table.getRows().get(r).setHeight(emu(DATA_ROW_H));
        }

        // Table anchor
        double tableH = HEADER_ROW_H + numData * DATA_ROW_H;
        table.setAnchor(rect(MARGIN_X, TABLE_Y, TABLE_W, tableH));

        // Header row
        for (int c = 0; c < HEADERS.length; c++) {
            styleHeader(table.getCell(0, c), HEADERS[c]);
        }

        // Data rows
        if (rows != null) {
            for (int r = 0; r < rows.size(); r++) {
                SlideRow row   = rows.get(r);
                Color    rowBg = (r % 2 == 0) ? C_ROW_ODD : C_ROW_EVEN;

                styleData(table.getCell(r + 1, 0), String.valueOf(row.getSr()),          rowBg, TextAlign.CENTER, false);
                styleData(table.getCell(r + 1, 1), row.getJiraNo(),                       rowBg, TextAlign.LEFT,   false);
                styleData(table.getCell(r + 1, 2), row.getJiraDescription(),              rowBg, TextAlign.LEFT,   false);
                styleData(table.getCell(r + 1, 3), row.getChangeType(),                   rowBg, TextAlign.CENTER, true);
                styleData(table.getCell(r + 1, 4), row.getWhyChangeRequired(),             rowBg, TextAlign.LEFT,   false);
                styleData(table.getCell(r + 1, 5), row.getRequirementType(),              rowBg, TextAlign.CENTER, false);
                styleData(table.getCell(r + 1, 6), row.getSignoffBy(),                    rowBg, TextAlign.CENTER, false);
            }
        }
    }

    // ── Cell Styling ───────────────────────────────────────────────────────────

    private void styleHeader(XSLFTableCell cell, String text) {
        cell.setFillColor(C_HEADER_BG);
        applyBorders(cell);
        cell.setVerticalAlignment(VerticalAlignment.MIDDLE);
        cell.setLeftInset(0.05);
        cell.setRightInset(0.05);
        cell.setTopInset(0.04);
        cell.setBottomInset(0.04);

        XSLFTextParagraph para = firstPara(cell);
        para.setTextAlign(TextAlign.CENTER);

        XSLFTextRun run = firstRun(para, text);
        run.setFontSize(10.0);
        run.setBold(true);
        run.setFontColor(C_HEADER_FG);
        run.setFontFamily("Calibri");
    }

    private void styleData(XSLFTableCell cell, String text,
                            Color bg, TextAlign align, boolean isChangeType) {
        cell.setFillColor(bg);
        applyBorders(cell);
        cell.setVerticalAlignment(VerticalAlignment.TOP);
        cell.setLeftInset(0.06);
        cell.setRightInset(0.06);
        cell.setTopInset(0.05);
        cell.setBottomInset(0.04);

        XSLFTextParagraph para = firstPara(cell);
        para.setTextAlign(align);
        para.setLineSpacing(110.0);

        XSLFTextRun run = firstRun(para, text);
        run.setFontSize(9.0);
        run.setFontFamily("Calibri");

        if (isChangeType) {
            run.setBold(true);
            run.setFontColor(changeTypeColor(text));
        } else {
            run.setFontColor(C_TEXT_DARK);
        }
    }

    private void applyBorders(XSLFTableCell cell) {
        for (BorderEdge edge : BorderEdge.values()) {
            cell.setBorderColor(edge, C_BORDER);
            cell.setBorderWidth(edge, 0.75);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Color changeTypeColor(String changeType) {
        if (changeType == null) return C_TECHNICAL;
        return switch (changeType.trim().toLowerCase()) {
            case "functional" -> C_FUNCTIONAL;
            case "bug"        -> C_BUG;
            default           -> C_TECHNICAL;
        };
    }

    private XSLFTextParagraph firstPara(XSLFTableCell cell) {
        List<XSLFTextParagraph> paras = cell.getTextParagraphs();
        return paras.isEmpty() ? cell.addNewTextParagraph() : paras.get(0);
    }

    private XSLFTextRun firstRun(XSLFTextParagraph para, String text) {
        List<XSLFTextRun> runs = para.getTextRuns();
        XSLFTextRun run = runs.isEmpty() ? para.addNewTextRun() : runs.get(0);
        run.setText(text != null ? text : "");
        return run;
    }

    private boolean hasMaster(String path) {
        return path != null && !path.isBlank() && new File(path).exists();
    }

    private Rectangle2D.Double rect(double x, double y, double w, double h) {
        return new Rectangle2D.Double(emu(x), emu(y), emu(w), emu(h));
    }

    private double emu(double inches) {
        return Units.toEMU(inches);
    }
}
