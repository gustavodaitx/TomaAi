# AGENTS.md — Projeto: TomaAí (Gestão de Medicamentos & Assinaturas)

## Stack Tecnológico
- **Mobile:** Kotlin, Android SDK, Jetpack Compose, Material3, Coroutines, Flow, Navigation Compose, ViewModel (MVVM).
- **Backend & Database:** Firebase Authentication, Cloud Firestore, Firebase Cloud Functions (TypeScript/Node.js), Firebase Cloud Messaging (FCM).
- **Pagamentos & Recorrência:** API Asaas Sandbox/Produção via Cloud Functions.

## Convenções de Código
- **Linguagem:** Kotlin (Android) e TypeScript (Cloud Functions).
- **Nomenclatura Kotlin:** `camelCase` para variáveis/funções; `PascalCase` para Composables e Classes; `UPPER_SNAKE_CASE` para Constantes e Enum values.
- **Nomenclatura Firestore:** Coleções em minúsculo no plural (`usuarios`, `medicamentos`, `doses`).
- **Padrões de Projeto:** MVVM + Repository Pattern. Respostas assíncronas encapsuladas em `Result<T>` ou `StateFlow<UiState>`.
- **Imports:** Não utilizar wildcards (`import package.*`).

## Estrutura de Pastas Principal
- **Android:** `app/src/main/java/br/com/tomai/` (`model`, `data/repository`, `data/remote`, `ui`, `viewmodel`).
- **Backend:** `functions/src/` (`asaas`, `webhooks`, `utils`, `index.ts`).

## Comandos Úteis
```bash
# Compilar projeto Android (Gradle wrapper)
./gradlew assembleDebug

# Executar testes unitários do Android
./gradlew testDebugUnitTest

# Emulador Local do Firebase (Cloud Functions + Firestore)
cd functions && npm run serve

# Deploy das Cloud Functions
firebase deploy --only functions