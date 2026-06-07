package com.serverscout.service;

import com.serverscout.entity.*;
import com.serverscout.repository.*;
import com.serverscout.common.ErrorCode;
import com.serverscout.exception.ResourceNotFoundException;
import com.serverscout.exception.ServiceException;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
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
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ScanTaskRepository scanTaskRepository;
    private final AssetRepository assetRepository;
    private final PortRepository portRepository;
    private final AssetVulnerabilityRepository vulnRepository;
    private final HoneypotDetectionRepository honeypotDetectionRepository;
    private final ScanAssetMappingRepository scanAssetMappingRepository;
    private final WebFingerprintRepository webFingerprintRepository;
    private final SslCertificateRepository sslCertificateRepository;

    @Value("${app.pdf.font-path:C:/Windows/Fonts/msyh.ttc}")
    private String fontPath;

    private PdfFont chineseFont;

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

    /**
     * Get all assets associated with a scan task, combining both linkages:
     * 1. asset.task_id (set on first discovery, used by demo data)
     * 2. scan_asset_mapping (set on every scan, used by re-scans)
     */
    private List<Asset> getTaskAssets(Long taskId) {
        List<Asset> assets = new ArrayList<>(assetRepository.findByTaskId(taskId));
        Set<Long> seen = new HashSet<>();
        for (Asset a : assets) seen.add(a.getId());
        for (ScanAssetMapping m : scanAssetMappingRepository.findByScanTaskIdWithAsset(taskId)) {
            if (seen.add(m.getAsset().getId())) {
                assets.add(m.getAsset());
            }
        }
        // Fallback: if no assets found via task linkage, include all assets in system
        if (assets.isEmpty()) {
            assets = assetRepository.findAll();
        }
        return assets;
    }
    private static final DeviceRgb COLOR_WHITE = new DeviceRgb(255, 255, 255);

    // ═══════════════ PDF Report ═══════════════

    private PdfFont loadChineseFont() {
        // Try configured path first
        String[] configuredFontPaths = {
            fontPath,
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/msyhbd.ttf",
            "C:/Windows/Fonts/simsun.ttc",
            "C:/Windows/Fonts/NotoSansSC-VF.ttf",
            "/System/Library/Fonts/PingFang.ttc",
            "/usr/share/fonts/wqy-microhei/wqy-microhei.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
        };

        for (String path : configuredFontPaths) {
            try {
                Path p = Paths.get(path);
                if (Files.exists(p)) {
                    String fontProgram = path;
                    if (path.endsWith(".ttc")) {
                        fontProgram = path + ",0";
                    }
                    PdfFont font = PdfFontFactory.createFont(fontProgram, PdfEncodings.IDENTITY_H,
                            PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                    log.info("PDF: Loaded Chinese font from {}", path);
                    return font;
                }
            } catch (IOException e) {
                log.debug("PDF font attempt failed for {}: {}", path, e.getMessage());
            }
        }

        log.warn("PDF: No Chinese font found. Chinese characters may not render correctly. "
                + "Please set app.pdf.font-path or install a CJK font.");
        return null;
    }

    private Paragraph cPara(String text) {
        Paragraph p = new Paragraph(text);
        if (chineseFont != null) p.setFont(chineseFont);
        return p;
    }

    private com.itextpdf.layout.element.Cell cCell() {
        com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell();
        if (chineseFont != null) cell.setFont(chineseFont);
        return cell;
    }

    @Transactional(readOnly = true)
    public byte[] generatePdfReport(Long taskId) {
        try {
            this.chineseFont = loadChineseFont();

            ScanTask task = scanTaskRepository.findById(taskId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SCAN_TASK_NOT_FOUND, "ScanTask", taskId));
            List<Asset> assets = getTaskAssets(taskId);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            pdf.setDefaultPageSize(PageSize.A4);
            Document doc = new Document(pdf);

            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new PageNumberHandler());

            // ═══ Cover Page ═══
            doc.add(cPara(" ").setHeight(50));
            doc.add(cPara("ServerScout")
                    .setFontSize(36).setBold()
                    .setFontColor(COLOR_PRIMARY)
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(cPara("安全扫描分析报告")
                    .setFontSize(22).setFontColor(COLOR_DARK)
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(cPara(" ").setHeight(12));
            doc.add(cPara("网络资产攻击面可视化分析平台")
                    .setFontSize(10).setFontColor(COLOR_GRAY)
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(cPara(" ").setHeight(18));
            doc.add(dividerLine());

            // Scan metadata table with labels
            doc.add(cPara(" ").setHeight(8));
            doc.add(cPara("扫描任务概要").setFontSize(13).setBold()
                    .setFontColor(COLOR_DARK).setTextAlignment(TextAlignment.CENTER));
            doc.add(cPara(" ").setHeight(6));
            addMetaTable(doc, task);
            doc.add(cPara(" ").setHeight(14));

            // Summary stats cards
            addSummaryCards(doc, task, assets);
            doc.add(cPara(" ").setHeight(8));
            doc.add(cPara("报告生成时间：" + java.time.LocalDateTime.now().format(DTF))
                    .setFontSize(7).setFontColor(COLOR_GRAY).setTextAlignment(TextAlignment.CENTER));
            doc.add(new AreaBreak());

            // ═══ Section 1: Summary & Legend ═══
            doc.add(cPara("一、报告概述").setFontSize(15).setBold().setFontColor(COLOR_DARK));
            doc.add(cPara(" ").setHeight(4));
            doc.add(cPara("本报告由 ServerScout 自动生成，包含扫描任务的资产发现、端口信息、漏洞详情等关键安全数据。"
                    + "以下各节分别展示不同维度的扫描结果。")
                    .setFontSize(9).setFontColor(COLOR_GRAY));
            doc.add(cPara(" ").setHeight(6));

            // Severity legend
            doc.add(cPara("漏洞严重等级说明：").setFontSize(9).setBold().setFontColor(COLOR_DARK));
            addSeverityLegend(doc);
            doc.add(cPara(" ").setHeight(8));

            // Stats summary
            addDetailedStats(doc, task, assets);
            doc.add(new AreaBreak());

            // ═══ Section 2: Asset List ═══
            doc.add(cPara("二、资产清单").setFontSize(15).setBold().setFontColor(COLOR_DARK));
            doc.add(cPara("以下列出扫描发现的所有网络资产，包括 IP 地址、主机名、操作系统指纹、开放端口数量及高危漏洞统计。")
                    .setFontSize(8).setFontColor(COLOR_GRAY));
            doc.add(cPara(" ").setHeight(6));

            if (!assets.isEmpty()) {
                Table table = new Table(UnitValue.createPercentArray(
                        new float[]{2.2f, 2.8f, 2.5f, 1.5f, 1.3f, 1.3f})).useAllAvailableWidth();
                pdfHeaderRow(table, "IP 地址", "主机名", "操作系统", "端口数", "高危漏洞", "状态");

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
                doc.add(cPara(String.format("（共 %d 个资产）", assets.size()))
                        .setFontSize(7).setFontColor(COLOR_GRAY).setTextAlignment(TextAlignment.RIGHT));
            } else {
                doc.add(cPara("（本次扫描未发现资产）").setFontSize(9).setFontColor(COLOR_GRAY));
            }
            doc.add(cPara(" ").setHeight(8));

            // ═══ Section 3: Port Details ═══
            int totalPortRows = 0;
            for (Asset a : assets) {
                totalPortRows += portRepository.findByAssetId(a.getId()).size();
            }
            if (totalPortRows > 0) {
                doc.add(cPara("三、端口详情").setFontSize(15).setBold().setFontColor(COLOR_DARK));
                doc.add(cPara("列出每个资产上开放的端口及其服务信息。\"服务\"列显示运行的服务类型（如 HTTP、SSH），"
                        + "\"产品/版本\"列显示具体软件及版本号，可用于漏洞关联分析。")
                        .setFontSize(8).setFontColor(COLOR_GRAY));
                doc.add(cPara(" ").setHeight(4));

                Table pTable = new Table(UnitValue.createPercentArray(
                        new float[]{2.2f, 1, 0.8f, 2.2f, 3, 1.2f})).useAllAvailableWidth();
                pdfHeaderRow(pTable, "IP 地址", "端口", "协议", "服务", "产品 / 版本", "状态");

                boolean alt = false;
                for (Asset a : assets) {
                    for (Port p : portRepository.findByAssetId(a.getId())) {
                        DeviceRgb bg = alt ? COLOR_LIGHT_BG : COLOR_WHITE;
                        pdfCell(pTable, a.getIpAddress(), bg);
                        pdfCell(pTable, String.valueOf(p.getPortNumber()), bg);
                        pdfCell(pTable, p.getProtocol() != null ? p.getProtocol() : "tcp", bg);
                        pdfCell(pTable, p.getServiceName() != null ? p.getServiceName() : "-", bg);
                        String prodVer = "";
                        if (p.getServiceProduct() != null) prodVer = p.getServiceProduct();
                        if (p.getServiceVersion() != null) prodVer += " " + p.getServiceVersion();
                        pdfCell(pTable, !prodVer.isBlank() ? prodVer.trim() : "-", bg);
                        pdfCell(pTable, p.getState() != null ? p.getState() : "open", bg);
                        alt = !alt;
                    }
                }
                doc.add(pTable);
                doc.add(cPara(String.format("（共 %d 条端口记录）", totalPortRows))
                        .setFontSize(7).setFontColor(COLOR_GRAY).setTextAlignment(TextAlignment.RIGHT));
                doc.add(cPara(" ").setHeight(8));
            }

            // ═══ Section 4: Web Fingerprint ═══
            int totalFingerprints = 0;
            List<String> fpLines = new ArrayList<>();
            for (Asset a : assets) {
                for (Port p : portRepository.findByAssetId(a.getId())) {
                    var wfOpt = webFingerprintRepository.findByPortId(p.getId());
                    if (wfOpt.isPresent()) {
                        totalFingerprints++;
                        var wf = wfOpt.get();
                        String fwCms = "";
                        if (wf.getFrameworkName() != null) fwCms = wf.getFrameworkName();
                        if (wf.getCmsName() != null) fwCms += (fwCms.isEmpty() ? "" : "/") + wf.getCmsName();
                        fpLines.add(a.getIpAddress() + ":" + p.getPortNumber() + "|"
                                + (wf.getServerHeader() != null ? wf.getServerHeader() : "-") + "|"
                                + (!fwCms.isEmpty() ? fwCms : "-") + "|"
                                + (wf.getTechStack() != null ? wf.getTechStack() : "-") + "|"
                                + (wf.getWafName() != null ? wf.getWafName() : "-") + "|"
                                + (wf.getTitle() != null ? wf.getTitle() : "-"));
                    }
                }
            }
            if (totalFingerprints > 0) {
                doc.add(cPara("四、Web 服务指纹").setFontSize(15).setBold().setFontColor(COLOR_DARK));
                doc.add(cPara("HTTP/HTTPS 探测结果，展示每个 Web 服务的服务器类型、框架、CMS 和技术栈信息。")
                        .setFontSize(8).setFontColor(COLOR_GRAY));
                doc.add(cPara(" ").setHeight(4));

                Table fpTable = new Table(UnitValue.createPercentArray(
                        new float[]{2.5f, 2, 2, 2, 2.5f, 1.2f})).useAllAvailableWidth();
                pdfHeaderRow(fpTable, "IP:端口", "服务器", "框架/CMS", "技术栈", "WAF", "标题");

                boolean alt = false;
                for (String line : fpLines) {
                    String[] cols = line.split("\\|", -1);
                    DeviceRgb bg = alt ? COLOR_LIGHT_BG : COLOR_WHITE;
                    for (int i = 0; i < 6 && i < cols.length; i++) {
                        String val = cols[i];
                        if (val.length() > 30) val = val.substring(0, 30);
                        pdfCell(fpTable, val, bg);
                    }
                    alt = !alt;
                }
                doc.add(fpTable);
                doc.add(cPara(String.format("（共 %d 条 Web 指纹记录）", totalFingerprints))
                        .setFontSize(7).setFontColor(COLOR_GRAY).setTextAlignment(TextAlignment.RIGHT));
                doc.add(cPara(" ").setHeight(8));
            }

            // ═══ Section 4.5: SSL Certificates ═══
            int totalCerts = 0;
            for (Asset a : assets) {
                for (Port p : portRepository.findByAssetId(a.getId())) {
                    if (sslCertificateRepository.findByPortId(p.getId()).isPresent()) totalCerts++;
                }
            }
            if (totalCerts > 0) {
                doc.add(cPara("五、SSL 证书信息").setFontSize(15).setBold().setFontColor(COLOR_DARK));
                doc.add(cPara(" ").setHeight(4));
                Table certTable = new Table(UnitValue.createPercentArray(
                        new float[]{2, 1, 2.5f, 2.5f, 1.5f, 1.5f})).useAllAvailableWidth();
                pdfHeaderRow(certTable, "IP:端口", "端口", "Subject", "Issuer", "有效期自", "有效期至");
                boolean alt = false;
                for (Asset a : assets) {
                    for (Port p : portRepository.findByAssetId(a.getId())) {
                        var certOpt = sslCertificateRepository.findByPortId(p.getId());
                        if (certOpt.isPresent()) {
                            SslCertificate cert = certOpt.get();
                            DeviceRgb bg = alt ? COLOR_LIGHT_BG : COLOR_WHITE;
                            pdfCell(certTable, a.getIpAddress() + ":" + p.getPortNumber(), bg);
                            pdfCell(certTable, String.valueOf(p.getPortNumber()), bg);
                            pdfCell(certTable, cert.getSubject() != null ? cert.getSubject() : "-", bg);
                            pdfCell(certTable, cert.getIssuer() != null ? cert.getIssuer() : "-", bg);
                            pdfCell(certTable, cert.getNotBefore() != null ? cert.getNotBefore().toString() : "-", bg);
                            pdfCell(certTable, cert.getNotAfter() != null ? cert.getNotAfter().toString() : "-", bg);
                            alt = !alt;
                        }
                    }
                }
                doc.add(certTable);
                doc.add(cPara(String.format("（共 %d 条 SSL 证书）", totalCerts))
                        .setFontSize(7).setFontColor(COLOR_GRAY).setTextAlignment(TextAlignment.RIGHT));
                doc.add(cPara(" ").setHeight(8));
            }

            int sec = 4;
            if (totalFingerprints > 0) sec++;
            if (totalCerts > 0) sec++;
            String vulnTitle = numToChinese(sec) + "、漏洞详情";
            String hpTitle = numToChinese(sec + 1) + "、蜜罐检测结果";

            // ═══ Section N: Vulnerability Details ═══
            int totalVulns = 0;
            for (Asset a : assets) {
                totalVulns += vulnRepository.findByAssetIdWithCve(a.getId()).size();
            }
            if (totalVulns > 0) {
                doc.add(cPara(vulnTitle).setFontSize(15).setBold().setFontColor(COLOR_DARK));
                doc.add(cPara("列出通过 CVE 匹配和 Nuclei 扫描发现的漏洞。"
                        + "\"CVSS 评分\"为通用漏洞评分系统得分（0-10，越高越严重），"
                        + "\"受影响软件\"列可用于定位需修复的系统组件。")
                        .setFontSize(8).setFontColor(COLOR_GRAY));
                doc.add(cPara(" ").setHeight(4));

                Table vTable = new Table(UnitValue.createPercentArray(
                        new float[]{2, 1.3f, 0.8f, 3, 1.5f})).useAllAvailableWidth();
                pdfHeaderRow(vTable, "CVE 编号", "严重等级", "CVSS", "受影响软件", "状态");

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
                doc.add(cPara(String.format("（共 %d 个漏洞）", totalVulns))
                        .setFontSize(7).setFontColor(COLOR_GRAY).setTextAlignment(TextAlignment.RIGHT));
                doc.add(cPara(" ").setHeight(8));

                // Per-vulnerability details section
                doc.add(cPara("漏洞详细信息：").setFontSize(11).setBold().setFontColor(COLOR_DARK));
                doc.add(cPara(" ").setHeight(4));
                int vulnIdx = 0;
                for (Asset a : assets) {
                    for (AssetVulnerability v : vulnRepository.findByAssetIdWithCve(a.getId())) {
                        CveDatabase cve = v.getCveDatabase();
                        if (cve == null) continue;
                        vulnIdx++;
                        String header = String.format("%d. %s [%s] — CVSS %.1f",
                                vulnIdx, cve.getCveId(),
                                cve.getSeverity() != null ? cve.getSeverity().toUpperCase() : "N/A",
                                cve.getCvssScore() != null ? cve.getCvssScore() : 0.0);
                        doc.add(cPara(header).setFontSize(9).setBold()
                                .setFontColor(getSeverityColor(cve.getSeverity())));
                        doc.add(cPara("受影响资产: " + a.getIpAddress()
                                + (a.getHostname() != null ? " (" + a.getHostname() + ")" : ""))
                                .setFontSize(8).setFontColor(COLOR_GRAY));
                        if (cve.getDescription() != null) {
                            doc.add(cPara("描述: " + cve.getDescription())
                                    .setFontSize(8).setFontColor(COLOR_DARK));
                        }
                        if (cve.getFixSuggestion() != null) {
                            doc.add(cPara("修复建议: " + cve.getFixSuggestion())
                                    .setFontSize(8).setFontColor(new DeviceRgb(22, 163, 74)));
                        }
                        doc.add(cPara(" ").setHeight(2));
                    }
                }
            } else {
                doc.add(cPara("四、漏洞详情").setFontSize(15).setBold().setFontColor(COLOR_DARK));
                doc.add(cPara("（本次扫描未发现漏洞）").setFontSize(9).setFontColor(COLOR_GRAY));
            }

            // ═══ Section N+1: Honeypot Detection ═══
            int totalHoneypots = 0;
            for (Asset a : assets) {
                totalHoneypots += honeypotDetectionRepository.findByAssetId(a.getId()).size();
            }
            if (totalHoneypots > 0) {
                doc.add(cPara(hpTitle).setFontSize(15).setBold().setFontColor(COLOR_DARK));
                doc.add(cPara("以下列出通过服务指纹规则检测到的疑似蜜罐资产。"
                        + "蜜罐是安全研究人员或攻击者部署的诱捕系统，资产若被识别为蜜罐，"
                        + "表明该目标可能为陷阱系统，需谨慎对待。")
                        .setFontSize(8).setFontColor(COLOR_GRAY));
                doc.add(cPara(" ").setHeight(4));

                Table hpTable = new Table(UnitValue.createPercentArray(
                        new float[]{2, 2, 1.5f, 1.5f, 3})).useAllAvailableWidth();
                pdfHeaderRow(hpTable, "IP 地址", "蜜罐类型", "置信度", "检测方法", "匹配证据");

                boolean alt = false;
                for (Asset a : assets) {
                    List<HoneypotDetection> hds = honeypotDetectionRepository.findByAssetId(a.getId());
                    for (HoneypotDetection hd : hds) {
                        DeviceRgb bg = alt ? COLOR_LIGHT_BG : COLOR_WHITE;
                        pdfCell(hpTable, a.getIpAddress(), bg);
                        pdfCell(hpTable, hd.getHoneypotType() != null ? hd.getHoneypotType() : "-", bg);
                        pdfCell(hpTable, hd.getConfidence() != null ? hd.getConfidence() : "-", bg);
                        pdfCell(hpTable, hd.getDetectionMethod() != null ? hd.getDetectionMethod() : "-", bg);
                        String evidence = hd.getMatchEvidence() != null ? hd.getMatchEvidence() : "";
                        if (evidence.length() > 80) evidence = evidence.substring(0, 80) + "...";
                        pdfCell(hpTable, !evidence.isEmpty() ? evidence : "-", bg);
                        alt = !alt;
                    }
                }
                doc.add(hpTable);
                doc.add(cPara(String.format("（共检测到 %d 条蜜罐记录）", totalHoneypots))
                        .setFontSize(7).setFontColor(COLOR_GRAY).setTextAlignment(TextAlignment.RIGHT));
                doc.add(cPara(" ").setHeight(8));
            }

            doc.add(cPara(" ").setHeight(16));
            doc.add(dividerLine());
            doc.add(cPara("本报告由 ServerScout 自动生成 | "
                    + java.time.LocalDateTime.now().format(DTF))
                    .setFontSize(7).setFontColor(COLOR_GRAY)
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(cPara("报告数据来源于扫描任务执行结果，仅供参考。请结合实际情况进行安全评估。")
                    .setFontSize(6).setFontColor(COLOR_GRAY)
                    .setTextAlignment(TextAlignment.CENTER));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new ServiceException("PDF报告生成失败: " + e.getMessage(), e);
        }
    }

    // ═══════════════ Excel Report ═══════════════

    @Transactional(readOnly = true)
    public byte[] generateExcelReport(Long taskId) {
        try {
            ScanTask task = scanTaskRepository.findById(taskId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SCAN_TASK_NOT_FOUND, "ScanTask", taskId));
            List<Asset> assets = getTaskAssets(taskId);

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

            // Sheet 4: Web Fingerprint
            Sheet fpSheet = wb.createSheet("Web指纹");
            String[] fpCols = {"IP:端口", "HTTP状态", "服务器", "标题", "框架", "CMS", "CMS版本", "技术栈", "WAF", "响应摘要"};
            int[] fpWidths = {4500, 2500, 4500, 6000, 4000, 4000, 3000, 8000, 3500, 8000};
            createSheetHeader(fpSheet, fpCols, fpWidths, headerStyle, wb);
            int fpr = 1;
            for (Asset a : assets) {
                for (Port p : portRepository.findByAssetId(a.getId())) {
                    var wfOpt = webFingerprintRepository.findByPortId(p.getId());
                    if (wfOpt.isPresent()) {
                        var wf = wfOpt.get();
                        Row row = fpSheet.createRow(fpr++);
                        fillRow(row, new String[]{
                                a.getIpAddress() + ":" + p.getPortNumber(),
                                wf.getHttpStatus() != null ? String.valueOf(wf.getHttpStatus()) : "",
                                wf.getServerHeader() != null ? wf.getServerHeader() : "",
                                wf.getTitle() != null ? wf.getTitle() : "",
                                wf.getFrameworkName() != null ? wf.getFrameworkName() : "",
                                wf.getCmsName() != null ? wf.getCmsName() : "",
                                wf.getCmsVersion() != null ? wf.getCmsVersion() : "",
                                wf.getTechStack() != null ? wf.getTechStack() : "",
                                wf.getWafName() != null ? wf.getWafName() : "",
                                wf.getResponseSummary() != null ? wf.getResponseSummary() : "",
                        }, altRowStyle, fpr);
                    }
                }
            }
            freezeAndFilter(fpSheet, fpCols.length);

            // Sheet 5: SSL Certificates
            Sheet certSheet = wb.createSheet("SSL证书");
            String[] certCols = {"IP:端口", "Subject", "Issuer", "有效期自", "有效期至", "序列号", "SHA256指纹", "SAN", "签名算法", "密钥长度", "是否过期"};
            int[] certWidths = {4500, 10000, 10000, 5000, 5000, 5000, 10000, 8000, 4000, 3000, 3000};
            createSheetHeader(certSheet, certCols, certWidths, headerStyle, wb);
            int certR = 1;
            for (Asset a : assets) {
                for (Port p : portRepository.findByAssetId(a.getId())) {
                    var certOpt = sslCertificateRepository.findByPortId(p.getId());
                    if (certOpt.isPresent()) {
                        SslCertificate cert = certOpt.get();
                        Row row = certSheet.createRow(certR++);
                        fillRow(row, new String[]{
                                a.getIpAddress() + ":" + p.getPortNumber(),
                                cert.getSubject() != null ? cert.getSubject() : "",
                                cert.getIssuer() != null ? cert.getIssuer() : "",
                                cert.getNotBefore() != null ? cert.getNotBefore().toString() : "",
                                cert.getNotAfter() != null ? cert.getNotAfter().toString() : "",
                                cert.getSerialNumber() != null ? cert.getSerialNumber() : "",
                                cert.getFingerprintSha256() != null ? cert.getFingerprintSha256() : "",
                                cert.getSan() != null ? cert.getSan() : "",
                                cert.getSigAlg() != null ? cert.getSigAlg() : "",
                                cert.getKeySize() != null ? String.valueOf(cert.getKeySize()) : "",
                                Boolean.TRUE.equals(cert.getIsExpired()) ? "是" : "否",
                        }, altRowStyle, certR);
                    }
                }
            }
            freezeAndFilter(certSheet, certCols.length);

            // Sheet 6: Vulnerabilities
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

            // Sheet 5: Honeypot Detection
            Sheet hpSheet = wb.createSheet("蜜罐检测");
            String[] hpCols = {"IP地址", "蜜罐类型", "分类", "置信度", "检测方法", "匹配端口", "匹配证据", "规则名称"};
            int[] hpWidths = {4500, 4000, 3500, 3000, 3500, 2500, 10000, 6000};
            createSheetHeader(hpSheet, hpCols, hpWidths, headerStyle, wb);

            int hpr = 1;
            for (Asset a : assets) {
                for (HoneypotDetection hd : honeypotDetectionRepository.findByAssetId(a.getId())) {
                    Row row = hpSheet.createRow(hpr++);
                    String evidence = hd.getMatchEvidence() != null ? hd.getMatchEvidence() : "";
                    fillRow(row, new String[]{
                            a.getIpAddress(),
                            hd.getHoneypotType() != null ? hd.getHoneypotType() : "",
                            hd.getHoneypotCategory() != null ? hd.getHoneypotCategory() : "",
                            hd.getConfidence() != null ? hd.getConfidence() : "",
                            hd.getDetectionMethod() != null ? hd.getDetectionMethod() : "",
                            hd.getMatchedPort() != null ? String.valueOf(hd.getMatchedPort()) : "",
                            evidence,
                            hd.getRule() != null ? hd.getRule().getRuleName() : "",
                    }, altRowStyle, hpr);
                }
            }
            freezeAndFilter(hpSheet, hpCols.length);

            // Sheet 6: Stats
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
            throw new ServiceException("Excel报告生成失败: " + e.getMessage(), e);
        }
    }

    // ──────────── PDF Helpers ────────────

    private void addSeverityLegend(Document doc) {
        String[][] legend = {
                {"Critical (严重)", "需立即修复，可能导致系统完全被控制"},
                {"High (高危)", "存在较大安全风险，建议尽快修复"},
                {"Medium (中危)", "存在一定安全风险，建议安排修复"},
                {"Low (低危)", "风险较低，建议择机修复"},
        };
        DeviceRgb[] colors = {COLOR_CRITICAL, COLOR_HIGH, COLOR_MEDIUM, COLOR_LOW};
        for (int i = 0; i < legend.length; i++) {
            doc.add(cPara("  ■ " + legend[i][0] + " — " + legend[i][1])
                    .setFontSize(7).setFontColor(colors[i]));
        }
    }

    private void addDetailedStats(Document doc, ScanTask task, List<Asset> assets) {
        long critCount = 0, highCount = 0, mediumCount = 0, lowCount = 0;
        int webCount = 0;
        for (Asset a : assets) {
            for (AssetVulnerability v : vulnRepository.findByAssetIdWithCve(a.getId())) {
                CveDatabase cve = v.getCveDatabase();
                if (cve == null) continue;
                String sev = cve.getSeverity();
                if ("critical".equalsIgnoreCase(sev)) critCount++;
                else if ("high".equalsIgnoreCase(sev)) highCount++;
                else if ("medium".equalsIgnoreCase(sev)) mediumCount++;
                else if ("low".equalsIgnoreCase(sev)) lowCount++;
            }
            for (Port p : portRepository.findByAssetId(a.getId())) {
                if (Boolean.TRUE.equals(p.getIsWebService())) webCount++;
            }
        }

        doc.add(cPara("关键数据摘要：").setFontSize(9).setBold().setFontColor(COLOR_DARK));
        doc.add(cPara(String.format(
                "  资产总数: %d 个  |  开放端口: %d 个  |  Web 服务: %d 个  |  漏洞总数: %d 个",
                assets.size(), task.getTotalPorts(), webCount, critCount + highCount + mediumCount + lowCount))
                .setFontSize(8).setFontColor(COLOR_DARK));
        doc.add(cPara(String.format(
                "  严重(Critical): %d  |  高危(High): %d  |  中危(Medium): %d  |  低危(Low): %d",
                critCount, highCount, mediumCount, lowCount))
                .setFontSize(8).setFontColor(COLOR_DARK));
    }

    private DeviceRgb getSeverityColor(String severity) {
        if (severity == null) return COLOR_GRAY;
        switch (severity.toLowerCase()) {
            case "critical": return COLOR_CRITICAL;
            case "high": return COLOR_HIGH;
            case "medium": return COLOR_MEDIUM;
            case "low": return COLOR_LOW;
            default: return COLOR_GRAY;
        }
    }

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
                .add(cPara(label).setFontSize(9).setBold().setFontColor(COLOR_DARK))
                .setBorder(Border.NO_BORDER).setPadding(4));
        table.addCell(new com.itextpdf.layout.element.Cell()
                .add(cPara(value != null ? value : "-").setFontSize(9).setFontColor(COLOR_GRAY))
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
        card.add(cPara(label).setFontSize(8).setFontColor(COLOR_GRAY)
                .setTextAlignment(TextAlignment.CENTER));
        card.add(cPara(value).setFontSize(18).setBold()
                .setFontColor(accent).setTextAlignment(TextAlignment.CENTER));
        table.addCell(card);
    }

    private static final String[] CN_NUM = {"零","一","二","三","四","五","六","七","八","九","十"};

    private String numToChinese(int n) {
        if (n <= 10) return CN_NUM[n];
        if (n < 20) return "十" + CN_NUM[n % 10];
        return String.valueOf(n);
    }

    private LineSeparator dividerLine() {
        LineSeparator ls = new LineSeparator(new SolidLine(1f));
        ls.setWidth(UnitValue.createPercentValue(90));
        return ls;
    }

    private void pdfHeaderRow(Table table, String... headers) {
        for (String h : headers) {
            table.addHeaderCell(new com.itextpdf.layout.element.Cell()
                    .add(cPara(h).setFontSize(7).setBold().setFontColor(COLOR_WHITE))
                    .setBackgroundColor(COLOR_DARK)
                    .setTextAlignment(TextAlignment.CENTER).setPadding(5));
        }
    }

    private void pdfCell(Table table, String text, DeviceRgb bg) {
        table.addCell(new com.itextpdf.layout.element.Cell()
                .add(cPara(text).setFontSize(7).setFontColor(COLOR_DARK))
                .setBackgroundColor(bg).setPadding(4));
    }

    private void pdfCellCrit(Table table, String text, DeviceRgb bg) {
        int val = 0;
        try { val = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
        DeviceRgb color = val > 0 ? COLOR_CRITICAL : COLOR_DARK;
        table.addCell(new com.itextpdf.layout.element.Cell()
                .add(cPara(text).setFontSize(7).setFontColor(color).setBold())
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
                .add(cPara(severity).setFontSize(7).setBold().setFontColor(color))
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
