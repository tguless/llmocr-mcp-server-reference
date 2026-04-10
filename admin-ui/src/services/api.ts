import axios from 'axios';

// For development, use localhost:8081 with full path (no /api suffix, endpoints include it)
// For production (Docker), use context root only, nginx will proxy /invoice/api/ to backend
const API_BASE_URL = process.env.REACT_APP_API_URL || 
  (process.env.NODE_ENV === 'production' 
    ? '/invoice'  // Production: just context root, endpoints already have /api
    : 'http://localhost:8081/mcp-invoice');  // Dev: direct to backend (no /api, endpoints have it)

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add auth token if available
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('authToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle 401 errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const requestUrl = typeof error?.config?.url === 'string' ? error.config.url : '';
    const isAuthEndpoint = requestUrl.includes('/api/auth/');

    if (error.response?.status === 401 && !isAuthEndpoint) {
      console.warn('Authentication failed. Redirecting to login...');
      // Clear tokens
      localStorage.removeItem('authToken');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('user');
      // Redirect to login (respect the basename/context root)
      // Detect if we're running under /invoice context
      const currentPath = window.location.pathname;
      const basename = currentPath.startsWith('/invoice') ? '/invoice' : '';
      window.location.href = `${basename}/login`;
    }
    return Promise.reject(error);
  }
);

export interface CategoryInfo {
  categoryCode: string;
  categoryName: string;
  description: string;
  categorizationInstructions: string;
  ruleCount: number;
  // Additional fields for UI display
  passThrough?: boolean;
  isCredit?: boolean;
  keywords?: string[];
  examples?: string[];
  statistics?: {
    totalItemsCategorized: number;
  };
}

export interface MetadataKeyInfo {
  keyCode: string;
  displayName: string;
  description: string;
  category: string;
  dataType: string;
  exampleValue?: string;
  required: boolean;
  billable: boolean;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface LineItemTransaction {
  lineItemId: number;
  id?: number; // Alias for lineItemId (for backward compatibility)
  invoiceId: number;
  invoiceNumber: string;
  vendorName: string;
  invoiceDate: string;
  invoiceTotal: number;
  lineNumber?: number;
  description: string;
  amount: number;
  lineTotal?: number; // Alias for amount (for backward compatibility)
  category: string | null;
  categoryName: string;
  categoryConfidence?: number;
  categorizedBy?: string;
  requiresReview: boolean;
  createdAt: string;
  updatedAt?: string;
  // Energy-specific fields
  energyUnit?: string;
  energyQuantity?: number;
  energyRate?: number;
  quantity?: number;
  unitPrice?: number;
}

// Category Management API - Using REST endpoints
export const categoryAPI = {
  // Get all categories with rules
  getCategories: async (): Promise<CategoryInfo[]> => {
    const response = await api.get('/api/categories');
    return response.data;
  },

  // Create new category
  createCategory: async (categoryData: {
    categoryCode: string;
    categoryName: string;
    description?: string;
    isPassThrough?: boolean;
    isCredit?: boolean;
    isBillable?: boolean;
    keywords?: string;
    examples?: string;
    categorizationInstructions?: string;
    displayOrder?: number;
  }) => {
    const response = await api.post('/api/categories', categoryData);
    return response.data;
  },

  // Update category
  updateCategory: async (categoryCode: string, updates: { description?: string; instructions?: string; keywords?: string }) => {
    const response = await api.put(`/api/categories/${categoryCode}`, updates);
    return response.data;
  },

  // Delete category (soft delete - sets isActive to false)
  deleteCategory: async (categoryCode: string) => {
    const response = await api.delete(`/api/categories/${categoryCode}`);
    return response.data;
  },
};

// Metadata Key Management API - Using REST endpoints (NEW)
export const metadataKeyAPI = {
  // Get all metadata keys
  getMetadataKeys: async (category?: string, active?: boolean): Promise<MetadataKeyInfo[]> => {
    const params: any = {};
    if (category) params.category = category;
    if (active !== undefined) params.active = active;
    const response = await api.get('/api/metadata-keys', { params });
    return response.data;
  },

  // Get a specific metadata key
  getMetadataKey: async (keyCode: string): Promise<MetadataKeyInfo> => {
    const response = await api.get(`/api/metadata-keys/${keyCode}`);
    return response.data;
  },

  // Get categories summary
  getCategoriesSummary: async (): Promise<any> => {
    const response = await api.get('/api/metadata-keys/categories/summary');
    return response.data;
  },

  // Create a new metadata key
  createMetadataKey: async (data: Partial<MetadataKeyInfo>): Promise<any> => {
    const response = await api.post('/api/metadata-keys', data);
    return response.data;
  },

  // Update a metadata key
  updateMetadataKey: async (keyCode: string, updates: Partial<MetadataKeyInfo>): Promise<any> => {
    const response = await api.put(`/api/metadata-keys/${keyCode}`, updates);
    return response.data;
  },

  // Delete a metadata key
  deleteMetadataKey: async (keyCode: string): Promise<any> => {
    const response = await api.delete(`/api/metadata-keys/${keyCode}`);
    return response.data;
  },
};

// Transaction Viewer API - Using REST endpoints
export const transactionAPI = {
  // Get all line items with invoice information
  getAllTransactions: async (): Promise<LineItemTransaction[]> => {
    const response = await api.get('/api/transactions');
    return response.data;
  },

  // Get line items by category
  getByCategory: async (category: string): Promise<LineItemTransaction[]> => {
    const response = await api.get('/api/transactions', {
      params: { category },
    });
    return response.data;
  },

  // Export all transactions to Excel
  exportToExcel: async (): Promise<void> => {
    const token = localStorage.getItem('authToken');
    if (!token) {
      console.error('No auth token found for Excel export');
      throw new Error('Not authenticated. Please log in again.');
    }
    
    console.log('[Excel Export] Starting export...');
    console.log('[Excel Export] API Base URL:', API_BASE_URL);
    console.log('[Excel Export] Auth token present:', !!token);
    
    // Explicitly include auth header for blob requests (axios interceptor may not work with blobs)
    const response = await api.get('/api/transactions/export/excel', {
      responseType: 'blob',
      headers: {
        'Authorization': `Bearer ${token}`,
      },
    });
    
    console.log('[Excel Export] Response received - status:', response.status);
    console.log('[Excel Export] Response headers:', response.headers);
    console.log('[Excel Export] Response data type:', response.data.type);
    
    // Create download link
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    
    // Extract filename from Content-Disposition header or use default
    const contentDisposition = response.headers['content-disposition'];
    let filename = 'transactions.xlsx';
    if (contentDisposition) {
      const filenameMatch = contentDisposition.match(/filename="?(.+)"?/i);
      if (filenameMatch && filenameMatch[1]) {
        filename = filenameMatch[1];
      }
    }
    
    console.log('[Excel Export] Downloading file:', filename);
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  },

  // Get line items by invoice
  getByInvoice: async (invoiceId: number): Promise<LineItemTransaction[]> => {
    const response = await api.get('/api/transactions', {
      params: { invoiceId: invoiceId.toString() },
    });
    return response.data;
  },

  // Get transaction summary
  getSummary: async () => {
    const response = await api.get('/api/transactions/summary');
    return response.data;
  },
};

// Additional helper for PDF retrieval
// Using stream endpoint instead of presigned URLs to avoid signature issues
export const getPdfPresignedUrl = async (invoiceId: number) => {
  // Get the auth token from localStorage
  const token = localStorage.getItem('authToken');
  
  // Return the stream URL with token as query parameter
  const baseURL = process.env.REACT_APP_API_URL || 
    (process.env.NODE_ENV === 'production' 
      ? '/invoice'
      : 'http://localhost:8081/mcp-invoice');
  
  // Add token as query parameter for iframe authentication
  const url = `${baseURL}/api/admin/invoices/${invoiceId}/pdf/stream?token=${token}`;
  
  return {
    success: true,
    url: url
  };
};

// Helper for S3 PDF streaming (used by Raw JSON Processing)
export const getS3PdfStreamUrl = (bucket: string, key: string) => {
  const token = localStorage.getItem('authToken');
  
  const baseURL = process.env.REACT_APP_API_URL || 
    (process.env.NODE_ENV === 'production' 
      ? '/invoice'
      : 'http://localhost:8081/mcp-invoice');
  
  const url = `${baseURL}/api/s3-pdf/stream?bucket=${encodeURIComponent(bucket)}&key=${encodeURIComponent(key)}&token=${token}`;
  
  return url;
};

// Invoice Editing API - for updating invoice number, service periods, metadata, and categories
export const invoiceEditAPI = {
  // Invoice Number
  updateInvoiceNumber: async (invoiceId: number, invoiceNumber: string) => {
    const response = await api.put(`/api/admin/invoices/${invoiceId}/invoice-number`, { invoiceNumber });
    return response.data;
  },

  // Service Period
  updateServicePeriod: async (invoiceId: number, servicePeriod: { startDate?: string; endDate?: string }) => {
    const response = await api.put(`/api/admin/invoices/${invoiceId}/service-period`, servicePeriod);
    return response.data;
  },

  // Metadata CRUD
  addMetadata: async (invoiceId: number, metadata: { key: string; value: string }) => {
    const response = await api.post(`/api/admin/invoices/${invoiceId}/metadata`, metadata);
    return response.data;
  },

  updateMetadata: async (invoiceId: number, metadataId: number, metadata: { key: string; value: string }) => {
    const response = await api.put(`/api/admin/invoices/${invoiceId}/metadata/${metadataId}`, metadata);
    return response.data;
  },

  deleteMetadata: async (invoiceId: number, metadataId: number) => {
    const response = await api.delete(`/api/admin/invoices/${invoiceId}/metadata/${metadataId}`);
    return response.data;
  },

  // Line Item Categories
  updateLineItemCategory: async (invoiceId: number, lineItemId: number, category: string) => {
    const response = await api.put(`/api/admin/invoices/${invoiceId}/line-items/${lineItemId}/category`, {
      category,
    });
    return response.data;
  },

  deleteLineItemCategory: async (invoiceId: number, lineItemId: number) => {
    const response = await api.delete(`/api/admin/invoices/${invoiceId}/line-items/${lineItemId}/category`);
    return response.data;
  },

  // Line Items
  deleteLineItem: async (invoiceId: number, lineItemId: number) => {
    const response = await api.delete(`/api/admin/invoices/${invoiceId}/line-items/${lineItemId}`);
    return response.data;
  },

  // Delete Invoice
  deleteInvoice: async (invoiceId: number) => {
    const response = await api.delete(`/api/admin/invoices/${invoiceId}`);
    return response.data;
  },

  createLineItem: async (invoiceId: number, lineItem: {
    description: string;
    quantity?: number;
    unitPrice?: number;
    lineTotal: number;
    taxRate?: number;
    taxAmount?: number;
    productCode?: string;
    unitOfMeasure?: string;
  }) => {
    const response = await api.post(`/api/admin/invoices/${invoiceId}/line-items`, lineItem);
    return response.data;
  },

  updateLineItem: async (invoiceId: number, lineItemId: number, lineItem: {
    description?: string;
    quantity?: number;
    unitPrice?: number;
    lineTotal?: number;
    taxRate?: number;
    taxAmount?: number;
    productCode?: string;
    unitOfMeasure?: string;
  }) => {
    const response = await api.put(`/api/admin/invoices/${invoiceId}/line-items/${lineItemId}`, lineItem);
    return response.data;
  },
};

// Invoice Audit Trail API
export const invoiceAuditAPI = {
  // Get MCP tool call history for an invoice
  getInvoiceAuditTrail: async (invoiceId: number): Promise<{
    invoiceId: number;
    sourceFileName: string;
    totalToolCalls: number;
    toolCalls: Array<{
      toolName: string;
      operationType: string;
      success: boolean;
      executionTimeMs: number;
      timestamp: string;
      createdBy: string;
      errorMessage?: string;
      jobId?: number;
      s3Bucket?: string;
      s3ObjectKey?: string;
    }>;
    statistics: {
      successfulCalls: number;
      failedCalls: number;
      averageExecutionTimeMs: number;
    };
  }> => {
    const response = await api.get(`/api/invoices/${invoiceId}/audit-trail`);
    return response.data;
  },
};

// Raw JSON Processing API
export const rawJsonProcessingAPI = {
  // Get all raw JSON processing records
  getAllRecords: async (): Promise<{ records: any[]; count: number }> => {
    const response = await api.get('/api/pdf-raw-json');
    return response.data;
  },

  // Get a specific record by ID
  getRecordById: async (id: number): Promise<any> => {
    const response = await api.get(`/api/pdf-raw-json/${id}`);
    return response.data;
  },

  // Get records by job ID
  getRecordsByJobId: async (jobId: string): Promise<any> => {
    const response = await api.get(`/api/pdf-raw-json/job/${jobId}`);
    return response.data;
  },

  // Correct/update raw JSON
  correctRawJson: async (id: number, correctedJson: string): Promise<any> => {
    const response = await api.put(`/api/pdf-raw-json/${id}/correct`, { correctedJson });
    return response.data;
  },

  // Validate raw JSON against schema
  validateRawJson: async (id: number, correctedJson: string): Promise<any> => {
    const response = await api.post(`/api/pdf-raw-json/${id}/validate`, { jsonData: correctedJson });
    return response.data;
  },
};

export default api;

