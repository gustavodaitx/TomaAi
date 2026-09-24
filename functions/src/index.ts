import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import { criarClienteNoAsaas } from "./asaas/clientes";
import { cancelarAssinaturaNoAsaas, criarAssinaturaNoAsaas } from "./asaas/assinaturas";
import { processarAsaasWebhook } from "./webhooks/asaasWebhook";

admin.initializeApp();

// Callable Cloud Function: Criar Cliente no Asaas
export const criarClienteAsaas = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "O usuário deve estar autenticado."
    );
  }

  const { nome, email, cpfCnpj } = data;
  if (!nome || !email) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Nome e e-mail são obrigatórios."
    );
  }

  const customerId = await criarClienteNoAsaas(nome, email, cpfCnpj);
  return { customerId };
});

// Callable Cloud Function: Criar Assinatura no Asaas
export const criarAssinaturaAsaas = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "O usuário deve estar autenticado."
    );
  }

  const { usuarioId, planoId, ciclo, valor, formaPagamento } = data;
  const uid = context.auth.uid;

  if (usuarioId !== uid) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Não é permitido criar assinaturas para outro usuário."
    );
  }

  // 1. Busca perfil do usuário no Firestore para obter/gerar o customerId no Asaas
  const db = admin.firestore();
  const userDoc = await db.collection("usuarios").doc(uid).get();
  const userData = userDoc.data();

  let customerId = userData?.asaasCustomerId;

  if (!customerId) {
    customerId = await criarClienteNoAsaas(
      userData?.nome || "Usuário TomaAí",
      userData?.email || "usuario@tomai.com"
    );
    await db.collection("usuarios").doc(uid).update({ asaasCustomerId: customerId });
  }

  // 2. Cria a assinatura no Asaas
  const resultado = await criarAssinaturaNoAsaas(
    customerId,
    usuarioId,
    planoId,
    ciclo,
    valor,
    formaPagamento || "PIX"
  );

  return { sucesso: true, ...resultado };
});

// Callable Cloud Function: Cancelar Assinatura no Asaas
export const cancelarAssinaturaAsaas = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "O usuário deve estar autenticado."
    );
  }

  const { asaasSubscriptionId } = data;
  if (!asaasSubscriptionId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Identificador da assinatura ausente."
    );
  }

  const sucesso = await cancelarAssinaturaNoAsaas(asaasSubscriptionId);
  return { sucesso };
});

// HTTP Cloud Function: Webhook do Asaas
export const asaasWebhook = functions.https.onRequest(processarAsaasWebhook);
