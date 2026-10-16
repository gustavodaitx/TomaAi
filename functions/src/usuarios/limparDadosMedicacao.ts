import * as admin from "firebase-admin";

const COLECOES_DADOS_MEDICACAO = [
  "medicamentos",
  "horarios_medicamentos",
  "doses",
  "alertas",
] as const;

/** Removes medication-only documents owned by one authenticated patient. */
export async function limparDadosMedicacao(
  uid: string,
  firestore: admin.firestore.Firestore = admin.firestore()
): Promise<Record<string, number>> {
  if (!uid.trim()) throw new Error("UID autenticado obrigatório.");

  let snapshots: admin.firestore.QuerySnapshot[];
  try {
    snapshots = await Promise.all(COLECOES_DADOS_MEDICACAO.map((colecao) =>
      firestore.collection(colecao).where("usuarioId", "==", uid).get()
    ));
  } catch (_error) {
    throw new Error("Falha ao localizar dados de medicação no Firestore.");
  }

  const refsPorColecao = new Map<string, Map<string, admin.firestore.DocumentReference>>();
  COLECOES_DADOS_MEDICACAO.forEach((colecao, indice) => {
    refsPorColecao.set(colecao, new Map<string, admin.firestore.DocumentReference>(
      snapshots[indice].docs.map((documento) => [documento.ref.path, documento.ref] as const)
    ));
  });

  // Alert documents use the dose ID. Include a legacy alert without usuarioId
  // only when its matching dose belongs to the authenticated patient.
  const doseSnapshot = snapshots[COLECOES_DADOS_MEDICACAO.indexOf("doses")];
  const alertas = refsPorColecao.get("alertas")!;
  for (const dose of doseSnapshot.docs) {
    const alertaRef = firestore.collection("alertas").doc(dose.id);
    if (alertas.has(alertaRef.path)) continue;
    try {
      if ((await alertaRef.get()).exists) alertas.set(alertaRef.path, alertaRef);
    } catch (_error) {
      throw new Error("Falha ao verificar alertas relacionados às doses.");
    }
  }

  const removidos: Record<string, number> = {};
  for (const colecao of COLECOES_DADOS_MEDICACAO) {
    const refs = [...refsPorColecao.get(colecao)!.values()];
    let removidosNaColecao = 0;
    for (let indice = 0; indice < refs.length; indice += 450) {
      const loteRefs = refs.slice(indice, indice + 450);
      const batch = firestore.batch();
      loteRefs.forEach((ref) => batch.delete(ref));
      try {
        await batch.commit();
        const aposExclusao = await Promise.all(loteRefs.map((ref) => ref.get()));
        if (aposExclusao.some((documento) => documento.exists)) {
          throw new Error("documentos ainda existentes");
        }
        removidosNaColecao += loteRefs.length;
      } catch (_error) {
        throw new Error(`Falha ao excluir/verificar a coleção ${colecao}.`);
      }
    }
    removidos[colecao] = removidosNaColecao;
  }

  // Success is returned only after owner-scoped server queries confirm that
  // no documents remain in any cleanup collection.
  for (const colecao of COLECOES_DADOS_MEDICACAO) {
    try {
      const restantes = await firestore.collection(colecao).where("usuarioId", "==", uid).limit(1).get();
      if (!restantes.empty) throw new Error("documentos restantes");
    } catch (_error) {
      throw new Error(`Falha ao validar a coleção ${colecao}.`);
    }
  }
  return removidos;
}
