package com.aquariux.technical.assessment.trade.service.impl;

import com.aquariux.technical.assessment.trade.dto.request.TradeRequest;
import com.aquariux.technical.assessment.trade.dto.response.TradeResponse;
import com.aquariux.technical.assessment.trade.entity.CryptoPrice;
import com.aquariux.technical.assessment.trade.entity.Trade;
import com.aquariux.technical.assessment.trade.entity.UserWallet;
import com.aquariux.technical.assessment.trade.enums.TradeType;
import com.aquariux.technical.assessment.trade.mapper.CryptoPairMapper;
import com.aquariux.technical.assessment.trade.mapper.CryptoPriceMapper;
import com.aquariux.technical.assessment.trade.mapper.TradeMapper;
import com.aquariux.technical.assessment.trade.mapper.UserWalletMapper;
import com.aquariux.technical.assessment.trade.service.TradeServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeServiceInterface {

    private final TradeMapper tradeMapper;
    private final UserWalletMapper userWalletMapper;
    private final CryptoPairMapper cryptoPairMapper;
    private final CryptoPriceMapper cryptoPriceMapper;

    @Override
    @Transactional
    public TradeResponse executeTrade(TradeRequest tradeRequest) {
        validateRequest(tradeRequest);

        String normalizedPairName = normalizePairName(tradeRequest.getPairName());
        tradeRequest.setPairName(normalizedPairName);

        Long cryptoPairId = cryptoPairMapper.findIdByPairName(normalizedPairName);
        if (cryptoPairId == null) {
            throw new IllegalArgumentException("Unsupported trading pair: " + normalizedPairName);
        }

        CryptoPrice latestPrice = cryptoPriceMapper.findLatestByPairName(normalizedPairName);
        if (latestPrice == null) {
            throw new IllegalStateException("No market price available for pair: " + normalizedPairName);
        }

        BigDecimal quantity = tradeRequest.getQuantity();
        BigDecimal price = getExecutionPrice(tradeRequest.getTradeType(), latestPrice);
        BigDecimal totalAmount = quantity.multiply(price);

        String baseSymbol = normalizedPairName.replace("USDT", "");
        String quoteSymbol = "USDT";

        Long baseSymbolId = userWalletMapper.findSymbolIdBySymbol(baseSymbol);
        Long quoteSymbolId = userWalletMapper.findSymbolIdBySymbol(quoteSymbol);

        if (baseSymbolId == null || quoteSymbolId == null) {
            throw new IllegalArgumentException("Unable to resolve wallet symbols for pair: " + normalizedPairName);
        }

        UserWallet baseWallet = getOrCreateWallet(tradeRequest.getUserId(), baseSymbolId);
        UserWallet quoteWallet = getOrCreateWallet(tradeRequest.getUserId(), quoteSymbolId);

        if (tradeRequest.getTradeType() == TradeType.BUY) {
            ensureSufficientBalance(quoteWallet, totalAmount, quoteSymbol);
            updateWalletBalance(quoteWallet, quoteWallet.getBalance().subtract(totalAmount));
            updateWalletBalance(baseWallet, baseWallet.getBalance().add(quantity));
        } else {
            ensureSufficientBalance(baseWallet, quantity, baseSymbol);
            updateWalletBalance(baseWallet, baseWallet.getBalance().subtract(quantity));
            updateWalletBalance(quoteWallet, quoteWallet.getBalance().add(totalAmount));
        }

        Trade trade = new Trade();
        trade.setUserId(tradeRequest.getUserId());
        trade.setCryptoPairId(cryptoPairId);
        trade.setTradeType(tradeRequest.getTradeType().name());
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setTotalAmount(totalAmount);
        trade.setTradeTime(LocalDateTime.now());

        tradeMapper.insertTrade(trade);

        TradeResponse response = new TradeResponse();
        response.setTradeId(trade.getId());
        response.setUserId(tradeRequest.getUserId());
        response.setPairName(normalizedPairName);
        response.setTradeType(tradeRequest.getTradeType());
        response.setQuantity(quantity);
        response.setPrice(price);
        response.setTotalAmount(totalAmount);
        response.setTradeTime(trade.getTradeTime());

        UserWallet refreshedQuoteWallet = userWalletMapper.findByUserIdAndSymbolId(tradeRequest.getUserId(), quoteSymbolId);
        UserWallet refreshedBaseWallet = userWalletMapper.findByUserIdAndSymbolId(tradeRequest.getUserId(), baseSymbolId);
        response.setRemainingUsdtBalance(refreshedQuoteWallet != null ? refreshedQuoteWallet.getBalance() : BigDecimal.ZERO);
        response.setRemainingBaseBalance(refreshedBaseWallet != null ? refreshedBaseWallet.getBalance() : BigDecimal.ZERO);
        return response;
    }

    private String normalizePairName(String pairName) {
        if (pairName == null) {
            return null;
        }
        return pairName.trim().toUpperCase();
    }

    private UserWallet getOrCreateWallet(Long userId, Long symbolId) {
        UserWallet wallet = userWalletMapper.findByUserIdAndSymbolId(userId, symbolId);
        if (wallet == null) {
            wallet = new UserWallet();
            wallet.setUserId(userId);
            wallet.setSymbolId(symbolId);
            wallet.setBalance(BigDecimal.ZERO);
            wallet.setUpdatedAt(LocalDateTime.now());
            userWalletMapper.insertWallet(wallet);
        }
        return wallet;
    }

    private void validateRequest(TradeRequest tradeRequest) {
        if (tradeRequest == null) {
            throw new IllegalArgumentException("Trade request is required");
        }
        if (tradeRequest.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (tradeRequest.getTradeType() == null) {
            throw new IllegalArgumentException("Trade type is required");
        }
        if (tradeRequest.getPairName() == null || tradeRequest.getPairName().isBlank()) {
            throw new IllegalArgumentException("Trading pair is required");
        }
        if (tradeRequest.getQuantity() == null || tradeRequest.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Trade quantity must be greater than zero");
        }
    }

    private BigDecimal getExecutionPrice(TradeType tradeType, CryptoPrice latestPrice) {
        if (tradeType == TradeType.BUY) {
            return latestPrice.getAskPrice();
        }
        return latestPrice.getBidPrice();
    }


    private void ensureSufficientBalance(UserWallet wallet, BigDecimal requiredAmount, String symbol) {
        if (wallet == null || wallet.getBalance() == null || wallet.getBalance().compareTo(requiredAmount) < 0) {
            throw new IllegalArgumentException("Insufficient " + symbol + " balance for this trade");
        }
    }

    private void updateWalletBalance(UserWallet wallet, BigDecimal newBalance) {
        wallet.setBalance(newBalance);
        wallet.setUpdatedAt(LocalDateTime.now());
        userWalletMapper.updateWallet(wallet);
    }
}