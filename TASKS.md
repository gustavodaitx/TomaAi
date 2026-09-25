# TASKS.md — Backlog Modularizado do Projeto TomaAí

> **Estratégia de Execução:** Tarefas quebradas em escopos reduzidos para otimizar consumo de tokens e viabilizar revisões contínuas por commit.

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

## 💊 Módulo 2: Domínio de Medicamentos & Horários
- [ ] **T2.1 — CRUD de Medicamentos (`MedicamentoRepository`)**
  - *Aceite:* Inserção, edição, listagem e desativação lógica (`ativo: false`) de medicamentos.
- [ ] **T2.2 — Configuração de Horários Recorrentes (`HorarioMedicamento`)**
  - *Aceite:* Associação 1:N entre medicamento e horários com definição de frequência e datas início/fim.

## ⏰ Módulo 3: Controle de Doses & Histórico Completo
- [ ] **T3.1 — Geração e Gestão de Doses do Dia (`Dose`)**
  - *Aceite:* Tela inicial exibindo doses com status `PENDENTE`, `CONFIRMADA`, `IGNORADA` ou `NAO_CONFIRMADA`.
- [ ] **T3.2 — Confirmação de Dose pelo Usuário**
  - *Aceite:* Ação de clique para confirmar dose atualizando registro no Firestore com `confirmadaEm`.
- [ ] **T3.3 — Histórico Completo de Doses com Filtros**
  - *Aceite:* Tela de histórico permitindo filtrar por período de datas e por medicamento.

## 👥 Módulo 4: Pessoa de Confiança & Alertas
- [ ] **T4.1 — Cadastro de Pessoa de Confiança (`PessoaConfianca`)**
  - *Aceite:* Tela de cadastro/listagem com flag `aceitouReceberAvisos` e atribuição de `responsavelPadraoId` no perfil do usuário.
- [ ] **T4.2 — Lógica de Disparo de Alertas por Dose Não Confirmada**
  - *Aceite:* Função no backend/App identificando atraso na confirmação e gerando registro na coleção `/alertas` com o `responsavelId` correto.
- [ ] **T4.3 — Camada Abstrata para Envio de Notificações (SMS / WhatsApp / E-mail)**
  - *Aceite:* Interface Service com implementação stub/mock preparada para integrações de envio (Twilio/SendGrid) sem simular falsos envios sem credenciais.

## 💳 Módulo 5: Planos, Assinaturas & Integração Asaas
- [ ] **T5.1 — Modelagem dos Planos (Quinzenal e Mensal)**
  - *Aceite:* Coleção `/planos` com registros dos planos `QUINZENAL` (15 dias) e `MENSAL` (ciclo mensal).
- [ ] **T5.2 — Firebase Cloud Functions para Integração com Asaas API**
  - *Aceite:* Functions em TypeScript para: `criarClienteAsaas`, `criarAssinaturaAsaas` e `consultarCobrancas`.
- [ ] **T5.3 — Endpoint de Webhook Asaas & Idempotência**
  - *Aceite:* Function HTTP para receber webhooks do Asaas, validar token, salvar na coleção `/eventos_webhook` e atualizar coleções `/assinaturas` e `/cobrancas`.
- [ ] **T5.4 — Telas Mobile de Seleção de Planos e Gestão da Assinatura/Cobranças**
  - *Aceite:* Visualização clara do plano atual, status da assinatura (`ATIVA`, `INADIMPLENTE`, `CANCELADA`) e lista de cobranças (métodos Pix e Cartão de Crédito).

## 🛡️ Módulo 6: Segurança, Banco de Dados & Documentação
- [ ] **T6.1 — Regras de Segurança do Cloud Firestore (Firestore Rules)**
  - *Aceite:* Garantir isolamento por `request.auth.uid`. Bloquear escrita direta do cliente no status das cobranças.
- [ ] **T6.2 — Testes Unitários e de Integração**
  - *Aceite:* Testes unitários para `ViewModels` e `Repositories` principais + testes das Cloud Functions.
- [ ] **T6.3 — Finalização do README.md e Diagramas Mermaid (DER e Arquitetura)**
  - *Aceite:* README completo com comandos de execução, diagramas e guia de configuração do Sandbox Asaas.