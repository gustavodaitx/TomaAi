import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import { criarClienteNoAsaas } from "./asaas/clientes";
import { cancelarAssinaturaNoAsaas, criarAssinaturaNoAsaas } from "./asaas/assinaturas";
import { obterPixDaCobranca, removerCobrancaPendente, sincronizarCobrancasNoAsaas } from "./asaas/cobrancas";
import { processarAsaasWebhook } from "./webhooks/asaasWebhook";
import { verificarDosesNaoConfirmadas } from "./alerts/verificarDosesNaoConfirmadas";
import { obterPlanosPadrao } from "./asaas/planos";
import { gerarCobrancasQuinzenais } from "./asaas/gerarCobrancasQuinzenais";

admin.initializeApp();
const db = admin.firestore();

function exigirLogin(context: functions.https.CallableContext): string {
  if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Faça login para continuar.");
  return context.auth.uid;
}

export const listarPlanos = functions.https.onCall(async (_data, context) => {
  exigirLogin(context);
  const snapshot = await db.collection("planos").where("ativo", "==", true).get();
  if (!snapshot.empty) return snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));

  let configurados;
  try {
    configurados = obterPlanosPadrao(process.env.PRECO_QUINZENAL, process.env.PRECO_MENSAL);
  } catch (error) {
    throw new functions.https.HttpsError("failed-precondition", error instanceof Error ? error.message : "Configure os preços dos planos.");
  }
  const batch = db.batch();
  configurados.forEach((plano) => batch.set(db.collection("planos").doc(plano.id), plano, { merge: true }));
  await batch.commit();
  return configurados;
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
export { verificarDosesNaoConfirmadas };
export { gerarCobrancasQuinzenais };
