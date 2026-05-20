package com.serverscout.service;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import com.serverscout.util.ResourceNotFoundException;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ScanTaskRepository scanTaskRepository;
    private final AssetRepository assetRepository;
    private final PortRepository portRepository;

    public byte[] generatePdfReport(Long taskId) {
        try {
            ScanTask task = scanTaskRepository.findById(taskId)
                    .orElseThrow(() -> new ResourceNotFoundException("ScanTask", taskId));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);

            doc.add(new Paragraph("ServerScout 扫描报告"));
            doc.add(new Paragraph("任务: " + task.getName()));
            doc.add(new Paragraph("目标: " + task.getTargetRange()));
            doc.add(new Paragraph("时间: " + task.getCompletedAt()));
            doc.add(new Paragraph(" "));

            List<Asset> assets = assetRepository.findByTaskId(taskId);
            doc.add(new Paragraph("发现资产: " + assets.size() + " 个"));

            Table table = new Table(UnitValue.createPercentArray(new float[]{15, 25, 15, 20, 25}))
                    .useAllAvailableWidth();
            table.addHeaderCell("IP");
            table.addHeaderCell("主机名");
            table.addHeaderCell("开放端口");
            table.addHeaderCell("OS");
            table.addHeaderCell("高危漏洞");

            for (Asset a : assets) {
                table.addCell(a.getIpAddress());
                table.addCell(a.getHostname() != null ? a.getHostname() : "-");
                table.addCell(String.valueOf(a.getOpenPortCount()));
                table.addCell(a.getOsFingerprint() != null ? a.getOsFingerprint() : "-");
                table.addCell(String.valueOf(a.getCriticalVulnCount()));
            }
            doc.add(table);
            doc.close();

            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF report generation failed", e);
        }
    }

    public byte[] generateExcelReport(Long taskId) {
        try {
            ScanTask task = scanTaskRepository.findById(taskId)
                    .orElseThrow(() -> new ResourceNotFoundException("ScanTask", taskId));
            List<Asset> assets = assetRepository.findByTaskId(taskId);

            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet("资产清单");

            Row header = sheet.createRow(0);
            String[] cols = {"IP", "主机名", "OS", "开放端口", "高危漏洞"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
            }

            int rowNum = 1;
            for (Asset a : assets) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(a.getIpAddress());
                row.createCell(1).setCellValue(a.getHostname() != null ? a.getHostname() : "");
                row.createCell(2).setCellValue(a.getOsFingerprint() != null ? a.getOsFingerprint() : "");
                row.createCell(3).setCellValue(a.getOpenPortCount());
                row.createCell(4).setCellValue(a.getCriticalVulnCount());
            }

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            wb.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Excel report generation failed", e);
        }
    }
}
