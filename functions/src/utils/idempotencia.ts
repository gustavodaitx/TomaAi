import * as admin from "firebase-admin";

export async function verificarERegistrarEvento(
  tipoEvento: string,
  resourceId: string,
  assinaturaId?: string,
  cobrancaId?: string
): Promise<{ duplicado: Boolean; eventoId: string }> {
  const db = admin.firestore();
  const collection = db.collection("eventos_webhook");

  // Consulta por evento e recurso no Asaas
  const snapshot = await collection
    .where("tipoEvento", "==", tipoEvento)
    .where("asaasResourceId", "==", resourceId)
    .get();

  if (!snapshot.empty) {
    const docExistente = snapshot.docs[0];
    return { duplicado: true, eventoId: docExistente.id };
  }

  // Registra novo evento com status RECEBIDO
  const docRef = collection.doc();
  const agora = Date.now();

  await docRef.set({
    id: docRef.id,
    tipoEvento: tipoEvento,
    asaasResourceId: resourceId,
    assinaturaId: assinaturaId || null,
    cobrancaId: cobrancaId || null,
    statusProcessamento: "RECEBIDO",
    recebidoEm: agora,
    processadoEm: null,
  });

  return { duplicado: false, eventoId: docRef.id };
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
