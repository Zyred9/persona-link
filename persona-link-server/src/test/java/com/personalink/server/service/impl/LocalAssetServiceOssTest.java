package com.personalink.server.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObjectSummary;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.personalink.server.config.OssConfiguration;
import com.personalink.server.controller.admin.AdminAssetController;
import com.personalink.server.entity.TestVersionEntity;
import com.personalink.server.entity.ImageGenerationTaskEntity;
import com.personalink.server.mapper.ImageGenerationTaskMapper;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.exception.GlobalExceptionHandler;
import com.personalink.server.mapper.TestVersionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.Base64;
import java.util.List;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LocalAssetServiceOssTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aL1sAAAAASUVORK5CYII=");
    @TempDir Path directory;
    private OSS oss;
    private TestVersionMapper mapper;
    private LocalAssetService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "oss-test"),
                TestVersionEntity.class);
        this.oss = mock(OSS.class);
        this.mapper = mock(TestVersionMapper.class);
        when(this.mapper.selectList(any())).thenReturn(List.of());
        this.service = new LocalAssetService(this.directory.toString(), this.mapper,
                new OssConfiguration("https://oss-cn-hangzhou.aliyuncs.com", "id", "secret", "bucket",
                        "https://cdn.example.com/"), this.oss);
    }

    @Test
    void multipartUploadShouldWriteImageToOssAndKeepResponseContract() throws Exception {
        String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "jpg", output);
        byte[] jpeg = output.toByteArray();
        when(this.oss.putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenAnswer(invocation -> {
            assertEquals("bucket", invocation.getArgument(0));
            String key = invocation.getArgument(1);
            ObjectMetadata metadata = invocation.getArgument(3);
            boolean png = "image/png".equals(metadata.getContentType());
            assertTrue(key.matches("common/" + date + "/[0-9a-f]{32}\\." + (png ? "png" : "jpg")));
            assertArrayEquals(png ? PNG : jpeg, invocation.<InputStream>getArgument(2).readAllBytes());
            assertEquals(png ? PNG.length : jpeg.length, metadata.getContentLength());
            assertEquals("persona-link", metadata.getUserMetadata().get("app"));
            return null;
        });
        MockMvcBuilders.standaloneSetup(new AdminAssetController(this.service))
                .setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(multipart("/api/admin/assets/images").file(this.image()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith(
                        "https://cdn.example.com/common/" + date + "/")));
        assertTrue(this.service.saveImage(new MockMultipartFile("file", "photo.jpeg", "image/jpeg", jpeg))
                .url().matches("https://cdn.example.com/common/" + date + "/[0-9a-f]{32}\\.jpg"));
        verify(this.oss, times(2)).putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class));
        try (var files = Files.list(this.directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void invalidImagesShouldNeverReachOss() {
        for (MockMultipartFile file : List.of(
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0]),
                new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]),
                new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes()),
                new MockMultipartFile("file", "fake.jpg", "image/jpeg", PNG),
                new MockMultipartFile("file", "file.svg", "image/svg+xml", PNG))) {
            assertThrows(BusinessException.class, () -> this.service.saveImage(file));
        }
        verifyNoInteractions(this.oss);
    }

    @Test
    void missingConfigurationAndSdkFailureShouldNotReportSuccessfulUpload() throws Exception {
        LocalAssetService unconfigured = new LocalAssetService(this.directory.toString(), this.mapper,
                new OssConfiguration("", "", "", "", ""), this.oss);
        assertThrows(BusinessException.class, () -> unconfigured.saveImage(this.image()));
        verifyNoInteractions(this.oss);
        when(this.oss.putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenThrow(new OSSException("storage unavailable"));
        assertThrows(RuntimeException.class, () -> this.service.saveImage(this.image()));
        try (var files = Files.list(this.directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void multipartSizeExceptionShouldReturn413() throws Exception {
        LocalAssetService failing = mock(LocalAssetService.class);
        when(failing.saveImage(any())).thenThrow(new MaxUploadSizeExceededException(5 * 1024 * 1024));
        MockMvcBuilders.standaloneSetup(new AdminAssetController(failing))
                .setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(multipart("/api/admin/assets/images").file(this.image()))
                .andExpect(status().isPayloadTooLarge());
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "photo.png", "image/png", PNG);
    }

    @Test
    void pendingGeneratedImagesCannotBeDeletedBeforeAdoption() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "image-task-assets"),
                ImageGenerationTaskEntity.class);
        ImageGenerationTaskMapper tasks = mock(ImageGenerationTaskMapper.class);
        ImageGenerationTaskEntity task = new ImageGenerationTaskEntity();
        String name = "oss-" + "f".repeat(32) + ".png";
        task.setCoverUrl("https://cdn.example.com/persona-link/images/" + name);
        when(tasks.selectList(any())).thenReturn(List.of(task));
        this.service.setImageTaskMapper(tasks);
        assertThrows(BusinessException.class, () -> this.service.deleteImage(name));
        verify(this.oss, never()).deleteObject(anyString(), anyString());
        assertTrue(this.service.saveGeneratedImage(PNG).url().startsWith("https://cdn.example.com/common/"));
    }

    @Test
    void listShouldReadAllOssPagesMergeLocalAndProtectCoverAndDetailReferences() throws Exception {
        String cover = "oss-" + "a".repeat(32) + ".png";
        String detail = "oss-" + "b".repeat(32) + ".png";
        Files.write(this.directory.resolve("history.png"), PNG);
        TestVersionEntity version = new TestVersionEntity();
        version.setCoverUrl("https://cdn.example.com/persona-link/images/" + cover);
        version.setDetailImageUrl("https://cdn.example.com/persona-link/images/" + detail);
        when(this.mapper.selectList(any())).thenReturn(List.of(version));
        ListObjectsV2Result first = new ListObjectsV2Result();
        first.setTruncated(true);
        first.setNextContinuationToken("page-2");
        first.getObjectSummaries().add(this.object("persona-link/images/" + cover));
        first.getObjectSummaries().add(this.object("another-app/" + cover));
        ListObjectsV2Result second = new ListObjectsV2Result();
        second.getObjectSummaries().add(this.object("persona-link/images/" + detail));
        String commonCover = "20260903/" + "d".repeat(32) + ".png";
        String commonDetail = "20260906/" + "e".repeat(32) + ".jpg";
        String unowned = "20260903/" + "f".repeat(32) + ".png";
        String missing = "20260903/" + "0".repeat(32) + ".png";
        TestVersionEntity commonVersion = new TestVersionEntity();
        commonVersion.setCoverUrl("https://cdn.example.com/common/" + commonCover);
        commonVersion.setDetailImageUrl("https://cdn.example.com/common/" + commonDetail);
        when(this.mapper.selectList(any())).thenReturn(List.of(version, commonVersion));
        ListObjectsV2Result commonFirst = new ListObjectsV2Result();
        commonFirst.setTruncated(true);
        commonFirst.setNextContinuationToken("page-2");
        commonFirst.getObjectSummaries().add(this.object("common/" + commonCover));
        commonFirst.getObjectSummaries().add(this.object("common/" + unowned));
        commonFirst.getObjectSummaries().add(this.object("common/" + missing));
        commonFirst.getObjectSummaries().add(this.object("common/invalid.png"));
        commonFirst.getObjectSummaries().add(this.object("another-app/" + commonCover));
        ListObjectsV2Result commonSecond = new ListObjectsV2Result();
        commonSecond.getObjectSummaries().add(this.object("common/" + commonDetail));
        ObjectMetadata owned = new ObjectMetadata();
        owned.addUserMetadata("app", "persona-link");
        when(this.oss.getObjectMetadata("bucket", "common/" + commonCover)).thenReturn(owned);
        when(this.oss.getObjectMetadata("bucket", "common/" + commonDetail)).thenReturn(owned);
        when(this.oss.getObjectMetadata("bucket", "common/" + unowned)).thenReturn(new ObjectMetadata());
        OSSException missingObject = mock(OSSException.class);
        when(missingObject.getErrorCode()).thenReturn("NoSuchKey");
        when(this.oss.getObjectMetadata("bucket", "common/" + missing)).thenThrow(missingObject);
        when(this.oss.listObjectsV2(any(ListObjectsV2Request.class))).thenAnswer(invocation -> {
            ListObjectsV2Request request = invocation.getArgument(0);
            assertEquals("bucket", request.getBucketName());
            boolean common = "common/".equals(request.getPrefix());
            if (!common) assertEquals("persona-link/images/", request.getPrefix());
            if (request.getContinuationToken() == null) return common ? commonFirst : first;
            assertEquals("page-2", request.getContinuationToken());
            return common ? commonSecond : second;
        });
        var images = this.service.listImages(null);
        assertEquals(5, images.size());
        assertEquals(4, images.stream().filter(item -> item.referenceCount() == 1).count());
        assertTrue(images.stream().anyMatch(item -> item.fileName().equals(commonCover.replace('/', '-'))
                && item.url().equals("https://cdn.example.com/common/" + commonCover)));
        assertEquals(1, this.service.listImages("HISTORY").size());
        assertThrows(BusinessException.class, () -> this.service.deleteImage(cover));
        assertThrows(BusinessException.class, () -> this.service.deleteImage(detail));
        assertThrows(BusinessException.class, () -> this.service.deleteImage(commonCover.replace('/', '-')));
        assertThrows(BusinessException.class, () -> this.service.deleteImage(commonDetail.replace('/', '-')));
        verify(this.oss, never()).getObjectMetadata("bucket", "common/invalid.png");
        verify(this.oss, never()).getObjectMetadata("bucket", "another-app/" + commonCover);
        verify(this.oss, never()).deleteObject(anyString(), anyString());
    }

    @Test
    void deleteShouldStayInsideOwnedPrefix() throws Exception {
        String name = "oss-" + "c".repeat(32) + ".png";
        when(this.oss.doesObjectExist("bucket", "persona-link/images/" + name)).thenReturn(true);
        for (String invalid : List.of("../" + name, "another-app/" + name, "C:\\" + name)) {
            assertThrows(BusinessException.class, () -> this.service.deleteImage(invalid));
        }
        this.service.deleteImage(name);
        verify(this.oss).deleteObject("bucket", "persona-link/images/" + name);
        verify(this.oss).doesObjectExist("bucket", "persona-link/images/" + name);
        String commonName = "20260906-" + "a".repeat(32) + ".png";
        String commonKey = "common/" + commonName.replaceFirst("-", "/");
        ObjectMetadata owned = new ObjectMetadata();
        owned.addUserMetadata("app", "persona-link");
        when(this.oss.getObjectMetadata("bucket", commonKey)).thenReturn(owned);
        MockMvcBuilders.standaloneSetup(new AdminAssetController(this.service))
                .setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/admin/assets/images/" + commonName)).andExpect(status().isOk());
        verify(this.oss).deleteObject("bucket", commonKey);
        ObjectMetadata otherApp = new ObjectMetadata();
        otherApp.addUserMetadata("app", "another-app");
        when(this.oss.getObjectMetadata("bucket", commonKey)).thenReturn(otherApp);
        assertEquals(404, assertThrows(BusinessException.class, () -> this.service.deleteImage(commonName))
                .getHttpStatus().value());
        verify(this.oss, times(1)).deleteObject("bucket", commonKey);
        Files.write(this.directory.resolve("history.png"), PNG);
        this.service.deleteImage("history.png");
        assertFalse(Files.exists(this.directory.resolve("history.png")));
    }

    @Test
    void missingOssConfigurationShouldNotPreventContextStartup() {
        new ApplicationContextRunner().withUserConfiguration(OssConfiguration.class, LocalAssetService.class)
                .withBean(TestVersionMapper.class, () -> this.mapper)
                .withPropertyValues("PERSONA_LINK_UPLOAD_DIR=" + this.directory)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertThrows(BusinessException.class,
                            () -> context.getBean(LocalAssetService.class).saveImage(this.image()));
                });
    }

    private OSSObjectSummary object(String key) {
        OSSObjectSummary object = new OSSObjectSummary();
        object.setKey(key);
        object.setSize(PNG.length);
        object.setLastModified(new Date(1000));
        return object;
    }
}
