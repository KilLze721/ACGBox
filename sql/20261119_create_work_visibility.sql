-- 作品公开状态增量脚本，由用户在目标 PostgreSQL 数据库手动执行。
-- 无状态记录的作品默认公开，历史作品无需回填；本脚本只创建状态表。
-- 此表以作品类型和作品 ID 唯一定位记录，后端负责作品存在性与删除清理。

BEGIN;

CREATE TABLE IF NOT EXISTS public.work_visibility (
    target_type varchar(16) NOT NULL,
    target_id integer NOT NULL,
    is_public boolean NOT NULL DEFAULT true,
    CONSTRAINT pk_work_visibility PRIMARY KEY (target_type, target_id),
    CONSTRAINT ck_work_visibility_target_type CHECK (target_type IN ('ANIME', 'BOOK'))
);

COMMENT ON TABLE public.work_visibility IS '作品公开状态表：无记录默认公开，显式记录按 is_public 判断';
COMMENT ON COLUMN public.work_visibility.target_type IS '作品类型：ANIME 动画、BOOK 书籍；漫画和小说统一按书籍 ID 定位';
COMMENT ON COLUMN public.work_visibility.target_id IS '作品 ID：对应动画或书籍主表的 integer 主键，由后端校验存在性';
COMMENT ON COLUMN public.work_visibility.is_public IS '公开展示开关：true 公开，false 隐藏';

COMMIT;
