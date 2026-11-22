package com.example.data.api

import com.example.data.model.JapBalanceResponse
import com.example.data.model.JapOrderResponse
import com.example.data.model.JapOrderStatusResponse
import com.example.data.model.JapService
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface JapApiService {

    @GET
    suspend fun getServices(
        @Url url: String = "https://justanotherpanel.com/api/v2",
        @Query("action") action: String = "services",
        @Query("key") apiKey: String
    ): List<JapService>

    @GET
    suspend fun addOrder(
        @Url url: String = "https://justanotherpanel.com/api/v2",
        @Query("action") action: String = "add",
        @Query("key") apiKey: String,
        @Query("service") serviceId: String,
        @Query("link") link: String,
        @Query("quantity") quantity: Int
    ): JapOrderResponse

    @GET
    suspend fun getOrderStatus(
        @Url url: String = "https://justanotherpanel.com/api/v2",
        @Query("action") action: String = "status",
        @Query("key") apiKey: String,
        @Query("order") orderId: Long
    ): JapOrderStatusResponse

    @GET
    suspend fun getBalance(
        @Url url: String = "https://justanotherpanel.com/api/v2",
        @Query("action") action: String = "balance",
        @Query("key") apiKey: String
    ): JapBalanceResponse
}
