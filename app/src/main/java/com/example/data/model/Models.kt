package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class JapService(
    @Json(name = "service") val service: String,
    @Json(name = "name") val name: String,
    @Json(name = "type") val type: String?,
    @Json(name = "category") val category: String,
    @Json(name = "rate") val rate: String,
    @Json(name = "min") val min: String,
    @Json(name = "max") val max: String
)

@JsonClass(generateAdapter = true)
data class JapOrderResponse(
    @Json(name = "order") val order: Long?,
    @Json(name = "error") val error: String?
)

@JsonClass(generateAdapter = true)
data class JapBalanceResponse(
    @Json(name = "balance") val balance: String?,
    @Json(name = "currency") val currency: String?,
    @Json(name = "error") val error: String?
)

@JsonClass(generateAdapter = true)
data class JapOrderStatusResponse(
    @Json(name = "charge") val charge: String?,
    @Json(name = "start_count") val startCount: String?,
    @Json(name = "status") val status: String?,
    @Json(name = "remains") val remains: String?,
    @Json(name = "currency") val currency: String?,
    @Json(name = "error") val error: String?
)

@JsonClass(generateAdapter = true)
data class NowPaymentRequest(
    @Json(name = "price_amount") val priceAmount: Double,
    @Json(name = "price_currency") val priceCurrency: String = "usd",
    @Json(name = "pay_currency") val payCurrency: String,
    @Json(name = "order_id") val orderId: String,
    @Json(name = "order_description") val orderDescription: String
)

@JsonClass(generateAdapter = true)
data class NowPaymentResponse(
    @Json(name = "payment_id") val paymentId: String?,
    @Json(name = "payment_status") val paymentStatus: String?,
    @Json(name = "pay_address") val payAddress: String?,
    @Json(name = "pay_amount") val payAmount: Double?,
    @Json(name = "pay_currency") val payCurrency: String?,
    @Json(name = "error") val error: String?
)

data class LocalOrder(
    val id: String,
    val orderId: Long?,
    val serviceName: String,
    val link: String,
    val quantity: Int,
    val charge: String,
    val status: String,
    val timestamp: Long
)

data class DepositRequest(
    val id: String,
    val userId: String,
    val userEmail: String,
    val amount: Double,
    val paymentMethod: String,
    val reference: String,
    val status: String, // "Pending", "Approved", "Rejected"
    val timestamp: Long
)

data class UserSettings(
    val japApiUrl: String = "https://justanotherpanel.com/api/v2",
    val japApiKey: String = "",
    val nowPaymentsApiKey: String = "",
    val balance: Double = 25.00,
    val isDemoMode: Boolean = true,
    val markupFactor: Double = 2.0,
    val isAdmin: Boolean = false
)
