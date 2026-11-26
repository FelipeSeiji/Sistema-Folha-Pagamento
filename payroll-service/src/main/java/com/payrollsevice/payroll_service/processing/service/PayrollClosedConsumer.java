package com.payrollsevice.payroll_service.processing.service;

import java.time.YearMonth;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.payrollsevice.payroll_service.outbox.model.PayrollClosedMessage;
import com.payrollsevice.payroll_service.outbox.model.PayrollOutboxEvent;
import com.payrollsevice.payroll_service.payslip.repository.PayslipRepository;
import com.payrollsevice.payroll_service.processing.model.ClosedPayrollSummary;
import com.payrollsevice.payroll_service.processing.model.PayrollStatus;
import com.payrollsevice.payroll_service.processing.repository.ClosedPayrollSummaryRepository;
import com.payrollsevice.payroll_service.processing.repository.PayrollRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class PayrollClosedConsumer {
    private final JsonMapper jsonMapper;
    private final PayrollRepository payrollRepository;
    private final PayslipRepository payslipRepository;
    private final ClosedPayrollSummaryRepository summaryRepository;

    @KafkaListener(topics = PayrollClosedMessage.TOPIC)
    @Transactional
    public void consume(String json) {
        var message = jsonMapper.readValue(json, PayrollClosedMessage.class);
        if (message == null || message.eventId() == null || message.occurredAt() == null
                || !PayrollOutboxEvent.PAYROLL_CLOSED.equals(message.eventType())
                || message.payload() == null || message.payload().payrollId() == null) {
            throw new IllegalArgumentException("Invalid PayrollClosed event.");
        }

        var payload = message.payload();
        var payroll = payrollRepository.findForUpdateById(payload.payrollId())
            .orElseThrow(() -> new IllegalStateException("Payroll not found: " + payload.payrollId()));
        if (!payroll.getCompanyId().equals(payload.companyId())
                || !YearMonth.from(payroll.getPeriod()).equals(payload.period())
                || payroll.getStatus() != PayrollStatus.CLOSED) {
            throw new IllegalStateException("Event does not match the closed payroll: " + payroll.getId());
        }

        if (summaryRepository.existsByEventId(message.eventId())) {
            return;
        }
        if (summaryRepository.existsById(payroll.getId())) {
            throw new IllegalStateException("Payroll already has a summary for another event: " + payroll.getId());
        }

        var totals = payslipRepository.summarizeByPayrollId(payroll.getId());
        summaryRepository.save(new ClosedPayrollSummary(payroll.getId(), message.eventId(), payroll.getCompanyId(),
            payroll.getPeriod(), message.occurredAt(), totals.getPayslipCount(), totals.getTotalEarnings(),
            totals.getTotalDeductions(), totals.getNetPay()));
    }
}
