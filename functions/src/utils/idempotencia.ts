import * as admin from "firebase-admin";
import { createHash } from "crypto";

export async function verificarERegistrarEvento(
  tipoEvento: string,
  resourceId: string,
  assinaturaId?: string,
  cobrancaId?: string,
  identificadorEvento?: string
): Promise<{ duplicado: boolean; eventoId: string }> {
  const db = admin.firestore();
  const collection = db.collection("eventos_webhook");
  const identity = identificadorEvento || `${tipoEvento}:${resourceId}`;
  const eventoId = createHash("sha256").update(identity).digest("hex");
  const docRef = collection.doc(eventoId);
  const agora = Date.now();
  return db.runTransaction(async (transaction) => {
    const existing = await transaction.get(docRef);
    if (existing.exists && existing.get("statusProcessamento") === "PROCESSADO") {
      return { duplicado: true, eventoId: docRef.id };
    }
    const inicioAnterior = existing.get("processamentoIniciadoEm") as number | undefined;
    const processamentoAtivo = existing.get("statusProcessamento") === "PROCESSANDO"
      && inicioAnterior !== undefined && agora - inicioAnterior < 5 * 60 * 1000;
    if (processamentoAtivo) return { duplicado: true, eventoId: docRef.id };

    const dadosEvento = {
      id: docRef.id,
      tipoEvento,
      asaasResourceId: resourceId,
      assinaturaId: assinaturaId || null,
      cobrancaId: cobrancaId || null,
      statusProcessamento: "PROCESSANDO",
      processamentoIniciadoEm: agora,
      recebidoEm: agora,
      processadoEm: null,
    };
    if (existing.exists) transaction.set(docRef, dadosEvento);
    else transaction.create(docRef, dadosEvento);
    return { duplicado: false, eventoId: docRef.id };
  });
}

export async function marcarEventoProcessado(
  eventoId: string,
  status: "PROCESSADO" | "ERRO"
): Promise<void> {
  const db = admin.firestore();
  await db.collection("eventos_webhook").doc(eventoId).update({
    statusProcessamento: status,
    processadoEm: Date.now(),
  });
}
