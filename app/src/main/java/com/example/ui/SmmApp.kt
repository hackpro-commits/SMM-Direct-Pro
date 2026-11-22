package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.data.model.JapService
import com.example.data.repository.SmmRepository
import com.example.ui.screens.*
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    object Services : Screen("services", "Services", Icons.Default.ShoppingCart)
    object Deposit : Screen("deposit", "Deposit", Icons.Default.AddCard)
    object Orders : Screen("orders", "Orders", Icons.Default.ListAlt)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmmApp(repository: SmmRepository) {
    var currentRoute by remember { mutableStateOf<String>(Screen.Dashboard.route) }

    val settings by repository.userSettings.collectAsState()
    val orders by repository.orders.collectAsState()

    var services by remember { mutableStateOf<List<JapService>>(emptyList()) }
    var isLoadingServices by remember { mutableStateOf(false) }
    var selectedServiceForOrder by remember { mutableStateOf<JapService?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Fetch services on launch or settings change
    LaunchedEffect(settings) {
        isLoadingServices = true
        services = repository.fetchServices()
        repository.fetchBalance()
        isLoadingServices = false
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val items = listOf(Screen.Dashboard, Screen.Services, Screen.Deposit, Screen.Orders, Screen.Settings)
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = { currentRoute = screen.route },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (currentRoute) {
                Screen.Dashboard.route -> DashboardScreen(
                    settings = settings,
                    orders = orders,
                    onNavigateToServices = { currentRoute = Screen.Services.route },
                    onNavigateToDeposit = { currentRoute = Screen.Deposit.route },
                    onNavigateToOrders = { currentRoute = Screen.Orders.route }
                )
                Screen.Services.route -> ServicesScreen(
                    services = services,
                    isLoading = isLoadingServices,
                    onSelectService = { service -> selectedServiceForOrder = service }
                )
                Screen.Deposit.route -> DepositScreen(
                    currentBalance = settings.balance,
                    onCreateInvoice = { amount, currency -> repository.createCryptoDeposit(amount, currency) },
                    onSimulateSuccess = { amount -> repository.simulateSuccessfulDeposit(amount) },
                    onSubmitManualDeposit = { amount, method, ref -> repository.submitManualDeposit(amount, method, ref) }
                )
                Screen.Orders.route -> OrdersScreen(orders = orders)
                Screen.Settings.route -> SettingsScreen(
                    settings = settings,
                    onSaveSettings = { url, apiKey, npKey, isDemo, markup, isAdmin ->
                        repository.updateSettings(url, apiKey, npKey, isDemo, markup, isAdmin)
                        snackbarMessage = if (isAdmin) "Admin settings saved!" else "Switched to User Mode"
                    },
                    onFetchPendingDeposits = { repository.fetchPendingDeposits() },
                    onApproveDeposit = { depositId, userId, amount ->
                        val res = repository.approveDeposit(depositId, userId, amount)
                        snackbarMessage = if (res.isSuccess) "Deposit approved and credited!" else "Failed to approve deposit"
                        res
                    }
                )
            }

            selectedServiceForOrder?.let { service ->
                OrderDialog(
                    service = service,
                    currentBalance = settings.balance,
                    onDismiss = { selectedServiceForOrder = null },
                    onSubmit = { link, quantity ->
                        coroutineScope.launch {
                            val result = repository.placeOrder(service, link, quantity)
                            selectedServiceForOrder = null
                            result.fold(
                                onSuccess = { order ->
                                    snackbarMessage = "Order #${order.orderId ?: "Demo"} placed successfully!"
                                    currentRoute = Screen.Orders.route
                                },
                                onFailure = { err ->
                                    snackbarMessage = err.localizedMessage ?: "Failed to place order"
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}
