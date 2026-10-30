export interface SalesReport {
  businessCode: string;
  from: string;
  to: string;
  totalDocuments: number;
  totalTaxBase: number;
  totalIgv: number;
  totalAmount: number;
  byType: { docType: string; count: number; taxBase: number; igvAmount: number; total: number }[];
}
