# ServerScout 统一异常处理设计

## 1. 重构目标

早期项目中部分接口存在局部 try-catch、手动返回错误响应、错误格式不统一等问题。V2 重构为统一异常处理体系：

- **Controller 层**：只负责接收请求、调用 Service、返回 `R<T>`，不写 try-catch
- **Service 层**：遇到业务异常直接 throw，由全局处理器统一捕获
- **SecurityConfig**：认证/授权失败通过 `AuthenticationEntryPoint` + `AccessDeniedHandler` 返回 JSON（不走 `@RestControllerAdvice`）

## 2. 包结构

```text
common/
├── ResultCode.java          # 结果码接口
├── ErrorCode.java           # 标准错误枚举 (HTTP 前缀)
└── R.java                   # 统一响应体 R<T>

exception/
├── BusinessException.java   # 基类 (携带 ResultCode + HttpStatus)
├── BadRequestException.java # 400
├── UnauthorizedException.java # 401
├── ForbiddenException.java  # 403
├── ResourceNotFoundException.java # 404
├── ConflictException.java   # 409
├── ScanException.java       # 50010 扫描失败
├── ServiceException.java    # 50000 通用服务异常
└── GlobalExceptionHandler.java # @RestControllerAdvice
```

## 3. 分层职责

### Controller 层
```java
@GetMapping("/{id}")
public R<AssetResponse> getAsset(@PathVariable Long id) {
    return R.ok(assetService.getAssetDetail(id));  // 异常抛出，不在此捕获
}
```

### Service 层
```java
public AssetResponse getAssetDetail(Long id) {
    Asset asset = assetRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(
            ErrorCode.ASSET_NOT_FOUND, "Asset", id));
    // ...
}
```

### GlobalExceptionHandler
- 捕获 `BusinessException` 及其子类 → 返回 `ResponseEntity<R<?>>`（HTTP 状态码 + JSON body）
- 捕获 Spring Security 异常（`AuthenticationException` → 401, `AccessDeniedException` → 403）
- 捕获参数校验异常（`MethodArgumentNotValidException` → 400）
- 捕获未知异常（`Exception` → 500，**不暴露原始 message 到前端**）

### SecurityConfig
```java
// Filter 层异常不进入 @RestControllerAdvice，需要单独配置
.exceptionHandling(ex -> ex
    .authenticationEntryPoint(authenticationEntryPoint())  // 401 JSON
    .accessDeniedHandler(accessDeniedHandler()))           // 403 JSON
```

## 4. 错误码规范

采用 **HTTP 状态前缀 + 序号** 格式，一眼看出 HTTP 状态对应关系：

| 错误码范围 | HTTP | 含义 |
|-----------|------|------|
| 20000 | 200 | 成功 |
| 40000–40099 | 400 | 请求参数错误 / 校验失败 |
| 40100–40199 | 401 | 未认证 / Token 过期 / 登录失败 |
| 40300–40399 | 403 | 无权限 |
| 40400–40499 | 404 | 资源不存在 |
| 40900–40999 | 409 | 数据冲突 |
| 42900–42999 | 429 | 限流 |
| 50000–50009 | 500 | 服务器内部错误 |
| 50010–50019 | 500 | 扫描工具失败 |
| 50020–50029 | 500 | 报告生成失败 |
| 50030–50039 | 500 | AI / 第三方服务失败 |

### 具体错误码

| 枚举常量 | 错误码 | 说明 |
|---------|--------|------|
| `SUCCESS` | 20000 | 成功 |
| `BAD_REQUEST` | 40000 | 通用请求参数错误 |
| `VALIDATION_FAILED` | 40001 | 参数校验失败 (JSR-380) |
| `CAPTCHA_ERROR` | 40002 | 验证码错误 |
| `UNAUTHORIZED` | 40100 | 未登录或 Token 过期 |
| `LOGIN_FAILED` | 40101 | 用户名或密码错误 |
| `FORBIDDEN` | 40300 | 无权限访问 |
| `NOT_FOUND` | 40400 | 通用资源不存在 |
| `ASSET_NOT_FOUND` | 40401 | 资产不存在 |
| `SCAN_TASK_NOT_FOUND` | 40402 | 扫描任务不存在 |
| `VULNERABILITY_NOT_FOUND` | 40403 | 漏洞不存在 |
| `USER_NOT_FOUND` | 40404 | 用户不存在 |
| `CONFLICT` | 40900 | 数据冲突 |
| `USERNAME_EXISTS` | 40901 | 用户名已存在 |
| `INTERNAL_ERROR` | 50000 | 服务器内部错误 |
| `SCAN_FAILED` | 50010 | 扫描任务执行失败 |
| `SCAN_TIMEOUT` | 50011 | 扫描超时 |
| `PDF_GENERATION_FAILED` | 50020 | PDF 报告生成失败 |

## 5. 响应格式

### 成功响应
```json
{
  "code": 20000,
  "message": "success",
  "data": { ... },
  "timestamp": "2026-06-08T00:00:00Z"
}
```

### 错误响应
```json
{
  "code": 40401,
  "message": "Asset 不存在: 9999",
  "timestamp": "2026-06-08T00:00:00Z"
}
```

> `data` 字段在 `@JsonInclude(Include.NON_NULL)` 控制下，`null` 时不序列化。

## 6. 典型场景映射

| 场景 | 异常类型 | HTTP | JSON code |
|------|---------|------|-----------|
| 资产不存在 | `ResourceNotFoundException` | 404 | 40401 |
| 扫描任务不存在 | `ResourceNotFoundException` | 404 | 40402 |
| 漏洞不存在 | `ResourceNotFoundException` | 404 | 40403 |
| 用户不存在 | `ResourceNotFoundException` | 404 | 40404 |
| 不带 Token 访问业务接口 | `SecurityConfig → AuthenticationEntryPoint` | 401 | 40100 |
| Token 过期 | `UnauthorizedException` | 401 | 40100 |
| 登录失败（用户名或密码错误） | `UnauthorizedException` | 401 | 40100 |
| 普通用户访问管理员接口 | `SecurityConfig → AccessDeniedHandler` | 403 | 40300 |
| 创建扫描任务参数为空 | `BadRequestException` | 400 | 40000 |
| 用户名已存在 | `ConflictException` | 409 | 40901 |
| Nmap 执行失败 | `ScanException` | 500 | 50010 |
| PDF 报告生成失败 | `ServiceException` | 500 | 50020 |

> **重要**: 登录失败统一返回「用户名或密码错误」，不区分「用户不存在」和「密码错误」，防止用户名枚举攻击。

## 7. 安全注意事项

### 7.1 错误信息不泄露内部细节
- 底层异常（Nmap stderr、堆栈、命令路径）**只写日志**，不返回前端
- `Exception.class` 的 catch-all handler 返回泛化的「服务器内部错误」，不拼接原始 message

### 7.2 ScanException 信息分级
```java
// ✅ 前端可见
throw new ScanException("Nmap execution failed (exit code 1)");

// ✅ 仅日志
log.error("Nmap exited with code {} for task {}: {}", exitCode, taskId, stderr);
```

### 7.3 登录防枚举
```java
// ✅ 统一提示
throw new UnauthorizedException("用户名或密码错误");

// ❌ 不要这样
if (!userExists) throw new XxxException("用户不存在");
if (!passwordMatch) throw new XxxException("密码错误");
```

## 8. 重构收益

- **统一前后端错误格式**：所有错误响应都是 `{code, message, timestamp}`
- **减少 Controller 中重复 try-catch**：异常统一抛到 `GlobalExceptionHandler`
- **Service 层业务语义更清晰**：`throw new ForbiddenException(...)` 比 `return ApiResponse.error(...)` 更表达业务意图
- **Security 认证失败也能返回 JSON**：通过 `AuthenticationEntryPoint` + `AccessDeniedHandler` 处理 filter 层异常
- **扫描工具异常不会直接暴露底层堆栈**：错误分级，前端只看到友好提示
- **错误码直观**：`40401` 一眼看出是 404 类错误中的资产不存在子类

## 9. 测试覆盖

共 58 个测试，覆盖所有异常类型和关键安全路径：

| 测试层级 | 测试文件 | 覆盖内容 |
|---------|---------|---------|
| Controller (集成) | `PluginControllerTest` | SecurityConfig 401 EntryPoint 返回 JSON |
| Controller (集成) | `AuthControllerTest` | 登录失败 400 |
| Controller (单元) | `AiBriefingControllerTest` | 业务异常 → 400 |
| Service | `UserServiceTest` | BadRequest / Conflict / ResourceNotFound |
| Service | `AiBriefingServiceTest` | BadRequest |
| Exception Handler | `GlobalExceptionHandlerTest` | **21 个测试**：40401→40404, 40100, 40300, 40000, 40900, 50010, 50000, 50030, 响应格式 |
