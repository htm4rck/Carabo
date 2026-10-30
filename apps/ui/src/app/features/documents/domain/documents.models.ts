export interface DocumentSummary {
  id: string;
  /**
   * Carabo assigns the fiscal correlativo only when the document is ready to
   * issue. A persisted intent blocked by fiscal configuration has no number
   * yet.
   */
  fullNumber: string | null;
  docType: string;
  docTypeName: string;
  status: string;
  total: number;
  currency: string;
  receiverName: string;
  receiverDoc: string;
  issueDate: string;
  hashCode: string;
  externalId: string;
  businessCode: string;
  sunatResponse: string;
  createdAt: string;
  timeZone: string;
}

export interface QueryResponse {
  items: DocumentSummary[];
  total: number;
  offset: number;
  limit: number;
}

export interface QueryParams {
  businessCode?: string;
  docType?: string;
  status?: string;
  dateFrom?: string;
  dateTo?: string;
  receiverDoc?: string;
  series?: string;
  search?: string;
  offset?: number;
  limit?: number;
}
