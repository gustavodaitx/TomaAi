import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import { LoggingNotificationService } from "../notifications/notificationService";

const notifier = new LoggingNotificationService();
const LIMITE_ATRASO_MS = 30 * 60 * 1000;

export const verificarDosesNaoConfirmadas = functions.pubsub
  .schedule("every 15 minutes")
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
    const limite = Date.now() - LIMITE_ATRASO_MS;
    const batch = db.batch();
    const notificacoes: Promise<void>[] = [];

    for (const doseDoc of snapshot.docs) {
      const dose = doseDoc.data();
      const programado = new Date(`${dose.dataAgenda}T${dose.horarioProgramado}:00-03:00`).getTime();
      if (!Number.isFinite(programado) || programado > limite) continue;
      const usuarioRef = db.collection("usuarios").doc(dose.usuarioId);
      const usuario = (await usuarioRef.get()).data();
      const responsavelId = usuario?.responsavelPadraoId;
      if (!responsavelId) continue;
      const pessoaRef = db.collection("pessoas_confianca").doc(responsavelId);
      const pessoa = (await pessoaRef.get()).data();
      if (!pessoa || pessoa.usuarioId !== dose.usuarioId || pessoa.aceitouReceberAvisos !== true) continue;

      const alertaRef = db.collection("alertas").doc(doseDoc.id);
      const existente = await alertaRef.get();
      if (existente.exists) continue;
      batch.create(alertaRef, {
        usuarioId: dose.usuarioId,
        responsavelId,
        doseId: doseDoc.id,
        medicamentoNome: dose.medicamentoNome || "Medicamento",
        horarioProgramado: dose.horarioProgramado,
        status: "ABERTO",
        criadoEm: admin.firestore.FieldValue.serverTimestamp(),
      });
      notificacoes.push(notifier.notify(
        responsavelId,
        "Dose não confirmada",
        `A dose de ${dose.medicamentoNome || "um medicamento"} ainda não foi confirmada.`
      ));
    }
    await batch.commit();
    await Promise.all(notificacoes);
    return null;
  });
