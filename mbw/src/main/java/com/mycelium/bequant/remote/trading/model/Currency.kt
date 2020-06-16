/**
* API
* Create API keys in your profile and use public API key as username and secret as password to authorize. 
*
* The version of the OpenAPI document: 2.19.0
* 
*
* NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
* https://openapi-generator.tech
* Do not edit the class manually.
*/
package com.mycelium.bequant.remote.trading.model


import com.squareup.moshi.Json
/**
 * 
 * @param id Currency code
 * @param fullName 
 * @param crypto True for cryptocurrencies, false for fiat, ICO and others.
 * @param payinEnabled True if cryptocurrency support generate adress or paymentId for deposits
 * @param payinPaymentId True if cryptocurrency requred use paymentId for deposits
 * @param payinConfirmations Confirmations count for cryptocurrency deposits
 * @param payoutEnabled 
 * @param payoutFee Default withdraw fee
 * @param payoutIsPaymentId True if cryptocurrency allow use paymentId for withdraw
 * @param delisted True if currency delisted (stopped deposit and trading)
 * @param transferEnabled 
 * @param payoutMinimalAmount Minimum withdraw amount
 * @param precisionPayout Currency precision for payout (number of digits after the decimal point)
 * @param precisionTransfer Currency precision for transfer (number of digits after the decimal point)
 */

data class Currency (
    /* Currency code */
    @Json(name = "id")
    val id: kotlin.String,
    @Json(name = "fullName")
    val fullName: kotlin.String,
    /* True for cryptocurrencies, false for fiat, ICO and others. */
    @Json(name = "crypto")
    val crypto: kotlin.Boolean,
    /* True if cryptocurrency support generate adress or paymentId for deposits */
    @Json(name = "payinEnabled")
    val payinEnabled: kotlin.Boolean,
    /* True if cryptocurrency requred use paymentId for deposits */
    @Json(name = "payinPaymentId")
    val payinPaymentId: kotlin.Boolean,
    /* Confirmations count for cryptocurrency deposits */
    @Json(name = "payinConfirmations")
    val payinConfirmations: kotlin.Int,
    @Json(name = "payoutEnabled")
    val payoutEnabled: kotlin.Boolean,
    /* Default withdraw fee */
    @Json(name = "payoutFee")
    val payoutFee: kotlin.String,
    /* True if cryptocurrency allow use paymentId for withdraw */
    @Json(name = "payoutIsPaymentId")
    val payoutIsPaymentId: kotlin.Boolean,
    /* True if currency delisted (stopped deposit and trading) */
    @Json(name = "delisted")
    val delisted: kotlin.Boolean,
    @Json(name = "transferEnabled")
    val transferEnabled: kotlin.Boolean,
    /* Minimum withdraw amount */
    @Json(name = "payoutMinimalAmount")
    val payoutMinimalAmount: kotlin.String,
    /* Currency precision for payout (number of digits after the decimal point) */
    @Json(name = "precisionPayout")
    val precisionPayout: kotlin.Int,
    /* Currency precision for transfer (number of digits after the decimal point) */
    @Json(name = "precisionTransfer")
    val precisionTransfer: kotlin.Int
)

