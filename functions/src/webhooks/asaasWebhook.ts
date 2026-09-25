import { Request, Response } from "express";
import * as admin from "firebase-admin";
import { marcarEventoProcessado, verificarERegistrarEvento } from "../utils/idempotencia";

export async function processarAsaasWebhook(req: Request, res: Response): Promise<void> {
  const tokenHeader = req.headers["asaas-access-token"];
  const expectedToken = process.env.ASAAS_WEBHOOK_TOKEN;

  if (!expectedToken) {
    console.error("ASAAS_WEBHOOK_TOKEN não configurado.");
    res.status(503).json({ error: "Webhook não configurado." });
    return;
  }
  if (typeof tokenHeader !== "string" || tokenHeader !== expectedToken) {
    res.status(401).json({ error: "Token de webhook inválido." });
    return;
  }

  const { event, payment, subscription } = req.body;

  if (!event) {
    res.status(400).json({ error: "Evento não especificado no payload." });
    return;
  }

  const resourceId = payment?.id || subscription?.id;
  if (!resourceId) {
    res.status(400).json({ error: "Recurso do evento ausente no payload." });
    return;
  }
  const subscriptionId = payment?.subscription || subscription?.id;
  const cobrancaId = payment?.id;

  // 1. Verificação de Idempotência
  const { duplicado, eventoId } = await verificarERegistrarEvento(
    event,
    resourceId,
    subscriptionId,
    cobrancaId,
    req.body.id
  );

  if (duplicado) {
    console.log(`Evento ${event} com ID ${resourceId} já processado anteriormente.`);
    res.status(200).json({ status: "Evento já processado (idempotente)." });
    return;
  }

  try {
    const db = admin.firestore();
    const externalReference = payment?.externalReference as string | undefined;
    const assinaturaPorReferencia = externalReference
      ? await db.collection("assinaturas").doc(externalReference).get()
      : null;
    const assinaturaSnapshot = !assinaturaPorReferencia?.exists && subscriptionId
      ? await db.collection("assinaturas").where("asaasSubscriptionId", "==", subscriptionId).limit(1).get()
      : null;
    const assinaturaRef = assinaturaPorReferencia?.exists
      ? assinaturaPorReferencia.ref
      : assinaturaSnapshot && !assinaturaSnapshot.empty ? assinaturaSnapshot.docs[0].ref : null;
    const assinaturaData = assinaturaPorReferencia?.exists
      ? assinaturaPorReferencia.data()
      : assinaturaSnapshot && !assinaturaSnapshot.empty ? assinaturaSnapshot.docs[0].data() : null;

    // 2. Processamento dos Eventos
    switch (event) {
      case "PAYMENT_RECEIVED":
      case "PAYMENT_CONFIRMED": {
        if (cobrancaId) {
          await db.collection("cobrancas").doc(cobrancaId).set(
            {
              id: cobrancaId,
              asaasPaymentId: cobrancaId,
              ...(assinaturaData?.usuarioId ? { usuarioId: assinaturaData.usuarioId } : {}),
              ...(assinaturaRef ? { assinaturaId: assinaturaRef.id } : {}),
              ...(payment?.value !== undefined ? { valor: payment.value } : {}),
              ...(payment?.dueDate ? { vencimento: payment.dueDate } : {}),
              ...(payment?.billingType ? { formaPagamento: payment.billingType } : {}),
              status: "RECEBIDA",
              pagoEm: Date.now(),
            },
            { merge: true }
          );
        }
        if (assinaturaRef && assinaturaData?.status !== "CANCELADA") {
          const atualizacao: Record<string, unknown> = { status: "ATIVA", asaasPaymentId: cobrancaId };
          if (assinaturaData?.ciclo === "QUINZENAL") {
            const dataPagamento = payment?.paymentDate || payment?.confirmedDate;
            const proximoPagamento = dataPagamento
              ? new Date(`${String(dataPagamento).slice(0, 10)}T00:00:00-03:00`)
              : new Date();
            proximoPagamento.setDate(proximoPagamento.getDate() + 15);
            atualizacao.proximaCobranca = proximoPagamento.getTime();
          }
          await assinaturaRef.update(atualizacao);
        }
        break;
      }

      case "PAYMENT_OVERDUE": {
        if (cobrancaId) {
          await db.collection("cobrancas").doc(cobrancaId).set(
            {
              id: cobrancaId,
              asaasPaymentId: cobrancaId,
              ...(assinaturaData?.usuarioId ? { usuarioId: assinaturaData.usuarioId } : {}),
              ...(assinaturaRef ? { assinaturaId: assinaturaRef.id } : {}),
              status: "VENCIDA",
            },
            { merge: true }
          );
        }
        if (assinaturaRef && assinaturaData?.status !== "CANCELADA") {
          await assinaturaRef.update({ status: "INADIMPLENTE" });
        }
        break;
      }

      case "SUBSCRIPTION_DELETED": {
        if (assinaturaRef) await assinaturaRef.update({ status: "CANCELADA", dataFim: Date.now() });
        break;
      }

      default:
        console.log(`Evento não mapeado: ${event}`);
    }

    // 3. Atualiza o status do evento no Firestore para PROCESSADO
    await marcarEventoProcessado(eventoId, "PROCESSADO");
    res.status(200).json({ status: "Evento processado com sucesso." });
  } catch (error: unknown) {
    console.error(`Erro ao processar evento do webhook ${event}:`, error);
    await marcarEventoProcessado(eventoId, "ERRO");
    res.status(500).json({ error: "Erro interno no processamento do webhook." });
  }
}
