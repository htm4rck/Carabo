export interface NotificationSummary {
  id: string;
  documentId: string;
  type: 'CALLBACK' | 'WEBHOOK';
  status: 'PENDING' | 'RETRY' | 'DELIVERED' | 'DEAD';
  attempts: number;
  lastError: string | null;
  nextAttemptAt: string | null;
  createdAt: string;
  deliveredAt: string | null;
}

export interface AsyncMessageSummary {
  id: string;
  externalId: string | null;
  status: 'RECEIVED' | 'PROCESSED' | 'DEAD' | 'REPLAYED';
  errorMessage: string | null;
  deliveryCount: number;
  replayCount: number;
  createdAt: string;
  updatedAt: string;
}
