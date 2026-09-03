package com.nest.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * MinIO 文件上传服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${nest.minio.endpoint}")
    private String endpoint;

    @Value("${nest.minio.bucket}")
    private String bucket;

    /**
     * 上传文件到 MinIO，返回可访问的完整 URL。
     *
     * @param file   前端上传的文件
     * @param folder 存储目录，如 "house"
     * @return 完整 URL，如 http://localhost:9012/nest-rent/house/uuid.jpg
     */
    public String upload(MultipartFile file, String folder) {
        // 生成唯一文件名
        String ext = getExtension(file.getOriginalFilename());
        String objectName = folder + "/" + UUID.randomUUID().toString().substring(0, 8) + ext;

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("MinIO 上传失败: bucket={}, object={}", bucket, objectName, e);
            throw new RuntimeException("文件上传失败", e);
        }

        String url = endpoint + "/" + bucket + "/" + objectName;
        log.info("MinIO 上传成功: {}", url);
        return url;
    }

    /** 提取文件扩展名，如 ".jpg" */
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}
