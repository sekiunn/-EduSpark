package com.eduspark.eduspark.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ========== 通用错误（1000-1999）==========
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    SERVER_ERROR(500, "服务器内部错误"),

    // ========== 文件相关错误（1001-1099）==========
    FILE_UPLOAD_ERROR(1001, "文件上传失败"),
    FILE_TYPE_NOT_SUPPORTED(1002, "不支持的文件类型"),
    FILE_NOT_FOUND(1003, "文件不存在"),
    FILE_EMPTY(1004, "文件内容为空"),
    FILE_TOO_LARGE(1005, "文件大小超出限制"),
    FILE_DELETE_ERROR(1006, "文件删除失败"),
    FILE_DOWNLOAD_ERROR(1007, "文件下载失败"),
    FILE_READ_ERROR(1008, "文件读取失败"),

    // ========== 文件解析错误（1100-1199）==========
    FILE_PARSING_ERROR(1101, "文件解析失败"),
    PDF_PARSING_ERROR(1102, "PDF解析失败"),
    WORD_PARSING_ERROR(1103, "Word文档解析失败"),
    TEXT_EXTRACTION_ERROR(1104, "文本提取失败"),

    // ========== 知识库错误（2000-2099）==========
    KNOWLEDGE_FILE_NOT_FOUND(2001, "知识库文件不存在"),
    KNOWLEDGE_FILE_STATUS_ERROR(2002, "文件状态异常"),
    KNOWLEDGE_CHUNK_ERROR(2003, "文本分块失败"),
    KNOWLEDGE_EMBEDDING_ERROR(2004, "文本向量化失败"),
    KNOWLEDGE_SEARCH_ERROR(2005, "知识检索失败"),
    KNOWLEDGE_DELETE_ERROR(2006, "知识删除失败"),
    KNOWLEDGE_WORKSPACE_NOT_FOUND(2007, "课程空间不存在"),
    KNOWLEDGE_WORKSPACE_DUPLICATE(2008, "课程空间名称已存在"),

    // ========== Ollama 服务错误（3000-3099）==========
    OLLAMA_SERVICE_UNAVAILABLE(3001, "Ollama服务不可用"),
    OLLAMA_EMBEDDING_ERROR(3002, "Ollama向量化失败"),
    OLLAMA_MODEL_NOT_FOUND(3003, "Ollama模型未找到"),

    // ========== 数据库错误（4000-4099）==========
    DB_ERROR(4001, "数据库操作失败"),
    DB_VECTOR_ERROR(4002, "向量数据库操作失败");

    private final Integer code;
    private final String message;

    /**
     * 根据错误码获取枚举
     */
    public static ErrorCode fromCode(Integer code) {
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return SERVER_ERROR;
    }
}
