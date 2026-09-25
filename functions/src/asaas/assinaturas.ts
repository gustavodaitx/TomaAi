import axios from "axios";
import * as admin from "firebase-admin";
import { dataLocalSaoPaulo } from "../utils/data";

export async function criarAssinaturaNoAsaas(
  customerId: string,
  usuarioId: string,
  planoId: string,
  ciclo: string,
  valor: number,
  billingType: string = "PIX"
): Promise<{ subscriptionId: string; status: string }> {
  const asaasUrl = process.env.ASAAS_URL || "https://sandbox.asaas.com/api/v3";
  const apiKey = process.env.ASAAS_API_KEY || "";

  if (!apiKey) {
    throw new Error("ASAAS_API_KEY não configurada no ambiente do backend.");
  }

  if (ciclo.toUpperCase() === "QUINZENAL") {
    return criarAssinaturaQuinzenalNoAsaas(customerId, usuarioId, planoId, valor, billingType, asaasUrl, apiKey);
  }

  if (ciclo.toUpperCase() !== "MENSAL") {
    throw new Error("Ciclo de assinatura Asaas inválido.");
  }
  const cycleMapped = "MONTHLY";

  // Data de vencimento da primeira cobrança (amanhã)
  const nextDueDate = dataLocalSaoPaulo(1);

  try {
    const response = await axios.post(
      `${asaasUrl}/subscriptions`,
      {
        customer: customerId,
        billingType: billingType,
        value: valor,
        nextDueDate: nextDueDate,
        cycle: cycleMapped,
        description: `Assinatura TomaAí - Plano ${ciclo}`,
      },
      {
        headers: {
          access_token: apiKey,
          "Content-Type": "application/json",
        },
      }
    );

    const asaasSub = response.data;
    const db = admin.firestore();
    const docRef = db.collection("assinaturas").doc();
    const agora = Date.now();

    const novaAssinatura = {
      id: docRef.id,
      usuarioId: usuarioId,
      planoId: planoId,
      asaasSubscriptionId: asaasSub.id,
      ciclo: ciclo,
      status: "PENDENTE",
      dataInicio: agora,
      dataFim: null,
      proximaCobranca: new Date(asaasSub.nextDueDate).getTime(),
      criadoEm: agora,
    };

    await docRef.set(novaAssinatura);

    return { subscriptionId: asaasSub.id, status: "PENDENTE" };
  } catch (error: any) {
    console.error("Erro ao criar assinatura no Asaas:", error?.response?.data || error.message);
    throw new Error(
      error?.response?.data?.errors?.[0]?.description ||
        "Falha ao criar assinatura no gateway Asaas."
    );
  }
}

async function criarAssinaturaQuinzenalNoAsaas(
  customerId: string,
  usuarioId: string,
  planoId: string,
  valor: number,
  billingType: string,
  asaasUrl: string,
  apiKey: string
): Promise<{ subscriptionId: string; status: string }> {
  const db = admin.firestore();
  const docRef = db.collection("assinaturas").doc();
  const dueDate = dataLocalSaoPaulo(1);
  try {
    const response = await axios.post(`${asaasUrl}/payments`, {
      customer: customerId,
      billingType,
      value: valor,
      dueDate,
      description: `TomaAí - Plano quinzenal`,
      externalReference: docRef.id,
    }, { headers: { access_token: apiKey, "Content-Type": "application/json" } });
    await docRef.set({
      id: docRef.id,
      usuarioId,
      planoId,
      asaasSubscriptionId: null,
      asaasPaymentId: response.data.id,
      asaasCustomerId: customerId,
      ciclo: "QUINZENAL",
      valor,
      formaPagamento: billingType,
      status: "PENDENTE",
      proximaCobranca: new Date(`${dueDate}T00:00:00-03:00`).getTime(),
      criadoEm: Date.now(),
    });
    return { subscriptionId: docRef.id, status: "PENDENTE" };
  } catch (error: any) {
    console.error("Erro ao criar a primeira cobrança quinzenal no Asaas:", error?.response?.data || error.message);
    throw new Error(error?.response?.data?.errors?.[0]?.description || "Falha ao criar cobrança quinzenal no Asaas.");
  }
}

export async function cancelarAssinaturaNoAsaas(
  asaasSubscriptionId: string
): Promise<boolean> {
  const asaasUrl = process.env.ASAAS_URL || "https://sandbox.asaas.com/api/v3";
  const apiKey = process.env.ASAAS_API_KEY || "";

  try {
    await axios.delete(`${asaasUrl}/subscriptions/${asaasSubscriptionId}`, {
      headers: {
        access_token: apiKey,
      },
    });

    const db = admin.firestore();
    const snapshot = await db
      .collection("assinaturas")
      .where("asaasSubscriptionId", "==", asaasSubscriptionId)
      .get();

    if (!snapshot.empty) {
      await snapshot.docs[0].ref.update({
        status: "CANCELADA",
        dataFim: Date.now(),
      });
    }

    return true;
  } catch (error: any) {
    console.error("Erro ao cancelar assinatura no Asaas:", error?.response?.data || error.message);
    return false;
  }
}
