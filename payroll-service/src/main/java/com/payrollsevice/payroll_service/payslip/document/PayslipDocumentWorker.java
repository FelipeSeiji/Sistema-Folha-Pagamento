package com.payrollsevice.payroll_service.payslip.document;

import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.payrollsevice.payroll_service.payslip.repository.PayslipRepository;
import com.payrollsevice.payroll_service.payslip.service.PayslipPdfService;
import com.payrollsevice.payroll_service.processing.model.PayrollStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PayslipDocumentWorker {
    private final PayslipRepository payslips;
    private final PayslipDocumentRepository documents;
    private final PayslipPdfService generator;

    @RabbitListener(queues = PayslipDocumentQueue.GENERATE)
    @Transactional
    public void generate(String job) {
        UUID payslipId = UUID.fromString(job);
        var payslip = payslips.findForUpdateById(payslipId)
            .orElseThrow(() -> new AmqpRejectAndDontRequeueException("Payslip not found: " + payslipId));
        if (payslip.getPayroll().getStatus() != PayrollStatus.CLOSED) {
            throw new AmqpRejectAndDontRequeueException("Payroll must be closed: " + payslipId);
        }
        if (documents.existsById(payslipId)) {
            return;
        }

        documents.save(new PayslipDocument(payslipId, generator.generate(payslipId)));
    }
}
