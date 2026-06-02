package com.eduspark.eduspark.controller.client;

import com.eduspark.eduspark.dto.common.Result;
import com.eduspark.eduspark.dto.interactive.InteractiveContentUpdateRequest;
import com.eduspark.eduspark.dto.interactive.InteractiveDownloadPayload;
import com.eduspark.eduspark.dto.interactive.InteractiveDocumentResponse;
import com.eduspark.eduspark.service.IInteractiveWorkspaceService;
import com.eduspark.eduspark.service.IInteractiveWorkspaceStreamService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractiveControllerTests {

    @Mock
    private IInteractiveWorkspaceService interactiveWorkspaceService;

    @Mock
    private IInteractiveWorkspaceStreamService interactiveWorkspaceStreamService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Test
    void updateContentShouldDelegateWithCurrentUser() {
        InteractiveController controller = new InteractiveController(
                interactiveWorkspaceService,
                interactiveWorkspaceStreamService
        );
        when(httpServletRequest.getAttribute("userId")).thenReturn(8L);
        when(interactiveWorkspaceService.updateDocumentContent(6L, 8L, "<html>saved</html>"))
                .thenReturn(InteractiveDocumentResponse.builder()
                        .documentId(6L)
                        .htmlContent("<html>saved</html>")
                        .build());

        Result<InteractiveDocumentResponse> response = controller.updateContent(
                6L,
                InteractiveContentUpdateRequest.builder().content("<html>saved</html>").build(),
                httpServletRequest
        );

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getDocumentId()).isEqualTo(6L);
        assertThat(response.getData().getHtmlContent()).isEqualTo("<html>saved</html>");
        verify(interactiveWorkspaceService).updateDocumentContent(6L, 8L, "<html>saved</html>");
    }

    @Test
    void exportDocumentShouldDelegateWithCurrentUser() {
        InteractiveController controller = new InteractiveController(
                interactiveWorkspaceService,
                interactiveWorkspaceStreamService
        );
        when(httpServletRequest.getAttribute("userId")).thenReturn(8L);
        when(interactiveWorkspaceService.exportDocument(6L, 8L))
                .thenReturn(InteractiveDocumentResponse.builder()
                        .documentId(6L)
                        .downloadUrl("/api/v1/interactive/documents/6/download")
                        .build());

        Result<InteractiveDocumentResponse> response = controller.exportDocument(6L, httpServletRequest);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getDownloadUrl()).isEqualTo("/api/v1/interactive/documents/6/download");
        verify(interactiveWorkspaceService).exportDocument(6L, 8L);
    }

    @Test
    void downloadDocumentShouldReturnAuthorizedFileStream() throws Exception {
        InteractiveController controller = new InteractiveController(
                interactiveWorkspaceService,
                interactiveWorkspaceStreamService
        );
        when(httpServletRequest.getAttribute("userId")).thenReturn(8L);
        when(interactiveWorkspaceService.downloadDocument(6L, 8L))
                .thenReturn(InteractiveDownloadPayload.builder()
                        .inputStream(new ByteArrayInputStream("<html>ok</html>".getBytes(StandardCharsets.UTF_8)))
                        .fileName("momentum_demo_interactive.html")
                        .contentType("text/html;charset=UTF-8")
                        .build());

        ResponseEntity<InputStreamResource> response = controller.downloadDocument(6L, httpServletRequest);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("filename*=");
        assertThat(new String(response.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("<html>ok</html>");
        verify(interactiveWorkspaceService).downloadDocument(6L, 8L);
    }
}
