import axios from "axios";
import * as admin from "firebase-admin";
import { dataLocalSaoPaulo } from "../utils/data";

export async function sincronizarCobrancasNoAsaas(
  assinaturaId: string,
  usuarioId: string
): Promise<unknown[]> {
  const asaasUrl = process.env.ASAAS_URL || "https://sandbox.asaas.com/api/v3";
  const apiKey = process.env.ASAAS_API_KEY || "";

  try {
    if (!apiKey) throw new Error("ASAAS_API_KEY não configurada no ambiente do backend.");
    const db = admin.firestore();
    const assinaturaSnapshot = await db.collection("assinaturas").doc(assinaturaId).get();
    const assinatura = assinaturaSnapshot.data();
    if (!assinatura || assinatura.usuarioId !== usuarioId) {
      throw new Error("Assinatura não pertence ao usuário autenticado.");
    }
    const url = assinatura.asaasSubscriptionId
      ? `${asaasUrl}/subscriptions/${encodeURIComponent(assinatura.asaasSubscriptionId)}/payments`
      : `${asaasUrl}/payments?externalReference=${encodeURIComponent(assinaturaId)}&limit=100`;
    const response = await axios.get(
      url,
      {
        headers: {
          access_token: apiKey,
        },
      }
    );

    const payments = response.data.data || [];
    const batch = db.batch();

    for (const p of payments) {
      const docRef = db.collection("cobrancas").doc(p.id);
      batch.set(
        docRef,
        {
          id: p.id,
          assinaturaId,
          usuarioId: usuarioId,
          asaasPaymentId: p.id,
          valor: p.value,
          vencimento: p.dueDate,
          status: p.status === "RECEIVED" || p.status === "CONFIRMED" ? "RECEBIDA" : p.status,
          formaPagamento: p.billingType,
          invoiceUrl: p.invoiceUrl || null,
          pagoEm: p.paymentDate ? new Date(p.paymentDate).getTime() : null,
          criadoEm: Date.now(),
        },
        { merge: true }
      );
    }

    await batch.commit();
    return payments;
  } catch (error: any) {
    console.error("Erro ao sincronizar cobranças no Asaas:", error);
    throw new Error("Falha ao consultar cobranças no gateway Asaas.");
  }
}

export async function obterPixDaCobranca(asaasPaymentId: string): Promise<{ encodedImage: string; payload: string; expirationDate: string }> {
  const asaasUrl = process.env.ASAAS_URL || "https://sandbox.asaas.com/api/v3";
  const apiKey = process.env.ASAAS_API_KEY || "";
  if (!apiKey) throw new Error("ASAAS_API_KEY não configurada no ambiente do backend.");
  const response = await axios.get(
    `${asaasUrl}/payments/${encodeURIComponent(asaasPaymentId)}/pixQrCode`,
    { headers: { access_token: apiKey } }
  );
  return response.data;
}

export async function removerCobrancaPendente(asaasPaymentId: string): Promise<void> {
  const asaasUrl = process.env.ASAAS_URL || "https://sandbox.asaas.com/api/v3";
  const apiKey = process.env.ASAAS_API_KEY || "";
  if (!apiKey) throw new Error("ASAAS_API_KEY não configurada no ambiente do backend.");
  const url = `${asaasUrl}/payments/${encodeURIComponent(asaasPaymentId)}`;
  const headers = { access_token: apiKey };
  const payment = await axios.get(url, { headers });
  if (["PENDING", "OVERDUE"].includes(payment.data.status)) await axios.delete(url, { headers });
}

export async function criarProximaCobrancaQuinzenal(
  assinaturaId: string,
  customerId: string,
  valor: number,
  billingType: string
): Promise<{ id: string; dueDate: string }> {
  const asaasUrl = process.env.ASAAS_URL || "https://sandbox.asaas.com/api/v3";
  const apiKey = process.env.ASAAS_API_KEY || "";
  if (!apiKey) throw new Error("ASAAS_API_KEY não configurada no ambiente do backend.");
  const headers = { access_token: apiKey, "Content-Type": "application/json" };
  const response = await axios.get(`${asaasUrl}/payments?externalReference=${encodeURIComponent(assinaturaId)}&limit=100`, { headers });
  const pagamentos = response.data.data || [];
  const ultimaCobranca = pagamentos.sort((a: { dueDate?: string }, b: { dueDate?: string }) =>
    (b.dueDate || "").localeCompare(a.dueDate || "")
  )[0];
  if (ultimaCobranca?.status === "PENDING") return { id: ultimaCobranca.id, dueDate: ultimaCobranca.dueDate };
  if (ultimaCobranca?.status === "OVERDUE") throw new Error("A última cobrança quinzenal venceu sem pagamento.");

  const dueDate = dataLocalSaoPaulo();
  const created = await axios.post(`${asaasUrl}/payments`, {
    customer: customerId,
    billingType,
    value: valor,
    dueDate,
    description: "TomaAí - Plano quinzenal",
    externalReference: assinaturaId,
  }, { headers });
  return { id: created.data.id, dueDate };
}
