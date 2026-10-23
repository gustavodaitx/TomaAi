import * as admin from "firebase-admin";

export interface NotificationService {
  notify(recipient: string, title: string, body: string, data?: Record<string, string>): Promise<void>;
}

/** FCM adapter for device tokens registered to an authenticated user. */
export class FcmNotificationService implements NotificationService {
  async notify(recipient: string, title: string, body: string, data: Record<string, string> = {}): Promise<void> {
    const token = recipient.trim();
    if (!token) return;
    await admin.messaging().send({
      token,
      data: { ...data, title, body },
      android: { priority: "high" },
    });
  }

  async sendData(recipient: string, data: Record<string, string>): Promise<void> {
    const token = recipient.trim();
    if (!token) return;
    await admin.messaging().send({
      token,
      data,
      android: { priority: "high" },
    });
  }
}
