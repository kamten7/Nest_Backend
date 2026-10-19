package com.nest.rag.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeMarkdownParserTest {

    @Test
    void parsesFrontMatterAndLeavesBody() {
        String raw = """
                ---
                title: 押金与退租规则
                category: policy
                city: 湛江市
                district: 霞山区
                ---
                # 押金与退租规则

                押金在租期内锁定。
                """;

        KnowledgeMarkdownParser.Parsed parsed = KnowledgeMarkdownParser.parse("02-deposit.md", raw);

        assertThat(parsed.title()).isEqualTo("押金与退租规则");
        assertThat(parsed.category()).isEqualTo("policy");
        assertThat(parsed.city()).isEqualTo("湛江市");
        assertThat(parsed.district()).isEqualTo("霞山区");
        assertThat(parsed.body()).startsWith("# 押金与退租规则");
        assertThat(parsed.body()).doesNotContain("category: policy");
    }

    @Test
    void fallsBackToFirstHeadingWhenNoFrontMatter() {
        KnowledgeMarkdownParser.Parsed parsed =
                KnowledgeMarkdownParser.parse("faq.md", "# 常见问题\n\n内容");

        assertThat(parsed.title()).isEqualTo("常见问题");
        assertThat(parsed.category()).isEqualTo("faq");
        assertThat(parsed.city()).isEmpty();
    }

    @Test
    void fallsBackToFilenameWhenNoHeading() {
        KnowledgeMarkdownParser.Parsed parsed =
                KnowledgeMarkdownParser.parse("12-faq.md", "没有标题的正文");

        assertThat(parsed.title()).isEqualTo("12-faq");
    }
}
