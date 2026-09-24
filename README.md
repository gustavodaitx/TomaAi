# TomaAi

# Aplicativo de Gerenciamento de Assinaturas

## 1. Descrição do Projeto

O projeto consiste no desenvolvimento de um aplicativo Android para gerenciamento de clientes, planos, assinaturas e cobranças recorrentes.

A aplicação será desenvolvida utilizando Kotlin e Jetpack Compose, com Firebase como plataforma de autenticação, armazenamento e backend serverless. A integração com pagamentos será realizada por meio da API externa do Asaas.

O objetivo é oferecer uma interface para acompanhar os clientes e o ciclo de vida das assinaturas, mantendo os dados financeiros sincronizados com os eventos recebidos da plataforma de pagamentos.

## 2. Stack Tecnológica

| Tecnologia               | Finalidade                  |
| ------------------------ | --------------------------- |
| Kotlin                   | Desenvolvimento Android     |
| Jetpack Compose          | Interface de usuário        |
| MVVM                     | Organização arquitetural    |
| Firebase Authentication  | Autenticação                |
| Cloud Firestore          | Banco de dados              |
| Firebase Cloud Functions | Backend seguro              |
| Asaas API                | Pagamentos e assinaturas    |
| Webhooks Asaas           | Sincronização de eventos    |
| Firebase Cloud Messaging | Notificações, se necessário |

## 3. Arquitetura

O projeto utiliza uma arquitetura cliente-servidor com organização em camadas e backend serverless.

O aplicativo Android não acessa diretamente a API do Asaas. As operações financeiras são encaminhadas para o Firebase Cloud Functions, que executa as chamadas externas em ambiente de backend.

### Camadas

* Apresentação: Jetpack Compose.
* Estado: ViewModel e UiState.
* Dados: Repository.
* Backend: Firebase Cloud Functions.
* Persistência: Cloud Firestore.
* Integração externa: Asaas API.

## 4. Segurança

A chave privada da API do Asaas não deve ser armazenada no aplicativo Android.

As chamadas à API de pagamentos devem ser realizadas pelo backend. Os webhooks devem validar o token de autenticação configurado e utilizar o identificador do evento para evitar processamento duplicado.

## 5. Modelo de Dados

O banco de dados será implementado utilizando Cloud Firestore. O modelo lógico é representado por entidades relacionadas por identificadores.

Entidades principais:

* Usuario
* Cliente
* Plano
* Assinatura
* Cobranca
* EventoWebhook

## 6. Diagrama Entidade-Relacionamento

```mermaid
erDiagram
    USUARIO ||--o{ CLIENTE : administra
    USUARIO ||--o{ PLANO : cadastra
    CLIENTE ||--o{ ASSINATURA : contrata
    PLANO ||--o{ ASSINATURA : possui
    ASSINATURA ||--o{ COBRANCA : gera
    CLIENTE ||--o{ COBRANCA : recebe
    ASSINATURA ||--o{ EVENTO_WEBHOOK : recebe
    COBRANCA ||--o{ EVENTO_WEBHOOK : origina

    USUARIO {
        string id PK
        string nome
        string email
        string perfil
        boolean ativo
        timestamp criadoEm
    }

    CLIENTE {
        string id PK
        string usuarioId FK
        string asaasCustomerId
        string nome
        string cpfCnpj
        string email
        string telefone
        boolean ativo
    }

    PLANO {
        string id PK
        string usuarioId FK
        string nome
        string descricao
        number valor
        string ciclo
        boolean ativo
    }

    ASSINATURA {
        string id PK
        string clienteId FK
        string planoId FK
        string asaasSubscriptionId
        string status
        timestamp dataInicio
        timestamp dataFim
        timestamp proximaCobranca
    }

    COBRANCA {
        string id PK
        string assinaturaId FK
        string clienteId FK
        string asaasPaymentId
        number valor
        timestamp vencimento
        string status
        string formaPagamento
        timestamp pagoEm
    }

    EVENTO_WEBHOOK {
        string id PK
        string tipoEvento
        string asaasResourceId
        string assinaturaId FK
        string cobrancaId FK
        timestamp recebidoEm
        timestamp processadoEm
        string statusProcessamento
    }
```

## 7. Fluxo de Pagamento

1. O usuário realiza login.
2. O usuário seleciona um cliente.
3. O usuário escolhe um plano.
4. O aplicativo envia uma solicitação para uma Cloud Function.
5. A Cloud Function realiza a integração com o Asaas.
6. O Asaas cria a assinatura ou cobrança.
7. O Asaas envia eventos para o webhook.
8. A Cloud Function processa o evento.
9. O Firestore é atualizado.
10. O aplicativo exibe o status atualizado.

## 8. Considerações

A integração deverá ser desenvolvida inicialmente em ambiente Sandbox. Antes da utilização em produção, deverão ser realizados testes de autenticação, criação de clientes, criação de assinaturas, processamento de cobranças, cancelamentos, eventos duplicados e falhas de comunicação.
