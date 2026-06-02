package com.eduspark.eduspark.dto.courseware;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured plan for a single PPT slide.
 *
 * <p>{@code slotLayout} 与 {@code assetType} 是渲染器据以选择版式 / 配图的视觉指令。
 * 由 LLM 输出，渲染器据此走不同分支，避免"千篇一律 bullet 文本框"。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PptSlidePlan {

    private Integer slideNo;

    private String title;

    /**
     * 顶层版式：cover / content / summary
     */
    private String layout;

    /**
     * 内容版式：title_only | bullet_list | two_column | image_right | quote | code | comparison | chart
     * 由 LLM 在 content 类型基础上进一步细化，渲染器据此选择不同模板。
     */
    private String slotLayout;

    /**
     * 视觉资产类型：icon | photo | chart | diagram | none
     * 当前实现：icon 走内置图标库；其他类型暂作为视觉提示存入 speaker notes。
     */
    private String assetType;

    /**
     * 资产关键词（最多 3 个），用于在内置图标库 / 配图库中检索。
     */
    private List<String> assetKeywords;

    private List<String> bullets;

    private String speakerNotes;

    private String visualFocus;
}
