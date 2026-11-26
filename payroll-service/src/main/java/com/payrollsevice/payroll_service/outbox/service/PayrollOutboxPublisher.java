package com.payrollsevice.payroll_service.outbox.service;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.payrollsevice.payroll_service.outbox.model.PayrollClosedMessage;
import com.payrollsevice.payroll_service.outbox.model.PayrollOutboxEvent;
import com.payrollsevice.payroll_service.outbox.repository.PayrollOutboxRepository;

import tools.jackson.databind.json.JsonMapper;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "payroll.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class PayrollOutboxPublisher {
    private static final int BATCH_SIZE = 20;

    private final PayrollOutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transaction;

    public PayrollOutboxPublisher(PayrollOutboxRepository repository, KafkaTemplate<String, String> kafka,
                                  JsonMapper jsonMapper, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.kafka = kafka;
        this.jsonMapper = jsonMapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${payroll.outbox.publisher.delay:5s}")
    public void publishPending() {
        for (int index = 0; index < BATCH_SIZE; index++) {
            boolean published = Boolean.TRUE.equals(transaction.execute(status -> repository
                .findFirstByPublishedAtIsNullOrderByOccurredAtAscIdAsc()
                .map(this::publish)
                .orElse(false)));
            if (!published) {
                return;
            }
        }
    }

    private boolean publish(PayrollOutboxEvent event) {
        var message = new PayrollClosedMessage(event.getId(), event.getEventType(), event.getOccurredAt(), event.getPayload());
        String value = jsonMapper.writeValueAsString(message);
        try {
            kafka.send(PayrollClosedMessage.TOPIC, event.getPayrollId().toString(), value).get(15, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted publishing outbox event " + event.getId(), exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Could not publish outbox event " + event.getId(), exception);
        }
        event.setPublishedAt(Instant.now());
        return true;
    }
}
