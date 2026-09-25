# TASK.md — Backlog Modularizado do Projeto TomaAí

> **Stack:** Kotlin + Jetpack Compose + Firebase (Auth, Firestore, FCM) + Cloud Functions (Asaas)  
> **Estratégia de Execução:** Tarefas quebradas em escopos reduzidos para otimizar consumo de tokens e viabilizar revisões contínuas por commit.

---

## 🔐 Módulo 1: Autenticação & Perfil de Usuário
- [x] **T1.1 — Configuração Base do Firebase Auth & Model `Usuario`**
  - *Aceite:* Model Kotlin `Usuario`, ViewModel e Repository de login/cadastro salvando no Firestore (`/usuarios/{uid}`).
  - *Status:* ✅ **Concluído**
    - Modelo `Usuario.kt` com suporte a Firestore (`id`, `nome`, `email`, `perfil`, `ativo`, `criadoEm`, `asaasCustomerId`, `responsavelPadraoId`).
    - Contrato `AuthRepository` e implementação `AuthRepositoryImpl` com login, cadastro, recuperação de senha, persistência em `/usuarios/{uid}` e tratamento de erros Firebase em PT-BR.
    - `AuthViewModel` e `AuthUiState` gerenciando estado reativo (`StateFlow`), sessão ativa e validações de formulário.
- [x] **T1.2 — Interface Compose de Login, Cadastro e Recuperação de Senha**
  - *Aceite:* Telas funcionais com tratamento de erros (ex: senha fraca, e-mail já cadastrado) e validação de campos.
  - *Status:* ✅ **Concluído**
    - `LoginScreen.kt`: Campos de e-mail e senha com toggle de visibilidade, validações, feedback de erro/carregamento e atalhos para cadastro e recuperação.
    - `CadastroScreen.kt`: Cadastro completo com validação de nome, formato de e-mail, força mínima da senha (6 caracteres) e confirmação de senha idêntica.
    - `RecuperarSenhaScreen.kt`: Fluxo de envio de e-mail de redefinição de senha com feedback de sucesso/erro.
    - `HomeScreen.kt`: Tela inicial logada exibindo dados do perfil sincronizados com o Firestore e botão de logout.
    - `NavGraph.kt` e `MainActivity.kt`: Navegação desacoplada via Navigation Compose com tema Material 3 médico/saúde (`TomaAiTheme`).
    - Testes unitários com `AuthViewModelTest.kt` validando todas as regras e fluxos com sucesso (`./gradlew testDebugUnitTest` e `./gradlew assembleDebug` aprovados).

---

## 💊 Módulo 2: Domínio de Medicamentos & Horários
- [x] **T2.1 — CRUD de Medicamentos (`MedicamentoRepository`)**
  - *Aceite:* Inserção, edição, listagem e desativação lógica (`ativo: false`) de medicamentos.
  - *Status:* ✅ **Concluído** — Coleção `/medicamentos` vinculada ao `usuarioId`, `MedicamentoRepositoryImpl`, `MedicamentoViewModel`, telas Compose (listagem + formulário) e navegação integrada a partir da `HomeScreen`.
- [x] **T2.2 — Configuração de Horários Recorrentes (`HorarioMedicamento`)**
  - *Aceite:* Associação 1:N entre medicamento e horários com definição de frequência e datas início/fim.
  - *Status:* ✅ **Concluído** — Horários embarcados no documento + `TimePicker` Material 3; frequência `DIARIA` / `PERSONALIZADA` (datas início/fim preparadas para a agenda).

---

## ⏰ Módulo 3: Controle de Doses & Histórico Completo
> **Objetivo:** Agenda diária a partir dos medicamentos; checklist Tomado / Pular / Atrasado; notificações locais/push nos horários.

- [x] **T3.1 — Geração e Gestão de Doses do Dia (`Dose`)**
  - *Aceite:* Tela inicial exibindo doses com status `PENDENTE`, `CONFIRMADA`, `IGNORADA` ou `NAO_CONFIRMADA`.
  - *Status:* ✅ **Concluído** — Agenda gerada na coleção `/doses` a partir dos medicamentos; `DosesDiaScreen` exibindo progresso diário.
- [x] **T3.2 — Confirmação de Dose pelo Usuário**
  - *Aceite:* Ação de clique para confirmar dose atualizando registro no Firestore com `confirmadaEm`.
  - *Status:* ✅ **Concluído** — Ações de Tomado, Pular e Atrasado funcionais; decremento automático no estoque do medicamento ao confirmar.
- [x] **T3.3 — Lembretes locais nos horários**
  - *Aceite:* Notificações nos horários cadastrados.
  - *Status:* ✅ **Concluído** — `AlarmManager` + `DoseReminderReceiver` configurados; suporte à permissão `POST_NOTIFICATIONS` (Android 13+ / API 33+).
- [ ] **T3.4 — Histórico Completo de Doses com Filtros**
  - *Aceite:* Tela de histórico permitindo filtrar por período de datas e por medicamento específico.
  - *Status:* ⏳ **Pendente**

---

## 👥 Módulo 4: Pessoa de Confiança & Alertas
> **Objetivo:** Cuidadores vinculados à conta; alertas se dose não confirmada após tempo limite.

- [ ] **T4.1 — Cadastro de Pessoa de Confiança (`PessoaConfianca`)**
  - *Aceite:* Tela de cadastro/listagem com flag `aceitouReceberAvisos` e atribuição de `responsavelPadraoId` no perfil do usuário (`/usuarios/{uid}`).
- [ ] **T4.2 — Lógica de Disparo de Alertas por Dose Não Confirmada**
  - *Aceite:* Verificação de estouro de tempo limite após horário programado sem confirmação da dose e gravação do registro na coleção `/alertas`.
- [ ] **T4.3 — Camada Abstrata para Envio de Notificações (SMS / E-mail / Push)**
  - *Aceite:* Interface Service com implementação stub/mock e Cloud Functions + FCM preparada para notificações ao responsável sem dependência direta de provedores pagos em ambiente dev.

---

## 💳 Módulo 5: Planos, Assinaturas & Integração Asaas
> **Objetivo:** Gateway Asaas; controle Free vs Premium/Pago (`asaasCustomerId` no usuário).

- [ ] **T5.1 — Modelagem dos Planos (Quinzenal e Mensal)**
  - *Aceite:* Coleção `/planos` com registros dos planos `QUINZENAL` (15 dias) e `MENSAL` (ciclo mensal).
- [ ] **T5.2 — Firebase Cloud Functions para Integração com Asaas API**
  - *Aceite:* Cloud Functions em TypeScript/JavaScript para: `criarClienteAsaas`, `criarAssinaturaAsaas` e `consultarCobrancas`.
- [ ] **T5.3 — Endpoint de Webhook Asaas & Idempotência**
  - *Aceite:* Function HTTP para receber webhooks do Asaas, validar token, salvar na coleção `/eventos_webhook` com idempotência e atualizar coleções `/assinaturas` e `/cobrancas`.
- [ ] **T5.4 — Telas Mobile de Seleção de Planos e Gestão da Assinatura/Cobranças**
  - *Aceite:* Visualização do plano atual, status da assinatura (`ATIVA`, `INADIMPLENTE`, `CANCELADA`) e telas de pagamento (Pix e Cartão de Crédito em ambiente Sandbox).

---

## 🛡️ Módulo 6: Testes, UI, Segurança & Documentação
> **Objetivo:** Validação completa dos fluxos, tratamento de erros, refinamento visual e entrega documentada.

- [ ] **T6.1 — Regras de Segurança do Cloud Firestore (Firestore Rules)**
  - *Aceite:* Garantir isolamento dos dados por `request.auth.uid`. Bloquear escrita direta no cliente do status de cobranças e assinaturas.
- [ ] **T6.2 — Testes Unitários e de Integração**
  - *Aceite:* Testes unitários para `ViewModels` e `Repositories` principais + testes das Cloud Functions.
- [ ] **T6.3 — Refinamento do Material 3, Acessibilidade e UX**
  - *Aceite:* Unificação de estados de carregamento (loading), tratamento visual de erros de rede/rede indisponível e componentes Material 3 otimizados.
- [ ] **T6.4 — Finalização do README.md e Diagramas Mermaid (DER e Arquitetura)**
  - *Aceite:* Documentação com instruções para execução, diagramas arquiteturais e guia para configuração do Sandbox Asaas.

---

## 🎯 Ordem Atual de Execução

1. ~~Módulo 1: Autenticação e Perfil~~ ✅
2. ~~Módulo 2: Medicamentos e Horários~~ ✅
3. Módulo 3: Controle de Doses
   - [x] T3.1, T3.2 e T3.3 ✅
   - [ ] T3.4 — Histórico Completo de Doses ⏳ *(Em andamento)*
4. **Módulo 4: Responsáveis de Confiança & Alertas** *(Próximo módulo)*
5. **Módulo 5: Planos e Assinatura Asaas**
6. **Módulo 6: Testes, Segurança Rules & Refatoração Geral**