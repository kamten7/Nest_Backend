package com.nest.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nest.rag.support.KnowledgeMarkdownParser;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("rag-eval")
class RagRetrievalEvalTest {

    private static final double MIN_SCORE = 0.55;
    private static final double REFUSE_SCORE = 0.65;

    @Test
    void evaluatesVectorRetrievalOnGoldenSet() throws Exception {
        EmbeddingModel embeddingModel = new BgeSmallZhV15QuantizedEmbeddingModel();
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] docs = resolver.getResources("classpath*:knowledge/*.md");
        assertThat(docs).isNotEmpty();
        int chunks = 0;

        for (Resource resource : docs) {
            String raw = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            KnowledgeMarkdownParser.Parsed parsed = KnowledgeMarkdownParser.parse(resource.getFilename(), raw);
            Metadata metadata = new Metadata()
                    .put("docId", resource.getFilename())
                    .put("title", parsed.title())
                    .put("category", parsed.category())
                    .put("city", parsed.city());
            Document document = Document.from(parsed.body(), metadata);
            List<TextSegment> segments = DocumentSplitters.recursive(450, 60).split(document);
            for (TextSegment segment : segments) {
                if (segment.text() == null || segment.text().isBlank()) {
                    continue;
                }
                Embedding embedding = embeddingModel.embed(segment).content();
                store.add(embedding, segment);
                chunks++;
            }
        }

        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(MIN_SCORE)
                .build();

        EmbeddingStoreContentRetriever strictRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(REFUSE_SCORE)
                .build();

        List<JSONObject> golden = loadGolden();
        int answerable = 0;
        int hit1 = 0;
        int hit3 = 0;
        double reciprocalRankSum = 0;
        int unanswerable = 0;
        int refused = 0;

        List<String> failures = new ArrayList<>();
        for (JSONObject item : golden) {
            String question = item.getString("question");
            if (item.getBooleanValue("answerable")) {
                answerable++;
                List<String> titles = retrieveTitles(retriever, question);
                int rank = titles.indexOf(item.getString("expectedDoc"));
                if (rank == 0) {
                    hit1++;
                }
                if (rank >= 0 && rank < 3) {
                    hit3++;
                    reciprocalRankSum += 1.0 / (rank + 1);
                } else {
                    failures.add(question + " -> " + titles + "（期望：" + item.getString("expectedDoc") + "）");
                }
            } else {
                unanswerable++;
                if (retrieveTitles(strictRetriever, question).isEmpty()) {
                    refused++;
                }
            }
        }

        double hitAt1 = answerable == 0 ? 0 : (double) hit1 / answerable;
        double hitAt3 = answerable == 0 ? 0 : (double) hit3 / answerable;
        double mrr = answerable == 0 ? 0 : reciprocalRankSum / answerable;
        double refusalRate = unanswerable == 0 ? 1 : (double) refused / unanswerable;

        System.out.printf("""
                ==================== RAG EVAL ====================
                docs=%d chunks=%d answerable=%d unanswerable=%d
                hit@1=%.3f hit@3=%.3f mrr=%.3f refusal=%.3f
                failures=%d
                note: refusal 仅按向量分数阈值统计，不能替代 LLM 拒答判断；
                      线上最终拒答由 System Prompt + 工具“无依据”提示共同保证。
                ==================================================
                """, docs.length, chunks, answerable, unanswerable, hitAt1, hitAt3, mrr, refusalRate, failures.size());
        failures.forEach(f -> System.out.println("MISS: " + f));

        assertThat(hitAt3).as("hit@3 低于阈值").isGreaterThanOrEqualTo(0.85);
        assertThat(mrr).as("MRR 低于阈值").isGreaterThanOrEqualTo(0.80);
    }

    private static List<String> retrieveTitles(EmbeddingStoreContentRetriever retriever, String question) {
        List<Content> contents = retriever.retrieve(Query.from(question));
        return contents.stream()
                .map(content -> content.textSegment().metadata().getString("title"))
                .toList();
    }

    private static List<JSONObject> loadGolden() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:eval/golden.jsonl");
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return content.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(JSON::parseObject)
                .toList();
    }
}
