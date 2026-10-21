package com.nest.controller.admin;

import com.nest.common.BaseContext;
import com.nest.common.Result;
import com.nest.constant.MessageConstant;
import com.nest.exception.BusinessException;
import com.nest.rag.service.KnowledgeAdminService;
import com.nest.rag.support.KnowledgeMarkdownParser;
import com.nest.service.impl.PlatformAdminGuard;
import com.nest.vo.rag.KnowledgeDocumentVO;
import com.nest.vo.rag.RetrievedChunkVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Slf4j
@RestController
@RequestMapping("/admin/knowledge")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nest.rag", name = "enabled", havingValue = "true")
@Tag(name = "房东端-知识库", description = "知识库文档上传、重建索引、删除与检索调试")
public class KnowledgeAdminController {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private final KnowledgeAdminService knowledgeAdminService;
    private final PlatformAdminGuard platformAdminGuard;

    @GetMapping("/documents")
    @Operation(summary = "知识库文档列表")
    public Result<List<KnowledgeDocumentVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        platformAdminGuard.require();
        return Result.success(knowledgeAdminService.list(page == null ? 1 : page, size == null ? 20 : size));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传知识库文档", description = "支持 Markdown / TXT，自动解析 front matter 并索引")
    public Result<KnowledgeDocumentVO> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district) throws IOException {
        platformAdminGuard.require();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(MessageConstant.KNOWLEDGE_CONTENT_EMPTY);
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException(MessageConstant.KNOWLEDGE_FILE_TOO_LARGE);
        }
        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".md") && !lower.endsWith(".markdown") && !lower.endsWith(".txt")) {
            throw new BusinessException(MessageConstant.KNOWLEDGE_FILE_TYPE_INVALID);
        }
        String raw = new String(file.getBytes(), StandardCharsets.UTF_8);
        KnowledgeMarkdownParser.Parsed parsed = KnowledgeMarkdownParser.parse(filename, raw);
        Long operatorId = BaseContext.getCurrentId();
        KnowledgeDocumentVO vo = knowledgeAdminService.ingest(
                firstNonBlank(title, parsed.title()),
                firstNonBlank(category, parsed.category()),
                firstNonBlank(city, parsed.city()),
                firstNonBlank(district, parsed.district()),
                "UPLOAD", filename, parsed.body(), operatorId);
        log.info("知识库上传成功: id={}, title={}, operator={}", vo.getId(), vo.getTitle(), operatorId);
        return Result.success(vo);
    }

    @PostMapping("/documents/{id}/reindex")
    @Operation(summary = "重建文档索引")
    public Result<Void> reindex(@PathVariable Long id) {
        platformAdminGuard.require();
        knowledgeAdminService.reindex(id);
        return Result.successMsg("已重建索引");
    }

    @DeleteMapping("/documents/{id}")
    @Operation(summary = "删除知识库文档")
    public Result<Void> delete(@PathVariable Long id) {
        platformAdminGuard.require();
        knowledgeAdminService.delete(id);
        return Result.successMsg("已删除");
    }

    @PostMapping("/search")
    @Operation(summary = "知识库检索（管理端调试）")
    public Result<List<RetrievedChunkVO>> search(
            @RequestParam("q") String query,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "5") Integer topK) {
        platformAdminGuard.require();
        return Result.success(knowledgeAdminService.search(query, city, category, topK == null ? 5 : topK));
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
