import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  CircularProgress,
  Alert,
  Chip,
  Stack,
  Tooltip,
} from '@mui/material';
import {
  Close as CloseIcon,
  Save as SaveIcon,
  CheckCircle as ValidateIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
} from '@mui/icons-material';
import Editor from '@monaco-editor/react';
import { getPdfPresignedUrl, getS3PdfStreamUrl, rawJsonProcessingAPI } from '../services/api';

interface RawJsonCorrectionDialogProps {
  open: boolean;
  onClose: () => void;
  record: any;
  onSaved?: () => void;
}

const RawJsonCorrectionDialog: React.FC<RawJsonCorrectionDialogProps> = ({
  open,
  onClose,
  record,
  onSaved,
}) => {
  const [editedJson, setEditedJson] = useState('');
  const [prettySchema, setPrettySchema] = useState('');
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [saveLoading, setSaveLoading] = useState(false);
  const [validateLoading, setValidateLoading] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);
  
  // PDF viewer state
  const [pdfUrl, setPdfUrl] = useState<string | undefined>(undefined);
  const [pdfLoading, setPdfLoading] = useState(false);
  const [pdfPaneExpanded, setPdfPaneExpanded] = useState(true);
  
  // Schema viewer state
  const [schemaViewerExpanded, setSchemaViewerExpanded] = useState(false);

  useEffect(() => {
    if (record && open) {
      // Format the JSON for display
      // Use processedOutput (corrected JSON) if available, otherwise use rawJsonInput
      const jsonToUse = record.processedOutput || record.rawJsonInput;
      console.log('Loading JSON from:', record.processedOutput ? 'processedOutput' : 'rawJsonInput');
      
      try {
        const parsed = typeof jsonToUse === 'string'
          ? JSON.parse(jsonToUse)
          : jsonToUse;
        setEditedJson(JSON.stringify(parsed, null, 2));
      } catch (e) {
        setEditedJson(jsonToUse || '');
      }

      // Format the schema for display
      if (record.pairedSchemaUsed) {
        try {
          const schemaParsed = typeof record.pairedSchemaUsed === 'string'
            ? JSON.parse(record.pairedSchemaUsed)
            : record.pairedSchemaUsed;
          setPrettySchema(JSON.stringify(schemaParsed, null, 2));
        } catch (e) {
          setPrettySchema(record.pairedSchemaUsed || '');
        }
      } else {
        setPrettySchema('');
      }

      // Reset states
      setValidationErrors([]);
      setSaveError(null);
      setSaveSuccess(false);

      // Build PDF URL from S3 information or job ID
      if (record.s3Bucket && record.s3ObjectKey) {
        // Use S3 stream endpoint for PDFs from S3 (tenant validated server-side)
        const pdfStreamUrl = getS3PdfStreamUrl(record.s3Bucket, record.s3ObjectKey);
        setPdfUrl(pdfStreamUrl);
        setPdfLoading(false);
      } else if (record.jobId) {
        // Fallback: try to fetch by job ID (if it's an invoice)
        fetchPdfUrl(parseInt(record.jobId));
      } else {
        setPdfUrl(undefined);
        setPdfLoading(false);
      }
    }
  }, [record, open]);

  const fetchPdfUrl = async (invoiceId: number) => {
    setPdfLoading(true);
    try {
      const response = await getPdfPresignedUrl(invoiceId);
      setPdfUrl(response.url);
    } catch (err: any) {
      console.error('Failed to fetch PDF URL:', err);
      // PDF might not be available, that's okay
      setPdfUrl(undefined);
    } finally {
      setPdfLoading(false);
    }
  };

  const handleMonacoChange = (value: string | undefined) => {
    if (value !== undefined) {
      setEditedJson(value);
      // Clear validation errors when user edits
      setValidationErrors([]);
      setSaveError(null);
    }
  };

  const handleValidate = async () => {
    setValidateLoading(true);
    setValidationErrors([]);
    setSaveError(null);
    
    try {
      const result = await rawJsonProcessingAPI.validateRawJson(record.id, editedJson);
      
      if (result.valid) {
        setValidationErrors([]);
        setSaveError(null);
      } else {
        setValidationErrors(result.errors || ['Validation failed']);
        setSaveError('JSON does not match the required schema');
      }
    } catch (err: any) {
      const errorMessage = err.response?.data?.error || err.message || 'Validation failed';
      setSaveError(errorMessage);
      setValidationErrors([errorMessage]);
    } finally {
      setValidateLoading(false);
    }
  };

  const handleSave = async () => {
    setSaveLoading(true);
    setSaveError(null);
    setSaveSuccess(false);
    
    try {
      await rawJsonProcessingAPI.correctRawJson(record.id, editedJson);
      setSaveSuccess(true);
      
      // Notify parent to refresh
      if (onSaved) {
        onSaved();
      }
      
      // Close dialog after a brief delay
      setTimeout(() => {
        onClose();
      }, 1500);
    } catch (err: any) {
      const errorMessage = err.response?.data?.error || err.message || 'Failed to save corrections';
      setSaveError(errorMessage);
      
      // If validation errors are returned, show them
      if (err.response?.data?.validationErrors) {
        setValidationErrors(err.response.data.validationErrors);
      }
    } finally {
      setSaveLoading(false);
    }
  };

  if (!record) return null;

  return (
    <Dialog
      open={open}
      onClose={onClose}
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
            <Typography variant="h6">
              Correct JSON - {record.sourceFilename || 'N/A'}
            </Typography>
            {record.pairedSchemaName && (
              <Chip
                label={record.pairedSchemaName}
                size="small"
                variant="outlined"
                color="primary"
              />
            )}
          </Stack>
          <Stack direction="row" spacing={1}>
            {record.pairedSchemaUsed && (
              <Tooltip title={schemaViewerExpanded ? "Hide Schema" : "View Schema"}>
                <Button
                  onClick={() => setSchemaViewerExpanded(!schemaViewerExpanded)}
                  size="small"
                  variant="outlined"
                  color={schemaViewerExpanded ? "primary" : "inherit"}
                  endIcon={schemaViewerExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                >
                  Schema
                </Button>
              </Tooltip>
            )}
            {pdfUrl && (
              <Tooltip title={pdfPaneExpanded ? "Hide PDF" : "Show PDF"}>
                <Button
                  onClick={() => setPdfPaneExpanded(!pdfPaneExpanded)}
                  size="small"
                  variant="outlined"
                  color={pdfPaneExpanded ? "primary" : "inherit"}
                  endIcon={pdfPaneExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                >
                  PDF
                </Button>
              </Tooltip>
            )}
          </Stack>
        </Stack>
      </DialogTitle>

      <DialogContent dividers sx={{ display: 'flex', gap: 2, p: 2, height: 'calc(100vh - 250px)' }}>
        <Box display="flex" gap={2} width="100%" height="100%">
          {/* Left side: JSON Editor */}
          <Box sx={{ 
            flex: pdfPaneExpanded && pdfUrl ? '0 0 50%' : 1, 
            display: 'flex', 
            flexDirection: 'column', 
            gap: 2,
            minWidth: 0 // Allow flex shrinking
          }}>
            {/* Record Info */}
            <Box>
              <Typography variant="subtitle2" gutterBottom>
                Record Information
              </Typography>
              <Stack direction="row" spacing={2} flexWrap="wrap">
                <Chip label={`Job ID: ${record.jobId || 'N/A'}`} size="small" variant="outlined" />
                <Chip label={`Tool: ${record.toolName}`} size="small" variant="outlined" />
                <Chip
                  label={record.processingStatus}
                  size="small"
                  color={record.processingStatus === 'SUCCESS' ? 'success' : 'error'}
                />
              </Stack>
            </Box>

            {/* Validation Errors */}
            {validationErrors.length > 0 && (
              <Alert severity="error">
                <Typography variant="subtitle2" gutterBottom>
                  Validation Errors:
                </Typography>
                {validationErrors.map((error: any, idx) => (
                  <Typography key={idx} variant="body2" component="div" sx={{ fontFamily: 'monospace', fontSize: '0.875rem' }}>
                    • {typeof error === 'string' ? error : `${error.path || error.type || 'Error'}: ${error.message || JSON.stringify(error)}`}
                  </Typography>
                ))}
              </Alert>
            )}

            {/* Success Message */}
            {saveSuccess && (
              <Alert severity="success">
                Corrections saved successfully! Dialog will close shortly...
              </Alert>
            )}

            {/* Error Message */}
            {saveError && !validationErrors.length && (
              <Alert severity="error">
                {saveError}
              </Alert>
            )}

            {/* JSON Editor */}
            <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
              <Typography variant="subtitle2" gutterBottom>
                JSON Data (Monaco Editor)
              </Typography>
              <Box sx={{ flex: 1, border: '1px solid #e0e0e0', borderRadius: '4px', overflow: 'hidden' }}>
                <Editor
                  height="100%"
                  defaultLanguage="json"
                  value={editedJson}
                  onChange={handleMonacoChange}
                  theme="vs-light"
                  options={{
                    minimap: { enabled: false },
                    scrollBeyondLastLine: false,
                    fontSize: 13,
                    lineNumbers: 'on',
                    formatOnPaste: true,
                    formatOnType: true,
                    tabSize: 2,
                    wordWrap: 'on',
                    automaticLayout: true,
                  }}
                />
              </Box>
            </Box>
          </Box>

          {/* Middle: Schema Viewer (if expanded) */}
          {schemaViewerExpanded && prettySchema && (
            <Box sx={{ flex: '0 0 30%', display: 'flex', flexDirection: 'column', minWidth: 0 }}>
              <Typography variant="subtitle2" gutterBottom>
                JSON Schema (Read-Only)
              </Typography>
              <Box sx={{ flex: 1, border: '1px solid #e0e0e0', borderRadius: '4px', overflow: 'hidden' }}>
                <Editor
                  height="100%"
                  defaultLanguage="json"
                  value={prettySchema}
                  theme="vs-light"
                  options={{
                    readOnly: true,
                    minimap: { enabled: false },
                    scrollBeyondLastLine: false,
                    fontSize: 12,
                    lineNumbers: 'on',
                    folding: true,
                    automaticLayout: true,
                    wordWrap: 'on',
                  }}
                />
              </Box>
            </Box>
          )}

          {/* Right side: PDF Viewer */}
          {pdfPaneExpanded && pdfUrl && (
            <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
              <Typography variant="subtitle2" gutterBottom>
                Original PDF
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
                    title="PDF Viewer"
                  />
                </Box>
              )}
            </Box>
          )}
        </Box>
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2, gap: 1 }}>
        <Button
          onClick={onClose}
          variant="outlined"
          startIcon={<CloseIcon />}
          disabled={saveLoading}
        >
          Close
        </Button>
        <Box sx={{ flex: 1 }} />
        <Button
          onClick={handleValidate}
          variant="outlined"
          startIcon={<ValidateIcon />}
          disabled={saveLoading || validateLoading}
        >
          {validateLoading ? 'Validating...' : 'Validate'}
        </Button>
        <Button
          onClick={handleSave}
          variant="contained"
          startIcon={<SaveIcon />}
          disabled={saveLoading || validateLoading || saveSuccess || validationErrors.length > 0}
          color={validationErrors.length > 0 ? 'error' : 'primary'}
        >
          {saveLoading ? 'Saving...' : validationErrors.length > 0 ? 'Fix Validation Errors First' : 'Save Corrections'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default RawJsonCorrectionDialog;



