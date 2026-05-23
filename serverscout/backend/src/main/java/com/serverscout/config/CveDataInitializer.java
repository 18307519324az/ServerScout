package com.serverscout.config;

import com.serverscout.entity.CveDatabase;
import com.serverscout.repository.CveDatabaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@Order(1)
public class CveDataInitializer implements CommandLineRunner {

    private final CveDatabaseRepository cveRepository;

    public CveDataInitializer(CveDatabaseRepository cveRepository) {
        this.cveRepository = cveRepository;
    }

    @Override
    public void run(String... args) {
        long existingCount = cveRepository.count();
        if (existingCount > 0) {
            log.info("CVE database already has {} entries, checking for new ones...", existingCount);
        }

        List<CveDatabase> seeds = List.of(
            // ==================== Web Servers ====================
            cve("CVE-2021-41773", "Apache HTTPD 路径穿越 RCE", "critical", 9.8f,
                "Apache HTTP Server", "2.4.49",
                "1) 立即检查Apache版本: httpd -v; 2) 确认版本为2.4.49则立即停止httpd服务; 3) 升级: apt-get install apache2=2.4.51 / yum update httpd; 4) 检查access.log中是否有/.%2e/路径的可疑请求; 5) 重启httpd后验证版本"),
            cve("CVE-2021-42013", "Apache HTTPD 路径穿越 RCE (绕过)", "critical", 9.8f,
                "Apache HTTP Server", "2.4.49 - 2.4.50",
                "1) 确认Apache版本: httpd -v; 2) 如为2.4.49~2.4.50立即升级到2.4.51+; 3) 检查日志中是否有%%32%65这类双重编码的可疑请求; 4) 升级后重启httpd; 5) 临时缓解: 禁用CGI模块(如不需要)"),
            cve("CVE-2019-0211", "Apache HTTPD 权限提升", "high", 7.8f,
                "Apache HTTP Server", "2.4.17 - 2.4.38",
                "1) 以非root用户运行: ps aux | grep httpd 查看运行用户; 2) 升级到Apache 2.4.39+; 3) 检查/etc/apache2或/etc/httpd下配置文件权限是否为root; 4) 运行: find / -user apache -perm /6000 检查SUID文件"),
            cve("CVE-2021-40438", "Apache HTTPD SSRF", "high", 7.7f,
                "Apache HTTP Server", "2.4.48",
                "1) 确认mod_proxy是否启用: httpd -M | grep proxy; 2) 升级到Apache 2.4.49+; 3) 如无法升级,限制ProxyPass目标为内网地址; 4) 检查proxy访问日志中有无可疑外部URL请求"),

            // ==================== Apache Tomcat ====================
            cve("CVE-2020-1938", "Apache Tomcat AJP 文件包含漏洞 (Ghostcat)", "high", 7.5f,
                "Apache Tomcat", "6.x, 7.0 - 7.0.99, 8.5.0 - 8.5.50, 9.0.0 - 9.0.30",
                "1) 检查server.xml中AJP Connector是否开启(端口8009); 2) 如不需要AJP则注释掉该Connector; 3) 如需使用,添加secretRequired=\"true\"和secret属性; 4) 升级Tomcat到7.0.100+/8.5.51+/9.0.31+; 5) 限制8009端口仅回环地址访问"),
            cve("CVE-2017-12615", "Apache Tomcat PUT 方法任意文件上传", "high", 8.1f,
                "Apache Tomcat", "7.0.0 - 7.0.81",
                "1) 检查web.xml中DefaultServlet的readonly参数是否为true; 2) 如为false则立即改为true; 3) 升级Tomcat到7.0.82+; 4) 扫描webapps目录下是否有不明.jsp/.jspx文件; 5) 检查access日志中PUT方法的请求"),
            cve("CVE-2017-12617", "Apache Tomcat 远程代码执行", "high", 8.1f,
                "Apache Tomcat", "7.0.0 - 7.0.80",
                "1) 确认Tomcat版本: catalina.sh version; 2) 升级到7.0.81+; 3) 检查webapps目录是否有不明JSP文件; 4) 扫描access日志中是否有.jsp%20或.jsp::$DATA的PUT请求"),
            cve("CVE-2022-45143", "Apache Tomcat JsonErrorReportValve 信息泄露", "high", 7.5f,
                "Apache Tomcat", "8.5.83",
                "1) 检查server.xml中是否有JsonErrorReportValve配置; 2) 如受影响版本则移除该Valve或升级到8.5.84+; 3) 检查返回的JSON错误信息是否包含敏感路径信息"),

            // ==================== Java Web Frameworks ====================
            cve("CVE-2021-44228", "Apache Log4j2 JNDI 远程代码执行漏洞 (Log4Shell)", "critical", 10.0f,
                "Apache Log4j2", "2.0-beta9 - 2.14.1",
                "1) 立即排查所有Java项目: find / -name 'log4j-core-*.jar' 2>/dev/null; 2) 检查pom.xml/build.gradle中log4j版本; 3) 紧急: 设置JVM参数 -Dlog4j2.formatMsgNoLookups=true; 4) 设置LOG4J_FORMAT_MSG_NO_LOOKUPS=true环境变量; 5) 升级到Log4j 2.17.0+; 6) 审计过去30天的出站DNS查询日志"),
            cve("CVE-2022-22965", "Spring Framework RCE 漏洞 (Spring4Shell)", "critical", 9.8f,
                "Spring Framework", "5.3.0 - 5.3.17, 5.2.0 - 5.2.19",
                "1) 检查项目中spring-beans版本: mvn dependency:tree | grep spring-beans; 2) 升级到Spring 5.3.18+/5.2.20+; 3) 若无法立即升级,在@ControllerAdvice中添加@InitBinder禁止直接绑定class属性; 4) 检查Tomcat access日志是否有class.module.classLoader开头的请求"),
            cve("CVE-2022-22947", "Spring Cloud Gateway 代码注入漏洞", "critical", 9.8f,
                "Spring Cloud Gateway", "3.1.0, 3.0.0 - 3.0.6",
                "1) 立即关闭Gateway Actuator端点对外暴露; 2) 升级到Gateway 3.1.1+/3.0.7+; 3) 检查/actuator/gateway/routes是否有不明路由; 4) 审计所有通过Gateway的请求日志"),
            cve("CVE-2022-22963", "Spring Cloud Function SpEL RCE", "critical", 9.8f,
                "Spring Cloud Function", "3.1.6, 3.2.2",
                "1) 检查项目中spring-cloud-function版本; 2) 升级到3.1.7+/3.2.3+; 3) 检查HTTP头中是否有spring.cloud.function.routing-expression的可疑请求; 4) 如不使用Function Routing功能则禁用"),
            cve("CVE-2023-20863", "Spring Framework DoS 漏洞", "high", 7.5f,
                "Spring Framework", "5.2.0 - 6.0.7",
                "1) 检查Spring版本: mvn dependency:tree | grep spring; 2) 升级到Spring 6.0.8+; 3) 为Spring应用配置WAF限制大请求体; 4) 设置server.max-http-header-size防止头部溢出攻击"),

            // ==================== Apache Struts ====================
            cve("CVE-2017-5638", "Apache Struts2 Jakarta 解析器 RCE", "critical", 10.0f,
                "Apache Struts2", "2.3.5 - 2.3.31, 2.5.0 - 2.5.10",
                "1) 立即检查Struts版本: struts2-core-*.jar; 2) 查看access日志中Content-Type头是否有%{开头的OGNL表达式; 3) 紧急升级到2.3.32+/2.5.10.1+; 4) 检查服务器是否有可疑进程监听外网端口"),
            cve("CVE-2018-11776", "Apache Struts2 命名空间 RCE", "critical", 9.8f,
                "Apache Struts2", "2.3 - 2.3.34, 2.5 - 2.5.16",
                "1) 检查所有struts.xml中namespace配置是否为空或通配符; 2) 确保alwaysSelectFullNamespace为true; 3) 升级到2.3.35+/2.5.17+; 4) 检查URL参数中是否有${开头的OGNL表达式注入尝试"),
            cve("CVE-2017-9791", "Apache Struts2 插件 RCE", "critical", 9.8f,
                "Apache Struts2", "2.3.x, 2.5.x < 2.5.13",
                "1) 排查项目是否使用REST/JSON插件; 2) 升级到Struts 2.5.13+; 3) 检查REST API请求中是否有OGNL注入payload; 4) 在不使用REST插件的项目中移除struts2-rest-plugin"),

            // ==================== Apache Commons ====================
            cve("CVE-2022-42889", "Apache Commons Text 变量插值 RCE (Text4Shell)", "critical", 9.8f,
                "Apache Commons Text", "1.5.0 - 1.9.0",
                "1) 检查项目依赖: mvn dependency:tree | grep commons-text; 2) 升级到Commons Text 1.10.0+; 3) 排查代码中所有StringSubstitutor/StrSubstitutor的使用; 4) 确保不接收用户输入作为插值表达式参数"),
            cve("CVE-2022-41852", "Apache Commons JXPath RCE", "critical", 9.8f,
                "Apache Commons JXPath", "< 1.3-rc2",
                "1) 检查pom.xml: grep commons-jxpath; 2) 如无使用JXPath则直接移除该依赖; 3) 如有使用则升级并确保JXPathContext.getValue不接收外部输入; 4) 替换为安全的XPath实现如javax.xml.xpath"),

            // ==================== Jackson / JSON ====================
            cve("CVE-2020-36179", "FasterXML jackson-databind 反序列化 RCE", "critical", 9.8f,
                "FasterXML jackson-databind", "< 2.9.10.8",
                "1) 检查jackson版本: mvn dependency:tree | grep jackson-databind; 2) 升级到2.9.10.8+/2.12.0+; 3) 代码审计: 检查所有@RequestBody和ObjectMapper.readValue()是否启用defaultTyping; 4) 迁移到Jackson 2.10+默认禁用defaultTyping"),
            cve("CVE-2020-26217", "XStream 反序列化 RCE", "critical", 9.8f,
                "XStream", "< 1.4.14",
                "1) 检查项目中XStream版本; 2) 升级到1.4.14+; 3) 对XStream实例设置安全类型白名单: stream.allowTypes(new Class[]{...}); 4) 如非必要,替换为Jackson/Gson等安全序列化方案"),

            // ==================== Oracle WebLogic ====================
            cve("CVE-2019-2725", "Oracle WebLogic Server 反序列化 RCE", "critical", 9.8f,
                "Oracle WebLogic Server", "10.3.6.0.0, 12.1.3.0.0, 12.2.1.3.0",
                "1) 登录Oracle Support下载对应版本的CPU补丁; 2) 关闭WLS-ASYNC响应服务; 3) 禁止外部访问bea_wls_internal测试页面; 4) 升级WebLogic到最新PSU补丁集; 5) 在WAF层拦截包含invoke的SOAP请求"),
            cve("CVE-2020-14882", "Oracle WebLogic Server Console RCE", "critical", 9.8f,
                "Oracle WebLogic Server", "10.3.6.0.0 - 14.1.1.0.0",
                "1) 立即禁止/console目录的外部访问; 2) 下载Oracle 2020年10月CPU补丁; 3) 检查console访问日志中是否有handle=com.tangosol.coherence.mvel2.sh.ShellSession的可疑请求; 4) 升级到WebLogic 14.1.1.0.0+并安装最新补丁"),
            cve("CVE-2017-10271", "Oracle WebLogic Server WLS RCE", "critical", 9.8f,
                "Oracle WebLogic Server", "10.3.6.0.0, 12.1.3.0.0, 12.2.1.1.0, 12.2.1.2.0",
                "1) 立即禁用/wls-wsat/路径的外部访问; 2) 下载Oracle 2017年10月CPU补丁; 3) 检查WLS日志是否有Coherence反序列化异常; 4) 临时缓解: 在WAF禁止访问/wls-wsat/路径"),
            cve("CVE-2020-2555", "Oracle Coherence 反序列化 RCE", "critical", 9.8f,
                "Oracle Coherence", "3.7.1.17, 12.1.3.0.0, 12.2.1.3.0, 12.2.1.4.0",
                "1) 确认WebLogic是否使用Coherence集群功能; 2) 下载Oracle 2020年1月CPU补丁; 3) 如不需要Coherence则关闭; 4) 限制T3协议端口(默认7001)的外部访问"),

            // ==================== Apache Shiro ====================
            cve("CVE-2016-4437", "Apache Shiro 反序列化漏洞 (Shiro-550)", "critical", 9.8f,
                "Apache Shiro", "< 1.2.5",
                "1) 检查Shiro版本: grep shiro pom.xml; 2) 升级到1.2.5+; 3) 更换默认的AES加密密钥(不要使用kPH+bIxk5D2deZiIxcaaaA==); 4) 启用RememberMe的严格签名验证"),

            // ==================== CMS: WordPress ====================
            cve("CVE-2022-21661", "WordPress WP_Query SQL 注入漏洞", "high", 7.5f,
                "WordPress", "5.8.2 之前版本",
                "1) 登录WordPress后台→更新页面→检查当前版本; 2) 一键升级到WordPress 5.8.3+; 3) 如无法自动更新: 手动下载https://wordpress.org/latest.zip替换; 4) 检查数据库wp_posts表是否有异常的SQL查询记录"),
            cve("CVE-2021-29447", "WordPress Media Library XML 解析 SSRF", "high", 7.7f,
                "WordPress", "4.7 - 5.7.1",
                "1) 升级到WordPress 5.7.2+; 2) 如使用PHP 8需另外检查XML解析器兼容性; 3) 检查上传目录是否有不明.xml文件和远程资源请求; 4) 在服务器防火墙禁止Web服务主动发起外网请求"),
            cve("CVE-2020-35489", "WordPress Contact Form 7 插件文件上传 RCE", "critical", 9.8f,
                "WordPress Contact Form 7", "< 5.3.2", "升级 Contact Form 7 到 5.3.2+"),
            cve("CVE-2019-9978", "WordPress Social Warfare 插件 XSS RCE", "critical", 9.8f,
                "WordPress Social Warfare", "< 3.5.4",
                "1) 登录WordPress后台; 2) 进入插件页面; 3) 将Social Warfare升级到3.5.4+; 4) 如无法升级则禁用该插件"),
            cve("CVE-2022-29455", "WordPress Elementor 插件 XSS 漏洞", "medium", 5.4f,
                "WordPress Elementor", "< 3.5.6",
                "1) 登录WordPress后台; 2) 进入插件页面; 3) 将Elementor升级到3.5.6+; 4) 检查所有Elementor创建的页面是否被注入恶意脚本"),
            cve("CVE-2023-32243", "WordPress Essential Addons 权限提升", "critical", 9.8f,
                "WordPress Essential Addons", "5.4.1 - 5.7.1",
                "1) 立即升级到5.7.2+; 2) 检查近期新建的管理员账户; 3) 审计所有登录日志; 4) 如发现可疑账户立即删除"),

            // ==================== CMS: Drupal ====================
            cve("CVE-2018-7600", "Drupal 远程代码执行漏洞 (Drupalgeddon2)", "critical", 9.8f,
                "Drupal", "6.x, 7.x, 8.x < 8.5.1", "升级到 Drupal 8.5.1+"),
            cve("CVE-2019-6340", "Drupal REST 模块 RCE", "critical", 9.8f,
                "Drupal", "8.5.x, 8.6.x < 8.6.10", "升级到 Drupal 8.6.10+"),

            // ==================== Atlassian ====================
            cve("CVE-2021-26084", "Atlassian Confluence OGNL 注入漏洞", "critical", 9.8f,
                "Atlassian Confluence", "< 6.13.23, 6.14.0 - 7.11.6", "升级到 Confluence 7.12.3+"),
            cve("CVE-2022-26134", "Atlassian Confluence OGNL 注入漏洞", "critical", 9.8f,
                "Atlassian Confluence", "1.3.0 - 7.4.17, 7.13.0 - 7.13.7, 7.14.0 - 7.14.3",
                "升级到 Confluence 7.4.18+"),
            cve("CVE-2023-22527", "Atlassian Confluence OGNL 注入 RCE", "critical", 10.0f,
                "Atlassian Confluence", "8.0.x - 8.5.3", "升级到 Confluence 8.5.4+"),
            cve("CVE-2024-21683", "Atlassian Confluence 代码注入 RCE", "critical", 9.8f,
                "Atlassian Confluence", "< 7.19.17, < 8.5.5", "升级到 Confluence 8.5.5+"),
            cve("CVE-2022-36804", "Atlassian Bitbucket Server RCE", "critical", 8.8f,
                "Atlassian Bitbucket Server", "6.10.17 - 8.3.2", "升级到 Bitbucket 8.3.3+"),

            // ==================== Jenkins ====================
            cve("CVE-2018-1000861", "Jenkins Stapler RCE", "critical", 9.8f,
                "Jenkins", "< 2.153, < 2.138.3 LTS", "升级到 Jenkins 2.153+ / 2.138.3+ LTS"),
            cve("CVE-2024-23897", "Jenkins CLI 任意文件读取", "high", 7.5f,
                "Jenkins", "2.441 - 2.442", "升级到 Jenkins 2.443+"),

            // ==================== GitLab / DevOps ====================
            cve("CVE-2021-22205", "GitLab CE/EE 远程代码执行", "critical", 10.0f,
                "GitLab", "11.9.0 - 13.8.8, 13.9.0 - 13.9.6, 13.10.0 - 13.10.3",
                "升级到 GitLab 13.10.4+"),

            // ==================== PHP Ecosystem ====================
            cve("CVE-2019-11043", "PHP-FPM fastcgi RCE", "critical", 9.8f,
                "PHP FPM", "< 7.1.33, < 7.2.24, < 7.3.11",
                "1) 确认当前PHP-FPM版本: php-fpm -v; 2) 非7.1/7.2/7.3分支则排查配置; 3) nginx用户: 在fastcgi_params中设置fastcgi_split_path_info防范; 4) 升级到PHP 7.3.11+/7.2.24+/7.1.33+"),
            cve("CVE-2017-9841", "PHPUnit eval-stdin.php RCE", "critical", 9.8f,
                "PHPUnit", "< 4.8.28, < 5.6.3",
                "1) 检查项目vendor目录是否存在eval-stdin.php; 2) 如有则立即删除; 3) 升级PHPUnit: composer require phpunit/phpunit ^8.0; 4) 确认生产环境未安装dev依赖"),
            cve("CVE-2018-10561", "GPON 路由器认证绕过", "critical", 9.8f,
                "GPON Router", "多种型号",
                "1) 登录路由器管理界面; 2) 在系统设置中检查固件更新; 3) 安装厂商提供的最新固件; 4) 如无法更新固件则关闭从WAN口访问管理界面的功能"),

            // ==================== Ruby on Rails ====================
            cve("CVE-2019-5418", "Ruby on Rails 任意文件读取", "high", 7.5f,
                "Ruby on Rails", "3.x - 5.x", "升级到 Rails 5.2.2.1+"),
            cve("CVE-2020-8163", "Ruby on Rails 远程代码执行", "critical", 9.8f,
                "Ruby on Rails", "< 5.2.4.3, < 6.0.3.1", "升级到 Rails 5.2.4.3+ / 6.0.3.1+"),

            // ==================== Laravel ====================
            cve("CVE-2021-3129", "Laravel Ignition debug 模式 RCE", "critical", 9.8f,
                "Laravel", "8.4.2 及以下", "不要在生产环境开启 debug 模式"),

            // ==================== Yii ====================
            cve("CVE-2020-15148", "Yii 2 反序列化 RCE", "high", 7.4f,
                "Yii Framework", "2.0 - 2.0.37", "升级到 Yii 2.0.38+"),

            // ==================== SSL / TLS ====================
            cve("CVE-2014-0160", "OpenSSL Heartbleed 信息泄露", "high", 7.5f,
                "OpenSSL", "1.0.1 - 1.0.1f", "升级到 OpenSSL 1.0.1g+"),
            cve("CVE-2016-2107", "OpenSSL 填充 Oracle 漏洞", "medium", 5.9f,
                "OpenSSL", "1.0.1 - 1.0.1s, 1.0.2 - 1.0.2g", "升级到 OpenSSL 1.0.1t+ / 1.0.2h+"),

            // ==================== Grafana / Monitoring ====================
            cve("CVE-2021-43798", "Grafana 任意文件读取漏洞", "high", 7.5f,
                "Grafana", "8.0.0 - 8.3.0", "升级到 Grafana 8.3.1+"),

            // ==================== F5 / Citrix / ADC ====================
            cve("CVE-2021-22986", "F5 BIG-IP iControl REST RCE", "critical", 9.8f,
                "F5 BIG-IP", "16.0.0 - 16.0.1, 15.1.0 - 15.1.2, 14.1.0 - 14.1.4",
                "1) 禁止从互联网访问iControl REST接口; 2) 升级到16.0.1.1+/15.1.3+/14.1.4.3+; 3) 如无法立即升级,执行: tmsh modify sys httpd allow none; 4) 重启httpd服务"),
            cve("CVE-2020-5902", "F5 BIG-IP TMUI RCE", "critical", 9.8f,
                "F5 BIG-IP", "11.6.1 - 15.1.0",
                "1) 限制TMUI访问来源IP; 2) 升级到15.1.0.4+/14.1.2.6+/13.1.3.4+/12.1.5.2+/11.6.5.2+; 3) 检查/var/log/audit日志确认无异常访问"),
            cve("CVE-2022-1388", "F5 BIG-IP iControl REST 认证绕过 RCE", "critical", 9.8f,
                "F5 BIG-IP", "11.6.1 - 16.1.2",
                "1) 立即阻断iControl REST端口(443)的外部访问; 2) 升级到17.0.0+/16.1.2.2+/15.1.5.1+/14.1.4.6+/13.1.5+; 3) 检查/var/log/restjavad日志; 4) 扫描是否有新增管理员账户"),
            cve("CVE-2019-19781", "Citrix ADC/Gateway 目录遍历漏洞", "critical", 9.8f,
                "Citrix ADC, Citrix Gateway", "10.5, 11.1, 12.0, 12.1, 13.0",
                "1) 立即应用Citrix官方缓解脚本; 2) 升级到13.0-47.24+/12.1-55.18+/11.1-63.15+/10.5-70.12+; 3) 检查/var/log/httperror.log确认无目录遍历尝试"),
            cve("CVE-2020-8193", "Citrix ADC/Gateway 权限提升", "high", 7.2f,
                "Citrix ADC, Citrix Gateway", "< 13.0-58.30",
                "1) 升级到13.0-58.30+; 2) 检查近期新建用户和权限变更记录; 3) 审计系统配置是否被篡改"),
            cve("CVE-2023-3519", "Citrix NetScaler ADC/Gateway 代码注入 RCE", "critical", 9.8f,
                "Citrix NetScaler ADC", "13.1 < 13.1-49.13, 13.0 < 13.0-91.13",
                "1) 立即离线检查系统; 2) 升级到13.1-49.13+/13.0-91.13+; 3) 检查/var/tmp和/netscaler/ns_gui下是否有可疑Webshell文件; 4) 查看crontab是否有不明任务"),

            // ==================== Fortinet ====================
            cve("CVE-2022-40684", "FortiOS/FortiProxy 认证绕过", "critical", 9.8f,
                "Fortinet FortiOS", "7.0.0 - 7.0.6, 7.2.0 - 7.2.1", "升级到 FortiOS 7.0.7+ / 7.2.2+"),
            cve("CVE-2023-27997", "FortiOS SSL-VPN RCE", "critical", 9.8f,
                "Fortinet FortiOS", "6.0.x, 6.2.x, 6.4.x, 7.0.x, 7.2.x",
                "1) 立即限制SSL-VPN访问来源IP; 2) 升级到6.0.17+/6.2.15+/6.4.13+/7.0.12+/7.2.5+; 3) 运行: diagnose debug application sslvpn -1 检查异常连接; 4) 检查SSL VPN用户列表是否有新增"),
            cve("CVE-2024-21762", "Fortinet FortiOS SSLVPN 越界写入 RCE", "critical", 9.8f,
                "Fortinet FortiOS", "6.0 - 7.4.1",
                "1) 紧急: 立即禁用SSLVPN或限制其外部访问; 2) 升级到7.4.2+/7.2.5+/7.0.13+; 3) 检查/var/log/sslvpn日志有无异常请求; 4) 扫描系统中是否有webshell后门"),

            // ==================== Cisco ====================
            cve("CVE-2020-3452", "Cisco ASA/FTD 任意文件读取", "high", 7.5f,
                "Cisco ASA, Cisco FTD", "多种版本",
                "1) 在Cisco ASA配置中禁用WebVPN(/csd/)路径的外部访问; 2) 参考Cisco安全公告cisco-sa-asaftd-ro-path-KJuQhB86应用对应版本补丁; 3) 检查VPN日志中是否有/+CSCOE+/路径的可疑请求"),
            cve("CVE-2021-1497", "Cisco HyperFlex HX RCE", "critical", 9.8f,
                "Cisco HyperFlex HX", "< 4.0(2e)", "升级到 HyperFlex HX 4.0(2e)+"),

            // ==================== Pulse Secure / VPN ====================
            cve("CVE-2019-11510", "Pulse Secure 任意文件读取", "critical", 10.0f,
                "Pulse Connect Secure", "9.0R1 - 9.0R3.4, 8.3R1 - 8.3R7.1",
                "1) 立即检查是否有未经授权的文件读取; 2) 升级到9.0R3.5+/9.1R1+修复版本; 3) 检查/etc/passwd和私钥文件是否被访问; 4) 重置所有VPN用户的密码和证书"),
            cve("CVE-2021-22893", "Pulse Connect Secure RCE", "critical", 10.0f,
                "Pulse Connect Secure", "9.0R3/9.1R1",
                "1) 立即将设备离线排查; 2) 检查是否存在webshell后门; 3) 升级到官方修复版本; 4) 全面审计所有管理员操作记录"),

            // ==================== SonicWall ====================
            cve("CVE-2021-20016", "SonicWall SMA100 SQL 注入", "critical", 9.8f,
                "SonicWall SMA100", "< 10.2.0.5-29sv",
                "1) 紧急升级固件到10.2.0.5-29sv+; 2) 检查数据库中的管理员账户是否有新增; 3) 限制管理界面只能从内网访问; 4) 审计登录日志有无异常IP"),

            // ==================== VMware ====================
            cve("CVE-2021-21972", "VMware vCenter Server RCE", "critical", 9.8f,
                "VMware vCenter Server", "6.5, 6.7, 7.0 < 7.0 U2c", "升级到 vCenter 7.0 U2c+"),
            cve("CVE-2021-22005", "VMware vCenter Server 文件上传 RCE", "critical", 9.8f,
                "VMware vCenter Server", "6.7, 7.0 < 7.0 U2c", "升级到 vCenter 7.0 U2c+"),

            // ==================== ManageEngine ====================
            cve("CVE-2021-40539", "ManageEngine ADSelfService Plus RCE", "critical", 9.8f,
                "ManageEngine ADSelfService Plus", "< 6114", "升级到 6114+"),
            cve("CVE-2022-47966", "ManageEngine 多产品 RCE", "critical", 9.8f,
                "ManageEngine", "多种产品 < 2022.12.22",
                "1) 立即从ManageEngine官网下载对应产品的最新build; 2) 按产品名称+版本号搜索官方安全公告; 3) 备份配置后执行升级; 4) 检查是否有新增的管理员用户或可疑API调用"),

            // ==================== Message Queues / Data ====================
            cve("CVE-2023-33246", "Apache RocketMQ RCE", "critical", 9.8f,
                "Apache RocketMQ", "4.x - 5.1.0", "升级到 RocketMQ 5.1.1+"),
            cve("CVE-2023-37582", "Apache RocketMQ NameServer RCE", "high", 7.8f,
                "Apache RocketMQ", "< 5.1.2", "升级到 RocketMQ 5.1.2+"),
            cve("CVE-2023-25194", "Apache Kafka Connect RCE", "critical", 9.8f,
                "Apache Kafka", "2.3.0 - 3.3.2", "升级到 Kafka 3.4.0+"),

            // ==================== API Gateway ====================
            cve("CVE-2024-3651", "Kong API Gateway 授权绕过", "high", 7.5f,
                "Kong API Gateway", "< 3.5.3", "升级到 Kong 3.5.3+"),

            // ==================== HTTP/2 Protocol ====================
            cve("CVE-2023-44487", "HTTP/2 Rapid Reset DoS", "high", 7.5f,
                "HTTP/2", "广泛影响服务器和代理软件", "更新受影响的 HTTP/2 实现"),

            // ==================== JetBrains ====================
            cve("CVE-2024-27198", "JetBrains TeamCity 认证绕过", "critical", 9.8f,
                "JetBrains TeamCity", "< 2023.11.4", "升级到 TeamCity 2023.11.4+"),

            // ==================== Databases ====================
            cve("CVE-2024-1597", "PostgreSQL JDBC SQL 注入 (pgjdbc)", "high", 8.8f,
                "PostgreSQL JDBC Driver", "< 42.7.2", "升级 pgjdbc 到 42.7.2+")
        );

        int added = 0;
        int updated = 0;
        for (CveDatabase cve : seeds) {
            var existing = cveRepository.findByCveId(cve.getCveId());
            if (existing.isEmpty()) {
                try {
                    cveRepository.save(cve);
                    added++;
                } catch (Exception e) {
                    log.debug("Failed to save CVE {}: {}", cve.getCveId(), e.getMessage());
                }
            } else {
                // Update existing entries with enhanced fix suggestions
                CveDatabase existingCve = existing.get();
                boolean changed = false;
                if (cve.getFixSuggestion() != null && cve.getFixSuggestion().length() > existingCve.getFixSuggestion().length()) {
                    existingCve.setFixSuggestion(cve.getFixSuggestion());
                    changed = true;
                }
                if (cve.getDescription() != null && !cve.getDescription().equals(existingCve.getDescription())) {
                    existingCve.setDescription(cve.getDescription());
                    changed = true;
                }
                if (changed) {
                    try {
                        cveRepository.save(existingCve);
                        updated++;
                    } catch (Exception e) {
                        log.debug("Failed to update CVE {}: {}", cve.getCveId(), e.getMessage());
                    }
                }
            }
        }
        log.info("CVE seed: {} total, {} newly added, {} updated", seeds.size(), added, updated);
    }

    private static CveDatabase cve(String id, String desc, String sev, float score,
                                    String software, String versions, String fix) {
        return CveDatabase.builder()
                .cveId(id).description(desc)
                .severity(sev).cvssScore(BigDecimal.valueOf(score))
                .affectedSoftware(software).affectedVersionRange(versions)
                .fixSuggestion(fix)
                .build();
    }
}
