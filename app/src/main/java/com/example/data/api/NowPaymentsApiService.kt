package com.example.data.api

import com.example.data.model.NowPaymentRequest
import com.example.data.model.NowPaymentResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface NowPaymentsApiService {

    @POST("v1/payment")
    suspend fun createPayment(
        @Header("x-api-key") apiKey: String,
        @Body request: NowPaymentRequest
    ): NowPaymentResponse

    @GET("v1/payment/{payment_id}")
    suspend fun getPaymentStatus(
        @Header("x-api-key") apiKey: String,
        @Path("payment_id") paymentId: String
    ): NowPaymentResponse
}
