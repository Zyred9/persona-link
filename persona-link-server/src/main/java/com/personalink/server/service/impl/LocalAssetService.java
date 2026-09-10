package com.personalink.server.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.ObjectMetadata;
import com.personalink.server.config.OssConfiguration;
import com.personalink.server.dto.AssetResponse;
import com.personalink.server.dto.AssetItemResponse;
import com.personalink.server.entity.TestVersionEntity;
import com.personalink.server.entity.ImageGenerationTaskEntity;
import com.personalink.server.mapper.ImageGenerationTaskMapper;
import com.personalink.server.exception.BusinessException;
import com.personalink.server.mapper.TestVersionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 新图片使用 OSS，保留历史本地图片的访问和管理。
 */
@Service
public class LocalAssetService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/webp", ".webp");
    private static final int NOT_DELETED = 0;
    private static final String OBJECT_PREFIX = "persona-link/images/";
    private static final String COMMON_PREFIX = "common/";
    private static final String APP_METADATA_KEY = "app";
    private static final String APP_METADATA_VALUE = "persona-link";

    private final Path uploadDirectory;
    private final TestVersionMapper testVersionMapper;
    private final OssConfiguration ossConfiguration;
    private final OSS ossClient;
    private ImageGenerationTaskMapper imageTaskMapper;
    private com.personalink.server.service.AppConfigService appConfigService;

    /** 首页标题图同样属于受保护的业务素材。 */
    @Autowired
    public void setAppConfigService(com.personalink.server.service.AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    /** 图片任务模块存在时，未采用的生成结果同样属于受保护的业务素材。 */
    @Autowired(required = false)
    public void setImageTaskMapper(ImageGenerationTaskMapper imageTaskMapper) {
        this.imageTaskMapper = imageTaskMapper;
    }

    public LocalAssetService(@Value("${PERSONA_LINK_UPLOAD_DIR:uploads}") String uploadDirectory,
                             TestVersionMapper testVersionMapper, OssConfiguration ossConfiguration,
                             @Lazy OSS ossClient) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.testVersionMapper = testVersionMapper;
        this.ossConfiguration = ossConfiguration;
        this.ossClient = ossClient;
    }

    /**
     * 保存一张白名单图片。
     *
     * @param file 图片文件
     * @return 图片访问地址
     */
    public AssetResponse saveImage(MultipartFile file) {
        return this.saveImage(file, false);
    }

    /** 头像单独存储，避免暴露到后台素材库或被素材清理删除。 */
    public AssetResponse saveAvatarImage(MultipartFile file) {
        return this.saveImage(file, true);
    }

    private AssetResponse saveImage(MultipartFile file, boolean avatar) {
        if (Objects.isNull(file) || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "图片不能为空且不能超过5MB");
        }
        String extension = Objects.isNull(file.getContentType()) ? null
                : CONTENT_TYPE_EXTENSIONS.get(file.getContentType());
        if (Objects.isNull(extension)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40002, "仅支持 PNG、JPG 和 WebP 图片");
        }

        try (InputStream inputStream = file.getInputStream()) {
            byte[] bytes = inputStream.readNBytes((int) MAX_IMAGE_BYTES + 1);
            return this.saveImageBytes(bytes, file.getContentType(), extension, avatar);
        } catch (IOException exception) {
            throw new IllegalStateException("读取待上传图片失败", exception);
        }
    }

    /** 保存经过服务端尺寸归一化的 AI 生成 PNG，复用上传资源归属及访问规则。 */
    public AssetResponse saveGeneratedImage(byte[] png) {
        return this.saveImageBytes(png, "image/png", ".png", false);
    }

    private AssetResponse saveImageBytes(byte[] bytes, String contentType, String extension, boolean avatar) {
        if (Objects.isNull(bytes) || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "图片不能为空且不能超过5MB");
        }
        if (!this.matchesImageHeader(bytes, extension)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40002, "图片内容与 PNG、JPG 或 WebP 格式不匹配");
        }
        String fileName = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().replace("-", "") + extension;
        this.ossConfiguration.validate();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(bytes.length);
        metadata.addUserMetadata(APP_METADATA_KEY, APP_METADATA_VALUE);
        String objectKey = avatar ? "persona-link/avatars/" + fileName : this.ossObjectKey(fileName);
        try {
            this.ossClient.putObject(this.ossConfiguration.getBucketName(), objectKey,
                    new ByteArrayInputStream(bytes), metadata);
        } catch (OSSException | ClientException exception) {
            throw new IllegalStateException("上传图片到 OSS 失败", exception);
        }
        return new AssetResponse(this.ossConfiguration.getDomain() + "/" + objectKey);
    }

    /**
     * 合并 OSS 和历史本地图片，按更新时间倒序返回。
     *
     * @param keyword 文件名关键字
     * @return 素材列表
     */
    public List<AssetItemResponse> listImages(String keyword) {
        Map<String, Long> references = this.referenceCounts();
        String normalizedKeyword = Objects.isNull(keyword) ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<AssetItemResponse> images = new ArrayList<>();
        if (Files.isDirectory(this.uploadDirectory)) {
            try (Stream<Path> paths = Files.list(this.uploadDirectory)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> this.isAllowedImage(path.getFileName().toString()))
                        .map(path -> this.toAssetItem(path, references))
                        .forEach(images::add);
            } catch (IOException exception) {
                throw new IllegalStateException("读取素材列表失败", exception);
            }
        }
        if (this.ossConfiguration.hasConfiguration()) {
            this.ossConfiguration.validate();
            this.addOssImages(images, references);
        }
        return images.stream()
                .filter(item -> item.fileName().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .sorted((first, second) -> second.updatedAt().compareTo(first.updatedAt()))
                .toList();
    }

    /**
     * 删除未被业务引用的图片；共享目录对象必须带本应用归属标记。
     *
     * @param fileName 文件名
     */
    public void deleteImage(String fileName) {
        Path target = this.resolveImage(fileName);
        boolean ossImage = this.isOssImage(fileName);
        if (ossImage) {
            this.ossConfiguration.validate();
        }
        String url = ossImage ? this.ossUrl(fileName) : "/uploads/" + fileName;
        if (this.referenceCounts().getOrDefault(url, 0L) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, 40903, "素材正在被业务配置引用，不能删除");
        }
        if (ossImage) {
            try {
                String bucketName = this.ossConfiguration.getBucketName();
                String objectKey = this.ossObjectKey(fileName);
                boolean exists = this.isCommonImage(fileName) ? this.isOwnedCommonImage(objectKey)
                        : this.ossClient.doesObjectExist(bucketName, objectKey);
                if (!exists) {
                    throw new BusinessException(HttpStatus.NOT_FOUND, 40403, "素材不存在");
                }
                this.ossClient.deleteObject(bucketName, objectKey);
                return;
            } catch (OSSException | ClientException exception) {
                throw new IllegalStateException("删除 OSS 图片素材失败", exception);
            }
        }
        try {
            if (!Files.deleteIfExists(target)) {
                throw new BusinessException(HttpStatus.NOT_FOUND, 40403, "素材不存在");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("删除素材失败", exception);
        }
    }

    private Map<String, Long> referenceCounts() {
        Map<String, Long> references = new HashMap<>();
        if (Objects.nonNull(this.appConfigService)) {
            // 图片处理参数只影响展示；素材引用按不含查询参数和片段的原始地址统计。
            String titleImageUrl = this.appConfigService.readHomeConfig().titleImageUrl().split("[?#]", 2)[0];
            if (!titleImageUrl.isEmpty()) { references.merge(titleImageUrl, 1L, Long::sum); }
        }
        this.testVersionMapper.selectList(Wrappers.<TestVersionEntity>lambdaQuery()
                        .select(TestVersionEntity::getCoverUrl, TestVersionEntity::getDetailImageUrl)
                        .eq(TestVersionEntity::getDeleted, NOT_DELETED))
                .stream()
                .flatMap(version -> Stream.of(version.getCoverUrl(), version.getDetailImageUrl()))
                .filter(Objects::nonNull)
                .forEach(url -> references.merge(url, 1L, Long::sum));
        if (Objects.nonNull(this.imageTaskMapper)) {
            this.imageTaskMapper.selectList(Wrappers.<ImageGenerationTaskEntity>lambdaQuery()
                            .select(ImageGenerationTaskEntity::getCoverUrl, ImageGenerationTaskEntity::getDetailImageUrl)
                            .eq(ImageGenerationTaskEntity::getDeleted, NOT_DELETED))
                    .stream()
                    .flatMap(task -> Stream.of(task.getCoverUrl(), task.getDetailImageUrl()))
                    .filter(Objects::nonNull)
                    .forEach(url -> references.merge(url, 1L, Long::sum));
        }
        return references;
    }

    private AssetItemResponse toAssetItem(Path path, Map<String, Long> references) {
        try {
            String fileName = path.getFileName().toString();
            String url = "/uploads/" + fileName;
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
            LocalDateTime updatedAt = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault());
            return new AssetItemResponse(fileName, url, extension, Files.size(path), updatedAt,
                    references.getOrDefault(url, 0L));
        } catch (IOException exception) {
            throw new IllegalStateException("读取素材信息失败", exception);
        }
    }

    private Path resolveImage(String fileName) {
        if (Objects.isNull(fileName) || !fileName.matches("[A-Za-z0-9_-]+\\.(?i:png|jpg|jpeg|webp)")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "图片文件名非法");
        }
        Path target = this.uploadDirectory.resolve(fileName).normalize();
        if (!target.startsWith(this.uploadDirectory)
                || !Objects.equals(fileName, target.getFileName().toString())
                || !this.isAllowedImage(fileName)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 40003, "图片文件名非法");
        }
        return target;
    }

    private boolean isAllowedImage(String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return CONTENT_TYPE_EXTENSIONS.containsValue(lowerName.substring(Math.max(0, lowerName.lastIndexOf('.'))));
    }

    public Path getUploadDirectory() {
        return this.uploadDirectory;
    }

    private String ossUrl(String fileName) {
        return this.ossConfiguration.getDomain() + "/" + this.ossObjectKey(fileName);
    }

    private String ossObjectKey(String fileName) {
        return this.isCommonImage(fileName) ? COMMON_PREFIX + fileName.substring(0, 8) + "/" + fileName.substring(9)
                : OBJECT_PREFIX + fileName;
    }

    private boolean isCommonImage(String fileName) {
        return fileName.matches("[0-9]{8}-[0-9a-f]{32}\\.(png|jpg|webp)");
    }

    private boolean isOssImage(String fileName) {
        return this.isCommonImage(fileName) || fileName.matches("oss-[0-9a-f]{32}\\.(png|jpg|webp)");
    }

    private void addOssImages(List<AssetItemResponse> images, Map<String, Long> references) {
        this.addOssImages(images, references, OBJECT_PREFIX);
        this.addOssImages(images, references, COMMON_PREFIX);
    }

    private void addOssImages(List<AssetItemResponse> images, Map<String, Long> references, String prefix) {
        ListObjectsV2Request request = new ListObjectsV2Request(this.ossConfiguration.getBucketName());
        request.setPrefix(prefix);
        request.setMaxKeys(1000);
        try {
            ListObjectsV2Result result;
            do {
                result = this.ossClient.listObjectsV2(request);
                result.getObjectSummaries().forEach(object -> {
                    if (!object.getKey().startsWith(prefix)) {
                        return;
                    }
                    String suffix = object.getKey().substring(prefix.length());
                    String fileName;
                    if (COMMON_PREFIX.equals(prefix)) {
                        if (!suffix.matches("[0-9]{8}/[0-9a-f]{32}\\.(png|jpg|webp)")
                                || !this.isOwnedCommonImage(object.getKey())) {
                            return;
                        }
                        fileName = suffix.replace('/', '-');
                    } else {
                        if (!suffix.matches("oss-[0-9a-f]{32}\\.(png|jpg|webp)")) {
                            return;
                        }
                        fileName = suffix;
                    }
                    String url = this.ossUrl(fileName);
                    images.add(new AssetItemResponse(fileName, url,
                            fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT),
                            object.getSize(), LocalDateTime.ofInstant(object.getLastModified().toInstant(),
                            ZoneId.systemDefault()), references.getOrDefault(url, 0L)));
                });
                request.setContinuationToken(result.getNextContinuationToken());
            } while (result.isTruncated());
        } catch (OSSException | ClientException exception) {
            throw new IllegalStateException("读取 OSS 图片素材列表失败", exception);
        }
    }

    private boolean isOwnedCommonImage(String objectKey) {
        try {
            // ponytail: 共享目录逐对象 HEAD 校验归属，素材规模增大后改为持久化素材索引。
            ObjectMetadata metadata = this.ossClient.getObjectMetadata(this.ossConfiguration.getBucketName(), objectKey);
            return APP_METADATA_VALUE.equals(metadata.getUserMetadata().get(APP_METADATA_KEY));
        } catch (OSSException exception) {
            if ("NoSuchKey".equals(exception.getErrorCode())) {
                return false;
            }
            throw exception;
        }
    }

    private boolean matchesImageHeader(byte[] bytes, String extension) {
        if (".png".equals(extension)) {
            return bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                    new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        }
        if (".jpg".equals(extension)) {
            return bytes.length >= 3 && bytes[0] == (byte) 0xff
                    && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
        }
        return bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P';
    }
}
