import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  CircularProgress,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  FormControlLabel,
  Checkbox,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Chip,
  List,
  ListItem,
  ListItemText,
  Divider,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import PreviewIcon from '@mui/icons-material/Preview';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import api from '../services/api';

interface TenantInfo {
  tenantId: string;
  categoryCount: number;
  metadataKeyCount: number;
}

interface PreviewData {
  sourceTenantId: string;
  targetTenantId: string;
  categories: {
    copyable: Array<{ categoryCode: string; categoryName: string; description: string }>;
    conflicts: Array<{ categoryCode: string; categoryName: string; description: string }>;
  };
  metadataKeys: {
    copyable: Array<{ keyCode: string; displayName: string; description: string }>;
    conflicts: Array<{ keyCode: string; displayName: string; description: string }>;
  };
}

interface HydrationResult {
  sourceTenantId: string;
  targetTenantId: string;
  categories: {
    copied: string[];
    skipped: string[];
    copiedCount: number;
    skippedCount: number;
  };
  metadataKeys: {
    copied: string[];
    skipped: string[];
    copiedCount: number;
    skippedCount: number;
  };
}

const TenantHydration: React.FC = () => {
  const [tenants, setTenants] = useState<TenantInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedSourceTenant, setSelectedSourceTenant] = useState<string>('');
  const [selectedTargetTenant, setSelectedTargetTenant] = useState<string>('');
  const [copyCategories, setCopyCategories] = useState(true);
  const [copyMetadataKeys, setCopyMetadataKeys] = useState(true);
  const [previewDialogOpen, setPreviewDialogOpen] = useState(false);
  const [previewData, setPreviewData] = useState<PreviewData | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [hydrating, setHydrating] = useState(false);
  const [resultDialogOpen, setResultDialogOpen] = useState(false);
  const [hydrationResult, setHydrationResult] = useState<HydrationResult | null>(null);

  useEffect(() => {
    loadTenants();
  }, []);

  const loadTenants = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await api.get('/api/admin/tenant-hydration/available-tenants');
      setTenants(response.data.tenants);
    } catch (err: any) {
      const errorMsg = err.response?.data?.error || err.message || 'Failed to load tenants';
      setError(errorMsg);
      console.error('Error loading tenants:', err);
    } finally {
      setLoading(false);
    }
  };

  const handlePreview = async () => {
    if (!selectedSourceTenant) {
      setError('Please select a source tenant');
      return;
    }

    if (!selectedTargetTenant) {
      setError('Please select a target tenant');
      return;
    }

    if (selectedSourceTenant === selectedTargetTenant) {
      setError('Source and target tenant cannot be the same');
      return;
    }

    try {
      setPreviewLoading(true);
      setError(null);
      const response = await api.get('/api/admin/tenant-hydration/preview', {
        params: { 
          sourceTenantId: selectedSourceTenant,
          targetTenantId: selectedTargetTenant
        }
      });
      setPreviewData(response.data.preview);
      setPreviewDialogOpen(true);
    } catch (err: any) {
      setError(err.response?.data?.error || err.message || 'Failed to preview hydration');
      console.error('Error previewing hydration:', err);
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleHydrate = async () => {
    if (!selectedSourceTenant) {
      setError('Please select a source tenant');
      return;
    }

    if (!selectedTargetTenant) {
      setError('Please select a target tenant');
      return;
    }

    if (selectedSourceTenant === selectedTargetTenant) {
      setError('Source and target tenant cannot be the same');
      return;
    }

    if (!copyCategories && !copyMetadataKeys) {
      setError('Please select at least one option to copy');
      return;
    }

    try {
      setHydrating(true);
      setError(null);
      setPreviewDialogOpen(false);
      
      const response = await api.post('/api/admin/tenant-hydration/hydrate', {
        sourceTenantId: selectedSourceTenant,
        targetTenantId: selectedTargetTenant,
        copyCategories,
        copyMetadataKeys
      });
      
      setHydrationResult(response.data.result);
      setResultDialogOpen(true);
      
      // Reset selections
      setSelectedSourceTenant('');
      setSelectedTargetTenant('');
      setCopyCategories(true);
      setCopyMetadataKeys(true);
      
    } catch (err: any) {
      setError(err.response?.data?.error || err.message || 'Failed to hydrate tenant');
      console.error('Error hydrating tenant:', err);
    } finally {
      setHydrating(false);
    }
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
      {/* Header */}
      <Box mb={3}>
        <Typography variant="h4" gutterBottom>
          <CloudUploadIcon sx={{ mr: 1, verticalAlign: 'middle' }} />
          Tenant Hydration (GLOBAL ADMIN ONLY)
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Copy categories and metadata key definitions from one tenant to another. This feature is only available to GLOBAL_ADMIN users.
        </Typography>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3}} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Configuration Card */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Configure Hydration
          </Typography>
          
          <Box display="flex" flexDirection="column" gap={3} mt={2}>
            {/* Source Tenant Selection */}
            <FormControl fullWidth>
              <InputLabel>Source Tenant (copy from)</InputLabel>
              <Select
                value={selectedSourceTenant}
                onChange={(e) => setSelectedSourceTenant(e.target.value)}
                label="Source Tenant (copy from)"
              >
                {tenants.map((tenant) => (
                  <MenuItem 
                    key={tenant.tenantId} 
                    value={tenant.tenantId}
                    disabled={tenant.tenantId === selectedTargetTenant}
                  >
                    {tenant.tenantId} ({tenant.categoryCount} categories, {tenant.metadataKeyCount} metadata keys)
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            {/* Target Tenant Selection */}
            <FormControl fullWidth>
              <InputLabel>Target Tenant (copy to)</InputLabel>
              <Select
                value={selectedTargetTenant}
                onChange={(e) => setSelectedTargetTenant(e.target.value)}
                label="Target Tenant (copy to)"
              >
                {tenants.map((tenant) => (
                  <MenuItem 
                    key={tenant.tenantId} 
                    value={tenant.tenantId}
                    disabled={tenant.tenantId === selectedSourceTenant}
                  >
                    {tenant.tenantId} ({tenant.categoryCount} categories, {tenant.metadataKeyCount} metadata keys)
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            {/* Options */}
            <Box>
              <Typography variant="subtitle2" gutterBottom>
                What to Copy:
              </Typography>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={copyCategories}
                    onChange={(e) => setCopyCategories(e.target.checked)}
                  />
                }
                label="Copy Categories"
              />
              <FormControlLabel
                control={
                  <Checkbox
                    checked={copyMetadataKeys}
                    onChange={(e) => setCopyMetadataKeys(e.target.checked)}
                  />
                }
                label="Copy Metadata Key Definitions"
              />
            </Box>

            {/* Action Buttons */}
            <Box display="flex" gap={2}>
              <Button
                variant="outlined"
                startIcon={<PreviewIcon />}
                onClick={handlePreview}
                disabled={!selectedSourceTenant || !selectedTargetTenant || previewLoading || selectedSourceTenant === selectedTargetTenant}
              >
                {previewLoading ? 'Loading...' : 'Preview'}
              </Button>
              <Button
                variant="contained"
                startIcon={<ContentCopyIcon />}
                onClick={handleHydrate}
                disabled={!selectedSourceTenant || !selectedTargetTenant || hydrating || (!copyCategories && !copyMetadataKeys) || selectedSourceTenant === selectedTargetTenant}
              >
                {hydrating ? 'Hydrating...' : 'Hydrate Tenant'}
              </Button>
            </Box>
          </Box>
        </CardContent>
      </Card>

      {/* Available Tenants Info */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Available Source Tenants
          </Typography>
          <List>
            {tenants.map((tenant) => (
              <React.Fragment key={tenant.tenantId}>
                <ListItem>
                  <ListItemText
                    primary={
                      <Typography variant="body1">
                        {tenant.tenantId}
                      </Typography>
                    }
                    secondary={
                      <Typography variant="body2" color="text.secondary">
                        {tenant.categoryCount} categories, {tenant.metadataKeyCount} metadata keys
                      </Typography>
                    }
                  />
                </ListItem>
                <Divider />
              </React.Fragment>
            ))}
          </List>
        </CardContent>
      </Card>

      {/* Preview Dialog */}
      <Dialog
        open={previewDialogOpen}
        onClose={() => setPreviewDialogOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          Hydration Preview
        </DialogTitle>
        <DialogContent>
          {previewData && (
            <Box>
              <Alert severity="info" sx={{ mb: 2 }}>
                Previewing hydration from <strong>{previewData.sourceTenantId}</strong> to <strong>{previewData.targetTenantId}</strong>
              </Alert>

              {/* Categories */}
              <Accordion defaultExpanded>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Typography variant="h6">
                    Categories ({previewData.categories.copyable.length} copyable, {previewData.categories.conflicts.length} conflicts)
                  </Typography>
                </AccordionSummary>
                <AccordionDetails>
                  <Box>
                    {previewData.categories.copyable.length > 0 && (
                      <>
                        <Typography variant="subtitle2" color="success.main" gutterBottom>
                          ✓ Will be copied ({previewData.categories.copyable.length}):
                        </Typography>
                        <List dense>
                          {previewData.categories.copyable.map((cat) => (
                            <ListItem key={cat.categoryCode}>
                              <ListItemText
                                primary={`${cat.categoryCode} - ${cat.categoryName}`}
                                secondary={cat.description}
                              />
                            </ListItem>
                          ))}
                        </List>
                      </>
                    )}
                    {previewData.categories.conflicts.length > 0 && (
                      <>
                        <Typography variant="subtitle2" color="warning.main" gutterBottom sx={{ mt: 2 }}>
                          ⚠ Will be skipped (already exists) ({previewData.categories.conflicts.length}):
                        </Typography>
                        <List dense>
                          {previewData.categories.conflicts.map((cat) => (
                            <ListItem key={cat.categoryCode}>
                              <ListItemText
                                primary={`${cat.categoryCode} - ${cat.categoryName}`}
                                secondary={cat.description}
                              />
                            </ListItem>
                          ))}
                        </List>
                      </>
                    )}
                  </Box>
                </AccordionDetails>
              </Accordion>

              {/* Metadata Keys */}
              <Accordion defaultExpanded>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Typography variant="h6">
                    Metadata Keys ({previewData.metadataKeys.copyable.length} copyable, {previewData.metadataKeys.conflicts.length} conflicts)
                  </Typography>
                </AccordionSummary>
                <AccordionDetails>
                  <Box>
                    {previewData.metadataKeys.copyable.length > 0 && (
                      <>
                        <Typography variant="subtitle2" color="success.main" gutterBottom>
                          ✓ Will be copied ({previewData.metadataKeys.copyable.length}):
                        </Typography>
                        <List dense>
                          {previewData.metadataKeys.copyable.map((key) => (
                            <ListItem key={key.keyCode}>
                              <ListItemText
                                primary={`${key.keyCode} - ${key.displayName}`}
                                secondary={key.description}
                              />
                            </ListItem>
                          ))}
                        </List>
                      </>
                    )}
                    {previewData.metadataKeys.conflicts.length > 0 && (
                      <>
                        <Typography variant="subtitle2" color="warning.main" gutterBottom sx={{ mt: 2 }}>
                          ⚠ Will be skipped (already exists) ({previewData.metadataKeys.conflicts.length}):
                        </Typography>
                        <List dense>
                          {previewData.metadataKeys.conflicts.map((key) => (
                            <ListItem key={key.keyCode}>
                              <ListItemText
                                primary={`${key.keyCode} - ${key.displayName}`}
                                secondary={key.description}
                              />
                            </ListItem>
                          ))}
                        </List>
                      </>
                    )}
                  </Box>
                </AccordionDetails>
              </Accordion>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPreviewDialogOpen(false)}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleHydrate}
            disabled={hydrating}
            startIcon={<ContentCopyIcon />}
          >
            {hydrating ? 'Hydrating...' : 'Proceed with Hydration'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Result Dialog */}
      <Dialog
        open={resultDialogOpen}
        onClose={() => setResultDialogOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          Hydration Complete
        </DialogTitle>
        <DialogContent>
          {hydrationResult && (
            <Box>
              <Alert severity="success" sx={{ mb: 2 }}>
                Successfully hydrated tenant <strong>{hydrationResult.targetTenantId}</strong> from <strong>{hydrationResult.sourceTenantId}</strong>
              </Alert>

              {/* Categories Result */}
              <Accordion defaultExpanded>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Typography variant="h6">
                    Categories ({hydrationResult.categories.copiedCount} copied, {hydrationResult.categories.skippedCount} skipped)
                  </Typography>
                </AccordionSummary>
                <AccordionDetails>
                  <Box>
                    {hydrationResult.categories.copied.length > 0 && (
                      <>
                        <Typography variant="subtitle2" color="success.main" gutterBottom>
                          ✓ Copied:
                        </Typography>
                        <List dense>
                          {hydrationResult.categories.copied.map((code) => (
                            <ListItem key={code}>
                              <Chip label={code} size="small" color="success" variant="outlined" />
                            </ListItem>
                          ))}
                        </List>
                      </>
                    )}
                    {hydrationResult.categories.skipped.length > 0 && (
                      <>
                        <Typography variant="subtitle2" color="warning.main" gutterBottom sx={{ mt: 2 }}>
                          ⚠ Skipped:
                        </Typography>
                        <List dense>
                          {hydrationResult.categories.skipped.map((code) => (
                            <ListItem key={code}>
                              <Chip label={code} size="small" color="warning" variant="outlined" />
                            </ListItem>
                          ))}
                        </List>
                      </>
                    )}
                  </Box>
                </AccordionDetails>
              </Accordion>

              {/* Metadata Keys Result */}
              <Accordion defaultExpanded>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Typography variant="h6">
                    Metadata Keys ({hydrationResult.metadataKeys.copiedCount} copied, {hydrationResult.metadataKeys.skippedCount} skipped)
                  </Typography>
                </AccordionSummary>
                <AccordionDetails>
                  <Box>
                    {hydrationResult.metadataKeys.copied.length > 0 && (
                      <>
                        <Typography variant="subtitle2" color="success.main" gutterBottom>
                          ✓ Copied:
                        </Typography>
                        <List dense>
                          {hydrationResult.metadataKeys.copied.map((code) => (
                            <ListItem key={code}>
                              <Chip label={code} size="small" color="success" variant="outlined" />
                            </ListItem>
                          ))}
                        </List>
                      </>
                    )}
                    {hydrationResult.metadataKeys.skipped.length > 0 && (
                      <>
                        <Typography variant="subtitle2" color="warning.main" gutterBottom sx={{ mt: 2 }}>
                          ⚠ Skipped:
                        </Typography>
                        <List dense>
                          {hydrationResult.metadataKeys.skipped.map((code) => (
                            <ListItem key={code}>
                              <Chip label={code} size="small" color="warning" variant="outlined" />
                            </ListItem>
                          ))}
                        </List>
                      </>
                    )}
                  </Box>
                </AccordionDetails>
              </Accordion>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button variant="contained" onClick={() => setResultDialogOpen(false)}>
            Close
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default TenantHydration;

