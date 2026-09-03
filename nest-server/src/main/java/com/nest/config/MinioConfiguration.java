package com.nest.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储配置。
 */
@Configuration
public class MinioConfiguration {

    @Value("${nest.minio.endpoint}")
    private String endpoint;

    @Value("${nest.minio.access-key}")
    private String accessKey;

    @Value("${nest.minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
