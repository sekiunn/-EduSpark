package com.eduspark.eduspark.controller.client;

import com.eduspark.eduspark.service.ICoursewareService;
import com.eduspark.eduspark.service.IFileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursewareControllerTests {

    @Mock
    private ICoursewareService coursewareService;

    @Mock
    private IFileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    @Test
    void downloadFileShouldReadExistingLocalFileWithoutCallingStorageService() throws Exception {
        CoursewareController controller = new CoursewareController(coursewareService, fileStorageService);
        ReflectionTestUtils.setField(controller, "localStoragePath", tempDir.toString());

        Path file = tempDir.resolve("water-cycle_interactive.html");
        Files.writeString(file, "<html>local</html>", StandardCharsets.UTF_8);

        ResponseEntity<InputStreamResource> response = controller.downloadFile(file.toString(), null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("filename*=");
        assertThat(new String(response.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("<html>local</html>");
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void downloadFileShouldUseStorageServiceForRemoteKeyAndKeepUtf8FileNameHeader() throws Exception {
        CoursewareController controller = new CoursewareController(coursewareService, fileStorageService);
        ReflectionTestUtils.setField(controller, "localStoragePath", tempDir.toString());

        when(fileStorageService.getInputStream("knowledge/1/水循环_interactive.html"))
                .thenReturn(new ByteArrayInputStream("remote".getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<InputStreamResource> response =
                controller.downloadFile("knowledge/1/水循环_interactive.html", null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("filename*=");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("%E6%B0%B4");
        assertThat(new String(response.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("remote");
        verify(fileStorageService).getInputStream("knowledge/1/水循环_interactive.html");
    }

    @Test
    void downloadFileShouldRejectAbsolutePathOutsideStorageRoot() throws Exception {
        CoursewareController controller = new CoursewareController(coursewareService, fileStorageService);
        ReflectionTestUtils.setField(controller, "localStoragePath", tempDir.toString());

        Path outsideFile = Files.createTempFile("outside", ".html");
        Files.writeString(outsideFile, "<html>outside</html>", StandardCharsets.UTF_8);

        ResponseEntity<InputStreamResource> response = controller.downloadFile(outsideFile.toString(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(fileStorageService);
    }
}
