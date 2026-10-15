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

  const snapshots = await Promise.all(COLECOES_DADOS_MEDICACAO.map((colecao) =>
    firestore.collection(colecao).where("usuarioId", "==", uid).get()
  ));
  const refsPorColecao = new Map<string, Map<string, admin.firestore.DocumentReference>>();
  COLECOES_DADOS_MEDICACAO.forEach((colecao, indice) => {
    refsPorColecao.set(colecao, new Map<string, admin.firestore.DocumentReference>(
      snapshots[indice].docs.map((documento) => [documento.ref.path, documento.ref] as const)
    ));
  });

  // Current alert documents use the dose ID as their own ID; include those even
  // if an older alert document lacks usuarioId, but only for this patient's doses.
  const doseSnapshot = snapshots[COLECOES_DADOS_MEDICACAO.indexOf("doses")];
  const alertas = refsPorColecao.get("alertas")!;
  for (const dose of doseSnapshot.docs) {
    const alertaRef = firestore.collection("alertas").doc(dose.id);
    alertas.set(alertaRef.path, alertaRef);
  }

  const removidos: Record<string, number> = {};
  for (const colecao of COLECOES_DADOS_MEDICACAO) {
    const refs = [...refsPorColecao.get(colecao)!.values()];
    removidos[colecao] = refs.length;
    for (let indice = 0; indice < refs.length; indice += 450) {
      const batch = firestore.batch();
      refs.slice(indice, indice + 450).forEach((ref) => batch.delete(ref));
      await batch.commit();
    }
  }
  return removidos;
}
