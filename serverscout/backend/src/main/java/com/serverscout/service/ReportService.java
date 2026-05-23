package com.serverscout.service;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import com.serverscout.util.ResourceNotFoundException;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ScanTaskRepository scanTaskRepository;
    private final AssetRepository assetRepository;
    private final PortRepository portRepository;
    private final AssetVulnerabilityRepository vulnRepository;

    private static final DateTimeFormatter DTF = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private static final DeviceRgb COLOR_PRIMARY = new DeviceRgb(37, 99, 235);
    private static final DeviceRgb COLOR_DARK = new DeviceRgb(30, 41, 59);
    private static final DeviceRgb COLOR_GRAY = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb COLOR_LIGHT_BG = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb COLOR_CRITICAL = new DeviceRgb(220, 38, 38);
    private static final DeviceRgb COLOR_HIGH = new DeviceRgb(234, 88, 12);
    private static final DeviceRgb COLOR_MEDIUM = new DeviceRgb(202, 138, 4);
    private static final DeviceRgb COLOR_LOW = new DeviceRgb(37, 99, 235);
    private static final DeviceRgb COLOR_WHITE = new DeviceRgb(255, 255, 255);

    // ═══════════════ PDF Report ═══════════════

    public byte[] generatePdfReport(Long taskId) {
        try {
            ScanTask task = scanTaskRepository.findById(taskId)
                    .orElseThrow(() -> new ResourceNotFoundException("ScanTask", taskId));
            List<Asset> assets = assetRepository.findByTaskId(taskId);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            pdf.setDefaultPageSize(PageSize.A4);
            Document doc = new Document(pdf);

            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new PageNumberHandler());

            // Cover
            doc.add(new Paragraph(" ").setHeight(60));
            doc.add(new Paragraph("ServerScout")
                    .setFontSize(36).setBold()
                    .setFontColor(COLOR_PRIMARY)
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("安全扫描报告")
                    .setFontSize(24).setFontColor(COLOR_DARK)
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph(" ").setHeight(20));
            doc.add(dividerLine());

            addMetaTable(doc, task);
            doc.add(new Paragraph(" ").setHeight(30));
            addSummaryCards(doc, task, assets);
            doc.add(new AreaBreak());

            // Asset Table
            doc.add(new Paragraph("资产清单")
                    .setFontSize(16).setBold().setFontColor(COLOR_DARK));
            doc.add(new Paragraph(" ").setHeight(6));

            if (!assets.isEmpty()) {
                Table table = new Table(UnitValue.createPercentArray(
                        new float[]{2.5f, 3, 2.5f, 1.5f, 1.2f, 1.2f})).useAllAvailableWidth();
                pdfHeaderRow(table, "IP地址", "主机名", "操作系统", "端口", "高危", "状态");

                boolean alt = false;
                for (Asset a : assets) {
                    DeviceRgb bg = alt ? COLOR_LIGHT_BG : COLOR_WHITE;
                    pdfCell(table, a.getIpAddress(), bg);
                    pdfCell(table, a.getHostname() != null ? a.getHostname() : "-", bg);
                    pdfCell(table, a.getOsFingerprint() != null ? a.getOsFingerprint() : "-", bg);
                    pdfCell(table, String.valueOf(a.getOpenPortCount()), bg);
                    pdfCellCrit(table, a.getCriticalVulnCount() != null && a.getCriticalVulnCount() > 0
                            ? String.valueOf(a.getCriticalVulnCount()) : "0", bg);
                    pdfCell(table, a.getStatus() != null ? a.getStatus() : "-", bg);
                    alt = !alt;
                }
                doc.add(table);
            } else {
                doc.add(new Paragraph("暂无资产数据").setFontSize(9).setFontColor(COLOR_GRAY));
            }
            doc.add(new Paragraph(" ").setHeight(10));

            // Port Table
            int totalPortRows = 0;
            for (Asset a : assets) {
                totalPortRows += portRepository.findByAssetId(a.getId()).size();
            }
            if (totalPortRows > 0) {
                doc.add(new Paragraph("端口详情")
                        .setFontSize(14).setBold().setFontColor(COLOR_DARK));
                doc.add(new Paragraph(" ").setHeight(4));

                Table pTable = new Table(UnitValue.createPercentArray(
                        new float[]{2.5f, 1.2f, 1, 2, 2.5f, 1.5f})).useAllAvailableWidth();
                pdfHeaderRow(pTable, "IP地址", "端口", "协议", "服务", "版本", "状态");

                boolean alt = false;
                for (Asset a : assets) {
                    for (Port p : portRepository.findByAssetId(a.getId())) {
                        DeviceRgb bg = alt ? COLOR_LIGHT_BG : COLOR_WHITE;
                        pdfCell(pTable, a.getIpAddress(), bg);
                        pdfCell(pTable, String.valueOf(p.getPortNumber()), bg);
                        pdfCell(pTable, p.getProtocol() != null ? p.getProtocol() : "tcp", bg);
                        pdfCell(pTable, p.getServiceName() != null ? p.getServiceName() : "-", bg);
                        pdfCell(pTable, p.getServiceVersion() != null ? p.getServiceVersion() : "-", bg);
                        pdfCell(pTable, p.getState() != null ? p.getState() : "open", bg);
                        alt = !alt;
                    }
                }
                doc.add(pTable);
                doc.add(new Paragraph(" ").setHeight(10));
            }

            // Vulnerability Table
            int totalVulns = 0;
            for (Asset a : assets) {
                totalVulns += vulnRepository.findByAssetIdWithCve(a.getId()).size();
            }
            if (totalVulns > 0) {
                doc.add(new Paragraph("漏洞详情")
                        .setFontSize(14).setBold().setFontColor(COLOR_DARK));
                doc.add(new Paragraph(" ").setHeight(4));

                Table vTable = new Table(UnitValue.createPercentArray(
                        new float[]{2, 1.5f, 1, 3, 1.5f})).useAllAvailableWidth();
                pdfHeaderRow(vTable, "CVE编号", "严重等级", "CVSS", "受影响软件", "状态");

                boolean alt = false;
                for (Asset a : assets) {
                    for (AssetVulnerability v : vulnRepository.findByAssetIdWithCve(a.getId())) {
                        CveDatabase cve = v.getCveDatabase();
                        DeviceRgb bg = alt ? COLOR_LIGHT_BG : COLOR_WHITE;
                        pdfCell(vTable, cve != null ? cve.getCveId() : "-", bg);
                        pdfCellSeverity(vTable, cve != null ? cve.getSeverity() : "unknown", bg);
                        pdfCell(vTable, cve != null && cve.getCvssScore() != null
                                ? String.format("%.1f", cve.getCvssScore()) : "-", bg);
                        pdfCell(vTable, cve != null && cve.getAffectedSoftware() != null
                                ? cve.getAffectedSoftware() : "-", bg);
                        pdfCell(vTable, v.getStatus() != null ? v.getStatus() : "open", bg);
                        alt = !alt;
                    }
                }
                doc.add(vTable);
            }

            doc.add(new Paragraph(" ").setHeight(20));
            doc.add(dividerLine());
            doc.add(new Paragraph("本报告由 ServerScout 自动生成 | "
                    + java.time.LocalDateTime.now().format(DTF))
                    .setFontSize(7).setFontColor(COLOR_GRAY)
                    .setTextAlignment(TextAlignment.CENTER));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF报告生成失败: " + e.getMessage(), e);
        }
    }

    // ═══════════════ Excel Report ═══════════════

    public byte[] generateExcelReport(Long taskId) {
        try {
            ScanTask task = scanTaskRepository.findById(taskId)
                    .orElseThrow(() -> new ResourceNotFoundException("ScanTask", taskId));
            List<Asset> assets = assetRepository.findByTaskId(taskId);

            Workbook wb = new XSSFWorkbook();

            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle titleStyle = createTitleStyle(wb);
            CellStyle labelStyle = createLabelStyle(wb);
            CellStyle valueStyle = createValueStyle(wb);
            CellStyle criticalStyle = createSeverityStyle(wb, IndexedColors.RED);
            CellStyle highStyle = createSeverityStyle(wb, IndexedColors.ORANGE);
            CellStyle mediumStyle = createSeverityStyle(wb, IndexedColors.GOLD);
            CellStyle lowStyle = createSeverityStyle(wb, IndexedColors.LIGHT_BLUE);
            CellStyle altRowStyle = createAltRowStyle(wb);

            // Sheet 1: Summary
            Sheet summary = wb.createSheet("扫描概要");
            summary.setColumnWidth(0, 5000);
            summary.setColumnWidth(1, 12000);

            int r = 0;
            Row titleRow = summary.createRow(r++);
            Cell tc = titleRow.createCell(0);
            tc.setCellValue("ServerScout 安全扫描报告");
            tc.setCellStyle(titleStyle);
            summary.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
            r++;

            String[][] metaRows = {
                    {"任务名称", task.getName()},
                    {"扫描目标", task.getTargetRange()},
                    {"扫描类型", task.getScanType()},
                    {"任务状态", task.getStatus()},
                    {"发现资产数", String.valueOf(task.getTotalAssets())},
                    {"开放端口数", String.valueOf(task.getTotalPorts())},
                    {"开始时间", task.getStartedAt() != null ? DTF.format(task.getStartedAt()) : "-"},
                    {"完成时间", task.getCompletedAt() != null ? DTF.format(task.getCompletedAt()) : "-"},
            };
            for (String[] mrow : metaRows) {
                Row metaRow = summary.createRow(r++);
                Cell lb = metaRow.createCell(0);
                lb.setCellValue(mrow[0]);
                lb.setCellStyle(labelStyle);
                Cell vl = metaRow.createCell(1);
                vl.setCellValue(mrow[1]);
                vl.setCellStyle(valueStyle);
            }

            // Sheet 2: Assets
            Sheet assetSheet = wb.createSheet("资产清单");
            String[] aCols = {"IP地址", "主机名", "操作系统", "开放端口数", "高危漏洞数", "状态", "MAC地址", "首次发现", "最近扫描"};
            int[] aWidths = {4500, 5000, 5500, 3000, 3000, 2500, 4500, 5000, 5000};
            createSheetHeader(assetSheet, aCols, aWidths, headerStyle, wb);

            int ar = 1;
            for (Asset a : assets) {
                Row row = assetSheet.createRow(ar++);
                fillRow(row, new String[]{
                        a.getIpAddress(),
                        a.getHostname() != null ? a.getHostname() : "",
                        a.getOsFingerprint() != null ? a.getOsFingerprint() : "",
                        String.valueOf(a.getOpenPortCount()),
                        String.valueOf(a.getCriticalVulnCount() != null ? a.getCriticalVulnCount() : 0),
                        a.getStatus() != null ? a.getStatus() : "",
                        a.getMacAddress() != null ? a.getMacAddress() : "",
                        a.getFirstSeenTime() != null ? DTF.format(a.getFirstSeenTime()) : "",
                        a.getLastScanTime() != null ? DTF.format(a.getLastScanTime()) : "",
                }, altRowStyle, ar);
            }
            freezeAndFilter(assetSheet, aCols.length);

            // Sheet 3: Ports
            Sheet portSheet = wb.createSheet("端口信息");
            String[] pCols = {"IP地址", "端口号", "协议", "服务", "产品", "版本", "Banner", "状态", "Web服务"};
            int[] pWidths = {4500, 2500, 2000, 4000, 4000, 3500, 6000, 2500, 2500};
            createSheetHeader(portSheet, pCols, pWidths, headerStyle, wb);

            int pr = 1;
            for (Asset a : assets) {
                for (Port p : portRepository.findByAssetId(a.getId())) {
                    Row row = portSheet.createRow(pr++);
                    fillRow(row, new String[]{
                            a.getIpAddress(),
                            String.valueOf(p.getPortNumber()),
                            p.getProtocol() != null ? p.getProtocol() : "tcp",
                            p.getServiceName() != null ? p.getServiceName() : "",
                            p.getServiceProduct() != null ? p.getServiceProduct() : "",
                            p.getServiceVersion() != null ? p.getServiceVersion() : "",
                            p.getBanner() != null ? p.getBanner() : "",
                            p.getState() != null ? p.getState() : "open",
                            Boolean.TRUE.equals(p.getIsWebService()) ? "是" : "否",
                    }, altRowStyle, pr);
                }
            }
            freezeAndFilter(portSheet, pCols.length);

            // Sheet 4: Vulnerabilities
            Sheet vSheet = wb.createSheet("漏洞详情");
            String[] vCols = {"CVE编号", "严重等级", "CVSS评分", "描述", "受影响软件", "受影响版本", "修复建议", "状态", "发现时间"};
            int[] vWidths = {4500, 3000, 3000, 8000, 6000, 4000, 8000, 2500, 5000};
            createSheetHeader(vSheet, vCols, vWidths, headerStyle, wb);

            int vr = 1;
            for (Asset a : assets) {
                for (AssetVulnerability v : vulnRepository.findByAssetIdWithCve(a.getId())) {
                    CveDatabase cve = v.getCveDatabase();
                    Row row = vSheet.createRow(vr++);
                    String severity = cve != null ? cve.getSeverity() : "";
                    fillRow(row, new String[]{
                            cve != null ? cve.getCveId() : "",
                            severity,
                            cve != null && cve.getCvssScore() != null ? String.format("%.1f", cve.getCvssScore()) : "",
                            cve != null && cve.getDescription() != null ? cve.getDescription() : "",
                            cve != null && cve.getAffectedSoftware() != null ? cve.getAffectedSoftware() : "",
                            cve != null && cve.getAffectedVersionRange() != null ? cve.getAffectedVersionRange() : "",
                            cve != null && cve.getFixSuggestion() != null ? cve.getFixSuggestion() : "",
                            v.getStatus() != null ? v.getStatus() : "open",
                            v.getDiscoveredAt() != null ? DTF.format(v.getDiscoveredAt()) : "",
                    }, altRowStyle, vr);

                    Cell sevCell = row.getCell(1);
                    if ("critical".equalsIgnoreCase(severity)) sevCell.setCellStyle(criticalStyle);
                    else if ("high".equalsIgnoreCase(severity)) sevCell.setCellStyle(highStyle);
                    else if ("medium".equalsIgnoreCase(severity)) sevCell.setCellStyle(mediumStyle);
                    else if ("low".equalsIgnoreCase(severity)) sevCell.setCellStyle(lowStyle);
                }
            }
            freezeAndFilter(vSheet, vCols.length);

            // Sheet 5: Stats
            Sheet statsSheet = wb.createSheet("统计汇总");
            statsSheet.setColumnWidth(0, 6000);
            statsSheet.setColumnWidth(1, 5000);

            int sr = 0;
            Row stTitle = statsSheet.createRow(sr++);
            Cell sh1 = stTitle.createCell(0);
            sh1.setCellValue("统计项");
            sh1.setCellStyle(headerStyle);
            Cell sh2 = stTitle.createCell(1);
            sh2.setCellValue("数量");
            sh2.setCellStyle(headerStyle);

            long totalVulns = 0, critical = 0, high = 0, medium = 0, low = 0;
            for (Asset a : assets) {
                List<AssetVulnerability> vulns = vulnRepository.findByAssetIdWithCve(a.getId());
                totalVulns += vulns.size();
                for (AssetVulnerability v : vulns) {
                    CveDatabase cve = v.getCveDatabase();
                    if (cve == null) continue;
                    String sev = cve.getSeverity();
                    if ("critical".equalsIgnoreCase(sev)) critical++;
                    else if ("high".equalsIgnoreCase(sev)) high++;
                    else if ("medium".equalsIgnoreCase(sev)) medium++;
                    else if ("low".equalsIgnoreCase(sev)) low++;
                }
            }

            String[][] statsRows = {
                    {"资产总数", String.valueOf(assets.size())},
                    {"开放端口总数", String.valueOf(task.getTotalPorts())},
                    {"漏洞总数", String.valueOf(totalVulns)},
                    {"Critical 高危", String.valueOf(critical)},
                    {"High 高危", String.valueOf(high)},
                    {"Medium 中危", String.valueOf(medium)},
                    {"Low 低危", String.valueOf(low)},
            };
            for (String[] sRow : statsRows) {
                Row row = statsSheet.createRow(sr++);
                Cell lb = row.createCell(0);
                lb.setCellValue(sRow[0]);
                lb.setCellStyle(labelStyle);
                Cell vl = row.createCell(1);
                vl.setCellValue(Integer.parseInt(sRow[1]));
                vl.setCellStyle(valueStyle);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            wb.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Excel报告生成失败: " + e.getMessage(), e);
        }
    }

    // ──────────── PDF Helpers ────────────

    private void addMetaTable(Document doc, ScanTask task) {
        Table metaTable = new Table(UnitValue.createPercentArray(new float[]{2, 5}))
                .useAllAvailableWidth().setMarginLeft(40).setMarginRight(40);
        metaRow(metaTable, "任务名称", task.getName());
        metaRow(metaTable, "扫描目标", task.getTargetRange());
        metaRow(metaTable, "扫描类型", task.getScanType());
        metaRow(metaTable, "任务状态", task.getStatus());
        if (task.getStartedAt() != null)
            metaRow(metaTable, "开始时间", DTF.format(task.getStartedAt()));
        if (task.getCompletedAt() != null)
            metaRow(metaTable, "完成时间", DTF.format(task.getCompletedAt()));
        doc.add(metaTable);
    }

    private void metaRow(Table table, String label, String value) {
        table.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(label).setFontSize(9).setBold().setFontColor(COLOR_DARK))
                .setBorder(Border.NO_BORDER).setPadding(4));
        table.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(value != null ? value : "-").setFontSize(9).setFontColor(COLOR_GRAY))
                .setBorder(Border.NO_BORDER).setPadding(4));
    }

    private void addSummaryCards(Document doc, ScanTask task, List<Asset> assets) {
        Table cards = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1}))
                .useAllAvailableWidth();

        long critCount = 0;
        for (Asset a : assets) {
            for (AssetVulnerability v : vulnRepository.findByAssetIdWithCve(a.getId())) {
                CveDatabase cve = v.getCveDatabase();
                if (cve != null && "critical".equalsIgnoreCase(cve.getSeverity())) critCount++;
            }
        }

        summaryCard(cards, "发现资产", String.valueOf(task.getTotalAssets()), COLOR_PRIMARY);
        summaryCard(cards, "开放端口", String.valueOf(task.getTotalPorts()),
                new DeviceRgb(8, 145, 178));
        summaryCard(cards, "高危漏洞", String.valueOf(critCount), COLOR_CRITICAL);
        summaryCard(cards, "扫描状态",
                "completed".equals(task.getStatus()) ? "已完成" : task.getStatus(),
                new DeviceRgb(34, 197, 94));
        doc.add(cards);
    }

    private void summaryCard(Table table, String label, String value, DeviceRgb accent) {
        com.itextpdf.layout.element.Cell card = new com.itextpdf.layout.element.Cell()
                .setBorder(Border.NO_BORDER).setPadding(10).setMargin(4)
                .setBackgroundColor(COLOR_LIGHT_BG);
        card.add(new Paragraph(label).setFontSize(8).setFontColor(COLOR_GRAY)
                .setTextAlignment(TextAlignment.CENTER));
        card.add(new Paragraph(value).setFontSize(18).setBold()
                .setFontColor(accent).setTextAlignment(TextAlignment.CENTER));
        table.addCell(card);
    }

    private LineSeparator dividerLine() {
        LineSeparator ls = new LineSeparator(new SolidLine(1f));
        ls.setWidth(UnitValue.createPercentValue(90));
        return ls;
    }

    private void pdfHeaderRow(Table table, String... headers) {
        for (String h : headers) {
            table.addHeaderCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(h).setFontSize(7).setBold().setFontColor(COLOR_WHITE))
                    .setBackgroundColor(COLOR_DARK)
                    .setTextAlignment(TextAlignment.CENTER).setPadding(5));
        }
    }

    private void pdfCell(Table table, String text, DeviceRgb bg) {
        table.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(text).setFontSize(7).setFontColor(COLOR_DARK))
                .setBackgroundColor(bg).setPadding(4));
    }

    private void pdfCellCrit(Table table, String text, DeviceRgb bg) {
        int val = 0;
        try { val = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
        DeviceRgb color = val > 0 ? COLOR_CRITICAL : COLOR_DARK;
        table.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(text).setFontSize(7).setFontColor(color).setBold())
                .setBackgroundColor(bg).setTextAlignment(TextAlignment.CENTER).setPadding(4));
    }

    private void pdfCellSeverity(Table table, String severity, DeviceRgb bg) {
        DeviceRgb color;
        switch (severity != null ? severity.toLowerCase() : "") {
            case "critical": color = COLOR_CRITICAL; break;
            case "high": color = COLOR_HIGH; break;
            case "medium": color = COLOR_MEDIUM; break;
            case "low": color = COLOR_LOW; break;
            default: color = COLOR_GRAY;
        }
        table.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(severity).setFontSize(7).setBold().setFontColor(color))
                .setBackgroundColor(bg).setTextAlignment(TextAlignment.CENTER).setPadding(4));
    }

    // ──────────── Excel Helpers ────────────

    private void createSheetHeader(Sheet sheet, String[] cols, int[] widths, CellStyle headerStyle, Workbook wb) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < cols.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, widths[i]);
        }
    }

    private void fillRow(Row row, String[] values, CellStyle altStyle, int rowNum) {
        for (int i = 0; i < values.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(values[i]);
            if (altStyle != null && rowNum % 2 == 0) c.setCellStyle(altStyle);
        }
    }

    private void freezeAndFilter(Sheet sheet, int colCount) {
        sheet.createFreezePane(0, 1);
        if (sheet.getLastRowNum() > 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, sheet.getLastRowNum(), 0, colCount - 1));
        }
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        return style;
    }

    private CellStyle createLabelStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createValueStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createAltRowStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createSeverityStyle(Workbook wb, IndexedColors color) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(color.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    // ──────────── Page Number Handler ────────────

    static class PageNumberHandler implements IEventHandler {
        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfPage page = docEvent.getPage();
            int pageNum = docEvent.getDocument().getPageNumber(page);
            PdfCanvas canvas = new PdfCanvas(page.newContentStreamBefore(),
                    page.getResources(), docEvent.getDocument());
            try {
                canvas.beginText()
                        .setFontAndSize(com.itextpdf.kernel.font.PdfFontFactory.createFont(), 7)
                        .moveText(page.getPageSize().getWidth() / 2 - 20, 20)
                        .showText("- " + pageNum + " -")
                        .endText();
            } catch (java.io.IOException e) {
                // silently skip page number if font unavailable
            }
        }
    }
}
