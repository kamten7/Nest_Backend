/**
 * Nest 租房平台 AI 集成层。
 *
 * <h3>架构</h3>
 * <p>基于 LangChain4j 1.18.1 的真 Function Calling 模式：</p>
 * <ol>
 *   <li>{@code config/AiModelConfig} — 房东端 + 租客端双供应商 OpenAiStreamingChatModel Bean</li>
 *   <li>{@code config/NestAiAgent} — {@code AiServices.builder().tools(...)} 构建 Agent</li>
 *   <li>{@code tools/} — {@code @Tool} 注解的纯 Java 工具类（房源搜索、推荐、收藏、预约）</li>
 * </ol>
 *
 * <h3>与外卖系统对比</h3>
 * <p>外卖用户端是正则预执行（UserAiOrchestrator + Pattern），
 * Nest 从一开始就用真 Function Calling，吸取外卖经验：</p>
 * <ul>
 *   <li>写操作（预约/收藏）必须用户确认后才执行</li>
 *   <li>禁止 Tool 单例 Bean 存共享实例字段（并发串号教训）</li>
 *   <li>userId 通过 {@code @ToolMemoryId} 自动注入，或用参数透传</li>
 * </ul>
 *
 * @author kamten7
 * @since 1.0
 */
package com.nest.AI;
