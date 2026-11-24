# ACGBox 用户端接口文档

## 1. 游客作品目录

以下路径均为后端路径，不含前端代理前缀。仅对应的 GET 请求允许匿名访问；管理端权限不变。隐藏作品由后端强制排除，不支持通过参数改变可见性。没有公开状态记录的作品视为公开。

| 路径 | 说明 |
| --- | --- |
| GET /web/anime/page | 公开动画分页 |
| GET /web/book/page | 公开书籍分页 |
| GET /web/anime/filters | 动画筛选项 |
| GET /web/book/filters | 书籍筛选项 |

### 1.1 分页查询

| 参数 | 类型 | 默认及说明 |
| --- | --- | --- |
| pageNum / pageSize | 整数 | 默认 1 / 20，页码至少为 1，每页 1～100 |
| keyword | 字符串 | 可省略；作品名、别名或所属系列名包含关键字，忽略大小写 |
| adaptationTypeId | 整数 | 可省略；改编类型 ID |
| broadcastTypeId / bookTypeId | 整数 | 可省略；分别用于动画和书籍 |
| status | 整数 | 可省略；对应类型的状态选项 ID |
| year | 字符串 | 省略表示全部；四位年份表示当年，`UNKNOWN` 表示未填写日期 |
| tagIds | 整数数组 | 重复参数形式；重复 ID 去重，作品须同时匹配所有标签 |
| sortBy | 字符串 | `DATE`（默认）或 `NAME` |
| sortDirection | 字符串 | `DESC`（默认）或 `ASC` |

筛选条件取交集。日期排序用开播或发表日期，空日期在两个方向均置尾；名称按忽略大小写后的名称以数据库 C 排序规则比较。同值按作品 ID 降序，不按个人评分排序。无匹配时总数、总页数为 0，`rows: []`；超页保留总数和总页数，`rows: []`。无效参数沿用 Result 错误结构。

```http
GET /web/anime/page?pageNum=1&pageSize=20&year=2024&tagIds=1&tagIds=2&sortBy=DATE&sortDirection=DESC
GET /web/book/page?keyword=灯塔&bookTypeId=1&year=UNKNOWN&sortBy=NAME&sortDirection=ASC
```

```json
{
  "code": 200, "message": "操作成功",
  "data": {
    "pageNum": 1, "pageSize": 20, "total": 1, "pages": 1,
    "rows": [{
      "id": 7, "name": "示例动画", "aliasNames": ["Example"],
      "coverImageUrl": null, "status": 3, "airDate": "2024-04-01",
      "broadcastType": {"id": 1, "name": "TV"},
      "adaptationType": {"id": 1, "name": "原创"},
      "tags": [{"id": 1, "name": "奇幻"}, {"id": 2, "name": "治愈"}],
      "persons": [{"personId": 8, "personName": "示例人物", "role": "监督"}],
      "companies": [{"companyId": 9, "companyName": "示例公司", "role": "制作"}]
    }]
  }
}
```

书籍行使用 `publishDate`、`bookType`，不返回 `airDate`、`broadcastType`；其他字段相同。日期为 `yyyy-MM-dd` 或 null。缺封面、缺字典资料时对应字段为 null，公司的职责也可为 null。别名、标签、人员、公司没有条目时为数组 `[]`。不返回个人评分、后台时间戳或可见性记录。

### 1.2 筛选项

```http
GET /web/anime/filters
GET /web/book/filters
```

```json
{
  "code": 200, "message": "操作成功",
  "data": {
    "broadcastTypes": [{"id": 1, "name": "TV"}],
    "adaptationTypes": [{"id": 1, "name": "原创"}],
    "statuses": [{"id": 1, "name": "未放送"}, {"id": 2, "name": "放送中"}, {"id": 3, "name": "已完结"}, {"id": 4, "name": "其他"}],
    "years": [2026, 2024],
    "hasUnknownYear": true,
    "tags": [{"id": 1, "name": "奇幻"}]
  }
}
```

书籍筛选项以 `bookTypes` 替换 `broadcastTypes`；书籍状态 ID 0～5 分别为其他、未发表、连载中、已完结、暂停中、已终止。选项 ID 和年份均为 JSON 数字。类型选项沿用字典排序；标签仅取被当前类型公开作品引用的标签，按名称首字母 A→Z 返回。年份仅返回当前类型公开作品实际出现过的年份，去重后降序；公开作品有空日期时 `hasUnknownYear` 为 true。筛选项与当前页条件无关。
