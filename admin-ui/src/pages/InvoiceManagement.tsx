import React, { useState, useEffect } from 'react';
import {
  Box,
  Paper,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Button,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  CircularProgress,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Stack,
  Tooltip,
  Card,
  CardContent,
  Divider,
  Autocomplete,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import VisibilityIcon from '@mui/icons-material/Visibility';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import DescriptionIcon from '@mui/icons-material/Description';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import HistoryIcon from '@mui/icons-material/History';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import api, { invoiceAuditAPI } from '../services/api';
import InvoiceMetadataDisplay from '../components/Invoice/InvoiceMetadataDisplay';
import EditableInvoiceDataDisplay from '../components/Invoice/EditableInvoiceDataDisplay';
import EditableLineItems from '../components/Invoice/EditableLineItems';
import { getPdfPresignedUrl, invoiceEditAPI } from '../services/api';
import { useNavigate } from 'react-router-dom';

interface Invoice {
  id: number;
  invoiceNumber: string;
  vendorName: string;
  invoiceDate: string;
  dueDate?: string;
  totalAmount: number;
  currency: string;
  status: string;
  processingStatus: string;
  createdAt: string;
  updatedAt: string;
}

interface InvoiceDetail extends Invoice {
  tenantId: string;
  vendor: {
    name: string;
    address?: string;
    taxId?: string;
  };
  customer: {
    name?: string;
    address?: string;
  };
  subtotalAmount?: number;
  taxAmount?: number;
  paymentTerms?: string;
  description?: string;
  servicePeriod?: {
    startDate?: string;
    endDate?: string;
  };
  customMetadata?: Array<{
    key: string;
    value: string;
    createdBy?: string;
    updatedAt?: string;
  }>;
  source: {
    fileName?: string;
    fileType?: string;
    filePath?: string;
  };
  confidenceScore?: number;
  validationErrors?: any;
  metadata?: any;
  lineItems: LineItem[];
  lineItemCount: number;
  createdBy?: string;
  updatedBy?: string;
}

interface LineItem {
  id: number;
  lineNumber: number;
  description: string;
  quantity?: number;
  unitPrice?: number;
  lineTotal: number;
  taxRate?: number;
  taxAmount?: number;
  productCode?: string;
  unitOfMeasure?: string;
  category?: string;
  categoryConfidence?: number;
  categorizedBy?: string;
  requiresReview: boolean;
  reviewedBy?: string;
  reviewedAt?: string;
  energy?: {
    unit: string;
    quantity: number;
    rate: number;
  };
  metadata?: any;
}

interface LlmOcrBucket {
  name: string;
  prefix?: string;
  description?: string;
  source: 'local' | 'llmocr';
}

const toInvoiceRef = (filename: string) =>
  (filename || '')
    .toLowerCase()
    .replace(/\.[^.]+$/, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');

const InvoiceManagement: React.FC = () => {
  const navigate = useNavigate();
  const theme = useTheme();
  const isNonMobile = useMediaQuery(theme.breakpoints.up('md')); // md breakpoint = 900px and up
  
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [totalElements, setTotalElements] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedVendor, setSelectedVendor] = useState<string | null>(null);
  const [vendors, setVendors] = useState<string[]>([]);
  const [detailDialogOpen, setDetailDialogOpen] = useState(false);
  const [selectedInvoice, setSelectedInvoice] = useState<InvoiceDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [pdfUrl, setPdfUrl] = useState<string | undefined>(undefined);
  const [pdfLoading, setPdfLoading] = useState(false);
  const [pdfPaneExpanded, setPdfPaneExpanded] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [auditTrail, setAuditTrail] = useState<any>(null);
  const [auditTrailExpanded, setAuditTrailExpanded] = useState(false);
  const [auditTrailLoading, setAuditTrailLoading] = useState(false);
  const [uploadDialogOpen, setUploadDialogOpen] = useState(false);
  const [uploadFiles, setUploadFiles] = useState<File[]>([]);
  const [uploadingInvoices, setUploadingInvoices] = useState(false);
  const [uploadMessage, setUploadMessage] = useState<string | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [preferredBucket, setPreferredBucket] = useState<LlmOcrBucket | null>(null);
  const [loadingPreferredBucket, setLoadingPreferredBucket] = useState(false);

  useEffect(() => {
    loadInvoices();
    loadVendors();
  }, [page, rowsPerPage, searchTerm, selectedVendor]);

  const loadInvoices = async () => {
    setLoading(true);
    setError(null);
    try {
      const params: any = {
        page,
        size: rowsPerPage,
        sortBy: 'createdAt',
        sortDir: 'desc',
      };
      
      if (searchTerm.trim()) {
        params.search = searchTerm.trim();
      }
      
      if (selectedVendor) {
        params.vendor = selectedVendor;
      }

      const response = await api.get('/api/admin/invoices', { params });
      setInvoices(response.data.content);
      setTotalElements(response.data.totalElements);
    } catch (err: any) {
      console.error('Failed to load invoices:', err);
      setError(err.response?.data?.error || 'Failed to load invoices. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const loadVendors = async () => {
    try {
      const response = await api.get('/api/admin/invoices/vendors');
      setVendors(response.data);
    } catch (err: any) {
      console.error('Failed to load vendors:', err);
    }
  };

  const loadInvoiceDetail = async (id: number) => {
    setDetailLoading(true);
    setError(null);
    // Auto-expand PDF viewer on non-mobile devices
    const shouldAutoExpand = isNonMobile;
    setPdfPaneExpanded(shouldAutoExpand);
    
    // If auto-expanding, fetch PDF immediately
    if (shouldAutoExpand) {
      fetchPdfUrl(id);
    } else {
      setPdfUrl(undefined);
    }
    
    try {
      const response = await api.get(`/api/admin/invoices/${id}`);
      setSelectedInvoice(response.data);
      setDetailDialogOpen(true);
      
      // Load audit trail for this invoice
      loadAuditTrail(id);
    } catch (err: any) {
      console.error('Failed to load invoice details:', err);
      setError(err.response?.data?.error || 'Failed to load invoice details.');
    } finally {
      setDetailLoading(false);
    }
  };

  const loadAuditTrail = async (invoiceId: number) => {
    setAuditTrailLoading(true);
    try {
      const trail = await invoiceAuditAPI.getInvoiceAuditTrail(invoiceId);
      setAuditTrail(trail);
      console.log('Loaded audit trail for invoice:', invoiceId, trail);
    } catch (err: any) {
      console.error('Failed to load audit trail:', err);
      setAuditTrail(null);
    } finally {
      setAuditTrailLoading(false);
    }
  };

  const handleChangePage = (event: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const handleSearch = () => {
    setPage(0);
    loadInvoices();
  };

  const handleClearSearch = () => {
    setSearchTerm('');
    setSelectedVendor(null);
    setPage(0);
  };

  const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
    switch (status) {
      case 'APPROVED':
      case 'PAID':
        return 'success';
      case 'PENDING':
        return 'warning';
      case 'REJECTED':
      case 'CANCELLED':
        return 'error';
      default:
        return 'default';
    }
  };

  const getProcessingStatusColor = (status: string): 'success' | 'warning' | 'error' | 'info' | 'default' => {
    switch (status) {
      case 'COMPLETED':
      case 'VALIDATED':
        return 'success';
      case 'PROCESSING':
      case 'NEW':
        return 'info';
      case 'FAILED':
        return 'error';
      case 'DUPLICATE_DETECTED':
        return 'warning';
      default:
        return 'default';
    }
  };

  const getProcessingStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED':
      case 'VALIDATED':
        return <CheckCircleIcon />;
      case 'FAILED':
        return <ErrorIcon />;
      case 'PROCESSING':
      case 'NEW':
        return <HourglassEmptyIcon />;
      default:
        return null;
    }
  };

  const getCategoryColor = (category?: string): 'primary' | 'secondary' | 'error' | 'warning' | 'success' | 'default' => {
    if (!category) return 'default';
    if (category.startsWith('PASS_THROUGH')) return 'primary';
    if (category.startsWith('CREDIT')) return 'success';
    if (category.startsWith('INTERNAL')) return 'secondary';
    if (category === 'REVIEW_REQUIRED') return 'warning';
    return 'default';
  };

  const formatCurrency = (amount: number, currency: string = 'USD') => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency,
    }).format(amount);
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString();
  };

  const fetchPdfUrl = async (invoiceId: number) => {
    setPdfLoading(true);
    try {
      const response = await getPdfPresignedUrl(invoiceId);
      setPdfUrl(response.url);
    } catch (err: any) {
      console.error('Failed to fetch PDF URL:', err);
      setError(err.response?.data?.error || 'Failed to fetch PDF URL.');
    } finally {
      setPdfLoading(false);
    }
  };

  const handleClosePdfDialog = () => {
    setPdfUrl(undefined);
  };

  const handleDeleteInvoice = async () => {
    if (!selectedInvoice) return;
    
    setDeleteLoading(true);
    try {
      await invoiceEditAPI.deleteInvoice(selectedInvoice.id);
      setDeleteConfirmOpen(false);
      setDetailDialogOpen(false);
      setSelectedInvoice(null);
      // Refresh the invoice list
      await loadInvoices();
    } catch (err: any) {
      console.error('Failed to delete invoice:', err);
      setError(err.response?.data?.error || 'Failed to delete invoice');
    } finally {
      setDeleteLoading(false);
    }
  };

  const resolvePreferredInvoiceBucket = (allBuckets: LlmOcrBucket[]): LlmOcrBucket | null => {
    const llmOcrBuckets = allBuckets.filter((bucket) => bucket.source === 'llmocr');
    const byPrefix = llmOcrBuckets.find((bucket) =>
      (bucket.prefix || '').toLowerCase().includes('/invoices/')
    );
    if (byPrefix) return byPrefix;

    const byDescription = llmOcrBuckets.find((bucket) =>
      (bucket.description || '').toLowerCase().includes('invoice')
    );
    if (byDescription) return byDescription;

    return llmOcrBuckets[0] || null;
  };

  const loadPreferredBucket = async () => {
    setLoadingPreferredBucket(true);
    setUploadError(null);
    try {
      const response = await api.get('/api/s3-upload/buckets');
      const allBuckets = (response?.data?.data?.all || []) as LlmOcrBucket[];
      const resolved = resolvePreferredInvoiceBucket(allBuckets);
      setPreferredBucket(resolved);
      if (!resolved) {
        setUploadError('No PaperIQ invoice bucket mapping is available for this tenant.');
      }
    } catch (err: any) {
      setUploadError(err.response?.data?.message || 'Failed to resolve invoice upload bucket.');
      setPreferredBucket(null);
    } finally {
      setLoadingPreferredBucket(false);
    }
  };

  const openUploadDialog = async () => {
    setUploadDialogOpen(true);
    setUploadFiles([]);
    setUploadMessage(null);
    setUploadError(null);
    await loadPreferredBucket();
  };

  const handleInvoiceFileSelection = (event: React.ChangeEvent<HTMLInputElement>) => {
    const selected = Array.from(event.target.files || []);
    setUploadFiles(selected);
    setUploadMessage(null);
    setUploadError(null);
  };

  const uploadInvoices = async () => {
    if (!preferredBucket) {
      setUploadError('Invoice upload bucket is not configured.');
      return;
    }
    if (uploadFiles.length === 0) {
      setUploadError('Select one or more invoice files first.');
      return;
    }

    setUploadingInvoices(true);
    setUploadError(null);
    setUploadMessage(null);

    try {
      const formData = new FormData();
      formData.append('bucketName', preferredBucket.name);
      if (preferredBucket.prefix) {
        formData.append('prefix', preferredBucket.prefix);
      }

      uploadFiles.forEach((file) => {
        formData.append('files', file);
      });

      // For multi-file uploads, keep metadata aligned per filename on backend defaults.
      // We provide predictable invoice-oriented defaults for processing callbacks.
      const firstRef = toInvoiceRef(uploadFiles[0]?.name || '');
      if (firstRef) {
        formData.append('sourceExternalId', firstRef);
      }
      formData.append('sourceHeadersJson', JSON.stringify({
        invoiceRef: firstRef || uploadFiles[0]?.name || 'invoice-upload',
        documentType: 'invoice',
      }));

      const response = await api.post('/api/s3-upload/upload-to-llmocr-bucket', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });

      const totalFiles = response?.data?.data?.totalFiles ?? uploadFiles.length;
      const successful = response?.data?.data?.successfulUploads ?? 0;
      const failed = response?.data?.data?.failedUploads ?? 0;

      setUploadMessage(`Uploaded ${successful}/${totalFiles} invoice files${failed > 0 ? ` (${failed} failed)` : ''}.`);
      setUploadFiles([]);
      await loadInvoices();
    } catch (err: any) {
      setUploadError(err.response?.data?.message || err.response?.data?.error || 'Invoice upload failed.');
    } finally {
      setUploadingInvoices(false);
    }
  };

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1">
          Invoice Management
        </Typography>
        <Stack direction="row" spacing={1}>
          <Button
            variant="contained"
            startIcon={<UploadFileIcon />}
            onClick={openUploadDialog}
          >
            Upload Invoices
          </Button>
          <Button
            variant="text"
            onClick={() => navigate('/s3-upload')}
          >
            Advanced S3 Upload
          </Button>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={loadInvoices}
            disabled={loading}
          >
            Refresh
          </Button>
        </Stack>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Search and Filter */}
      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack spacing={2}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems="stretch">
            <Box sx={{ flex: { md: 5 } }}>
              <TextField
                fullWidth
                size="small"
                placeholder="Search by invoice number, vendor, or customer..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                onKeyPress={(e) => {
                  if (e.key === 'Enter') {
                    handleSearch();
                  }
                }}
                InputProps={{
                  startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} />,
                }}
              />
            </Box>
            <Box sx={{ flex: { md: 4 } }}>
              <Autocomplete
                size="small"
                options={vendors}
                value={selectedVendor}
                onChange={(event, newValue) => {
                  setSelectedVendor(newValue);
                }}
                renderInput={(params) => (
                  <TextField {...params} label="Filter by Vendor" placeholder="Select vendor" />
                )}
              />
            </Box>
            <Box sx={{ flex: { md: 3 } }}>
              <Stack direction="row" spacing={1}>
                <Button
                  variant="contained"
                  startIcon={<SearchIcon />}
                  onClick={handleSearch}
                  disabled={loading}
                  fullWidth
                >
                  Search
                </Button>
                <Button
                  variant="outlined"
                  startIcon={<ClearIcon />}
                  onClick={handleClearSearch}
                  disabled={loading || (!searchTerm && !selectedVendor)}
                >
                  Clear
                </Button>
              </Stack>
            </Box>
          </Stack>
        </Stack>
      </Paper>

      {/* Invoices Table */}
      {loading ? (
        <Box display="flex" justifyContent="center" alignItems="center" py={8}>
          <CircularProgress />
        </Box>
      ) : (
        <>
          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Invoice #</TableCell>
                  <TableCell>Vendor</TableCell>
                  <TableCell>Invoice Date</TableCell>
                  <TableCell>Due Date</TableCell>
                  <TableCell align="right">Total Amount</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Processing</TableCell>
                  <TableCell align="center">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {invoices.map((invoice) => (
                  <TableRow key={invoice.id} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={500}>
                        {invoice.invoiceNumber}
                      </Typography>
                    </TableCell>
                    <TableCell>{invoice.vendorName}</TableCell>
                    <TableCell>{formatDate(invoice.invoiceDate)}</TableCell>
                    <TableCell>
                      {invoice.dueDate ? formatDate(invoice.dueDate) : '-'}
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2" fontWeight={500}>
                        {formatCurrency(invoice.totalAmount, invoice.currency)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={invoice.status}
                        color={getStatusColor(invoice.status)}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={1} alignItems="center">
                        {getProcessingStatusIcon(invoice.processingStatus)}
                        <Chip
                          label={invoice.processingStatus}
                          color={getProcessingStatusColor(invoice.processingStatus)}
                          size="small"
                        />
                      </Stack>
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title="View Details">
                        <IconButton
                          size="small"
                          color="primary"
                          onClick={(e) => {
                            e.stopPropagation();
                            loadInvoiceDetail(invoice.id);
                          }}
                          disabled={detailLoading}
                        >
                          <VisibilityIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <TablePagination
              rowsPerPageOptions={[10, 20, 50, 100]}
              component="div"
              count={totalElements}
              rowsPerPage={rowsPerPage}
              page={page}
              onPageChange={handleChangePage}
              onRowsPerPageChange={handleChangeRowsPerPage}
            />
          </TableContainer>

          {invoices.length === 0 && !loading && (
            <Box textAlign="center" py={4}>
              <DescriptionIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
              <Typography variant="body1" color="text.secondary">
                No invoices found.
              </Typography>
              {(searchTerm || selectedVendor) && (
                <Typography variant="body2" color="text.secondary" mt={1}>
                  Try adjusting your search criteria.
                </Typography>
              )}
            </Box>
          )}
        </>
      )}

      {/* Invoice Detail Dialog */}
      <Dialog
        open={detailDialogOpen}
        onClose={() => {
          setDetailDialogOpen(false);
          setPdfUrl(undefined);
          setPdfPaneExpanded(false);
        }}
        maxWidth={false}
        fullWidth
        sx={{
          '& .MuiDialog-paper': {
            width: '95vw',
            maxHeight: '95vh',
          },
        }}
      >
        <DialogTitle>
          <Stack direction="row" alignItems="center" justifyContent="space-between">
            <Stack direction="row" alignItems="center" spacing={1}>
              <DescriptionIcon />
              <Typography variant="h6">
                Invoice Details - {selectedInvoice?.invoiceNumber}
              </Typography>
            </Stack>
            <Tooltip title={pdfPaneExpanded ? "Collapse PDF View" : "Expand PDF View"}>
              <Button
                onClick={() => {
                  const isExpanding = !pdfPaneExpanded;
                  setPdfPaneExpanded(isExpanding);
                  // If collapsing, clear the PDF URL
                  if (!isExpanding) {
                    setPdfUrl(undefined);
                  } else {
                    // If expanding, always fetch fresh PDF
                    if (selectedInvoice?.id) {
                      fetchPdfUrl(selectedInvoice.id);
                    }
                  }
                }}
                size="small"
                variant="outlined"
                endIcon={pdfPaneExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
              >
                {pdfPaneExpanded ? "Collapse" : "Expand"} PDF
              </Button>
            </Tooltip>
          </Stack>
        </DialogTitle>
        <DialogContent dividers sx={{ display: 'flex', gap: 2, p: 2, height: 'calc(100vh - 180px)' }}>
          <Box display="flex" gap={2} width="100%" height="100%">
            {/* Left side: Invoice Details */}
            <Box sx={{ flex: pdfPaneExpanded ? '0 0 50%' : 1, overflowY: 'auto', pr: 1 }}>
              {detailLoading ? (
                <Box display="flex" justifyContent="center" py={4}>
                  <CircularProgress />
                </Box>
              ) : selectedInvoice ? (
                <Box>
                  {/* Header Information */}
                  <Stack direction={{ xs: 'column', md: 'row' }} spacing={3} mb={3}>
                    <Box sx={{ flex: 1 }}>
                      <Card variant="outlined">
                        <CardContent>
                          <Typography variant="subtitle2" color="primary" gutterBottom>
                            Vendor Information
                          </Typography>
                          <Typography variant="body1" fontWeight={600}>
                            {selectedInvoice.vendor.name}
                          </Typography>
                          {selectedInvoice.vendor.address && (
                            <Typography variant="body2" color="text.secondary">
                              {selectedInvoice.vendor.address}
                            </Typography>
                          )}
                          {selectedInvoice.vendor.taxId && (
                            <Typography variant="body2" color="text.secondary">
                              Tax ID: {selectedInvoice.vendor.taxId}
                            </Typography>
                          )}
                        </CardContent>
                      </Card>
                    </Box>
                    <Box sx={{ flex: 1 }}>
                      <Card variant="outlined">
                        <CardContent>
                          <Typography variant="subtitle2" color="primary" gutterBottom>
                            Customer Information
                          </Typography>
                          <Typography variant="body1" fontWeight={600}>
                            {selectedInvoice.customer.name || 'N/A'}
                          </Typography>
                          {selectedInvoice.customer.address && (
                            <Typography variant="body2" color="text.secondary">
                              {selectedInvoice.customer.address}
                            </Typography>
                          )}
                        </CardContent>
                      </Card>
                    </Box>
                  </Stack>

                  {/* Invoice Summary */}
                  <Card variant="outlined" sx={{ mb: 3 }}>
                    <CardContent>
                      <Typography variant="subtitle2" color="primary" gutterBottom>
                        Invoice Summary
                      </Typography>
                      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} flexWrap="wrap">
                        <Box sx={{ flex: { xs: '1 0 45%', sm: '1 0 20%' } }}>
                          <Typography variant="caption" color="text.secondary">
                            Invoice Date
                          </Typography>
                          <Typography variant="body2">
                            {formatDate(selectedInvoice.invoiceDate)}
                          </Typography>
                        </Box>
                        <Box sx={{ flex: { xs: '1 0 45%', sm: '1 0 20%' } }}>
                          <Typography variant="caption" color="text.secondary">
                            Due Date
                          </Typography>
                          <Typography variant="body2">
                            {selectedInvoice.dueDate ? formatDate(selectedInvoice.dueDate) : 'N/A'}
                          </Typography>
                        </Box>
                        <Box sx={{ flex: { xs: '1 0 45%', sm: '1 0 20%' } }}>
                          <Typography variant="caption" color="text.secondary">
                            Payment Terms
                          </Typography>
                          <Typography variant="body2">
                            {selectedInvoice.paymentTerms || 'N/A'}
                          </Typography>
                        </Box>
                        <Box sx={{ flex: { xs: '1 0 45%', sm: '1 0 20%' } }}>
                          <Typography variant="caption" color="text.secondary">
                            Currency
                          </Typography>
                          <Typography variant="body2">{selectedInvoice.currency}</Typography>
                        </Box>
                      </Stack>
                      
                      <Divider sx={{ my: 2 }} />
                      
                      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} flexWrap="wrap">
                        <Box sx={{ flex: { xs: '1 0 45%', sm: '1 0 30%' } }}>
                          <Typography variant="caption" color="text.secondary">
                            Subtotal
                          </Typography>
                          <Typography variant="body2">
                            {selectedInvoice.subtotalAmount
                              ? formatCurrency(selectedInvoice.subtotalAmount, selectedInvoice.currency)
                              : 'N/A'}
                          </Typography>
                        </Box>
                        <Box sx={{ flex: { xs: '1 0 45%', sm: '1 0 30%' } }}>
                          <Typography variant="caption" color="text.secondary">
                            Tax
                          </Typography>
                          <Typography variant="body2">
                            {selectedInvoice.taxAmount
                              ? formatCurrency(selectedInvoice.taxAmount, selectedInvoice.currency)
                              : 'N/A'}
                          </Typography>
                        </Box>
                        <Box sx={{ flex: { xs: '1 0 100%', sm: '1 0 30%' } }}>
                          <Typography variant="caption" color="text.secondary">
                            Total Amount
                          </Typography>
                          <Typography variant="h6" color="primary">
                            {formatCurrency(selectedInvoice.totalAmount, selectedInvoice.currency)}
                          </Typography>
                        </Box>
                      </Stack>

                      {selectedInvoice.description && (
                        <>
                          <Divider sx={{ my: 2 }} />
                          <Typography variant="caption" color="text.secondary">
                            Description
                          </Typography>
                          <Typography variant="body2">{selectedInvoice.description}</Typography>
                        </>
                      )}
                    </CardContent>
                  </Card>

                  {/* Service Period & Custom Metadata Display */}
                  <EditableInvoiceDataDisplay
                    invoiceId={selectedInvoice.id}
                    invoiceNumber={selectedInvoice.invoiceNumber}
                    servicePeriod={selectedInvoice.servicePeriod}
                    customMetadata={selectedInvoice.customMetadata}
                    onInvoiceNumberUpdate={async (invoiceNumber) => {
                      await invoiceEditAPI.updateInvoiceNumber(selectedInvoice.id, invoiceNumber);
                      // Refresh invoice details
                      await loadInvoiceDetail(selectedInvoice.id);
                    }}
                    onServicePeriodUpdate={async (servicePeriod) => {
                      await invoiceEditAPI.updateServicePeriod(selectedInvoice.id, servicePeriod);
                      // Refresh invoice details
                      await loadInvoiceDetail(selectedInvoice.id);
                    }}
                    onMetadataAdd={async (metadata) => {
                      await invoiceEditAPI.addMetadata(selectedInvoice.id, metadata);
                      // Refresh invoice details
                      await loadInvoiceDetail(selectedInvoice.id);
                    }}
                    onMetadataUpdate={async (id, metadata) => {
                      await invoiceEditAPI.updateMetadata(selectedInvoice.id, id, metadata);
                      // Refresh invoice details
                      await loadInvoiceDetail(selectedInvoice.id);
                    }}
                    onMetadataDelete={async (id) => {
                      await invoiceEditAPI.deleteMetadata(selectedInvoice.id, id);
                      // Refresh invoice details
                      await loadInvoiceDetail(selectedInvoice.id);
                    }}
                  />

                  {/* Line Items */}
                  <Typography variant="subtitle1" gutterBottom fontWeight={600}>
                    Line Items ({selectedInvoice.lineItemCount})
                  </Typography>
                  <EditableLineItems
                    lineItems={selectedInvoice.lineItems}
                    currency={selectedInvoice.currency}
                    onCategoryUpdate={async (lineItemId, category) => {
                      await invoiceEditAPI.updateLineItemCategory(selectedInvoice.id, lineItemId, category);
                      // Refresh invoice details
                      await loadInvoiceDetail(selectedInvoice.id);
                    }}
                    onCategoryDelete={async (lineItemId) => {
                      await invoiceEditAPI.deleteLineItemCategory(selectedInvoice.id, lineItemId);
                      // Refresh invoice details
                      await loadInvoiceDetail(selectedInvoice.id);
                    }}
                    onLineItemAdd={async (lineItem) => {
                      await invoiceEditAPI.createLineItem(selectedInvoice.id, lineItem);
                      // Refresh invoice details
                      await loadInvoiceDetail(selectedInvoice.id);
                    }}
                    onLineItemEdit={async (lineItemId, lineItem) => {
                      await invoiceEditAPI.updateLineItem(selectedInvoice.id, lineItemId, lineItem);
                      // Refresh invoice details
                      await loadInvoiceDetail(selectedInvoice.id);
                    }}
                    onLineItemDelete={async (lineItemId) => {
                      await invoiceEditAPI.deleteLineItem(selectedInvoice.id, lineItemId);
                      // Refresh invoice details
                      await loadInvoiceDetail(selectedInvoice.id);
                    }}
                  />

                  {/* Metadata */}
                  {(selectedInvoice.source.fileName || selectedInvoice.confidenceScore) && (
                    <Card variant="outlined" sx={{ mt: 3 }}>
                      <CardContent>
                        <Typography variant="subtitle2" color="primary" gutterBottom>
                          Processing Information
                        </Typography>
                        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3}>
                          {selectedInvoice.source.fileName && (
                            <Box flex={1}>
                              <Typography variant="caption" color="text.secondary">
                                Source File
                              </Typography>
                              <Typography variant="body2">
                                {selectedInvoice.source.fileName}
                              </Typography>
                            </Box>
                          )}
                          {selectedInvoice.confidenceScore && (
                            <Box flex={1}>
                              <Typography variant="caption" color="text.secondary">
                                Confidence Score
                              </Typography>
                              <Typography variant="body2">
                                {(selectedInvoice.confidenceScore * 100).toFixed(2)}%
                              </Typography>
                            </Box>
                          )}
                        </Stack>
                      </CardContent>
                    </Card>
                  )}

                  {/* MCP Tool Call History */}
                  <Card variant="outlined" sx={{ mt: 3 }}>
                    <CardContent>
                      <Box display="flex" justifyContent="space-between" alignItems="center">
                        <Box display="flex" alignItems="center" gap={1}>
                          <HistoryIcon color="primary" />
                          <Typography variant="subtitle2" color="primary">
                            AI Processing History
                          </Typography>
                          {auditTrail && (
                            <Chip 
                              label={`${auditTrail.totalToolCalls} tool calls`} 
                              size="small" 
                              color="default"
                            />
                          )}
                        </Box>
                        <IconButton onClick={() => setAuditTrailExpanded(!auditTrailExpanded)} size="small">
                          {auditTrailExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                        </IconButton>
                      </Box>
                      
                      {auditTrailExpanded && (
                        <Box mt={2}>
                          {auditTrailLoading ? (
                            <Box display="flex" justifyContent="center" py={2}>
                              <CircularProgress size={24} />
                            </Box>
                          ) : auditTrail && auditTrail.toolCalls.length > 0 ? (
                            <>
                              {/* Statistics Summary */}
                              <Stack direction="row" spacing={2} mb={2}>
                                <Chip 
                                  icon={<CheckCircleIcon />}
                                  label={`${auditTrail.statistics.successfulCalls} successful`} 
                                  color="success" 
                                  size="small" 
                                />
                                {auditTrail.statistics.failedCalls > 0 && (
                                  <Chip 
                                    icon={<ErrorIcon />}
                                    label={`${auditTrail.statistics.failedCalls} failed`} 
                                    color="error" 
                                    size="small" 
                                  />
                                )}
                                <Chip 
                                  label={`Avg: ${auditTrail.statistics.averageExecutionTimeMs}ms`} 
                                  size="small" 
                                  variant="outlined"
                                />
                              </Stack>
                              
                              {/* Tool Call Timeline */}
                              <TableContainer>
                                <Table size="small">
                                  <TableHead>
                                    <TableRow>
                                      <TableCell>Tool Name</TableCell>
                                      <TableCell>Status</TableCell>
                                      <TableCell>Time (ms)</TableCell>
                                      <TableCell>Timestamp</TableCell>
                                      <TableCell>Job ID</TableCell>
                                    </TableRow>
                                  </TableHead>
                                  <TableBody>
                                    {auditTrail.toolCalls.map((call: any, index: number) => (
                                      <TableRow key={index}>
                                        <TableCell>
                                          <Typography variant="body2" fontFamily="monospace">
                                            {call.toolName}
                                          </Typography>
                                          {call.errorMessage && (
                                            <Typography variant="caption" color="error">
                                              {call.errorMessage}
                                            </Typography>
                                          )}
                                        </TableCell>
                                        <TableCell>
                                          {call.success ? (
                                            <Chip label="Success" color="success" size="small" />
                                          ) : (
                                            <Chip label="Failed" color="error" size="small" />
                                          )}
                                        </TableCell>
                                        <TableCell>{call.executionTimeMs || '-'}</TableCell>
                                        <TableCell>
                                          <Typography variant="caption">
                                            {new Date(call.timestamp).toLocaleString()}
                                          </Typography>
                                        </TableCell>
                                        <TableCell>
                                          <Typography variant="caption" fontFamily="monospace">
                                            {call.jobId || '-'}
                                          </Typography>
                                        </TableCell>
                                      </TableRow>
                                    ))}
                                  </TableBody>
                                </Table>
                              </TableContainer>
                            </>
                          ) : (
                            <Typography variant="body2" color="text.secondary">
                              No AI processing history available for this invoice.
                            </Typography>
                          )}
                        </Box>
                      )}
                    </CardContent>
                  </Card>
                </Box>
              ) : (
                <Typography>No invoice details available.</Typography>
              )}
            </Box>

            {/* Right side: PDF Preview */}
            {pdfPaneExpanded && (
              <Box sx={{ flex: '0 0 50%', display: 'flex', flexDirection: 'column', height: '100%' }}>
                <Typography variant="subtitle2" color="primary" gutterBottom sx={{ mb: 1 }}>
                  Invoice PDF Preview
                </Typography>
                {pdfLoading ? (
                  <Box display="flex" justifyContent="center" alignItems="center" flex={1}>
                    <CircularProgress />
                  </Box>
                ) : (
                  <Box sx={{ flex: 1, overflow: 'hidden', border: '1px solid #e0e0e0', borderRadius: '4px' }}>
                    <iframe
                      src={`${pdfUrl}#toolbar=1&view=FitH&page=1&zoom=auto&navpanes=0`}
                      style={{ width: '100%', height: '100%', border: 'none' }}
                    />
                  </Box>
                )}
              </Box>
            )}
          </Box>
        </DialogContent>
        <DialogActions sx={{ justifyContent: 'space-between', px: 3, py: 2 }}>
          <Button 
            variant="outlined" 
            color="error"
            onClick={() => setDeleteConfirmOpen(true)}
            disabled={deleteLoading}
          >
            Delete Invoice
          </Button>
          <Button onClick={() => setDetailDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteConfirmOpen} onClose={() => setDeleteConfirmOpen(false)}>
        <DialogTitle>Confirm Delete</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete invoice <strong>{selectedInvoice?.invoiceNumber}</strong>?
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            This will permanently delete the invoice and all {selectedInvoice?.lineItemCount || 0} line items.
            This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirmOpen(false)} disabled={deleteLoading}>
            Cancel
          </Button>
          <Button 
            onClick={handleDeleteInvoice} 
            variant="contained" 
            color="error" 
            disabled={deleteLoading}
          >
            {deleteLoading ? 'Deleting...' : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={uploadDialogOpen}
        onClose={() => !uploadingInvoices && setUploadDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Upload Invoices</DialogTitle>
        <DialogContent>
          {loadingPreferredBucket ? (
            <Box display="flex" justifyContent="center" py={2}>
              <CircularProgress size={24} />
            </Box>
          ) : (
            <>
              {preferredBucket && (
                <Alert severity="info" sx={{ mb: 2 }}>
                  Upload destination: <strong>{preferredBucket.name}</strong>
                  {preferredBucket.prefix ? ` / ${preferredBucket.prefix}` : ''}
                </Alert>
              )}
              {uploadError && (
                <Alert severity="error" sx={{ mb: 2 }}>
                  {uploadError}
                </Alert>
              )}
              {uploadMessage && (
                <Alert severity="success" sx={{ mb: 2 }}>
                  {uploadMessage}
                </Alert>
              )}
              <Button
                variant="outlined"
                component="label"
                startIcon={<UploadFileIcon />}
                disabled={uploadingInvoices || !preferredBucket}
                sx={{ mb: 2 }}
                fullWidth
              >
                Select Invoice Files
                <input
                  type="file"
                  hidden
                  multiple
                  accept=".pdf,.png,.jpg,.jpeg,.tif,.tiff,.txt,.wav"
                  onChange={handleInvoiceFileSelection}
                />
              </Button>
              {uploadFiles.length > 0 && (
                <Typography variant="body2" color="text.secondary">
                  {uploadFiles.length} file(s) selected
                </Typography>
              )}
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => setUploadDialogOpen(false)}
            disabled={uploadingInvoices}
          >
            Close
          </Button>
          <Button
            variant="contained"
            onClick={uploadInvoices}
            disabled={uploadingInvoices || loadingPreferredBucket || !preferredBucket || uploadFiles.length === 0}
          >
            {uploadingInvoices ? 'Uploading...' : 'Upload'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default InvoiceManagement;

