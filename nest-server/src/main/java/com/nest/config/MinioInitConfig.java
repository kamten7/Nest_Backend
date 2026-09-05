package com.nest.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 初始化：应用启动时确保桶存在。
 *
 * 用于替代 docker-compose 中的 minio-init 服务，实现零手动建桶；
 * 幂等：桶已存在则跳过；构建失败会抛出异常，让启动阶段尽早暴露问题。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioInitConfig {

    private final MinioClient minioClient;

    @Value("${nest.minio.bucket}")
    private String bucket;

    @Bean
    public ApplicationRunner minioBucketInit() {
        return args -> {
            try {
                // 1. 判断桶是否存在
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(bucket).build());
                if (exists) {
                    log.info("MinIO 桶已存在: {}", bucket);
                } else {
                    // 2. 不存在则创建
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("MinIO 桶已自动创建: {}", bucket);
                }
            } catch (Exception e) {
                log.error("MinIO 桶初始化失败: bucket={}", bucket, e);
                throw e;
            }
        };
    }
}
