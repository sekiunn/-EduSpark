package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.interactive.InteractiveContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractiveHtmlWriterServiceImplTests {

    @Mock
    private ExternalInteractiveHtmlWriterDelegate externalWriter;

    @Mock
    private LocalInteractiveHtmlWriterDelegate localWriter;

    @Mock
    private InteractiveTemplateGenerationService templateGenerationService;

    private InteractiveHtmlWriterServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InteractiveHtmlWriterServiceImpl(
                new InteractivePromptBuilder(),
                templateGenerationService,
                externalWriter,
                localWriter
        );
        ReflectionTestUtils.setField(service, "writerMode", "local");
        ReflectionTestUtils.setField(service, "fallbackToLocal", false);
        when(templateGenerationService.tryGenerateInitialHtml(any(), any())).thenReturn(null);
    }

    @Test
    void streamInitialHtmlShouldExtractLastCompleteDocumentFromNestedWrapper() {
        InteractiveContext context = InteractiveContext.builder()
                .topic("动量守恒")
                .build();

        String nested = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <title>外层包装</title>
                </head>
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <title>真正页面</title>
                </head>
                <body>
                  <div id="app">可以运行</div>
                </body>
                </html>
                """;

        when(localWriter.streamInitialHtml(eq(context), any())).thenReturn(nested);

        String result = service.streamInitialHtml(context, null);

        assertThat(result).contains("真正页面");
        assertThat(result).contains("<div id=\"app\">可以运行</div>");
        assertThat(result).doesNotContain("外层包装");
        assertThat(countOccurrences(result, "<!DOCTYPE html>")).isEqualTo(1);
        verify(localWriter, never()).streamPrompt(anyString(), anyString(), any());
    }

    @Test
    void streamInitialHtmlShouldRepairMalformedDocumentBeforeReturning() {
        InteractiveContext context = InteractiveContext.builder()
                .topic("动量守恒")
                .interactionType("动画演示")
                .build();

        String broken = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <title>外层</title>
                </head>
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <title>动量守恒碰撞演示</title>
                </head>
                <body>
                  <canvas id="sim"></canvas>
                  <script>
                    const running = true;
                </html>
                """;

        String repaired = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>动量守恒碰撞演示</title>
                </head>
                <body>
                  <canvas id="sim"></canvas>
                  <script>
                    const running = true;
                  </script>
                </body>
                </html>
                """;

        when(localWriter.streamInitialHtml(eq(context), any())).thenReturn(broken);
        when(localWriter.streamPrompt(anyString(), contains("待修复 HTML"), any())).thenReturn(repaired);

        String result = service.streamInitialHtml(context, null);

        assertThat(result).contains("<canvas id=\"sim\"></canvas>");
        assertThat(result).contains("</script>");
        assertThat(result).contains("</body>");
        assertThat(countOccurrences(result, "<!DOCTYPE html>")).isEqualTo(1);
        verify(localWriter).streamPrompt(anyString(), contains("待修复 HTML"), any());
    }

    @Test
    void streamInitialHtmlShouldPreferTemplateRendererWhenAvailable() {
        InteractiveContext context = InteractiveContext.builder()
                .topic("动量守恒")
                .build();

        String html = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head><meta charset="UTF-8" /><title>模板页</title></head>
                <body><canvas id="collisionCanvas"></canvas></body>
                </html>
                """;

        when(templateGenerationService.tryGenerateInitialHtml(eq(context), any())).thenReturn(html);

        String result = service.streamInitialHtml(context, null);

        assertThat(result).contains("collisionCanvas");
        verify(localWriter, never()).streamInitialHtml(any(), any());
        verify(externalWriter, never()).streamInitialHtml(any(), any());
    }

    private int countOccurrences(String content, String token) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
