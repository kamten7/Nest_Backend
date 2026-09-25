package com.nest.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** P1-1：AI 对话入参必须有长度上限，防止超大 body 原样进入 prompt。 */
class AiChatDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankMessageRejected() {
        AiChatDTO dto = new AiChatDTO();
        dto.setMessage("   ");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void messageOver200CharsRejected() {
        AiChatDTO dto = new AiChatDTO();
        dto.setMessage("x".repeat(201));
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void messageWithin200CharsAccepted() {
        AiChatDTO dto = new AiChatDTO();
        dto.setMessage("x".repeat(200));
        assertThat(validator.validate(dto)).isEmpty();
    }
}
