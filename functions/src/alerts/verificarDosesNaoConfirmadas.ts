import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import { FcmNotificationService } from "../notifications/notificationService";

const fcmNotifier = new FcmNotificationService();

async function tokensDaConta(db: admin.firestore.Firestore, uid: string): Promise<string[]> {
  const snapshot = await db.collection("usuarios").doc(uid).collection("fcm_tokens").get();
  return [...new Set(snapshot.docs
    .map((documento) => documento.get("token"))
    .filter((token): token is string => typeof token === "string")
    .map((token) => token.trim())
    .filter(Boolean))];
}

export const verificarDosesNaoConfirmadas = functions.pubsub
  .schedule("every 1 minutes")
  .timeZone("America/Sao_Paulo")
  .onRun(async () => {
    const db = admin.firestore();
    const agora = new Date();
    const partesData = new Intl.DateTimeFormat("en-CA", {
      timeZone: "America/Sao_Paulo",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).formatToParts(agora);
    const dataLocal = Object.fromEntries(partesData.map(({ type, value }) => [type, value]));
    const hojeLocal = `${dataLocal.year}-${dataLocal.month}-${dataLocal.day}`;
    const snapshot = await db.collection("doses")
      .where("status", "==", "PENDENTE")
      .where("dataAgenda", "==", hojeLocal)
      .get();
    const agoraMs = Date.now();
    const batch = db.batch();
    const notificacoes: Array<() => Promise<void>> = [];

    for (const doseDoc of snapshot.docs) {
      const dose = doseDoc.data();
      const programado = new Date(`${dose.dataAgenda}T${dose.horarioProgramado}:00-03:00`).getTime();
      if (!Number.isFinite(programado) || programado > agoraMs) continue;

      const pacienteUid = typeof dose.usuarioId === "string" ? dose.usuarioId : "";
      if (!pacienteUid) continue;
      const paciente = (await db.collection("usuarios").doc(pacienteUid).get()).data();
      if (paciente?.perfil !== "PACIENTE") continue;

      const tokens = new Set<string>();
      const destinatarios = new Set<string>();
      const vinculos = await db.collection("vinculos")
        .where("pacienteUid", "==", pacienteUid)
        .where("ativo", "==", true)
        .get();

      for (const vinculoDoc of vinculos.docs) {
        const vinculo = vinculoDoc.data();
        const cuidadorUid = typeof vinculo.cuidadorUid === "string" ? vinculo.cuidadorUid : "";
        if (!cuidadorUid || vinculo.pacienteUid !== pacienteUid) continue;
        const cuidador = (await db.collection("usuarios").doc(cuidadorUid).get()).data();
        if (!cuidador || !["PESSOA_DE_CONFIANCA", "CUIDADOR"].includes(cuidador.perfil) ||
            cuidador.pacienteUid !== pacienteUid || cuidador.aceitouReceberAvisos !== true) continue;
        const tokensDaPessoa = await tokensDaConta(db, cuidadorUid);
        tokensDaPessoa.forEach((token) => tokens.add(token));
        if (tokensDaPessoa.length > 0) destinatarios.add(cuidadorUid);
      }

      // Preserve support for legacy contact records that already contain an FCM token.
      const responsavelId = typeof paciente.responsavelPadraoId === "string"
        ? paciente.responsavelPadraoId
        : "";
      if (responsavelId) {
        const contato = (await db.collection("pessoas_confianca").doc(responsavelId).get()).data();
        if (contato && contato.usuarioId === pacienteUid && contato.aceitouReceberAvisos === true &&
            typeof contato.fcmToken === "string" && contato.fcmToken.trim()) {
          tokens.add(contato.fcmToken.trim());
          destinatarios.add(responsavelId);
        }
      }

      const alertaRef = db.collection("alertas").doc(doseDoc.id);
      const existente = await alertaRef.get();
      if (existente.exists && existente.get("status") === "ENVIADA") continue;
      if (existente.exists && existente.get("status") === "SEM_CANAL_CONFIGURADO" && tokens.size === 0) continue;

      batch.set(alertaRef, {
        usuarioId: pacienteUid,
        responsavelId: responsavelId || [...destinatarios][0] || null,
        doseId: doseDoc.id,
        medicamentoNome: dose.medicamentoNome || "Medicamento",
        horarioProgramado: dose.horarioProgramado,
        status: tokens.size > 0 ? "PENDENTE_ENVIO" : "SEM_CANAL_CONFIGURADO",
        criadoEm: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });

      if (tokens.size > 0) {
        notificacoes.push(async () => {
          const resultados = await Promise.allSettled([...tokens].map((token) =>
            fcmNotifier.notify(
              token,
              "Hora do medicamento",
              `Está na hora de tomar ${dose.medicamentoNome || "seu medicamento"}.`,
              { doseId: doseDoc.id }
            )
          ));
          const enviadas = resultados.filter((resultado) => resultado.status === "fulfilled").length;
          if (enviadas > 0) {
            await alertaRef.update({
              status: "ENVIADA",
              quantidadeDispositivosNotificados: enviadas,
              enviadaEm: admin.firestore.FieldValue.serverTimestamp(),
            });
          } else {
            console.error("Falha ao enviar push de dose não confirmada para os dispositivos vinculados.");
            await alertaRef.update({ status: "FALHA_ENVIO" });
          }
        });
      }
    }
    await batch.commit();
    await Promise.all(notificacoes.map((enviar) => enviar()));
    return null;
  });
