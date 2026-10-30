export interface DispatchGuideSummary {
  id: string;
  fullNumber: string;
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
