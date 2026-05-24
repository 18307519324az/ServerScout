-- ============================================
-- ServerScout 数据库初始化脚本
-- 用法: 在 phpMyAdmin SQL 控制台直接执行本文件全部内容
--       或: mysql -u root -p < serverscout-init.sql
-- ============================================

CREATE DATABASE IF NOT EXISTS serverscout CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE serverscout;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 用户表
-- ============================================
CREATE TABLE IF NOT EXISTS `app_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `gender` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER',
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 默认用户: admin / admin123
INSERT INTO `app_user` VALUES
(1, NOW(), 'admin@serverscout.local', b'1', 'MALE', '系统管理员',
 '$2a$10$nnhiLO7xye3kHxC2iKDRBu8X3/WHtuoLrJU6Bx0YrE8GqdGJ3kwyW', 'ADMIN', 'admin'),
(2, NOW(), 'demo@serverscout.local', b'1', 'MALE', '演示用户',
 '$2a$10$gpiD1t9lFSk7zrore1bKkefIyRMoJvPTLhha2fZczhHkru9XAplQK', 'USER', 'demo_user');

-- ============================================
-- 系统配置表
-- ============================================
CREATE TABLE IF NOT EXISTS `system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_value` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `system_config` VALUES
(1,'daily-scan-enabled','false',NULL),
(2,'daily-scan-target','192.168.1.0/24',NULL),
(3,'daily-scan-cron','0 0 2 * * ?',NULL),
(4,'weekly-scan-enabled','false',NULL),
(5,'weekly-scan-target','192.168.1.0/24',NULL),
(6,'weekly-scan-cron','0 0 3 * * SUN',NULL),
(7,'email-enabled','false',NULL),
(8,'email-recipient','',NULL),
(9,'email-smtp-host','',NULL),
(10,'email-smtp-port','465',NULL),
(11,'email-smtp-username','',NULL),
(12,'email-smtp-password','',NULL),
(13,'email-smtp-ssl','true',NULL),
(14,'nmap-path','nmap',NULL),
(15,'nuclei-path','nuclei',NULL),
(16,'webhook-dingtalk','',NULL),
(17,'webhook-feishu','',NULL),
(18,'webhook-wecom','',NULL),
(19,'censys-api-id','',NULL),
(20,'censys-api-secret','',NULL),
(21,'virustotal-api-key','',NULL);

-- ============================================
-- 扫描任务表
-- ============================================
CREATE TABLE IF NOT EXISTS `scan_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `completed_at` datetime(6) DEFAULT NULL,
  `config_json` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enable_fingerprint` bit(1) DEFAULT NULL,
  `enable_vuln_scan` bit(1) DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `port_range` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `progress` int NOT NULL DEFAULT 0,
  `scan_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending',
  `target_range` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_assets` int DEFAULT 0,
  `total_ports` int DEFAULT 0,
  `enable_crawler` bit(1) DEFAULT NULL,
  `max_retries` int NOT NULL DEFAULT 0,
  `retry_count` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 资产表
-- ============================================
CREATE TABLE IF NOT EXISTS `asset` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `critical_vuln_count` int DEFAULT 0,
  `discovered_at` datetime(6) NOT NULL,
  `first_seen_time` datetime(6) DEFAULT NULL,
  `hostname` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `hostname_aliases` json DEFAULT NULL,
  `ip_address` varchar(45) COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_scan_time` datetime(6) DEFAULT NULL,
  `mac_address` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `open_port_count` int DEFAULT 0,
  `os_fingerprint` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `os_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tags` json DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `task_id` bigint DEFAULT NULL,
  `honeypot_confidence` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `honeypot_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_honeypot` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY (`ip_address`),
  KEY (`task_id`),
  CONSTRAINT FOREIGN KEY (`task_id`) REFERENCES `scan_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 端口表
-- ============================================
CREATE TABLE IF NOT EXISTS `port` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `banner` text COLLATE utf8mb4_unicode_ci,
  `first_seen_at` datetime(6) NOT NULL,
  `is_web_service` bit(1) DEFAULT NULL,
  `port_number` int NOT NULL,
  `protocol` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT 'tcp',
  `service_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `service_product` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `service_version` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `state` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'open',
  `asset_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY (`asset_id`,`port_number`,`protocol`),
  CONSTRAINT FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Web 指纹表
-- ============================================
CREATE TABLE IF NOT EXISTS `web_fingerprint` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `port_id` bigint NOT NULL,
  `http_status` int DEFAULT NULL,
  `server_header` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `framework_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `framework_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cms_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cms_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `waf_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tech_stack` text COLLATE utf8mb4_unicode_ci,
  `title` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `favicon_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `body_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `response_headers` text COLLATE utf8mb4_unicode_ci,
  `response_summary` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY (`port_id`),
  CONSTRAINT FOREIGN KEY (`port_id`) REFERENCES `port` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- SSL 证书表
-- ============================================
CREATE TABLE IF NOT EXISTS `ssl_certificate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `port_id` bigint NOT NULL,
  `subject` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `issuer` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fingerprint_sha256` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `not_before` datetime(6) DEFAULT NULL,
  `not_after` datetime(6) DEFAULT NULL,
  `san` text COLLATE utf8mb4_unicode_ci,
  `sig_alg` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `key_size` int DEFAULT NULL,
  `is_expired` bit(1) DEFAULT NULL,
  `serial_number` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `discovered_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY (`port_id`),
  CONSTRAINT FOREIGN KEY (`port_id`) REFERENCES `port` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- CVE 数据库
-- ============================================
CREATE TABLE IF NOT EXISTS `cve_database` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `affected_software` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `affected_version_range` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cve_id` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cvss_score` decimal(3,1) DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `fix_suggestion` text COLLATE utf8mb4_unicode_ci,
  `last_updated` datetime(6) NOT NULL,
  `publication_date` date DEFAULT NULL,
  `severity` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY (`cve_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 漏洞关联表
-- ============================================
CREATE TABLE IF NOT EXISTS `asset_vulnerability` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `asset_id` bigint NOT NULL,
  `cve_id` bigint NOT NULL,
  `port_id` bigint DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'open',
  `reproduction_steps` text COLLATE utf8mb4_unicode_ci,
  `discovered_at` datetime(6) NOT NULL,
  `fixed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY (`asset_id`,`cve_id`),
  KEY (`cve_id`),
  KEY (`port_id`),
  CONSTRAINT FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE CASCADE,
  CONSTRAINT FOREIGN KEY (`cve_id`) REFERENCES `cve_database` (`id`),
  CONSTRAINT FOREIGN KEY (`port_id`) REFERENCES `port` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 扫描-资产映射表
-- ============================================
CREATE TABLE IF NOT EXISTS `scan_asset_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scan_task_id` bigint NOT NULL,
  `asset_id` bigint NOT NULL,
  `scan_time` datetime(6) NOT NULL,
  `is_new` bit(1) DEFAULT b'0',
  `ports_found` int DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY (`scan_task_id`),
  KEY (`asset_id`),
  CONSTRAINT FOREIGN KEY (`scan_task_id`) REFERENCES `scan_task` (`id`) ON DELETE CASCADE,
  CONSTRAINT FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 子域名表
-- ============================================
CREATE TABLE IF NOT EXISTS `subdomain` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `domain` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subdomain` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ip_address` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'dns',
  `asset_id` bigint DEFAULT NULL,
  `first_seen_time` datetime(6) NOT NULL,
  `last_seen_time` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY (`subdomain`,`source`),
  KEY (`asset_id`),
  CONSTRAINT FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 操作日志表
-- ============================================
CREATE TABLE IF NOT EXISTS `operation_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `detail` text COLLATE utf8mb4_unicode_ci,
  `ip_address` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `geo_location` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_method` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_uri` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status_code` int DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_oplog_user` (`username`),
  KEY `idx_oplog_time` (`created_at`),
  KEY `idx_oplog_type` (`operation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 扫描策略插件表
-- ============================================
CREATE TABLE IF NOT EXISTS `scan_strategy_plugins` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `command_template` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `finding_regex` text COLLATE utf8mb4_unicode_ci,
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_parser` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT 'line',
  `scan_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY (`name`),
  UNIQUE KEY (`scan_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 漏洞状态日志表
-- ============================================
CREATE TABLE IF NOT EXISTS `vuln_status_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `vulnerability_id` bigint NOT NULL,
  `old_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `new_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `note` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT FOREIGN KEY (`vulnerability_id`) REFERENCES `asset_vulnerability` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- URL 爬虫表
-- ============================================
CREATE TABLE IF NOT EXISTS `crawled_urls` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint DEFAULT NULL,
  `asset_id` bigint NOT NULL,
  `port_id` bigint DEFAULT NULL,
  `url` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL,
  `path` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `http_status` int DEFAULT NULL,
  `content_type` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `body_text` mediumtext COLLATE utf8mb4_unicode_ci,
  `links_found` int DEFAULT 0,
  `crawl_depth` int DEFAULT 0,
  `response_time_ms` bigint DEFAULT NULL,
  `is_dynamic` bit(1) DEFAULT b'0',
  `screenshot_path` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `crawled_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_crawled_task` (`task_id`),
  KEY `idx_crawled_asset` (`asset_id`),
  KEY `idx_crawled_port` (`port_id`),
  CONSTRAINT FOREIGN KEY (`task_id`) REFERENCES `scan_task` (`id`) ON DELETE SET NULL,
  CONSTRAINT FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE SET NULL,
  CONSTRAINT FOREIGN KEY (`port_id`) REFERENCES `port` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 蜜罐规则表
-- ============================================
CREATE TABLE IF NOT EXISTS `honeypot_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rule_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `honeypot_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `honeypot_category` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `match_field` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `match_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'regex',
  `match_pattern` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `confidence` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'medium',
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 蜜罐检测结果表
-- ============================================
CREATE TABLE IF NOT EXISTS `honeypot_detection` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `asset_id` bigint NOT NULL,
  `rule_id` bigint DEFAULT NULL,
  `honeypot_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `honeypot_category` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `match_evidence` text COLLATE utf8mb4_unicode_ci,
  `confidence` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT 'medium',
  `detection_method` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT 'rule',
  `matched_port` int DEFAULT NULL,
  `matched_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY (`asset_id`),
  KEY (`rule_id`),
  CONSTRAINT FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`) ON DELETE CASCADE,
  CONSTRAINT FOREIGN KEY (`rule_id`) REFERENCES `honeypot_rule` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 初始化完成
-- 默认管理员账号: admin / admin123
-- 默认普通用户:   demo_user / demo123
-- ============================================
