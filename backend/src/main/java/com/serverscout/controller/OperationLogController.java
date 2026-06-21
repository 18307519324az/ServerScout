package com.serverscout.controller;

import com.serverscout.dto.ApiResponse;
import com.serverscout.entity.OperationLog;
import com.serverscout.service.OperationLogService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operation-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OperationLogController {

    private final OperationLogService logService;

    @GetMapping
    public ApiResponse<Page<OperationLog>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OperationLog> result = logService.search(username, type, startTime, endTime, pageable);
        return ApiResponse.success(result);
    }

    @GetMapping("/user/{username}")
    public ApiResponse<Page<OperationLog>> byUser(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(logService.findByUser(username, pageable));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(Map.of(
                "total", logService.search(null, null, null, null,
                        PageRequest.of(0, 1)).getTotalElements(),
                "types", Map.of(
                        "LOGIN_SUCCESS", logService.search(null, "LOGIN_SUCCESS", null, null,
                                PageRequest.of(0, 1)).getTotalElements(),
                        "LOGIN_FAILED", logService.search(null, "LOGIN_FAILED", null, null,
                                PageRequest.of(0, 1)).getTotalElements(),
                        "API_CALL", logService.search(null, "API_CALL", null, null,
                                PageRequest.of(0, 1)).getTotalElements()
                )
        ));
    }

    @GetMapping("/export")
    public void export(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            HttpServletResponse response) throws IOException {

        List<OperationLog> logs = logService.findAllForExport(username, type, startTime, endTime);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.of("Asia/Shanghai"));

        if ("excel".equalsIgnoreCase(format)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=operation-logs.xlsx");

            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("操作日志");
                Row header = sheet.createRow(0);
                String[] cols = {"ID", "类型", "用户", "操作目标", "详情", "IP地址", "归属地", "请求方法", "请求URI", "状态码", "耗时(ms)", "时间"};
                for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

                int rowIdx = 1;
                for (OperationLog log : logs) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(log.getId() != null ? log.getId() : 0);
                    row.createCell(1).setCellValue(log.getOperationType() != null ? log.getOperationType() : "");
                    row.createCell(2).setCellValue(log.getUsername() != null ? log.getUsername() : "");
                    row.createCell(3).setCellValue(log.getTarget() != null ? log.getTarget() : "");
                    row.createCell(4).setCellValue(log.getDetail() != null ? log.getDetail() : "");
                    row.createCell(5).setCellValue(log.getIpAddress() != null ? log.getIpAddress() : "");
                    row.createCell(6).setCellValue(log.getGeoLocation() != null ? log.getGeoLocation() : "");
                    row.createCell(7).setCellValue(log.getRequestMethod() != null ? log.getRequestMethod() : "");
                    row.createCell(8).setCellValue(log.getRequestUri() != null ? log.getRequestUri() : "");
                    row.createCell(9).setCellValue(log.getStatusCode() != null ? log.getStatusCode() : 0);
                    row.createCell(10).setCellValue(log.getDurationMs() != null ? log.getDurationMs() : 0);
                    row.createCell(11).setCellValue(log.getCreatedAt() != null ? dtf.format(log.getCreatedAt()) : "");
                }
                wb.write(response.getOutputStream());
            }
        } else {
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=operation-logs.csv");
            // BOM for Excel UTF-8 recognition
            response.getOutputStream().write(0xEF);
            response.getOutputStream().write(0xBB);
            response.getOutputStream().write(0xBF);

            try (PrintWriter writer = response.getWriter()) {
                writer.println("ID,类型,用户,操作目标,详情,IP地址,归属地,请求方法,请求URI,状态码,耗时(ms),时间");
                for (OperationLog log : logs) {
                    writer.printf("%d,%s,%s,\"%s\",\"%s\",%s,%s,%s,%s,%d,%d,%s%n",
                            log.getId() != null ? log.getId() : 0,
                            csvEscape(log.getOperationType()),
                            csvEscape(log.getUsername()),
                            csvEscape(log.getTarget()),
                            csvEscape(log.getDetail()),
                            csvEscape(log.getIpAddress()),
                            csvEscape(log.getGeoLocation()),
                            csvEscape(log.getRequestMethod()),
                            csvEscape(log.getRequestUri()),
                            log.getStatusCode() != null ? log.getStatusCode() : 0,
                            log.getDurationMs() != null ? log.getDurationMs() : 0,
                            log.getCreatedAt() != null ? dtf.format(log.getCreatedAt()) : "");
                }
            }
        }
        response.flushBuffer();
    }

    private String csvEscape(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
