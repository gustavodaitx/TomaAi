import axios from "axios";
import * as admin from "firebase-admin";
import { dataLocalSaoPaulo } from "../utils/data";

export type DadosCartaoAsaas = {
  nomeTitular: string; numero: string; validadeMes: string; validadeAno: string; cvv: string;
  cpf: string; cep: string; numeroEndereco: string; telefone: string; email: string; remoteIp: string;
};

function dadosPagamentoCartao(dados: DadosCartaoAsaas) {
  return {
    creditCard: { holderName: dados.nomeTitular, number: dados.numero, expiryMonth: dados.validadeMes, expiryYear: dados.validadeAno, ccv: dados.cvv },
    creditCardHolderInfo: { name: dados.nomeTitular, email: dados.email, cpfCnpj: dados.cpf, postalCode: dados.cep, addressNumber: dados.numeroEndereco, phone: dados.telefone, mobilePhone: dados.telefone },
    remoteIp: dados.remoteIp,
  };
}

export async function criarAssinaturaNoAsaas(
  customerId: string, usuarioId: string, planoId: string, ciclo: string, valor: number,
  billingType = "PIX", dadosCartao?: DadosCartaoAsaas
): Promise<{ subscriptionId: string; status: string }> {
  const asaasUrl = process.env.ASAAS_URL || "https://api-sandbox.asaas.com/v3";
  const apiKey = process.env.ASAAS_API_KEY || "";
  if (!apiKey) throw new Error("ASAAS_API_KEY não configurada no ambiente do backend.");
  if (billingType === "CREDIT_CARD" && !dadosCartao) throw new Error("Dados do cartão não informados.");
  if (ciclo.toUpperCase() === "QUINZENAL") return criarAssinaturaQuinzenalNoAsaas(customerId, usuarioId, planoId, valor, billingType, asaasUrl, apiKey, dadosCartao);
  if (ciclo.toUpperCase() !== "MENSAL") throw new Error("Ciclo de assinatura Asaas inválido.");

  const nextDueDate = dataLocalSaoPaulo(1);
  try {
    const response = await axios.post(`${asaasUrl}/subscriptions`, {
      customer: customerId, billingType, value: valor, nextDueDate, cycle: "MONTHLY",
      description: `Assinatura TomaAí - Plano ${ciclo}`,
      ...(billingType === "CREDIT_CARD" && dadosCartao ? dadosPagamentoCartao(dadosCartao) : {}),
    }, { headers: { access_token: apiKey, "Content-Type": "application/json" }, timeout: 60000 });
    const db = admin.firestore();
    const docRef = db.collection("assinaturas").doc();
    const agora = Date.now();
    await docRef.set({
      id: docRef.id, usuarioId, planoId, asaasSubscriptionId: response.data.id, ciclo,
      status: "PENDENTE", dataInicio: agora, dataFim: null,
      proximaCobranca: new Date(response.data.nextDueDate).getTime(), criadoEm: agora,
    });
    return { subscriptionId: docRef.id, status: "PENDENTE" };
  } catch (error: any) {
    console.error("Erro ao criar assinatura no Asaas:", error?.response?.data || error.message);
    throw new Error(error?.response?.data?.errors?.[0]?.description || "Falha ao criar assinatura no gateway Asaas.");
  }
}

async function criarAssinaturaQuinzenalNoAsaas(
  customerId: string, usuarioId: string, planoId: string, valor: number, billingType: string,
  asaasUrl: string, apiKey: string, dadosCartao?: DadosCartaoAsaas
): Promise<{ subscriptionId: string; status: string }> {
  const db = admin.firestore();
  const docRef = db.collection("assinaturas").doc();
  const dueDate = dataLocalSaoPaulo(1);
  const headers = { access_token: apiKey, "Content-Type": "application/json" };
  try {
    let token: string | undefined;
    if (billingType === "CREDIT_CARD" && dadosCartao) {
      const tokenResponse = await axios.post(`${asaasUrl}/creditCard/tokenizeCreditCard`, {
        customer: customerId, ...dadosPagamentoCartao(dadosCartao),
      }, { headers, timeout: 60000 });
      token = tokenResponse.data.creditCardToken;
      if (!token) throw new Error("O Asaas não retornou token para o cartão.");
    }
    const response = await axios.post(`${asaasUrl}/payments`, {
      customer: customerId, billingType, value: valor, dueDate, description: "TomaAí - Plano quinzenal",
      externalReference: docRef.id,
      ...(token ? { creditCardToken: token, remoteIp: dadosCartao?.remoteIp } : {}),
    }, { headers, timeout: 60000 });
    await docRef.set({
      id: docRef.id, usuarioId, planoId, asaasSubscriptionId: null, asaasPaymentId: response.data.id,
      asaasCustomerId: customerId,
      ciclo: "QUINZENAL", valor, formaPagamento: billingType, status: "PENDENTE",
      proximaCobranca: new Date(`${dueDate}T00:00:00-03:00`).getTime(), criadoEm: Date.now(),
    });
    if (token && dadosCartao) await db.collection("asaas_metodos_pagamento").doc(docRef.id).set({
      usuarioId, customerId, creditCardToken: token, remoteIp: dadosCartao.remoteIp, criadoEm: Date.now(),
    });
    return { subscriptionId: docRef.id, status: "PENDENTE" };
  } catch (error: any) {
    console.error("Erro ao criar cobrança quinzenal no Asaas:", error?.response?.data || error.message);
    throw new Error(error?.response?.data?.errors?.[0]?.description || error.message || "Falha ao criar cobrança quinzenal no Asaas.");
  }
}

export async function cancelarAssinaturaNoAsaas(asaasSubscriptionId: string): Promise<boolean> {
  const asaasUrl = process.env.ASAAS_URL || "https://api-sandbox.asaas.com/v3";
  const apiKey = process.env.ASAAS_API_KEY || "";
  try {
    await axios.delete(`${asaasUrl}/subscriptions/${asaasSubscriptionId}`, { headers: { access_token: apiKey } });
    const snapshot = await admin.firestore().collection("assinaturas").where("asaasSubscriptionId", "==", asaasSubscriptionId).get();
    if (!snapshot.empty) await snapshot.docs[0].ref.update({ status: "CANCELADA", dataFim: Date.now() });
    return true;
  } catch (error: any) {
    console.error("Erro ao cancelar assinatura no Asaas:", error?.response?.data || error.message);
    return false;
  }
}
