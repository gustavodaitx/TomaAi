import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import { ResendEmailNotificationService } from "../notifications/emailNotificationService";

const emailNotifier = new ResendEmailNotificationService();
const TOLERANCIA_ATRASO_MS = 30 * 60 * 1000;
const TRAVA_EXPIRA_MS = 10 * 60 * 1000;

function dataHojeSaoPaulo(agora: Date): string {
  const partes = new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(agora);
  const data = Object.fromEntries(partes.map(({ type, value }) => [type, value]));
  return `${data.year}-${data.month}-${data.day}`;
}

function obterInstanteProgramado(dataAgenda: string, horario: string): number {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dataAgenda) || !/^\d{2}:\d{2}$/.test(horario)) return NaN;
  return new Date(`${dataAgenda}T${horario}:00-03:00`).getTime();
}

export const verificarDosesNaoConfirmadas = functions
  .runWith({ secrets: ["RESEND_API_KEY"], timeoutSeconds: 120 })
  .pubsub.schedule("every 5 minutes")
  .timeZone("America/Sao_Paulo")
  .onRun(async () => {
    const db = admin.firestore();
    const agoraMs = Date.now();
    const hoje = dataHojeSaoPaulo(new Date(agoraMs));
    const snapshot = await db.collection("doses")
      .where("status", "==", "PENDENTE")
      .where("dataAgenda", "==", hoje)
      .get();

    for (const doseDoc of snapshot.docs) {
      // Re-read immediately before alerting so a just-confirmed dose is not reported.
      const atual = await doseDoc.ref.get();
      if (!atual.exists || atual.get("status") !== "PENDENTE") continue;
      const dose = atual.data() || {};
      const dataAgenda = String(dose.dataAgenda || dose.data || "");
      const horario = String(dose.horarioProgramado || dose.horario || "");
      const instanteProgramado = obterInstanteProgramado(dataAgenda, horario);
      if (!Number.isFinite(instanteProgramado) || agoraMs < instanteProgramado + TOLERANCIA_ATRASO_MS) continue;

      const usuarioId = String(dose.usuarioId || "");
      if (!usuarioId) continue;
      const usuario = (await db.collection("usuarios").doc(usuarioId).get()).data();
      const responsavelId = String(usuario?.responsavelPadraoId || "");
      if (!responsavelId) continue;
      const pessoaRef = db.collection("pessoas_confianca").doc(responsavelId);
      const pessoa = (await pessoaRef.get()).data();
      if (!pessoa || pessoa.usuarioId !== usuarioId || pessoa.aceitouReceberAvisos !== true) continue;

      const alertaRef = db.collection("alertas").doc(doseDoc.id);
      const podeEnviar = await db.runTransaction(async (transaction) => {
        const alerta = await transaction.get(alertaRef);
        if (alerta.get("status") === "ENVIADA") return false;
        const processandoEm = alerta.get("processandoEm") as admin.firestore.Timestamp | undefined;
        if (alerta.get("status") === "PROCESSANDO" && processandoEm &&
          agoraMs - processandoEm.toMillis() < TRAVA_EXPIRA_MS) return false;
        transaction.set(alertaRef, {
          usuarioId,
          responsavelId,
          doseId: doseDoc.id,
          medicamentoNome: dose.medicamentoNome || dose.nomeMedicamento || "Medicamento",
          horarioProgramado: horario,
          dataAgenda,
          status: "PROCESSANDO",
          processandoEm: admin.firestore.FieldValue.serverTimestamp(),
          atualizadoEm: admin.firestore.FieldValue.serverTimestamp(),
        }, { merge: true });
        return true;
      });
      if (!podeEnviar) continue;

      const alertaNome = String(dose.medicamentoNome || dose.nomeMedicamento || "Medicamento");
      const email = String(pessoa.email || "").trim();
      if (!email) {
        await alertaRef.update({ status: "SEM_EMAIL_CADASTRADO", erro: "A pessoa de confiança não tem e-mail cadastrado." });
        continue;
      }

      try {
        await emailNotifier.sendDoseReminder(
          email,
          String(usuario?.nome || "paciente"),
          alertaNome,
          horario,
          `dose-alert/${doseDoc.id}`
        );
        await alertaRef.update({
          status: "ENVIADA",
          enviadaEm: admin.firestore.FieldValue.serverTimestamp(),
          erro: admin.firestore.FieldValue.delete(),
        });
      } catch (error) {
        const mensagem = error instanceof Error ? error.message : "Falha desconhecida ao enviar e-mail.";
        console.error(`Falha no alerta da dose ${doseDoc.id}: ${mensagem}`);
        await alertaRef.update({
          status: "FALHA_ENVIO",
          erro: mensagem.slice(0, 500),
          atualizadoEm: admin.firestore.FieldValue.serverTimestamp(),
        });
      }
    }
    return null;
  });
