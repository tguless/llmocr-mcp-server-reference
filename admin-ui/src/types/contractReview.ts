/**
 * TypeScript interfaces for Contract Review JSON Schema
 * Used for contract variance assessment
 */

export interface ContractObligation {
  ppaId: string;
  clauseReference: string;
  obligationCategory: 'Reporting' | 'Deliverable' | 'Performance' | 'Financial' | 'Regulatory' | 'Insurance' | 'Maintenance' | 'Testing' | 'Documentation' | 'Compliance' | 'Other';
  obligationDescription: string;
  obligationSummary: string;
  clauseText: string;
  task: string;
  responsibleParty: string;
  counterparty?: string;
  contractDate?: string; // MM/DD/YYYY
  firstTaskDueDate?: string; // MM/DD/YYYY
  obligationFrequency?: 'One-time' | 'Daily' | 'Weekly' | 'Monthly' | 'Quarterly' | 'Semi-annually' | 'Annually' | 'As needed' | 'Other';
}

export interface ContractMetadata {
  contractName: string;
  fundName: string;
  projectName: string;
  contractType: string;
  codDate?: string; // MM/DD/YYYY
  technologies?: ('PV' | 'ESS' | 'BESS' | 'Wind' | 'Solar' | 'Other')[];
}

export interface ContractSummary {
  totalObligationsExtracted: number;
  totalMissingObligations: number;
  totalRisksUnclear: number;
  selfCheckQuestion?: string;
  selfCheckAnswer?: 'Yes' | 'No';
  selfCheckExplanation?: string;
}

export interface ContractExtraction {
  preCodObligations: ContractObligation[];
  postCodObligations: ContractObligation[];
}

export interface AssumptionLogEntry {
  logId: string; // A001, A002, etc.
  trigger: string;
  proposedAssumption: string;
  status: 'Pending Review' | 'Approved' | 'Rejected' | 'Clarified';
}

export interface MissingObligation {
  ppaId: string; // M001, M002, etc.
  missingObligation: string;
  reference?: string;
  severity: 'High' | 'Medium' | 'Low';
}

export interface RiskUnclear {
  ppaId: string; // R001, R002, etc.
  textExcerpt: string;
  issueType: 'Vague' | 'Wording Risk' | 'Scope Gap';
  comment?: string;
}

export interface IngestionIssue {
  sectionName: string;
  reason: string;
}

export interface ValidationChecks {
  clauseCount?: {
    contractClauses?: number;
    extractedRows?: number;
    match?: boolean;
  };
  keywordSearch?: {
    shall?: number;
    must?: number;
    deliver?: number;
    provide?: number;
  };
  noDuplicateIds?: boolean;
}

/**
 * Rate schedule for invoice variance assessment
 */
export interface ContractRate {
  rateId: string; // R001, R002, etc.
  rateType: 'Energy' | 'Demand' | 'Fuel' | 'Transmission' | 'Distribution' | 'Environmental' | 'Fixed' | 'Other';
  description: string;
  unitPrice: number; // Dollar amount
  unitOfMeasure: 'kWh' | 'kW' | 'Therm' | 'CCF' | 'Fixed' | 'Other';
  effectiveDate?: string; // MM/DD/YYYY
  expirationDate?: string; // MM/DD/YYYY
  timeOfUse?: string; // Peak, Off-Peak, etc.
  seasonalAdjustment?: string;
  minimumCharge?: number;
  maximumCharge?: number;
  clauseReference?: string;
  notes?: string;
}

export interface RateSchedule {
  scheduleName: string;
  accountNumbers?: string[]; // Which accounts this rate applies to
  rates: ContractRate[];
}

export interface ContractReviewData {
  id?: number; // Database ID
  metadata: ContractMetadata;
  summary: ContractSummary;
  extraction: ContractExtraction;
  rateSchedules?: RateSchedule[]; // FOR VARIANCE ASSESSMENT
  assumptionsLog: AssumptionLogEntry[];
  obligationsNotFound?: MissingObligation[];
  risksUnclear?: RiskUnclear[];
  ingestionIssues?: IngestionIssue[];
  validationChecks?: ValidationChecks;
  createdAt?: string;
  processedBy?: string;
  pdfJobId?: number;
  pdfUrl?: string; // For side-by-side view
}

export interface ContractGridRow {
  id: number;
  contractName: string;
  fundName: string;
  projectName: string;
  contractType: string;
  codDate?: string;
  technologies?: string[];
  totalObligations: number;
  totalMissing: number;
  totalRisks: number;
  totalRates: number;
  primaryEnergyRate?: number; // For variance comparison
  primaryDemandRate?: number;
  createdAt?: string;
  processedBy?: string;
}

