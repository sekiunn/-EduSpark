package com.eduspark.eduspark.dto.courseware;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 上传 pptx 模板里一个"可改写"的文本块——按 XSLFTextShape 粒度切分。
 * 跟 marker（{{xxx}}）路线不同：这里不要求管理员手工标记，
 * 而是把每个非空文本框作为整体单元喂给 LLM 让它按主题改写整个块。
 * <p>多段落（如 bullet list）的 shape 会把所有 paragraph 用 {@code \n} 拼成 original，
 * LLM 返回时必须保持相同的换行数（否则替换时按规则 fallback 原文）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextBlockInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 形如 "0-shape5"——slideIndex + shapeId，用于在 LLM JSON 里关联回原 shape。 */
    private String blockId;

    /** 第几页（0-based），用于在 prompt 给 LLM 上下文。 */
    private int slideIndex;

    /** 原始文本（多段落用 \n 拼接）。 */
    private String original;

    /** 角色提示：cover_title / cover_subtitle / content / summary / table_cell / notes / other。 */
    private String role;

    /** 是否来自备注页。MVP 阶段 notes 不送 LLM，保持原样。 */
    private boolean fromNotes;

    /** 段落数，用于替换时校验"换行数对齐"。 */
    private int paragraphCount;
}
