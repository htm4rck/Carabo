export type ProducerScope = 'DOCUMENT_INGEST' | 'DOCUMENT_READ' | 'DOCUMENT_EMIT';

export interface ProducerIntegration {
  id: string;
  code: string;
  name: string;
  description: string | null;
  scopes: ProducerScope[];
  status: 'ACTIVE' | 'REVOKED';
  health: 'HEALTHY' | 'PENDING' | 'IDLE' | 'DEGRADED' | 'REVOKED';
  createdAt: string;
  rotatedAt: string;
  lastUsedAt: string | null;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  lastHttpStatus: number | null;
  totalRequests: number;
  failureRequests: number;
  totalDocuments: number;
  lastDocumentAt: string | null;
}

export interface ProducerCredential {
  producer: ProducerIntegration;
  apiKey: string;
}

export interface IntegrationValidationIssue {
  path: string;
  code: string;
  severity: 'ERROR' | 'WARNING';
  message: string;
  expected: string;
  received: string | null;
  suggestion: string;
}

export interface IntegrationValidationReport {
  valid: boolean;
  issues: IntegrationValidationIssue[];
}
