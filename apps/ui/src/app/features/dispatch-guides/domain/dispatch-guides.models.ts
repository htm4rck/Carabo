export interface DispatchGuideSummary {
  updatedAt?: string | null;
  createdAt?: string | null;
  nextRetryAt?: string | null;
  retryCount?: number;
  maxRetryCount?: number;
  canRetry?: boolean;
  automaticRetry?: boolean;
  retryAction?: string | null;
  retryBlocker?: string | null;
  id: string;
  fullNumber: string | null;
  transportAuthorized: boolean;
  sunatResponseCode?: string | null;
  status: string;
  issueDate: string;
  startDate: string;
  receiverName: string;
  receiverDoc: string;
  transportMode: string;
  originAddress: string;
  destinationAddress: string;
  transferReasonCode: string;
  externalId: string;
  hashCode: string;
  ticket: string | null;
  sunatResponse: string | null;
}
