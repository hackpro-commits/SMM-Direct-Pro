# SMM Direct Pro - Implementation Plan

An Android application for managing and ordering SMM services via the JustAnotherPanel (JAP) API, featuring NOWPayments cryptocurrency deposit gateway and Firebase Firestore for robust cloud data persistence (user balance, orders, and API settings).

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Confirmed App Name**: SMM Direct Pro
> - **Visual Aesthetic**: Modern Dark Mode with a crypto/fintech sleek vibe (deep charcoal backgrounds, electric indigo/cyan accents, crisp cards).
> - **Ordering & Catalog Flow**: Full service catalog with category tabs, search, quantity calculator, and instant crypto deposit via NOWPayments.
> - **Data & Config Storage**: Cloud-synced user account settings and local/cloud balance stored in Firebase Firestore, with secure runtime settings for JAP API Key and NOWPayments API Key.

- **Confirmed Decision 1**: Direct client-side integration with JAP API (`/api/v2`) and NOWPayments API using Retrofit/OkHttp and Kotlin Coroutines.
- **Confirmed Decision 2**: Firebase Auth (Google Sign-In via Credential Manager) + Firestore for secure user sessions, order tracking, and balance management without Cloud Functions.

## 1. Overview & Core Concept

- **What It Does**: Allows users to log in securely, view real-time SMM services from JustAnotherPanel, top up their account balance with crypto using NOWPayments, place orders, and track order execution status.
- **Target Audience**: SMM resellers, digital marketers, and social media managers looking for an efficient mobile management tool.
- **Key Value**: Fully native Android app with real-time API connectivity, zero reliance on external middleman servers, sleek dark fintech UI, and secure payment processing.

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Authentication**: Secure Google Sign-In or email profile registration stored in Firestore.
  2. **Dashboard**: Overview of wallet balance, quick stats, active orders shortcut, and quick deposit button.
  3. **Service Catalog**: Browse categories (Instagram Followers, YouTube Views, TikTok Likes, etc.), search by service name or ID, view rates per 1,000, and place orders with min/max validation.
  4. **Deposit & Payments (NOWPayments)**: Enter deposit amount (USD/crypto), select cryptocurrency (BTC, ETH, USDT, LTC), generate payment invoice with address/QR link, and check/confirm payment status to credit balance.
  5. **Orders History**: Real-time list of all user orders fetched from Firestore and synced with JAP status checks.
  6. **Settings / API Config**: Configure JAP API URL and API Key, NOWPayments API Key, and view account details.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Fintech dark mode (`#0F172A` deep navy/charcoal background, `#1E293B` card surfaces, `#38BDF8` cyan highlights, `#10B981` success emerald, `#6366F1` indigo primary accent).
  - *Typography*: Clean sans-serif hierarchy with bold headers and legible tabular numbers for pricing and quantities.

## 3. Key Product Decisions & Architecture

- **Decision 1**: Direct API Integration (No Cloud Functions)
  - *Chosen Approach*: App communicates directly with JAP API and NOWPayments API using Retrofit, storing API keys in secure user documents or BuildConfig/Settings.
  - *Why*: Simplicity, speed, and full compliance with client-side architecture requirements without requiring deployed server-side functions.
- **Decision 2**: Firebase Firestore for Persistence
  - *Chosen Approach*: Store user profiles, balances, and orders in Firestore collections (`users`, `orders`, `transactions`).
  - *Why*: Real-time updates, secure security rules, and persistence across devices.

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│                   SMM Direct Pro App                   │
├─────────────────┬──────────────────┬───────────────────┤
│  Jetpack Compose│  ViewModel & State│ Retrofit API      │
│  UI (Dark Theme)│  Management      │ (JAP & NOWPayments│
├─────────────────┴──────────────────┴───────────────────┤
│                     Firebase SDK                       │
│        (Firebase Auth & Firestore Cloud Database)      │
└────────────────────────────────────────────────────────┘
```

- **Data Models**:
  - `UserProfile`: `uid`, `email`, `displayName`, `balance`, `japApiKey`, `nowPaymentsApiKey`.
  - `JapService`: `service`, `name`, `type`, `rate`, `min`, `max`, `category`.
  - `Order`: `orderId`, `serviceId`, `link`, `quantity`, `charge`, `status`, `createdAt`, `userId`.
  - `PaymentInvoice`: `paymentId`, `paymentStatus`, `priceAmount`, `payCurrency`, `payAmount`, `payAddress`.
- **Interactive Component Mapping**:
  - Catalog search query -> Local filtering of fetched JAP services list.
  - Order placement -> Deduct balance in Firestore -> Call JAP `add` order endpoint -> Save order in Firestore.
  - Deposit creation -> Call NOWPayments API (`/v1/payment`) -> Display payment instructions -> Check status -> Update user balance in Firestore upon success.
