package com.payrollsevice.payroll_service.payslip.document;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.payrollsevice.payroll_service.payslip.repository.PayslipRepository;
import com.payrollsevice.payroll_service.processing.model.PayrollStatus;
import com.payrollsevice.payroll_service.processing.repository.PayrollRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PayslipDocumentDispatcher {
    private final PayrollRepository payrolls;
    private final PayslipRepository payslips;
    private final RabbitTemplate rabbit;

    public void enqueue(UUID payrollId) {
        var payroll = payrolls.findById(payrollId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll not found."));
        if (payroll.getStatus() != PayrollStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payroll must be closed to request payslip PDFs.");
        }

        for (UUID payslipId : payslips.findIdsByPayrollId(payrollId)) {
            send(payslipId);
        }
    }

    private void send(UUID payslipId) {
        CorrelationData correlation = new CorrelationData();
        try {
            rabbit.convertAndSend("", PayslipDocumentQueue.GENERATE, payslipId.toString(), correlation);
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw unavailable(null);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        } catch (AmqpException | ExecutionException | TimeoutException exception) {
            throw unavailable(exception);
        }
    }

    private static ResponseStatusException unavailable(Exception cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not enqueue payslip PDF jobs.", cause);
    }
}
