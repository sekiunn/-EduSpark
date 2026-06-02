package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.interactive.InteractiveContext;
import com.eduspark.eduspark.service.ILLMService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractiveTemplateGenerationServiceTests {

    @Mock
    private ILLMService llmService;

    private InteractiveTemplateGenerationService service;

    @BeforeEach
    void setUp() {
        service = new InteractiveTemplateGenerationService(
                llmService,
                new ObjectMapper(),
                new InteractiveTemplateRendererService(new ObjectMapper())
        );
        ReflectionTestUtils.setField(service, "templateEnabled", true);
        ReflectionTestUtils.setField(service, "plannerModel", "test-model");
    }

    @Test
    void tryGenerateInitialHtmlShouldUseCollisionTemplateForMomentumTopic() {
        InteractiveContext context = InteractiveContext.builder()
                .topic("动量守恒碰撞演示")
                .studentAction("观察弹性碰撞和非弹性碰撞的差异")
                .build();

        when(llmService.chatWithModel(anyList(), eq("test-model"))).thenReturn("{}");

        String html = service.tryGenerateInitialHtml(context, null);

        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains("collisionCanvas");
        assertThat(context.getTemplateId()).isEqualTo(InteractiveTemplateCatalog.TEMPLATE_PHYSICS_COLLISION_V1);
        assertThat(context.getRenderMode()).isEqualTo(InteractiveTemplateCatalog.RENDER_MODE_TEMPLATE);
    }

    @Test
    void tryGenerateInitialHtmlShouldUsePptDemoTemplateForConceptExplanation() {
        InteractiveContext context = InteractiveContext.builder()
                .topic("细胞结构讲解")
                .studentAction("做一个讲解型互动页面，分模块介绍细胞膜、细胞核和细胞质")
                .build();

        when(llmService.chatWithModel(anyList(), eq("test-model"))).thenReturn("""
                {
                  "title": "细胞结构讲解",
                  "subtitle": "通过分模块讲解帮助学生建立整体认识",
                  "intro": "按照顺序浏览各部分内容。",
                  "slides": [
                    {
                      "tag": "引入",
                      "title": "先认识细胞整体",
                      "summary": "先知道细胞有哪些主要部分。",
                      "bullets": ["细胞是生命活动的基本单位", "不同结构承担不同功能"],
                      "highlight": "先建立整体框架"
                    },
                    {
                      "tag": "展开",
                      "title": "聚焦细胞核",
                      "summary": "细胞核在细胞中起核心作用。",
                      "bullets": ["储存遗传信息", "调控细胞活动"],
                      "highlight": "围绕核心结构展开"
                    }
                  ]
                }
                """);

        String html = service.tryGenerateInitialHtml(context, null);

        assertThat(context.getTemplateId()).isEqualTo(InteractiveTemplateCatalog.TEMPLATE_PPT_DEMO_V1);
        assertThat(context.getInteractiveType()).isEqualTo(InteractiveTemplateCatalog.TYPE_ANIMATION_DEMO);
        assertThat(html).contains("slideTabs");
        assertThat(html).contains("slide-stage-card");
    }

    @Test
    void tryGenerateInitialHtmlShouldUseFlowDemoTemplateForProcessExplanation() {
        InteractiveContext context = InteractiveContext.builder()
                .topic("水循环过程")
                .studentAction("做一个流程演示页面，按阶段展示蒸发、凝结、降水和汇集")
                .build();

        when(llmService.chatWithModel(anyList(), eq("test-model"))).thenReturn("""
                {
                  "title": "水循环过程",
                  "subtitle": "逐步查看水在自然界中的循环路径",
                  "overview": "点击每个阶段，理解水循环如何首尾相连。",
                  "steps": [
                    { "title": "蒸发", "description": "液态水受热后变成水蒸气。", "keyword": "蒸发" },
                    { "title": "凝结", "description": "水蒸气冷却后形成小水滴。", "keyword": "凝结" },
                    { "title": "降水", "description": "云中的水滴变大后降落。", "keyword": "降水" },
                    { "title": "汇集", "description": "地表水重新汇入江河湖海。", "keyword": "汇集" }
                  ]
                }
                """);

        String html = service.tryGenerateInitialHtml(context, null);

        assertThat(context.getTemplateId()).isEqualTo(InteractiveTemplateCatalog.TEMPLATE_FLOW_DEMO_V1);
        assertThat(context.getInteractiveType()).isEqualTo(InteractiveTemplateCatalog.TYPE_ANIMATION_DEMO);
        assertThat(html).contains("flowRail");
        assertThat(html).contains("flow-detail-card");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryGenerateInitialHtmlShouldSupportVirtualXAxisForLinearFunctionExplorer() {
        InteractiveContext context = InteractiveContext.builder()
                .topic("一次函数 y=ax+b")
                .studentAction("拖动 a 和 b 的滑杆，观察图像变化")
                .build();

        when(llmService.chatWithModel(anyList(), eq("test-model"))).thenReturn("""
                {
                  "title": "一次函数 y=ax+b",
                  "formulaText": "y = ax + b",
                  "parameters": [
                    { "key": "a", "label": "参数 a", "min": -5, "max": 5, "step": 0.5, "defaultValue": 1 },
                    { "key": "b", "label": "参数 b", "min": -5, "max": 5, "step": 0.5, "defaultValue": 0 }
                  ],
                  "metrics": [
                    { "label": "结果", "expression": "a * x", "unit": "", "precision": 2 }
                  ],
                  "chart": {
                    "xKey": "x",
                    "yExpression": "a * x + b",
                    "yLabel": "结果值"
                  }
                }
                """);

        String html = service.tryGenerateInitialHtml(context, null);

        Map<String, Object> spec = context.getTemplateSpec();
        Map<String, Object> chart = (Map<String, Object>) spec.get("chart");
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) spec.get("metrics");

        assertThat(context.getTemplateId()).isEqualTo(InteractiveTemplateCatalog.TEMPLATE_PARAMETER_EXPLORER_V1);
        assertThat(chart.get("xKey")).isEqualTo("x");
        assertThat(chart.get("yExpression")).isEqualTo("a * x + b");
        assertThat(chart.get("xMin")).isEqualTo(-10.0);
        assertThat(chart.get("xMax")).isEqualTo(10.0);
        assertThat(metrics).extracting(metric -> metric.get("expression")).contains("a", "b");
        assertThat(html).contains("chartAxisKey");
        assertThat(html).contains("[chartAxisKey]: xValue");
    }
}
