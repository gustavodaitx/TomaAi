import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import { criarPerfilPaciente as criarPerfilPacienteNoFirestore, garantirCodigoPaciente as garantirCodigoPacienteNoFirestore, ErroCadastroPaciente, obterIdentidadeAutenticada } from "./usuarios/cadastroPaciente";
import { criarClienteNoAsaas } from "./asaas/clientes";
import { cancelarAssinaturaNoAsaas, criarAssinaturaNoAsaas } from "./asaas/assinaturas";
import { obterPixDaCobranca, removerCobrancaPendente, sincronizarCobrancasNoAsaas } from "./asaas/cobrancas";
import { processarAsaasWebhook } from "./webhooks/asaasWebhook";
import { obterPlanosPadrao } from "./asaas/planos";
import { gerarCobrancasQuinzenais } from "./asaas/gerarCobrancasQuinzenais";
import { criarPerfilPessoaDeConfianca as criarPerfilPessoaDeConfiancaNoFirestore, ErroCadastroPessoaDeConfianca } from "./usuarios/cadastroPessoaDeConfianca";
import { limparDadosMedicacao as limparDadosMedicacaoNoFirestore } from "./usuarios/limparDadosMedicacao";

admin.initializeApp();
const db = admin.firestore();

function exigirLogin(context: functions.https.CallableContext): string {
  if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Faça login para continuar.");
  return context.auth.uid;
}

export const criarPerfilPaciente = functions.https.onCall(async (data, context) => {
  try {
    const identidade = obterIdentidadeAutenticada(context.auth);
    return await criarPerfilPacienteNoFirestore(identidade.uid, identidade.email, data, db);
  } catch (error) {
    if (error instanceof ErroCadastroPaciente) {
      throw new functions.https.HttpsError(error.codigo, error.message);
    }
    console.error("Falha inesperada ao criar perfil de paciente.", error);
    throw new functions.https.HttpsError("internal", "Não foi possível concluir o cadastro. Tente novamente.");
  }
});

export const garantirCodigoPaciente = functions.https.onCall(async (_data, context) => {
  try {
    const identidade = obterIdentidadeAutenticada(context.auth);
    return { codigoPaciente: await garantirCodigoPacienteNoFirestore(identidade.uid, db) };
  } catch (error) {
    if (error instanceof ErroCadastroPaciente) {
      throw new functions.https.HttpsError(error.codigo, error.message);
    }
    console.error("Falha inesperada ao garantir o código do paciente.", error);
    throw new functions.https.HttpsError("internal", "Não foi possível gerar o código. Tente novamente.");
  }
});

export const criarPerfilPessoaDeConfianca = functions.https.onCall(async (data, context) => {
  try {
    const identidade = obterIdentidadeAutenticada(context.auth);
    return await criarPerfilPessoaDeConfiancaNoFirestore(identidade.uid, identidade.email, data, db);
  } catch (error) {
    if (error instanceof ErroCadastroPessoaDeConfianca) throw new functions.https.HttpsError(error.codigo, error.message);
    console.error("Falha inesperada ao criar perfil de pessoa de confiança.", error);
    throw new functions.https.HttpsError("internal", "Não foi possível concluir o cadastro. Tente novamente.");
  }
});

export const limparDadosMedicacao = functions.https.onCall(async (_data, context) => {
  const uid = exigirLogin(context);
  try {
    const perfil = (await db.collection("usuarios").doc(uid).get()).get("perfil");
    if (perfil !== "PACIENTE") {
      throw new functions.https.HttpsError("permission-denied", "Somente o paciente pode limpar os próprios dados de medicação.");
    }
    return { removidosPorColecao: await limparDadosMedicacaoNoFirestore(uid, db) };
  } catch (error) {
    if (error instanceof functions.https.HttpsError) throw error;
    console.error("Falha ao limpar dados de medicação do usuário autenticado.", error);
    const etapa = error instanceof Error && error.message.startsWith("Falha ao ")
      ? error.message
      : "A limpeza não foi concluída integralmente.";
    throw new functions.https.HttpsError("internal", `${etapa} Tente novamente; a operação pode ser repetida com segurança.`);
  }
});

export const obterNomePacienteVinculado = functions.https.onCall(async (_data, context) => {
  const uid = exigirLogin(context);
  const pessoa = (await db.collection("usuarios").doc(uid).get()).data();
  if (pessoa?.perfil !== "PESSOA_DE_CONFIANCA" ||
      typeof pessoa.pacienteUid !== "string" || !pessoa.pacienteUid) {
    throw new functions.https.HttpsError("permission-denied", "Não há um vínculo autorizado com paciente.");
  }
  const vinculo = await db.collection("vinculos").doc(`${pessoa.pacienteUid}_${uid}`).get();
  if (!vinculo.exists || vinculo.get("ativo") !== true || vinculo.get("pacienteUid") !== pessoa.pacienteUid ||
      vinculo.get("cuidadorUid") !== uid) {
    throw new functions.https.HttpsError("permission-denied", "O vínculo com o paciente não está ativo.");
  }
  const paciente = (await db.collection("usuarios").doc(pessoa.pacienteUid).get()).data();
  if (paciente?.perfil !== "PACIENTE") {
    throw new functions.https.HttpsError("not-found", "Perfil do paciente não encontrado.");
  }
  return { nome: typeof paciente.nome === "string" ? paciente.nome.trim() : "" };
});

export const listarPlanos = functions.https.onCall(async (_data, context) => {
  exigirLogin(context);
  const snapshot = await db.collection("planos").where("ativo", "==", true).get();
  const existentes = new Map(snapshot.docs.map((doc) => [doc.id, { id: doc.id, ...doc.data() }]));
  const idsEsperados = ["QUINZENAL", "MENSAL"] as const;
  const faltantes = idsEsperados.filter((id) => !existentes.has(id));
  if (faltantes.length === 0) return idsEsperados.map((id) => existentes.get(id));

  let configurados;
  try {
    configurados = obterPlanosPadrao(process.env.PRECO_QUINZENAL, process.env.PRECO_MENSAL);
  } catch (error) {
    throw new functions.https.HttpsError("failed-precondition", error instanceof Error ? error.message : "Configure os preços dos planos.");
  }
  const batch = db.batch();
  configurados.filter((plano) => faltantes.includes(plano.id)).forEach((plano) => {
    batch.set(db.collection("planos").doc(plano.id), plano, { merge: true });
    existentes.set(plano.id, plano);
  });
  await batch.commit();
  return idsEsperados.map((id) => existentes.get(id));
});

export const criarClienteAsaas = functions.https.onCall(async (_data, context) => {
  const uid = exigirLogin(context);
  const userRef = db.collection("usuarios").doc(uid);
  const userSnapshot = await userRef.get();
  if (!userSnapshot.exists) throw new functions.https.HttpsError("failed-precondition", "Perfil não encontrado.");
  const user = userSnapshot.data()!;
  if (user.asaasCustomerId) return { customerId: user.asaasCustomerId };
  if (!user.nome || !context.auth?.token.email) throw new functions.https.HttpsError("failed-precondition", "Nome e e-mail verificado são necessários.");
  const customerId = await criarClienteNoAsaas(user.nome, context.auth.token.email as string);
  await userRef.update({ asaasCustomerId: customerId });
  return { customerId };
});

export const criarAssinaturaAsaas = functions.https.onCall(async (data, context) => {
  const uid = exigirLogin(context);
  const planoId = String(data?.planoId || "");
  const formaPagamento = String(data?.formaPagamento || "PIX");
  if (!["QUINZENAL", "MENSAL"].includes(planoId) || !["PIX", "CREDIT_CARD"].includes(formaPagamento)) {
    throw new functions.https.HttpsError("invalid-argument", "Plano ou forma de pagamento inválidos.");
  }
  const planoSnapshot = await db.collection("planos").doc(planoId).get();
  const plano = planoSnapshot.data();
  if (!planoSnapshot.exists || !plano?.ativo || typeof plano.valor !== "number" || plano.valor <= 0) {
    throw new functions.https.HttpsError("failed-precondition", "Plano indisponível.");
  }
  const assinaturasExistentes = await db.collection("assinaturas").where("usuarioId", "==", uid).get();
  if (assinaturasExistentes.docs.some((doc) => ["PENDENTE", "ATIVA", "INADIMPLENTE"].includes(doc.get("status")))) {
    throw new functions.https.HttpsError("failed-precondition", "Já existe uma assinatura aberta. Cancele ou regularize-a antes de contratar outra.");
  }
  const userRef = db.collection("usuarios").doc(uid);
  const userSnapshot = await userRef.get();
  const user = userSnapshot.data();
  if (!user) throw new functions.https.HttpsError("failed-precondition", "Perfil não encontrado.");
  let customerId = user.asaasCustomerId as string | undefined;
  if (!customerId) {
    const email = context.auth?.token.email as string | undefined;
    if (!user.nome || !email) throw new functions.https.HttpsError("failed-precondition", "Nome e e-mail são necessários para iniciar o pagamento.");
    customerId = await criarClienteNoAsaas(user.nome, email);
    await userRef.update({ asaasCustomerId: customerId });
  }
  const resultado = await criarAssinaturaNoAsaas(customerId, uid, planoId, plano.ciclo, plano.valor, formaPagamento);
  return { sucesso: true, ...resultado };
});

export const consultarCobrancas = functions.https.onCall(async (data, context) => {
  const uid = exigirLogin(context);
  const assinaturaId = String(data?.assinaturaId || "");
  const assinaturaSnapshot = await db.collection("assinaturas").doc(assinaturaId).get();
  const assinatura = assinaturaSnapshot.data();
  if (!assinatura || assinatura.usuarioId !== uid) throw new functions.https.HttpsError("permission-denied", "Assinatura não encontrada para este usuário.");
  return { cobrancas: await sincronizarCobrancasNoAsaas(assinaturaId, uid) };
});

export const obterPixCobranca = functions.https.onCall(async (data, context) => {
  const uid = exigirLogin(context);
  const cobrancaId = String(data?.cobrancaId || "");
  const cobranca = (await db.collection("cobrancas").doc(cobrancaId).get()).data();
  if (!cobranca || cobranca.usuarioId !== uid) throw new functions.https.HttpsError("permission-denied", "Cobrança não encontrada para este usuário.");
  if (cobranca.formaPagamento !== "PIX") throw new functions.https.HttpsError("failed-precondition", "Esta cobrança não utiliza Pix.");
  return obterPixDaCobranca(cobranca.asaasPaymentId);
});

export const cancelarAssinaturaAsaas = functions.https.onCall(async (data, context) => {
  const uid = exigirLogin(context);
  const assinaturaId = String(data?.assinaturaId || "");
  const assinaturaRef = db.collection("assinaturas").doc(assinaturaId);
  const assinatura = (await assinaturaRef.get()).data();
  if (!assinatura || assinatura.usuarioId !== uid) throw new functions.https.HttpsError("permission-denied", "Assinatura não encontrada para este usuário.");
  if (!assinatura.asaasSubscriptionId && assinatura.ciclo === "QUINZENAL") {
    await assinaturaRef.update({ status: "CANCELADA", dataFim: Date.now() });
    if (assinatura.asaasPaymentId) await removerCobrancaPendente(assinatura.asaasPaymentId);
    return { sucesso: true };
  }
  const sucesso = await cancelarAssinaturaNoAsaas(assinatura.asaasSubscriptionId);
  if (!sucesso) throw new functions.https.HttpsError("internal", "Não foi possível cancelar a assinatura.");
  return { sucesso };
});

export const asaasWebhook = functions.https.onRequest(processarAsaasWebhook);
export { gerarCobrancasQuinzenais };
