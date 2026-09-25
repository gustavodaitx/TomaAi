export interface NotificationService {
  notify(recipient: string, title: string, body: string): Promise<void>;
}

/** Development adapter. Replace with FCM, SMS, or email without coupling alert rules to a provider. */
export class LoggingNotificationService implements NotificationService {
  async notify(recipient: string, title: string, body: string): Promise<void> {
    void body;
    console.info("Notification stub", { recipient, title });
  }
}
