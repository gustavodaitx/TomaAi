# TESTE.md — Plano de testes do MVP TomaAí

**Projeto:** TomaAí  
**Repositório:** https://github.com/gustavodaitx/TomaAi  
**Stack:** Kotlin, Jetpack Compose, Firebase Auth, Firestore, FCM e Cloud Functions (Asaas)  
**Data-base:** 2026-10-29  
**Squad/responsáveis:** preencher com os nomes reais dos integrantes.

## Objetivo

Definir o que será testado, como será testado e os critérios de sucesso. Este arquivo deve orientar o Codex ou o Chat do Git a inspecionar o código real, implementar testes compatíveis com a arquitetura existente, executá-los e registrar resultados comprovados.

> **Regra importante:** não marque um teste como `Passou` sem executar o teste e conferir o resultado. Não invente classes, métodos, dependências ou caminhos. Primeiro inspecione o projeto e adapte os casos à implementação real. Não use credenciais reais nem produza cobranças reais.

## Instruções para Codex / Chat do Git

1. Inspecione a estrutura do projeto, `TASK.md`, `README.md`, `settings.gradle(.kts)`, arquivos `build.gradle(.kts)`, código de produção e testes existentes.
2. Identifique os nomes e assinaturas reais de `AuthViewModel`, repositories, modelos de medicamento/dose e funções relacionadas a Firebase. Não presuma que os nomes deste documento correspondem exatamente aos símbolos do código.
3. Escolha no mínimo **um teste unitário** de baixo acoplamento e **um teste de integração** viável no ambiente atual.
4. Se já houver infraestrutura de testes, reutilize-a. Se não houver, proponha a menor alteração necessária e explique dependências e configuração antes de adicioná-las.
5. Para unitários, prefira testar regras de validação/estado sem rede, usando fakes/mocks quando compatível com o projeto.
6. Para integração Firebase, verifique se o Firebase Emulator Suite está configurado. Se não estiver, não conecte testes automatizados a um projeto de produção: prepare a configuração de emulador ou documente claramente o bloqueio.
7. Execute os testes reais. Registre o comando, resultado, falhas e evidência. Não confunda `assembleDebug` com teste.
8. Execute `./gradlew testDebugUnitTest` (Windows PowerShell: `.\gradlew.bat testDebugUnitTest`) quando compatível com o projeto. Execute também o teste de integração específico, conforme a configuração encontrada.
9. Execute `./gradlew assembleDebug` (Windows: `.\gradlew.bat assembleDebug`) como verificação adicional de compilação.
10. Faça mudanças somente em uma branch de trabalho; não faça commit diretamente em `main`/`master`. Não faça push nem commit sem autorização explícita do usuário. Ao final, apresente os arquivos alterados, comandos e resultados.

## Plano de testes

**Status possíveis:** `Não iniciado`, `Passou`, `Falhou`, `Bloqueado`.

| ID | Tipo | Descrição | Critério de aceite | Responsável | Status | Tarefa |
|---|---|---|---|---|---|---|
| TC01 | Unitário | AuthViewModel valida e-mail inválido no login. | O estado apresenta erro de validação e não inicia autenticação. | Responsável por Auth | Passou | T1.2 |
| TC02 | Unitário | AuthViewModel rejeita senha com menos de 6 caracteres no cadastro. | Cadastro não é enviado; mensagem orienta a corrigir a senha. | Responsável por Auth | Não iniciado | T1.2 |
| TC03 | Unitário | Cadastro exige confirmação de senha idêntica. | Senhas diferentes impedem o envio e geram feedback visível. | Responsável por Auth | Não iniciado | T1.2 |
| TC04 | Unitário | MedicamentoViewModel valida os campos obrigatórios do medicamento. | Sem nome ou dados obrigatórios, não salva e informa o campo inválido. | Responsável por Medicamentos | Não iniciado | T2.1 |
| TC05 | Unitário | Regra de confirmação de dose atualiza status e data de confirmação. | Ao confirmar, status é CONFIRMADA e confirmadaEm é preenchido. | Responsável por Doses | Não iniciado | T3.2 |
| TC06 | Unitário | Filtro do histórico aplica período e medicamento selecionado. | A lista contém somente doses que atendem a ambos os filtros. | Responsável por Histórico | Não iniciado | T3.4 |
| TC07 | Unitário | Lógica de alerta não repete notificação de dose já confirmada. | Quando status é CONFIRMADA, a rotina não agenda nova repetição. | Responsável por Alertas | Não iniciado | T4.2 |
| TC08 | Integração | Cadastro Firebase Auth cria perfil correspondente em /usuarios/{uid}. | Auth retorna UID e Firestore contém documento com o mesmo UID e perfil informado. | Responsável por Auth/Firebase | Passou | T1.1 |
| TC09 | Integração | Salvar medicamento persiste usuário proprietário e dados no Firestore. | Documento fica em /medicamentos e contém usuarioId do usuário autenticado. | Responsável por Medicamentos | Não iniciado | T2.1 |
| TC10 | Integração | Confirmação de dose persiste status e confirmadaEm no Firestore. | Após recarregar a dose, status e horário de confirmação permanecem atualizados. | Responsável por Doses | Não iniciado | T3.2 |
| TC11 | Integração | Rules impedem usuário A de ler ou alterar dados privados do usuário B. | Operação cruzada é negada; operações próprias são permitidas conforme as regras. | Responsável por Segurança | Não iniciado | T6.1 |
| TC12 | Integração | Webhook Asaas processa evento repetido sem duplicar efeito. | Mesmo identificador de evento é processado uma única vez; assinatura/cobrança não duplica. | Responsável por Backend/Pagamentos | Não iniciado | T5.3 |
| TC13 | Aceitação | Paciente cadastra medicamento, agenda horário e confirma uma dose. | Fluxo completo é concluído; dose aparece na agenda e histórico com estado correto. | Responsável por QA/Produto | Não iniciado | T2.1, T2.2, T3.1, T3.2, T3.4 |
| TC14 | Aceitação | Paciente vincula Pessoa de Confiança e verifica alerta de dose não confirmada. | Vínculo ativo aparece para as contas envolvidas; alerta ocorre após limite configurado e cessa após confirmação sincronizada. | Responsável por QA/Alertas | Não iniciado | T4.1, T4.2, T4.3 |
| TC15 | Aceitação | Usuário seleciona plano e percorre pagamento Sandbox Pix ou cartão. | Planos e status aparecem corretamente; fluxo é de teste e não cria cobrança real. | Responsável por QA/Pagamentos | Não iniciado | T5.1, T5.2, T5.4 |

## Roteiros de aceitação

### TC13 — Cadastro de medicamento, agenda e confirmação de dose

1. Entrar no app com conta de teste autenticada.
2. Abrir Medicamentos e adicionar um medicamento com os campos obrigatórios válidos.
3. Configurar um horário recorrente para hoje e salvar.
4. Abrir a agenda e localizar a dose gerada.
5. Confirmar a dose como tomada.
6. Abrir o Histórico e filtrar por hoje e pelo medicamento.

**Esperado:** medicamento e horário persistem; dose aparece na agenda; confirmação atualiza status/data; histórico apresenta o resultado correto.

### TC14 — Vínculo e alerta para Pessoa de Confiança

1. Usar dois dispositivos/emuladores e duas contas de teste distintas.
2. Na conta do paciente, cadastrar/convidar Pessoa de Confiança e gerar o código de vínculo.
3. Aceitar o vínculo na segunda conta, conforme o fluxo real do app.
4. Confirmar que o vínculo está ativo e a autorização de avisos está habilitada.
5. Criar uma dose de teste para horário próximo e não confirmá-la.
6. Aguardar o limite de atraso configurado e observar os dois dispositivos.
7. Confirmar a dose no dispositivo do paciente e observar se a repetição para.

**Esperado:** vínculo e permissões são respeitados; o alerta segue a configuração; após confirmação sincronizada, não há novas repetições. **Uma notificação local não comprova que o push FCM remoto funciona.** Registre os dois resultados separadamente.

### TC15 — Planos e pagamento Sandbox

1. Entrar no app com conta de teste.
2. Abrir Planos/Assinatura e selecionar Quinzenal.
3. Iniciar o fluxo Pix e verificar que os dados são de Sandbox/fictícios; não pagar de verdade.
4. Repetir com Mensal e cartão de teste autorizado pelo ambiente Sandbox.
5. Consultar o estado da assinatura/cobrança após retorno do backend.

**Esperado:** plano e status corretos; resposta Sandbox processada; nenhuma cobrança real criada.

## Execução e registro de resultados

Na raiz do projeto Android, quando os tasks existirem:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

No Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Para testes de integração Firebase, use Firebase Emulator Suite quando aplicável. Consulte os scripts do `functions/package.json` antes de escolher o comando das Cloud Functions. Não invente scripts.

### Execução comprovada nesta branch

**Branch:** `testes/mvp-tomai`  
**HEAD na execução:** `301a4e4` (alterações locais não commitadas)  
**Data/hora:** 2026-10-29, fuso `-03:00`

| Caso executado | Data/hora | Branch/commit | Comando/roteiro | Resultado real | Evidência/log |
|---|---|---|---|---|---|
| TC01 unitário | 2026-10-29 20:03:19 -03:00 | `testes/mvp-tomai` / `301a4e4` | `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` | Passou: 29 testes unitários, 0 falhas; `assembleDebug` também concluiu com `BUILD SUCCESSFUL`. O `AuthViewModelTest` teve 19 testes aprovados. | `app/build/test-results/testDebugUnitTest/TEST-br.com.tomai.viewmodel.AuthViewModelTest.xml` e saída Gradle |
| TC08 integração | 2026-10-29 20:02:58 -03:00 | `testes/mvp-tomai` / `301a4e4` | `firebase emulators:exec` com Auth/Firestore e `:app:connectedDebugAndroidTest` | Passou: 1 teste, 0 falhas. O repositório cadastrou a conta no Auth Emulator e o teste confirmou UID, nome e perfil no documento Firestore `/usuarios/{uid}`. | `app/build/outputs/androidTest-results/connected/debug/TEST-Medium_Phone_API_36.1(AVD) - 16-_app-.xml` e saída Firebase/Gradle |

#### Reprodução dos testes

Pré-requisitos da integração: AVD `Medium_Phone_API_36.1` iniciado e acessível por `adb`; Firebase CLI; JDK 21 ou superior para iniciar o Firebase Emulator Suite; JDK 17 para Gradle 8.7. O teste usa exclusivamente `demo-tomai`, uma FirebaseApp de teste com chave fictícia e `10.0.2.2` para Auth/Firestore Emulator. A configuração HTTP é restrita ao host do emulador na variante `debug`.

Inicie o AVD em outro terminal PowerShell e aguarde `sys.boot_completed` retornar `1`:

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
Start-Process -FilePath "$sdk\emulator\emulator.exe" -ArgumentList @(
  "-avd", "Medium_Phone_API_36.1", "-no-window", "-no-audio",
  "-no-snapshot-load", "-no-snapshot-save"
)
$adb = "$sdk\platform-tools\adb.exe"
& $adb wait-for-device
while ((& $adb shell getprop sys.boot_completed).Trim() -ne "1") {
  Start-Sleep -Seconds 5
}
```

Com o JDK 21+ do Android Studio, execute a integração na raiz do repositório:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
firebase emulators:exec --project demo-tomai --only auth,firestore --log-verbosity INFO 'set JAVA_HOME=C:\Program Files\Java\jdk-17&& set PATH=C:\Program Files\Java\jdk-17\bin;%PATH%&& .\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=br.com.tomai.data.repository.AuthRepositoryFirebaseEmulatorTest --console=plain'
```

Execute os testes unitários e a compilação:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
```

**Falhas observadas durante a configuração:** a primeira inicialização do Firebase CLI foi bloqueada porque Java 17 não atende ao requisito Java 21+ do `firebase-tools` 15.32.1; a execução foi repetida com JDK 25 do Android Studio, mantendo Gradle em JDK 17. A primeira execução instrumentada foi bloqueada por cleartext HTTP para `10.0.2.2`; a configuração foi restringida ao host do emulador em `debug` e a execução seguinte passou. Não houve acesso a projeto Firebase de produção nem criação de cobranças.

| Caso executado | Data/hora | Branch/commit | Comando/roteiro | Resultado real | Evidência/log |
|---|---|---|---|---|---|
| Outros casos TC02–TC15 | — | — | — | Não executados nesta tarefa | — |

## Critérios de conclusão da tarefa

- [x] Pelo menos um teste unitário implementado e executado.
- [x] Pelo menos um teste de integração implementado e executado.
- [x] Status da tabela atualizados com resultados reais.
- [x] Evidências/logs identificados.
- [x] Testes compilam e são reproduzíveis por outro integrante.
- [x] Nenhuma credencial real foi incluída no código ou logs.
- [ ] Alterações revisadas e commitadas na branch de trabalho conforme o fluxo do squad.
- [ ] PDF e DOCX do plano atualizados para refletir os resultados comprovados.

> Não foi feito commit, push ou merge, conforme solicitado. Não há arquivos PDF/DOCX do plano no repositório para atualizar; por isso, esses dois itens permanecem pendentes.

## Relação com TASK.md

- `T1.1` / `T1.2` → `TC01`–`TC03`, `TC08`
- `T2.1` → `TC04`, `TC09`, `TC13`
- `T2.2` → `TC13`
- `T3.2` → `TC05`, `TC10`, `TC13`
- `T3.4` → `TC06`, `TC13`
- `T4.1`–`T4.3` → `TC07`, `TC14`
- `T5.1`–`T5.4` → `TC12`, `TC15`
- `T6.1` → `TC11`
- `T6.2` → implementação, execução e manutenção dos casos deste plano
