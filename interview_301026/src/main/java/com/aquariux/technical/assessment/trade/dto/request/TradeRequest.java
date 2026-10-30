package com.aquariux.technical.assessment.trade.dto.request;

import com.aquariux.technical.assessment.trade.enums.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TradeRequest {
    @NotNull(message = "User ID is required")
    @Schema(description = "User identifier initiating the trade", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;

    @NotNull(message = "Trade type is required")
    @Schema(description = "Buy or sell side of the trade", example = "BUY", allowableValues = {"BUY", "SELL"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private TradeType tradeType;

    @NotNull(message = "Trading pair is required")
    @Schema(description = "Trading pair symbol, such as BTCUSDT or ETHUSDT", example = "BTCUSDT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String pairName;

    @NotNull(message = "Trade quantity is required")
    @DecimalMin(value = "0.00000001", message = "Quantity must be greater than zero")
    @Schema(description = "Quantity to buy or sell", example = "0.25", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;
}