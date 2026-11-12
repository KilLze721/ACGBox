# ACGBox

本仓库提供 ACGBox 管理端的 REST API，涵盖动画、书籍、关联资料、基础字典和账号管理。

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 4.0.6 |
| ORM | MyBatis Plus | 3.5.16 |
| 数据库 | PostgreSQL | 16+（推荐 18） |
| 工具 | Lombok | — |
| 构建 | Maven | 3.9+ |

## 模块结构

项目为 Maven 多模块工程，最终打包为单个 JAR：

```
ACGBox/
├── acgbox-model/                # 数据模型
│   ├── entity/content/          # MyBatis Plus 实体，映射数据库表
│   ├── dto/content/             # 请求体 DTO（含 @Valid 校验注解）
│   ├── dto/common/              # 通用 DTO（分页 PageDTO）
│   └── vo/content/ & vo/common/ # 响应视图对象，用 @Builder 构建
├── acgbox-common/               # 共享基础设施
│   ├── config/MybatisPlusConfig # MyBatis Plus 分页插件配置（PostgreSQL）
│   ├── exception/               # BusinessException + GlobalExceptionHandler
│   └── result/                  # Result<T> 统一响应 + ResultCode 枚举
├── acgbox-user/                 # 用户与安全模块
│   └── config/SecurityConfig    # 后台接口访问规则
├── acgbox-content/              # 内容业务模块
│   ├── controller/              # REST 控制器
│   ├── service/ + service/impl/ # 服务层
│   ├── mapper/                  # MyBatis Plus Mapper 接口
│   └── resources/mapper/        # 复杂查询的自定义 SQL XML
├── acgbox-app/                  # 启动模块（Spring Boot 入口，端口 8080）
│   ├── AcgBoxApplication        # 唯一启动类
│   └── resources/application.yml # 数据源与 MyBatis Plus 配置
└── sql/public.sql               # 数据库建表脚本
```

**依赖方向**：`app` 依赖 `content` 和 `user`；`content` 与 `user` 均依赖 `model` 和 `common`；`common` 依赖 `model`。

## 环境要求

- JDK 21+
- Maven 3.9+
- PostgreSQL（本地或远程均可）

## 快速开始

### 1. 初始化数据库

创建数据库 `acgbox`，并执行建表脚本 `sql/public.sql`：

```bash
psql -U postgres -d acgbox -f sql/public.sql
```

也可以使用 Navicat 等图形化工具导入该脚本。

### 2. 配置数据源

将数据库连接和 JWT 密钥配置在仓库外的 `application-secrets.yml` 中。按需填写 Redis、邮件和图片上传配置。字段和示例见 [外部私有配置说明](docs/外部私有配置说明.md)。

### 3. 启动

在后端仓库根目录构建可执行 JAR。默认端口为 8080，将启动参数中的文件路径替换为本机实际路径。

```powershell
mvn -pl acgbox-app -am -DskipTests package
java -jar acgbox-app/target/acgbox-app-0.0.1-SNAPSHOT.jar --spring.config.additional-location=file:D:/path/to/application-secrets.yml
```

### 4. 验证

```powershell
curl.exe -i http://localhost:8080/adaptation-type/list
```

未携带有效 JWT 时，受保护接口应返回 HTTP 401。业务联调使用已启用的 `user_account` 账号登录，在后续请求中携带 `Authorization: Bearer <token>`。

## 构建命令

```bash
# 编译整个项目
mvn clean compile

# 运行测试
mvn test

# 打包（跳过测试）
mvn clean package -DskipTests
```

## 接口概览

| 模块 | 路径 | 说明 |
|------|------|------|
| 作品 | `/anime`、`/book` | 作品维护、详情和条件分页 |
| 关联资料 | `/series`、`/companies`、`/persons`、`/tags` | 资料维护、名称查询和首字母排序 |
| 基础字典 | `/broadcast-type`、`/adaptation-type`、`/region`、`/book-type` | 字典维护和选项查询 |
| 作品工具 | `/uploads`、`/data-transfer`、`/data-quality` | 封面上传、数据导入导出、质量检查 |
| 登录与个人账号 | `/admin/login`、`/admin/logout`、`/admin/account` | 登录、退出、资料查询、密码和邮箱管理 |
| 管理员功能 | `/admin/users`、`/admin/system` | 账号管理和服务连接检测 |

登录支持账号密码、邮箱密码和邮箱验证码。用户管理与系统检测仅允许 `ADMIN` 访问。其余已认证接口面向 `ADMIN` 和 `EDITOR`，各接口的业务条件见 [管理端接口文档](../docs/ACGBox管理端接口文档.md)。

## 统一响应规范

常规 JSON 业务响应使用 `Result<T>`：

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 状态码（`SUCCESS=200`, `BAD_REQUEST=400`, `UNAUTHORIZED=401`, `FORBIDDEN=403`, `NOT_FOUND=404`, `ERROR=500`） |
| message | String | 提示信息 |
| data | T | 响应数据（无数据时为 `null`） |

分页请求使用 `PageDTO`（`pageNum`, `pageSize`），分页响应使用 `PageVO<T>`（`pageNum`, `pageSize`, `total`, `pages`, `rows`）。参数校验使用 Jakarta Validation，失败由 `GlobalExceptionHandler` 统一处理。

## 数据库表

作品主体存放在 `anime` 和 `book` 中。别名、标签、公司、人物、外链与评分通过作品类型和 ID 关联，系列归属由 `series_item` 记录。完整表结构、约束和索引见 [初始化 SQL](sql/public.sql)。

## 相关文档

- [工作区导航](../CLAUDE.md)
- [部署说明](deploy/README.md)
