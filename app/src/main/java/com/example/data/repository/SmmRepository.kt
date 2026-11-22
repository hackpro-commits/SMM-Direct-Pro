package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.api.JapApiService
import com.example.data.api.NowPaymentsApiService
import com.example.data.model.DepositRequest
import com.example.data.model.JapService
import com.example.data.model.LocalOrder
import com.example.data.model.NowPaymentRequest
import com.example.data.model.NowPaymentResponse
import com.example.data.model.UserSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "smm_settings")

class SmmRepository(private val context: Context) {

    private val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logger)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val japRetrofit = Retrofit.Builder()
        .baseUrl("https://justanotherpanel.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val nowPaymentsRetrofit = Retrofit.Builder()
        .baseUrl("https://api.nowpayments.io/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val japApi = japRetrofit.create(JapApiService::class.java)
    private val nowPaymentsApi = nowPaymentsRetrofit.create(NowPaymentsApiService::class.java)

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private object PreferencesKeys {
        val JAP_API_URL = stringPreferencesKey("jap_api_url")
        val JAP_API_KEY = stringPreferencesKey("jap_api_key")
        val NOWPAYMENTS_API_KEY = stringPreferencesKey("nowpayments_api_key")
        val BALANCE = stringPreferencesKey("balance")
        val IS_DEMO_MODE = stringPreferencesKey("is_demo_mode")
        val MARKUP_FACTOR = stringPreferencesKey("markup_factor")
        val IS_ADMIN = stringPreferencesKey("is_admin")
    }

    private val _userSettings = MutableStateFlow(UserSettings())
    val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    private val _orders = MutableStateFlow<List<LocalOrder>>(emptyList())
    val orders: StateFlow<List<LocalOrder>> = _orders.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            loadSettings()
            syncWithFirestore()
        }
        loadMockInitialOrders()
    }

    private suspend fun loadSettings() {
        val prefs = context.dataStore.data.first()
        val url = prefs[PreferencesKeys.JAP_API_URL] ?: "https://justanotherpanel.com/api/v2"
        val apiKey = prefs[PreferencesKeys.JAP_API_KEY] ?: ""
        val npKey = prefs[PreferencesKeys.NOWPAYMENTS_API_KEY] ?: ""
        val bal = prefs[PreferencesKeys.BALANCE]?.toDoubleOrNull() ?: 25.00
        val demo = prefs[PreferencesKeys.IS_DEMO_MODE]?.toBoolean() ?: true
        val markup = prefs[PreferencesKeys.MARKUP_FACTOR]?.toDoubleOrNull() ?: 2.0
        val admin = prefs[PreferencesKeys.IS_ADMIN]?.toBoolean() ?: false

        _userSettings.value = UserSettings(
            japApiUrl = url,
            japApiKey = apiKey,
            nowPaymentsApiKey = npKey,
            balance = bal,
            isDemoMode = demo || apiKey.isBlank(),
            markupFactor = markup,
            isAdmin = admin
        )
    }

    private suspend fun syncWithFirestore() {
        try {
            val user = auth.currentUser
            var isAdminUser = false

            if (user != null) {
                val email = user.email ?: ""
                val adminDoc = firestore.collection("admins").document(email).get().await()
                if (adminDoc.exists() && adminDoc.getBoolean("active") == true) {
                    isAdminUser = true
                } else {
                    val userDoc = firestore.collection("users").document(user.uid).get().await()
                    if (userDoc.exists()) {
                        isAdminUser = userDoc.getBoolean("isAdmin") ?: false
                        val cloudBalance = userDoc.getDouble("balance")
                        if (cloudBalance != null) {
                            updateBalance(cloudBalance)
                        }
                    } else {
                        val profile = mapOf(
                            "email" to email,
                            "balance" to _userSettings.value.balance,
                            "isAdmin" to false,
                            "updatedAt" to System.currentTimeMillis()
                        )
                        firestore.collection("users").document(user.uid).set(profile).await()
                    }
                }

                val ordersSnapshot = firestore.collection("users").document(user.uid)
                    .collection("orders")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get().await()

                if (!ordersSnapshot.isEmpty) {
                    val cloudOrders = ordersSnapshot.documents.mapNotNull { d ->
                        LocalOrder(
                            id = d.id,
                            orderId = d.getLong("orderId"),
                            serviceName = d.getString("serviceName") ?: "SMM Service",
                            link = d.getString("link") ?: "",
                            quantity = d.getLong("quantity")?.toInt() ?: 0,
                            charge = d.getString("charge") ?: "$0.00",
                            status = d.getString("status") ?: "Pending",
                            timestamp = d.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    _orders.value = cloudOrders
                }
            }

            _userSettings.value = _userSettings.value.copy(isAdmin = isAdminUser)

        } catch (e: Exception) {
            // Fallback
        }
    }

    private suspend fun fetchMarkupFactorFromFirestore(): Double {
        return try {
            val doc = firestore.collection("settings").document("pricing").get().await()
            doc.getDouble("markupFactor") ?: _userSettings.value.markupFactor
        } catch (e: Exception) {
            _userSettings.value.markupFactor
        }
    }

    suspend fun updateSettings(url: String, apiKey: String, npKey: String, isDemo: Boolean, markup: Double, isAdmin: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.JAP_API_URL] = url
            prefs[PreferencesKeys.JAP_API_KEY] = apiKey
            prefs[PreferencesKeys.NOWPAYMENTS_API_KEY] = npKey
            prefs[PreferencesKeys.IS_DEMO_MODE] = isDemo.toString()
            prefs[PreferencesKeys.MARKUP_FACTOR] = markup.toString()
            prefs[PreferencesKeys.IS_ADMIN] = isAdmin.toString()
        }
        _userSettings.value = _userSettings.value.copy(
            japApiUrl = url,
            japApiKey = apiKey,
            nowPaymentsApiKey = npKey,
            isDemoMode = isDemo || apiKey.isBlank(),
            markupFactor = markup,
            isAdmin = isAdmin
        )

        // Save markup factor dynamically to Firestore so frontend reads it from database
        try {
            val pricingMap = mapOf("markupFactor" to markup, "updatedAt" to System.currentTimeMillis())
            firestore.collection("settings").document("pricing").set(pricingMap).await()
        } catch (e: Exception) {
            // Ignore
        }
    }

    suspend fun updateBalance(newBalance: Double) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.BALANCE] = newBalance.toString()
        }
        _userSettings.value = _userSettings.value.copy(balance = newBalance)

        try {
            val user = auth.currentUser
            if (user != null) {
                firestore.collection("users").document(user.uid)
                    .update("balance", newBalance).await()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    suspend fun submitManualDeposit(amount: Double, method: String, reference: String): Result<Unit> {
        try {
            val user = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
            val depositId = "dep-${System.currentTimeMillis()}"
            val depositMap = mapOf(
                "id" to depositId,
                "userId" to user.uid,
                "userEmail" to (user.email ?: "user@gmail.com"),
                "amount" to amount,
                "paymentMethod" to method,
                "reference" to reference,
                "status" to "Pending",
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("deposits").document(depositId).set(depositMap).await()
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun fetchPendingDeposits(): List<DepositRequest> {
        return try {
            val snapshot = firestore.collection("deposits")
                .whereEqualTo("status", "Pending")
                .get().await()

            snapshot.documents.mapNotNull { d ->
                DepositRequest(
                    id = d.getString("id") ?: d.id,
                    userId = d.getString("userId") ?: "",
                    userEmail = d.getString("userEmail") ?: "",
                    amount = d.getDouble("amount") ?: 0.0,
                    paymentMethod = d.getString("paymentMethod") ?: "",
                    reference = d.getString("reference") ?: "",
                    status = d.getString("status") ?: "Pending",
                    timestamp = d.getLong("timestamp") ?: System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun approveDeposit(depositId: String, userId: String, amount: Double): Result<Unit> {
        try {
            firestore.collection("deposits").document(depositId)
                .update("status", "Approved").await()

            val userDoc = firestore.collection("users").document(userId).get().await()
            val currentBal = userDoc.getDouble("balance") ?: 0.0
            val newBal = currentBal + amount

            firestore.collection("users").document(userId)
                .update("balance", newBal).await()

            val currentUser = auth.currentUser
            if (currentUser != null && currentUser.uid == userId) {
                updateBalance(newBal)
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun loadMockInitialOrders() {
        _orders.value = listOf(
            LocalOrder(
                id = "ord-101",
                orderId = 98451,
                serviceName = "Instagram Followers [High Quality]",
                link = "https://instagram.com/p/sample",
                quantity = 1000,
                charge = "$3.00",
                status = "Completed",
                timestamp = System.currentTimeMillis() - 86400000
            ),
            LocalOrder(
                id = "ord-102",
                orderId = 98489,
                serviceName = "YouTube Views [Fast Delivery]",
                link = "https://youtube.com/watch?v=sample",
                quantity = 5000,
                charge = "$30.00",
                status = "In Progress",
                timestamp = System.currentTimeMillis() - 3600000
            )
        )
    }

    suspend fun fetchServices(): List<JapService> {
        val settings = _userSettings.value
        // Fetch dynamic markup factor from Firestore (not hardcoded in frontend)
        val markup = fetchMarkupFactorFromFirestore()

        val rawServices = if (settings.japApiKey.isBlank()) {
            getMockServices() // Fallback template if no API key configured yet
        } else {
            try {
                japApi.getServices(url = settings.japApiUrl, apiKey = settings.japApiKey)
            } catch (e: Exception) {
                getMockServices()
            }
        }

        return rawServices.map { service ->
            val baseRate = service.rate.toDoubleOrNull() ?: 1.0
            val markedUpRate = baseRate * markup
            service.copy(rate = String.format("%.2f", markedUpRate))
        }
    }

    suspend fun fetchBalance(): Double {
        val settings = _userSettings.value
        if (settings.isDemoMode || settings.japApiKey.isBlank()) {
            return settings.balance
        }
        return try {
            val res = japApi.getBalance(url = settings.japApiUrl, apiKey = settings.japApiKey)
            val bal = res.balance?.toDoubleOrNull() ?: settings.balance
            updateBalance(bal)
            bal
        } catch (e: Exception) {
            settings.balance
        }
    }

    suspend fun placeOrder(service: JapService, link: String, quantity: Int): Result<LocalOrder> {
        val settings = _userSettings.value
        val rate = service.rate.toDoubleOrNull() ?: 1.0
        val totalCharge = (rate * quantity) / 1000.0

        if (settings.balance < totalCharge && !settings.isDemoMode) {
            return Result.failure(Exception("Insufficient balance ($${String.format("%.2f", settings.balance)}). Please top up."))
        }

        val orderId: Long?
        val status: String

        if (settings.isDemoMode || settings.japApiKey.isBlank()) {
            orderId = (100000..999999).random().toLong()
            status = "Pending"
        } else {
            try {
                val res = japApi.addOrder(
                    url = settings.japApiUrl,
                    apiKey = settings.japApiKey,
                    serviceId = service.service,
                    link = link,
                    quantity = quantity
                )
                if (res.error != null) {
                    return Result.failure(Exception(res.error))
                }
                orderId = res.order
                status = "In Progress"
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }

        val newBalance = (settings.balance - totalCharge).coerceAtLeast(0.0)
        updateBalance(newBalance)

        val newOrder = LocalOrder(
            id = "ord-${System.currentTimeMillis()}",
            orderId = orderId,
            serviceName = service.name,
            link = link,
            quantity = quantity,
            charge = "$${String.format("%.2f", totalCharge)}",
            status = status,
            timestamp = System.currentTimeMillis()
        )

        _orders.value = listOf(newOrder) + _orders.value

        try {
            val user = auth.currentUser
            if (user != null) {
                val orderMap = mapOf(
                    "orderId" to (orderId ?: 0L),
                    "serviceName" to service.name,
                    "link" to link,
                    "quantity" to quantity,
                    "charge" to "$${String.format("%.2f", totalCharge)}",
                    "status" to status,
                    "timestamp" to newOrder.timestamp
                )
                firestore.collection("users").document(user.uid)
                    .collection("orders").document(newOrder.id).set(orderMap).await()
            }
        } catch (e: Exception) {
            // Ignore
        }

        return Result.success(newOrder)
    }

    suspend fun createCryptoDeposit(amountUsd: Double, payCurrency: String): Result<NowPaymentResponse> {
        val settings = _userSettings.value
        if (settings.nowPaymentsApiKey.isBlank()) {
            val simulatedResponse = NowPaymentResponse(
                paymentId = "np-${(10000..99999).random()}",
                paymentStatus = "waiting",
                payAddress = "0x71C...DemoCryptoAddressForTestingOnly",
                payAmount = amountUsd / 65000.0,
                payCurrency = payCurrency,
                error = null
            )
            return Result.success(simulatedResponse)
        }

        return try {
            val req = NowPaymentRequest(
                priceAmount = amountUsd,
                priceCurrency = "usd",
                payCurrency = payCurrency.lowercase(),
                orderId = "smm-topup-${System.currentTimeMillis()}",
                orderDescription = "SMM Direct Pro Balance Top-up"
            )
            val res = nowPaymentsApi.createPayment(apiKey = settings.nowPaymentsApiKey, request = req)
            if (res.error != null) {
                Result.failure(Exception(res.error))
            } else {
                Result.success(res)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun simulateSuccessfulDeposit(amountUsd: Double) {
        val settings = _userSettings.value
        val newBal = settings.balance + amountUsd
        updateBalance(newBal)
    }

    private fun getMockServices(): List<JapService> {
        return listOf(
            JapService("101", "Instagram Followers [High Quality]", "Default", "Instagram", "0.75", "10", "50000"),
            JapService("102", "Instagram Likes [Instant Delivery]", "Default", "Instagram", "0.25", "20", "100000"),
            JapService("103", "Instagram Reel Views [Super Fast]", "Default", "Instagram", "0.10", "100", "1000000"),
            JapService("201", "YouTube Views [Retention 90%]", "Default", "YouTube", "1.50", "50", "50000"),
            JapService("202", "YouTube Subscribers [Lifetime Guarantee]", "Default", "YouTube", "6.00", "10", "10000"),
            JapService("301", "TikTok Followers [HQ Profiles]", "Default", "TikTok", "1.25", "50", "20000"),
            JapService("302", "TikTok Video Likes & Saves", "Default", "TikTok", "0.45", "100", "50000"),
            JapService("401", "Telegram Channel Members [Active]", "Default", "Telegram", "1.00", "100", "100000"),
            JapService("501", "Twitter / X Post Retweets & Likes", "Default", "Twitter", "2.25", "20", "5000")
        )
    }
}
