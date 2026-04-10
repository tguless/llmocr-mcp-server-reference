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
  TextField,
  Button,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  CircularProgress,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Stack,
  Tooltip,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import EditIcon from '@mui/icons-material/Edit';
import WarningIcon from '@mui/icons-material/Warning';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import SearchIcon from '@mui/icons-material/Search';
import DownloadIcon from '@mui/icons-material/Download';
import { transactionAPI, LineItemTransaction, categoryAPI } from '../services/api';
import { useNavigate } from 'react-router-dom';

const TransactionViewer: React.FC = () => {
  const navigate = useNavigate();
  const [transactions, setTransactions] = useState<LineItemTransaction[]>([]);
  const [filteredTransactions, setFilteredTransactions] = useState<LineItemTransaction[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [searchTerm, setSearchTerm] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [reviewFilter, setReviewFilter] = useState<string>('ALL');
  const [selectedTransaction, setSelectedTransaction] = useState<LineItemTransaction | null>(null);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [exporting, setExporting] = useState(false);
  
  // Fetch tenant-specific categories from API
  const [availableCategories, setAvailableCategories] = useState<string[]>([]);

  // Load transactions and categories from backend API
  useEffect(() => {
    loadTransactions();
    loadCategories();
  }, []);
  
  const loadCategories = async () => {
    try {
      const categories = await categoryAPI.getCategories();
      // Extract category codes for the filter dropdown
      const categoryCodes = categories.map(cat => cat.categoryCode);
      setAvailableCategories(categoryCodes);
    } catch (err) {
      console.error('Failed to fetch categories:', err);
      setAvailableCategories([]);
    }
  };

  const loadTransactions = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await transactionAPI.getAllTransactions();
      setTransactions(data);
      setFilteredTransactions(data);
    } catch (err: any) {
      console.error('Failed to load transactions:', err);
      setError(err.response?.data?.message || 'Failed to load transactions. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    filterTransactions();
  }, [searchTerm, categoryFilter, reviewFilter, transactions]);

  const filterTransactions = () => {
    let filtered = [...transactions];

    // Search filter
    if (searchTerm) {
      filtered = filtered.filter(
        (t) =>
          t.invoiceNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
          t.vendorName.toLowerCase().includes(searchTerm.toLowerCase()) ||
          t.description.toLowerCase().includes(searchTerm.toLowerCase()) ||
          t.lineItemId.toString().includes(searchTerm)
      );
    }

    // Category filter
    if (categoryFilter !== 'ALL') {
      filtered = filtered.filter((t) => t.category === categoryFilter);
    }

    // Review filter
    if (reviewFilter === 'NEEDS_REVIEW') {
      filtered = filtered.filter((t) => t.requiresReview);
    } else if (reviewFilter === 'REVIEWED') {
      filtered = filtered.filter((t) => !t.requiresReview);
    }

    setFilteredTransactions(filtered);
    setPage(0);
  };

  const handleChangePage = (event: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const handleEditClick = (transaction: LineItemTransaction) => {
    setSelectedTransaction(transaction);
    setEditDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setEditDialogOpen(false);
    setSelectedTransaction(null);
  };

  const handleExportToExcel = async () => {
    setExporting(true);
    setError(null);
    try {
      await transactionAPI.exportToExcel();
    } catch (err: any) {
      console.error('Failed to export transactions:', err);
      setError(err.response?.data?.message || 'Failed to export transactions. Please try again.');
    } finally {
      setExporting(false);
    }
  };

  const getCategoryColor = (category?: string): 'primary' | 'success' | 'default' | 'warning' => {
    if (!category) return 'default';
    if (category.startsWith('PASS_THROUGH_')) return 'primary';
    if (category.startsWith('CREDIT_')) return 'success';
    if (category.startsWith('INTERNAL_')) return 'default';
    if (category === 'REVIEW_REQUIRED') return 'warning';
    return 'default';
  };

  const getConfidenceColor = (confidence?: number): string => {
    if (!confidence) return 'gray';
    if (confidence >= 0.95) return 'green';
    if (confidence >= 0.85) return 'blue';
    if (confidence >= 0.70) return 'orange';
    return 'red';
  };

  // Use fetched categories instead of deriving from transactions
  // This ensures all tenant categories are available in the filter, not just those currently in use

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1">
          Transaction Viewer
        </Typography>
        <Stack direction="row" spacing={2}>
          <Button
            variant="contained"
            color="primary"
            onClick={() => navigate('/s3-upload')}
          >
            Upload Invoices
          </Button>
          <Button
            variant="contained"
            startIcon={<DownloadIcon />}
            onClick={handleExportToExcel}
            disabled={loading || exporting || transactions.length === 0}
          >
            {exporting ? 'Exporting...' : 'Download as Excel'}
          </Button>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={loadTransactions}
            disabled={loading}
          >
            Refresh
          </Button>
        </Stack>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {loading ? (
        <Box display="flex" justifyContent="center" alignItems="center" py={8}>
          <CircularProgress />
        </Box>
      ) : (
        <>
          {/* Filters */}
          <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={2} alignItems="center">
          <TextField
            placeholder="Search by ID, Invoice #, Vendor, or Description..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            sx={{ flexGrow: 1 }}
            size="small"
            InputProps={{
              startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} />,
            }}
          />
          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel>Category</InputLabel>
            <Select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              label="Category"
            >
              <MenuItem value="ALL">All Categories</MenuItem>
              {availableCategories.map((cat) => (
                <MenuItem key={cat} value={cat}>
                  {cat.replace(/_/g, ' ')}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 150 }}>
            <InputLabel>Review Status</InputLabel>
            <Select
              value={reviewFilter}
              onChange={(e) => setReviewFilter(e.target.value)}
              label="Review Status"
            >
              <MenuItem value="ALL">All</MenuItem>
              <MenuItem value="NEEDS_REVIEW">Needs Review</MenuItem>
              <MenuItem value="REVIEWED">Reviewed</MenuItem>
            </Select>
          </FormControl>
        </Stack>
      </Paper>

      {/* Table */}
      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Line Item ID</TableCell>
              <TableCell>Invoice ID</TableCell>
              <TableCell>Invoice #</TableCell>
              <TableCell>Vendor</TableCell>
              <TableCell>Line #</TableCell>
              <TableCell>Description</TableCell>
              <TableCell align="right">Amount</TableCell>
              <TableCell>Category</TableCell>
              <TableCell>Confidence</TableCell>
              <TableCell align="center">Status</TableCell>
              <TableCell align="center">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {filteredTransactions
              .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
              .map((transaction) => (
                <TableRow key={transaction.lineItemId} hover>
                  <TableCell>{transaction.lineItemId}</TableCell>
                  <TableCell>{transaction.invoiceId}</TableCell>
                  <TableCell>{transaction.invoiceNumber}</TableCell>
                  <TableCell>{transaction.vendorName}</TableCell>
                  <TableCell>{transaction.lineNumber}</TableCell>
                  <TableCell>
                    <Tooltip title={transaction.description}>
                      <Typography variant="body2" noWrap sx={{ maxWidth: 250 }}>
                        {transaction.description}
                      </Typography>
                    </Tooltip>
                  </TableCell>
                  <TableCell align="right">
                    <Typography
                      variant="body2"
                      color={transaction.amount < 0 ? 'success.main' : 'text.primary'}
                      fontWeight={transaction.amount < 0 ? 'bold' : 'normal'}
                    >
                      ${transaction.amount.toFixed(2)}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={transaction.category || 'Uncategorized'}
                      color={getCategoryColor(transaction.category || '')}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    {transaction.categoryConfidence && (
                      <Chip
                        label={`${(transaction.categoryConfidence * 100).toFixed(0)}%`}
                        size="small"
                        sx={{
                          backgroundColor: getConfidenceColor(transaction.categoryConfidence),
                          color: 'white',
                        }}
                      />
                    )}
                  </TableCell>
                  <TableCell align="center">
                    {transaction.requiresReview ? (
                      <Tooltip title="Requires Review">
                        <WarningIcon color="warning" />
                      </Tooltip>
                    ) : (
                      <Tooltip title="Reviewed">
                        <CheckCircleIcon color="success" />
                      </Tooltip>
                    )}
                  </TableCell>
                  <TableCell align="center">
                    <IconButton
                      size="small"
                      onClick={() => handleEditClick(transaction)}
                      color="primary"
                    >
                      <EditIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
          </TableBody>
        </Table>
        <TablePagination
          rowsPerPageOptions={[5, 10, 25, 50]}
          component="div"
          count={filteredTransactions.length}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={handleChangePage}
          onRowsPerPageChange={handleChangeRowsPerPage}
        />
      </TableContainer>

      {filteredTransactions.length === 0 && !loading && (
        <Box textAlign="center" py={4}>
          <Typography variant="body1" color="text.secondary">
            No transactions found. {searchTerm || categoryFilter !== 'ALL' || reviewFilter !== 'ALL' 
              ? 'Try adjusting your filters.' 
              : 'Upload some invoices to get started.'}
          </Typography>
        </Box>
      )}
        </>
      )}

      {/* Edit Dialog */}
      <Dialog open={editDialogOpen} onClose={handleCloseDialog} maxWidth="md" fullWidth>
        <DialogTitle>Edit Transaction #{selectedTransaction?.lineItemId}</DialogTitle>
        <DialogContent>
          {selectedTransaction && (
            <Box mt={2}>
              <Typography variant="subtitle2" gutterBottom>
                Invoice: {selectedTransaction.invoiceNumber} (ID: {selectedTransaction.invoiceId})
              </Typography>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                {selectedTransaction.description}
              </Typography>

              <Box mt={3}>
                <FormControl fullWidth margin="normal">
                  <InputLabel>Category</InputLabel>
                  <Select
                    defaultValue={selectedTransaction.category}
                    label="Category"
                  >
                    {availableCategories.map((cat) => (
                      <MenuItem key={cat} value={cat}>
                        {cat.replace(/_/g, ' ')}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <TextField
                  fullWidth
                  label="Correction Reason"
                  multiline
                  rows={3}
                  margin="normal"
                  placeholder="Why are you changing the category?"
                />
              </Box>

              <Alert severity="info" sx={{ mt: 2 }}>
                Category updates require backend API implementation. Changes will be stored
                and used to improve the AI categorization model.
              </Alert>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog}>Cancel</Button>
          <Button variant="contained" onClick={handleCloseDialog} disabled>
            Save Changes (Coming Soon)
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default TransactionViewer;

