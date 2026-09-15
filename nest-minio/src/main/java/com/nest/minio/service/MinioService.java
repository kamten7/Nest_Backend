package com.nest.minio.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** MinIO 文件上传服务。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${nest.minio.endpoint}")
    private String endpoint;

    /** 通用文件 bucket（房源图片等） */
    @Value("${nest.minio.bucket}")
    private String bucket;

    /** 用户头像专用 bucket */
    @Value("${nest.minio.avatar-bucket}")
    private String avatarBucket;

    /** 已确认存在的 bucket 缓存，避免每次上传都多问 MinIO 一次 */
    private final Set<String> ensuredBuckets = ConcurrentHashMap.newKeySet();

    /** 上传文件到通用 bucket，返回可访问的完整 URL。 */
    public String upload(MultipartFile file, String folder) {
        return doUpload(file, folder, bucket);
    }

    /** 上传用户头像到「头像专用 bucket」，返回可访问的完整 URL。 */
    public String uploadAvatar(MultipartFile file, String folder) {
        return doUpload(file, folder, avatarBucket);
    }

    private String doUpload(MultipartFile file, String folder, String targetBucket) {
        String ext = getExtension(file.getOriginalFilename());
        String objectName = folder + "/" + UUID.randomUUID().toString().substring(0, 8) + ext;

        try {
            ensureBucket(targetBucket);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(targetBucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("MinIO 上传失败: bucket={}, object={}", targetBucket, objectName, e);
            throw new RuntimeException("文件上传失败", e);
        }

        String url = endpoint + "/" + targetBucket + "/" + objectName;
        log.info("MinIO 上传成功: {}", url);
        return url;
    }

    /**
     * 确保 bucket 存在，不存在则自动创建。
     *
     * <p>不依赖 docker-compose 的 minio-init 一次性脚本 —— 新环境 clone 下来只要配置正确就能直接上传。
     */
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

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}
