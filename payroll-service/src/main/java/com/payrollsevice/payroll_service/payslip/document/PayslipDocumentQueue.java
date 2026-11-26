package com.payrollsevice.payroll_service.payslip.document;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PayslipDocumentQueue {
    static final String GENERATE = "payroll.payslip.pdf.generate";
    static final String FAILED = "payroll.payslip.pdf.failed";

    @Bean
    Queue payslipDocumentJobs() {
        return QueueBuilder.durable(GENERATE).deadLetterExchange("").deadLetterRoutingKey(FAILED).build();
    }

    @Bean
    Queue failedPayslipDocumentJobs() {
        return QueueBuilder.durable(FAILED).build();
    }
}
