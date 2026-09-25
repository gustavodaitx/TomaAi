const RESEND_EMAILS_URL = "https://api.resend.com/emails";

export interface EmailNotificationService {
  sendDoseReminder(to: string, patientName: string, medicineName: string, scheduledTime: string, idempotencyKey: string): Promise<void>;
}

/** Sends a transactional email through Resend. The API key is supplied as a Firebase secret. */
export class ResendEmailNotificationService implements EmailNotificationService {
  async sendDoseReminder(
    to: string,
    patientName: string,
    medicineName: string,
    scheduledTime: string,
    idempotencyKey: string
  ): Promise<void> {
    const apiKey = process.env.RESEND_API_KEY;
    const from = process.env.RESEND_FROM_EMAIL;
    if (!apiKey || !from) throw new Error("E-mail não configurado: defina RESEND_API_KEY e RESEND_FROM_EMAIL.");

    const response = await fetch(RESEND_EMAILS_URL, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify({
        from,
        to: [to],
        subject: "Lembrete: dose ainda não confirmada",
        text: `Olá! A dose de ${medicineName} de ${patientName}, programada para ${scheduledTime}, continua sem confirmação. Por favor, entre em contato com o paciente para lembrá-lo de tomar o medicamento, se ainda não o fez. Este aviso é automático.`,
      }),
    });

    if (!response.ok) {
      const detail = await response.text();
      throw new Error(`O serviço de e-mail respondeu HTTP ${response.status}: ${detail.slice(0, 300)}`);
    }
  }
}
