package com.example.email.web.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailRepositoryPreviewTest {

    @Test
    void preview_truncatesAt200Chars() {
        String body = "a".repeat(500);
        assertThat(EmailRepository.preview(body)).hasSize(200);
    }

    @Test
    void preview_collapsesWhitespace() {
        assertThat(EmailRepository.preview("hello\n\nworld   foo")).isEqualTo("hello world foo");
    }

    @Test
    void preview_handlesNullAndEmpty() {
        assertThat(EmailRepository.preview(null)).isEmpty();
        assertThat(EmailRepository.preview("")).isEmpty();
    }
}
