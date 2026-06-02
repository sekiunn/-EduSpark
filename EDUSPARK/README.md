# EduSpark

EduSpark 是一个面向教师备课与课堂资源生成的多模态 AI 教学平台。项目包含教师端、管理端和 Spring Boot 后端，支持 AI 对话、知识库检索增强、教案生成、PPT 生成、互动课件生成、语音转写和 PPT 模板管理等能力。

## 功能特性

- AI 教学助手：支持普通对话、文件上下文、多轮澄清和流式响应。
- 知识库 RAG：支持 PDF、Word、TXT、Markdown 上传、切分、向量检索和全文检索。
- 教案生成：基于教学主题、知识库资料和 AI 模型生成教案文档。
- PPT 生成：支持模板选择、逐页生成进度、PPTX 导出和缩略图预览。
- 互动课件：生成可下载的交互式 HTML 教学资源。
- 多模态能力：包含图片、视频资料解析入口，以及语音转写接口。
- 管理后台：维护 PPT 场景、风格、模板文件和意图规则。
- Docker 部署：提供 PostgreSQL、Redis、Ollama、后端、教师端和管理端编排。

## 技术栈

### 后端

- Java 17
- Spring Boot 3.5
- Spring Web / WebFlux / Validation
- MyBatis-Plus
- PostgreSQL 16
- pgvector + PGroonga
- Redis
- JWT
- Apache POI / PDFBox
- LibreOffice / FFmpeg
- Ollama、DashScope、DeepSeek、阿里云 OSS / ASR

### 前端

- Vue 3
- Vite 7
- Vue Router
- Tailwind CSS
- TipTap
- marked、KaTeX、highlight.js
- lucide-vue-next
- Nginx

## 目录结构

```text
EDUSPARK
├── BACKEND/EduSpark                 # Spring Boot 后端服务
├── HEADEND_CLIENT/EduSpark-Client   # 教师端前端
├── HEADEND_ADMIN/EduSpark-Admin     # 管理端前端
├── docker/postgres                  # 带 pgvector、PGroonga 的 PostgreSQL 镜像
├── docker-compose.infra.yml         # 基础设施：PostgreSQL、Redis、Ollama
├── docker-compose.eduspark.yml      # 应用服务：后端、教师端、管理端
└── .env.docker.example              # Docker 环境变量示例
```

## 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 20.19+ 或 22.12+
- npm
- Docker 与 Docker Compose
- 可选：LibreOffice、FFmpeg、Ollama

本地直接运行后端时，需要本机已启动 PostgreSQL、Redis 和 Ollama。使用 Docker Compose 时，基础设施会由容器提供。

## Docker 部署

1. 复制环境变量文件：

```powershell
Copy-Item .env.docker.example .env
```

2. 按需修改 `.env`：

- `JWT_SECRET`：生产环境必须改为足够长的随机密钥。
- `POSTGRES_PASSWORD`、`REDIS_PASSWORD`：按部署环境设置。
- `RAG_EMBEDDING_API_KEY`：知识库向量化使用的 DashScope API Key。
- `LESSON_PLAN_WRITER_EXTERNAL_API_KEY`：教案生成模型 API Key。
- `INTERACTIVE_WRITER_EXTERNAL_API_KEY`：互动课件生成模型 API Key。
- `ALIYUN_OSS_*`：文件、缩略图等对象存储配置。
- `ALIYUN_ASR_*`：语音识别配置。

3. 启动基础设施：

```powershell
docker compose -f docker-compose.infra.yml up -d
```

4. 可选：拉取 Ollama embedding 模型：

```powershell
docker compose -f docker-compose.infra.yml --profile models up eduspark-ollama-models
```

5. 构建并启动应用：

```powershell
docker compose -f docker-compose.eduspark.yml up -d --build
```

6. 访问服务：

- 教师端：http://localhost:5173
- 管理端：http://localhost:5174
- 后端接口：http://localhost:8080/api

停止服务：

```powershell
docker compose -f docker-compose.eduspark.yml down
docker compose -f docker-compose.infra.yml down
```

## 本地开发

### 后端

```powershell
cd BACKEND/EduSpark
mvn spring-boot:run
```

默认后端地址：

```text
http://localhost:8080/api
```

后端主要配置位于：

```text
BACKEND/EduSpark/src/main/resources/application.yml
```

常用环境变量：

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_DATA_REDIS_HOST
SPRING_DATA_REDIS_PORT
JWT_SECRET
RAG_EMBEDDING_API_KEY
LESSON_PLAN_WRITER_EXTERNAL_API_KEY
INTERACTIVE_WRITER_EXTERNAL_API_KEY
ALIYUN_OSS_ACCESS_KEY_ID
ALIYUN_OSS_ACCESS_KEY_SECRET
```

### 教师端

```powershell
cd HEADEND_CLIENT/EduSpark-Client
npm install
npm run dev -- --port 5173
```

默认访问地址：

```text
http://localhost:5173
```

如需指定后端地址，可创建本地环境变量：

```text
VITE_API_BASE_URL=http://localhost:8080/api
```

### 管理端

```powershell
cd HEADEND_ADMIN/EduSpark-Admin
npm install
npm run dev -- --port 5174
```

默认访问地址：

```text
http://localhost:5174
```

管理端接口需要 `admin` 角色。可先在教师端注册用户，再在数据库中将对应用户角色调整为管理员：

```sql
UPDATE sys_user SET role = 'admin' WHERE phone = '<your_phone>';
```

## 构建与测试

后端测试：

```powershell
cd BACKEND/EduSpark
mvn test
```

后端打包：

```powershell
cd BACKEND/EduSpark
mvn -DskipTests package
```

教师端构建：

```powershell
cd HEADEND_CLIENT/EduSpark-Client
npm run build
```

管理端构建：

```powershell
cd HEADEND_ADMIN/EduSpark-Admin
npm run build
```

## 主要接口

后端统一挂载在 `/api` 下：

| 模块 | 路径 |
| --- | --- |
| 用户 | `/api/v1/user` |
| AI 对话 | `/api/v1/chat` |
| 会话 | `/api/v1/chat/sessions` |
| 上传 | `/api/v1/upload` |
| 知识库 | `/api/v1/knowledge` |
| 教案工作区 | `/api/v1/lesson-plan/documents` |
| PPT 模板与工作区 | `/api/v1/ppt`、`/api/v1/ppt/documents` |
| 互动课件 | `/api/v1/interactive/documents` |
| 语音转写 | `/api/v1/voice` |
| 管理端 PPT 模板 | `/api/admin/ppt` |
| 管理端意图规则 | `/api/admin/intent-rules` |

## 数据库初始化

Docker Compose 会在 PostgreSQL 首次初始化时执行：

```text
BACKEND/EduSpark/sql/schema.sql
```

后端资源目录中也包含业务表结构脚本：

```text
BACKEND/EduSpark/src/main/resources/sql
```

如果数据库卷已经存在，初始化脚本不会重复执行。需要重新初始化时，先确认数据可删除，再清理对应 Docker volume。

## 注意事项

- 不要提交真实 API Key、OSS 密钥、JWT 密钥或数据库密码。
- `node_modules`、`target`、日志文件和运行时生成的课件文件不应提交到 Git。
- PPT 缩略图依赖 LibreOffice；视频处理依赖 FFmpeg。
- AI 生成功能依赖外部模型 API Key 或本地 Ollama 模型，未配置时相关功能可能不可用。
- Docker 前端镜像通过 Nginx 将 `/api/` 反向代理到后端容器。

## 许可证

当前仓库未声明开源许可证。发布到 GitHub 前请根据项目实际情况补充 `LICENSE`。
