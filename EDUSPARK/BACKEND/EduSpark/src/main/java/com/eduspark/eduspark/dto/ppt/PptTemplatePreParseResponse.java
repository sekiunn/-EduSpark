package com.eduspark.eduspark.dto.ppt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 管理端上传 pptx 后立即解析返回的"待提交"摘要。
 * 用户在新增模板对话框里先选 pptx → 后端临时存盘 + 解析标记 →
 * 前端拿到 pendingFileKey 在最终保存时随 createTemplate 一并提交，
 * 让"创建模板"和"上传 pptx"在同一个对话里完成。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PptTemplatePreParseResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用于在 createTemplate / updateTemplate 时引用临时文件的 key（即 _pending/ 下的 UUID）。 */
    private String pendingFileKey;

    /** 用户上传时的原始文件名，前端用于展示。 */
    private String originalFileName;

    /** pptx 总页数。 */
    private int totalSlides;

    /** 解析出的标记总数（跨所有 slide）。 */
    private int markerCount;

    /** 可重复内容页索引（用于额外内容页扩展），-1 表示未识别到。 */
    private int repeatableSlideIndex;

    /** 完整 TemplateStructure 的 JSON，准备直接写入 render_config_json。 */
    private String renderConfigJson;

    /** 每页摘要，便于管理端弹窗回显。 */
    private List<SlideSummary> slides;

    /** 系统建议的模板名（POI 抽首页标题，兜底为文件名）。前端可直接回填到 name 字段。 */
    private String suggestedName;

    /** LLM 根据 pptx 前几页文本生成的一句话描述。失败/不可用时为 null。 */
    private String suggestedDescription;

    /** LLM 自动匹配到的场景 ID，前端可直接回填到 sceneId 下拉。匹配失败为 null。 */
    private Long suggestedSceneId;

    /** LLM 自动匹配到的风格 ID。注意必须属于 suggestedSceneId。匹配失败为 null。 */
    private Long suggestedStyleId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlideSummary implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private int slideIndex;
        private String slideRole;
        private List<String> markerNames;
    }
}
