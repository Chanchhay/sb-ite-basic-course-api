package kh.edu.istad.ite.features.order.dto;

import kh.edu.istad.ite.shared.enums.OrderChannel;

import java.math.BigDecimal;
import java.time.LocalDate;


public record DailyChannelRevenue(
        LocalDate date,
        OrderChannel channel,
        BigDecimal revenue
) {
}
