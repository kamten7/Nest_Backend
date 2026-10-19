-- ============================================================================
-- pgvector 初始化（在 pgvector 容器首次启动时执行）
-- 表结构与 LangChain4j PgVectorEmbeddingStore 的 COMBINED_JSONB 模式对齐：
--   embedding_id / embedding / text / metadata(JSONB)
-- HNSW 索引用于余弦距离检索；数据量小时 PostgreSQL 也会走顺序扫描。
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS nest_knowledge_embedding (
    embedding_id uuid PRIMARY KEY,
    embedding vector(512) NOT NULL,
    text text NOT NULL,
    metadata jsonb
);

CREATE INDEX IF NOT EXISTS idx_nest_knowledge_embedding_hnsw
    ON nest_knowledge_embedding USING hnsw (embedding vector_cosine_ops);
