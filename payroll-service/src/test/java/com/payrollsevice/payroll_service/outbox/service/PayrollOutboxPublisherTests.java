package com.payrollsevice.payroll_service.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import com.payrollsevice.payroll_service.outbox.model.PayrollOutboxEvent;
import com.payrollsevice.payroll_service.outbox.repository.PayrollOutboxRepository;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:outbox-publisher;MODE=PostgreSQL")
@ActiveProfiles("test")
class PayrollOutboxPublisherTests {
    @Autowired
    private PayrollOutboxRepository repository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void retriesUnconfirmedEventsAndMarksThemAfterAcknowledgment() {
        UUID payrollId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        var event = repository.saveAndFlush(new PayrollOutboxEvent(payrollId, companyId,
            YearMonth.of(2026, 11), Instant.now()));

        @SuppressWarnings("unchecked")
        var kafka = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> rejected =
            CompletableFuture.failedFuture(new IllegalStateException("Broker unavailable"));
        CompletableFuture<SendResult<String, String>> acknowledged = CompletableFuture.completedFuture(null);
        when(kafka.send(eq("payroll.closed"), eq(payrollId.toString()), anyString()))
            .thenReturn(rejected, acknowledged);

        var publisher = new PayrollOutboxPublisher(repository, kafka, jsonMapper, transactionManager);
        assertThatThrownBy(publisher::publishPending).isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById(event.getId()).orElseThrow().getPublishedAt()).isNull();

        publisher.publishPending();
        assertThat(repository.findById(event.getId()).orElseThrow().getPublishedAt()).isNotNull();
        publisher.publishPending();

        var message = ArgumentCaptor.forClass(String.class);
        verify(kafka, times(2)).send(eq("payroll.closed"), eq(payrollId.toString()), message.capture());
        assertThat(message.getAllValues()).containsOnly(message.getValue());
        var json = jsonMapper.readTree(message.getValue());
        assertThat(json.path("eventId").asString()).isEqualTo(event.getId().toString());
        assertThat(json.path("eventType").asString()).isEqualTo("PayrollClosed");
        assertThat(json.path("payload").path("payrollId").asString()).isEqualTo(payrollId.toString());
        assertThat(json.path("payload").path("companyId").asString()).isEqualTo(companyId.toString());
    }
}
