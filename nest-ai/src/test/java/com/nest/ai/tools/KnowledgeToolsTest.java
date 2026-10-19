package com.nest.ai.tools;

import com.nest.service.KnowledgeRetrievalService;
import com.nest.vo.RetrievedChunkVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeToolsTest {

    @Test
    void returnsNumberedCitationsFromRetriever() {
        KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
        when(retrievalService.retrieve("押金能退吗", "湛江市", null, 3)).thenReturn(List.of(
                RetrievedChunkVO.builder()
                        .docId(2L)
                        .title("押金与退租结算规则")
                        .category("policy")
                        .city("湛江市")
                        .chunkIndex(0)
                        .content("退租申请满 7 天后可结算。")
                        .source("02-deposit-and-refund.md")
                        .score(0.82)
                        .build()));

        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeRetrievalService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(retrievalService);

        KnowledgeTools tools = new KnowledgeTools(provider);
        String result = tools.searchRentalKnowledge("押金能退吗", "湛江市", null, 3);

        assertThat(result).contains("[资料1]");
        assertThat(result).contains("押金与退租结算规则");
        assertThat(result).contains("退租申请满 7 天");
    }

    @Test
    void returnsExplicitFallbackWhenNoHit() {
        KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
        when(retrievalService.retrieve("火星租房", null, null, 3)).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeRetrievalService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(retrievalService);

        String result = new KnowledgeTools(provider).searchRentalKnowledge("火星租房", null, null, 3);

        assertThat(result).contains("没有找到").contains("不要编造");
    }

    @Test
    void degradesWhenRagDisabled() {
        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeRetrievalService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        String result = new KnowledgeTools(provider).searchRentalKnowledge("押金能退吗", null, null, 3);

        assertThat(result).contains("知识库未启用");
    }
}
