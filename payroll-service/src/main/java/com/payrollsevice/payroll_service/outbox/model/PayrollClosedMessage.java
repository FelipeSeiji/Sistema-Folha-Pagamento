package com.payrollsevice.payroll_service.outbox.model;

import java.time.Instant;
import java.util.UUID;

public record PayrollClosedMessage(
    UUID eventId, String eventType, Instant occurredAt, PayrollOutboxEvent.PayrollClosed payload
) {
    public static final String TOPIC = "payroll.closed";
}
