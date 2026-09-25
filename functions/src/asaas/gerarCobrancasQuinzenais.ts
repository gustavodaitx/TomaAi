import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import { criarProximaCobrancaQuinzenal, removerCobrancaPendente } from "./cobrancas";

export const gerarCobrancasQuinzenais = functions.pubsub
  .schedule("every 24 hours")
  .timeZone("America/Sao_Paulo")
  .onRun(async () => {
    const db = admin.firestore();
    const vencidas = await db.collection("assinaturas")
      .where("ciclo", "==", "QUINZENAL")
      .where("status", "==", "ATIVA")
      .where("proximaCobranca", "<=", Date.now())
      .limit(300)
      .get();

    for (const assinaturaDoc of vencidas.docs) {
      const agora = Date.now();
      const assinatura = await db.runTransaction(async (transaction) => {
        const atual = await transaction.get(assinaturaDoc.ref);
        if (atual.get("status") !== "ATIVA" || (atual.get("proximaCobranca") as number) > agora) return null;
        transaction.update(assinaturaDoc.ref, { proximaCobranca: agora + 60 * 60 * 1000 });
        return atual.data();
      });
      if (!assinatura) continue;
      try {
        const cobranca = await criarProximaCobrancaQuinzenal(
          assinaturaDoc.id,
          assinatura.asaasCustomerId,
          assinatura.valor,
          assinatura.formaPagamento || "PIX"
        );
        const dueDateMillis = new Date(`${cobranca.dueDate}T00:00:00-03:00`).getTime();
        const atualizado = await db.runTransaction(async (transaction) => {
          const atual = await transaction.get(assinaturaDoc.ref);
          if (atual.get("status") !== "ATIVA") return false;
          transaction.set(db.collection("cobrancas").doc(cobranca.id), {
            id: cobranca.id,
            asaasPaymentId: cobranca.id,
            assinaturaId: assinaturaDoc.id,
            usuarioId: assinatura.usuarioId,
            valor: assinatura.valor,
            vencimento: cobranca.dueDate,
            status: "PENDENTE",
            formaPagamento: assinatura.formaPagamento || "PIX",
            criadoEm: Date.now(),
          }, { merge: true });
          transaction.update(assinaturaDoc.ref, {
            status: "PENDENTE",
            asaasPaymentId: cobranca.id,
            proximaCobranca: dueDateMillis,
          });
          return true;
        });
        if (!atualizado) await removerCobrancaPendente(cobranca.id);
      } catch (error) {
        console.error(`Falha ao gerar cobrança quinzenal para assinatura ${assinaturaDoc.id}:`, error);
        await db.runTransaction(async (transaction) => {
          const atual = await transaction.get(assinaturaDoc.ref);
          if (atual.get("status") !== "ATIVA") return;
          transaction.update(assinaturaDoc.ref, error instanceof Error && error.message.includes("venceu sem pagamento")
            ? { status: "INADIMPLENTE" }
            : { proximaCobranca: assinatura.proximaCobranca });
        });
      }
    }
    return null;
  });
