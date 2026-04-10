import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Chip,
  TextField,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  CircularProgress,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Divider,
  Stack,
  FormControlLabel,
  Checkbox,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import EditIcon from '@mui/icons-material/Edit';
import SearchIcon from '@mui/icons-material/Search';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import { metadataKeyAPI, MetadataKeyInfo } from '../services/api';

const MetadataManagement: React.FC = () => {
  const [metadataKeys, setMetadataKeys] = useState<MetadataKeyInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedKey, setSelectedKey] = useState<MetadataKeyInfo | null>(null);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [categorySummary, setCategorySummary] = useState<any>(null);
  const [newKey, setNewKey] = useState<Partial<MetadataKeyInfo>>({
    keyCode: '',
    displayName: '',
    description: '',
    category: 'CUSTOM',
    dataType: 'STRING',
    required: false,
    billable: false,
    active: true,
  });

  useEffect(() => {
    loadMetadataKeys();
    loadCategorySummary();
  }, []);

  const loadMetadataKeys = async () => {
    try {
      setLoading(true);
      setError(null);
      const result = await metadataKeyAPI.getMetadataKeys();
      setMetadataKeys(result);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to load metadata keys');
      console.error('Error loading metadata keys:', err);
    } finally {
      setLoading(false);
    }
  };

  const loadCategorySummary = async () => {
    try {
      const summary = await metadataKeyAPI.getCategoriesSummary();
      setCategorySummary(summary);
    } catch (err: any) {
      console.error('Error loading category summary:', err);
    }
  };

  const handleEditClick = (key: MetadataKeyInfo) => {
    setSelectedKey(key);
    setEditDialogOpen(true);
  };

  const handleCreateClick = () => {
    setNewKey({
      keyCode: '',
      displayName: '',
      description: '',
      category: 'CUSTOM',
      dataType: 'STRING',
      required: false,
      billable: false,
      active: true,
    });
    setCreateDialogOpen(true);
  };

  const handleCloseEditDialog = () => {
    setEditDialogOpen(false);
    setSelectedKey(null);
  };

  const handleCloseCreateDialog = () => {
    setCreateDialogOpen(false);
    setNewKey({});
  };

  const handleSaveEdit = async () => {
    if (!selectedKey) return;
    try {
      await metadataKeyAPI.updateMetadataKey(selectedKey.keyCode, selectedKey);
      await loadMetadataKeys();
      handleCloseEditDialog();
    } catch (err: any) {
      setError(err.message || 'Failed to update metadata key');
    }
  };

  const handleSaveCreate = async () => {
    if (!newKey.keyCode || !newKey.displayName || !newKey.description) {
      setError('Key code, display name, and description are required');
      return;
    }
    try {
      await metadataKeyAPI.createMetadataKey(newKey);
      await loadMetadataKeys();
      handleCloseCreateDialog();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to create metadata key');
    }
  };

  const handleDelete = async (keyCode: string) => {
    if (window.confirm(`Are you sure you want to delete the metadata key "${keyCode}"?`)) {
      try {
        await metadataKeyAPI.deleteMetadataKey(keyCode);
        await loadMetadataKeys();
      } catch (err: any) {
        setError(err.message || 'Failed to delete metadata key');
      }
    }
  };

  const handleToggleActive = async (key: MetadataKeyInfo) => {
    try {
      await metadataKeyAPI.updateMetadataKey(key.keyCode, {
        ...key,
        active: !key.active,
      });
      await loadMetadataKeys();
    } catch (err: any) {
      setError(err.message || 'Failed to toggle metadata key status');
    }
  };

  const filteredKeys = metadataKeys.filter(
    (key) =>
      key.keyCode.toLowerCase().includes(searchTerm.toLowerCase()) ||
      key.displayName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      key.description.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (key.category && key.category.toLowerCase().includes(searchTerm.toLowerCase()))
  );

  const getCategoryColor = (category: string) => {
    const colors: { [key: string]: any } = {
      GENERATION: { bg: '#e3f2fd', text: '#1976d2' },
      RATES: { bg: '#f3e5f5', text: '#7b1fa2' },
      IDENTIFIERS: { bg: '#e8f5e9', text: '#388e3c' },
      BILLING: { bg: '#fff3e0', text: '#f57c00' },
      PERFORMANCE: { bg: '#fce4ec', text: '#c2185b' },
      CREDITS: { bg: '#e0f2f1', text: '#00796b' },
      CUSTOM: { bg: '#f5f5f5', text: '#616161' },
    };
    return colors[category] || colors.CUSTOM;
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1">
          Metadata Key Management
        </Typography>
        <Stack direction="row" spacing={1}>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={handleCreateClick}
          >
            Add New Key
          </Button>
          <Button
            variant="outlined"
            startIcon={<SearchIcon />}
            onClick={loadMetadataKeys}
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

      {/* Category Summary */}
      {categorySummary && (
        <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
          <Card sx={{ flex: '1 1 calc(25% - 6px)', minWidth: '200px' }}>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Total Keys
              </Typography>
              <Typography variant="h5">
                {categorySummary.totalKeys}
              </Typography>
            </CardContent>
          </Card>
          <Card sx={{ flex: '1 1 calc(25% - 6px)', minWidth: '200px' }}>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Active Keys
              </Typography>
              <Typography variant="h5">
                {categorySummary.activeKeys}
              </Typography>
            </CardContent>
          </Card>
          <Card sx={{ flex: '1 1 calc(25% - 6px)', minWidth: '200px' }}>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Required Keys
              </Typography>
              <Typography variant="h5">
                {categorySummary.requiredKeys}
              </Typography>
            </CardContent>
          </Card>
          <Card sx={{ flex: '1 1 calc(25% - 6px)', minWidth: '200px' }}>
            <CardContent>
              <Typography color="textSecondary" gutterBottom>
                Billable Keys
              </Typography>
              <Typography variant="h5">
                {categorySummary.billableKeys}
              </Typography>
            </CardContent>
          </Card>
        </Box>
      )}

      <TextField
        fullWidth
        variant="outlined"
        placeholder="Search by key code, name, description, or category..."
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
        sx={{ mb: 3 }}
        InputProps={{
          startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} />,
        }}
      />

      <Stack spacing={2}>
        {filteredKeys.length === 0 ? (
          <Alert severity="info">No metadata keys found matching your search.</Alert>
        ) : (
          filteredKeys.map((metadataKey) => (
            <Card key={metadataKey.keyCode} sx={{ border: '1px solid #e0e0e0' }}>
              <Accordion defaultExpanded={false}>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, width: '100%' }}>
                    <Box sx={{ flex: 1 }}>
                      <Typography variant="h6" sx={{ mb: 0.5 }}>
                        {metadataKey.displayName}
                      </Typography>
                      <Typography variant="body2" color="textSecondary">
                        Code: <strong>{metadataKey.keyCode}</strong>
                      </Typography>
                    </Box>
                    <Chip
                      label={metadataKey.category}
                      size="small"
                      sx={{
                        backgroundColor: getCategoryColor(metadataKey.category).bg,
                        color: getCategoryColor(metadataKey.category).text,
                        fontWeight: 'bold',
                      }}
                    />
                    <Chip
                      label={metadataKey.dataType}
                      size="small"
                      variant="outlined"
                    />
                    {metadataKey.required && (
                      <Chip label="Required" size="small" color="primary" />
                    )}
                    {metadataKey.billable && (
                      <Chip label="Billable" size="small" color="secondary" />
                    )}
                    {!metadataKey.active && (
                      <Chip label="Inactive" size="small" variant="outlined" />
                    )}
                  </Box>
                </AccordionSummary>

                <AccordionDetails>
                  <Box sx={{ width: '100%' }}>
                    <Typography variant="body2" paragraph>
                      <strong>Description:</strong> {metadataKey.description}
                    </Typography>
                    
                    {metadataKey.exampleValue && (
                      <Typography variant="body2" paragraph>
                        <strong>Example:</strong> {metadataKey.exampleValue}
                      </Typography>
                    )}

                    <Box sx={{ display: 'flex', gap: 1, mt: 2, pt: 2, borderTop: '1px solid #e0e0e0' }}>
                      <Button
                        size="small"
                        startIcon={<EditIcon />}
                        onClick={() => handleEditClick(metadataKey)}
                      >
                        Edit
                      </Button>
                      <Button
                        size="small"
                        startIcon={<DeleteIcon />}
                        color="error"
                        onClick={() => handleDelete(metadataKey.keyCode)}
                      >
                        Delete
                      </Button>
                      <Button
                        size="small"
                        variant={metadataKey.active ? 'outlined' : 'contained'}
                        color={metadataKey.active ? 'inherit' : 'success'}
                        onClick={() => handleToggleActive(metadataKey)}
                      >
                        {metadataKey.active ? 'Deactivate' : 'Activate'}
                      </Button>
                    </Box>
                  </Box>
                </AccordionDetails>
              </Accordion>
            </Card>
          ))
        )}
      </Stack>

      {/* Edit Dialog */}
      <Dialog
        open={editDialogOpen}
        onClose={handleCloseEditDialog}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Edit Metadata Key: {selectedKey?.keyCode}</DialogTitle>
        <DialogContent>
          {selectedKey && (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}>
              <TextField
                label="Display Name"
                fullWidth
                value={selectedKey.displayName}
                onChange={(e) =>
                  setSelectedKey({ ...selectedKey, displayName: e.target.value })
                }
              />
              <TextField
                label="Description"
                fullWidth
                multiline
                rows={3}
                value={selectedKey.description}
                onChange={(e) =>
                  setSelectedKey({ ...selectedKey, description: e.target.value })
                }
              />
              <TextField
                label="Category"
                fullWidth
                value={selectedKey.category}
                onChange={(e) =>
                  setSelectedKey({ ...selectedKey, category: e.target.value })
                }
                select
                SelectProps={{
                  native: true,
                }}
              >
                <option value="GENERATION">Generation</option>
                <option value="RATES">Rates</option>
                <option value="IDENTIFIERS">Identifiers</option>
                <option value="BILLING">Billing</option>
                <option value="PERFORMANCE">Performance</option>
                <option value="CREDITS">Credits</option>
                <option value="CUSTOM">Custom</option>
              </TextField>
              <TextField
                label="Data Type"
                fullWidth
                value={selectedKey.dataType}
                onChange={(e) =>
                  setSelectedKey({ ...selectedKey, dataType: e.target.value })
                }
                select
                SelectProps={{
                  native: true,
                }}
              >
                <option value="STRING">String</option>
                <option value="NUMBER">Number</option>
                <option value="DECIMAL">Decimal</option>
                <option value="PERCENTAGE">Percentage</option>
                <option value="DATE">Date</option>
              </TextField>
              <TextField
                label="Example Value"
                fullWidth
                value={selectedKey.exampleValue || ''}
                onChange={(e) =>
                  setSelectedKey({ ...selectedKey, exampleValue: e.target.value })
                }
              />
              <Box>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={selectedKey.required || false}
                      onChange={(e) =>
                        setSelectedKey({ ...selectedKey, required: e.target.checked })
                      }
                    />
                  }
                  label="Required"
                />
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={selectedKey.billable || false}
                      onChange={(e) =>
                        setSelectedKey({ ...selectedKey, billable: e.target.checked })
                      }
                    />
                  }
                  label="Billable"
                />
              </Box>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseEditDialog}>Cancel</Button>
          <Button variant="contained" onClick={handleSaveEdit}>
            Save Changes
          </Button>
        </DialogActions>
      </Dialog>

      {/* Create Dialog */}
      <Dialog
        open={createDialogOpen}
        onClose={handleCloseCreateDialog}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Create New Metadata Key</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 2 }}>
            <TextField
              label="Key Code"
              fullWidth
              placeholder="e.g., generated_kwh"
              value={newKey.keyCode || ''}
              onChange={(e) =>
                setNewKey({ ...newKey, keyCode: e.target.value.toLowerCase().replace(/\s+/g, '_') })
              }
              helperText="Lowercase with underscores (e.g., generated_kwh)"
            />
            <TextField
              label="Display Name"
              fullWidth
              placeholder="e.g., Generated kWh"
              value={newKey.displayName || ''}
              onChange={(e) =>
                setNewKey({ ...newKey, displayName: e.target.value })
              }
            />
            <TextField
              label="Description"
              fullWidth
              multiline
              rows={3}
              placeholder="Describe what this metadata field represents"
              value={newKey.description || ''}
              onChange={(e) =>
                setNewKey({ ...newKey, description: e.target.value })
              }
            />
            <TextField
              label="Category"
              fullWidth
              value={newKey.category || 'CUSTOM'}
              onChange={(e) =>
                setNewKey({ ...newKey, category: e.target.value })
              }
              select
              SelectProps={{
                native: true,
              }}
            >
              <option value="GENERATION">Generation</option>
              <option value="RATES">Rates</option>
              <option value="IDENTIFIERS">Identifiers</option>
              <option value="BILLING">Billing</option>
              <option value="PERFORMANCE">Performance</option>
              <option value="CREDITS">Credits</option>
              <option value="CUSTOM">Custom</option>
            </TextField>
            <TextField
              label="Data Type"
              fullWidth
              value={newKey.dataType || 'STRING'}
              onChange={(e) =>
                setNewKey({ ...newKey, dataType: e.target.value })
              }
              select
              SelectProps={{
                native: true,
              }}
            >
              <option value="STRING">String</option>
              <option value="NUMBER">Number</option>
              <option value="DECIMAL">Decimal</option>
              <option value="PERCENTAGE">Percentage</option>
              <option value="DATE">Date</option>
            </TextField>
            <TextField
              label="Example Value"
              fullWidth
              placeholder="e.g., 942847"
              value={newKey.exampleValue || ''}
              onChange={(e) =>
                setNewKey({ ...newKey, exampleValue: e.target.value })
              }
            />
            <Box>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={newKey.required || false}
                    onChange={(e) =>
                      setNewKey({ ...newKey, required: e.target.checked })
                    }
                  />
                }
                label="Required"
              />
              <FormControlLabel
                control={
                  <Checkbox
                    checked={newKey.billable || false}
                    onChange={(e) =>
                      setNewKey({ ...newKey, billable: e.target.checked })
                    }
                  />
                }
                label="Billable"
              />
            </Box>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseCreateDialog}>Cancel</Button>
          <Button variant="contained" onClick={handleSaveCreate}>
            Create Key
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default MetadataManagement;
