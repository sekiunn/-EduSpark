package com.eduspark.eduspark.dto.pptworkspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Incremental planning payload sent to the PPT workspace SSE stream.
 *
 * <p>当 {@code slideNo} 为 {@code null} 时，表示规划阶段的整体 LLM token 流（与原有
 * {@code content_delta} 事件兼容）；当 {@code slideNo} 非空时，配合
 * {@code slide.content_delta} 事件用于"逐页填字"：{@code field} 指明落到哪个槽位
 * （{@code title} / {@code bullet} / {@code visualFocus} / {@code speakerNotes}），
 * {@code append=true} 表示追加（如逐条 bullet），{@code append=false} 表示整体替换。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PptStreamDeltaResponse {

    private Long documentId;

    private String delta;

    private Integer slideNo;

    private String field;

    private String value;

    private Boolean append;
}
