package com.nest.rag.support;

import com.nest.rag.service.KnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagIngestionRunner implements ApplicationRunner {

    private final ObjectProvider<KnowledgeIngestionService> ingestionServiceProvider;

    @Value("${nest.rag.ingest-on-startup:false}")
    private boolean ingestOnStartup;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!ingestOnStartup) {
            return;
        }
        KnowledgeIngestionService ingestionService = ingestionServiceProvider.getIfAvailable();
        if (ingestionService == null) {
            return;
        }
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:knowledge/*.md");
        int success = 0;
        int failed = 0;
        for (Resource resource : resources) {
            try {
                String filename = resource.getFilename();
                String raw = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                KnowledgeMarkdownParser.Parsed parsed = KnowledgeMarkdownParser.parse(filename, raw);
                ingestionService.ingest(
                        parsed.title(), parsed.category(), parsed.city(), parsed.district(),
                        "MARKDOWN", filename, parsed.body(), null);
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("知识库文档导入失败: {}, {}", resource.getFilename(), e.getMessage());
            }
        }
        log.info("知识库启动导入完成: 成功 {} 篇, 失败 {} 篇", success, failed);
    }
}
