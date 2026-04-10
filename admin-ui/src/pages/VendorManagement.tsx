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
  Stack,
  Tooltip,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import BusinessIcon from '@mui/icons-material/Business';
import BlockIcon from '@mui/icons-material/Block';
import api from '../services/api';

interface Vendor {
  id: number;
  vendorName: string;
  vendorCode?: string;
  taxId?: string;
  address?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
  phone?: string;
  email?: string;
  website?: string;
  contactPerson?: string;
  paymentTerms?: string;
  category?: string;
  notes?: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

const VendorManagement: React.FC = () => {
  const [vendors, setVendors] = useState<Vendor[]>([]);
  const [filteredVendors, setFilteredVendors] = useState<Vendor[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [searchTerm, setSearchTerm] = useState('');
  
  // Dialog states
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [selectedVendor, setSelectedVendor] = useState<Vendor | null>(null);
  
  // Form fields
  const [formData, setFormData] = useState({
    vendorName: '',
    vendorCode: '',
    taxId: '',
    address: '',
    city: '',
    state: '',
    postalCode: '',
    country: '',
    phone: '',
    email: '',
    website: '',
    contactPerson: '',
    paymentTerms: '',
    category: '',
    notes: '',
  });

  useEffect(() => {
    loadVendors();
  }, []);

  useEffect(() => {
    filterVendors();
  }, [searchTerm, vendors]);

  const loadVendors = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.get('/api/admin/vendors', {
        params: {
          size: 1000 // Get all vendors (paginated backend, but we want all for client-side filtering)
        }
      });
      // Backend returns paginated response: { content: [...], totalElements, ... }
      const vendorsData = Array.isArray(response.data?.content) 
        ? response.data.content 
        : Array.isArray(response.data) 
        ? response.data 
        : [];
      setVendors(vendorsData);
      setFilteredVendors(vendorsData);
    } catch (err: any) {
      console.error('Failed to load vendors:', err);
      setError(err.response?.data?.error || 'Failed to load vendors. Please try again.');
      setVendors([]);
      setFilteredVendors([]);
    } finally {
      setLoading(false);
    }
  };

  const filterVendors = () => {
    if (!Array.isArray(vendors)) {
      setFilteredVendors([]);
      return;
    }

    if (!searchTerm.trim()) {
      setFilteredVendors(vendors);
      return;
    }

    const term = searchTerm.toLowerCase();
    const filtered = vendors.filter(vendor =>
      vendor.vendorName.toLowerCase().includes(term) ||
      vendor.vendorCode?.toLowerCase().includes(term) ||
      vendor.taxId?.toLowerCase().includes(term) ||
      vendor.email?.toLowerCase().includes(term) ||
      vendor.city?.toLowerCase().includes(term) ||
      vendor.category?.toLowerCase().includes(term)
    );
    setFilteredVendors(filtered);
    setPage(0);
  };

  const handleChangePage = (_event: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const resetForm = () => {
    setFormData({
      vendorName: '',
      vendorCode: '',
      taxId: '',
      address: '',
      city: '',
      state: '',
      postalCode: '',
      country: '',
      phone: '',
      email: '',
      website: '',
      contactPerson: '',
      paymentTerms: '',
      category: '',
      notes: '',
    });
  };

  const handleCreateDialogOpen = () => {
    resetForm();
    setCreateDialogOpen(true);
  };

  const handleEditDialogOpen = (vendor: Vendor) => {
    setSelectedVendor(vendor);
    setFormData({
      vendorName: vendor.vendorName,
      vendorCode: vendor.vendorCode || '',
      taxId: vendor.taxId || '',
      address: vendor.address || '',
      city: vendor.city || '',
      state: vendor.state || '',
      postalCode: vendor.postalCode || '',
      country: vendor.country || '',
      phone: vendor.phone || '',
      email: vendor.email || '',
      website: vendor.website || '',
      contactPerson: vendor.contactPerson || '',
      paymentTerms: vendor.paymentTerms || '',
      category: vendor.category || '',
      notes: vendor.notes || '',
    });
    setEditDialogOpen(true);
  };

  const handleCreateVendor = async () => {
    setError(null);
    setSuccess(null);

    if (!formData.vendorName.trim()) {
      setError('Vendor name is required');
      return;
    }

    try {
      await api.post('/api/admin/vendors', formData);
      setSuccess('Vendor created successfully');
      setCreateDialogOpen(false);
      resetForm();
      loadVendors();
    } catch (err: any) {
      console.error('Failed to create vendor:', err);
      setError(err.response?.data?.error || 'Failed to create vendor. Please try again.');
    }
  };

  const handleUpdateVendor = async () => {
    if (!selectedVendor) return;

    setError(null);
    setSuccess(null);

    if (!formData.vendorName.trim()) {
      setError('Vendor name is required');
      return;
    }

    try {
      await api.put(`/api/admin/vendors/${selectedVendor.id}`, formData);
      setSuccess('Vendor updated successfully');
      setEditDialogOpen(false);
      setSelectedVendor(null);
      resetForm();
      loadVendors();
    } catch (err: any) {
      console.error('Failed to update vendor:', err);
      setError(err.response?.data?.error || 'Failed to update vendor. Please try again.');
    }
  };

  const handleDeactivateVendor = async (vendor: Vendor) => {
    if (!window.confirm(`Deactivate vendor "${vendor.vendorName}"?`)) {
      return;
    }

    setError(null);
    setSuccess(null);

    try {
      await api.patch(`/api/admin/vendors/${vendor.id}/deactivate`);
      setSuccess('Vendor deactivated successfully');
      loadVendors();
    } catch (err: any) {
      console.error('Failed to deactivate vendor:', err);
      setError(err.response?.data?.error || 'Failed to deactivate vendor. Please try again.');
    }
  };

  const handleDeleteVendor = async (vendor: Vendor) => {
    if (!window.confirm(
      `Permanently delete vendor "${vendor.vendorName}"?\n\nThis cannot be undone.`
    )) {
      return;
    }

    setError(null);
    setSuccess(null);

    try {
      await api.delete(`/api/admin/vendors/${vendor.id}`);
      setSuccess('Vendor permanently deleted');
      loadVendors();
    } catch (err: any) {
      console.error('Failed to delete vendor:', err);
      setError(err.response?.data?.error || 'Failed to permanently delete vendor. Please try again.');
    }
  };

  const getStatusChip = (active: boolean) => {
    return active ? (
      <Chip label="Active" color="success" size="small" />
    ) : (
      <Chip label="Inactive" color="default" size="small" />
    );
  };

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1" fontWeight={600}>
          Vendor Management
        </Typography>
        <Stack direction="row" spacing={2}>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={handleCreateDialogOpen}
          >
            Add Vendor
          </Button>
          <Tooltip title="Refresh">
            <IconButton onClick={loadVendors} color="primary">
              <RefreshIcon />
            </IconButton>
          </Tooltip>
        </Stack>
      </Stack>

      {error && (
        <Alert severity="error" onClose={() => setError(null)} sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {success && (
        <Alert severity="success" onClose={() => setSuccess(null)} sx={{ mb: 2 }}>
          {success}
        </Alert>
      )}

      {/* Search Bar */}
      <Paper sx={{ p: 2, mb: 2 }}>
        <TextField
          fullWidth
          placeholder="Search vendors by name, code, tax ID, email, city, or category..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          InputProps={{
            startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} />,
          }}
        />
      </Paper>

      {/* Vendors Table */}
      <Paper>
        <TableContainer>
          {loading ? (
            <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
              <CircularProgress />
            </Box>
          ) : !Array.isArray(filteredVendors) || filteredVendors.length === 0 ? (
            <Box display="flex" flexDirection="column" alignItems="center" justifyContent="center" minHeight="400px" p={3}>
              <BusinessIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
              <Typography variant="h6" color="text.secondary" gutterBottom>
                {searchTerm ? 'No vendors found' : 'No vendors yet'}
              </Typography>
              <Typography variant="body2" color="text.secondary" mb={2}>
                {searchTerm ? 'Try a different search term' : 'Get started by adding your first vendor'}
              </Typography>
              {!searchTerm && (
                <Button variant="contained" startIcon={<AddIcon />} onClick={handleCreateDialogOpen}>
                  Add Vendor
                </Button>
              )}
            </Box>
          ) : (
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell><strong>Vendor Name</strong></TableCell>
                  <TableCell><strong>Code</strong></TableCell>
                  <TableCell><strong>Tax ID</strong></TableCell>
                  <TableCell><strong>Contact</strong></TableCell>
                  <TableCell><strong>Location</strong></TableCell>
                  <TableCell><strong>Category</strong></TableCell>
                  <TableCell><strong>Status</strong></TableCell>
                  <TableCell align="right"><strong>Actions</strong></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {Array.isArray(filteredVendors) && filteredVendors.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage).map((vendor) => (
                  <TableRow key={vendor.id} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>
                        {vendor.vendorName}
                      </Typography>
                      {vendor.contactPerson && (
                        <Typography variant="caption" color="text.secondary">
                          {vendor.contactPerson}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>{vendor.vendorCode || '-'}</TableCell>
                    <TableCell>{vendor.taxId || '-'}</TableCell>
                    <TableCell>
                      {vendor.email && (
                        <Typography variant="body2">{vendor.email}</Typography>
                      )}
                      {vendor.phone && (
                        <Typography variant="caption" color="text.secondary">
                          {vendor.phone}
                        </Typography>
                      )}
                      {!vendor.email && !vendor.phone && '-'}
                    </TableCell>
                    <TableCell>
                      {vendor.city && vendor.state
                        ? `${vendor.city}, ${vendor.state}`
                        : vendor.city || vendor.state || '-'}
                    </TableCell>
                    <TableCell>{vendor.category || '-'}</TableCell>
                    <TableCell>
                      {getStatusChip(vendor.active)}
                    </TableCell>
                    <TableCell align="right">
                      <Tooltip title="Edit">
                        <IconButton size="small" onClick={() => handleEditDialogOpen(vendor)} color="primary">
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title={vendor.active ? "Deactivate (soft delete)" : "Already inactive"}>
                        <span>
                          <IconButton
                            size="small"
                            onClick={() => handleDeactivateVendor(vendor)}
                            color="warning"
                            disabled={!vendor.active}
                          >
                            <BlockIcon fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                      <Tooltip title="Delete permanently (hard delete)">
                        <IconButton
                          size="small"
                          onClick={() => handleDeleteVendor(vendor)}
                          color="error"
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </TableContainer>
        {Array.isArray(filteredVendors) && filteredVendors.length > 0 && (
          <TablePagination
            rowsPerPageOptions={[5, 10, 25, 50]}
            component="div"
            count={filteredVendors.length}
            rowsPerPage={rowsPerPage}
            page={page}
            onPageChange={handleChangePage}
            onRowsPerPageChange={handleChangeRowsPerPage}
          />
        )}
      </Paper>

      {/* Create Vendor Dialog */}
      <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Add New Vendor</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="Vendor Name *"
                value={formData.vendorName}
                onChange={(e) => setFormData({ ...formData, vendorName: e.target.value })}
              />
              <TextField
                fullWidth
                label="Vendor Code"
                value={formData.vendorCode}
                onChange={(e) => setFormData({ ...formData, vendorCode: e.target.value })}
              />
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="Tax ID"
                value={formData.taxId}
                onChange={(e) => setFormData({ ...formData, taxId: e.target.value })}
              />
              <TextField
                fullWidth
                label="Category"
                value={formData.category}
                onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                placeholder="e.g., Supplier, Contractor, Utility"
              />
            </Stack>
            <TextField
              fullWidth
              label="Address"
              value={formData.address}
              onChange={(e) => setFormData({ ...formData, address: e.target.value })}
            />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="City"
                value={formData.city}
                onChange={(e) => setFormData({ ...formData, city: e.target.value })}
              />
              <TextField
                fullWidth
                label="State/Province"
                value={formData.state}
                onChange={(e) => setFormData({ ...formData, state: e.target.value })}
              />
              <TextField
                fullWidth
                label="Postal Code"
                value={formData.postalCode}
                onChange={(e) => setFormData({ ...formData, postalCode: e.target.value })}
              />
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="Country"
                value={formData.country}
                onChange={(e) => setFormData({ ...formData, country: e.target.value })}
              />
              <TextField
                fullWidth
                label="Contact Person"
                value={formData.contactPerson}
                onChange={(e) => setFormData({ ...formData, contactPerson: e.target.value })}
              />
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="Phone"
                value={formData.phone}
                onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
              />
              <TextField
                fullWidth
                label="Email"
                type="email"
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              />
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="Website"
                value={formData.website}
                onChange={(e) => setFormData({ ...formData, website: e.target.value })}
              />
              <TextField
                fullWidth
                label="Payment Terms"
                value={formData.paymentTerms}
                onChange={(e) => setFormData({ ...formData, paymentTerms: e.target.value })}
                placeholder="e.g., Net 30"
              />
            </Stack>
            <TextField
              fullWidth
              label="Notes"
              multiline
              rows={3}
              value={formData.notes}
              onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleCreateVendor} variant="contained">
            Create Vendor
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit Vendor Dialog */}
      <Dialog open={editDialogOpen} onClose={() => setEditDialogOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Edit Vendor</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="Vendor Name *"
                value={formData.vendorName}
                onChange={(e) => setFormData({ ...formData, vendorName: e.target.value })}
              />
              <TextField
                fullWidth
                label="Vendor Code"
                value={formData.vendorCode}
                onChange={(e) => setFormData({ ...formData, vendorCode: e.target.value })}
              />
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="Tax ID"
                value={formData.taxId}
                onChange={(e) => setFormData({ ...formData, taxId: e.target.value })}
              />
              <TextField
                fullWidth
                label="Category"
                value={formData.category}
                onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                placeholder="e.g., Supplier, Contractor, Utility"
              />
            </Stack>
            <TextField
              fullWidth
              label="Address"
              value={formData.address}
              onChange={(e) => setFormData({ ...formData, address: e.target.value })}
            />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="City"
                value={formData.city}
                onChange={(e) => setFormData({ ...formData, city: e.target.value })}
              />
              <TextField
                fullWidth
                label="State/Province"
                value={formData.state}
                onChange={(e) => setFormData({ ...formData, state: e.target.value })}
              />
              <TextField
                fullWidth
                label="Postal Code"
                value={formData.postalCode}
                onChange={(e) => setFormData({ ...formData, postalCode: e.target.value })}
              />
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="Country"
                value={formData.country}
                onChange={(e) => setFormData({ ...formData, country: e.target.value })}
              />
              <TextField
                fullWidth
                label="Contact Person"
                value={formData.contactPerson}
                onChange={(e) => setFormData({ ...formData, contactPerson: e.target.value })}
              />
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="Phone"
                value={formData.phone}
                onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
              />
              <TextField
                fullWidth
                label="Email"
                type="email"
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              />
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="Website"
                value={formData.website}
                onChange={(e) => setFormData({ ...formData, website: e.target.value })}
              />
              <TextField
                fullWidth
                label="Payment Terms"
                value={formData.paymentTerms}
                onChange={(e) => setFormData({ ...formData, paymentTerms: e.target.value })}
                placeholder="e.g., Net 30"
              />
            </Stack>
            <TextField
              fullWidth
              label="Notes"
              multiline
              rows={3}
              value={formData.notes}
              onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleUpdateVendor} variant="contained">
            Update Vendor
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default VendorManagement;

