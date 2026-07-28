/*
 Navicat Premium Dump SQL

 Source Server         : PostgreSQL
 Source Server Type    : PostgreSQL
 Source Server Version : 180001 (180001)
 Source Host           : localhost:5432
 Source Catalog        : acgbox
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 180001 (180001)
 File Encoding         : 65001

 Date: 12/07/2026 23:36:59
*/


-- ----------------------------
-- Sequence structure for adaptation_type_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."adaptation_type_id_seq";
CREATE SEQUENCE "public"."adaptation_type_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for alias_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."alias_id_seq";
CREATE SEQUENCE "public"."alias_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for anime_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."anime_id_seq";
CREATE SEQUENCE "public"."anime_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for broadcast_type_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."broadcast_type_id_seq";
CREATE SEQUENCE "public"."broadcast_type_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for company_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."company_id_seq";
CREATE SEQUENCE "public"."company_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for company_relation_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."company_relation_id_seq";
CREATE SEQUENCE "public"."company_relation_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for external_link_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."external_link_id_seq";
CREATE SEQUENCE "public"."external_link_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for personal_rating_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."personal_rating_id_seq";
CREATE SEQUENCE "public"."personal_rating_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for region_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."region_id_seq";
CREATE SEQUENCE "public"."region_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for series_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."series_id_seq";
CREATE SEQUENCE "public"."series_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for series_item_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."series_item_id_seq";
CREATE SEQUENCE "public"."series_item_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for tag_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."tag_id_seq";
CREATE SEQUENCE "public"."tag_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for tag_relation_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."tag_relation_id_seq";
CREATE SEQUENCE "public"."tag_relation_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 2147483647
START 1
CACHE 1;

-- ----------------------------
-- Table structure for adaptation_type
-- ----------------------------
DROP TABLE IF EXISTS "public"."adaptation_type";
CREATE TABLE "public"."adaptation_type" (
  "id" int4 NOT NULL DEFAULT nextval('adaptation_type_id_seq'::regclass),
  "name" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "created_at" timestamp(6) NOT NULL,
  "updated_at" timestamp(6) NOT NULL
)
;
COMMENT ON COLUMN "public"."adaptation_type"."id" IS '改编类型ID';
COMMENT ON COLUMN "public"."adaptation_type"."name" IS '改编类型名称';
COMMENT ON COLUMN "public"."adaptation_type"."created_at" IS '记录创建时间';
COMMENT ON COLUMN "public"."adaptation_type"."updated_at" IS '记录最后更新时间';
COMMENT ON TABLE "public"."adaptation_type" IS '改编类型表：存储动画、漫画、小说等作品的改编类型';

-- ----------------------------
-- Table structure for alias
-- ----------------------------
DROP TABLE IF EXISTS "public"."alias";
CREATE TABLE "public"."alias" (
  "id" int4 NOT NULL DEFAULT nextval('alias_id_seq'::regclass),
  "target_type" varchar(30) COLLATE "pg_catalog"."default" NOT NULL,
  "target_id" int4 NOT NULL,
  "alias_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL
)
;
COMMENT ON COLUMN "public"."alias"."id" IS '别名 ID';
COMMENT ON COLUMN "public"."alias"."target_type" IS '关联对象类型：ANIME、MANGA、NOVEL、SERIES、OTHER';
COMMENT ON COLUMN "public"."alias"."target_id" IS '关联对象 ID';
COMMENT ON COLUMN "public"."alias"."alias_name" IS '别名名称';
COMMENT ON TABLE "public"."alias" IS '通用别名表：记录动画、漫画、小说等对象的别名';

-- ----------------------------
-- Table structure for anime
-- ----------------------------
DROP TABLE IF EXISTS "public"."anime";
CREATE TABLE "public"."anime" (
  "id" int4 NOT NULL DEFAULT nextval('anime_id_seq'::regclass),
  "name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "episode_count" int4,
  "broadcast_type_id" int4 NOT NULL,
  "adaptation_type_id" int4 NOT NULL,
  "air_date" date NOT NULL,
  "cover_image_url" varchar(500) COLLATE "pg_catalog"."default",
  "status" int4 NOT NULL,
  "region_id" int4 NOT NULL,
  "created_at" timestamp(6) NOT NULL DEFAULT now(),
  "updated_at" timestamp(6) NOT NULL DEFAULT now()
)
;
COMMENT ON COLUMN "public"."anime"."id" IS '动画ID';
COMMENT ON COLUMN "public"."anime"."name" IS '动画名称';
COMMENT ON COLUMN "public"."anime"."episode_count" IS '动画总集数';
COMMENT ON COLUMN "public"."anime"."broadcast_type_id" IS '放送类型ID，关联broadcast_type表';
COMMENT ON COLUMN "public"."anime"."adaptation_type_id" IS '改编类型ID，关联adaptation_type表';
COMMENT ON COLUMN "public"."anime"."air_date" IS '动画放送日期';
COMMENT ON COLUMN "public"."anime"."cover_image_url" IS '动画封面图片URL';
COMMENT ON COLUMN "public"."anime"."status" IS '动画放送状态，1: 未放送 2: 放送中 3: 已完结 4: 其他';
COMMENT ON COLUMN "public"."anime"."region_id" IS '动画地区，关联region表';
COMMENT ON COLUMN "public"."anime"."created_at" IS '记录创建时间';
COMMENT ON COLUMN "public"."anime"."updated_at" IS '记录最后更新时间';
COMMENT ON TABLE "public"."anime" IS '动画表：存储动画的基本信息';

-- ----------------------------
-- Table structure for broadcast_type
-- ----------------------------
DROP TABLE IF EXISTS "public"."broadcast_type";
CREATE TABLE "public"."broadcast_type" (
  "id" int4 NOT NULL DEFAULT nextval('broadcast_type_id_seq'::regclass),
  "name" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "created_at" timestamp(6) NOT NULL,
  "updated_at" timestamp(6) NOT NULL
)
;
COMMENT ON COLUMN "public"."broadcast_type"."id" IS '放送类型ID';
COMMENT ON COLUMN "public"."broadcast_type"."name" IS '放送类型名称';
COMMENT ON COLUMN "public"."broadcast_type"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."broadcast_type"."updated_at" IS '最后修改时间';
COMMENT ON TABLE "public"."broadcast_type" IS '放送类型表：例如 TV、WEB、OVA、剧场版、其他';

-- ----------------------------
-- Table structure for company
-- ----------------------------
DROP TABLE IF EXISTS "public"."company";
CREATE TABLE "public"."company" (
  "id" int4 NOT NULL DEFAULT nextval('company_id_seq'::regclass),
  "name" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "description" text COLLATE "pg_catalog"."default",
  "created_at" timestamp(6) NOT NULL,
  "updated_at" timestamp(6) NOT NULL
)
;
COMMENT ON COLUMN "public"."company"."id" IS '公司ID';
COMMENT ON COLUMN "public"."company"."name" IS '公司名称';
COMMENT ON COLUMN "public"."company"."description" IS '公司简介';
COMMENT ON COLUMN "public"."company"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."company"."updated_at" IS '最后修改时间';
COMMENT ON TABLE "public"."company" IS '制作公司表：存储动画、漫画、小说作品的制作/发行公司';

-- ----------------------------
-- Table structure for company_relation
-- ----------------------------
DROP TABLE IF EXISTS "public"."company_relation";
CREATE TABLE "public"."company_relation" (
  "id" int4 NOT NULL DEFAULT nextval('company_relation_id_seq'::regclass),
  "target_type" varchar(30) COLLATE "pg_catalog"."default" NOT NULL,
  "target_id" int4 NOT NULL,
  "company_id" int4 NOT NULL,
  "role" varchar(50) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."company_relation"."id" IS '公司关联 ID';
COMMENT ON COLUMN "public"."company_relation"."target_type" IS '关联对象类型：ANIME、MANGA、NOVEL、SERIES、OTHER';
COMMENT ON COLUMN "public"."company_relation"."target_id" IS '关联对象 ID';
COMMENT ON COLUMN "public"."company_relation"."company_id" IS '公司 ID';
COMMENT ON COLUMN "public"."company_relation"."role" IS '公司在对象中的职责，例如制作、发行、企划';
COMMENT ON TABLE "public"."company_relation" IS '通用公司关联表：记录动画、漫画、小说等对象与公司的关联关系';

-- ----------------------------
-- Table structure for external_link
-- ----------------------------
DROP TABLE IF EXISTS "public"."external_link";
CREATE TABLE "public"."external_link" (
  "id" int4 NOT NULL DEFAULT nextval('external_link_id_seq'::regclass),
  "title" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "url" varchar(500) COLLATE "pg_catalog"."default" NOT NULL,
  "target_type" varchar(30) COLLATE "pg_catalog"."default" NOT NULL,
  "target_id" int4 NOT NULL,
  "sort_order" int4 NOT NULL DEFAULT 0
)
;
COMMENT ON COLUMN "public"."external_link"."id" IS '链接 ID';
COMMENT ON COLUMN "public"."external_link"."title" IS '链接标题，例如官网、Bangumi、维基百科';
COMMENT ON COLUMN "public"."external_link"."url" IS '链接 URL';
COMMENT ON COLUMN "public"."external_link"."target_type" IS '外链所属对象类型：ANIME、COMPANY、SERIES、MANGA、NOVEL、OTHER';
COMMENT ON COLUMN "public"."external_link"."target_id" IS '外链所属对象 ID';
COMMENT ON COLUMN "public"."external_link"."sort_order" IS '排序值，越小越靠前';
COMMENT ON TABLE "public"."external_link" IS '通用外部链接表：存储动画、公司、系列等对象的外部链接';

-- ----------------------------
-- Table structure for personal_rating
-- ----------------------------
DROP TABLE IF EXISTS "public"."personal_rating";
CREATE TABLE "public"."personal_rating" (
  "id" int4 NOT NULL DEFAULT nextval('personal_rating_id_seq'::regclass),
  "target_type" varchar(30) COLLATE "pg_catalog"."default" NOT NULL,
  "target_id" int4 NOT NULL,
  "score" numeric(3,1) NOT NULL,
  "created_at" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_at" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
)
;
COMMENT ON COLUMN "public"."personal_rating"."id" IS '个人评分 ID';
COMMENT ON COLUMN "public"."personal_rating"."target_type" IS '评分对象类型：ANIME、MANGA、NOVEL';
COMMENT ON COLUMN "public"."personal_rating"."target_id" IS '评分对象 ID';
COMMENT ON COLUMN "public"."personal_rating"."score" IS '个人评分，范围 0 到 10，支持一位小数';
COMMENT ON COLUMN "public"."personal_rating"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."personal_rating"."updated_at" IS '最后修改时间';
COMMENT ON TABLE "public"."personal_rating" IS '个人评分表：记录站长/个人对动画、漫画、小说等作品的评分';

-- ----------------------------
-- Table structure for region
-- ----------------------------
DROP TABLE IF EXISTS "public"."region";
CREATE TABLE "public"."region" (
  "id" int4 NOT NULL DEFAULT nextval('region_id_seq'::regclass),
  "name" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "created_at" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_at" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
)
;
COMMENT ON COLUMN "public"."region"."id" IS '地区ID';
COMMENT ON COLUMN "public"."region"."name" IS '地区名称';
COMMENT ON COLUMN "public"."region"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."region"."updated_at" IS '最后修改时间';
COMMENT ON TABLE "public"."region" IS '地区表，用于表示作品所属国家或地区';

-- ----------------------------
-- Table structure for series
-- ----------------------------
DROP TABLE IF EXISTS "public"."series";
CREATE TABLE "public"."series" (
  "id" int4 NOT NULL DEFAULT nextval('series_id_seq'::regclass),
  "name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "description" text COLLATE "pg_catalog"."default",
  "created_at" timestamp(6) NOT NULL DEFAULT now(),
  "updated_at" timestamp(6) DEFAULT now()
)
;
COMMENT ON COLUMN "public"."series"."id" IS '系列ID';
COMMENT ON COLUMN "public"."series"."name" IS '系列名称';
COMMENT ON COLUMN "public"."series"."description" IS '系列简介';
COMMENT ON COLUMN "public"."series"."created_at" IS '记录创建时间';
COMMENT ON COLUMN "public"."series"."updated_at" IS '记录最后更新时间';
COMMENT ON TABLE "public"."series" IS '系列表：用于存储一个作品系列的基础信息';

-- ----------------------------
-- Table structure for series_item
-- ----------------------------
DROP TABLE IF EXISTS "public"."series_item";
CREATE TABLE "public"."series_item" (
  "id" int4 NOT NULL DEFAULT nextval('series_item_id_seq'::regclass),
  "series_id" int4 NOT NULL,
  "work_type" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "work_id" int4 NOT NULL,
  "sort_order" int4 NOT NULL DEFAULT 0
)
;
COMMENT ON COLUMN "public"."series_item"."id" IS '系列作品条目ID';
COMMENT ON COLUMN "public"."series_item"."series_id" IS '系列ID，关联series表';
COMMENT ON COLUMN "public"."series_item"."work_type" IS '作品类型，例如 anime、manga、novel';
COMMENT ON COLUMN "public"."series_item"."work_id" IS '作品ID，对应work_type指向的作品表主键';
COMMENT ON COLUMN "public"."series_item"."sort_order" IS '系列内排序值，数值越小越靠前';
COMMENT ON TABLE "public"."series_item" IS '系列作品条目表：用于记录系列下包含的动画、漫画、小说等作品';

-- ----------------------------
-- Table structure for tag
-- ----------------------------
DROP TABLE IF EXISTS "public"."tag";
CREATE TABLE "public"."tag" (
  "id" int4 NOT NULL DEFAULT nextval('tag_id_seq'::regclass),
  "name" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "created_at" timestamp(6) NOT NULL,
  "updated_at" timestamp(6) NOT NULL
)
;
COMMENT ON COLUMN "public"."tag"."id" IS '类型词条ID';
COMMENT ON COLUMN "public"."tag"."name" IS '类型词条名称';
COMMENT ON COLUMN "public"."tag"."created_at" IS '记录创建时间';
COMMENT ON COLUMN "public"."tag"."updated_at" IS '记录最后更新时间';
COMMENT ON TABLE "public"."tag" IS '类型词条表：存储科幻、恋爱、奇幻等标签，可用于动画、漫画、小说';

-- ----------------------------
-- Table structure for tag_relation
-- ----------------------------
DROP TABLE IF EXISTS "public"."tag_relation";
CREATE TABLE "public"."tag_relation" (
  "id" int4 NOT NULL DEFAULT nextval('tag_relation_id_seq'::regclass),
  "target_type" varchar(30) COLLATE "pg_catalog"."default" NOT NULL,
  "target_id" int4 NOT NULL,
  "tag_id" int4 NOT NULL
)
;
COMMENT ON COLUMN "public"."tag_relation"."id" IS '标签关联 ID';
COMMENT ON COLUMN "public"."tag_relation"."target_type" IS '关联对象类型：ANIME、MANGA、NOVEL、SERIES、OTHER';
COMMENT ON COLUMN "public"."tag_relation"."target_id" IS '关联对象 ID';
COMMENT ON COLUMN "public"."tag_relation"."tag_id" IS '标签 ID';
COMMENT ON TABLE "public"."tag_relation" IS '通用标签关联表：记录动画、漫画、小说等对象与标签的关联关系';

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."adaptation_type_id_seq"
OWNED BY "public"."adaptation_type"."id";
SELECT setval('"public"."adaptation_type_id_seq"', 4, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."alias_id_seq"
OWNED BY "public"."alias"."id";
SELECT setval('"public"."alias_id_seq"', 9, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."anime_id_seq"
OWNED BY "public"."anime"."id";
SELECT setval('"public"."anime_id_seq"', 16, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."broadcast_type_id_seq"
OWNED BY "public"."broadcast_type"."id";
SELECT setval('"public"."broadcast_type_id_seq"', 4, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."company_id_seq"
OWNED BY "public"."company"."id";
SELECT setval('"public"."company_id_seq"', 5, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."company_relation_id_seq"
OWNED BY "public"."company_relation"."id";
SELECT setval('"public"."company_relation_id_seq"', 11, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."external_link_id_seq"
OWNED BY "public"."external_link"."id";
SELECT setval('"public"."external_link_id_seq"', 16, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."personal_rating_id_seq"
OWNED BY "public"."personal_rating"."id";
SELECT setval('"public"."personal_rating_id_seq"', 2, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."region_id_seq"
OWNED BY "public"."region"."id";
SELECT setval('"public"."region_id_seq"', 5, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."series_id_seq"
OWNED BY "public"."series"."id";
SELECT setval('"public"."series_id_seq"', 7, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."series_item_id_seq"
OWNED BY "public"."series_item"."id";
SELECT setval('"public"."series_item_id_seq"', 6, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."tag_id_seq"
OWNED BY "public"."tag"."id";
SELECT setval('"public"."tag_id_seq"', 23, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."tag_relation_id_seq"
OWNED BY "public"."tag_relation"."id";
SELECT setval('"public"."tag_relation_id_seq"', 42, true);

-- ----------------------------
-- Uniques structure for table adaptation_type
-- ----------------------------
ALTER TABLE "public"."adaptation_type" ADD CONSTRAINT "adaptation_type_name_key" UNIQUE ("name");

-- ----------------------------
-- Primary Key structure for table adaptation_type
-- ----------------------------
ALTER TABLE "public"."adaptation_type" ADD CONSTRAINT "adaptation_type_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table alias
-- ----------------------------
CREATE INDEX "idx_alias_name" ON "public"."alias" USING btree (
  "alias_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_alias_target" ON "public"."alias" USING btree (
  "target_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "target_id" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE UNIQUE INDEX "uk_alias_target_name" ON "public"."alias" USING btree (
  "target_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "target_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "alias_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Checks structure for table alias
-- ----------------------------
ALTER TABLE "public"."alias" ADD CONSTRAINT "alias_target_type_check" CHECK (target_type::text = ANY (ARRAY['ANIME'::character varying, 'MANGA'::character varying, 'NOVEL'::character varying, 'SERIES'::character varying, 'OTHER'::character varying]::text[]));

-- ----------------------------
-- Primary Key structure for table alias
-- ----------------------------
ALTER TABLE "public"."alias" ADD CONSTRAINT "alias_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table anime
-- ----------------------------
ALTER TABLE "public"."anime" ADD CONSTRAINT "anime_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Uniques structure for table broadcast_type
-- ----------------------------
ALTER TABLE "public"."broadcast_type" ADD CONSTRAINT "broadcast_type_name_key" UNIQUE ("name");

-- ----------------------------
-- Primary Key structure for table broadcast_type
-- ----------------------------
ALTER TABLE "public"."broadcast_type" ADD CONSTRAINT "broadcast_type_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Uniques structure for table company
-- ----------------------------
ALTER TABLE "public"."company" ADD CONSTRAINT "company_name_key" UNIQUE ("name");

-- ----------------------------
-- Primary Key structure for table company
-- ----------------------------
ALTER TABLE "public"."company" ADD CONSTRAINT "company_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table company_relation
-- ----------------------------
CREATE INDEX "idx_company_relation_company" ON "public"."company_relation" USING btree (
  "company_id" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "idx_company_relation_target" ON "public"."company_relation" USING btree (
  "target_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "target_id" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE UNIQUE INDEX "uk_company_relation_target_company" ON "public"."company_relation" USING btree (
  "target_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "target_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "company_id" "pg_catalog"."int4_ops" ASC NULLS LAST
);

-- ----------------------------
-- Checks structure for table company_relation
-- ----------------------------
ALTER TABLE "public"."company_relation" ADD CONSTRAINT "company_relation_target_type_check" CHECK (target_type::text = ANY (ARRAY['ANIME'::character varying, 'MANGA'::character varying, 'NOVEL'::character varying, 'SERIES'::character varying, 'OTHER'::character varying]::text[]));

-- ----------------------------
-- Primary Key structure for table company_relation
-- ----------------------------
ALTER TABLE "public"."company_relation" ADD CONSTRAINT "company_relation_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table external_link
-- ----------------------------
CREATE INDEX "idx_external_link_sort" ON "public"."external_link" USING btree (
  "target_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "target_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "sort_order" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "id" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "idx_external_link_target" ON "public"."external_link" USING btree (
  "target_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "target_id" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE UNIQUE INDEX "uk_external_link_target_url" ON "public"."external_link" USING btree (
  "target_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "target_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "url" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Checks structure for table external_link
-- ----------------------------
ALTER TABLE "public"."external_link" ADD CONSTRAINT "external_link_target_type_check" CHECK (target_type::text = ANY (ARRAY['ANIME'::character varying, 'COMPANY'::character varying, 'SERIES'::character varying, 'MANGA'::character varying, 'NOVEL'::character varying, 'OTHER'::character varying]::text[]));

-- ----------------------------
-- Primary Key structure for table external_link
-- ----------------------------
ALTER TABLE "public"."external_link" ADD CONSTRAINT "external_link_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table personal_rating
-- ----------------------------
CREATE INDEX "idx_personal_rating_score" ON "public"."personal_rating" USING btree (
  "score" "pg_catalog"."numeric_ops" ASC NULLS LAST
);
CREATE UNIQUE INDEX "uk_personal_rating_target" ON "public"."personal_rating" USING btree (
  "target_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "target_id" "pg_catalog"."int4_ops" ASC NULLS LAST
);

-- ----------------------------
-- Checks structure for table personal_rating
-- ----------------------------
ALTER TABLE "public"."personal_rating" ADD CONSTRAINT "personal_rating_target_type_check" CHECK (target_type::text = ANY (ARRAY['ANIME'::character varying, 'MANGA'::character varying, 'NOVEL'::character varying]::text[]));
ALTER TABLE "public"."personal_rating" ADD CONSTRAINT "personal_rating_score_check" CHECK (score >= 0::numeric AND score <= 10::numeric);

-- ----------------------------
-- Primary Key structure for table personal_rating
-- ----------------------------
ALTER TABLE "public"."personal_rating" ADD CONSTRAINT "personal_rating_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Uniques structure for table region
-- ----------------------------
ALTER TABLE "public"."region" ADD CONSTRAINT "region_name_key" UNIQUE ("name");

-- ----------------------------
-- Primary Key structure for table region
-- ----------------------------
ALTER TABLE "public"."region" ADD CONSTRAINT "region_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table series
-- ----------------------------
ALTER TABLE "public"."series" ADD CONSTRAINT "series_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table series_item
-- ----------------------------
CREATE INDEX "idx_series_item_series_sort" ON "public"."series_item" USING btree (
  "series_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "sort_order" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "id" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "idx_series_item_work" ON "public"."series_item" USING btree (
  "work_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "work_id" "pg_catalog"."int4_ops" ASC NULLS LAST
);

-- ----------------------------
-- Uniques structure for table series_item
-- ----------------------------
ALTER TABLE "public"."series_item" ADD CONSTRAINT "series_item_series_work_key" UNIQUE ("series_id", "work_type", "work_id");

-- ----------------------------
-- Checks structure for table series_item
-- ----------------------------
ALTER TABLE "public"."series_item" ADD CONSTRAINT "series_item_work_type_check" CHECK (work_type::text = ANY (ARRAY['anime'::character varying, 'manga'::character varying, 'novel'::character varying]::text[]));

-- ----------------------------
-- Primary Key structure for table series_item
-- ----------------------------
ALTER TABLE "public"."series_item" ADD CONSTRAINT "series_item_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Uniques structure for table tag
-- ----------------------------
ALTER TABLE "public"."tag" ADD CONSTRAINT "tag_name_key" UNIQUE ("name");

-- ----------------------------
-- Primary Key structure for table tag
-- ----------------------------
ALTER TABLE "public"."tag" ADD CONSTRAINT "tag_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table tag_relation
-- ----------------------------
CREATE INDEX "idx_tag_relation_tag" ON "public"."tag_relation" USING btree (
  "tag_id" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "idx_tag_relation_target" ON "public"."tag_relation" USING btree (
  "target_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "target_id" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE UNIQUE INDEX "uk_tag_relation_target_tag" ON "public"."tag_relation" USING btree (
  "target_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "target_id" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "tag_id" "pg_catalog"."int4_ops" ASC NULLS LAST
);

-- ----------------------------
-- Checks structure for table tag_relation
-- ----------------------------
ALTER TABLE "public"."tag_relation" ADD CONSTRAINT "tag_relation_target_type_check" CHECK (target_type::text = ANY (ARRAY['ANIME'::character varying, 'MANGA'::character varying, 'NOVEL'::character varying, 'SERIES'::character varying, 'OTHER'::character varying]::text[]));

-- ----------------------------
-- Primary Key structure for table tag_relation
-- ----------------------------
ALTER TABLE "public"."tag_relation" ADD CONSTRAINT "tag_relation_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Foreign Keys structure for table anime
-- ----------------------------
ALTER TABLE "public"."anime" ADD CONSTRAINT "anime_adaptation_type_id_fkey" FOREIGN KEY ("adaptation_type_id") REFERENCES "public"."adaptation_type" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE "public"."anime" ADD CONSTRAINT "anime_broadcast_type_id_fkey" FOREIGN KEY ("broadcast_type_id") REFERENCES "public"."broadcast_type" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE "public"."anime" ADD CONSTRAINT "anime_region_id_fkey" FOREIGN KEY ("region_id") REFERENCES "public"."region" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table company_relation
-- ----------------------------
ALTER TABLE "public"."company_relation" ADD CONSTRAINT "company_relation_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "public"."company" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table series_item
-- ----------------------------
ALTER TABLE "public"."series_item" ADD CONSTRAINT "series_item_series_id_fkey" FOREIGN KEY ("series_id") REFERENCES "public"."series" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table tag_relation
-- ----------------------------
ALTER TABLE "public"."tag_relation" ADD CONSTRAINT "tag_relation_tag_id_fkey" FOREIGN KEY ("tag_id") REFERENCES "public"."tag" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;
