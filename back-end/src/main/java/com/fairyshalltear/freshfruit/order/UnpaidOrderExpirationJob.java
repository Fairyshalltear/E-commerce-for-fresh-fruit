package com.fairyshalltear.freshfruit.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UnpaidOrderExpirationJob {
    private static final Logger log=LoggerFactory.getLogger(UnpaidOrderExpirationJob.class);
    private final OrderService orders;
    @Value("${freshfruit.order.unpaid-timeout-minutes:15}") private int timeoutMinutes;
    public UnpaidOrderExpirationJob(OrderService orders){this.orders=orders;}

    @Scheduled(fixedDelayString="${freshfruit.order.expiration-scan-ms:60000}")
    public void expire() {
        if(timeoutMinutes<1) {log.warn("Skipping unpaid-order expiration because timeout is invalid");return;}
        try {int count=orders.expireUnpaid(timeoutMinutes);if(count>0)log.info("Expired {} unpaid orders",count);}
        catch(RuntimeException exception){log.error("Unpaid-order expiration batch failed",exception);}
    }
}
