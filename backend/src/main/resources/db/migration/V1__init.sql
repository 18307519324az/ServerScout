-- Flyway V1: Initial schema for ServerScout
-- Generated from JPA entities (Spring Boot 3.3 / Hibernate 6.5)

CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(256) NOT NULL,
    name VARCHAR(64),
    gender VARCHAR(16),
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    email VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL UNIQUE,
    config_value TEXT,
    description VARCHAR(256),
    updated_at DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scan_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    target_range VARCHAR(512) NOT NULL,
    scan_type VARCHAR(32),
    port_range VARCHAR(64),
    enable_fingerprint BOOLEAN,
    enable_vuln_scan BOOLEAN,
    enable_crawler BOOLEAN,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    progress INTEGER NOT NULL DEFAULT 0,
    total_assets INTEGER DEFAULT 0,
    total_ports INTEGER DEFAULT 0,
    config_json TEXT,
    error_message TEXT,
    started_at DATETIME(6),
    completed_at DATETIME(6),
    created_by VARCHAR(64),
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS asset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT,
    ip_address VARCHAR(45) NOT NULL,
    hostname VARCHAR(256),
    hostname_aliases TEXT,
    mac_address VARCHAR(32),
    os_fingerprint VARCHAR(256),
    os_version VARCHAR(64),
    status VARCHAR(32),
    open_port_count INTEGER DEFAULT 0,
    critical_vuln_count INTEGER DEFAULT 0,
    tags TEXT DEFAULT '[]',
    last_scan_time DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_asset_task FOREIGN KEY (task_id) REFERENCES scan_task(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_asset_ip ON asset(ip_address);

CREATE TABLE IF NOT EXISTS port (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    port_number INTEGER NOT NULL,
    protocol VARCHAR(8) DEFAULT 'tcp',
    service_name VARCHAR(128),
    service_version VARCHAR(128),
    service_product VARCHAR(128),
    state VARCHAR(16) DEFAULT 'open',
    banner TEXT,
    is_web_service BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_port_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_port_asset ON port(asset_id);

CREATE TABLE IF NOT EXISTS web_fingerprint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    port_id BIGINT UNIQUE,
    http_status INTEGER,
    server_header VARCHAR(256),
    framework_name VARCHAR(128),
    framework_version VARCHAR(64),
    cms_name VARCHAR(128),
    cms_version VARCHAR(64),
    waf_name VARCHAR(128),
    tech_stack TEXT,
    title VARCHAR(512),
    favicon_hash VARCHAR(64),
    body_hash VARCHAR(64),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_wf_port FOREIGN KEY (port_id) REFERENCES port(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ssl_certificate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    port_id BIGINT,
    subject VARCHAR(512),
    issuer VARCHAR(512),
    fingerprint_sha256 VARCHAR(128),
    not_before DATETIME(6),
    not_after DATETIME(6),
    san TEXT,
    sig_alg VARCHAR(64),
    key_size INTEGER,
    is_expired BOOLEAN DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ssl_port FOREIGN KEY (port_id) REFERENCES port(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cve_database (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cve_id VARCHAR(32) NOT NULL UNIQUE,
    description TEXT,
    severity VARCHAR(16),
    cvss_score DECIMAL(3,1),
    affected_software VARCHAR(256),
    affected_version VARCHAR(128),
    fix_suggestion TEXT,
    publication_date DATE,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS asset_vulnerability (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    cve_id BIGINT,
    status VARCHAR(32) DEFAULT 'open',
    reproduction_steps TEXT,
    discovered_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_av_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE CASCADE,
    CONSTRAINT fk_av_cve FOREIGN KEY (cve_id) REFERENCES cve_database(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scan_asset_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scan_task_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    scan_time DATETIME(6),
    is_new BOOLEAN DEFAULT FALSE,
    ports_found INTEGER DEFAULT 0,
    CONSTRAINT fk_sam_task FOREIGN KEY (scan_task_id) REFERENCES scan_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_sam_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_sam_task ON scan_asset_mapping(scan_task_id);
CREATE INDEX idx_sam_asset ON scan_asset_mapping(asset_id);

CREATE TABLE IF NOT EXISTS subdomain (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain VARCHAR(256) NOT NULL,
    subdomain VARCHAR(512) NOT NULL,
    ip_address VARCHAR(45),
    source VARCHAR(64),
    asset_id BIGINT,
    first_seen_time DATETIME(6) NOT NULL,
    last_seen_time DATETIME(6) NOT NULL,
    CONSTRAINT fk_sub_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_sub_domain ON subdomain(domain);

CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    target VARCHAR(256),
    detail TEXT,
    ip_address VARCHAR(64),
    geo_location VARCHAR(128),
    user_agent VARCHAR(512),
    request_method VARCHAR(8),
    request_uri VARCHAR(256),
    status_code INTEGER,
    duration_ms BIGINT,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_oplog_user ON operation_log(username);
CREATE INDEX idx_oplog_time ON operation_log(created_at);
CREATE INDEX idx_oplog_type ON operation_log(operation_type);

CREATE TABLE IF NOT EXISTS scan_strategy_plugin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    scan_type VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(512),
    command_template TEXT NOT NULL,
    result_parser VARCHAR(32) DEFAULT 'line',
    finding_regex VARCHAR(512),
    enabled BOOLEAN DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS vuln_status_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vulnerability_id BIGINT NOT NULL,
    old_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    operator VARCHAR(64),
    remark TEXT,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_vsl_vuln FOREIGN KEY (vulnerability_id) REFERENCES asset_vulnerability(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crawled_urls (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT,
    asset_id BIGINT,
    port_id BIGINT,
    url VARCHAR(2048) NOT NULL,
    path VARCHAR(1024),
    http_status INTEGER,
    content_type VARCHAR(128),
    title VARCHAR(512),
    body_text MEDIUMTEXT,
    links_found INTEGER DEFAULT 0,
    crawl_depth INTEGER DEFAULT 0,
    response_time_ms BIGINT,
    is_dynamic BOOLEAN DEFAULT FALSE,
    screenshot_path VARCHAR(256),
    crawled_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_cu_task FOREIGN KEY (task_id) REFERENCES scan_task(id) ON DELETE SET NULL,
    CONSTRAINT fk_cu_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE SET NULL,
    CONSTRAINT fk_cu_port FOREIGN KEY (port_id) REFERENCES port(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_cu_task ON crawled_urls(task_id);
CREATE INDEX idx_cu_asset ON crawled_urls(asset_id);
