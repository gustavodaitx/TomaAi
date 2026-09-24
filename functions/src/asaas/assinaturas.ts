import axios from "axios";
import * as admin from "firebase-admin";

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

  // Mapeamento do ciclo conforme a documentação oficial da API Asaas
  // QUINZENAL -> BIWEEKLY (a cada 15 dias / 2 semanas)
  // MENSAL -> MONTHLY
  const cycleMapped = ciclo.toUpperCase() === "QUINZENAL" ? "BIWEEKLY" : "MONTHLY";

  // Data de vencimento da primeira cobrança (amanhã)
  const amanha = new Date();
  amanha.setDate(amanha.getDate() + 1);
  const nextDueDate = amanha.toISOString().split("T")[0];

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
