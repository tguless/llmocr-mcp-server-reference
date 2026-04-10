import React, { useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  Button,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  TextField,
  MenuItem,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Stack,
  Divider,
  Alert,
  Tooltip
} from '@mui/material';
import {
  ArrowBack as BackIcon,
  Edit as EditIcon,
  Save as SaveIcon,
  Cancel as CancelIcon,
  Add as AddIcon,
  Delete as DeleteIcon,
  Fullscreen as FullscreenIcon
} from '@mui/icons-material';
import { useParams, useNavigate } from 'react-router-dom';
import { mockContractReviews } from '../mockData/contractReviewMockData';
import { ContractRate } from '../types/contractReview';

const ContractViewer: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [editingRateId, setEditingRateId] = useState<string | null>(null);
  const [editedRate, setEditedRate] = useState<ContractRate | null>(null);
  const [addRateDialogOpen, setAddRateDialogOpen] = useState(false);

  const contract = mockContractReviews.find(c => c.id === parseInt(id || '0'));

  if (!contract) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">Contract not found</Alert>
      </Box>
    );
  }

  const rateSchedule = contract.rateSchedules?.[0]; // For POC, show first schedule

  const handleEditRate = (rate: ContractRate) => {
    setEditingRateId(rate.rateId);
    setEditedRate({ ...rate });
  };

  const handleSaveRate = () => {
    // TODO: API call to save rate
    console.log('Saving rate:', editedRate);
    setEditingRateId(null);
    setEditedRate(null);
  };

  const handleCancelEdit = () => {
    setEditingRateId(null);
    setEditedRate(null);
  };

  const handleDeleteRate = (rateId: string) => {
    // TODO: API call to delete rate
    console.log('Deleting rate:', rateId);
  };

  const formatCurrency = (amount: number, decimals: number = 4) => {
    return `$${amount.toFixed(decimals)}`;
  };

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Box>
          <Button
            startIcon={<BackIcon />}
            onClick={() => navigate('/contracts')}
            sx={{ mb: 1 }}
          >
            Back to Contracts
          </Button>
          <Typography variant="h5" gutterBottom>
            {contract.metadata.contractName}
          </Typography>
          <Stack direction="row" spacing={1}>
            {contract.metadata.technologies?.map(tech => (
              <Chip key={tech} label={tech} size="small" color="primary" />
            ))}
            {contract.metadata.codDate && (
              <Chip label={`COD: ${contract.metadata.codDate}`} size="small" variant="outlined" />
            )}
          </Stack>
        </Box>
        <Button
          variant="outlined"
          startIcon={<FullscreenIcon />}
          onClick={() => navigate(`/contracts/${id}/detail`)}
        >
          Full Detail View
        </Button>
      </Box>

      {/* Side-by-Side Layout */}
      <Box sx={{ display: 'flex', gap: 2, height: 'calc(100vh - 250px)' }}>
        {/* LEFT: PDF Viewer */}
        <Paper sx={{ flex: 1, p: 2, overflow: 'auto' }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Typography variant="h6">Contract PDF</Typography>
            <Button size="small" variant="outlined">
              Download PDF
            </Button>
          </Box>
          <Box
            sx={{
              width: '100%',
              height: 'calc(100% - 60px)',
              backgroundColor: '#f5f5f5',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              border: '1px solid #ddd',
              borderRadius: 1
            }}
          >
            <Typography variant="body2" color="text.secondary">
              📄 PDF Viewer - Contract Document<br />
              (Integration with PDF viewer pending)
            </Typography>
          </Box>
        </Paper>

        {/* RIGHT: Editable Rate Data */}
        <Paper sx={{ flex: 1, p: 2, overflow: 'auto' }}>
          {/* Contract Metadata */}
          <Box sx={{ mb: 3 }}>
            <Typography variant="h6" gutterBottom>Contract Metadata</Typography>
            <Divider sx={{ mb: 2 }} />
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
              <Box>
                <Typography variant="caption" color="text.secondary">Fund</Typography>
                <Typography variant="body2">{contract.metadata.fundName}</Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary">Project</Typography>
                <Typography variant="body2">{contract.metadata.projectName}</Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary">Type</Typography>
                <Typography variant="body2">{contract.metadata.contractType}</Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary">COD Date</Typography>
                <Typography variant="body2">{contract.metadata.codDate || 'Not specified'}</Typography>
              </Box>
            </Box>
          </Box>

          {/* Rate Schedule */}
          <Box sx={{ mb: 2 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="h6">
                Rate Schedule
                {rateSchedule && (
                  <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                    ({rateSchedule.scheduleName})
                  </Typography>
                )}
              </Typography>
              <Button
                size="small"
                startIcon={<AddIcon />}
                onClick={() => setAddRateDialogOpen(true)}
              >
                Add Rate
              </Button>
            </Box>

            {rateSchedule && rateSchedule.accountNumbers && (
              <Alert severity="info" sx={{ mb: 2 }}>
                Applies to accounts: {rateSchedule.accountNumbers.join(', ')}
              </Alert>
            )}

            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                    <TableCell><strong>Type</strong></TableCell>
                    <TableCell><strong>Description</strong></TableCell>
                    <TableCell align="right"><strong>Unit Price</strong></TableCell>
                    <TableCell><strong>Unit</strong></TableCell>
                    <TableCell><strong>Time of Use</strong></TableCell>
                    <TableCell><strong>Clause</strong></TableCell>
                    <TableCell align="center"><strong>Actions</strong></TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rateSchedule?.rates.map((rate) => (
                    <TableRow key={rate.rateId} hover>
                      <TableCell>
                        {editingRateId === rate.rateId ? (
                          <TextField
                            select
                            size="small"
                            value={editedRate?.rateType || rate.rateType}
                            onChange={(e) => setEditedRate({ ...editedRate!, rateType: e.target.value as any })}
                            fullWidth
                          >
                            <MenuItem value="Energy">Energy</MenuItem>
                            <MenuItem value="Demand">Demand</MenuItem>
                            <MenuItem value="Fuel">Fuel</MenuItem>
                            <MenuItem value="Transmission">Transmission</MenuItem>
                            <MenuItem value="Fixed">Fixed</MenuItem>
                            <MenuItem value="Other">Other</MenuItem>
                          </TextField>
                        ) : (
                          <Chip
                            label={rate.rateType}
                            size="small"
                            color={
                              rate.rateType === 'Energy' ? 'primary' :
                              rate.rateType === 'Demand' ? 'secondary' :
                              rate.rateType === 'Fixed' ? 'success' : 'default'
                            }
                          />
                        )}
                      </TableCell>
                      <TableCell>
                        {editingRateId === rate.rateId ? (
                          <TextField
                            size="small"
                            value={editedRate?.description || ''}
                            onChange={(e) => setEditedRate({ ...editedRate!, description: e.target.value })}
                            fullWidth
                          />
                        ) : (
                          <Typography variant="body2">{rate.description}</Typography>
                        )}
                      </TableCell>
                      <TableCell align="right">
                        {editingRateId === rate.rateId ? (
                          <TextField
                            size="small"
                            type="number"
                            value={editedRate?.unitPrice || 0}
                            onChange={(e) => setEditedRate({ ...editedRate!, unitPrice: parseFloat(e.target.value) })}
                            inputProps={{ step: 0.0001, min: 0 }}
                            sx={{ width: 120 }}
                          />
                        ) : (
                          <Typography variant="body2" fontWeight={600} color="success.main">
                            {formatCurrency(rate.unitPrice, rate.unitPrice < 1 ? 4 : 2)}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        {editingRateId === rate.rateId ? (
                          <TextField
                            select
                            size="small"
                            value={editedRate?.unitOfMeasure || rate.unitOfMeasure}
                            onChange={(e) => setEditedRate({ ...editedRate!, unitOfMeasure: e.target.value as any })}
                            sx={{ width: 100 }}
                          >
                            <MenuItem value="kWh">kWh</MenuItem>
                            <MenuItem value="kW">kW</MenuItem>
                            <MenuItem value="Therm">Therm</MenuItem>
                            <MenuItem value="CCF">CCF</MenuItem>
                            <MenuItem value="Fixed">Fixed</MenuItem>
                            <MenuItem value="Other">Other</MenuItem>
                          </TextField>
                        ) : (
                          rate.unitOfMeasure
                        )}
                      </TableCell>
                      <TableCell>
                        {editingRateId === rate.rateId ? (
                          <TextField
                            size="small"
                            value={editedRate?.timeOfUse || ''}
                            onChange={(e) => setEditedRate({ ...editedRate!, timeOfUse: e.target.value })}
                            placeholder="Peak, Off-Peak"
                            sx={{ width: 120 }}
                          />
                        ) : (
                          <Typography variant="caption">{rate.timeOfUse || '-'}</Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        {editingRateId === rate.rateId ? (
                          <TextField
                            size="small"
                            value={editedRate?.clauseReference || ''}
                            onChange={(e) => setEditedRate({ ...editedRate!, clauseReference: e.target.value })}
                            placeholder="Schedule A"
                            sx={{ width: 120 }}
                          />
                        ) : (
                          <Typography variant="caption">{rate.clauseReference || '-'}</Typography>
                        )}
                      </TableCell>
                      <TableCell align="center">
                        {editingRateId === rate.rateId ? (
                          <Stack direction="row" spacing={1}>
                            <IconButton size="small" color="primary" onClick={handleSaveRate}>
                              <SaveIcon fontSize="small" />
                            </IconButton>
                            <IconButton size="small" onClick={handleCancelEdit}>
                              <CancelIcon fontSize="small" />
                            </IconButton>
                          </Stack>
                        ) : (
                          <Stack direction="row" spacing={1}>
                            <Tooltip title="Edit rate">
                              <IconButton size="small" onClick={() => handleEditRate(rate)}>
                                <EditIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Delete rate">
                              <IconButton size="small" color="error" onClick={() => handleDeleteRate(rate.rateId)}>
                                <DeleteIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          </Stack>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>

          {/* Summary Stats */}
          <Box sx={{ mt: 3 }}>
            <Typography variant="h6" gutterBottom>Quick Stats</Typography>
            <Divider sx={{ mb: 2 }} />
            <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
              <Paper variant="outlined" sx={{ p: 2, flex: '1 1 calc(50% - 8px)', minWidth: 150 }}>
                <Typography variant="caption" color="text.secondary">Total Rates</Typography>
                <Typography variant="h4" color="success.main">
                  {rateSchedule?.rates.length || 0}
                </Typography>
              </Paper>
              <Paper variant="outlined" sx={{ p: 2, flex: '1 1 calc(50% - 8px)', minWidth: 150 }}>
                <Typography variant="caption" color="text.secondary">Obligations</Typography>
                <Typography variant="h4" color="info.main">
                  {contract.summary.totalObligationsExtracted}
                </Typography>
              </Paper>
              <Paper variant="outlined" sx={{ p: 2, flex: '1 1 calc(50% - 8px)', minWidth: 150 }}>
                <Typography variant="caption" color="text.secondary">Missing</Typography>
                <Typography variant="h4" color="warning.main">
                  {contract.summary.totalMissingObligations}
                </Typography>
              </Paper>
              <Paper variant="outlined" sx={{ p: 2, flex: '1 1 calc(50% - 8px)', minWidth: 150 }}>
                <Typography variant="caption" color="text.secondary">Risks</Typography>
                <Typography variant="h4" color="error.main">
                  {contract.summary.totalRisksUnclear}
                </Typography>
              </Paper>
            </Box>
          </Box>

          {/* Key Rates Summary */}
          {rateSchedule && (
            <Box sx={{ mt: 3 }}>
              <Typography variant="h6" gutterBottom>Key Rates for Variance Assessment</Typography>
              <Divider sx={{ mb: 2 }} />
              <Stack spacing={1}>
                {rateSchedule.rates
                  .filter(r => ['Energy', 'Demand', 'Fixed'].includes(r.rateType))
                  .map((rate) => (
                    <Box key={rate.rateId} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', py: 1 }}>
                      <Box>
                        <Typography variant="body2" fontWeight={500}>{rate.description}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {rate.rateType} • {rate.clauseReference}
                        </Typography>
                      </Box>
                      <Typography variant="h6" color="success.main">
                        {formatCurrency(rate.unitPrice, rate.unitPrice < 1 ? 4 : 2)}/{rate.unitOfMeasure}
                      </Typography>
                    </Box>
                  ))}
              </Stack>
            </Box>
          )}
        </Paper>
      </Box>

      {/* Add Rate Dialog */}
      <Dialog open={addRateDialogOpen} onClose={() => setAddRateDialogOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Add New Rate</DialogTitle>
        <DialogContent>
          <Alert severity="info" sx={{ mb: 2 }}>
            Add a new contracted rate for invoice variance assessment
          </Alert>
          {/* TODO: Add form fields for new rate */}
          <Typography variant="body2">Form fields coming soon...</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddRateDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => setAddRateDialogOpen(false)}>Add Rate</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ContractViewer;

