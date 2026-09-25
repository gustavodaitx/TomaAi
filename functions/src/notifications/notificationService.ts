import * as admin from "firebase-admin";

export interface NotificationService {
  notify(recipient: string, title: string, body: string): Promise<void>;
}

/** FCM adapter for trusted device tokens stored on a person of trust. */
export class FcmNotificationService implements NotificationService {
  async notify(recipient: string, title: string, body: string): Promise<void> {
    const token = recipient.trim();
    if (!token) return;
    await admin.messaging().send({
      token,
      notification: { title, body },
    });
  }
}
