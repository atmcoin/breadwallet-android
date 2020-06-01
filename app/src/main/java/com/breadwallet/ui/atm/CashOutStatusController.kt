package com.breadwallet.ui.atm

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.Toast
import androidx.core.os.bundleOf
import cash.just.wac.WacSDK
import cash.just.wac.model.CashCodeStatusResponse
import cash.just.wac.model.CashStatus
import cash.just.wac.model.CodeStatus
import com.bluelinelabs.conductor.RouterTransaction
import com.breadwallet.R
import com.breadwallet.legacy.presenter.entities.CryptoRequest
import com.breadwallet.legacy.wallet.wallets.bitcoin.WalletBitcoinManager
import com.breadwallet.tools.qrcode.QRUtils
import com.breadwallet.ui.BaseController
import com.breadwallet.ui.platform.PlatformConfirmTransactionController
import com.breadwallet.ui.send.SendSheetController
import com.breadwallet.util.CryptoUriParser
import com.platform.PlatformTransactionBus
import kotlinx.android.synthetic.main.controller_receive.qr_image
import kotlinx.android.synthetic.main.fragment_request_cash_out_status.*
import kotlinx.android.synthetic.main.request_status_awaiting.*
import kotlinx.android.synthetic.main.request_status_funded.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.kodein.di.erased.instance
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.HttpURLConnection.HTTP_OK

class CashOutStatusController(args: Bundle) : BaseController(args) {

    constructor(status: CashStatus) : this(
        bundleOf(cashStatus to status)
    )

    constructor(code: String) : this(
        bundleOf(secureCode to code)
    )

    companion object {
        private const val cashStatus = "CashOutStatusController.Status"
        private const val secureCode = "CashOutStatusController.SecureCode"
        private const val clipboardLabel = "coinsquare_wallet"
    }

    override val layoutId = R.layout.fragment_request_cash_out_status
    private val cryptoUriParser by instance<CryptoUriParser>()
    private lateinit var clipboard: android.content.ClipboardManager

    enum class ViewState {
        LOADING,
        AWAITING,
        FUNDED
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(view: View) {
        super.onCreateView(view)
        changeUiState(ViewState.LOADING)

        clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

        handlePlatformMessages().launchIn(viewCreatedScope)

        val context = view.context
        val status:CashStatus? = argOptional(cashStatus)
        val code:String? = argOptional(secureCode)

        status?.let {
            if (CodeStatus.resolve(it.status) == CodeStatus.NEW_CODE) {
                populateAwaitingView(context, it.address, it.description, it.usdAmount, it.btc_amount)
            } else if (CodeStatus.resolve(it.status) == CodeStatus.FUNDED) {
                populateFundedView(context, it.code!!, it.usdAmount, it.description)
            }
        } ?: run {
            val safeCode = code ?: throw IllegalArgumentException("Missing arguments $cashStatus and $secureCode")
            WacSDK.checkCashCodeStatus(safeCode).enqueue(object: Callback<CashCodeStatusResponse> {
                override fun onResponse(call: Call<CashCodeStatusResponse>,
                    response: Response<CashCodeStatusResponse>) {
                    if (response.isSuccessful && response.code() == HTTP_OK) {

                        response.body()?.let { it ->
                            val cashStatus = it.data!!.items[0]

                            if (CodeStatus.resolve(cashStatus.status) == CodeStatus.NEW_CODE) {
                                populateAwaitingView(context, cashStatus.address, cashStatus.description,
                                    cashStatus.usdAmount, cashStatus.btc_amount)
                            } else if (CodeStatus.resolve(cashStatus.status) == CodeStatus.FUNDED) {
                                populateFundedView(context, cashStatus.code!!,
                                    cashStatus.usdAmount, cashStatus.description)
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<CashCodeStatusResponse>, t: Throwable) {
                    Toast.makeText(context.applicationContext, 
                        "Failed to load $safeCode status", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun changeUiState(state: ViewState){
        when (state) {
            ViewState.LOADING -> {
                loadingView.visibility = View.VISIBLE
                fundedCard.visibility = View.GONE
                awaitingCard.visibility = View.GONE
            }
            ViewState.AWAITING -> {
                loadingView.visibility = View.GONE
                fundedCard.visibility = View.GONE
                awaitingCard.visibility = View.VISIBLE
            }
            ViewState.FUNDED -> {
                loadingView.visibility = View.GONE
                fundedCard.visibility = View.GONE
                awaitingCard.visibility = View.GONE
            }
        }
    }
    private fun handlePlatformMessages() = PlatformTransactionBus.requests().onEach {
        withContext(Dispatchers.Main) {
            val transaction = RouterTransaction.with(PlatformConfirmTransactionController(it))
            router.pushController(transaction)
        }
    }

    private fun populateAwaitingView(context:Context, address:String,
        details:String, usdAmount:String, btcAmount:String) {

        changeUiState(ViewState.AWAITING)

        awaitingFundsTitle.setOnTouchListener(OnTouchListener { _, event ->
            val drawableRight = 2
            if (event.action == MotionEvent.ACTION_UP) {
                if (event.rawX >= awaitingFundsTitle.right
                    - awaitingFundsTitle.compoundDrawables[drawableRight].bounds.width()) {
                    goToSend(address, btcAmount)
                    return@OnTouchListener true
                }
            }
            false
        })

        awaitingAddress.text = address
        awaitingAddress.isSelected = true
        awaitingAddress.setOnClickListener {
            copyToClipboard(context, address)
        }
        awaitingBTCAmount.text = "Amount: $btcAmount BTC"
        awaitingBTCAmount.setOnClickListener {
            copyToClipboard(context, btcAmount)
        }

        awaitingLocationAddress.text = "Location: $details"

        awaitingLocationAddress.setOnClickListener {
            openMaps(context, details)
        }

        awaitingUSDAmount.text = "Amount (USD): $$usdAmount"

        qr_image.setOnClickListener {
            copyToClipboard(context, address)
        }

        val request = CryptoRequest.Builder()
            .setAddress(address)
            .setAmount(btcAmount.toFloat().toBigDecimal())
            .build()

        val uri = cryptoUriParser.createUrl("BTC", request)

        if (!QRUtils.generateQR(activity, uri.toString(), qr_image)) {
            error("failed to generate qr image for address")
        }
    }

    private fun goToSend(btc:String, address:String) {
        val builder = CryptoRequest.Builder()
        builder.address = address
        builder.amount = btc.toFloat().toBigDecimal()
        builder.currencyCode = WalletBitcoinManager.BITCOIN_CURRENCY_CODE
        val request = builder.build()
        router.pushController(RouterTransaction.with(
            SendSheetController(
            request //make it default
        )
        ))
    }

    private fun populateFundedView(context: Context, code:String, usdAmount:String, address:String){
        changeUiState(ViewState.FUNDED)

        cashCode.text = code
        cashCode.setOnClickListener {
            copyToClipboard(context, code)
        }
        amountFunded.text = "Amount (USD):  \$$usdAmount"
        locationFunded.text = "Location: $address"
        locationFunded.setOnClickListener {
            openMaps(context, address)
        }
    }

    private fun copyToClipboard(context:Context, data: String){
        val clip = ClipData.newPlainText(clipboardLabel, data)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to the clipboard!", Toast.LENGTH_SHORT).show()
    }

    private fun openMaps(context:Context, address:String) {
        val geoUri = "http://maps.google.com/maps?q=loc:$address"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
        context.startActivity(intent)
    }
}
