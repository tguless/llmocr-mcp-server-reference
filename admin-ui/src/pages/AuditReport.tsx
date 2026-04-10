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
  TableSortLabel,
  CircularProgress,
  Alert,
  Button,
  TextField,
  Stack,
  Card,
  CardContent,
  IconButton,
  Tooltip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import FileDownloadIcon from '@mui/icons-material/FileDownload';
import DownloadIcon from '@mui/icons-material/Download';
import api from '../services/api';
import { getPdfPresignedUrl } from '../services/api';

interface AuditReportRow {
  id?: number;
  invoice_number: string;
  service_period_start_date: string | null;
  service_period_end_date: string | null;
  meter_number: string | null;
  generated_kwh: string | null;
  contracted_rate: string | null;
  ['DG Credit']: string | null;
  ['$ Misc']: string | null;
  ['Invoice $']: number;
}

const AuditReport: React.FC = () => {
  const [rows, setRows] = useState<AuditReportRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);
  const [totalElements, setTotalElements] = useState(0);
  const [searchInvoice, setSearchInvoice] = useState('');
  const [searchMeter, setSearchMeter] = useState('');
  const [filterApplied, setFilterApplied] = useState(false);
  const [pdfUrl, setPdfUrl] = useState<string | undefined>(undefined);
  const [pdfLoading, setPdfLoading] = useState(false);
  const [selectedInvoiceNumber, setSelectedInvoiceNumber] = useState<string>('');

  // Sort state
  type SortField = 'invoice_number' | 'service_period_start_date' | 'service_period_end_date' | 'meter_number' | 'generated_kwh' | 'contracted_rate' | 'DG Credit' | '$ Misc' | 'Invoice $';
  const [sortField, setSortField] = useState<SortField>('invoice_number');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

  useEffect(() => {
    loadAuditReport();
  }, [page, rowsPerPage, filterApplied, sortField, sortDirection]);

  const loadAuditReport = async () => {
    setLoading(true);
    setError(null);
    try {
      const params: any = {
        page,
        pageSize: rowsPerPage,
        sortBy: sortField,
        sortDirection: sortDirection,
      };

      if (searchInvoice.trim()) {
        params.invoiceNumber = searchInvoice.trim();
      }
      if (searchMeter.trim()) {
        params.meterNumber = searchMeter.trim();
      }

      const response = await api.get('/api/admin/audit-report', { params });
      // Map invoice_id to id for consistency
      const mappedRows = response.data.rows.map((row: any) => ({
        ...row,
        id: row.invoice_id,
      }));
      setRows(mappedRows);
      setTotalElements(response.data.total);
    } catch (err: any) {
      console.error('Failed to load audit report:', err);
      setError(err.response?.data?.error || 'Failed to load audit report.');
    } finally {
      setLoading(false);
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
    setFilterApplied(!filterApplied);
  };

  const handleClearFilters = () => {
    setSearchInvoice('');
    setSearchMeter('');
    setPage(0);
    setFilterApplied(!filterApplied);
  };

  const handleRefresh = () => {
    setPage(0);
    loadAuditReport();
  };

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      // Toggle direction if clicking same column
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      // Set new field with ascending direction
      setSortField(field);
      setSortDirection('asc');
    }
    setPage(0); // Reset to first page when sorting changes
  };

  const handleDownloadCSV = () => {
    if (rows.length === 0) {
      setError('No data to download');
      return;
    }

    // Define CSV headers
    const headers = [
      'Invoice #',
      'Service Start',
      'Service End',
      'Meter #',
      'Generated kWh',
      'Contracted Rate',
      'DG Credit',
      '$ Misc',
      'Invoice $',
    ];

    // Convert rows to CSV format
    const csvContent = [
      headers.join(','),
      ...rows.map((row) => {
        const values = [
          `"${row.invoice_number}"`,
          formatDate(row.service_period_start_date),
          formatDate(row.service_period_end_date),
          row.meter_number || '-',
          row.generated_kwh || '-',
          row.contracted_rate || '-',
          row['DG Credit'] || '-',
          row['$ Misc'] || '-',
          row['Invoice $'] || '-',
        ];
        return values.join(',');
      }),
    ].join('\n');

    // Create blob and download
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', `audit-report-${new Date().toISOString().split('T')[0]}.csv`);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const fetchPdfUrl = async (invoiceId: number) => {
    setPdfLoading(true);
    try {
      console.log('Fetching PDF for invoice ID:', invoiceId);
      const response = await getPdfPresignedUrl(invoiceId);
      console.log('PDF Response:', response);
      // Extract URL from response
      const url = response.url;
      if (!url) {
        throw new Error('No URL in response');
      }
      console.log('Setting PDF URL:', url);
      setPdfUrl(url);
    } catch (err: any) {
      console.error('Failed to fetch PDF URL:', err);
      setError(err.response?.data?.error || err.message || 'Failed to generate PDF URL.');
    } finally {
      setPdfLoading(false);
    }
  };

  const handleClosePdfDialog = () => {
    setPdfUrl(undefined);
  };

  const formatDate = (dateString: string | null) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString('en-US');
  };

  const formatNumber = (value: string | number | null) => {
    if (value === null || value === undefined) return '-';
    const num = typeof value === 'string' ? parseFloat(value) : value;
    return isNaN(num) ? '-' : num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1">
          Audit Report
        </Typography>
        <Stack direction="row" spacing={1}>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={handleRefresh}
            disabled={loading}
          >
            Refresh
          </Button>
          <Button
            variant="contained"
            startIcon={<DownloadIcon />}
            onClick={handleDownloadCSV}
            disabled={loading || rows.length === 0}
          >
            Download CSV
          </Button>
        </Stack>
      </Box>

      {/* Search and Filter Card */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" mb={2}>
            Filters
          </Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} mb={2}>
            <TextField
              label="Invoice #"
              placeholder="Search by invoice number..."
              value={searchInvoice}
              onChange={(e) => setSearchInvoice(e.target.value)}
              size="small"
              sx={{ flex: 1 }}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
            />
            <TextField
              label="Meter #"
              placeholder="Search by meter number..."
              value={searchMeter}
              onChange={(e) => setSearchMeter(e.target.value)}
              size="small"
              sx={{ flex: 1 }}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
            />
            <Stack direction="row" spacing={1}>
              <Button
                variant="contained"
                startIcon={<SearchIcon />}
                onClick={handleSearch}
                disabled={loading}
              >
                Search
              </Button>
              <Button
                variant="outlined"
                startIcon={<ClearIcon />}
                onClick={handleClearFilters}
                disabled={loading}
              >
                Clear
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {loading && !rows.length ? (
        <Box display="flex" justifyContent="center" alignItems="center" py={8}>
          <CircularProgress />
        </Box>
      ) : (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'invoice_number'}
                    direction={sortField === 'invoice_number' ? sortDirection : 'asc'}
                    onClick={() => handleSort('invoice_number')}
                  >
                    <strong>Invoice #</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'service_period_start_date'}
                    direction={sortField === 'service_period_start_date' ? sortDirection : 'asc'}
                    onClick={() => handleSort('service_period_start_date')}
                  >
                    <strong>Service Start</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'service_period_end_date'}
                    direction={sortField === 'service_period_end_date' ? sortDirection : 'asc'}
                    onClick={() => handleSort('service_period_end_date')}
                  >
                    <strong>Service End</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell>
                  <TableSortLabel
                    active={sortField === 'meter_number'}
                    direction={sortField === 'meter_number' ? sortDirection : 'asc'}
                    onClick={() => handleSort('meter_number')}
                  >
                    <strong>Meter #</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={sortField === 'generated_kwh'}
                    direction={sortField === 'generated_kwh' ? sortDirection : 'asc'}
                    onClick={() => handleSort('generated_kwh')}
                  >
                    <strong>Generated kWh</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={sortField === 'contracted_rate'}
                    direction={sortField === 'contracted_rate' ? sortDirection : 'asc'}
                    onClick={() => handleSort('contracted_rate')}
                  >
                    <strong>Contracted Rate</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={sortField === 'DG Credit'}
                    direction={sortField === 'DG Credit' ? sortDirection : 'asc'}
                    onClick={() => handleSort('DG Credit')}
                  >
                    <strong>DG Credit</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={sortField === '$ Misc'}
                    direction={sortField === '$ Misc' ? sortDirection : 'asc'}
                    onClick={() => handleSort('$ Misc')}
                  >
                    <strong>$ Misc</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right">
                  <TableSortLabel
                    active={sortField === 'Invoice $'}
                    direction={sortField === 'Invoice $' ? sortDirection : 'asc'}
                    onClick={() => handleSort('Invoice $')}
                  >
                    <strong>Invoice $</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="center"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.length > 0 ? (
                rows.map((row, index) => (
                  <TableRow key={`${row.invoice_number}-${index}`} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={500}>
                        {row.invoice_number}
                      </Typography>
                    </TableCell>
                    <TableCell>{formatDate(row.service_period_start_date)}</TableCell>
                    <TableCell>{formatDate(row.service_period_end_date)}</TableCell>
                    <TableCell>{row.meter_number || '-'}</TableCell>
                    <TableCell align="right">{formatNumber(row.generated_kwh)}</TableCell>
                    <TableCell align="right">{formatNumber(row.contracted_rate)}</TableCell>
                    <TableCell align="right">{formatNumber(row['DG Credit'])}</TableCell>
                    <TableCell align="right">{formatNumber(row['$ Misc'])}</TableCell>
                    <TableCell align="right">
                      <Typography
                        variant="body2"
                        fontWeight={500}
                        sx={{
                          color: row['Invoice $'] < 0 ? '#d32f2f' : '#388e3c',
                        }}
                      >
                        ${formatNumber(row['Invoice $'])}
                      </Typography>
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title="View PDF">
                        <IconButton
                          onClick={() => {
                            console.log('PDF button clicked, row.id:', row.id);
                            if (row.id) {
                              fetchPdfUrl(row.id);
                            } else {
                              console.error('No invoice ID in row:', row);
                              setError('Invoice ID not found. Please try again.');
                            }
                          }}
                          disabled={pdfLoading}
                        >
                          {pdfLoading ? (
                            <CircularProgress size={20} color="inherit" />
                          ) : (
                            <FileDownloadIcon />
                          )}
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={10} align="center" sx={{ py: 4 }}>
                    <Typography variant="body2" color="text.secondary">
                      No audit data found.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
          <TablePagination
            rowsPerPageOptions={[10, 25, 50, 100]}
            component="div"
            count={totalElements}
            rowsPerPage={rowsPerPage}
            page={page}
            onPageChange={handleChangePage}
            onRowsPerPageChange={handleChangeRowsPerPage}
          />
        </TableContainer>
      )}

      {/* PDF Preview Dialog */}
      <Dialog
        open={!!pdfUrl}
        onClose={handleClosePdfDialog}
        maxWidth="lg"
        fullWidth
      >
        <DialogTitle>
          <Stack direction="row" alignItems="center" spacing={1}>
            <FileDownloadIcon />
            <Typography variant="h6">
              Invoice PDF Preview
            </Typography>
          </Stack>
        </DialogTitle>
        <DialogContent dividers>
          {pdfLoading ? (
            <Box display="flex" justifyContent="center" py={4}>
              <CircularProgress />
            </Box>
          ) : (
            <Box sx={{ width: '100%', height: 'calc(100vh - 200px)', overflow: 'hidden' }}>
              <iframe
                src={pdfUrl}
                style={{ width: '100%', height: '100%', border: 'none' }}
              />
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClosePdfDialog} disabled={pdfLoading}>
            Close
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default AuditReport;


