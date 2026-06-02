-- ============================================================
-- 修复表结构：添加自增序列
-- 执行此脚本前请确保已备份数据
-- ============================================================

-- 1. 清空所有表数据
TRUNCATE TABLE chat_message, chat_session, knowledge_chunk, knowledge_file, sys_user RESTART IDENTITY CASCADE;

-- 2. 为 sys_user 表添加自增序列
ALTER TABLE sys_user ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE IF EXISTS sys_user_id_seq CASCADE;
CREATE SEQUENCE sys_user_id_seq START 1;
ALTER TABLE sys_user ALTER COLUMN id SET DEFAULT nextval('sys_user_id_seq');
ALTER SEQUENCE sys_user_id_seq OWNED BY sys_user.id;

-- 3. 为 knowledge_file 表添加自增序列
ALTER TABLE knowledge_file ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE IF EXISTS knowledge_file_id_seq CASCADE;
CREATE SEQUENCE knowledge_file_id_seq START 1;
ALTER TABLE knowledge_file ALTER COLUMN id SET DEFAULT nextval('knowledge_file_id_seq');
ALTER SEQUENCE knowledge_file_id_seq OWNED BY knowledge_file.id;

-- 4. 为 knowledge_chunk 表添加自增序列
ALTER TABLE knowledge_chunk ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE IF EXISTS knowledge_chunk_id_seq CASCADE;
CREATE SEQUENCE knowledge_chunk_id_seq START 1;
ALTER TABLE knowledge_chunk ALTER COLUMN id SET DEFAULT nextval('knowledge_chunk_id_seq');
ALTER SEQUENCE knowledge_chunk_id_seq OWNED BY knowledge_chunk.id;

-- 5. 为 chat_session 表添加自增序列
ALTER TABLE chat_session ALTER COLUMN id DROP DEFAULT;
DROP SEQUENCE IF EXISTS chat_session_id_seq CASCADE;
CREATE SEQUENCE chat_session_id_seq START 1;
ALTER TABLE chat_session ALTER COLUMN id SET DEFAULT nextval('chat_session_id_seq');
ALTER SEQUENCE chat_session_id_seq OWNED BY chat_session.id;

-- 6. 验证序列是否创建成功
SELECT 
    sequence_name,
    last_value
FROM information_schema.sequences 
WHERE sequence_schema = 'public'
ORDER BY sequence_name;

-- 完成
SELECT '表结构修复完成！请重新注册用户测试。' AS message;
