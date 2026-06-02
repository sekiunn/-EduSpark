package com.eduspark.eduspark.service.impl;

import com.eduspark.eduspark.dto.interactive.InteractiveDocumentResponse;
import com.eduspark.eduspark.dto.interactive.InteractiveDownloadPayload;
import com.eduspark.eduspark.exception.BusinessException;
import com.eduspark.eduspark.mapper.InteractiveDocumentMapper;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import com.eduspark.eduspark.pojo.entity.InteractiveDocument;
import com.eduspark.eduspark.service.IChatSessionService;
import com.eduspark.eduspark.service.IInteractiveHtmlWriterService;
import com.eduspark.eduspark.service.IInteractiveWorkspaceStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractiveWorkspaceServiceImplTests {

    @Mock
    private InteractiveDocumentMapper interactiveDocumentMapper;

    @Mock
    private IInteractiveHtmlWriterService interactiveHtmlWriterService;

    @Mock
    private IChatSessionService chatSessionService;

    @Mock
    private IInteractiveWorkspaceStreamService workspaceStreamService;

    @TempDir
    Path tempDir;

    @Test
    void updateDocumentContentShouldClearExportMetadataOnManualSave() {
        InteractiveWorkspaceServiceImpl service = new InteractiveWorkspaceServiceImpl(
                interactiveDocumentMapper,
                interactiveHtmlWriterService,
                chatSessionService,
                workspaceStreamService,
                new ObjectMapper()
        );

        InteractiveDocument document = InteractiveDocument.builder()
                .id(5L)
                .userId(8L)
                .status(InteractiveDocument.Status.COMPLETED)
                .title("Water Cycle")
                .htmlContent("<html>old</html>")
                .downloadUrl("/api/v1/courseware/download?path=old.html")
                .exportFilePath("old.html")
                .build();
        when(interactiveDocumentMapper.selectOwnedById(5L, 8L)).thenReturn(document);

        InteractiveDocumentResponse response = service.updateDocumentContent(5L, 8L, "<html>new</html>");

        assertThat(document.getDownloadUrl()).isNull();
        assertThat(document.getExportFilePath()).isNull();
        assertThat(response.getDownloadUrl()).isNull();
        assertThat(response.getHtmlContent()).isEqualTo("<html>new</html>");
        verify(interactiveDocumentMapper).updateById(document);
        verify(workspaceStreamService).publishSnapshot(eq(5L), any(InteractiveDocumentResponse.class));
    }

    @Test
    void updateDocumentContentShouldRejectSaveWhenDocumentNotCompleted() {
        InteractiveWorkspaceServiceImpl service = new InteractiveWorkspaceServiceImpl(
                interactiveDocumentMapper,
                interactiveHtmlWriterService,
                chatSessionService,
                workspaceStreamService,
                new ObjectMapper()
        );

        InteractiveDocument document = InteractiveDocument.builder()
                .id(8L)
                .userId(8L)
                .status(InteractiveDocument.Status.IMPLEMENTING)
                .title("Water Cycle")
                .htmlContent("<html>old</html>")
                .build();
        when(interactiveDocumentMapper.selectOwnedById(8L, 8L)).thenReturn(document);

        assertThatThrownBy(() -> service.updateDocumentContent(8L, 8L, "<html>new</html>"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未生成完成");

        verify(interactiveDocumentMapper, never()).updateById(any(InteractiveDocument.class));
        verify(workspaceStreamService, never()).publishSnapshot(any(), any());
    }

    @Test
    void updateDocumentContentShouldStripNewExecutableContentAndDemoteTemplateContext() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        InteractiveWorkspaceServiceImpl service = new InteractiveWorkspaceServiceImpl(
                interactiveDocumentMapper,
                interactiveHtmlWriterService,
                chatSessionService,
                workspaceStreamService,
                objectMapper
        );

        String templateContextJson = objectMapper.writeValueAsString(Map.of(
                "topic", "Water Cycle",
                "renderMode", "template",
                "templateId", "drag_match_v1",
                "templateVersion", "1.0",
                "templateSpec", Map.of("title", "Water Cycle")
        ));

        InteractiveDocument document = InteractiveDocument.builder()
                .id(9L)
                .sessionId(38L)
                .userId(8L)
                .status(InteractiveDocument.Status.COMPLETED)
                .title("Water Cycle")
                .sourceContextJson(templateContextJson)
                .enrichedContextJson(templateContextJson)
                .htmlContent("<!DOCTYPE html><html><body><button>Click</button></body></html>")
                .build();
        when(interactiveDocumentMapper.selectOwnedById(9L, 8L)).thenReturn(document);

        InteractiveDocumentResponse response = service.updateDocumentContent(
                9L,
                8L,
                """
                <!DOCTYPE html>
                <html>
                  <body>
                    <button onclick="alert('x')">Click</button>
                    <script>alert('x')</script>
                  </body>
                </html>
                """
        );

        assertThat(response.getHtmlContent()).contains("<button>Click</button>");
        assertThat(response.getHtmlContent()).doesNotContain("<script");
        assertThat(response.getHtmlContent()).doesNotContain("onclick=");

        @SuppressWarnings("unchecked")
        Map<String, Object> enrichedContext = objectMapper.readValue(document.getEnrichedContextJson(), Map.class);
        assertThat(enrichedContext).containsEntry("renderMode", "custom_html");
        assertThat(enrichedContext).doesNotContainKeys("templateId", "templateVersion");
        verify(chatSessionService).updateTeachingMode(38L, null, ChatSession.Stage.COMPLETED, document.getEnrichedContextJson());
    }

    @Test
    void exportDocumentShouldReturnDocumentScopedDownloadUrl() throws Exception {
        InteractiveWorkspaceServiceImpl service = new InteractiveWorkspaceServiceImpl(
                interactiveDocumentMapper,
                interactiveHtmlWriterService,
                chatSessionService,
                workspaceStreamService,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "localStoragePath", tempDir.toString());

        InteractiveDocument document = InteractiveDocument.builder()
                .id(6L)
                .userId(8L)
                .status(InteractiveDocument.Status.COMPLETED)
                .title("Momentum Demo")
                .htmlContent("<html>export</html>")
                .build();
        when(interactiveDocumentMapper.selectOwnedById(6L, 8L)).thenReturn(document);

        InteractiveDocumentResponse response = service.exportDocument(6L, 8L);

        assertThat(response.getDownloadUrl()).isEqualTo("/api/v1/interactive/documents/6/download");
        assertThat(document.getExportFilePath()).isNotBlank();
        assertThat(Files.exists(Paths.get(document.getExportFilePath()))).isTrue();
        verify(interactiveDocumentMapper).updateById(document);
        verify(workspaceStreamService).publishSnapshot(eq(6L), any(InteractiveDocumentResponse.class));
    }

    @Test
    void downloadDocumentShouldReadAuthorizedExportedFile() throws Exception {
        InteractiveWorkspaceServiceImpl service = new InteractiveWorkspaceServiceImpl(
                interactiveDocumentMapper,
                interactiveHtmlWriterService,
                chatSessionService,
                workspaceStreamService,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "localStoragePath", tempDir.toString());

        Path exportFile = tempDir.resolve("momentum_demo_interactive.html");
        Files.writeString(exportFile, "<html>download</html>", StandardCharsets.UTF_8);

        InteractiveDocument document = InteractiveDocument.builder()
                .id(7L)
                .userId(8L)
                .status(InteractiveDocument.Status.COMPLETED)
                .title("Momentum Demo")
                .exportFilePath(exportFile.toString())
                .build();
        when(interactiveDocumentMapper.selectOwnedById(7L, 8L)).thenReturn(document);

        InteractiveDownloadPayload payload = service.downloadDocument(7L, 8L);

        assertThat(payload.getFileName()).isEqualTo("momentum_demo_interactive.html");
        assertThat(payload.getContentType()).isEqualTo("text/html;charset=UTF-8");
        assertThat(new String(payload.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("<html>download</html>");
    }
}
