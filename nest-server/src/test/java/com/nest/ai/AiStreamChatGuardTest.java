package com.nest.ai;

import com.nest.ai.config.NestAiAgent.TenantAiAssistant;
import com.nest.ai.service.impl.AiUserServiceImpl;
import com.nest.common.BaseContext;
import com.nest.constant.MessageConstant;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P1-3 同租户并发串行化 + P1-4 容器超时后回调迟到不得二次写/二次 complete。 */
class AiStreamChatGuardTest {

    private static final long TENANT_ID = 7L;

    private TenantAiAssistant assistant;
    private AiUserServiceImpl service;

    private record Harness(StringWriter buffer, AsyncContext ctx, Consumer<ChatResponse> onComplete) {
    }

    @BeforeEach
    void setUp() {
        assistant = mock(TenantAiAssistant.class);
        service = new AiUserServiceImpl(assistant, mock(ChatMemoryStore.class));
        BaseContext.setCurrentId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        BaseContext.remove();
    }

    private Harness startStream() throws Exception {
        StringWriter buffer = new StringWriter();
        AsyncContext ctx = mock(AsyncContext.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(ctx.getResponse()).thenReturn(response);
        when(response.getWriter()).thenReturn(new PrintWriter(buffer, true));

        TokenStream stream = mock(TokenStream.class, RETURNS_SELF);
        when(assistant.chat(eq(TENANT_ID), any())).thenReturn(stream);
        service.streamChat("想租个两居室", ctx);

        if (mockingDetails(stream).getInvocations().isEmpty()) {
            return new Harness(buffer, ctx, null);
        }
        ArgumentCaptor<Consumer<ChatResponse>> completeCap = ArgumentCaptor.forClass(Consumer.class);
        verify(stream).onCompleteResponse(completeCap.capture());
        return new Harness(buffer, ctx, completeCap.getValue());
    }

    private AsyncListener captureListener(AsyncContext ctx) {
        ArgumentCaptor<AsyncListener> cap = ArgumentCaptor.forClass(AsyncListener.class);
        verify(ctx).addListener(cap.capture());
        return cap.getValue();
    }

    @Test
    void secondConcurrentStreamForSameTenantIsRejectedWhileFirstInFlight() throws Exception {
        Harness first = startStream();
        Harness second = startStream();

        assertThat(second.buffer().toString()).contains(MessageConstant.AI_CHAT_IN_PROGRESS);
        assertThat(first.buffer().toString()).doesNotContain(MessageConstant.AI_CHAT_IN_PROGRESS);
        verify(assistant, times(1)).chat(eq(TENANT_ID), any());
    }

    @Test
    void containerTimeoutFinishesOnceAndLateModelCallbackIsIgnored() throws Exception {
        Harness harness = startStream();
        captureListener(harness.ctx()).onTimeout(mock(AsyncEvent.class));

        assertThat(harness.buffer().toString()).contains("[ERROR]").contains("[DONE]");
        verify(harness.ctx(), times(1)).complete();

        harness.onComplete().accept(mock(ChatResponse.class));
        verify(harness.ctx(), times(1)).complete();
    }

    @Test
    void tenantLockReleasedAfterTimeoutSoNextRequestProceeds() throws Exception {
        Harness harness = startStream();
        captureListener(harness.ctx()).onTimeout(mock(AsyncEvent.class));

        Harness next = startStream();
        assertThat(next.buffer().toString()).doesNotContain(MessageConstant.AI_CHAT_IN_PROGRESS);
        verify(assistant, times(2)).chat(eq(TENANT_ID), any());
    }

    @Test
    void normalCompletionWritesDoneOnceAndReleasesTenantLock() throws Exception {
        Harness harness = startStream();
        harness.onComplete().accept(mock(ChatResponse.class));

        assertThat(harness.buffer().toString()).contains("[DONE]").doesNotContain("[ERROR]");
        verify(harness.ctx(), times(1)).complete();

        startStream();
        verify(assistant, times(2)).chat(eq(TENANT_ID), any());
    }
}
