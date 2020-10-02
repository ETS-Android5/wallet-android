package com.mycelium.wapi.wallet.fio

import com.google.common.base.Optional
import com.mrd.bitlib.crypto.BipDerivationType
import com.mrd.bitlib.crypto.InMemoryPrivateKey
import com.mrd.bitlib.util.HexUtils
import com.mycelium.wapi.wallet.*
import com.mycelium.wapi.wallet.btc.FeePerKbFee
import com.mycelium.wapi.wallet.coins.Balance
import com.mycelium.wapi.wallet.coins.CryptoCurrency
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.exceptions.BuildTransactionException
import com.mycelium.wapi.wallet.fio.coins.FIOToken
import fiofoundation.io.fiosdk.FIOSDK
import fiofoundation.io.fiosdk.errors.FIOError
import fiofoundation.io.fiosdk.models.TokenPublicAddress
import fiofoundation.io.fiosdk.models.fionetworkprovider.FIOApiEndPoints
import fiofoundation.io.fiosdk.models.fionetworkprovider.FIORequestContent
import fiofoundation.io.fiosdk.models.fionetworkprovider.response.PushTransactionResponse
import fiofoundation.io.fiosdk.utilities.Utils
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class FioAccount(private val accountContext: FioAccountContext,
                 private val backing: FioAccountBacking,
                 private val accountListener: AccountListener?,
                 private val fiosdk: FIOSDK? = null,
                 address: FioAddress? = null) : WalletAccount<FioAddress>, ExportableAccount {
    private val logger: Logger = Logger.getLogger("asdaf")
    private val receivingAddress = fiosdk?.let { FioAddress(coinType, FioAddressData(it.publicKey)) }
            ?: address!!
    private val transactionService = FioTransactionHistoryService(accountContext.currency,
            receiveAddress.toString(), Utils.generateActor(receiveAddress.toString()))
    private val balanceService by lazy {
        FioBalanceService(coinType as FIOToken, receivingAddress.toString())
    }

    var registeredFIONames: MutableList<RegisteredFIOName> = accountContext.registeredFIONames?.toMutableList()
            ?: mutableListOf()

    var registeredFIODomains: MutableList<FIODomain> = accountContext.registeredFIODomains?.toMutableList()
            ?: mutableListOf()

    private fun addRegisteredAddress(address: RegisteredFIOName) {
        registeredFIONames.add(address)
        accountContext.registeredFIONames = registeredFIONames
    }

    private fun addRegisteredDomain(domain: FIODomain) {
        registeredFIODomains.add(domain)
        accountContext.registeredFIODomains = registeredFIODomains
    }

    @Volatile
    private var syncing = false

    val accountIndex: Int
        get() = accountContext.accountIndex

    fun hasHadActivity() = accountContext.actionSequenceNumber != BigInteger.ZERO

    /**
     * @return expiration date in format "yyyy-MM-dd'T'HH:mm:ss"
     */
    fun registerFIOAddress(fioAddress: String): String? =
            fiosdk!!.registerFioAddress(fioAddress, receivingAddress.toString(),
                    getFeeByEndpoint(FIOApiEndPoints.FeeEndPoint.RegisterFioAddress)).getActionTraceResponse()?.expiration?.also {
                addRegisteredAddress(RegisteredFIOName(fioAddress, convertToDate(it)))
            }

    /**
     * @return expiration date in format "yyyy-MM-dd'T'HH:mm:ss"
     */
    fun registerFIODomain(fioDomain: String): String? {
        return fiosdk!!.registerFioDomain(fioDomain, receivingAddress.toString(),
                getFeeByEndpoint(FIOApiEndPoints.FeeEndPoint.RegisterFioDomain)).getActionTraceResponse()?.expiration
    }

    @ExperimentalUnsignedTypes
    fun addPubAddress(fioAddress: String, publicAddresses: List<TokenPublicAddress>): Boolean {
        val actionTraceResponse = fiosdk!!.addPublicAddresses(fioAddress, publicAddresses, fiosdk.getFeeForAddPublicAddress(fioAddress).fee)
                .getActionTraceResponse()
        return actionTraceResponse != null && actionTraceResponse.status == "OK"
    }

    private fun getFioNames(): List<RegisteredFIOName> = try {
        FioTransactionHistoryService.getFioNames(coinType as FIOToken,
                receivingAddress.toString())?.fio_addresses?.map {
            RegisteredFIOName(it.fio_address, convertToDate(it.expiration))
        } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun getFioDomains(): List<FIODomain> = try {
        FioTransactionHistoryService.getFioNames(coinType as FIOToken,
                receivingAddress.toString())?.fio_domains?.map {
            FIODomain(it.fio_domain, convertToDate(it.expiration), it.isPublic != 0)
        } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun convertToDate(fioDateStr: String): Date {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("GMT")
        return sdf.parse(fioDateStr)
    }

    override fun setAllowZeroConfSpending(b: Boolean) {
        // TODO("Not yet implemented")
    }

    override fun createTx(address: Address, amount: Value, fee: Fee, data: TransactionData?): Transaction {
        if (amount > calculateMaxSpendableAmount((fee as FeePerKbFee).feePerKb, address as FioAddress)) {
            throw BuildTransactionException(Throwable("Invalid amount"))
        }

        return FioTransaction(coinType, address.toString(), amount, fee.feePerKb.value)
    }

    override fun signTx(request: Transaction?, keyCipher: KeyCipher?) {
    }

    override fun broadcastTx(tx: Transaction?): BroadcastResult {
        val fioTx = tx as FioTransaction
        return try {
            val response = fiosdk!!.transferTokens(fioTx.toAddress, fioTx.value.value, fioTx.fee)
            val actionTraceResponse = response.getActionTraceResponse()
            if (actionTraceResponse != null && actionTraceResponse.status == "OK") {
                BroadcastResult(BroadcastResultType.SUCCESS)
            } else {
                BroadcastResult("Status: ${actionTraceResponse?.status}", BroadcastResultType.REJECT_INVALID_TX_PARAMS)
            }
        } catch (e: FIOError) {
            e.printStackTrace()
            BroadcastResult(e.toJson(), BroadcastResultType.REJECT_INVALID_TX_PARAMS)
        } catch (e: Exception) {
            e.printStackTrace()
            BroadcastResult(e.message, BroadcastResultType.REJECT_INVALID_TX_PARAMS)
        }
    }

    override fun getReceiveAddress(): Address = receivingAddress

    override fun getCoinType(): CryptoCurrency = accountContext.currency

    override fun getBasedOnCoinType(): CryptoCurrency = coinType

    override fun getAccountBalance(): Balance = accountContext.balance

    override fun isMineAddress(address: Address?): Boolean = address == receiveAddress

    override fun isExchangeable(): Boolean = true

    override fun getTx(transactionId: ByteArray?): Transaction {
        TODO("Not yet implemented")
    }

    override fun getTxSummary(transactionId: ByteArray?): TransactionSummary =
            backing.getTransactionSummary(HexUtils.toHex(transactionId), receiveAddress.toString())!!

    fun getRequestsGroups() :List<FioGroup> = backing.getRequestsGroups()


    fun rejectFunds(fioRequestId: BigInteger, maxFee: BigInteger): PushTransactionResponse {
        return fiosdk!!.rejectFundsRequest(fioRequestId, maxFee)
    }

    fun requestFunds(
            payerFioAddress: String, payeeFioAddress: String,
            payeeTokenPublicAddress: String, amount: Double, chainCode: String, tokenCode: String,
            maxFee: BigInteger, technologyPartnerId: String = ""
    ): PushTransactionResponse {
        return fiosdk!!.requestFunds(payerFioAddress, payeeFioAddress, payeeTokenPublicAddress, amount, chainCode, tokenCode, maxFee, technologyPartnerId)
    }

    override fun getTransactionSummaries(offset: Int, limit: Int) =
            backing.getTransactionSummaries(offset.toLong(), limit.toLong())

    override fun getTransactionsSince(receivingSince: Long): MutableList<TransactionSummary> {
        return mutableListOf()
    }

    override fun getUnspentOutputViewModels(): MutableList<OutputViewModel> {
        return mutableListOf()
    }

    override fun getLabel(): String = accountContext.accountName

    override fun setLabel(label: String?) {
        label?.let {
            accountContext.accountName = it
        }
    }

    override fun isSpendingUnconfirmed(tx: Transaction?): Boolean = false

    override fun synchronize(mode: SyncMode?): Boolean {
        syncing = true
        syncFioRequests()
        syncFioAddresses()
        syncFioDomains()
        updateBlockHeight()
        syncTransactions()
        try {
            val fioBalance = fiosdk?.getFioBalance()?.balance ?: balanceService.getBalance()
            val newBalance = Balance(Value.valueOf(coinType, fioBalance),
                    Value.zeroValue(coinType), Value.zeroValue(coinType), Value.zeroValue(coinType))
            if (newBalance != accountContext.balance) {
                accountContext.balance = newBalance
                accountListener?.balanceUpdated(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logger.log(Level.INFO, "update balance exception: ${e.message}")
        }
        syncing = false
        return true
    }

    var requests: List<FIORequestContent> ? =null
    private fun syncFioRequests() {
        try {
            val sentFioRequests = fiosdk?.getSentFioRequests() ?: emptyList()
            val pendingFioRequests = fiosdk?.getPendingFioRequests() ?: emptyList()
            requests = sentFioRequests + pendingFioRequests
        }catch (ex:Throwable){
            requests = emptyList<FIORequestContent>()
        }


//        backing.putRequests("sent", sentFioRequests)
//        backing.putRequests("pending", pendingFioRequests)
    }

    private fun syncFioAddresses() {
        val fioNames = getFioNames()
        fioNames.forEach {
            if (it !in registeredFIONames) {
                addRegisteredAddress(it)
            }
        }
    }

    private fun syncFioDomains() {
        val fioDomains = getFioDomains()
        fioDomains.forEach {
            if (it !in registeredFIODomains) {
                addRegisteredDomain(it)
            }
        }
    }

    private fun updateBlockHeight() {
        accountContext.blockHeight = transactionService.getLatestBlock()?.toInt()
                ?: accountContext.blockHeight
    }

    private fun syncTransactions() {
        transactionService.getTransactions(accountContext.blockHeight.toBigInteger()).forEach {
            try {
                backing.putTransaction(it.blockNumber.toInt(), it.timestamp, it.txid, "",
                        it.fromAddress, it.toAddress, it.sum,
                        kotlin.math.max(accountContext.blockHeight - it.blockNumber.toInt(), 0),
                        it.fee, it.transferred, it.memo)
            } catch (e: Exception) {
                e.printStackTrace()
                logger.log(Level.INFO, "asdaf syncTransactions exception: ${e.message}")
            }
        }
        accountContext.actionSequenceNumber = transactionService.lastActionSequenceNumber
    }

    override fun getBlockChainHeight(): Int = accountContext.blockHeight

    override fun canSpend(): Boolean = fiosdk != null

    override fun canSign(): Boolean = false

    override fun isSyncing(): Boolean = syncing

    override fun isArchived(): Boolean = accountContext.archived

    override fun isActive(): Boolean = !isArchived

    override fun archiveAccount() {
        accountContext.archived = true
        dropCachedData()
    }

    override fun activateAccount() {
        accountContext.archived = false
        dropCachedData()
    }

    override fun dropCachedData() {
        accountContext.balance = Balance.getZeroBalance(coinType)
        accountContext.actionSequenceNumber = BigInteger.ZERO
    }

    override fun isVisible(): Boolean = true

    override fun isDerivedFromInternalMasterseed(): Boolean = fiosdk != null

    override fun getId(): UUID = accountContext.uuid

    override fun broadcastOutgoingTransactions(): Boolean = true

    override fun removeAllQueuedTransactions() {
    }

    override fun calculateMaxSpendableAmount(minerFeePerKilobyte: Value?, destinationAddress: FioAddress?): Value {
        val spendableWithFee = accountBalance.spendable - (minerFeePerKilobyte
                ?: Value.zeroValue(coinType))
        return if (spendableWithFee.isNegative()) Value.zeroValue(coinType) else spendableWithFee
    }

    override fun getSyncTotalRetrievedTransactions(): Int {
        return 0
    }

    override fun getTypicalEstimatedTransactionSize(): Int {
        return 0
    }

    override fun getPrivateKey(cipher: KeyCipher?): InMemoryPrivateKey {
        TODO("Not yet implemented")
    }

    override fun getDummyAddress(): FioAddress = FioAddress(coinType, FioAddressData(""))

    override fun getDummyAddress(subType: String?): FioAddress = dummyAddress

    override fun getDependentAccounts(): MutableList<WalletAccount<Address>> {
        return mutableListOf()
    }

    override fun queueTransaction(transaction: Transaction) {
        TODO("Not yet implemented")
    }

    fun getTransferTokensFee() = fiosdk!!.getFee(FIOApiEndPoints.FeeEndPoint.TransferTokens).fee

    fun getFeeForFunds(payeeFioAddress: String) = fiosdk!!.getFeeForNewFundsRequest(payeeFioAddress)

    fun getFeeByEndpoint(endpoint: FIOApiEndPoints.FeeEndPoint) = fiosdk!!.getFee(endpoint).fee

    override fun getExportData(cipher: KeyCipher): ExportableAccount.Data =
            ExportableAccount.Data(Optional.fromNullable(fiosdk?.getPrivateKey()),
                    mutableMapOf<BipDerivationType, String>().apply {
                        this[BipDerivationType.BIP44] = fiosdk?.publicKey
                                ?: receivingAddress.toString()
                    })
}