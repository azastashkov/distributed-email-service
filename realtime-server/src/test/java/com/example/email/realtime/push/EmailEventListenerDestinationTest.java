package com.example.email.realtime.push;

import com.example.email.common.event.EmailEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailEventListenerDestinationTest {

    @Test
    void destinationFor_mapsCreatedToEmailNew() {
        EmailEvent e = new EmailEvent.EmailCreated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "s", "f", "p", Instant.now(), Instant.now());
        assertThat(EmailEventListener.destinationFor(e)).isEqualTo("/queue/email.new");
    }

    @Test
    void destinationFor_mapsDeletedToEmailDeleted() {
        EmailEvent e = new EmailEvent.EmailDeleted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        assertThat(EmailEventListener.destinationFor(e)).isEqualTo("/queue/email.deleted");
    }

    @Test
    void destinationFor_mapsReadChangedToEmailRead() {
        EmailEvent e = new EmailEvent.EmailReadChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true, Instant.now());
        assertThat(EmailEventListener.destinationFor(e)).isEqualTo("/queue/email.read");
    }
}
