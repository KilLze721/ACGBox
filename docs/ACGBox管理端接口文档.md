# ACGBox 接口文档

## 概述

ACGBox 是一个 ACG 内容后台管理系统，用于管理动画、漫画、小说等作品的元数据。系统基于 Spring Boot 4.0.6 + MyBatis Plus 3.5.16 构建，使用 PostgreSQL 作为主数据库；邮箱验证码使用 Spring Mail 与 Redis（`acgbox-user` 模块）。

- **基础路径**：`http://localhost:8080`
- **请求格式**：通常为 `application/json`；图片上传接口使用 `multipart/form-data`，详见第十六节。
- **响应格式**：`application/json`
- **认证方式**：两个密码登录接口、邮箱登录验证码发送及验证码登录接口允许匿名 POST，其余接口均需在请求头携带 JWT：`Authorization: Bearer <token>`；接口按角色授权，`ADMIN`、`EDITOR` 可访问内容接口与自身账号接口，`/admin/users/**` 和 `/admin/system/**` 仅 `ADMIN` 可访问，详见 [九、认证与鉴权](#九认证与鉴权)
- **启动前置**：数据库口令、JWT 密钥、SMTP 与 Redis 参数都写在仓库外的私有配置文件 `application-secrets.yml` 中，通过 `spring.config.additional-location` 加载；同名系统环境变量或 JVM 参数会覆盖文件值，详见 [9.1](#91-前置配置私有配置与-jwt-密钥)

---

## 通用说明

### 统一响应结构 `Result<T>`

所有接口返回统一 JSON 结构：

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 状态码 |
| message | String | 提示信息 |
| data | T | 响应数据（泛型，无数据时为 `null`） |

**成功响应示例**

```json
{ "code": 200, "message": "操作成功", "data": { ... } }
```

**失败响应示例**

```json
{ "code": 500, "message": "此标签已存在", "data": null }
```

### 状态码

| 状态码 | 枚举常量 | 说明 |
|--------|----------|------|
| 200 | SUCCESS | 操作成功 |
| 400 | BAD_REQUEST | 请求参数错误（预留） |
| 401 | UNAUTHORIZED | 未登录（预留） |
| 403 | FORBIDDEN | 无权限（预留） |
| 404 | NOT_FOUND | 资源不存在（预留） |
| 500 | ERROR | 服务器内部错误 / 业务异常 |

> 上表是**响应体中的业务状态码**（`Result.code`），与 HTTP 状态码不是一回事：通常业务接口的 HTTP 状态码为 `200`，客户端需以 `Result.code` 判断结果。认证与授权失败由 Spring Security 过滤器链在进入 Controller 之前处理，返回**响应体为空的真实 HTTP 状态码**（`401` / `403`），详见 [九、认证与鉴权](#九认证与鉴权)。另一个例外是图片、Excel 上传文件或 multipart 请求体超限：返回真实 **HTTP 413**，响应体仍为 `Result`，其中 `code=500`，不修改现有 `ResultCode`；Excel 与图片入口分别返回对应文案。

### 分页请求 `PageDTO`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| pageNum | Long | 1 | 当前页码 |
| pageSize | Long | 20 | 每页记录数（1~100） |

### 分页响应 `PageVO<T>`

| 字段 | 类型 | 说明 |
|------|------|------|
| pageNum | Long | 当前页码 |
| pageSize | Long | 每页条数 |
| total | Long | 总记录数 |
| pages | Long | 总页数 |
| rows | List\<T\> | 当前页数据列表 |

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "pageNum": 1,
    "pageSize": 20,
    "total": 100,
    "pages": 5,
    "rows": [ ... ]
  }
}
```

### 全局错误处理

| 异常类型 | 触发场景 | 返回示例 |
|----------|----------|----------|
| `BusinessException` | 业务校验失败 | `{"code": 500, "message": "此标签已存在"}` |
| `MethodArgumentNotValidException` | `@Valid` 校验不通过 | `{"code": 500, "message": "标签名称不能为空"}` |
| `Exception` | 未预期的系统异常 | `{"code": 500, "message": "系统错误，请联系管理员"}` |

> `GlobalExceptionHandler` 只覆盖进入 Controller 之后的异常。认证失败、授权失败、请求体 JSON 格式非法等在过滤器链或参数解析阶段抛出的错误不会走这里，其响应体为空，只有 HTTP 状态码。

---

## 数据库 ER 图

```
┌──────────────────┐     ┌─────────────────────┐     ┌──────────────┐
│  adaptation_type │     │    broadcast_type    │     │    region    │
├──────────────────┤     ├─────────────────────┤     ├──────────────┤
│ id (PK)          │     │ id (PK)              │     │ id (PK)      │
│ name (UNIQUE)    │     │ name (UNIQUE)         │     │ name (UNIQUE)│
│ created_at       │     │ created_at           │     │ created_at   │
│ updated_at       │     │ updated_at           │     │ updated_at   │
└──┬───────────────┘     └──┬──────────────────┘     └──┬───────────┘
   │                        │                           │
   │                        │    ┌──────────────────────┘
   │                        │    │
   │    ┌───────────────────┼────┤
   │    │    ┌──────────────┴────┴─────┐
   │    │    │         anime           │
   ├────┼────┤ adaptation_type_id (FK) │
   │    ├────┤ broadcast_type_id (FK)  │
   │    └────┤ region_id (FK)          │
   │         ├─────────────────────────┤
   │         │ name, episode_count     │
   │         │ air_date, status        │
   │         │ cover_image_url         │
   │         │ description             │
   │         │ created_at / updated_at │
   │         └─────────────────────────┘
   │              ↑ (target_type + target_id)
   │    ┌─────────┼──────────┬──────────────┐
   │    ↓         ↓          ↓              ↓
   │ ┌──────────┐ ┌──────────────┐ ┌─────────────┐ ┌────────────────┐
   │ │  alias   │ │company_rel   │ │ tag_relation│ │personal_rating │
   │ └──────────┘ └──────┬───────┘ └──────┬──────┘ │score, created_ │
   │                     │                │         │at, updated_at  │
   │                     ↓                ↓         └────────────────┘
   │              ┌──────────────┐  ┌──────────────┐
   │              │   company    │  │     tag      │
   │              └──────────────┘  └──────────────┘
   │
   │ ┌──────────────────────────────────────────┐
   │ │          external_link  （通用外链）       │
   │ │  id, target_type, target_id, title       │
   │ │  url, sort_order                        │
   │ └──────────────────────────────────────────┘
   │
   │ ┌──────────────────────────────────────────┐
   │ │          series + series_item            │
   │ │  series: id, name, description           │
   │ │  series_item: id, series_id (FK)         │
   │ │    work_type, work_id, sort_order        │
   │ └──────────────────────────────────────────┘
```

### 数据表

| 表名 | 时间戳 | 说明 |
|------|--------|------|
| `anime` | ✅ | 动画表 |
| `book` | ✅ | 书籍作品，允许同名，一条记录代表整部作品而非单卷 |
| `book_type` | ✅ | 书籍主类型，名称唯一，非负排序 |
| `adaptation_type` | ✅ | 改编类型表 |
| `broadcast_type` | ✅ | 放送类型表 |
| `region` | ✅ | 地区表 |
| `tag` | ✅ | 类型词条表 |
| `company` | ✅ | 制作公司表 |
| `person` | ✅ | 创作人员资料，允许同名，姓名最多 100 字符 |
| `person_relation` | — | 人物关联（UNIQUE target_type+target_id+person_id+role，FK→person，非负排序） |
| `series` | ✅ | 系列表 |
| `personal_rating` | ✅ | 个人评分表（UNIQUE target_type+target_id, CHECK 0≤score≤10） |
| `alias` | — | 通用别名表（UNIQUE target_type+target_id+alias_name） |
| `company_relation` | — | 通用公司关联表（UNIQUE target_type+target_id+company_id, FK→company） |
| `tag_relation` | — | 通用标签关联表（UNIQUE target_type+target_id+tag_id, FK→tag） |
| `external_link` | — | 通用外部链接表（UNIQUE target_type+target_id+url） |
| `series_item` | — | 系列条目表（UNIQUE series_id+work_type+work_id, FK→series） |
| `user_account` | ✅ | 用户账号表（UNIQUE lower(username)、UNIQUE email、单 ADMIN 部分唯一索引、CHECK role∈{ADMIN,EDITOR,USER}） |

---

## 一、动画接口 `/anime`  完整

> 五个端点全部实现：创建、修改、删除、分页查询、详情。

### 1.1 创建动画

```http
POST /anime/create
```

**请求体**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| name | String | 是 | `@NotBlank` | 动画名称，不可重复 |
| episodeCount | Long | 否 | — | 动画总集数 |
| broadcastTypeId | Long | 是 | `@NotNull` | 放送类型 ID |
| adaptationTypeId | Long | 是 | `@NotNull` | 改编类型 ID |
| regionId | Long | 是 | `@NotNull` | 地区 ID |
| airDate | LocalDate | 否 | 有值时必须是有效日期 | 开播日期 `yyyy-MM-dd`，未确定时传 null 或省略 |
| coverImageUrl | String | 否 | — | 封面图片 URL |
| status | Long | 是 | `@NotNull` | 1=未放送 2=放送中 3=已完结 4=其他 |
| description | String | 否 | — | 动画简介 |
| isPublic | Boolean | 否 | — | 新增省略或 null 默认公开；修改省略或 null 保留，false 隐藏、true 公开 |
| aliasNames | List\<String\> | 否 | — | 按数组顺序 trim、去空、去重，保留首次出现顺序 |
| companies | List\<CompanyRelationDTO\> | 否 | `@Valid` | 关联公司 |
| persons | List\<PersonRelationDTO\> | 否 | `@Valid`、关联项不可为 null | 创作人员；创建缺省无关联，修改未传或 null 保留，`[]` 清空 |
| externalLinks | List\<ExternalLinkDTO\> | 否 | `@Valid` | 外部链接 |
| tagIds | List\<Long\> | 否 | — | 标签 ID，自动去重+校验存在性 |
| seriesId | Long | 否 | — | 已有系列 ID |
| autoCreateSeries | Boolean | 否 | — | 无系列时用动画名自动创建 |
| personalRatingScore | BigDecimal | 否 | `@IntegerScore` | 可空；非空为 0~10 整数，8 与 8.0 等价，不接受 8.1、8.5 |

**`CompanyRelationDTO`**：`companyId`(Long, 必填) + `role`(String) + `sortOrder`(Long, 可选)

**`PersonRelationDTO`**：`personId`(Long, 必填且为有效人员 ID) + `role`(String, 必填、trim 后不为空且最多 50 字符) + `sortOrder`(Long, 可选、0～2147483647)。排序不接受浮点数或负数，数字字符串沿用框架默认转换；未传或 null 按数组位置从 0 赋值。同一人员可以承担不同职责，trim 后的同人同职责重复返回错误，不静默去重。

动画创建、修改、详情和分页统一返回 `persons: [{ personId, personName, role, sortOrder }]`，没有人员时为 `[]`。按 `person_relation.sort_order ASC, person_relation.id ASC` 返回；姓名读取人物当前资料，改名后下次查询即生效。分页按当页动画批量读取关联和人物，不逐部动画查询。

**关联排序契约（ACG-20261025-001，版本 1）**：

- `aliasNames` 继续使用字符串数组。清洗后按数组位置从 0 连续写入 `alias.sort_order`；创建、修改响应、详情和分页均按 `sort_order ASC, id ASC` 返回别名。
- 公司 `sortOrder` 支持 -2147483648 至 2147483647 的整数，可以为负数。显式传值按值保存，创建关联时未传或 null 则按清洗后的数组位置从 0 赋值。
- JSON 整数字段统一禁用浮点数转整数，ID、排序、数量等 Long 字段拒绝 `1.5`、`1.0`、`1e0`，不静默截断。数字字符串等输入沿用框架默认转换，范围继续由业务校验；未传和 null 语义不变。配置为 `spring.jackson.deserialization.accept-float-as-int=false`，不再使用自定义 deserializer。
- 公司按 `companyId` 去重，保留首次记录的职责和排序值；职责 trim 后为空则保存 null。
- 修改时公司 `sortOrder` 未传或 null：原有关联保留原排序，新增关联使用清洗后的数组位置。读取旧排序、删除和重建关联在现有动画修改事务内完成。
- 公司查询和写入响应统一按 `sort_order ASC, id ASC` 排序，同值按关联自身 ID，不按公司 ID；创建时同值保持有效输入顺序，刷新后顺序一致。
- 公司对象在创建、修改和详情响应中包含 `companyId`、`role`、`sortOrder`；分页额外包含 `companyName`。数组首项即列表优先展示的公司。
- 查询和覆盖操作均限定 `target_type = 'ANIME'` 及对应动画 ID，不影响其他类型或其他动画的关联；不批量调整存量排序值。

**`ExternalLinkDTO`**：`title`(String，动画必填且 trim 后非空；书籍沿用原可空规则) + `url`(String, 必填) + `sortOrder`(Long, 默认0)。动画空标题在写入前返回 `动画外链标题不能为空`。

**请求示例**

```json
{
  "name": "葬送的芙莉莲",
  "episodeCount": 28,
  "broadcastTypeId": 1,
  "adaptationTypeId": 2,
  "regionId": 1,
  "airDate": "2023-09-29",
  "coverImageUrl": "https://lain.bgm.tv/pic/cover/l/13/c5/400602_ZI8Y9.jpg",
  "status": 3,
  "description": "魔法使芙莉莲在勇者一行击败魔王后，踏上了重新认识人类的旅程。",
  "aliasNames": ["葬送的芙莉蓮", "Frieren"],
  "companies": [{ "companyId": 5, "role": "制作", "sortOrder": 0 }],
  "persons": [
    { "personId": 12, "role": "导演", "sortOrder": 0 },
    { "personId": 12, "role": "脚本", "sortOrder": 1 }
  ],
  "externalLinks": [
    { "title": "Bangumi", "url": "https://bgm.tv/subject/400602", "sortOrder": 0 }
  ],
  "tagIds": [17, 18, 19],
  "autoCreateSeries": true,
  "personalRatingScore": 8.0
}
```

**成功响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 15,
    "name": "葬送的芙莉莲",
    "episodeCount": 28,
    "broadcastTypeId": 1,
    "adaptationTypeId": 2,
    "regionId": 1,
    "airDate": "2023-09-29",
    "coverImageUrl": "https://lain.bgm.tv/pic/cover/l/13/c5/400602_ZI8Y9.jpg",
    "status": 3,
    "description": "魔法使芙莉莲在勇者一行击败魔王后，踏上了重新认识人类的旅程。",
    "aliasNames": ["葬送的芙莉蓮", "Frieren"],
    "companies": [{ "companyId": 5, "role": "制作", "sortOrder": 0 }],
    "persons": [
      { "personId": 12, "personName": "示例人物", "role": "导演", "sortOrder": 0 },
      { "personId": 12, "personName": "示例人物", "role": "脚本", "sortOrder": 1 }
    ],
    "externalLinks": [
      { "title": "Bangumi", "url": "https://bgm.tv/subject/400602", "sortOrder": 0 }
    ],
    "tagIds": [17, 18, 19],
    "seriesId": 7,
    "personalRatingScore": 8.0
  }
}
```

---

### 1.2 修改动画

```http
POST /anime/update
```

**请求体**：同 `AnimeDTO`，`id` 必填。全部字段显式更新，子资源覆盖式替换。`@Transactional` 保护。

`persons` 是兼容旧请求的例外：未传或 null 时保留原人物关联，不执行人物关联删除；明确传 `[]` 才清空，非空数组则先校验整个集合，再在原事务内替换。这个兼容规则不改变公司、别名、外链、标签、系列或评分的既有更新语义。

`airDate` 可空：创建时未传或 null 保存为 NULL；修改时传 null 或省略会清空原开播日期。详情和分页返回 `airDate: null`，按开播日期排序时空值仍排在最后；指定日期范围时，空日期不命中该范围。前端留空建议提交 null，不提交无效日期字符串。

**请求示例**

```json
{
  "id": 15,
  "name": "葬送的芙莉莲",
  "episodeCount": 28,
  "broadcastTypeId": 1,
  "adaptationTypeId": 2,
  "regionId": 1,
  "airDate": "2023-09-29",
  "coverImageUrl": "https://new-cover.jpg",
  "status": 3,
  "description": "魔法使芙莉莲的旅程。",
  "aliasNames": ["葬送的芙莉蓮"],
  "companies": [{ "companyId": 5, "role": "动画制作", "sortOrder": 0 }],
  "externalLinks": [
    { "title": "官网", "url": "https://frieren-anime.jp", "sortOrder": 0 }
  ],
  "tagIds": [17, 18, 19],
  "seriesId": 1,
  "personalRatingScore": 9
}
```

**成功响应**：同详情接口，返回完整 `AnimeVO`。

---

### 1.3 批量删除动画  新增

```http
POST /anime/delete
```

**请求体**：`List<Long>` — 动画 ID 列表

**级联删除**：别名/公司关联/人物关联/外链/标签关联/系列关联/个人评分 → 动画本体，全部在一个事务内。人物关联清理限定 `target_type = 'ANIME'` 和本次动画 ID，不删除人物资料，也不影响同 ID 的 BOOK 关联。

**请求示例**

```json
[15, 16, 17]
```

**成功响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

**错误情况**

| 场景 | message |
|------|---------|
| 列表为空 | 请选择要删除的动画 |

---

### 1.4 分页查询动画  新增

```http
GET /anime/page
```

**Query 参数 (`AnimePageDTO`)**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| pageNum | Long | 1 | 当前页码 |
| pageSize | Long | 20 | 每页条数（1~100） |
| keyword | String | — | **关键词**，同时匹配动画名称、别名、系列名称 |
| tagIds | List\<Long\> | — | 标签 ID 列表，多个用逗号分隔 |
| tagMatchMode | String | `ALL` | `ALL`=须包含全部标签（AND），`ANY`=包含任一即可（OR） |
| broadcastTypeId | Long | — | 放送类型 ID |
| adaptationTypeId | Long | — | 改编类型 ID |
| broadcastStartDate | String | — | 放送起始日期，支持 `yyyy`（如 `2023`）或 `yyyy-MM`（如 `2023-10`） |
| broadcastEndDate | String | — | 放送结束日期，格式同上。`2023` 会转为 `2023-12-31` |
| status | Long | — | 放送状态：1=未放送 2=放送中 3=已完结 4=其他 |
| regionId | Long | — | 地区 ID |
| companyId | Long | — | 公司 ID，匹配关联的任意公司；1～2147483647，不存在或无关联返回空分页 |
| personId | Long | — | 人物 ID，匹配该人物的全部职责；1～2147483647，不存在或无关联返回空分页 |
| seriesId | Long | — | 系列 ID，按系列动画条目筛选；1～2147483647，不存在或无关联返回空分页 |
| isPublic | Boolean | — | 省略或 null 查询全部；true 仅公开，false 仅隐藏 |
| ratingMin | BigDecimal | — | 最低个人评分，可选 0~10 整数，8.0 等数值整数也有效 |
| ratingMax | BigDecimal | — | 最高个人评分，可选 0~10 整数，ratingMin 不得大于 ratingMax |
| sortBy | String | `BROADCAST_DATE` | 排序字段：`BROADCAST_DATE`=按开播日期排，`PERSONAL_RATING`=按个人评分排 |
| sortDirection | String | `DESC` | 排序方向：`ASC`=升序，`DESC`=降序 |

**关联实体反向查询（2026-10-26）**：

- 公司、人物、系列详情共用 `/anime/page`，不新增同义接口，不按名称搜索后再筛选。
- `companyId`、`personId`、`seriesId` 未传时不参与过滤；显式空串、非数字、小数、0、负数、超过 2147483647 均返回现有统一参数错误响应，不忽略非法参数。
- 三类 ID 合法但实体不存在或没有关联时，正常返回 `code: 200`、`total: 0`、`pages: 0`、`rows: []`。按用户确认，公司不再因实体不存在而返回“公司不存在”；其他字典筛选的原有资源存在性校验不变。
- 多个关联 ID 以及 keyword、日期、标签等现有筛选条件按 AND 求交集。关联 ID 不隐式追加 keyword，同名人物或同名系列按 ID 严格区分。
- 人物关联限定 `target_type = 'ANIME'`，系列条目限定原有小写 `work_type = 'anime'`；BOOK 或其他类型不会混入。
- 使用 EXISTS 筛选动画，一个人物在同部动画有多个职责不会产生重复行；total、pages 按唯一动画计算，不在 Java 中做分页后筛选。
- 排序支持 BROADCAST_DATE / PERSONAL_RATING 和 ASC / DESC：选择开播日期时，个人评分作为次排序；选择个人评分时，开播日期作为次排序。两项均使用所选方向，空值始终靠后，两项都相同时按动画 ID 降序；不按系列条目或人物署名排序值排列动画。
- 返回保持 `PageVO<AnimePageVO>`，保留完整 persons、companies、series 等数据。personId/companyId 只筛选动画，不裁掉其他人物、职责或公司；当前页关联继续批量读取。

```http
GET /anime/page?personId=12&pageNum=1&pageSize=10&sortBy=BROADCAST_DATE&sortDirection=DESC
GET /anime/page?seriesId=8&pageNum=1&pageSize=10&sortBy=BROADCAST_DATE&sortDirection=DESC
GET /anime/page?companyId=3&pageNum=1&pageSize=10&sortBy=PERSONAL_RATING&sortDirection=DESC
GET /anime/page?personId=12&seriesId=8&companyId=3&keyword=示例&pageNum=1&pageSize=10
```

---

**示例一：最简查询（仅分页，不传任何筛选条件）**

```http
GET /anime/page?pageNum=1&pageSize=10
```

---

**示例二：多条件组合查询**

> 查询「2020 年至 2024 年间在日本播出的、标签同时包含冒险和奇幻、漫画改编、MADHOUSE 参与制作、已完结的 TV 动画，个人评分在 7.0 到 10.0 之间，按评分从高到低排序」

```http
GET /anime/page?pageNum=1&pageSize=20&keyword=芙莉莲&broadcastTypeId=1&adaptationTypeId=2&regionId=1&status=3&companyId=5&broadcastStartDate=2020&broadcastEndDate=2024&tagIds=17,18&tagMatchMode=ALL&ratingMin=7.0&ratingMax=10.0&sortBy=PERSONAL_RATING&sortDirection=DESC
```

---

**示例三：仅关键词模糊搜索**

```http
GET /anime/page?pageNum=1&pageSize=10&keyword=芙莉莲
```

> `keyword` 会同时搜索 `anime.name`、`alias.alias_name`、`series.name`，任一命中即返回。

---

**示例四：标签任意匹配 + 年月粒度日期范围**

> 查询「2022 年 1 月到 2023 年 6 月间开播的，标签包含冒险或奇幻任意一个即可」

```http
GET /anime/page?pageNum=1&pageSize=20&tagIds=17,18&tagMatchMode=ANY&broadcastStartDate=2022-01&broadcastEndDate=2023-06
```

---

**示例五：仅评分范围 + 按评分排序**

> 查询「个人评分 8 及以上的动画，按评分从高到低」

```http
GET /anime/page?pageNum=1&pageSize=10&ratingMin=8.0&sortBy=PERSONAL_RATING&sortDirection=DESC
```

---

**示例六：按开播日期排序 + 筛选状态**

> 查询「正在放送中的动画，按开播日期从新到旧排列」

```http
GET /anime/page?pageNum=1&pageSize=20&status=2&sortBy=BROADCAST_DATE&sortDirection=DESC
```

---

**成功响应（对应示例二的全条件查询）**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "pageNum": 1,
    "pageSize": 20,
    "total": 3,
    "pages": 1,
    "rows": [
      {
        "id": 15,
        "name": "葬送的芙莉莲",
        "aliasNames": ["葬送的芙莉蓮", "Frieren"],
        "tags": [
          { "id": 17, "name": "冒险" },
          { "id": 18, "name": "奇幻" },
          { "id": 19, "name": "治愈" }
        ],
        "episodeCount": 28,
        "broadcastType": { "id": 1, "name": "TV", "sortOrder": 0 },
        "adaptationType": { "id": 2, "name": "漫画改", "sortOrder": 0 },
        "airDate": "2023-09-29",
        "coverImageUrl": "https://lain.bgm.tv/pic/cover/l/13/c5/400602_ZI8Y9.jpg",
        "status": 3,
        "region": { "id": 1, "name": "日本" },
        "companies": [
          { "companyId": 5, "companyName": "MADHOUSE", "role": "制作", "sortOrder": 0 }
        ],
        "persons": [
          { "personId": 12, "personName": "示例人物", "role": "导演", "sortOrder": 0 }
        ],
        "externalLinks": [
          { "title": "Bangumi", "url": "https://bgm.tv/subject/400602", "sortOrder": 0 }
        ],
        "personalRating": 8.0,
        "series": { "id": 7, "name": "葬送的芙莉莲", "description": null }
      },
      {
        "id": 6,
        "name": "迷宫饭",
        "aliasNames": ["舌尖上的地下城"],
        "tags": [
          { "id": 17, "name": "冒险" },
          { "id": 18, "name": "奇幻" },
          { "id": 20, "name": "美食" }
        ],
        "episodeCount": 24,
        "broadcastType": { "id": 1, "name": "TV", "sortOrder": 0 },
        "adaptationType": { "id": 2, "name": "漫画改", "sortOrder": 0 },
        "airDate": "2024-01-04",
        "coverImageUrl": null,
        "status": 3,
        "region": { "id": 1, "name": "日本" },
        "companies": [
          { "companyId": 8, "companyName": "TRIGGER", "role": "制作", "sortOrder": 0 }
        ],
        "persons": [],
        "externalLinks": [],
        "personalRating": 9.0,
        "series": { "id": 3, "name": "迷宫饭", "description": null }
      },
      {
        "id": 20,
        "name": "药屋少女的呢喃",
        "aliasNames": ["药屋少女"],
        "tags": [
          { "id": 17, "name": "冒险" },
          { "id": 18, "name": "奇幻" },
          { "id": 21, "name": "推理" }
        ],
        "episodeCount": 24,
        "broadcastType": { "id": 1, "name": "TV", "sortOrder": 0 },
        "adaptationType": { "id": 2, "name": "漫画改", "sortOrder": 0 },
        "airDate": "2023-10-22",
        "coverImageUrl": null,
        "status": 3,
        "region": { "id": 1, "name": "日本" },
        "companies": [
          { "companyId": 5, "companyName": "MADHOUSE", "role": "制作", "sortOrder": 0 }
        ],
        "persons": [],
        "externalLinks": [],
        "personalRating": 7.5,
        "series": null
      }
    ]
  }
}
```

> `AnimePageVO` 与 `AnimeVO` 的区别：列表中将标签/放送类型/改编类型/地区/公司/系列展开为**完整对象**（含 name），而非仅存 ID，减少前端二次查询。公司信息额外包含 `companyName` 字段；公司和别名数组顺序与详情一致，放送类型和改编类型对象也包含 `sortOrder`。
> 列表内的 `tags` 按名称首字母正序（A–Z，`#` 在末尾）展示，同组按标签更新时间降序、ID 降序，不改变保存的标签关联。

**错误情况**

| 场景 | message |
|------|---------|
| pageSize 超范围 | pageSize 必须处于 1 到 100 之间 |
| 字典或标签 ID 无效 | broadcastTypeId / adaptationTypeId / regionId / tagIds 对应的资源不存在 |
| 关联筛选 ID 越界 | companyId / personId / seriesId 必须处于 1 到 2147483647 之间 |
| 关联筛选 ID 格式错误 | 显式空串、非数字或小数按现有参数绑定错误响应处理 |
| status 无效 | status 仅支持 1、2、3、4 |
| 日期格式错误 | broadcastStartDate / broadcastEndDate 格式错误，正确格式为 yyyy 或 yyyy-MM |
| 日期范围颠倒 | broadcastStartDate 不能晚于 broadcastEndDate |
| tagMatchMode 无效 | tagMatchMode 仅支持 ALL 或 ANY |
| sortBy 无效 | sortBy 仅支持 BROADCAST_DATE 或 PERSONAL_RATING |
| sortDirection 无效 | sortDirection 仅支持 ASC 或 DESC |
| ratingMin > ratingMax | ratingMin 不能大于 ratingMax |
| ratingMin / ratingMax 非整数或超出范围 | ratingMin必须为 0 到 10 的整数 / ratingMax必须为 0 到 10 的整数 |

动画创建、修改与分页统一按数值判断整数，8、8.0、8.00 等值均可接受，8.1、8.5、-1、10.1、11 拒绝，不进行截断或四舍五入。创建、修改的非法评分在作品及关联写入前拒绝，提示 `个人评分必须为 0 到 10 的整数`。0 是有效评分，null 表示未评分；更新省略或传 null 沿用删除当前 ANIME 评分记录的语义，不影响 BOOK 评分。评分范围筛选不命中未评分作品；0~0、0~10、10~10 均为合法区间。

响应保留实际存储数值，不对历史小数取整；历史数据若不符合整数规则，须由用户决定处理，后端不自动修正数据库。

---

### 1.5 根据 ID 获取动画详情

```http
GET /anime/{id}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 动画 ID |

**请求示例**

```http
GET /anime/15
```

**成功响应**：和创建/修改返回结构相同，`AnimeVO`（字段含 `id` 而非对象展开）。

动画创建、修改、详情和列表均返回有效布尔字段 `isPublic`，无独立状态记录时为 true。状态保存、筛选及部署前提见下方“作品公开状态”。

查看关联系列时，使用响应中的 `seriesId` 请求 `GET /series/{id}/works`（见 7.6）；没有系列时不需要发起该请求。

**错误情况**

| 场景 | message |
|------|---------|
| ID 不存在 | 此动画不存在 |

---

## 二、改编类型 `/adaptation-type`

本节及第三节放送类型使用相同的排序契约：`sortOrder` 为可选 Long，允许 -2147483648 至 2147483647 的整数（含负数）。创建未传或 null 默认 0；修改未传或 null 保留原值，显式 0 才归零。创建、修改、详情和列表响应均返回有效 `sortOrder`，全量列表按 `sort_order ASC, id ASC` 返回，同值不再按名称排序，存量 0 不做批量改写。

### 2.1 创建

```http
POST /adaptation-type/create
```

```json
{ "name": "轻小说改", "sortOrder": -1 }
→ { "code": 200, "data": { "id": 7, "name": "轻小说改", "sortOrder": -1 } }
```

### 2.2 修改

```http
POST /adaptation-type/update
```

```json
{ "id": 7, "name": "轻小说改编", "sortOrder": 0 }
```

### 2.3 批量删除

```http
POST /adaptation-type/delete
```

```json
[5, 6, 7]
```

动画、书籍引用预检查或数据库外键拒绝删除时，统一返回业务错误 `所选改编类型已被作品引用，不能删除`；任一类型被引用则整批拒绝删除。

### 2.4 根据 ID 获取

```http
GET /adaptation-type/1
```

返回对象：`{ "id": 1, "name": "原创", "sortOrder": 0 }`。

### 2.5 获取全部

```http
GET /adaptation-type/list
```

> 按 `sort_order ASC, id ASC`，每个对象包含 `id`、`name`、`sortOrder`。

---

## 三、放送类型 `/broadcast-type`

### 3.1 创建

```http
POST /broadcast-type/create
```

```json
{ "name": "TV特别篇", "sortOrder": 2 }
→ { "code": 200, "data": { "id": 6, "name": "TV特别篇", "sortOrder": 2 } }
```

### 3.2 修改

```http
POST /broadcast-type/update
```

请求示例：`{ "id": 6, "name": "TV特别篇", "sortOrder": 0 }`；不传 `sortOrder` 或传 null 时保留原排序。成功响应包含 `id`、`name`、`sortOrder`。

### 3.3 批量删除

```http
POST /broadcast-type/delete
```

### 3.4 根据 ID 获取

```http
GET /broadcast-type/1
```

返回对象：`{ "id": 1, "name": "TV", "sortOrder": 0 }`。

### 3.5 获取全部

```http
GET /broadcast-type/list
```

按 `sort_order ASC, id ASC`，每个对象包含 `id`、`name`、`sortOrder`。

---

## 四、地区 `/region`

### 4.1 创建

```http
POST /region/create
```

```json
{ "name": "韩国" }
→ { "code": 200, "data": { "id": 4, "name": "韩国" } }
```

### 4.2 修改

```http
POST /region/update
```

### 4.3 批量删除

```http
POST /region/delete
```

动画、书籍引用预检查或数据库外键拒绝删除时，统一返回业务错误 `所选地区已被作品引用，不能删除`；任一地区被引用则整批拒绝删除。

### 4.4 根据 ID 获取

```http
GET /region/1
```

### 4.5 获取全部

```http
GET /region/list
```

---

## 五、标签 `/tags`

### 5.1 创建

```http
POST /tags/create
```

```json
{ "name": "异世界" }
→ { "code": 200, "data": { "id": 11, "name": "异世界" } }
```

### 5.2 修改

```http
POST /tags/update
```

### 5.3 批量删除

```http
POST /tags/delete
```

### 5.4 根据 ID 获取

```http
GET /tags/1
```

### 5.5 分页查询

```http
GET /tags/page?pageNum=1&pageSize=10&name=科幻
```

传 `sortBy=NAME_INITIAL` 启用名称首字母排序，可通过 `sortDirection=ASC|DESC` 选择方向，规则见 7.5。不传 `sortBy` 沿用原查询顺序。

---

## 六、公司 `/companies`

### 6.1 创建

```http
POST /companies/create
```

```json
{
  "name": "京都动画",
  "description": "日本动画制作公司。"
}
```

### 6.2 修改

```http
POST /companies/update
```

### 6.3 批量删除

```http
POST /companies/delete
```

### 6.4 根据 ID 获取

```http
GET /companies/1
```

### 6.5 分页查询

```http
GET /companies/page?pageNum=1&pageSize=10&name=动画
```

> 默认按 `updated_at` 降序；传 `sortBy=NAME_INITIAL` 启用名称首字母排序，规则见 7.5。

---

## 七、系列 `/series`

### 7.1 创建

```http
POST /series/create
```

```json
{
  "name": "物语系列",
  "description": "由西尾维新创作的轻小说系列。"
}
```

### 7.2 修改

```http
POST /series/update
```

### 7.3 批量删除

```http
POST /series/delete
```

### 7.4 根据 ID 获取

```http
GET /series/1
```

### 7.5 分页查询

```http
GET /series/page?pageNum=1&pageSize=10&name=物语
```

> 默认按 `updated_at` 降序；传 `sortBy=NAME_INITIAL` 启用名称首字母排序。

公司、人物、系列和标签的 `NAME_INITIAL` 使用相同规则：沿用各自的名称筛选，对全部命中记录排序后再分页，`pageSize` 为 1～100。忽略名称前置空白，英文字母不分大小写，汉字取第一个字的拼音首字母，多音字采用拼音库的首个读音。传 `sortDirection=ASC`（默认）按 A–Z，传 `DESC` 按 Z–A，其他非空方向值返回参数错误。数字、符号、无 A–Z 首字母的记录在两个方向均归入末尾的 `#`；同组始终按更新时间降序、ID 降序。响应不增加分组字段；不传 `sortBy` 时沿用各自原排序，不读取方向参数。

编辑资料时，人员、公司、系列和标签选择器默认传 `sortBy=NAME_INITIAL&sortDirection=ASC`，不改变已有关系的署名顺序或保存规则。

### 7.6 查询系列及关联作品

```http
GET /series/7/works?pageNum=1&pageSize=20
```

动画和书籍详情使用已有的 `seriesId` 调用此接口。仅 ADMIN、EDITOR 可访问，沿用现有 JWT 和统一响应；不改变原有作品详情或系列详情的响应结构。

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 路径参数，系列 ID |
| pageNum | Long | 默认 1，至少为 1 |
| pageSize | Long | 默认 20，范围 1～100 |

成功响应的 `data`：

```json
{
  "series": { "id": 7, "name": "示例系列", "description": null },
  "works": {
    "pageNum": 1,
    "pageSize": 20,
    "total": 1,
    "pages": 1,
    "rows": [
      {
        "workType": "BOOK",
        "workId": 12,
        "name": "示例原作",
        "coverImageUrl": null,
        "releaseDate": "2020-04-01",
        "bookTypeName": "轻小说"
      }
    ]
  }
}
```

- 动画和书籍混合分页，包含当前作品；跳转或标记当前作品时，用 `workType` 与 `workId` 组合识别。`ANIME` 跳转动画详情，`BOOK` 跳转书籍详情。
- `releaseDate` 对应动画开播日期或书籍发表日期，按日期升序、空日期置后，同日期按系列条目 ID 升序。`releaseDate` 和 `coverImageUrl` 可为 null；动画的 `bookTypeName` 为 null，书籍类型没有匹配记录时也为 null。
- 仅返回存在于动画表、书籍表中的作品，跳过无对应作品的条目及未接入的旧类型。`total` 只计算可展示的作品。
- 系列存在但无作品时，`rows: []`、`total: 0`、`pages: 0`；超页返回空 `rows`，保留实际总数。系列不存在时沿用“此系列不存在”业务错误。

---

## 九、认证与鉴权

> 本模块不遵循前述"统一提供 5 类端点"的模式，只提供登录（`/admin/login/...`）与退出登录（`/admin/logout`）接口；账号数据来自 `user_account` 表，密码以 BCrypt 哈希存储。

### 9.1 前置配置：私有配置与 JWT 密钥

`acgbox-app/src/main/resources/application.yml` 只保留公共默认值，真实密钥一律写成占位符（如 `secret: ${ACGBOX_JWT_SECRET}`），实际取值来自仓库之外的私有配置文件：

| 文件 | 用途 |
|------|------|
| `config/application-secrets.example.yml` | 仓库内的示例，逐项带中文注释，不含真实值，可以提交 |
| `D:/ACGBox-config/application-secrets.yml`（本机路径示例） | 私有文件，不进入 Git，也不打进 JAR |

加载方式（两种等价，未加 `optional:`，文件不存在时启动直接失败）：

```text
# IDEA：AcgBoxApplication 启动配置的 VM options
-Dspring.config.additional-location=file:D:/ACGBox-config/application-secrets.yml
```

```bash
# JAR：本机
java -jar acgbox-app-0.0.1-SNAPSHOT.jar --spring.config.additional-location=file:D:/ACGBox-config/application-secrets.yml
# JAR：Linux
java -jar /opt/acgbox/acgbox-app.jar --spring.config.additional-location=file:/etc/acgbox/application-secrets.yml
```

私有文件里的写法是扁平的 `ACGBOX_*` 键（可带引号），Spring 用它们解析 `application.yml` 中的同名占位符：

```yaml
ACGBOX_JWT_SECRET: ""
ACGBOX_DB_URL: "jdbc:postgresql://localhost:5432/acgbox"
ACGBOX_DB_USERNAME: "postgres"
ACGBOX_DB_PASSWORD: ""
```

JWT 相关默认配置：

```yaml
jwt:
  secret: ${ACGBOX_JWT_SECRET}   # Base64 文本，解码后不少于 32 字节
  issuer: acgbox-admin
  audience: acgbox-admin
  expiration: 259200             # 新签发令牌的有效期，单位秒，默认 3 天
```

`JwtConfig` 会把 `secret` 按 Base64 解码，结果少于 32 字节时直接抛异常、应用启动失败。生成方式：

```bash
openssl rand -base64 32
```

把输出写入私有文件的 `ACGBOX_JWT_SECRET`，然后重启后端。更换密钥会使所有旧 JWT 校验失败，并使旧邮箱验证码的 HMAC 摘要失效。

> 同名系统环境变量、JVM 属性或命令行参数优先级高于私有文件，排查"改了文件不生效"时要检查所有启动来源。修改配置后必须重启，不是热更新。

### 9.2 用户名密码登录

```http
POST /admin/login/username
```

**请求体 `LoginDTO`**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| username | String | 是 | 服务层判空 | 登录用户名，查询时忽略大小写并去除首尾空格 |
| email | String | 否 | `@Email` | 本接口不读取该字段，传值也必须满足邮箱格式 |
| password | String | 是 | `@NotBlank` | 登录密码，与库中 BCrypt 哈希比对 |

> `@Email` 对空字符串不拦截，因此"未传"与"传空串"都不会被校验注解挡下，是否为空由服务层判断。两个登录接口各只读取 `username` 或 `email` 之一，另一个字段即使传了也不会参与查询。

**请求示例**

```json
{ "username": "admin", "password": "123456" }
```

**成功响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJhY2dib3gtYWRtaW4iLCJzdWIiOiIxIiwicm9sZSI6IkFETUlOIn0.xxx",
    "expiresIn": 259200,
    "id": 1,
    "username": "admin",
    "role": "ADMIN"
  }
}
```

**错误情况**

| 场景 | message |
|------|---------|
| 用户名为空 | 登录用户名不能为空 |
| 账号不存在或密码错误 | 账号或密码错误 |
| 账号已禁用 | 此账号已被禁用 |
| 角色不是 ADMIN 或 EDITOR | 此账号无管理端登录权限 |

> 账号不存在与密码错误共用同一条提示，刻意不区分，避免通过接口探测账号是否存在。以上错误均由 `BusinessException` 返回，HTTP 状态码仍是 `200`，响应体 `code` 为 `500`。

### 9.3 邮箱密码登录

```http
POST /admin/login/email-password
```

**请求体**：同 `LoginDTO`，本接口读取 `email`，不读取 `username`。

邮箱查询去除首尾空格后进行精确匹配，区分整个邮箱地址的大小写，必须与账号保存的邮箱一致。

**请求示例**

```json
{ "email": "admin@example.com", "password": "123456" }
```

**成功响应**：结构同 9.2。

**错误情况**：错误提示与 9.2 一致，仅"登录邮箱不能为空"替换"登录用户名不能为空"。

> `user_account.email` 允许为空，未设置邮箱的账号无法通过本接口登录。索引名 `uk_user_account_email_lower` 虽然带 `lower`，但建表脚本实际是对 `email` 原值建立的唯一索引，因此大小写不同的两个邮箱可以并存，唯一性判断也区分大小写。

### 9.4 令牌结构

登录返回的 `token` 是一个 HS256 签名的 JWT：

| 声明 | 取值 |
|------|------|
| `iss` | `jwt.issuer`，默认 `acgbox-admin` |
| `aud` | `jwt.audience`，默认 `acgbox-admin` |
| `sub` | 账号 ID |
| `iat` | 签发时间 |
| `exp` | 签发时间 + `jwt.expiration` 秒，默认 3 天（`259200`） |
| `role` | 账号角色，如 `ADMIN`、`EDITOR` |
| `token_version` | 签发时账号的令牌版本号，用于使旧令牌失效 |

服务端先校验签名、有效期、签发方与受众，再由 `JwtAccountAuthoritiesConverter` 用 `sub` 查询账号：账号必须存在、`enabled = true` 且 `token_version` 与令牌中的值一致，最后才按 `role` 声明授予 `ROLE_ADMIN`、`ROLE_EDITOR` 或 `ROLE_USER`。任一项不满足都按 401 拒绝，权限不读取数据库中的 `role` 字段，角色变更通过递增 `token_version` 生效（见 [10.7](#107-令牌版本与强制下线)）。

### 9.5 携带令牌访问业务接口

```http
GET /anime/page?pageNum=1&pageSize=10
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.xxx.yyy
```

`SecurityConfig` 的访问规则分三层：`POST /admin/login/username`、`POST /admin/login/email-password`、`POST /admin/login/email-code/send` 与 `POST /admin/login/email-code` 匿名可访问，只放行这些明确路径的 POST；`/admin/users/**` 与 `/admin/system/**` 仅 `ROLE_ADMIN`；其余请求 `ROLE_ADMIN` 或 `ROLE_EDITOR` 之一均可。角色为 `USER` 的账号能通过 JWT 认证，但访问任何后台接口都会得到 403。表单登录、HTTP Basic 与 CSRF 均已关闭，会话策略为 `STATELESS`，即不存在 Session 登录态，每个受保护请求都必须自带令牌。

前端 `ACGBox-UI` 已实现完整链路：`/login` 页面提供用户名与邮箱两种登录模式，调用 `src/api/auth.ts`；登录成功后 `src/utils/auth.ts` 把令牌写入 `sessionStorage` 的 `acgbox-token`，同时记录 `acgbox-token-expires-at`（毫秒时间戳）与 `acgbox-user`（id / username / role）；`router/index.ts` 的路由守卫对 `requiresAuth` 路由校验令牌，未登录时跳转 `/login` 并带上 `redirect`，对声明了 `meta.roles` 的路由再做角色过滤（目前只有 `/admin/users` 限定 `ADMIN`，`EDITOR` 直接访问会被重定向到 `/admin/dashboard`，侧边菜单也不显示该项）；`src/api/http.ts` 在每次请求时读取令牌拼成 `Authorization: Bearer <token>`，遇到 401 会清理登录态并派发 `acgbox:unauthorized` 事件，由路由跳回登录页；退出登录调用 `src/api/auth.ts` 的 `logout()`（即 `/admin/logout`），成功后清理 `sessionStorage` 并回到 `/login`；若接口返回 401（例如令牌已失效），按登录失效处理直接回登录页，其他错误则保留本地登录态并在用户菜单中提示。开发环境下 Vite 将 `/api` 前缀代理到 `http://localhost:8080` 并去掉该前缀，因此前端调用 `/api/anime/page` 对应后端 `/anime/page`。自助账号页面在 `/admin/account`（路由名 `admin-account`，`meta.roles` 为 `ADMIN`、`EDITOR`，不在侧边菜单显示，从用户菜单的"账号编辑"进入）；修改密码、绑定/换绑或解绑邮箱成功后，页面用响应中的 `LoginVO` 调 `saveLogin()` 覆盖本地令牌与到期时间，停留在当前页面继续使用，只有接口失败才在页面内提示。

### 9.6 鉴权失败的响应形态

这一部分与业务接口的"HTTP 200 + `Result` 包装"约定不同，响应体为空，只有 HTTP 状态码：

| 场景 | HTTP 状态码 | 说明 |
|------|-------------|------|
| 未携带 `Authorization` 请求头 | 401 | 响应头含 `WWW-Authenticate: Bearer`，不会重定向到登录页 |
| 令牌签名错误、格式非法、已过期，或 `iss` / `aud` 不匹配 | 401 | 解码阶段即拒绝，不查询数据库 |
| 令牌缺少 `token_version`、版本非整数或与库中不一致 | 401 | 账号被改过资料或密码后，旧令牌立即失效 |
| 令牌 `sub` 非法、账号已被删除或 `enabled = false` | 401 | 认证阶段即拒绝 |
| 角色不足以访问该路径 | 403 | 例如 `EDITOR` 访问 `/admin/users/**`，或 `USER` 访问任何后台接口 |

客户端应按 HTTP 状态码处理：收到 `401` 时清理本地令牌并引导重新登录。`ResultCode` 中预留的 `401 UNAUTHORIZED`、`403 FORBIDDEN` 目前不会被业务代码用到，它们的 `Result` 响应体形态与上表无关。

### 9.7 退出登录

```http
POST /admin/logout
```

> 路径挂在 `/admin` 下而不是 `/admin/login` 下，但属于同一认证模块。需要携带有效令牌，`ADMIN` 与 `EDITOR` 均可调用。

**请求体**：无。接口不读取请求体，即使传入 `id`、`tokenVersion` 也会被忽略。

**请求示例**

```http
POST /admin/logout
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.xxx.yyy
```

**成功响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

**业务规则**

- 实现方式是 `token_version + 1`，没有令牌黑名单，也没有服务端会话，因此服务端不需要保存任何退出状态。
- 更新语句同时带 `id = 令牌 sub` 与 `token_version = 令牌中的版本` 两个条件，只作用于当前账号、当前版本；该账号在同一版本下签发的**所有**令牌会一起失效，不限于本次请求使用的这一个。
- 若账号在认证通过之后、执行退出之前被其他操作改过版本，本次更新影响 0 行，接口仍返回成功，不会误伤新版本令牌。
- 同一令牌重复调用会返回 401，因为第一次调用后该版本已失效；重复退出不是幂等成功。
- 只接受 `POST`，`GET /admin/logout` 返回 405 Method Not Allowed。
- 不创建 HttpSession，响应也不带 `Location` 头；客户端必须自行清理本地保存的令牌，服务端无法代为删除。

**错误情况**

| 场景 | HTTP 状态码 |
|------|-------------|
| 未携带令牌、令牌签名错误或已过期 | 401 |
| 账号已被禁用 | 401 |
| 账号已被删除，或令牌版本与库中不一致 | 401 |
| 角色为 `USER` | 403 |
| 用 `GET` 调用 | 405 |

---

### 9.8 邮箱验证码登录

先发送登录验证码，无需密码或 JWT：

```http
POST /admin/login/email-code/send
Content-Type: application/json
```

```json
{ "email": "Editor@Example.com" }
```

发送成功响应沿用 `{ "code": 200, "message": "操作成功", "data": null }`，不返回验证码、账号资料或 JWT。
仅向已有账号、邮箱已验证（`email_verified_at` 非空）、账号已启用且角色为 ADMIN 或 EDITOR 的邮箱发信。
账号不存在、邮箱未验证、账号禁用或权限不符时，统一返回相同的正常响应但不发信，客户端不应将成功响应视为一定已发出邮件。

收到邮件后提交验证码：

```http
POST /admin/login/email-code
Content-Type: application/json
```

```json
{ "email": "Editor@Example.com", "verificationCode": "003821" }
```

两个接口共用 `EmailCodeLoginDTO`，原密码登录的 `LoginDTO` 不变：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | String | 是 | `@NotBlank`、`@Email`、最大 254 字符，整个邮箱区分大小写 |
| verificationCode | String | 登录时必填 | 六位数字，保留开头的零；发送请求可以不传，登录服务校验必填 |

验证码登录成功响应沿用 9.2 的 `LoginVO`：token、expiresIn、id、username、role；使用数据库当前账号和版本签发 JWT，不使用客户端传入的 ID、角色或版本。
登录不自动注册账号，不修改 `email_verified_at` 或 `token_version`，也不会使其他正常登录令牌失效。

**复用与隔离规则**

- 复用 `EmailVerificationService.sendCode()`、`verifyAndConsume()`、现有五个 Lua 脚本、邮件配置和 JWT 签发逻辑，不新增表或配置项。
- 登录使用独立的 `LOGIN` 用途，验证码绑定数据库中的账号 ID、当前 `token_version` 和邮箱；BIND、CHANGE、UNBIND 验证码不能用于登录，LOGIN 验证码也不能用于编辑邮箱。
- 六位数字、五分钟有效、单条验证码五次错误上限；同一账号 LOGIN 用途 60 秒冷却，所有用途合计一小时最多 10 次。
- 提交时重新读取账号状态和版本，禁用、删除、取消邮箱验证、改邮箱或角色、退出登录及其他版本变化后，之前发送的验证码无法继续登录。
- 验证码正确后立即消费，再签发 JWT；重放会失败，签发失败也不会恢复已消费的验证码，需要重新发送。
- 未配置 SMTP、Redis 不可用、邮件发送失败及功能未开启时，符合条件账号的请求返回相应业务错误，不能绕过验证码直接签发 JWT。

**错误情况**

| 场景 | message |
|------|---------|
| 邮箱为空、格式错误或超长 | 登录邮箱不能为空 / 邮箱格式不正确 / 邮箱长度不能超过 254 个字符 |
| 登录未提交验证码 | 登录验证码不能为空 |
| 非六位数字验证码 | 验证码必须为 6 位数字 |
| 账号不符合条件，或验证码错误、过期、已消费 | 验证码错误或已过期，请重新获取 |
| 发送频繁 / 小时次数超限 | 验证码发送过于频繁，请 60 秒后重试 / 验证码发送次数已达上限，请稍后重试 |
| 邮件服务或 Redis 故障 | 验证码邮件发送失败，请稍后重试 / 验证码服务暂不可用，请稍后重试 |
| 功能未开启 | 邮箱验证码功能尚未启用 |

上述校验和业务错误继续使用 HTTP 200、响应体 code 500；不改变 ResultCode 或原密码登录的错误约定。
前端调用这两个接口时不携带旧 JWT，成功登录后复用 `saveLogin(data)`，不要把验证码或令牌打印到日志。
邮箱与账号状态的普通响应保持一致不等于完全消除时序或限流侧信道，生产环境还应在网关对公开发信、登录路径配置 IP 频率和总量限制；本次后端不新增 IP 限流或验证码登录页面。
测试使用模拟的数据库、Redis 和 SMTP，真实收信与 Lua 运行仍需部署环境联调。

## 十、用户账号管理 `/admin/users`

> 六个端点全部仅 `ADMIN` 可访问，`EDITOR` 与 `USER` 调用一律返回 HTTP 403。支持创建、查询、修改、重置密码与删除，但**接口层面既不能创建 `ADMIN`，也不能删除 `ADMIN`**，所以初始管理员仍然只能直接写数据库（见下方说明）。没有批量删除接口；退出登录在 `/admin/logout`，见 [9.7](#97-退出登录)。

`user_account` 表带部分唯一索引 `uk_user_account_single_admin`（`WHERE role = 'ADMIN'`），库里最多只允许存在一个 `ADMIN`，这一约束同时构成"不允许把其他账号提升为管理员"的数据库兜底。建表脚本不包含任何账号数据，初始化第一个管理员需要自行写入一条 `INSERT`，且 `password_hash` 必须是 BCrypt 哈希：

```sql
INSERT INTO user_account (username, email, password_hash, role, enabled)
VALUES ('admin', 'admin@example.com', '$2a$10$<BCrypt 哈希>', 'ADMIN', true);
```

### 10.1 创建用户账号

```http
POST /admin/users/create
```

**请求体 `UserAccountCreateDTO`**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| username | String | 是 | `@NotBlank` `@Size(max=50)` `@Pattern([^@]+)` | 保留大小写，提交时去除首尾空格，不允许包含 `@` |
| email | String | 否 | `@Email` `@Size(max=254)` | 不传或传空串时不写入邮箱 |
| password | String | 是 | `@NotBlank` | 初始密码，原样使用、不 trim，不超过 72 个 UTF-8 字节 |
| role | String | 是 | `@Pattern(EDITOR\|USER)` | 只能创建 `EDITOR` 或 `USER`，不接受 `ADMIN` |
| enabled | Boolean | 是 | `@NotNull` | 是否启用，可创建已禁用账号 |

**请求示例**

```json
{
  "username": "NewEditor",
  "email": "editor@example.com",
  "password": "InitialPassword!",
  "role": "EDITOR",
  "enabled": true
}
```

**成功响应**：`data` 为新建账号的 `UserAccountVO`，字段与 10.2 中 `rows` 的元素相同。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 20,
    "username": "NewEditor",
    "email": "editor@example.com",
    "role": "EDITOR",
    "enabled": true,
    "emailVerifiedAt": null,
    "createdAt": "2026-10-11T10:00:00",
    "updatedAt": "2026-10-11T10:00:00"
  }
}
```

**业务规则**

- `token_version` 由服务端固定初始化为 `0`，`email_verified_at` 为 `null`，`created_at` 与 `updated_at` 取同一时刻；请求体里附加的 `id`、`tokenVersion`、`passwordHash`、`createdAt`、`updatedAt` 等字段一律被忽略。
- 密码不做 trim：`" InitialPassword! "` 与 `"InitialPassword!"` 是两个不同的密码。
- 用户名唯一性按 `lower(username)` 比较，邮箱唯一性按 `email` 原值比较。
- **创建接口不校验操作者密码**，与 10.4 改邮箱、10.5 重置密码、10.6 删除不同。

**错误情况**

| 场景 | message |
|------|---------|
| role 缺失或为空 | 账号角色不能为空 |
| role 为 `ADMIN`、其他取值或大小写不符（如 `editor`） | 创建账号仅支持 EDITOR、USER 角色 |
| 未传初始密码 | 初始密码不能为空 |
| 用户名重复 | 此登录用户名已存在 |
| 邮箱重复 | 此邮箱已被使用 |
| 初始密码超过 72 个 UTF-8 字节 | 密码不能超过 72 个 UTF-8 字节 |
| 并发写入撞唯一索引 | 登录用户名或邮箱已被使用 |

### 10.2 分页查询用户账号

```http
GET /admin/users/page
```

**Query 参数 `UserAccountPageDTO`**（继承 `PageDTO`）

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| pageNum | Long | 1 | 当前页码，必须 ≥ 1 |
| pageSize | Long | 20 | 每页条数，1~100 |
| username | String | — | 用户名模糊查询，`ILIKE`，忽略大小写 |
| email | String | — | 邮箱模糊查询，`ILIKE`，忽略大小写 |
| role | String | — | 角色精确匹配，取值为 `ADMIN`、`EDITOR`、`USER`；不校验合法性，传其他值只会查不到数据 |
| enabled | Boolean | — | 启用状态精确匹配，不传时查询全部状态 |

**请求示例**

```http
GET /admin/users/page?pageNum=1&pageSize=10&role=EDITOR&enabled=true
```

**成功响应**：`PageVO<UserAccountVO>`，按 `updated_at` 降序、`id` 降序排列。`UserAccountVO` 字段为 `id`、`username`、`email`、`role`、`enabled`、`emailVerifiedAt`、`createdAt`、`updatedAt`，不含 `password_hash` 与 `token_version`。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "pageNum": 1,
    "pageSize": 10,
    "total": 1,
    "pages": 1,
    "rows": [
      {
        "id": 2,
        "username": "EditorCase",
        "email": "Editor@Example.com",
        "role": "EDITOR",
        "enabled": true,
        "emailVerifiedAt": null,
        "createdAt": "2026-09-01T12:00:00",
        "updatedAt": "2026-10-02T12:00:00"
      }
    ]
  }
}
```

> 这里的用户名、邮箱模糊查询用 `ILIKE` 忽略大小写，而登录时邮箱要求大小写完全一致，两处语义并不相同。

**错误情况**

| 场景 | message |
|------|---------|
| pageNum < 1 | pageNum 必须大于等于 1 |
| pageSize 超范围 | pageSize 必须处于 1 到 100 之间 |

### 10.3 根据 ID 获取用户账号

```http
GET /admin/users/{id}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 用户账号 ID，必须 ≥ 1 |

**请求示例**

```http
GET /admin/users/2
```

**成功响应**：`data` 为单个 `UserAccountVO`，字段同 10.2 中 `rows` 的元素。

**错误情况**

| 场景 | message |
|------|---------|
| ID 非法 | 用户账号 ID 必须大于等于 1 |
| 账号不存在 | 此用户账号不存在 |

### 10.4 修改用户账号

```http
POST /admin/users/update
```

**请求体 `UserAccountDTO`**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| id | Long | 是 | `@NotNull` `@Min(1)` | 目标账号 ID |
| username | String | 是 | `@NotBlank` `@Size(max=50)` `@Pattern([^@]+)` | 保留大小写，提交时去除首尾空格，不允许包含 `@` |
| email | String | 否 | `@Email` `@Size(max=254)` | 传 `null` 或空串表示清空邮箱 |
| role | String | 是 | `@Pattern(ADMIN\|EDITOR\|USER)` | 目标角色 |
| enabled | Boolean | 是 | `@NotNull` | 是否启用 |
| currentPassword | String | 条件必填 | 无注解，服务层校验 | **当前登录管理员本人**的密码，仅在 `email` 实际变化时要求 |

**请求示例**

```json
{
  "id": 2,
  "username": "EditorCase",
  "email": "new@example.com",
  "role": "EDITOR",
  "enabled": true,
  "currentPassword": "admin-password"
}
```

**请求示例（仅改启用状态，不改邮箱）**

```json
{ "id": 2, "username": "EditorCase", "email": "Editor@Example.com", "role": "EDITOR", "enabled": false }
```

**业务规则**

- 操作者身份取自 JWT 的 `sub`，请求体里额外的 `operatorId` 等字段会被忽略。
- 目标账号角色为 `ADMIN` 时，不允许改成其他角色，也不允许 `enabled = false`。
- 目标账号角色不是 `ADMIN` 时，不允许改成 `ADMIN`。
- 只有 `email` 真正变化才校验 `currentPassword`；`username`、`role`、`enabled` 单独变化不需要密码。
- `email` 变化会同时把 `email_verified_at` 置空。
- 四项字段都没有实际变化时直接返回当前数据，不写库、不递增版本号。
- 有变化时，`token_version` 自增 1 与 `updated_at` 在同一条 `UPDATE` 中写入；若期间账号被并发修改（版本不匹配），本次更新失败。
- 唯一性：用户名按 `lower(username)` 比较，邮箱按 `email` 原值比较。

**成功响应**：`data` 为修改后的 `UserAccountVO`，`currentPassword` 不会出现在任何响应里。

**错误情况**

| 场景 | message |
|------|---------|
| 目标账号不存在 | 此用户账号不存在 |
| 把管理员降级或禁用 | 管理员不能降级或禁用 |
| 把其他账号改成 ADMIN | 不允许将其他账号修改为管理员 |
| 改了邮箱但没传 currentPassword | 管理员密码不能为空 |
| 管理员密码错误 | 管理员密码错误 |
| 操作者账号已失效 | 管理员身份已失效，请重新登录 |
| 用户名与他人重复 | 此登录用户名已存在 |
| 邮箱与他人重复 | 此邮箱已被使用 |
| 并发修改冲突 | 账号信息已发生变化，请刷新后重试 |
| 并发写入撞唯一索引 | 登录用户名或邮箱已被使用 |
| role 取值非法 | 账号角色仅支持 ADMIN、EDITOR、USER |

### 10.5 重置用户账号密码

```http
POST /admin/users/reset-password
```

**请求体 `UserAccountPasswordDTO`**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| id | Long | 是 | `@NotNull` `@Min(1)` | 需要重置密码的账号 ID |
| currentPassword | String | 是 | `@NotBlank` | **当前登录管理员本人**的密码，用于二次验证，不是目标账号的原密码 |
| newPassword | String | 是 | `@NotBlank` | 目标账号的新密码，原样使用，不 trim、不转换大小写 |

**请求示例**

```json
{ "id": 2, "currentPassword": "admin-password", "newPassword": "new-user-password" }
```

**业务规则**

- 只验证操作者身份与管理员本人密码，不校验目标账号的原密码，因此可用于找回密码。
- 新密码超过 72 个 UTF-8 字节会被拒绝（BCrypt 只使用前 72 字节）。
- 新密码与目标账号现有密码相同时直接返回成功，不写库、不递增版本号。
- 写入成功后 `password_hash` 更新为新的 BCrypt 哈希，`token_version` 自增 1。

**成功响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

**错误情况**

| 场景 | message |
|------|---------|
| 目标账号不存在 | 此用户账号不存在 |
| 未传 currentPassword | 管理员密码不能为空 |
| 管理员密码错误 | 管理员密码错误 |
| 操作者账号已失效 | 管理员身份已失效，请重新登录 |
| 新密码过长 | 新密码不能超过 72 个 UTF-8 字节 |
| 并发修改冲突 | 账号信息已发生变化，请刷新后重试 |

### 10.6 删除用户账号

```http
POST /admin/users/delete
```

**请求体 `UserAccountDeleteDTO`**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| id | Long | 是 | `@NotNull` `@Min(1)` | 需要删除的账号 ID |
| currentPassword | String | 是 | `@NotBlank` | **当前登录管理员本人**的密码，不是目标账号的密码 |

**请求示例**

```json
{ "id": 2, "currentPassword": "admin-password" }
```

**成功响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

**业务规则**

- 物理删除（`DELETE`），不是软删除，也没有回收站。
- 不允许删除 `ADMIN` 账号，包括正在操作的管理员自己。
- 删除语句同时带上 `role <> 'ADMIN'` 与 `token_version = 读取时的值`，若目标账号在读取与删除之间被他人修改，删除影响 0 行并报错。
- 删除后该账号的旧令牌会在下一次请求时因账号不存在被拒（401），不需要再递增版本号。
- 一次只能删除一个账号，没有批量删除接口。

**错误情况**

| 场景 | message |
|------|---------|
| ID 非法 | 用户账号 ID 必须大于等于 1 |
| 目标账号不存在 | 此用户账号不存在 |
| 目标是管理员账号 | 不允许删除管理员账号 |
| 未传 currentPassword | 管理员密码不能为空 |
| 管理员密码错误 | 管理员密码错误 |
| 操作者账号已失效 | 管理员身份已失效，请重新登录 |
| 并发修改冲突 | 账号信息已发生变化，请刷新后重试 |

### 10.7 令牌版本与强制下线

`user_account.token_version` 是这套鉴权体系的撤销开关，签发的 JWT 会携带当时的值，之后每次请求都会与库中比对：

| 操作 | 对已签发令牌的影响 |
|------|---------------------|
| 通过 `/update` 修改用户名、邮箱、角色或启用状态（任一实际变化） | `token_version` + 1，该账号所有旧令牌立即失效 |
| 通过 `/reset-password` 重置密码 | `token_version` + 1，该账号所有旧令牌立即失效 |
| 通过 `/admin/logout` 退出登录 | `token_version` + 1，该账号同一版本下签发的所有令牌立即失效 |
| 通过 `/delete` 删除账号 | 记录被直接删除，不改版本号；旧令牌因账号不存在而失效（401） |
| 通过 `/admin/account/change-password` 修改自己的密码 | `token_version` + 1，旧令牌全部失效，本次响应返回新版本 JWT；密码未变时不递增版本，仍返回登录信息 |
| 通过 `/admin/account/email` 绑定或换绑邮箱 | `token_version` + 1，旧令牌全部失效，本次响应返回新版本 JWT |
| 通过 `/admin/account/email/unbind` 解绑邮箱 | `token_version` + 1，旧令牌全部失效，本次响应返回新版本 JWT |
| 通过 `/admin/account/email/code` 发信、`/email/verify-current` 验证原邮箱 | 不递增，当前登录与其他设备都不受影响 |
| 直接改库把 `enabled` 置为 `false` | 不改版本号，但认证阶段直接拒绝，旧令牌立即失效 |
| 之后直接改库把 `enabled` 置回 `true` | 版本号未变，未过期的旧令牌会重新可用 |

> 最后两行只出现在手工改库的场景：走接口的启用/禁用都会递增 `token_version`，旧令牌不会复活。若希望"禁用即永久失效"，应统一走接口修改，或额外在启用时递增版本号。

---

## 十一、当前账号自助管理 `/admin/account`

> 六个端点要求已登录，`ADMIN` 与 `EDITOR` 可用，`USER` 返回 403。操作对象只取自 JWT 的 `sub`，请求里传其他账号 ID 无效。只能改自己的密码与邮箱，**不能修改用户名、角色、启用状态**。其中修改密码、绑定/换绑邮箱、解绑邮箱三个写接口成功后会在 `data` 中返回新的登录令牌（`LoginVO`），当前页面替换令牌后无需重新登录，详见 [11.9](#119-修改后的登录续接)。

与第十章的区别：

| 能力 | `/admin/users`（管理员管理他人） | `/admin/account`（管理自己） |
|------|----------------------------------|------------------------------|
| 访问角色 | 仅 `ADMIN` | `ADMIN`、`EDITOR` |
| 目标账号 | 路径或请求体指定 | 只能是 JWT 对应的账号 |
| 修改密码 | 校验操作者管理员密码 | 校验目标账号自己的当前密码 |
| 修改邮箱 | 直接提交 + 管理员密码 | 必须邮箱验证码；换绑还需先验证原邮箱 |
| 用户名 / 角色 / 启用状态 | 可修改 | 不可修改 |

### 11.1 前置配置：邮箱验证码（SMTP + Redis）

邮箱验证码开关默认关闭，关闭时只有邮箱相关接口报"邮箱验证码功能尚未启用"，查询账号、修改密码不受影响；开关打开但未配置发件人时报"邮件服务尚未配置"。相关配置同样写在私有文件中：

| 私有文件键名 | 含义 | 默认值 |
|---|---|---|
| `ACGBOX_ACCOUNT_EMAIL_ENABLED` | 是否启用邮箱验证码 | `false` |
| `ACGBOX_MAIL_HOST` | SMTP 服务器地址 | 空 |
| `ACGBOX_MAIL_PORT` | SMTP 端口 | `587` |
| `ACGBOX_MAIL_USERNAME` | SMTP 认证账号 | 空 |
| `ACGBOX_MAIL_PASSWORD` | SMTP 密码或服务商授权码 | 空 |
| `ACGBOX_MAIL_FROM` | 发件地址 | 回退为 `ACGBOX_MAIL_USERNAME` |
| `ACGBOX_MAIL_STARTTLS` | 是否启用并强制要求 STARTTLS | `true` |
| `ACGBOX_MAIL_SSL` | 是否使用隐式 TLS | `false` |
| `ACGBOX_REDIS_HOST` | Redis 地址 | `localhost` |
| `ACGBOX_REDIS_PORT` | Redis 端口 | `6379` |
| `ACGBOX_REDIS_USERNAME` | Redis ACL 用户名 | 空 |
| `ACGBOX_REDIS_PASSWORD` | Redis 密码 | 空 |
| `ACGBOX_REDIS_DATABASE` | Redis 数据库编号 | `0` |

- 常见端口组合：587 用 `STARTTLS=true、SSL=false`，465 用 `STARTTLS=false、SSL=true`，以邮件服务商要求为准。
- SMTP 连接、读取、写入超时均为 5 秒，Redis 连接与执行超时均为 3 秒。
- 邮箱验证码状态只放在 Redis，**不需要修改数据库表结构**。

### 11.2 查询自己的账号

```http
GET /admin/account
```

无请求参数与请求体，返回当前令牌对应账号的 `UserAccountVO`，字段同 [10.2](#102-分页查询用户账号) 中 `rows` 的元素，不含 `passwordHash` 与 `tokenVersion`。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 2,
    "username": "Editor",
    "email": "Editor@Example.com",
    "role": "EDITOR",
    "enabled": true,
    "emailVerifiedAt": "2026-10-20T09:30:00",
    "createdAt": "2026-09-01T12:00:00",
    "updatedAt": "2026-10-20T09:30:00"
  }
}
```

**错误情况**：未携带或无效令牌返回 401；`USER` 角色返回 403。

### 11.3 修改自己的密码

```http
POST /admin/account/change-password
```

**请求体 `UserAccountChangePasswordDTO`**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| currentPassword | String | 是 | `@NotBlank` | 当前账号的原密码 |
| newPassword | String | 是 | `@NotBlank` | 新密码，原样使用、不 trim，不超过 72 个 UTF-8 字节 |

**请求示例**

```json
{ "currentPassword": "old-password", "newPassword": "NewPassword!" }
```

**业务规则**

- 当前密码必须正确，与新密码是否相同无关。
- 新旧密码相同时不写数据库、不递增 `token_version`，仍返回当前版本的登录信息。
- 实际修改后 `token_version` + 1，该账号所有旧令牌立即失效；本次响应返回新版本 JWT，前端立即替换登录信息并停留在当前页面，其他设备下次请求会收到 401。

**成功响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "修改成功后签发的新登录 JWT",
    "expiresIn": 259200,
    "id": 2,
    "username": "Editor",
    "role": "EDITOR"
  }
}
```

**错误情况**

| 场景 | message |
|------|---------|
| 未传当前密码 | 当前密码不能为空 |
| 未传新密码 | 新密码不能为空 |
| 当前密码错误 | 当前密码错误 |
| 新密码超长 | 新密码不能超过 72 个 UTF-8 字节 |
| 账号状态或版本已在认证后变化 | 登录状态已失效，请重新登录 |
| 并发修改冲突 | 账号信息已发生变化，请刷新后重试 |

### 11.4 发送邮箱验证码

```http
POST /admin/account/email/code
```

**请求体 `UserAccountEmailCodeDTO`**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| purpose | String | 是 | `@NotBlank` `@Pattern(BIND\|CHANGE\|UNBIND)` | 用途：绑定新邮箱 / 确认原邮箱换绑 / 确认解绑 |
| email | String | BIND 必填 | `@Email` `@Size(max=254)` | 待绑定或待验证的邮箱；`CHANGE`、`UNBIND` 忽略此字段 |
| emailChangeToken | String | 换绑时必填 | `@Pattern([A-Za-z0-9_-]{43})` | 换绑凭证，来自 11.5 |

**用途说明**

| purpose | 收件地址 | 是否需要换绑凭证 |
|---|---|---|
| `BIND` | 请求体 `email` | 账号已绑定其他邮箱时必须提供，否则报"请先验证原邮箱后再换绑" |
| `CHANGE` | 数据库中的原邮箱，无法由前端指定 | 否 |
| `UNBIND` | 数据库中的原邮箱 | 否 |

**请求示例（绑定或验证新邮箱）**

```json
{ "purpose": "BIND", "email": "New@Example.com" }
```

**请求示例（向原邮箱发送换绑验证码）**

```json
{ "purpose": "CHANGE" }
```

**请求示例（换绑流程中向新邮箱发送验证码）**

```json
{
  "purpose": "BIND",
  "email": "New@Example.com",
  "emailChangeToken": "替换为 11.5 返回的实际凭证"
}
```

**业务规则**

- 发送成功不递增 `token_version`，当前登录与其他设备都不受影响。
- 同一账号同一用途每 60 秒只能发送一次；同一账号所有用途合计每小时最多 10 次。
- 同一邮箱重新发送会覆盖旧验证码；不同邮箱、不同用途的验证码分别保存。
- 发送失败时会删除本次验证码摘要，但保留发送限流计数，重试仍需等待冷却。

**成功响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

**错误情况**

| 场景 | message |
|------|---------|
| 用途为空或取值非法 | 验证码用途不能为空 / 验证码用途仅支持 BIND、CHANGE、UNBIND |
| BIND 未传邮箱或邮箱为空串 | 邮箱不能为空 |
| 邮箱格式或长度非法 | 邮箱格式不正确 / 邮箱长度不能超过 254 个字符 |
| 账号尚无邮箱却请求 CHANGE/UNBIND | 当前账号尚未绑定邮箱 |
| 目标邮箱就是当前已绑定并验证的邮箱 | 此邮箱已绑定并验证 |
| 目标邮箱已被其他账号占用 | 此邮箱已被使用 |
| 已绑定其他邮箱但未带换绑凭证 | 请先验证原邮箱后再换绑 |
| 换绑凭证无效或过期 | 邮箱换绑凭证无效或已过期，请重新验证原邮箱 |
| 60 秒内重复发送 | 验证码发送过于频繁，请 60 秒后重试 |
| 一小时内超过 10 次 | 验证码发送次数已达上限，请稍后重试 |
| Redis 不可用 | 验证码服务暂不可用，请稍后重试 |
| SMTP 投递失败 | 验证码邮件发送失败，请稍后重试 |
| 开关关闭 / 未配置发件人 | 邮箱验证码功能尚未启用 / 邮件服务尚未配置 |

### 11.5 换绑第一步：验证原邮箱

```http
POST /admin/account/email/verify-current
```

**请求体 `UserAccountVerifyEmailDTO`**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| currentEmailCode | String | 是 | `@NotBlank` `@Pattern([0-9]{6})` | 原邮箱收到的 `CHANGE` 验证码 |

**请求示例**

```json
{ "currentEmailCode": "654321" }
```

**成功响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "emailChangeToken": "替换为接口返回的实际换绑凭证",
    "expiresIn": 300
  }
}
```

**业务规则**

- 原邮箱验证码验证成功后立即消费；此步骤只签发凭证，**不解除原邮箱绑定、不递增版本号**。
- 凭证为 32 字节随机数的 Base64URL 编码（43 个字符），有效期 5 分钟，绑定当前账号、当前 `token_version` 与当前原邮箱。
- 同一账号、版本、原邮箱只保留最近一次签发的凭证，重复验证会覆盖旧凭证。
- 凭证不是登录 JWT，前端只在本次换绑流程中暂存，不要放进 `Authorization` 头、URL 或日志。

**错误情况**

| 场景 | message |
|------|---------|
| 未传验证码 | 原邮箱验证码不能为空 |
| 验证码不是 6 位数字 | 验证码必须为 6 位数字 |
| 账号尚未绑定邮箱 | 当前账号尚未绑定邮箱 |
| 验证码错误或已过期 | 验证码错误或已过期，请重新获取 |
| Redis 不可用 / 功能关闭 | 验证码服务暂不可用，请稍后重试 / 邮箱验证码功能尚未启用 |

### 11.6 绑定或换绑邮箱

```http
POST /admin/account/email
```

**请求体 `UserAccountEmailDTO`**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| email | String | 是 | `@NotBlank` `@Email` `@Size(max=254)` | 待绑定邮箱，保留大小写 |
| newEmailCode | String | 是 | `@NotBlank` `@Pattern([0-9]{6})` | 新邮箱收到的 `BIND` 验证码 |
| emailChangeToken | String | 换绑时必填 | `@Pattern([A-Za-z0-9_-]{43})` | 11.5 返回的换绑凭证；首次绑定不需要 |

**请求示例（首次绑定）**

```json
{ "email": "New@Example.com", "newEmailCode": "123456" }
```

**请求示例（换绑已有邮箱）**

```json
{
  "email": "New@Example.com",
  "newEmailCode": "123456",
  "emailChangeToken": "替换为 11.5 返回的实际凭证"
}
```

**业务规则**

- 账号没有邮箱（`email` 为 `null`）时走首次绑定，只需 `BIND` 验证码。
- 账号已有邮箱且 `emailVerifiedAt` 为空、提交的又是同一个邮箱时，可走同一流程补做验证。
- 提交的邮箱与现有邮箱不同即为换绑，必须先完成 11.5 拿到凭证；校验顺序为"换绑凭证 → 新邮箱验证码 → 消费凭证 → 保存邮箱"。
- 保存时把 `email` 与 `email_verified_at` 一起写入，并递增 `token_version`；旧令牌立即失效，响应返回新 JWT，前端替换后保持当前页面登录。
- 邮箱保持原样大小写，唯一性与邮箱密码登录都区分大小写；只改字母大小写也算换绑。
- 新邮箱验证码错误时，尚未过期的换绑凭证会保留；凭证过期则需从 11.4 重新开始，原邮箱保持不变。

**成功响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "绑定或换绑成功后签发的新登录 JWT",
    "expiresIn": 259200,
    "id": 2,
    "username": "Editor",
    "role": "EDITOR"
  }
}
```

**错误情况**

| 场景 | message |
|------|---------|
| 未传邮箱 | 邮箱不能为空 |
| 邮箱格式非法 | 邮箱格式不正确 |
| 未传或非法新邮箱验证码 | 新邮箱验证码不能为空 / 验证码必须为 6 位数字 |
| 目标邮箱就是当前已绑定并验证的邮箱 | 此邮箱已绑定并验证 |
| 邮箱已被其他账号占用 | 此邮箱已被使用 |
| 换绑未带凭证 | 请先验证原邮箱后再换绑 |
| 凭证无效或过期 | 邮箱换绑凭证无效或已过期，请重新验证原邮箱 |
| 新邮箱验证码错误或过期 | 验证码错误或已过期，请重新获取 |
| 并发修改或唯一性冲突 | 账号信息已发生变化，请刷新后重试 / 登录用户名或邮箱已被使用 |
| 认证后账号状态变化 | 登录状态已失效，请重新登录 |
| 功能未启用 | 邮箱验证码功能尚未启用 |

### 11.7 解绑邮箱

```http
POST /admin/account/email/unbind
```

**请求体 `UserAccountUnbindEmailDTO`**

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| verificationCode | String | 是 | `@NotBlank` `@Pattern([0-9]{6})` | 原邮箱收到的 `UNBIND` 验证码 |

**请求示例**

```json
{ "verificationCode": "654321" }
```

**业务规则**

- 只验证原邮箱验证码，不需要密码。
- 成功后把 `email` 与 `email_verified_at` 显式置为 `null`，保留用户名与密码，之后仍可用用户名密码登录。
- 递增 `token_version`，旧令牌立即失效，响应返回新 JWT，前端替换后保持当前页面登录。
- 没有绑定邮箱时调用会报"当前账号尚未绑定邮箱"。

**成功响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "解绑成功后签发的新登录 JWT",
    "expiresIn": 259200,
    "id": 2,
    "username": "Editor",
    "role": "EDITOR"
  }
}
```

**错误情况**

| 场景 | message |
|------|---------|
| 未传验证码 | 邮箱验证码不能为空 |
| 验证码不是 6 位数字 | 验证码必须为 6 位数字 |
| 账号尚未绑定邮箱 | 当前账号尚未绑定邮箱 |
| 验证码错误或已过期 | 验证码错误或已过期，请重新获取 |
| 并发修改冲突 | 账号信息已发生变化，请刷新后重试 |
| 功能未启用 | 邮箱验证码功能尚未启用 |

### 11.8 验证码与换绑凭证规则

| 规则 | 取值 |
|------|------|
| 验证码格式 | 6 位数字字符串，保留开头的零 |
| 验证码有效期 | 5 分钟 |
| 发送冷却 | 同一账号同一用途 60 秒一次 |
| 小时上限 | 同一账号所有用途合计每小时 10 次 |
| 错误次数 | 每条验证码最多 5 次错误尝试，达到上限即失效 |
| 绑定维度 | 账号 ID + `token_version` + 用途 + 大小写保持原样的邮箱，不能跨用途、跨邮箱、跨版本混用 |
| 存储形式 | Redis 中只存 HMAC-SHA256 摘要（密钥复用 `jwt.secret`），不存明文验证码 |
| 换绑凭证 | 32 字节随机数 Base64URL（43 字符），有效期 5 分钟；发信时只校验不消费，最终换绑时原子校验并删除 |
| 版本联动 | 修改密码、绑定/换绑、解绑、管理员改资料、退出登录都会递增 `token_version`，使此前版本的验证码与凭证无法再用于自助编辑 |
| 事务边界 | 验证码与凭证的消费不能随 PostgreSQL 事务回滚；凭证过期或唯一性检查失败时需要重新获取 |
| 成功后 | 本人实际修改接口返回 `LoginVO`，前端替换新 JWT 和到期时间，当前页面无需重新登录；其他设备旧 JWT 下次请求收到 401；管理员账号管理接口不返回 JWT |

### 11.9 修改后的登录续接

- 仅 `/admin/account/change-password`、`/admin/account/email`、`/admin/account/email/unbind` 返回 `LoginVO`；请求路径、字段不变，`expiresIn` 由 `jwt.expiration` 决定，以上示例使用 259200 秒。
- 使用现有 `saveLogin(data)` 更新 JWT、到期时间及账号摘要，再查询 `/admin/account` 回显新资料；不要再清理登录状态或跳转登录页。
- 令牌签发在数据库修改事务内完成，签发失败回滚修改，提交失败不返回成功响应；验证码和换绑凭证消费仍不能随数据库事务回滚。
- `/admin/account/email/code` 和 `/admin/account/email/verify-current` 不签发登录 JWT，原邮箱验证返回的 `emailChangeToken` 不是登录令牌。
- `/admin/users/**` 仍不返回目标账号的新 JWT；被修改账号需要重新登录，管理员操作者不受影响，除非改的是自己。ADMIN 在个人设置入口修改自己同样可以续接。
- 前端已有请求令牌比对，旧请求的 401 不会清除已替换的新 JWT；修改期间应暂停其他受保护请求，避免旧请求在新 JWT 到达之前触发登录过期处理。
- 如果修改成功但响应丢失、未完成新 JWT 替换，需要重新登录，不能继续用旧 JWT 换取新令牌；此功能不是通用 refresh token 接口。

---

## 十二、服务连接检测 `/admin/system`

`GET /admin/system/health`，携带有效 JWT，仅 ADMIN 可访问，EDITOR、USER 返回 HTTP 403。
复用现有 DataSource、StringRedisTemplate 和 JavaMailSenderImpl，分别执行 JDBC 连接验证、Redis PING、SMTP testConnection()，不修改业务数据、不发送测试邮件。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "checkedAt": "2026-10-23T04:00:00Z",
    "database": { "status": "UP", "message": "连接正常" },
    "redis": { "status": "DOWN", "message": "连接检测失败" },
    "smtp": { "status": "DISABLED", "message": "邮箱验证码功能未启用" }
  }
}
```

- status 包含 UP、DOWN、DISABLED、UNCONFIGURED、NOT_CHECKED；DOWN 仅表示本次连接检测未通过，不等于确认服务进程已停止。
- 依赖失败时仍以 code 200 返回独立检测结果；邮箱功能关闭显示 DISABLED，邮件配置不完整显示 UNCONFIGURED，不支持连接检测或工作线程繁忙显示 NOT_CHECKED。
- 结果在每个应用实例内存中缓存 30 秒，手动刷新不绕过缓存，checkedAt 保持实际检测完成时间。
- 三项检测并行，共享约十秒等待期限；后台最多三个检测线程且不排队，沿用既有底层网络超时，超时后尝试取消，不无限积压探测任务。
- 仅返回安全说明，不返回连接地址、凭据或异常原文；SMTP 连接正常不代表邮件最终投递成功。
- JWT 认证仍依赖数据库，数据库故障可能导致检测接口在认证阶段失败；前端此时显示“无法获取检测结果”，不推断三个依赖全为 DOWN，不绕过账号认证。
- 设置页由 ADMIN 首次进入时获取，可手动刷新；EDITOR 不请求该接口，前端请求十五秒超时，为后端十秒检测及结果传输留出余量，离开页面时取消请求。

完整状态定义、缓存、超时与认证边界见 `ACGBox/docs/服务连接检测接口.md`，无需新增依赖、表或配置项。

## 十三、人物管理 `/persons`

沿用内容管理权限：ADMIN、EDITOR 可访问，USER 返回 HTTP 403，未登录按现有认证规则返回 HTTP 401。无数据库迁移，不开放可指定任意目标类型的通用人物关联写接口。

### 13.1 创建人物

```http
POST /persons/create
```

```json
{
  "name": "示例人物",
  "description": "用于区分同名人员的简介"
}
```

- `name` 必填，去除首尾空格后最多 100 字符，允许同名，不按姓名合并或自动选择人员。
- `description` 可空，空串或全空白保存为 NULL；非空简介保留原文。
- ID 由数据库生成，不使用客户端指定的 ID；创建和更新时间由后端维护，不能通过请求修改。

创建、修改、详情返回相同的 `PersonVO`，分页每项也包含这些字段：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 12,
    "name": "示例人物",
    "description": "用于区分同名人员的简介",
    "createdAt": "2026-10-25T10:00:00",
    "updatedAt": "2026-10-25T10:00:00"
  }
}
```

### 13.2 修改人物

```http
POST /persons/update
```

```json
{
  "id": 12,
  "name": "新署名",
  "description": null
}
```

必须传入有效且现存的 ID，不存在时返回业务错误，不自动创建。姓名规则同创建；简介 null、空串、全空白或省略时显式清空为 NULL，更新时间由后端更新，创建时间不变。人物改名后，后续动画详情及列表读取新姓名。

### 13.3 单条或批量删除

```http
POST /persons/delete
```

```json
[12, 35]
```

- 单条删除传 `[12]`，不新增另一条删除路径；空数组、无效 ID、不存在的目标均返回业务错误，重复 ID 按一个目标处理。
- 在事务内先检查全部目标，再检查所有 `person_relation` 引用，包括 BOOK。任一目标被引用，整批拒绝，不删除未引用的其他目标。
- 动画或其他作品引用预检查以及并发外键限制失败时，返回 `所选人物已被作品引用，不能删除，请先解除关联`。
- 不自动解除人物关联、不删除作品；未引用人物删除成功时返回 `data: null`。并发目标变化导致实际删除数量不符时抛出业务错误并回滚。

### 13.4 查询详情

```http
GET /persons/12
```

成功返回上述 `PersonVO`，无效或不存在的人物 ID 返回业务错误。

### 13.5 分页搜索

```http
GET /persons/page?pageNum=1&pageSize=20&name=示例
```

| 参数 | 类型 | 默认值 | 规则 |
| --- | --- | --- | --- |
| pageNum | Long | 1 | ≥ 1 |
| pageSize | Long | 20 | 1～100 |
| name | String | 不筛选 | 去除首尾空格后参数化模糊搜索，忽略字母大小写 |
| sortBy | String | 不指定 | `NAME_INITIAL` 启用名称首字母排序，规则见 7.5 |
| sortDirection | String | `ASC` | `NAME_INITIAL` 下支持 `ASC`（A–Z）和 `DESC`（Z–A） |

响应使用 `PageVO<PersonVO>`：`pageNum`、`pageSize`、`total`、`pages`、`rows`；空结果 `rows: []`。默认按 `updated_at DESC, id DESC` 返回，同更新时间也有稳定顺序。

### 13.6 动画人员关联示例

创建或修改动画时在原请求体中增加：

```json
{
  "persons": [
    { "personId": 12, "role": "导演", "sortOrder": 0 },
    { "personId": 12, "role": "脚本", "sortOrder": 1 },
    { "personId": 35, "role": "音乐", "sortOrder": 2 }
  ]
}
```

该片段需与原动画必填字段一起提交，不是独立关联接口。未选择有效人员、职责空白或超过 50 字符、同人同职责重复、署名排序负数或越界均拒绝保存；浮点数排序在字段绑定阶段拒绝，不会截断，数字字符串沿用框架默认转换。角色名称不转换成枚举，`导演` 与 `DIRECTOR` 不视为同一职责。人员、职责和排序校验在动画写入前完成，关联保存及删除沿用原事务。

## 十四、书籍类型 `/book-type`

权限沿用内容管理：ADMIN、EDITOR 可操作，USER 不可访问，携带现有 JWT；不新增类型初始化数据、不写死类型 ID。

| 方法 | 路径 | 请求 | 成功 data |
| --- | --- | --- | --- |
| POST | `/book-type/create` | `{ name, sortOrder? }` | `{ id, name, sortOrder }` |
| POST | `/book-type/update` | `{ id, name, sortOrder? }` | 同上 |
| POST | `/book-type/delete` | `[id1, id2]`，单条传一个元素 | null |
| GET | `/book-type/{id}` | 现存 ID | 类型对象 |
| GET | `/book-type/list` | 无 | 按 sortOrder ASC、id ASC 的数组 |

- 名称 trim 后必填、最多 50 字符，按数据库原有精确名称规则唯一，不忽略写入时的大小写。
- `sortOrder` 为 0～2147483647 的整数，拒绝浮点数和负数，数字字符串沿用框架默认转换。创建省略/null 默认 0；修改省略/null 保留原值，明确传 0 才归零。
- 创建、更新由后端维护时间；未知 ID 的修改、详情、删除返回业务错误，不自动创建。
- 删除检查全部目标，空数组、无效 ID、缺失目标拒绝。任一类型被书籍引用则整批拒绝，提示 `所选书籍类型已被作品引用，不能删除`，数据库外键继续兜底。

```http
POST /book-type/create
Content-Type: application/json
```

```json
{ "name": "轻小说", "sortOrder": 0 }
```

## 十五、书籍 `/book`

小说、轻小说、漫画及其他书籍共用此接口，所有通用关联固定 `BOOK`，系列条目固定小写 `book`。主类型改变不迁移关联、不改旧模块类型；不增加版本、卷册明细或阅读正文。封面先通过第十六节的独立上传接口获取地址，再提交 coverImageUrl。

### 15.1 端点与权限

| 方法 | 路径 | 请求 | 成功 data |
| --- | --- | --- | --- |
| POST | `/book/create` | BookDTO | BookVO |
| POST | `/book/update` | 完整 BookDTO，id 必填 | BookVO |
| POST | `/book/delete` | `[id1, id2]` | null |
| GET | `/book/{id}` | 现存书籍 ID | BookVO |
| GET | `/book/page` | BookPageDTO | PageVO<BookPageVO> |

沿用 Result、PageVO 和全局错误响应，ADMIN、EDITOR 可使用，USER 返回 HTTP 403；未认证返回 HTTP 401。所有书籍创建、更新和删除在原有 Spring 事务机制中完成。

### 15.2 基础请求字段

书籍仅保留 BookDTO、BookPageDTO、BookTypeDTO 三个专用请求对象；公司、人物、外链分别复用 CompanyRelationDTO、PersonRelationDTO、ExternalLinkDTO。书籍及书籍类型删除直接接收 List<Long>，JSON 仍为 ID 数组，无额外包装字段；嵌套校验和既有业务规则不变。

| 字段 | 类型 | 要求 |
| --- | --- | --- |
| id | Long | 修改必填，创建时不采用客户端 ID，由数据库生成 |
| name | String | trim 后必填、最多 200 字符，允许不同作品同名，按 ID 定位 |
| bookTypeId / adaptationTypeId / regionId | Long | 必填、正 int4，保存时必须引用现存资料 |
| publishDate | LocalDate | 与动画一样使用框架默认绑定，推荐 yyyy-MM-dd；null 或空串表示未填，仅年份、年月及无效日期不自动补齐 |
| coverImageUrl | String | 可空，最多 500 字符；支持手填外部地址或第十六节上传接口返回的 url |
| status | Long | 必填整数：0 其他、1 未发表、2 连载中、3 已完结、4 暂停中、5 已终止 |
| volumeCount / chapterCount | Long | null 或 0～2147483647 的整数，0 与未知严格区分 |
| description | String | 可空，纯空白归一为 NULL |
| personalRatingScore | BigDecimal | null 或 0～10 的数值整数；8.0 与 8 等价，8.5 拒绝，0 有效，不四舍五入或截断 |
| aliasNames | String[] | trim、去空去重，保留首项顺序，按有效下标保存排序 |
| companies | 关联数组 | companyId、role、sortOrder；公司 ID 去重保留首条，职责可空、trim 后最多 50 字符 |
| persons | 关联数组 | personId、role、sortOrder；同人不同职责允许，同人同职责 trim 后重复拒绝，职责必填最多 50 字符 |
| externalLinks | 关联数组 | title、url、sortOrder；URL 必填最多 500 字符，只接受有效 http/https，标题最多 100 字符，未填标题保存空串以遵守已有 NOT NULL 约束 |
| tagIds | Long[] | 按 ID 去重并校验存在，不新建书籍专用标签 |
| seriesId | Long | 选定系列优先；修改未传或 null 解除系列 |
| autoCreateSeries | Boolean | 仅创建且未选择 seriesId 时生效，默认关闭 |

所有新增书籍数值输入拒绝小数和字符串隐式转换，删除仍接收 JSON 数组，但元素也只接受整数数字。人物排序只能 0～2147483647，公司、外链和系列排序允许有符号 int4，不能将人物非负规则套给旧模块。公司创建缺省排序为清洗后位置，更新缺省保留原公司排序、新公司按有效位置赋值；人物和外链缺省排序使用有效数组位置。

### 15.3 完整请求示例

下列 ID 仅为格式示例，使用时须选择实际存在的资料；更新时追加现存 `id`。

```json
{
  "name": "示例作品",
  "bookTypeId": 1,
  "adaptationTypeId": 2,
  "regionId": 3,
  "publishDate": null,
  "coverImageUrl": null,
  "status": 0,
  "volumeCount": 0,
  "chapterCount": null,
  "description": "作品简介",
  "aliasNames": ["其他语言名称"],
  "companies": [{ "companyId": 5, "role": "原版出版", "sortOrder": 0 }],
  "persons": [
    { "personId": 12, "role": "作者", "sortOrder": 0 },
    { "personId": 12, "role": "插画", "sortOrder": 1 }
  ],
  "externalLinks": [{ "title": "官方网站", "url": "https://example.com", "sortOrder": 0 }],
  "tagIds": [4, 7],
  "seriesId": null,
  "autoCreateSeries": false,
  "personalRatingScore": 0
}
```

### 15.4 更新、系列及响应语义

- 完整表单更新：必填基础字段缺失拒绝；可空基础字段省略/null 均写入 NULL，不能通过省略保留旧基础字段。
- aliasNames、companies、externalLinks、tagIds 省略/null 为清空；persons 省略/null 保留，明确 `[]` 才清空；personalRatingScore 省略/null 删除原评分记录。
- 选择 seriesId 时优先按 ID；仅创建允许按书名精确自动匹配系列，无则创建，一条则复用，多条同名时提示 `存在多个同名系列，请按 ID 手动选择系列`。编辑不自动创建或重命名系列。
- 写入响应及详情统一为 BookVO：全部基础字段、createdAt、updatedAt，以及 aliasNames、companies、persons、externalLinks、tagIds、seriesId、personalRatingScore。没有关联数组返回 `[]`，可空标量返回 null。
- 详情有 `seriesId` 时，可请求 `GET /series/{id}/works` 获取系列信息及关联作品（见 7.6）。
- 动画与书籍不再保存或返回系列内排序；旧客户端提交的 `seriesSortOrder` 兼容接收但不生效。作品仍可通过 `seriesId` 关联已有系列。
- 公司返回 companyId、role、sortOrder；人物返回 personId、personName、role、sortOrder。人物名称读取当前档案，不冗余保存姓名。
- 公司、人物、外链按 sortOrder ASC、关联 ID ASC 返回；别名按已保存顺序。写入返回直接使用详情聚合，确保刷新前后顺序一致。
- BookPageVO 不返回长篇 description；返回其他基础展示字段及只读时间，bookType、adaptationType、region 展开为资料对象，有排序的字典额外返回 sortOrder；tags 为 id/name 数组，按名称首字母正序（A–Z，`#` 在末尾）展示，同组按标签更新时间降序、ID 降序；companies 额外返回 companyName；persons 保留完整名单；series 为 id/name/description 或 null；个人评分字段名为 personalRating。

### 15.5 分页筛选与反向关联

| 参数 | 规则 |
| --- | --- |
| pageNum / pageSize | 默认 1、20；页码 ≥1，页大小 1～100 |
| keyword | 书名、BOOK 别名、book 系列名内部 OR，忽略字母大小写，空白不筛选 |
| bookTypeId / adaptationTypeId / regionId | 精确过滤，合法不存在返回空分页 |
| status | 0～5，0 有效，未传才不筛选 |
| tagIds / tagMatchMode | 标签去重，ALL 全部、ANY 任一，默认 ALL |
| publishStartDate / publishEndDate | 真实 yyyy、yyyy-MM 或 yyyy-MM-dd；开始取首日、结束取末日，含边界，开始晚于结束拒绝 |
| companyId / personId / seriesId | 按关联 ID 精确筛选，人物覆盖全部职责，不追加名称关键词 |
| ratingMin / ratingMax | BigDecimal，可选 0～10 数值整数，允许 8.0；最小值不得大于最大值，范围不命中未评分书籍 |
| sortBy / sortDirection | PUBLISH_DATE 或 PERSONAL_RATING；ASC 或 DESC；默认日期倒序 |

多个筛选条件按 AND，ID 仅允许十进制正 int4，非法格式或范围返回现有参数错误响应；合法不存在返回 total=0、pages=0、rows=[]。使用 EXISTS 避免一人多职重复作品，分页和计数在数据库完成。选择发表日期时，个人评分作为次排序；选择个人评分时，发表日期作为次排序。两项均使用所选方向，均为 NULLS LAST，两项都相同时按书籍 ID DESC 稳定排序，不按署名或系列排序值排列作品。日期未知作品在未限制日期时仍可查询。

```http
GET /book/page?personId=12&pageNum=1&pageSize=10&sortBy=PUBLISH_DATE&sortDirection=DESC
GET /book/page?companyId=5&pageNum=1&pageSize=10&sortBy=PERSONAL_RATING&sortDirection=DESC
GET /book/page?seriesId=8&status=0&tagIds=4,7&tagMatchMode=ALL&publishStartDate=2020&publishEndDate=2026-10
```

关联筛选只筛作品，仍返回作品的全部人物、公司、职责。当前页按书籍 ID 批量查询并批量获取资料，避免逐条补查；不使用实体姓名或前端全量筛选替代关联查询。

### 15.6 删除、事务及兼容边界

删除传单条 `[id]` 或批量 `[id1,id2]`，重复 ID 去重；空数组、无效 ID、缺失目标均拒绝，先检查整批再操作。清理 BOOK 的别名、公司、人物、标签、外链、个人评分，以及小写 book 系列项后删除书籍本体，不删除资料档案，不影响同 ID 动画或其他类型。中途失败、实际删除数量不符均抛错回滚，自动建系列及评分写入同属事务。

人物已有全类型引用保护继续生效；书籍类型、改编类型、地区增加书籍引用预检查及外键兜底；公司、标签、系列的既有删除规则不变。本轮不修改动画评分或日期绑定、不执行 SQL 导出、不迁移旧 MANGA/NOVEL 数据，也不开放任意指定目标类型的通用关联写接口。

## 十六、封面图片上传 `/uploads`

### 16.1 上传接口

`POST /uploads/images`，请求头携带 `Authorization: Bearer <token>`；`ADMIN`、`EDITOR` 可用，未登录、无效或失效令牌返回原有 HTTP 401，`USER` 返回 HTTP 403。此接口不允许匿名调用，不要求作品先创建。

请求类型为 `multipart/form-data`，只支持一张图片：

| 表单字段 | 类型 | 必填 | 说明 |
|----------|------|------|------|
| file | 文件 | 是 | 只能有一个 file，不能同时上传其他文件字段 |
| targetType | String | 是 | 严格使用 `ANIME` 或 `BOOK`，不接受空白、小写或多个值 |

`ANIME` 固定使用 `anime` 目录，`BOOK` 固定使用 `book` 目录；漫画和小说同属书籍，不增加独立目录。客户端不能选择桶、地域、保存目录、对象键、文件名或访问域名，也无需提交 targetId。

```bash
curl -X POST "http://localhost:8080/uploads/images" \
  -H "Authorization: Bearer <token>" \
  -F "targetType=ANIME" \
  -F "file=@cover.jpg"
```

前端通过现有代理访问 `/api/uploads/images`；后端路径本身不带 `/api`。使用 `FormData` 时不要手工设置 Content-Type，由浏览器附加 multipart boundary：

```javascript
const form = new FormData();
form.append('targetType', 'BOOK');
form.append('file', file);
const response = await fetch('/api/uploads/images', {
  method: 'POST',
  headers: { Authorization: `Bearer ${token}` },
  body: form
});
const result = await response.json();
```

上例省略项目已有的 401/403 和统一错误处理；认证失败可能没有 JSON 响应体，实际接入应先检查 HTTP 状态。

### 16.2 校验与对象规则

- 仅允许 JPEG、PNG，文件名后缀只允许 jpg、jpeg、png（忽略大小写）；后缀必须和实际文件格式一致。客户端 Content-Type 不能决定图片类型。
- 单文件最大 **5 MiB（5242880 字节）**，整个 multipart 请求最大 **6 MiB（6291456 字节）**。代理或网关需配置相容的请求体限制；若请求在代理层被拒绝，响应格式由代理决定。
- 宽、高必须大于 0，单边不超过 10000，总像素不超过 20000000；先检查尺寸再完整解码，拒绝伪装、损坏、截断图片，PNG 额外检查块边界、CRC 和 IEND。
- 不裁剪、缩放或转码，上传原始字节，PNG 透明度保留。COS 元数据使用实际长度和 `image/jpeg` / `image/png`，不设置附件下载或逐对象公有读 ACL。
- 对象键固定为 `anime/yyyy/MM/UUID.jpg` 或 `book/yyyy/MM/UUID.png`；JPEG 的扩展名统一为 jpg，UUID 无连字符，每次重新生成，原始文件名不参与路径。
- 年月取本次业务接收上传时的 **Asia/Shanghai** 日期，一次取值；公开地址只由配置基址拼接，不读取请求 Host、Origin 或 Referer，长度不超过 500 字符。
- 公开地址无签名、无过期参数，可长期缓存（`public, max-age=31536000, immutable`）。原图由 COS 或用户配置的 CDN 提供，无后端 `/media` 读取接口。

### 16.3 成功响应与作品保存

只有 COS PUT 返回确认结果后才返回成功。示例域名及对象键均为占位：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "url": "https://img.example.com/anime/2026/10/6ea20491916e46ec8e091c30204cd316.jpg",
    "objectKey": "anime/2026/10/6ea20491916e46ec8e091c30204cd316.jpg",
    "targetType": "ANIME",
    "contentType": "image/jpeg",
    "size": 245678,
    "width": 1200,
    "height": 1800
  }
}
```

size、width、height 均为 `Long`，分别表示实际上传字节数和解码得到的像素尺寸。objectKey 只是桶内对象标识，业务表第一版无需保存它。

**上传成功不等于作品已保存**：前端将 url 回填现有 coverImageUrl，再调用动画或书籍创建、修改接口。本接口不写任何业务表，不创建作品，不改变手动填写外部 HTTP/HTTPS 图片地址的能力。

取消草稿、替换或清空封面、删除作品均不自动删除 COS 文件；第一版无文件资产表、删除接口、去重和闲置文件清理。数据库回滚不会回滚 COS，写入超时也可能产生未引用对象，不对不确定状态的对象盲目删除。

### 16.4 错误响应

| 情况 | HTTP | Result.code | 提示或说明 |
|------|------|-------------|------------|
| 功能关闭 | 200 | 500 | 图片上传功能未启用 |
| 缺失或多个文件 | 200 | 500 | 请通过 file 上传且仅上传一张图片 |
| 空文件 | 200 | 500 | 请选择非空图片文件 |
| 无效 targetType | 200 | 500 | targetType 必须为 ANIME 或 BOOK |
| 不允许的后缀 | 200 | 500 | 仅支持 JPG、JPEG、PNG 图片 |
| 实际类型与后缀不一致 | 200 | 500 | 图片内容无效或与文件扩展名不一致 |
| 损坏或尺寸超限 | 200 | 500 | 返回对应校验提示，COS PUT 不执行 |
| 文件或 multipart 请求超限 | **413** | 500 | 图片不能超过 5 MiB，上传请求不能超过 6 MiB |
| multipart 解析失败 | 200 | 500 | 上传表单解析失败，请使用 multipart/form-data 上传图片 |
| COS 超时、拒绝或服务异常 | 200 | 500 | 图片上传失败，请稍后重试 |

上传失败不向客户端返回 SDK 原始异常、签名、凭证或内部端点。连接池等待、连接超时均为 5 秒，读取超时 30 秒，SDK 不自动重试；失败后是否重新上传由调用方决定。

### 16.5 部署配置

公共 `application.yml` 使用 `storage.cos` 配置项，复用现有 JAR 外私有文件加载方式。只在仓库外 `application-secrets.yml` 中填写真实值并重启，不修改已提交示例来存放真实凭证：

```yaml
ACGBOX_COS_ENABLED: true
ACGBOX_COS_REGION: "ap-guangzhou"
ACGBOX_COS_BUCKET: "covers-1250000000"
ACGBOX_COS_SECRET_ID: "<受限账号 SecretId>"
ACGBOX_COS_SECRET_KEY: "<受限账号 SecretKey>"
ACGBOX_COS_PUBLIC_BASE_URL: "https://img.example.com"
```

以上均为占位示例。默认 enabled=false，关闭时不要求其他字段齐全，也不创建 COS 客户端；启用时检查必填项和格式，配置错误会阻止启动，异常不打印配置值。public-base-url 只接受 HTTPS 域名基址，不带端口、业务路径、参数或片段，末尾斜杠统一移除。

使用腾讯云官方 Java SDK `com.qcloud:cos_api:5.6.283`，客户端共享并随应用关闭释放资源；SDK 标准上传端点由地域和桶确定，与图片公开访问域名分开，不将自定义图片域名当上传端点。

用户需自行准备桶、域名、DNS、HTTPS 证书及公开读取策略。建议专用公开封面桶、公有读私有写，或由 CDN 提供固定公开读取；受限账号只授权指定桶 `anime/*`、`book/*` 上传，不授予列举、删除或改桶策略权限，不使用主账号全权限密钥。生产环境不要开启 HTTP 请求及 SDK 的敏感调试日志。

初次后端交付覆盖隔离验证。动画和书籍现已接入封面上传组件，用户于 2026-11-05 确认封面验收基本完成并进入阶段收尾。各环境的实际上传与公开域名读取结果以对应验收记录为准。

## 十七、作品数据导入导出 `/data-transfer`

### 17.1 权限与接口

仅 ADMIN、EDITOR 可用，沿用 JWT、账号启用状态和 token_version 校验；匿名返回 HTTP 401，USER 返回 HTTP 403。动画、书籍各有固定分组，所有操作都要求 targetType 严格匹配 ANIME／BOOK，不接受缺省、小写或跨组 token。

| 方法 | 动画路径 | 书籍路径 | 请求 |
| --- | --- | --- | --- |
| POST | `/data-transfer/anime/export` | `/data-transfer/book/export` | JSON：targetType、scope、ids 或 filters |
| GET | `/data-transfer/anime/template` | `/data-transfer/book/template` | 查询参数 targetType |
| POST | `/data-transfer/anime/import/preview` | `/data-transfer/book/import/preview` | multipart：唯一 file、唯一 targetType |
| POST | `/data-transfer/anime/import/commit` | `/data-transfer/book/import/commit` | JSON：importToken、targetType、acknowledged=true；有待创建资料时另传 createMissingCatalogs=true |
| GET | `/data-transfer/anime/import/{importToken}` | `/data-transfer/book/import/{importToken}` | 查询参数 targetType，仅本人批次 |
| POST | `/data-transfer/anime/import/{importToken}/cancel` | `/data-transfer/book/import/{importToken}/cancel` | JSON：targetType，仅本人未执行批次 |

不开放共用 `/data-transfer/export` 等动态类型入口，不接收表名、SQL、数据库连接或远程文件地址。前端访问时沿用已有 `/api` 代理，不在后端路径添加该前缀。

### 17.2 导出与模板

勾选导出示例：

```json
{"targetType":"ANIME","scope":"SELECTED","ids":[15,16]}
```

筛选导出示例，filters 使用对应列表已应用的筛选字段；页码和页大小被忽略，无筛选时明确传空对象：

```json
{"targetType":"BOOK","scope":"FILTERED","filters":{"keyword":"示例","ratingMin":0,"ratingMax":10}}
```

SELECTED 要求非空 ID 集合，去重后按 ID 升序导出全部勾选作品；任一 ID 不存在时拒绝导出。FILTERED 沿用现有列表筛选与排序，忽略列表分页参数，导出全部命中作品；`filters: {}` 导出该类型的全部作品。ids 与 filters 不能混用。

成功返回 XLSX 二进制，Content-Type 为 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，附受控 Content-Disposition、Content-Length，文件名为 `ACGBox_ANIME_yyyyMMdd_HHmmss.xlsx` 或 BOOK，时间为 Asia/Shanghai。参数、读取及生成错误仍返回 JSON Result，前端须检查 HTTP 状态及 Content-Type，不能将错误体保存成 Excel。

在同一数据源的只读 REPEATABLE_READ 事务内分批查询并写入工作表，输出简介、封面 URL、关联及资料名称，不输出数据库 ID 或创建／更新时间。事务结束后封装为完整 XLSX，生成成功才开始发送响应；生成失败或超过 XLSX 格式限制时拒绝导出，不截断。非整数历史评分拒绝导出并提示作品 ID，不自动取整。

### 17.3 文件协议 v2（当前导出与模板）

新下载文件使用 `meta.schemaVersion=2`，保留原七张工作表和原接口。所有行固定 16 磅，所有关联编号列统一叫“编号”，宽 6 字符。编号只用于将关系表中的行挂到同文件的作品，不作为数据库 ID。

- 动画 works 原业务列顺序及列宽：编号 6、作品名称 70、系列名称 20、简介 30、总集数 10、放送类型 10、改编类型 10、开播日期 15、状态名称 10、地区 10、个人评分 10。
- 书籍用卷数和章节数替代总集数（各 10），用书籍类型替代放送类型（10），用发表日期替代开播日期（15），其余顺序相同。
- companies 为编号、公司名称、职责、排序值；persons 为编号、人物名称、职责、排序值；tags 为编号、标签名称；aliases、external_links 保留原数据列，只将关联列标题改为编号。
- 动画和书籍的 works 在简介后插入“标签”“公司与职责”“人物与职责”，在个人评分后插入“别名”“外部链接”。标签列宽 25，全部标签用 `/` 分隔；其他摘要列宽 20，显示前三项及剩余项数，外链按“标题（URL）”显示，无标题时只显示 URL。摘要按编号汇总，可省略，导入忽略其值，以关联明细表为准；旧文件不需要补列。
- 新文件不包含数据库 ID、创建／更新时间、系列内排序，不使用隐藏 ID 列。个人评分与别名之间为可选的“封面 URL”（列宽 20），沿用原 coverImageUrl 字段；旧文件没有这列也可导入。简介仍保留全文，固定行高下可在编辑栏查看。
- 预览按名称原值精确匹配已有类型、地区、系列、公司、人物和标签，区分大小写。系列、公司、人物、标签缺失时列入待创建清单；类型和地区缺失、任意资料重名仍为错误。系列名称可空。状态使用现有动画／书籍状态文字，不保存系列内排序。

#### 旧版文件协议 v1（仅兼容导入）

旧版同样使用七张表：meta、works、aliases、companies、persons、tags、external_links，文件版本为 1。以下为旧文件结构和兼容读取规则，不适用于新版导出排版。新旧文件均不执行公式。

导入兼容中文标题和旧版英文字段表头，也允许中英文混用及调整列顺序；先映射为下列字段，再校验未知、缺失或重复列。例如同时存在 `作品名称` 和 `name` 两列会被拒绝，不能借中英文重复列覆盖数据。Issue.field 和预览 JSON 继续返回原英文字段名，前端不需要改接口字段。

导出文件和模板的每张工作表均设置默认列宽 22、数据行默认高度 24 磅、表头高度 28 磅；已生成的数据行另逐行保存固定 24 磅高度，避免仅有工作表默认高度时被 Excel／WPS 按换行内容撑高。ID／排序／评分列宽 16，名称／标题列宽 32，简介／链接列宽 60。保留自动换行，不自动扩展行高；长简介或链接可能需要手动调整行高或在编辑栏查看全文，内容不会因此截断，导入规则不受行高和列宽影响。

works 新增只读展示列：adaptationTypeName（改编类型）、regionName（地区）、statusName（状态名称），动画另有 broadcastTypeName（放送类型），书籍另有 bookTypeName（书籍类型）。导出名称读取同一快照中的资料表，状态按现有作品类型的代码规则转换；新增文字列位于 works 的原字段之后。原 adaptationTypeId、regionId、status、broadcastTypeId／bookTypeId 列保留原值并默认隐藏，不影响其他 ID 列。

旧版可包含只读系列名称和隐藏的 seriesId 列；导入仍以 ID 和状态代码列为准。文字列不进入作品 DTO，不按文字查找、创建或修改资料。展示列可省略，列顺序可以调整。旧版的系列内排序列可保留或省略，导入时忽略。

| 英文字段 | 中文列标题 | 英文字段 | 中文列标题 |
| --- | --- | --- | --- |
| key | 配置项 | value | 配置值 |
| rowKey | 文件内关联编号 | sourceId | 来源作品ID |
| name | 作品名称 | adaptationTypeId | 改编类型ID |
| regionId | 地区ID | coverImageUrl | 封面图片地址 |
| status | 状态 | description | 简介 |
| seriesId | 系列ID | seriesSortOrder | 系列内排序 |
| personalRatingScore | 个人评分 | createdAt | 创建时间 |
| updatedAt | 更新时间 | broadcastTypeId | 放送类型ID |
| episodeCount | 总集数 | airDate | 开播日期 |
| bookTypeId | 书籍类型ID | publishDate | 首次发表日期 |
| volumeCount | 卷数 | chapterCount | 章节数 |
| aliasName | 别名 | sortOrder | 排序值 |
| companyId | 公司ID | companyName | 公司名称 |
| role | 职责 | personId | 人物ID |
| personName | 人物名称 | tagId | 标签ID |
| tagName | 标签名称 | title | 链接标题 |
| url | 链接地址 | — | — |
| adaptationTypeName | 改编类型 | regionName | 地区 |
| statusName | 状态名称 | broadcastTypeName | 放送类型 |
| bookTypeName | 书籍类型 | seriesName | 系列名称 |

meta 包含 schemaVersion=1、targetType、fileKind=EXPORT／TEMPLATE、exportedAt。works 共同字段为 rowKey、sourceId、name、adaptationTypeId、regionId、coverImageUrl、status、description、seriesId、seriesSortOrder、personalRatingScore、createdAt、updatedAt；动画另有 broadcastTypeId、episodeCount、airDate，书籍另有 bookTypeId、publishDate、volumeCount、chapterCount。

sourceId、createdAt、updatedAt 导入时忽略，不指定主键或更新旧作品；不提供 autoCreateSeries，导入只引用已有系列。rowKey 为最多 100 字符的唯一文件内字符串，关联按它定位，不受作品行顺序变化影响。辅助名称只核对提醒，仍按资料 ID 引用，不按名称创建或合并档案。

关联表字段：

- aliases：rowKey、aliasName、sortOrder；按排序值及文件行号确定 aliasNames 顺序。
- companies：rowKey、companyId、companyName、role、sortOrder。
- persons：rowKey、personId、personName、role、sortOrder。
- tags：rowKey、tagId、tagName。
- external_links：rowKey、title、url、sortOrder。

必须保留全部工作表和列，子表可为空；拒绝缺表、未知／重复表头、重复 rowKey 和孤立关联。空白行忽略，半成品行报错。日期必须为真实 yyyy-MM-dd 文本或空白，不猜测 Excel 日期数值；数字支持数值单元格和规范十进制数字文本，按真实数值判断整数，8.0 等于 8，拒绝小数截断、百分数、千分位及科学计数文本。0 与 null 分开处理。

### 17.4 预览与确认

查询本人批次 `GET /data-transfer/{anime|book}/import/{importToken}?targetType=ANIME|BOOK` 时，若状态为 READY 且原缓存可用，`data.preview` 返回与上传预览相同结构的作品行、统计、提醒、待创建资料、名称映射、token 和截止时间。名称映射从当前资料读取，作品数据和待创建项沿用已绑定的批次快照，不重新解析 XLSX，不重置有效期。保留关闭只关闭界面并保留 token，不调用取消接口；取消预览仍调用原 `POST .../cancel`，确认终态后清理本地批次。没有生成 token 的错误预览无法从后端恢复。处理中、终态或 Redis 暂不可用时不返回预览明细。

新版名称文件的预览返回 `pendingCatalogs`，每项包含 `temporaryId`（负数预览标识）、`type`（tag/company/person/series）、`name` 和引用它的 `rowKeys`；同类型同名只列一次。预览不创建资料，旧 ID 文件不启用这项行为。存在待创建项时，用户须额外勾选确认并随原批次提交 `createMissingCatalogs: true`，否则不写入且保留批次。待创建关联的最终校验在解析出实际 ID 后执行。提交重新匹配：唯一同名复用、重名拒绝、仍缺失时按名称创建；资料、作品和关联一起提交或回滚。只在导入提交中锁定涉及的资料表，不新增名称唯一约束。

有效预览不写作品、关联、系列或评分；复用新增校验及清洗，只暂存 Redis 和精简批次元信息。上传示例：

```bash
curl -X POST "http://localhost:8080/data-transfer/book/import/preview" \
  -H "Authorization: Bearer <token>" -F "targetType=BOOK" -F "file=@books.xlsx"
```

预览 Result.data 字段：targetType、workCount、relationCount、errorCount、warningCount、truncated、rows、referenceNames、errors、warnings；无错误时另有 importToken、expiresAt。rows 为 `{rowKey,row,data}`，data 展示规范化新增数据；无效作品保留可解析的原数据。referenceNames 按资料表名及 ID 提供本文件引用的当前名称，只读取已引用资料，不扫描或输出整库字典。Issue 为 `{sheet,row,field,rowKey,message}`，Excel 第一条数据行是第 2 行。文件结构整体错误直接返回业务提示；作品级业务错误定位 works 行，字段级错误定位具体列或关联行。

errors／warnings 明细各最多 100 条，计数保留已发现问题的完整数量，truncated 表示明细截断。有错误不生成 token；存在提醒仍需核对后确认。EXPORT 文件明确提醒“新增而非覆盖”，书籍同名仅提醒，动画遵守原值等值判重。

确认请求示例：

```json
{"targetType":"BOOK","importToken":"<预览返回的批次UUID>","acknowledged":true}
```

成功 data 包含 importToken、targetType、status=SUCCEEDED、expiresAt、workCount、createdCount、mappings，例如 `{"W001":123,"W002":124}`。映射是实际新 ID，不是 sourceId。同 token 重试返回 PostgreSQL 已保存的原结果，即使 Redis 已过期或不可用也不重复创建。

提交只接受已有预览，不接收新的作品数组。批次账号、路径、请求类型、缓存版本／账号／到期时间及 SHA-256 摘要均核对；请求错误不使原 READY 失效。空预览、过期、缓存丢失或内容不一致不能提交。

### 17.5 状态、事务及缓存

数据库持久化状态：READY、SUCCEEDED、INVALID、CANCELLED、EXPIRED。查询遇到正在持有批次锁的提交，可返回临时 PROCESSING，不能据旧 READY 断言未执行。INVALID／EXPIRED 使用 Result.code=500，但保留 data 中的真实状态、message、必要 rowKey；前端应保留这些错误数据。其他账号或跨类型访问拒绝，不暴露批次内容。

确认使用 READ_COMMITTED，先锁批次再取得动画名称写锁；整批作品和 SUCCEEDED 映射同事务提交。确定性业务失败回滚业务保存点，撤销本批作品及关联，再持久化 INVALID；内部保存入口要求已有事务，业务异常不将外层事务提前标记 rollback-only。暂时性数据库故障或提交结果不确定时，只能查询或使用同 token 重试，不能直接生成新批次重复导入。

普通动画新增、修改与批量导入共享 PostgreSQL 事务级 advisory lock，键为 `(1094928194,0)`；名称原值等值判重，不新增 trim 或忽略大小写。该协议不约束直接 SQL，也不是唯一索引。BOOK 仍允许同名。每账号预览名额协调使用保留命名空间 1094928195，不与名称写锁混用。

Redis 复用现有连接，独立键前缀 `acgbox:data-transfer:preview:`，最长 30 分钟，包含预览 JSON、提醒与绑定元信息；PostgreSQL 仅存摘要、状态、计数、结果和时间，无完整 payload。每账号最多 3 个未过期 READY，并发名额检查和创建由数据库原子化。

先保存带 TTL 的缓存，再登记 READY；登记失败清理缓存，失败残留无数据库批次就无法执行，并由 TTL 回收。终态先提交数据库，再尽力删除缓存，删除失败不改变执行结果。缓存确实丢失为 INVALID，服务暂不可用不误标记失效。数据库终态保留 7 天；每分钟小批清理到期预览及旧终态，跳过正在持锁的批次，不清理作品或 COS。

### 17.6 限制与部署

- 导入只支持标准 XLSX；单文件最大 5 MiB，multipart 请求最大 6 MiB；每次导入最多 500 部作品、10000 条关联。若导出文件超过这些限制，重新导入前需拆分文件，并保留各批作品对应的关联及编号。
- 实际累计解压最多 50 MiB、单项最多 20 MiB，保留 POI 压缩比保护；读取与 XML 预检执行 15 秒时限。预检先限制异常行／单元格／共享文本及 XML 复杂度，再创建工作簿，不允许无限构建对象。
- 导入的标准化 JSON 最多 5 MiB，Redis value 和完整预览 Result 各最多 6 MiB。导出不受上述配额限制；XLSX 单张表最多 1,048,576 行（含表头），每个文本单元格最多 32767 个字符，超限拒绝生成，不截断。大文件下载仍受服务端可用临时磁盘、网关超时及网络条件影响。
- 拒绝公式、宏、嵌入图片／对象、外部关系、DTD 和受密码保护等不支持的文件，不执行公式，不采用缓存公式值。原始 XLSX 临时文件在成功或失败后清理，不放公开目录。
- Excel 超限为 HTTP 413 + Result，提示 `Excel 文件不能超过 5 MiB，上传请求不能超过 6 MiB`；图片入口保留原图片文案。

`data_import_batch` 的当前定义见 `ACGBox/sql/public.sql`。部署时核对目标库的批次表结构和 Redis 外部配置。已具备对应结构的环境可以使用导入功能，缺表的已有数据库由用户安排增量建表。`public.sql` 是结构快照，包含删表语句。应用启动流程以目标库现有结构为准。缺表时模板生成和原作品管理仍可用，预览／批次接口及定时清理会提示检查批次表。

动画和书籍的导入导出前后端已接入，用户于 2026-11-05 确认相关事项基本完成并进入阶段收尾。早期隔离回归结果见设计方案的实施记录，真实环境各用例以对应验收记录为准。动画新增、修改及导入均要求外链 `title` 去除首尾空格后非空，书籍外链标题可空。

## 十八、数据质量检查 `/data-quality`

### 18.1 分页检查

**GET `/data-quality/page`**，仅 ADMIN、EDITOR 可访问，沿用现有 JWT 和 Result。接口实时只读，不保存扫描记录、不探测封面地址、不修改作品写入规则。

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| targetType | String | 必填，`ANIME`、`BOOK`、`PERSON`、`COMPANY` 或 `SERIES` |
| keyword | String | 可选，仅匹配正式名称，忽略英文字母大小写；首尾空白去除后为空则不限制 |
| ruleCodes | String[] | 必填，逗号分隔且至少一项；重复去重，未知代码拒绝；人物、公司、系列支持 `SAME_NAME_REVIEW` 和 `UNREFERENCED`，动画和书籍不支持 `UNREFERENCED` |
| matchMode | String | `ANY` 满足任一项（默认），`ALL` 满足全部所选项；非法值拒绝 |
| pageNum | Long | 默认 1，至少为 1 |
| pageSize | Long | 默认 20，范围 1～100 |

| 规则代码 | 命中条件 |
| --- | --- |
| MISSING_COVER | 封面 URL 为 null 或仅包含常见空白 |
| MISSING_DESCRIPTION | 简介为 null 或仅包含常见空白 |
| MISSING_PERSON | 没有同时匹配作品类型及 ID 的创作人员关联 |
| MISSING_COMPANY | 没有同时匹配作品类型及 ID 的公司关联 |
| MISSING_TAG | 没有同时匹配作品类型及 ID 的标签关联 |
| MISSING_DATE | 动画开播日期 `air_date` 或书籍发表日期 `publish_date` 为 null |
| SAME_NAME_REVIEW | 在同一内容类型内，去除名称首尾常见空白、忽略英文字母大小写后存在同名记录；书籍还须同一书籍主类型 ID |
| UNREFERENCED | 仅人物、公司、系列支持，各自的关系表中没有指向该档案 ID 的记录 |

```http
GET /data-quality/page?targetType=BOOK&ruleCodes=MISSING_COVER,MISSING_PERSON&matchMode=ALL&pageNum=1&pageSize=20
Authorization: Bearer <token>
```

响应示例：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "pageNum": 1,
    "pageSize": 20,
    "total": 1,
    "pages": 1,
    "checkedAt": "2026-11-06T10:00:00+08:00",
    "summary": {
      "scopeTotal": 3,
      "ruleCounts": {
        "MISSING_COVER": 2,
        "MISSING_DESCRIPTION": 0,
        "MISSING_PERSON": 2,
        "MISSING_COMPANY": 1,
        "MISSING_TAG": 2,
        "MISSING_DATE": 1,
        "SAME_NAME_REVIEW": 2
      }
    },
    "rows": [
      {
        "targetType": "BOOK",
        "targetId": 12,
        "name": "示例作品",
        "coverImageUrl": null,
        "contentType": { "id": 1, "name": "轻小说" },
        "region": { "id": 2, "name": "日本" },
        "updatedAt": "2026-11-05T12:00:00",
        "issues": [
          { "code": "MISSING_COVER", "message": "缺封面" },
          { "code": "MISSING_PERSON", "message": "缺创作人员" }
        ],
        "duplicateCount": null,
        "description": null
      }
    ]
  }
}
```

- 一部作品只占一行，按更新时间降序、ID 降序；issues 只列出已选择且命中的规则，顺序与规则表一致。
- 七项规则统计与 scopeTotal 只受内容类型和关键词影响，total 还受所选规则和 matchMode 影响；统计和分页来自同一条 SQL 的一致读取视图，不承诺跨请求固定快照。
- 仅当已选且命中同名规则时返回 duplicateCount，包含当前作品及完整组成员，不受关键词和分页截断。该提示不代表作品重复；不比较别名，不自动合并或删除。
- 动画的 contentType 为放送类型，书籍为书籍类型。查不到对应资料时 contentType 或 region 为 null。日期仍可为空；只有选择缺日期规则时，日期为空才参与命中筛选。数量未知、未评分不在检查范围内。新增规则均为可选补录提示，不改变作品保存校验。
- 无命中时 rows 为空数组，total、pages 均为 0，仍返回规则统计；请求页码超出范围时 rows 为空、total 和 pages 保留实际值。参数或查询失败按现有 Result 错误返回，不返回伪造的空结果；匿名为 HTTP 401，USER 为 HTTP 403。

### 18.2 人物、公司及系列检查

```http
GET /data-quality/page?targetType=PERSON&keyword=Alice&ruleCodes=SAME_NAME_REVIEW&pageNum=1&pageSize=20
GET /data-quality/page?targetType=COMPANY&ruleCodes=UNREFERENCED&pageNum=1&pageSize=20
GET /data-quality/page?targetType=SERIES&ruleCodes=SAME_NAME_REVIEW,UNREFERENCED&matchMode=ALL&pageNum=1&pageSize=20
Authorization: Bearer <token>
```

- 每个档案 ID 对应一行，按更新时间降序、ID 降序；`targetId` 是对应类型的档案 ID，`name` 保留原始名称，`description` 可为 null，作品字段 `coverImageUrl`、`contentType`、`region` 均为 null。动画和书籍的 `description` 在质检中为 null。
- `scopeTotal` 是当前类型匹配名称关键词的档案数；`ruleCounts` 分别返回 `SAME_NAME_REVIEW` 和 `UNREFERENCED` 的命中档案数，不随已选规则、匹配模式或页码变化。`total` 则是满足已选规则和匹配模式的档案数。
- 无引用分别依据 `person_relation.person_id`、`company_relation.company_id`、`series_item.series_id` 判定。任一对应关系存在就不命中，不按作品公开状态或类型过滤；不进一步核对关系指向的作品是否已删除。命中时返回 `{ "code": "UNREFERENCED", "message": "无引用" }`。检查为只读，不改动现有删除规则。
- 人物、公司、系列分别分组。`duplicateCount` 是完整同名组的档案数，包含当前档案，不受关键词和分页影响。结果仅用于人工核对，不改变原有的保存、重名、关联和导入规则。

## 作品公开状态

动画和书籍共用独立表 `work_visibility`，按 `target_type + target_id` 定位，作品主表不增加 `is_public` 列。漫画和小说均为 BOOK 类型，仍使用各自的书籍 ID。

| 场景 | isPublic 契约 |
| --- | --- |
| POST /anime/create、POST /book/create | 可选 Boolean，省略或 null 默认公开；显式 false 直接创建隐藏作品 |
| POST /anime/update、POST /book/update | 省略或 null 保留原状态；true、false 分别保存为公开、隐藏，重复保存幂等 |
| 创建、修改响应及 GET /anime/{id}、GET /book/{id} | 返回非空的有效 Boolean，缺少状态记录视为公开 |
| GET /anime/page、GET /book/page | 可选布尔筛选 isPublic；省略为全部，true 公开，false 隐藏；列表行也返回有效状态 |
| 按条件导出 | filters.isPublic 与管理列表语义相同，筛选在分页与计数前完成 |

状态写入、清理与作品保存和删除共用现有事务；操作失败一起回滚。同数字 ID 的动画和书籍互不影响，状态查询不代替真实作品的存在性校验。管理详情、关联查询和系列作品查询仍可看到隐藏作品，角色权限不变。

Excel 文件格式不增加公开状态列，新导入作品默认公开，旧预览和确认 payload 缺字段可继续使用。当前 acgbox-web 仍为骨架，本轮未开放任何用户端查询；后续公开接口须统一先过滤隐藏作品再分页，并限制隐藏详情。

部署代码前，由用户在目标 PostgreSQL 执行 `sql/20261119_create_work_visibility.sql`；本次后端开发未执行该脚本或连接业务库。表未建立时相关管理查询可能报错，不降级为伪造的公开结果。隔离测试脚本会单独建立测试表，不操作业务环境。

## 接口汇总

| 模块 | 路径 | 接口数 | 端点 |
|------|------|--------|------|
| 动画 | `/anime` | **5** | create / update / delete / getById / page |
| 书籍 | `/book` | 5 | create / update / delete / getById / page |
| 书籍类型 | `/book-type` | 5 | create / update / delete / getById / list |
| 改编类型 | `/adaptation-type` | 5 | create / update / delete / getById / list |
| 放送类型 | `/broadcast-type` | 5 | create / update / delete / getById / list |
| 地区 | `/region` | 5 | create / update / delete / getById / list |
| 标签 | `/tags` | 5 | create / update / delete / getById / page |
| 公司 | `/companies` | 5 | create / update / delete / getById / page |
| 人物 | `/persons` | 5 | create / update / delete / getById / page |
| 系列 | `/series` | 6 | create / update / delete / getById / page / works |
| 认证 | `/admin/login`、`/admin/logout` | 5 | loginByUsername / loginByEmail / sendLoginEmailCode / loginByEmailCode / logout |
| 用户账号 | `/admin/users` | 6 | create / page / getById / update / resetPassword / delete |
| 当前账号 | `/admin/account` | 6 | getCurrent / changePassword / sendEmailCode / verifyCurrentEmail / bindEmail / unbindEmail |
| 服务检测 | `/admin/system` | 1 | getSystemHealth |
| 图片上传 | `/uploads` | 1 | uploadImage |
| 动画数据传输 | `/data-transfer/anime` | 6 | export / template / preview / commit / batch / cancel |
| 书籍数据传输 | `/data-transfer/book` | 6 | export / template / preview / commit / batch / cancel |
| 数据质量检查 | `/data-quality` | 1 | page |
| **合计** | | **83** | |

### 设计模式

| 端点 | 方法 | 说明 |
|------|------|------|
| `/create` | POST | `@Valid` DTO + 数据清洗；人物允许同名，不执行名称重复检查 |
| `/update` | POST | ID 校验 + 更新；人物允许同名，Anime 的 persons 未传或 null 时保留 |
| `/delete` | POST `List<Long>` | 非空校验；Anime 清理 7 张关联表；人物有作品引用时拒绝删除 |
| `/{id}` | GET | 单条；Anime 聚合 7 张关联表 |
| `/list` 或 `/page` | GET | 字典用 `/list`（名称升序），可检索用 `/page`（updated_at 降序） |

- **ID 统一 `Long`**，分页参数统一 `Long`
- **事务**：Anime 所有写操作 `@Transactional`
- **分页查询**：Anime 支持公司、人物、系列 ID 关联筛选 + 关键词多字段匹配 + 标签 AND/OR + 评分范围 + 日期间隔 + 双字段排序，关联条件按 AND 组合
- **VO 分层**：详情用 `AnimeVO`（含 ID），列表用 `AnimePageVO`（展开为完整对象含 name）
- **鉴权范围**：登录接口无需令牌；`/admin/users/**` 与 `/admin/system/**` 仅 `ROLE_ADMIN`；其余接口 `ROLE_ADMIN` 或 `ROLE_EDITOR` 之一
- **令牌撤销**：`user_account.token_version` + JWT 声明比对，账号被修改、改密或退出登录后旧令牌立即失效；无不透明黑名单、无服务端会话
- **退出登录**：`POST /admin/logout` 递增令牌版本，客户端随后丢弃本地令牌；重复退出返回 401
- **用户账号**：创建 / 查询 / 改资料 / 重置密码 / 删除单条；创建与删除都排斥 `ADMIN`，数据库部分唯一索引再兜底限制最多一个 `ADMIN`；改邮箱、重置密码、删除三类操作要求重新提交操作者自己的密码
- **账号自助**：`/admin/account` 只操作 JWT 对应的账号，仅能改密码与邮箱，用户名、角色、启用状态不可自助修改；邮箱变更一律走邮件验证码，换绑必须先验证原邮箱；修改密码、绑定/换绑、解绑三处写接口返回续签的 `LoginVO`，当前设备免重新登录
- **验证码存储**：Redis + Lua 原子脚本，只存 HMAC 摘要（密钥复用 `jwt.secret`），含 60 秒冷却、每小时 10 次上限与单码 5 次错误上限，不落库
- **删除语义**：账号删除是物理删除，Anime 等业务实体的批量删除同样是物理删除，项目内没有软删除与回收站

---

## 当前进度

| 状态 | 内容 |
|------|------|
| 后端已实现，真实环境待验收 | 数据质量检查 `GET /data-quality/page`；已验证筛选、统计、同名范围、分页、错误响应及权限，真实 PostgreSQL 性能与前端联调待验收 |
| ✅ 全部完成 | 动画（5 端点）、改编类型、放送类型、地区、标签、公司、系列 |
| ✅ 后端已实现 | 人物资料 5 端点、全类型引用删除保护、动画人物关联创建/修改/详情/分页/删除清理；前端接入及真实 PostgreSQL 联调另行验证 |
| ✅ 后端已实现 | 书籍及书籍类型各 5 端点、全部 BOOK/book 关联、整数评分、反向关联分页与引用保护；已做隔离自测，真实 PostgreSQL 与前端联调未执行 |
| 阶段收尾 | COS 封面上传前后端已接入，JPEG/PNG 校验、ANIME/BOOK 目录、权限及超限规则见第十六节 |
| 阶段收尾 | 动画、书籍导入导出前后端已接入，v2 格式、导入确认、批次结果及部署要求见第十七节 |
| ✅ 已完成 | 账号与登录（`user_account` 表、用户名密码 / 邮箱密码 / 邮箱验证码三种登录方式、BCrypt 密码校验、JWT 签发；验证码登录仅允许已验证邮箱的启用 ADMIN、EDITOR） |
| ✅ 已完成 | 授权分层：`ADMIN` 可访问全部接口，`EDITOR` 可访问内容接口与自身账号接口，`USER` 一律 403 |
| ✅ 已完成 | 用户账号管理：创建 / 分页 / 详情 / 修改 / 重置密码 / 单条删除，含令牌版本强制下线与单 `ADMIN` 约束 |
| ✅ 已完成 | 退出登录：`POST /admin/logout` 递增令牌版本，使该账号同版本签发的全部令牌立即失效 |
| ✅ 已完成 | 账号自助编辑：查询自身账号、修改密码、绑定 / 换绑 / 解绑邮箱，邮箱变更需邮件验证码；三处写接口成功后返回新登录令牌 |
| ✅ 已完成 | 私有配置：`application.yml` 只保留占位符，真实密钥由仓库外的 `application-secrets.yml` 提供 |
| ⚠️ 默认关闭 | 邮箱验证码需要先配好 SMTP 与 Redis，再把 `ACGBOX_ACCOUNT_EMAIL_ENABLED` 打开；未启用时查询账号与修改密码仍可用 |
| ✅ 已完成 | 前端：`/login` 登录页、路由守卫与 `meta.roles` 菜单过滤、401 自动清理跳转、用户菜单退出登录、`/admin/users` 用户管理页面、`/admin/account` 个人账号页面 |
| ⚠️ 待细分 | `ADMIN` 与 `EDITOR` 在内容接口上权限完全相同，没有更细的操作级权限；`ResultCode` 预留的 401/403 未参与业务返回 |
| ❌ 未实现 | 令牌刷新（refresh token）：令牌到期后只能重新登录；退出登录会使该账号全部旧令牌失效 |
| ❌ 未实现 | 账号批量删除接口（当前每次只能删一个） |
| ❌ 未实现 | 社区评分系统（计划用 Redis；个人评分 `personal_rating` 已实现） |

---

> 文档版本：v6.13  
> 最后更新：2026-11-06  
> 作者：killze
