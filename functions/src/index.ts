import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import { criarClienteNoAsaas } from "./asaas/clientes";
import { cancelarAssinaturaNoAsaas, criarAssinaturaNoAsaas, DadosCartaoAsaas } from "./asaas/assinaturas";
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
  let dadosCartao: DadosCartaoAsaas | undefined;
  if (formaPagamento === "CREDIT_CARD") {
    const card = data?.dadosCartao;
    const digits = (value: unknown) => String(value || "").replace(/\D/g, "");
    if (!card || String(card.nomeTitular || "").trim().length < 2 || digits(card.numero).length < 13 || digits(card.numero).length > 19 ||
      digits(card.cvv).length < 3 || digits(card.cvv).length > 4 || digits(card.cpf).length !== 11 || digits(card.cep).length !== 8 ||
      !String(card.numeroEndereco || "").trim() || ![10, 11].includes(digits(card.telefone).length)) {
      throw new functions.https.HttpsError("invalid-argument", "Confira os dados do cartão e do titular.");
    }
    dadosCartao = {
      nomeTitular: String(card.nomeTitular).trim(), numero: digits(card.numero), validadeMes: digits(card.validadeMes),
      validadeAno: digits(card.validadeAno), cvv: digits(card.cvv), cpf: digits(card.cpf), cep: digits(card.cep),
      numeroEndereco: String(card.numeroEndereco).trim(), telefone: digits(card.telefone),
      email: String(context.auth?.token.email || ""), remoteIp: context.rawRequest.ip || "",
    };
    if (!/^\d{2}$/.test(dadosCartao.validadeMes) || !/^\d{4}$/.test(dadosCartao.validadeAno) || !dadosCartao.email || !dadosCartao.remoteIp) {
      throw new functions.https.HttpsError("invalid-argument", "Vencimento, e-mail ou conexão inválida para processar o cartão.");
    }
  }
  let customerId = user.asaasCustomerId as string | undefined;
  if (!customerId) {
    const email = context.auth?.token.email as string | undefined;
    if (!user.nome || !email) throw new functions.https.HttpsError("failed-precondition", "Nome e e-mail são necessários para iniciar o pagamento.");
    customerId = await criarClienteNoAsaas(user.nome, email);
    await userRef.update({ asaasCustomerId: customerId });
  }
  const resultado = await criarAssinaturaNoAsaas(customerId, uid, planoId, plano.ciclo, plano.valor, formaPagamento, dadosCartao);
  if (formaPagamento !== "PIX") return { sucesso: true, ...resultado };
  try {
    const pagamentos = await sincronizarCobrancasNoAsaas(resultado.subscriptionId, uid) as Array<{ id?: string; billingType?: string }>;
    const pagamentoPix = pagamentos.find((pagamento) => pagamento.billingType === "PIX" && pagamento.id);
    if (pagamentoPix?.id) return { sucesso: true, ...resultado, pix: await obterPixDaCobranca(pagamentoPix.id) };
    return { sucesso: true, ...resultado, pixErro: "A cobrança foi criada, mas o QR Pix ainda não está disponível. Atualize as cobranças em instantes." };
  } catch (error) {
    console.error("Assinatura Pix criada, QR ainda indisponível:", error);
    return { sucesso: true, ...resultado, pixErro: "A cobrança foi criada, mas não foi possível carregar o QR Pix agora. Atualize as cobranças em instantes." };
  }
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
