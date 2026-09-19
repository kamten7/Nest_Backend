package com.nest.minio.service;

import com.nest.constant.MessageConstant;
import com.nest.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** MinIO 文件上传服务：白名单后缀 + 体积上限 + 文件头校验 + 服务端强制 Content-Type。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    /**
     * 允许的后缀 → 服务端强制写入的 Content-Type。
     */
    private static final Map<String, String> ALLOWED_EXTENSIONS = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp",
            "bmp", "image/bmp");

    private final MinioClient minioClient;

    @Value("${nest.minio.endpoint}")
    private String endpoint;

    @Value("${nest.minio.bucket}")
    private String bucket;

    @Value("${nest.minio.avatar-bucket}")
    private String avatarBucket;

    /**
     * 单文件业务上限（默认 10MB）。
     */
    @Value("${nest.minio.image-max-size:10485760}")
    private long maxFileSize;

    private final Set<String> ensuredBuckets = ConcurrentHashMap.newKeySet();

    public String upload(MultipartFile file, String folder) {
        return doUpload(file, folder, bucket);
    }

    public String uploadAvatar(MultipartFile file, String folder) {
        return doUpload(file, folder, avatarBucket);
    }

    private String doUpload(MultipartFile file, String folder, String targetBucket) {
        String ext = resolveExtension(file.getOriginalFilename());
        String contentType = ALLOWED_EXTENSIONS.get(ext);

        if (file.getSize() > maxFileSize) {
            log.warn("拒绝上传：文件过大, name={}, size={}, limit={}",
                    file.getOriginalFilename(), file.getSize(), maxFileSize);
            throw new BusinessException(MessageConstant.FILE_SIZE_EXCEED);
        }

        /* 一次性读入内存（已受 maxFileSize 约束），既要校验文件头，也要把同一份数据交给 MinIO */
        byte[] data = readBytes(file);
        if (!matchesMagic(data, ext)) {
            log.warn("拒绝上传：文件内容与后缀不符, name={}, ext={}, size={}",
                    file.getOriginalFilename(), ext, data.length);
            throw new BusinessException(MessageConstant.FILE_CONTENT_INVALID);
        }

        String objectName = folder + "/" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;

        try {
            ensureBucket(targetBucket);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(targetBucket)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 上传失败: bucket={}, object={}", targetBucket, objectName, e);
            throw new RuntimeException("文件上传失败", e);
        }

        String url = endpoint + "/" + targetBucket + "/" + objectName;
        log.info("MinIO 上传成功: {}", url);
        return url;
    }

    /** 解析并校验后缀，返回小写裸后缀（不含点）；不在白名单内直接拒绝。 */
    private String resolveExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(MessageConstant.FILE_TYPE_NOT_ALLOWED);
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new BusinessException(MessageConstant.FILE_TYPE_NOT_ALLOWED);
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.containsKey(ext)) {
            throw new BusinessException(MessageConstant.FILE_TYPE_NOT_ALLOWED);
        }
        return ext;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("读取上传文件失败: name={}", file.getOriginalFilename(), e);
            throw new BusinessException(MessageConstant.FILE_READ_FAILED);
        }
    }

    /** 文件头魔数校验：挡住「把 HTML/脚本改名成 .jpg」这类绕过。 */
    private boolean matchesMagic(byte[] data, String ext) {
        switch (ext) {
            case "jpg":
            case "jpeg":
                return startsWith(data, 0xFF, 0xD8, 0xFF);
            case "png":
                return startsWith(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif":
                return startsWith(data, 0x47, 0x49, 0x46, 0x38);
            case "bmp":
                return startsWith(data, 0x42, 0x4D);
            case "webp":
                return startsWith(data, 0x52, 0x49, 0x46, 0x46)
                        && matchesAt(data, 8, 0x57, 0x45, 0x42, 0x50);
            default:
                return false;
        }
    }

    private boolean startsWith(byte[] data, int... magic) {
        return matchesAt(data, 0, magic);
    }

    private boolean matchesAt(byte[] data, int offset, int... magic) {
        if (data == null || data.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((data[offset + i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private void ensureBucket(String targetBucket) throws Exception {
        if (targetBucket == null || targetBucket.isBlank() || ensuredBuckets.contains(targetBucket)) {
            return;
        }
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(targetBucket).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(targetBucket).build());
            log.info("MinIO bucket 不存在，已自动创建: {}", targetBucket);
        }
        ensuredBuckets.add(targetBucket);
    }
}
