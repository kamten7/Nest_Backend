package com.nest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Nest 租房平台启动类。 */
@SpringBootApplication
@EnableCaching
@EnableTransactionManagement
@EnableScheduling
public class NestApplication {

    public static void main(String[] args) {
        SpringApplication.run(NestApplication.class, args);
        System.out.println("""

                ============================================================
                  🏠  Nest 租房平台 启动成功！
                ============================================================
                  API 文档：  http://localhost:8080/doc.html
                  健康检查：  http://localhost:8080/actuator/health
                ============================================================
                """);
    }
}
