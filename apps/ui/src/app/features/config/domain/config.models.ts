export interface TenantConfig {
  id: string;
  businessCode: string;
  businessName: string;
  issuerRuc: string;
  issuerName: string;
  issuerTrade: string;
  issuerAddress: string;
  issuerUbigeo: string;
  sendMode: string;
  sunatEndpoint: string;
  solUser?: string;
  solPassword?: string;
  oseBaseUrl: string;
  oseApiToken?: string;
  certificateFile: string;
  certificateValidFrom: string;
  certificateValidTo: string;
  invoiceSeries: string;
  receiptSeries: string;
  creditNoteSeries: string;
  debitNoteSeries: string;
  environment: string;
  webhookUrl: string;
  maxDocumentsPerDay: number;
  active: boolean;
  countryCode: string;
}

export interface FiscalReadinessCheck {
  code: string;
  label: string;
  ready: boolean;
  required: boolean;
  detail: string;
}

export interface FiscalReadiness {
  businessCode: string;
  environment: string;
  sendMode: string;
  ready: boolean;
  blockers: number;
  checks: FiscalReadinessCheck[];
}
