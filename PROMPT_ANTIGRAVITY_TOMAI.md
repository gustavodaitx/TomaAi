
# PROMPT DE DESENVOLVIMENTO — APLICATIVO TomaAí

## 1. PAPEL E OBJETIVO

Você é um desenvolvedor de software Android sênior, especialista em Kotlin, Jetpack Compose, Firebase, arquitetura MVVM, desenvolvimento de APIs e integração segura com gateways de pagamento.

Seu objetivo é desenvolver um aplicativo Android chamado **TomaAí**, destinado ao gerenciamento de medicamentos, horários, lembretes, confirmação de doses e acompanhamento por uma pessoa de confiança.

O projeto também deverá contemplar um modelo de assinatura com dois planos:

- Plano Quinzenal: recorrência prevista a cada 15 dias.
- Plano Mensal: recorrência mensal.

A integração de pagamentos deverá utilizar a API externa do Asaas, por meio de um backend seguro implementado com Firebase Cloud Functions.

O projeto deverá ser organizado, funcional, didático e adequado para apresentação acadêmica em um curso de Análise e Desenvolvimento de Sistemas.

IMPORTANTE:
- Não expor credenciais privadas do Asaas no aplicativo Android.
- Não implementar chamadas sensíveis da API do Asaas diretamente no APK.
- Utilizar Firebase Cloud Functions para operações financeiras.
- Não inventar endpoints do Asaas.
- Consultar a documentação oficial do Asaas para validar os endpoints, parâmetros e recursos suportados.
- Se alguma integração não puder ser executada sem credenciais, implementar uma estrutura preparada para configuração posterior e documentar o procedimento.
- Não afirmar que uma operação financeira foi concluída quando ela não foi realmente confirmada pela API ou pelo webhook.

---

# 2. CONTEXTO DO PROJETO

O TomaAí foi originalmente idealizado como uma solução para auxiliar pessoas que apresentam dificuldades para lembrar os horários dos medicamentos, confirmar se uma dose já foi tomada e manter uma rotina adequada.

As funcionalidades identificadas no projeto acadêmico incluem:

- Cadastro de usuário.
- Cadastro de medicamentos.
- Configuração de horários.
- Lembretes.
- Confirmação de doses.
- Histórico de doses.
- Cadastro de pessoa de confiança.
- Aviso quando uma dose não for confirmada.

A nova versão deverá preservar esse contexto e acrescentar um modelo de assinatura para acesso aos recursos do aplicativo.

O aplicativo deve ter uma interface simples, clara, objetiva e acessível para usuários com diferentes níveis de familiaridade tecnológica.

---

# 3. ESCOPO FUNCIONAL

## 3.1 Autenticação

Implementar:

- Criar conta.
- Login com e-mail e senha.
- Logout.
- Recuperação de senha.
- Persistência da sessão.
- Identificação do usuário autenticado.
- Proteção das telas que exigem autenticação.

Tecnologia:

- Firebase Authentication.

Regras:

- O usuário autenticado deve acessar somente seus próprios dados.
- Não permitir que um usuário visualize medicamentos, doses, alertas ou dados privados de outro usuário.
- Tratar erros de autenticação de forma clara na interface.

---

## 3.2 Gerenciamento de medicamentos

Permitir que o usuário:

- Cadastre um medicamento.
- Informe o nome.
- Informe a dosagem, quando aplicável.
- Cadastre instruções.
- Edite um medicamento.
- Desative um medicamento.
- Consulte a lista de medicamentos ativos.
- Visualize os horários relacionados ao medicamento.

Campos sugeridos:

- id
- usuarioId
- nome
- dosagem
- instrucoes
- ativo
- criadoEm

Não realizar recomendações médicas, alteração de prescrições ou decisões clínicas.

O aplicativo deve funcionar como uma ferramenta de organização e lembrete, não como substituto de orientação médica.

---

## 3.3 Configuração de horários

Permitir:

- Cadastro de um ou mais horários para cada medicamento.
- Definição da frequência.
- Definição da data de início.
- Definição opcional da data de término.
- Edição e desativação de horários.

Campos sugeridos:

- id
- medicamentoId
- horario
- frequencia
- dataInicio
- dataFim
- ativo

A estrutura deve permitir que um medicamento possua vários horários.

---

## 3.4 Controle de doses

Cada ocorrência prevista de um medicamento deverá ser representada por uma dose.

Permitir:

- Visualizar doses previstas.
- Confirmar uma dose.
- Registrar data e hora da confirmação.
- Consultar doses pendentes.
- Registrar doses não confirmadas, conforme a regra de negócio.
- Consultar o histórico.

Campos sugeridos:

- id
- usuarioId
- medicamentoId
- horarioMedicamentoId
- dataHoraPrevista
- status
- confirmadaEm
- criadoEm

Status sugeridos:

- PENDENTE
- CONFIRMADA
- IGNORADA
- NAO_CONFIRMADA

Não considerar automaticamente uma dose como tomada. A confirmação deve ser realizada pelo usuário.

---

# 4. PESSOA DE CONFIANÇA

## 4.1 Objetivo

Permitir que o usuário cadastre uma pessoa de confiança para receber um aviso quando uma dose não for confirmada.

A pessoa de confiança deverá possuir uma entidade própria no banco de dados, com identificador único.

## 4.2 Campos da entidade PESSOA_CONFIANCA

- id: identificador único do responsável.
- usuarioId: identificador do usuário que cadastrou o responsável.
- nome.
- email, opcional.
- telefone, opcional.
- parentesco, opcional.
- aceitouReceberAvisos.
- ativo.
- criadoEm.

## 4.3 Identificação do responsável

O identificador do responsável será:

PESSOA_CONFIANCA.id

O usuário deverá possuir um campo opcional:

USUARIO.responsavelPadraoId

Esse campo referencia o ID do responsável padrão.

A entidade ALERTA deverá possuir:

ALERTA.responsavelId

Esse campo deverá indicar qual responsável foi selecionado para receber determinado aviso.

IMPORTANTE:

Não utilizar somente o nome ou telefone como referência do responsável.

Utilizar IDs para estabelecer os relacionamentos.

## 4.4 Regras

- O usuário pode cadastrar uma ou mais pessoas de confiança.
- O usuário pode selecionar um responsável padrão.
- O usuário pode editar ou desativar um responsável.
- O sistema deve verificar se o responsável está ativo.
- O sistema deve verificar se existe autorização para recebimento de avisos.
- O alerta deve preservar o responsável associado no momento do envio.
- A alteração do responsável padrão não deve modificar o histórico de alertas já gerados.

## 4.5 Entidade ALERTA

Campos:

- id.
- usuarioId.
- doseId.
- responsavelId.
- tipo.
- status.
- enviadoEm.
- criadoEm.

Tipos sugeridos:

- LEMBRETE_DOSE.
- DOSE_NAO_CONFIRMADA.

Status sugeridos:

- PENDENTE.
- ENVIADO.
- ENTREGUE.
- ERRO.

Implementar a estrutura para alertas de dose não confirmada.

Se o envio real de SMS, e-mail ou WhatsApp depender de serviços adicionais, não simular um envio como se tivesse ocorrido. Criar uma camada de serviço preparada para integração e documentar as dependências necessárias.

---

# 5. PLANOS DE ASSINATURA

## 5.1 Tipos de plano

O aplicativo deverá trabalhar inicialmente com dois ciclos:

### Plano Quinzenal

- Código: QUINZENAL.
- Periodicidade de negócio: a cada 15 dias.
- Campo diasRecorrencia: 15.

### Plano Mensal

- Código: MENSAL.
- Periodicidade de negócio: mensal.
- Campo ciclo: MENSAL.

A implementação efetiva dos ciclos no Asaas deverá ser validada na documentação oficial da API.

Não presumir que o Asaas aceita todos os ciclos utilizando o mesmo parâmetro ou que uma recorrência de 15 dias equivale necessariamente a um ciclo mensal.

## 5.2 Entidade PLANO

Campos:

- id.
- usuarioId, quando aplicável.
- nome.
- descricao.
- valor.
- ciclo.
- diasRecorrencia.
- ativo.
- criadoEm.

O valor deve ser armazenado em formato numérico apropriado, evitando cálculos financeiros com imprecisão de ponto flutuante.

Se a API exigir um formato específico para valores monetários, realizar a conversão no backend conforme a documentação.

## 5.3 Entidade ASSINATURA

Campos:

- id.
- usuarioId.
- planoId.
- asaasSubscriptionId.
- ciclo.
- status.
- dataInicio.
- dataFim.
- proximaCobranca.
- criadoEm.

Status sugeridos:

- PENDENTE.
- ATIVA.
- INADIMPLENTE.
- CANCELADA.
- ENCERRADA.

A assinatura deverá estar relacionada a um plano e a um usuário.

O campo asaasSubscriptionId deverá armazenar o identificador externo da assinatura quando a criação tiver sido confirmada pelo Asaas.

---

# 6. COBRANÇAS E PAGAMENTOS

## 6.1 Entidade COBRANCA

Campos:

- id.
- assinaturaId.
- usuarioId.
- asaasPaymentId.
- valor.
- vencimento.
- status.
- formaPagamento.
- pagoEm.
- criadoEm.

Status sugeridos:

- PENDENTE.
- RECEBIDA.
- VENCIDA.
- CANCELADA.
- ESTORNADA, quando aplicável ao fluxo implementado.

Os valores e status deverão ser mapeados de acordo com a resposta real da API do Asaas.

Não criar uma tabela ou registro de cobrança como pago apenas porque o usuário iniciou o processo de contratação.

---

# 7. EVENTOS DE WEBHOOK

## 7.1 Objetivo

Receber e processar notificações enviadas pelo Asaas sobre alterações em pagamentos e assinaturas.

## 7.2 Entidade EVENTO_WEBHOOK

Campos:

- id.
- tipoEvento.
- asaasResourceId.
- assinaturaId, opcional.
- cobrancaId, opcional.
- statusProcessamento.
- recebidoEm.
- processadoEm.

Status sugeridos:

- RECEBIDO.
- PROCESSADO.
- ERRO.

## 7.3 Regras de segurança

- Validar o mecanismo de autenticação do webhook conforme a documentação do Asaas.
- Não confiar somente nos dados enviados pelo aplicativo.
- Implementar idempotência.
- Evitar o processamento duplicado do mesmo evento.
- Registrar falhas de processamento.
- Atualizar o Firestore somente após as validações necessárias.
- Não armazenar segredos no aplicativo.
- Não expor tokens em logs.

A integração deve permitir que os status de cobrança e assinatura sejam atualizados com base nos eventos recebidos.

---

# 8. ARQUITETURA DO APLICATIVO

Utilizar:

- Kotlin.
- Android Studio.
- Jetpack Compose.
- Arquitetura MVVM.
- Repository Pattern.
- ViewModel.
- UiState.
- Kotlin Coroutines.
- Firebase Authentication.
- Cloud Firestore.
- Firebase Cloud Functions.
- Asaas API.
- Firebase Cloud Messaging, se necessário.

## 8.1 Camadas

### Apresentação

Responsável por:

- Telas.
- Componentes Compose.
- Navegação.
- Estados visuais.
- Mensagens de erro e sucesso.

### ViewModel

Responsável por:

- Gerenciar o estado da tela.
- Executar ações do usuário.
- Comunicar-se com os repositórios.
- Expor UiState.
- Controlar estados de carregamento, sucesso e erro.

### Repository

Responsável por:

- Abstrair acesso aos dados.
- Encapsular chamadas ao Firebase.
- Comunicar-se com as Cloud Functions.
- Não conter regras de apresentação.

### Firebase Cloud Functions

Responsável por:

- Operações de backend.
- Integração com Asaas.
- Validação de solicitações.
- Processamento de webhooks.
- Atualização de dados no Firestore.

---

# 9. ESTRUTURA DE PASTAS ANDROID

Utilizar uma estrutura organizada semelhante à seguinte:

app/
└── src/
    └── main/
        └── java/
            └── br/
                └── com/
                    └── tomai/
                        ├── MainActivity.kt
                        │
                        ├── model/
                        │   ├── Usuario.kt
                        │   ├── Medicamento.kt
                        │   ├── HorarioMedicamento.kt
                        │   ├── Dose.kt
                        │   ├── PessoaConfianca.kt
                        │   ├── Alerta.kt
                        │   ├── Plano.kt
                        │   ├── Assinatura.kt
                        │   ├── Cobranca.kt
                        │   └── EventoWebhook.kt
                        │
                        ├── data/
                        │   ├── repository/
                        │   │   ├── AuthRepository.kt
                        │   │   ├── UsuarioRepository.kt
                        │   │   ├── MedicamentoRepository.kt
                        │   │   ├── DoseRepository.kt
                        │   │   ├── PessoaConfiancaRepository.kt
                        │   │   ├── PlanoRepository.kt
                        │   │   └── AssinaturaRepository.kt
                        │   │
                        │   └── remote/
                        │       └── FirebaseFunctionsService.kt
                        │
                        ├── ui/
                        │   ├── navigation/
                        │   ├── components/
                        │   ├── theme/
                        │   ├── login/
                        │   ├── cadastro/
                        │   ├── home/
                        │   ├── medicamentos/
                        │   ├── horarios/
                        │   ├── doses/
                        │   ├── historico/
                        │   ├── responsavel/
                        │   ├── planos/
                        │   ├── assinatura/
                        │   └── cobrancas/
                        │
                        └── viewmodel/
                            ├── AuthViewModel.kt
                            ├── MedicamentoViewModel.kt
                            ├── DoseViewModel.kt
                            ├── PessoaConfiancaViewModel.kt
                            ├── PlanoViewModel.kt
                            └── AssinaturaViewModel.kt

functions/
├── src/
│   ├── index.ts
│   ├── asaas/
│   │   ├── clientes.ts
│   │   ├── assinaturas.ts
│   │   └── cobrancas.ts
│   ├── webhooks/
│   │   └── asaasWebhook.ts
│   └── utils/
│       └── idempotencia.ts
└── package.json

---

# 10. TELAS DO APLICATIVO

Criar as seguintes telas.

## 10.1 Autenticação

- Splash, se necessário.
- Login.
- Criar conta.
- Recuperar senha.

## 10.2 Área principal

- Dashboard ou tela inicial.
- Resumo das doses do dia.
- Próximos horários.
- Doses pendentes.
- Status do plano de assinatura.
- Acesso rápido ao histórico.

## 10.3 Medicamentos

- Lista de medicamentos.
- Cadastro de medicamento.
- Edição de medicamento.
- Configuração de horários.
- Visualização de detalhes.

## 10.4 Doses e histórico

- Lista de doses do dia.
- Botão de confirmação.
- Histórico de doses.
- Filtros por data e medicamento, se necessário.
- Identificação do status de cada dose.

## 10.5 Pessoa de confiança

- Lista de responsáveis.
- Cadastro.
- Edição.
- Definição de responsável padrão.
- Ativação e desativação.
- Indicação de consentimento para avisos.

## 10.6 Planos

- Lista de planos disponíveis.
- Identificação do ciclo quinzenal ou mensal.
- Valor do plano.
- Descrição dos recursos.
- Ação para iniciar contratação.

## 10.7 Assinatura

- Plano contratado.
- Status da assinatura.
- Data de início.
- Próxima cobrança, quando disponível.
- Identificador da assinatura, se necessário para diagnóstico.
- Opção de cancelamento, se implementada.
- Mensagens claras para estados pendentes e falhas.

## 10.8 Cobranças

- Lista de cobranças.
- Valor.
- Vencimento.
- Status.
- Forma de pagamento, quando disponível.
- Data de pagamento, quando disponível.

---

# 11. MODELO DE DADOS

Utilizar as seguintes entidades:

- USUARIO.
- PESSOA_CONFIANCA.
- MEDICAMENTO.
- HORARIO_MEDICAMENTO.
- DOSE.
- ALERTA.
- PLANO.
- ASSINATURA.
- COBRANCA.
- EVENTO_WEBHOOK.

## 11.1 Relacionamentos

- USUARIO 1:N MEDICAMENTO.
- USUARIO 1:N PESSOA_CONFIANCA.
- USUARIO 0:1 PESSOA_CONFIANCA como responsável padrão.
- MEDICAMENTO 1:N HORARIO_MEDICAMENTO.
- HORARIO_MEDICAMENTO 1:N DOSE.
- MEDICAMENTO 1:N DOSE.
- USUARIO 1:N DOSE.
- DOSE 1:N ALERTA.
- PESSOA_CONFIANCA 1:N ALERTA.
- USUARIO 1:N PLANO, quando o modelo de administração exigir.
- USUARIO 1:N ASSINATURA.
- PLANO 1:N ASSINATURA.
- ASSINATURA 1:N COBRANCA.
- ASSINATURA 1:N EVENTO_WEBHOOK.
- COBRANCA 1:N EVENTO_WEBHOOK.

Gerar no README um diagrama ER utilizando a sintaxe Mermaid erDiagram.

Identificar explicitamente:

- Chaves primárias.
- Chaves estrangeiras lógicas.
- Cardinalidades.
- Relacionamento entre usuário e responsável.
- Relacionamento entre dose, alerta e responsável.
- Relacionamento entre plano, assinatura e cobrança.

---

# 12. FIRESTORE

Utilizar uma estrutura de coleções organizada.

Coleções sugeridas:

- usuarios.
- pessoas_confianca.
- medicamentos.
- horarios_medicamentos.
- doses.
- alertas.
- planos.
- assinaturas.
- cobrancas.
- eventos_webhook.

Avaliar se determinadas entidades devem ser subcoleções ou coleções de nível superior conforme os padrões de consulta, segurança e escalabilidade.

Não criar referências SQL físicas no Firestore. Utilizar IDs e referências lógicas entre documentos.

Criar regras de segurança que limitem o acesso aos dados do usuário autenticado.

As operações administrativas e financeiras devem ocorrer em backend seguro quando necessário.

---

# 13. SEGURANÇA

Implementar boas práticas:

- Não colocar a chave do Asaas no código Android.
- Não colocar a chave do Asaas no Git.
- Utilizar variáveis de ambiente ou Secret Manager no backend.
- Validar o usuário autenticado.
- Validar dados recebidos nas Cloud Functions.
- Restringir acesso no Firestore.
- Não confiar em valores enviados pelo cliente para determinar autorização.
- Não permitir que o cliente altere diretamente o status de uma cobrança para paga.
- Validar webhooks.
- Implementar idempotência.
- Não registrar informações secretas em logs.
- Tratar erros sem exibir informações internas ao usuário.

---

# 14. BACKEND E ASAAS

Criar funções separadas para as operações necessárias, como:

- Criar cliente no Asaas.
- Consultar cliente.
- Criar assinatura.
- Consultar assinatura.
- Cancelar assinatura, se aplicável.
- Consultar cobranças.
- Processar webhook.

Os nomes e formatos finais dos endpoints devem ser verificados na documentação oficial do Asaas.

Se a integração exigir configuração de ambiente, criar um arquivo de exemplo sem credenciais reais:

.env.example

Incluir instruções para:

- Configurar o ambiente Sandbox.
- Adicionar credenciais.
- Configurar o webhook.
- Executar as funções localmente.
- Testar as operações.
- Migrar para produção somente após validação.

---

# 15. UX/UI

Criar uma interface:

- Simples.
- Clara.
- Responsiva.
- Adequada para Android.
- Com boa legibilidade.
- Com botões de ação identificáveis.
- Com feedback de carregamento.
- Com mensagens de erro compreensíveis.
- Com confirmação visual após uma dose ser registrada.
- Com destaque para doses pendentes.
- Com diferenciação clara entre assinatura ativa, pendente e cancelada.

Utilizar componentes reutilizáveis.

Evitar excesso de elementos visuais.

Manter consistência de cores, tipografia, espaçamentos e navegação.

---

# 16. REGRAS DE IMPLEMENTAÇÃO

Antes de criar arquivos:

1. Inspecionar a estrutura atual do projeto.
2. Identificar se já existe um projeto Android.
3. Identificar o package name atual.
4. Verificar versão do Kotlin.
5. Verificar versão do Android Gradle Plugin.
6. Verificar configuração do Firebase.
7. Verificar se existem dependências já instaladas.
8. Não apagar código funcional sem necessidade.
9. Não substituir o projeto inteiro sem explicar o motivo.
10. Apresentar um plano de implementação antes de alterações de grande porte.

Ao implementar:

- Criar código compilável.
- Evitar código fictício apresentado como funcional.
- Não inventar respostas de APIs.
- Utilizar tratamento de erros.
- Utilizar estados de carregamento.
- Criar componentes reutilizáveis.
- Manter nomes de classes e pacotes consistentes.
- Comentar trechos complexos para fins didáticos.
- Não colocar comentários desnecessários em todas as linhas.
- Documentar decisões técnicas relevantes.

---

# 17. TESTES

Criar ou preparar testes para:

- Cadastro de usuário.
- Login.
- Cadastro de medicamento.
- Cadastro de horário.
- Confirmação de dose.
- Registro de dose não confirmada.
- Cadastro de pessoa de confiança.
- Associação de responsavelId ao alerta.
- Cadastro de plano quinzenal.
- Cadastro de plano mensal.
- Criação de assinatura.
- Atualização por webhook.
- Processamento duplicado de evento.
- Tratamento de erros da API.
- Controle de acesso ao Firestore.

Para a integração Asaas, utilizar o ambiente Sandbox quando disponível.

Distinguir testes unitários, testes de integração e testes manuais.

---

# 18. DOCUMENTAÇÃO OBRIGATÓRIA

Criar ou atualizar o README.md com:

1. Nome do projeto.
2. Descrição.
3. Problema abordado.
4. Objetivo.
5. Público-alvo.
6. Funcionalidades.
7. Stack tecnológica.
8. Justificativa da stack.
9. Arquitetura.
10. Diagrama macro em Mermaid.
11. Modelo de dados.
12. DER em Mermaid.
13. Entidades, atributos, PKs e FKs lógicas.
14. Cardinalidades.
15. Fluxo de pagamento.
16. Fluxo de dose não confirmada.
17. Estrutura de pastas.
18. Configuração do Firebase.
19. Configuração do backend.
20. Configuração do Asaas Sandbox.
21. Regras de segurança.
22. Como executar o projeto.
23. Como executar testes.
24. Limitações conhecidas.
25. Próximos passos.

Os diagramas Mermaid devem ser apresentados em blocos de código Markdown.

---

# 19. ENTREGAS ESPERADAS

Ao concluir cada etapa, apresentar:

- Arquivos criados.
- Arquivos modificados.
- Explicação das alterações.
- Dependências adicionadas.
- Configurações necessárias.
- Comandos para executar.
- Testes realizados.
- Erros encontrados.
- Soluções aplicadas.
- Pendências que dependem de configuração externa.

Não afirmar que o aplicativo está totalmente funcional se existirem integrações ou configurações ainda pendentes.

---

# 20. ORDEM DE EXECUÇÃO

Seguir a ordem:

## Fase 1 — Análise

- Inspecionar projeto existente.
- Identificar tecnologias.
- Identificar package name.
- Avaliar estrutura atual.
- Apresentar plano de trabalho.

## Fase 2 — Base do aplicativo

- Configurar arquitetura.
- Configurar tema.
- Configurar navegação.
- Configurar Firebase.
- Implementar autenticação.

## Fase 3 — Domínio de medicamentos

- Criar modelos.
- Implementar repositórios.
- Implementar cadastro de medicamentos.
- Implementar horários.
- Implementar doses.
- Implementar histórico.

## Fase 4 — Pessoa de confiança

- Criar modelo.
- Criar cadastro.
- Criar responsável padrão.
- Implementar associação do responsavelId aos alertas.
- Implementar regras de acesso.

## Fase 5 — Planos e assinaturas

- Criar entidade Plano.
- Implementar ciclos quinzenal e mensal.
- Criar entidade Assinatura.
- Criar telas de planos.
- Criar tela de assinatura.
- Preparar integração backend.

## Fase 6 — Asaas

- Validar documentação oficial.
- Configurar Sandbox.
- Implementar Cloud Functions.
- Implementar criação de clientes.
- Implementar assinaturas.
- Implementar cobranças.
- Implementar webhook.
- Implementar idempotência.

## Fase 7 — Testes

- Executar compilação.
- Corrigir erros.
- Executar testes disponíveis.
- Validar regras de segurança.
- Validar fluxos principais.

## Fase 8 — Documentação

- Atualizar README.
- Inserir diagrama macro.
- Inserir DER.
- Inserir instruções de execução.
- Registrar limitações.
- Registrar decisões técnicas.

---

# 21. CRITÉRIO DE CONCLUSÃO

O projeto será considerado preparado para a apresentação quando:

- O aplicativo compilar.
- O usuário conseguir realizar login.
- O usuário conseguir cadastrar medicamentos.
- O usuário conseguir configurar horários.
- O usuário conseguir confirmar doses.
- O histórico apresentar os registros.
- O usuário conseguir cadastrar uma pessoa de confiança.
- O alerta possuir o responsavelId correto.
- Os planos quinzenal e mensal estiverem representados no modelo.
- A assinatura estiver relacionada ao plano.
- As cobranças estiverem relacionadas à assinatura.
- A integração com Asaas estiver documentada e implementada conforme os recursos efetivamente validados.
- O README contiver a arquitetura e o DER.
- Os riscos de segurança estiverem documentados.
- As limitações de funcionalidades externas estiverem claramente identificadas.

COMECE PELA FASE 1.

Não implemente todas as etapas de uma só vez sem apresentar o diagnóstico inicial do projeto e o plano de alterações.