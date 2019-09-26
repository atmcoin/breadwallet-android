package com.breadwallet.ui.wallet

import com.breadwallet.model.PriceChange
import com.breadwallet.model.PriceDataPoint
import com.breadwallet.presenter.entities.CryptoRequest
import java.math.BigDecimal

sealed class WalletScreenEvent {
    data class OnSyncProgressUpdated(
            val progress: Float,
            val syncThroughMillis: Long,
            val isSyncing: Boolean
    ) : WalletScreenEvent() {
        init {
            require(progress in 0.0..1.0) {
                "Sync progress must be in 0..1 but was $progress"
            }
        }
    }

    data class OnQueryChanged(val query: String) : WalletScreenEvent()

    data class OnCurrencyNameUpdated(val name: String) : WalletScreenEvent()
    data class OnBrdRewardsUpdated(val showing: Boolean) : WalletScreenEvent()
    data class OnBalanceUpdated(val balance: BigDecimal, val fiatBalance: BigDecimal) : WalletScreenEvent()
    data class OnFiatPricePerUpdated(val pricePerUnit: String, val priceChange: PriceChange) : WalletScreenEvent()
    data class OnTransactionsUpdated(
        val walletTransactions: List<WalletTransaction>
    ) : WalletScreenEvent() {
        override fun toString() = "OnTransactionsUpdated(walletTransactions=(size:${walletTransactions.size}))"
    }
    data class OnTransactionAdded(val walletTransaction: WalletTransaction) : WalletScreenEvent()
    data class OnTransactionRemoved(val walletTransaction: WalletTransaction) : WalletScreenEvent()
    data class OnTransactionUpdated(val walletTransaction: WalletTransaction) : WalletScreenEvent()
    data class OnConnectionUpdated(val isConnected: Boolean) : WalletScreenEvent()

    object OnFilterSentClicked : WalletScreenEvent()
    object OnFilterReceivedClicked : WalletScreenEvent()
    object OnFilterPendingClicked : WalletScreenEvent()
    object OnFilterCompleteClicked : WalletScreenEvent()

    object OnSearchClicked : WalletScreenEvent()
    object OnSearchDismissClicked : WalletScreenEvent()
    object OnBackClicked : WalletScreenEvent()

    object OnChangeDisplayCurrencyClicked : WalletScreenEvent()

    object OnSendClicked : WalletScreenEvent()
    data class OnSendRequestGiven(val cryptoRequest: CryptoRequest) : WalletScreenEvent()
    object OnReceiveClicked : WalletScreenEvent()

    data class OnTransactionClicked(val txHash: String) : WalletScreenEvent()

    object OnBrdRewardsClicked : WalletScreenEvent()

    object OnShowReviewPrompt : WalletScreenEvent()
    object OnIsShowingReviewPrompt : WalletScreenEvent()
    data class OnHideReviewPrompt(val isDismissed: Boolean) : WalletScreenEvent()
    object OnReviewPromptAccepted : WalletScreenEvent()

    data class OnIsCryptoPreferredLoaded(val isCryptoPreferred: Boolean) : WalletScreenEvent()

    data class OnChartIntervalSelected(val interval: Interval) : WalletScreenEvent()
    data class OnMarketChartDataUpdated(
        val priceDataPoints: List<PriceDataPoint>
    ) : WalletScreenEvent() {
        override fun toString() = "OnMarketChartDataUpdated(priceDataPoints=(size:${priceDataPoints.size}))"
    }
    data class OnChartDataPointSelected(val priceDataPoint: PriceDataPoint) : WalletScreenEvent()
    object OnChartDataPointReleased : WalletScreenEvent()
}

