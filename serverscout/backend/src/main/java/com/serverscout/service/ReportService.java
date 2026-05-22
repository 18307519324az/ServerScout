package com.serverscout.service;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import com.serverscout.util.ResourceNotFoundException;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
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

    public byte[] generatePdfReport(Long taskId) {
        try {
            ScanTask task = scanTaskRepository.findById(taskId)
                    .orElseThrow(() -> new ResourceNotFoundException("ScanTask", taskId));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);

            // Title
            doc.add(new Paragraph("ServerScout 安全扫描报告")
                    .setFontSize(20).setBold().setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph(" "));

            // Task info
            doc.add(new Paragraph("扫描任务概要").setFontSize(14).setBold());
            doc.add(new Paragraph("任务名称: " + task.getName()));
            doc.add(new Paragraph("扫描目标: " + task.getTargetRange()));
            doc.add(new Paragraph("扫描类型: " + task.getScanType()));
            doc.add(new Paragraph("任务状态: " + task.getStatus()));
            if (task.getStartedAt() != null)
                doc.add(new Paragraph("开始时间: " + DTF.format(task.getStartedAt())));
            if (task.getCompletedAt() != null)
                doc.add(new Paragraph("完成时间: " + DTF.format(task.getCompletedAt())));
            doc.add(new Paragraph(" "));

            // Stats
            doc.add(new Paragraph("资产统计").setFontSize(14).setBold());
            doc.add(new Paragraph("发现资产: " + task.getTotalAssets() + " 个"));
            doc.add(new Paragraph("开放端口: " + task.getTotalPorts() + " 个"));
            doc.add(new Paragraph(" "));

            // Asset table
            List<Asset> assets = assetRepository.findByTaskId(taskId);
            if (!assets.isEmpty()) {
                doc.add(new Paragraph("资产清单").setFontSize(14).setBold());
                Table table = new Table(UnitValue.createPercentArray(new float[]{3, 4, 4, 3, 3, 3}))
                        .useAllAvailableWidth();
                pdfHeader(table, "IP地址");
                pdfHeader(table, "主机名");
                pdfHeader(table, "OS");
                pdfHeader(table, "端口数");
                pdfHeader(table, "高危漏洞");
                pdfHeader(table, "状态");

                for (Asset a : assets) {
                    table.addCell(pdfCell(a.getIpAddress()));
                    table.addCell(pdfCell(a.getHostname() != null ? a.getHostname() : "-"));
                    table.addCell(pdfCell(a.getOsFingerprint() != null ? a.getOsFingerprint() : "-"));
                    table.addCell(pdfCell(String.valueOf(a.getOpenPortCount())));
                    table.addCell(pdfCell(String.valueOf(a.getCriticalVulnCount())));
                    table.addCell(pdfCell(a.getStatus()));
                }
                doc.add(table);
            }

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("本报告由 ServerScout 自动生成 | "
                    + java.time.LocalDateTime.now().format(DTF)).setFontSize(8));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF报告生成失败: " + e.getMessage(), e);
        }
    }

    public byte[] generateExcelReport(Long taskId) {
        try {
            ScanTask task = scanTaskRepository.findById(taskId)
                    .orElseThrow(() -> new ResourceNotFoundException("ScanTask", taskId));
            List<Asset> assets = assetRepository.findByTaskId(taskId);

            Workbook wb = new XSSFWorkbook();
            CellStyle boldStyle = wb.createCellStyle();
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            boldStyle.setFont(boldFont);

            // Summary sheet
            Sheet summary = wb.createSheet("扫描概要");
            int r = 0;
            excelRow(summary, r++, "任务名称", task.getName(), boldStyle);
            excelRow(summary, r++, "扫描目标", task.getTargetRange(), boldStyle);
            excelRow(summary, r++, "扫描类型", task.getScanType(), boldStyle);
            excelRow(summary, r++, "发现资产数", String.valueOf(task.getTotalAssets()), boldStyle);
            excelRow(summary, r++, "开放端口数", String.valueOf(task.getTotalPorts()), boldStyle);

            // Assets sheet
            Sheet assetSheet = wb.createSheet("资产清单");
            Row aHeader = assetSheet.createRow(0);
            String[] aCols = {"IP地址", "主机名", "操作系统", "开放端口数", "高危漏洞数", "状态"};
            for (int i = 0; i < aCols.length; i++) {
                aHeader.createCell(i).setCellValue(aCols[i]);
                aHeader.getCell(i).setCellStyle(boldStyle);
            }
            int ar = 1;
            for (Asset a : assets) {
                Row row = assetSheet.createRow(ar++);
                row.createCell(0).setCellValue(a.getIpAddress());
                row.createCell(1).setCellValue(a.getHostname() != null ? a.getHostname() : "");
                row.createCell(2).setCellValue(a.getOsFingerprint() != null ? a.getOsFingerprint() : "");
                row.createCell(3).setCellValue(a.getOpenPortCount());
                row.createCell(4).setCellValue(a.getCriticalVulnCount());
                row.createCell(5).setCellValue(a.getStatus());
            }
            for (int i = 0; i < aCols.length; i++) assetSheet.autoSizeColumn(i);

            // Ports sheet
            Sheet portSheet = wb.createSheet("端口信息");
            Row pHeader = portSheet.createRow(0);
            String[] pCols = {"IP地址", "端口号", "协议", "服务", "版本", "状态"};
            for (int i = 0; i < pCols.length; i++) {
                pHeader.createCell(i).setCellValue(pCols[i]);
                pHeader.getCell(i).setCellStyle(boldStyle);
            }
            int pr = 1;
            for (Asset a : assets) {
                List<Port> ports = portRepository.findByAssetId(a.getId());
                for (Port p : ports) {
                    Row row = portSheet.createRow(pr++);
                    row.createCell(0).setCellValue(a.getIpAddress());
                    row.createCell(1).setCellValue(p.getPortNumber());
                    row.createCell(2).setCellValue(p.getProtocol());
                    row.createCell(3).setCellValue(p.getServiceName() != null ? p.getServiceName() : "");
                    row.createCell(4).setCellValue(p.getServiceVersion() != null ? p.getServiceVersion() : "");
                    row.createCell(5).setCellValue(p.getState());
                }
            }
            for (int i = 0; i < pCols.length; i++) portSheet.autoSizeColumn(i);

            // Vulnerabilities sheet (with CVE data)
            Sheet vSheet = wb.createSheet("漏洞详情");
            Row vHeader = vSheet.createRow(0);
            String[] vCols = {"CVE编号", "严重等级", "CVSS评分", "受影响软件", "状态"};
            for (int i = 0; i < vCols.length; i++) {
                vHeader.createCell(i).setCellValue(vCols[i]);
                vHeader.getCell(i).setCellStyle(boldStyle);
            }
            int vr = 1;
            for (Asset a : assets) {
                List<AssetVulnerability> vulns = vulnRepository.findByAssetIdWithCve(a.getId());
                for (AssetVulnerability v : vulns) {
                    CveDatabase cve = v.getCveDatabase();
                    Row row = vSheet.createRow(vr++);
                    row.createCell(0).setCellValue(cve != null ? cve.getCveId() : "");
                    row.createCell(1).setCellValue(cve != null ? cve.getSeverity() : "");
                    row.createCell(2).setCellValue(cve != null && cve.getCvssScore() != null
                            ? cve.getCvssScore().doubleValue() : 0);
                    row.createCell(3).setCellValue(cve != null && cve.getAffectedSoftware() != null
                            ? cve.getAffectedSoftware() : "");
                    row.createCell(4).setCellValue(v.getStatus());
                }
            }
            for (int i = 0; i < vCols.length; i++) vSheet.autoSizeColumn(i);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            wb.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Excel报告生成失败: " + e.getMessage(), e);
        }
    }

    private void pdfHeader(Table table, String text) {
        table.addHeaderCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(text).setFontSize(8).setBold().setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(ColorConstants.DARK_GRAY)
                .setTextAlignment(TextAlignment.CENTER));
    }

    private com.itextpdf.layout.element.Cell pdfCell(String text) {
        return new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(text).setFontSize(8));
    }

    private void excelRow(Sheet sheet, int rowNum, String label, String value, CellStyle boldStyle) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.getCell(0).setCellStyle(boldStyle);
        row.createCell(1).setCellValue(value);
    }
}
