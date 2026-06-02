package com.eduspark.eduspark.dto.pptworkspace;

import com.eduspark.eduspark.dto.courseware.PptSlidePlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Slide-level progress payload broadcast through the PPT workspace SSE stream.
 *
 * <p>事件流：{@code slide.skeleton} → {@code slide.background} → 多个
 * {@code slide.content_delta} → {@code slide.completed}。每个阶段携带的字段不一定都填，
 * 前端按 stage 决定渲染分支。</p>
 *
 * <p>同一条事件结构同时用于"实时推送"和"断线重连快照回放"：当前进度数组直接挂在
 * {@link PptDocumentResponse#getSlidesProgress()} 上。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PptSlideProgressEvent {

    public static final class Stage {
        public static final String SKELETON = "skeleton";
        public static final String BACKGROUND_READY = "background_ready";
        public static final String CONTENT_FILLING = "content_filling";
        public static final String COMPLETED = "completed";

        private Stage() {
        }
    }

    private Integer slideNo;

    private Integer total;

    private String stage;

    private String layout;

    private String slotLayout;

    private String backgroundImageUrl;

    private String title;

    private List<String> bullets;

    private String visualFocus;

    private String speakerNotes;

    private PptSlidePlan slidePlan;
}
