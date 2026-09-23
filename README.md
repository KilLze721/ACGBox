# ACGBox

ACGBox 是一个 ACG 内容后台管理系统，用于管理动画、漫画、小说等作品的元数据。本仓库为后端部分，基于 Spring Boot 构建，提供 RESTful 接口。

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
├── acgbox-model/                # 数据模型（被 common 和 content 依赖）
│   ├── entity/content/          # MyBatis Plus 实体，映射数据库表
│   ├── dto/content/             # 请求体 DTO（含 @Valid 校验注解）
│   ├── dto/common/              # 通用 DTO（分页 PageDTO）
│   └── vo/content/ & vo/common/ # 响应视图对象，用 @Builder 构建
├── acgbox-common/               # 共享基础设施
│   ├── config/MybatisPlusConfig # MyBatis Plus 分页插件配置（PostgreSQL）
│   ├── exception/               # BusinessException + GlobalExceptionHandler
│   └── result/                  # Result<T> 统一响应 + ResultCode 枚举
├── acgbox-content/              # 主应用（Spring Boot 入口，端口 8080）
│   ├── controller/              # REST 控制器
│   ├── service/ + service/impl/ # 服务层
│   ├── mapper/                  # MyBatis Plus Mapper 接口
│   └── resources/mapper/        # 复杂查询的自定义 SQL XML
└── sql/public.sql               # 数据库建表脚本
```

**依赖方向**：`common` → `model` ← `content`，`content` 也依赖 `common`。

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

编辑 `acgbox-content/src/main/resources/application.yml`，按需修改数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/acgbox
    username: postgres
    password: 123456
```

### 3. 编译

```bash
mvn clean compile
```

### 4. 启动

```bash
# 从 ACGBox 根目录启动（监听 8080 端口）
mvn spring-boot:run -pl acgbox-content
```

### 5. 验证

```bash
curl http://localhost:8080/adaptation-type/list
```

返回 `{"code":200,"message":"操作成功","data":[...]}` 即启动成功。

## 构建命令

```bash
# 编译整个项目
mvn clean compile

# 运行测试
mvn test

# 启动（监听 8080 端口）
mvn spring-boot:run -pl acgbox-content

# 打包（跳过测试）
mvn clean package -DskipTests
```

## 接口概览

目前共实现 7 个 Controller，统一提供 5 类端点：`POST /create`、`POST /update`、`POST /delete`（接收 `List<Long>`）、`GET /{id}`、`GET /list`（或 `/page`）。

| 模块 | 路径 | 说明 |
|------|------|------|
| 动画 | `/anime` | 完整 CRUD + 多维度条件分页查询 |
| 改编类型 | `/adaptation-type` | 全量列表查询 |
| 放送类型 | `/broadcast-type` | 全量列表查询 |
| 地区 | `/region` | 全量列表查询 |
| 标签 | `/tags` | 分页模糊查询 |
| 公司 | `/companies` | 分页模糊查询（含简介） |
| 系列 | `/series` | 分页模糊查询（含简介） |

详细请求/响应说明见 `ACGBox接口文档.md`。

## 统一响应规范

所有接口返回 `Result<T>` 结构：

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 状态码（`SUCCESS=200`, `BAD_REQUEST=400`, `UNAUTHORIZED=401`, `FORBIDDEN=403`, `NOT_FOUND=404`, `ERROR=500`） |
| message | String | 提示信息 |
| data | T | 响应数据（无数据时为 `null`） |

分页请求使用 `PageDTO`（`pageNum`, `pageSize`），分页响应使用 `PageVO<T>`（`pageNum`, `pageSize`, `total`, `pages`, `rows`）。参数校验使用 Jakarta Validation，失败由 `GlobalExceptionHandler` 统一处理。

## 数据库表

共 13 张表，完整建表语句见 `sql/public.sql`：

| 表名 | 说明 |
|------|------|
| `anime` | 动画表 |
| `adaptation_type` | 改编类型表 |
| `broadcast_type` | 放送类型表 |
| `region` | 地区表 |
| `tag` | 标签表 |
| `company` | 制作公司表 |
| `series` | 系列表 |
| `alias` | 通用别名表（`target_type` + `target_id`） |
| `company_relation` | 通用公司关联表（`target_type` + `target_id`） |
| `tag_relation` | 通用标签关联表（`target_type` + `target_id`） |
| `external_link` | 通用外部链接表（`target_type` + `target_id`） |
| `series_item` | 系列条目表（`series_id` + `work_type` + `work_id`） |
| `personal_rating` | 个人评分表（`target_type` + `target_id`） |

## 当前进度

- ✅ 字典类 CRUD：改编类型、放送类型、地区、标签、公司、系列
- ✅ 核心业务：动画 CRUD（创建 / 修改 / 批量删除 / 详情 / 条件分页查询，含别名、标签、公司、外链、系列、个人评分等关联）
- ❌ 认证 / 授权：尚未实现（`ResultCode` 预留了 401/403）
- ❌ 社区评分系统：尚未实现（计划用 Redis；个人评分已实现）

## 相关文档

- [`../ACGBox接口文档.md`](../ACGBox接口文档.md)：接口详细说明（上级目录）
- [`../CLAUDE.md`](../CLAUDE.md)：项目开发指南（上级目录）
