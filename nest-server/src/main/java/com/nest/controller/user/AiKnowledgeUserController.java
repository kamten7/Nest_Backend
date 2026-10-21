package com.nest.controller.user;

import com.nest.common.Result;
import com.nest.service.KnowledgeRetrievalService;
import com.nest.vo.rag.RetrievedChunkVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user/ai/knowledge")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nest.rag", name = "enabled", havingValue = "true")
@Tag(name = "租客端-知识库", description = "知识库检索调试")
public class AiKnowledgeUserController {

    private final KnowledgeRetrievalService retrievalService;

    @GetMapping("/search")
    @Operation(summary = "知识库检索（调试）", description = "返回命中片段、来源与分数，用于验证 RAG 召回效果")
    public Result<List<RetrievedChunkVO>> search(
            @RequestParam("q") String query,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "3") Integer topK) {
        if (query == null || query.isBlank()) {
            return Result.success(List.of());
        }
        log.info("知识库检索: q={}, city={}, category={}, topK={}", query, city, category, topK);
        return Result.success(retrievalService.retrieve(query, city, category, topK == null ? 3 : topK));
    }
}
