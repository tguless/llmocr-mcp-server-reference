import React, { useState } from 'react';
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
  Chip,
  Stack,
  Card,
  CardContent,
  Divider,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Alert,
} from '@mui/material';
import { Event, Inventory2, Edit, Delete, Save, Close, Add, Description as DescriptionIcon } from '@mui/icons-material';

interface ServicePeriod {
  startDate?: string;
  endDate?: string;
}

interface CustomMetadata {
  id?: number;
  key: string;
  value: string;
  createdBy?: string;
  updatedAt?: string;
}

interface EditableInvoiceDataDisplayProps {
  invoiceId: number;
  invoiceNumber?: string;
  servicePeriod?: ServicePeriod;
  customMetadata?: CustomMetadata[];
  onInvoiceNumberUpdate?: (invoiceNumber: string) => Promise<void>;
  onServicePeriodUpdate?: (servicePeriod: ServicePeriod) => Promise<void>;
  onMetadataAdd?: (metadata: CustomMetadata) => Promise<void>;
  onMetadataUpdate?: (id: number, metadata: CustomMetadata) => Promise<void>;
  onMetadataDelete?: (id: number) => Promise<void>;
}

const formatDate = (dateString?: string): string => {
  if (!dateString) return 'N/A';
  try {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return dateString;
  }
};

export const EditableInvoiceDataDisplay: React.FC<EditableInvoiceDataDisplayProps> = ({
  invoiceId,
  invoiceNumber: initialInvoiceNumber,
  servicePeriod,
  customMetadata = [],
  onInvoiceNumberUpdate,
  onServicePeriodUpdate,
  onMetadataAdd,
  onMetadataUpdate,
  onMetadataDelete,
}) => {
  const [editingInvoiceNumber, setEditingInvoiceNumber] = useState(false);
  const [invoiceNumberData, setInvoiceNumberData] = useState(initialInvoiceNumber || '');
  const [editingServicePeriod, setEditingServicePeriod] = useState(false);
  const [servicePeriodData, setServicePeriodData] = useState<ServicePeriod>({
    startDate: servicePeriod?.startDate,
    endDate: servicePeriod?.endDate,
  });
  const [editingMetadata, setEditingMetadata] = useState<{ [key: number | string]: boolean }>({});
  const [metadataData, setMetadataData] = useState<CustomMetadata[]>(customMetadata);
  const [addMetadataDialogOpen, setAddMetadataDialogOpen] = useState(false);
  const [newMetadata, setNewMetadata] = useState<CustomMetadata>({ key: '', value: '' });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);

  const hasInvoiceNumber = invoiceNumberData && invoiceNumberData.trim().length > 0;
  const hasServicePeriod = servicePeriodData?.startDate || servicePeriodData?.endDate;
  const hasMetadata = metadataData && metadataData.length > 0;

  const handleEditInvoiceNumber = () => {
    setEditingInvoiceNumber(true);
  };

  const handleSaveInvoiceNumber = async () => {
    try {
      setLoading(true);
      setError(null);
      if (onInvoiceNumberUpdate) {
        await onInvoiceNumberUpdate(invoiceNumberData);
      }
      setEditingInvoiceNumber(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update invoice number');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelEditInvoiceNumber = () => {
    setInvoiceNumberData(initialInvoiceNumber || '');
    setEditingInvoiceNumber(false);
  };

  const handleEditServicePeriod = () => {
    setEditingServicePeriod(true);
  };

  const handleSaveServicePeriod = async () => {
    try {
      setLoading(true);
      setError(null);
      if (onServicePeriodUpdate) {
        await onServicePeriodUpdate(servicePeriodData);
      }
      setEditingServicePeriod(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update service period');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelEditServicePeriod = () => {
    setServicePeriodData({
      startDate: servicePeriod?.startDate,
      endDate: servicePeriod?.endDate,
    });
    setEditingServicePeriod(false);
  };

  const handleServicePeriodChange = (field: 'startDate' | 'endDate', value: string) => {
    setServicePeriodData((prev) => ({
      ...prev,
      [field]: value,
    }));
  };

  const handleEditMetadata = (index: number) => {
    setEditingMetadata((prev) => ({
      ...prev,
      [index]: true,
    }));
  };

  const handleSaveMetadata = async (index: number) => {
    try {
      setLoading(true);
      setError(null);
      const metadata = metadataData[index];
      if (metadata.id && onMetadataUpdate) {
        await onMetadataUpdate(metadata.id, metadata);
      }
      setEditingMetadata((prev) => ({
        ...prev,
        [index]: false,
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update metadata');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelEditMetadata = (index: number) => {
    setMetadataData((prev) => {
      const newData = [...prev];
      newData[index] = customMetadata[index];
      return newData;
    });
    setEditingMetadata((prev) => ({
      ...prev,
      [index]: false,
    }));
  };

  const handleMetadataChange = (index: number, field: 'key' | 'value', value: string) => {
    setMetadataData((prev) => {
      const newData = [...prev];
      newData[index] = {
        ...newData[index],
        [field]: value,
      };
      return newData;
    });
  };

  const handleOpenDeleteConfirm = (id: number) => {
    setDeleteTargetId(id);
    setDeleteConfirmOpen(true);
  };

  const handleConfirmDelete = async () => {
    if (deleteTargetId && onMetadataDelete) {
      try {
        setLoading(true);
        setError(null);
        await onMetadataDelete(deleteTargetId);
        setMetadataData((prev) => prev.filter((m) => m.id !== deleteTargetId));
        setDeleteConfirmOpen(false);
        setDeleteTargetId(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to delete metadata');
      } finally {
        setLoading(false);
      }
    }
  };

  const handleAddMetadata = async () => {
    if (!newMetadata.key || !newMetadata.value) {
      setError('Both key and value are required');
      return;
    }
    try {
      setLoading(true);
      setError(null);
      if (onMetadataAdd) {
        await onMetadataAdd(newMetadata);
      }
      setMetadataData((prev) => [...prev, newMetadata]);
      setNewMetadata({ key: '', value: '' });
      setAddMetadataDialogOpen(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add metadata');
    } finally {
      setLoading(false);
    }
  };

  if (!hasInvoiceNumber && !hasServicePeriod && !hasMetadata) {
    return null;
  }

  return (
    <Stack spacing={3} sx={{ mb: 3 }}>
      {error && <Alert severity="error">{error}</Alert>}

      {/* Invoice Number Section */}
      {hasInvoiceNumber && (
        <Card variant="outlined">
          <CardContent>
            <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 2 }}>
              <DescriptionIcon color="primary" />
              <Typography variant="h6" sx={{ fontWeight: 600, flex: 1 }}>
                Invoice Number
              </Typography>
              {!editingInvoiceNumber && (
                <IconButton size="small" onClick={handleEditInvoiceNumber}>
                  <Edit fontSize="small" />
                </IconButton>
              )}
            </Stack>
            <Divider sx={{ mb: 2 }} />

            {editingInvoiceNumber ? (
              <Stack spacing={2}>
                <TextField
                  label="Invoice Number"
                  value={invoiceNumberData}
                  onChange={(e) => setInvoiceNumberData(e.target.value)}
                  fullWidth
                  disabled={loading}
                />
                <Stack direction="row" spacing={1} justifyContent="flex-end">
                  <Button
                    size="small"
                    startIcon={<Close />}
                    onClick={handleCancelEditInvoiceNumber}
                    disabled={loading}
                  >
                    Cancel
                  </Button>
                  <Button
                    size="small"
                    variant="contained"
                    startIcon={<Save />}
                    onClick={handleSaveInvoiceNumber}
                    disabled={loading || !invoiceNumberData.trim()}
                  >
                    Save
                  </Button>
                </Stack>
              </Stack>
            ) : (
              <Typography variant="body1" sx={{ fontWeight: 500, fontFamily: 'monospace' }}>
                {invoiceNumberData}
              </Typography>
            )}
          </CardContent>
        </Card>
      )}

      {/* Service Period Section */}
      {hasServicePeriod && (
        <Card variant="outlined">
          <CardContent>
            <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 2 }}>
              <Event color="primary" />
              <Typography variant="h6" sx={{ fontWeight: 600, flex: 1 }}>
                Service Period
              </Typography>
              {!editingServicePeriod && (
                <IconButton size="small" onClick={handleEditServicePeriod}>
                  <Edit fontSize="small" />
                </IconButton>
              )}
            </Stack>
            <Divider sx={{ mb: 2 }} />

            {editingServicePeriod ? (
              <Stack spacing={2}>
                <TextField
                  label="Start Date"
                  type="date"
                  value={servicePeriodData.startDate || ''}
                  onChange={(e) => handleServicePeriodChange('startDate', e.target.value)}
                  InputLabelProps={{ shrink: true }}
                  fullWidth
                  disabled={loading}
                />
                <TextField
                  label="End Date"
                  type="date"
                  value={servicePeriodData.endDate || ''}
                  onChange={(e) => handleServicePeriodChange('endDate', e.target.value)}
                  InputLabelProps={{ shrink: true }}
                  fullWidth
                  disabled={loading}
                />
                <Stack direction="row" spacing={1} justifyContent="flex-end">
                  <Button
                    size="small"
                    startIcon={<Close />}
                    onClick={handleCancelEditServicePeriod}
                    disabled={loading}
                  >
                    Cancel
                  </Button>
                  <Button
                    size="small"
                    variant="contained"
                    startIcon={<Save />}
                    onClick={handleSaveServicePeriod}
                    disabled={loading}
                  >
                    Save
                  </Button>
                </Stack>
              </Stack>
            ) : (
              <Stack direction="row" spacing={4}>
                <Box>
                  <Typography variant="caption" color="textSecondary" sx={{ display: 'block', mb: 0.5 }}>
                    Start Date
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 500 }}>
                    {formatDate(servicePeriodData.startDate)}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="caption" color="textSecondary" sx={{ display: 'block', mb: 0.5 }}>
                    End Date
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 500 }}>
                    {formatDate(servicePeriodData.endDate)}
                  </Typography>
                </Box>
              </Stack>
            )}
          </CardContent>
        </Card>
      )}

      {/* Custom Metadata Section */}
      <Card variant="outlined">
        <CardContent>
          <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 2 }}>
            <Inventory2 color="primary" />
            <Typography variant="h6" sx={{ fontWeight: 600, flex: 1 }}>
              Custom Metadata ({metadataData.length})
            </Typography>
            <Button
              size="small"
              startIcon={<Add />}
              onClick={() => setAddMetadataDialogOpen(true)}
            >
              Add
            </Button>
          </Stack>
          <Divider sx={{ mb: 2 }} />

          {metadataData.length === 0 ? (
            <Typography variant="body2" color="textSecondary">
              No metadata entries. Click "Add" to create one.
            </Typography>
          ) : (
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                    <TableCell sx={{ fontWeight: 600 }}>Key</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Value</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Created By</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Updated</TableCell>
                    <TableCell sx={{ fontWeight: 600, width: 120 }}>Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {metadataData.map((meta, index) => (
                    <TableRow key={index} hover>
                      {editingMetadata[index] ? (
                        <>
                          <TableCell>
                            <TextField
                              value={meta.key}
                              onChange={(e) => handleMetadataChange(index, 'key', e.target.value)}
                              size="small"
                              disabled={loading}
                            />
                          </TableCell>
                          <TableCell>
                            <TextField
                              value={meta.value}
                              onChange={(e) => handleMetadataChange(index, 'value', e.target.value)}
                              size="small"
                              fullWidth
                              multiline
                              maxRows={3}
                              disabled={loading}
                            />
                          </TableCell>
                          <TableCell>
                            <Typography variant="caption" color="textSecondary">
                              {meta.createdBy || 'System'}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="caption" color="textSecondary">
                              {meta.updatedAt ? formatDate(meta.updatedAt) : 'N/A'}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={0.5}>
                              <IconButton
                                size="small"
                                onClick={() => handleSaveMetadata(index)}
                                disabled={loading}
                              >
                                <Save fontSize="small" />
                              </IconButton>
                              <IconButton
                                size="small"
                                onClick={() => handleCancelEditMetadata(index)}
                                disabled={loading}
                              >
                                <Close fontSize="small" />
                              </IconButton>
                            </Stack>
                          </TableCell>
                        </>
                      ) : (
                        <>
                          <TableCell>
                            <Chip
                              label={meta.key}
                              variant="outlined"
                              size="small"
                              color="primary"
                            />
                          </TableCell>
                          <TableCell>
                            <Typography
                              variant="body2"
                              sx={{
                                fontFamily: 'monospace',
                                backgroundColor: '#f0f0f0',
                                padding: '4px 8px',
                                borderRadius: '4px',
                                maxWidth: '300px',
                                overflow: 'auto',
                              }}
                            >
                              {meta.value}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="caption" color="textSecondary">
                              {meta.createdBy || 'System'}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="caption" color="textSecondary">
                              {meta.updatedAt ? formatDate(meta.updatedAt) : 'N/A'}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={0.5}>
                              <IconButton
                                size="small"
                                onClick={() => handleEditMetadata(index)}
                              >
                                <Edit fontSize="small" />
                              </IconButton>
                              <IconButton
                                size="small"
                                color="error"
                                onClick={() => handleOpenDeleteConfirm(meta.id || index)}
                              >
                                <Delete fontSize="small" />
                              </IconButton>
                            </Stack>
                          </TableCell>
                        </>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>

      {/* Add Metadata Dialog */}
      <Dialog open={addMetadataDialogOpen} onClose={() => setAddMetadataDialogOpen(false)}>
        <DialogTitle>Add New Metadata</DialogTitle>
        <DialogContent sx={{ minWidth: 400 }}>
          <Stack spacing={2} sx={{ mt: 2 }}>
            <TextField
              label="Key"
              value={newMetadata.key}
              onChange={(e) => setNewMetadata((prev) => ({ ...prev, key: e.target.value }))}
              fullWidth
              disabled={loading}
            />
            <TextField
              label="Value"
              value={newMetadata.value}
              onChange={(e) => setNewMetadata((prev) => ({ ...prev, value: e.target.value }))}
              fullWidth
              multiline
              rows={4}
              disabled={loading}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddMetadataDialogOpen(false)} disabled={loading}>
            Cancel
          </Button>
          <Button onClick={handleAddMetadata} variant="contained" disabled={loading || !newMetadata.key || !newMetadata.value}>
            Add
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteConfirmOpen} onClose={() => setDeleteConfirmOpen(false)}>
        <DialogTitle>Confirm Delete</DialogTitle>
        <DialogContent>
          <Typography>Are you sure you want to delete this metadata entry?</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirmOpen(false)} disabled={loading}>
            Cancel
          </Button>
          <Button onClick={handleConfirmDelete} variant="contained" color="error" disabled={loading}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
};

export default EditableInvoiceDataDisplay;
