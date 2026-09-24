import axios from "axios";
import * as admin from "firebase-admin";

export async function sincronizarCobrancasNoAsaas(
  asaasSubscriptionId: string,
  usuarioId: string
): Promise<any[]> {
  const asaasUrl = process.env.ASAAS_URL || "https://sandbox.asaas.com/api/v3";
  const apiKey = process.env.ASAAS_API_KEY || "";

  try {
    const response = await axios.get(
      `${asaasUrl}/subscriptions/${asaasSubscriptionId}/payments`,
      {
        headers: {
          access_token: apiKey,
        },
      }
    );

    const payments = response.data.data || [];
    const db = admin.firestore();
    const batch = db.batch();

    for (const p of payments) {
      const docRef = db.collection("cobrancas").doc(p.id);
      batch.set(
        docRef,
        {
          id: p.id,
          assinaturaId: asaasSubscriptionId,
          usuarioId: usuarioId,
          asaasPaymentId: p.id,
          valor: p.value,
          vencimento: p.dueDate,
          status: p.status === "RECEIVED" || p.status === "CONFIRMED" ? "RECEBIDA" : p.status,
          formaPagamento: p.billingType,
          pagoEm: p.paymentDate ? new Date(p.paymentDate).getTime() : null,
          criadoEm: Date.now(),
        },
        { merge: true }
      );
    }

    await batch.commit();
    return payments;
  } catch (error: any) {
    console.error("Erro ao sincronizar cobranças no Asaas:", error?.response?.data || error.message);
    return [];
  }
}
