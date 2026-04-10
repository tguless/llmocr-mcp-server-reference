import React, { useState, useCallback, useEffect } from 'react';
import axios from 'axios';
import {
  Box,
  Paper,
  Typography,
  Button,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  TextField,
  Alert,
  LinearProgress,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  IconButton,
  Chip,
  CircularProgress,
} from '@mui/material';
import {
  CloudUpload,
  InsertDriveFile,
  Delete,
  CheckCircle,
  Error as ErrorIcon,
  Folder,
} from '@mui/icons-material';

// Use the same API base URL as other services
const API_BASE_URL = process.env.REACT_APP_API_URL || 
  (process.env.NODE_ENV === 'production' 
    ? '/invoice'  // Production: just context root, endpoints already have /api
    : 'http://localhost:8081/mcp-invoice');  // Dev: direct to backend

// Create axios instance with auth
const api = axios.create({
  baseURL: API_BASE_URL,
});

// Add auth token interceptor
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('authToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

interface S3Bucket {
  id?: number;
  name: string;
  endpoint?: string;
  description?: string;
  prefix?: string;
  source: 'local' | 'llmocr';
  isActive?: boolean;
}

interface UploadedFile {
  file: File;
  status: 'pending' | 'uploading' | 'success' | 'error';
  progress?: number;
  error?: string;
  s3Key?: string;
}

const toDocumentRef = (filename: string) =>
  (filename || '')
    .toLowerCase()
    .replace(/\.[^.]+$/, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');

const S3Upload: React.FC = () => {
  const [buckets, setBuckets] = useState<S3Bucket[]>([]);
  const [bucketsLoading, setBucketsLoading] = useState(true);
  const [selectedBucket, setSelectedBucket] = useState<S3Bucket | null>(null);
  const [prefix, setPrefix] = useState<string>('');
  const [files, setFiles] = useState<UploadedFile[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [sourceExternalId, setSourceExternalId] = useState<string>('');
  const [sourceHeadersJson, setSourceHeadersJson] = useState<string>('');

  // Fetch available buckets on mount
  useEffect(() => {
    const fetchBuckets = async () => {
      try {
        const response = await api.get('/api/s3-upload/buckets');
        if (response.data.success && response.data.data) {
          // Backend returns { all: [...], local: [...] }
          // Combine both local and LLMOCR buckets
          const allBuckets: S3Bucket[] = response.data.data.all || [];
          setBuckets(allBuckets);
          // Auto-select first bucket if available
          if (allBuckets.length > 0) {
            setSelectedBucket(allBuckets[0]);
            // Pre-fill prefix for LLMOCR buckets
            if (allBuckets[0].source === 'llmocr' && allBuckets[0].prefix) {
              setPrefix(allBuckets[0].prefix);
            }
          }
        }
      } catch (error: any) {
        console.error('Failed to fetch buckets:', error);
        setUploadError('Failed to load available buckets');
      } finally {
        setBucketsLoading(false);
      }
    };

    fetchBuckets();
  }, []);

  const handleFileSelect = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = Array.from(event.target.files || []);
    const newFiles: UploadedFile[] = selectedFiles.map(file => ({
      file,
      status: 'pending',
    }));
    setFiles(prev => [...prev, ...newFiles]);
    setUploadError(null);
  }, []);

  const handleRemoveFile = (index: number) => {
    setFiles(prev => prev.filter((_, i) => i !== index));
  };

  const handleUpload = async () => {
    if (!selectedBucket) {
      setUploadError('Please select a bucket');
      return;
    }
    if (files.length === 0) {
      setUploadError('Please select files to upload');
      return;
    }

    setUploading(true);
    setUploadError(null);

    // Mark all files as uploading
    setFiles(prevFiles =>
      prevFiles.map(f => ({ ...f, status: 'uploading' as const }))
    );

    try {
      if (selectedBucket.source === 'llmocr') {
        const perFileResults: Array<{ success: boolean; s3Key?: string; error?: string }> = [];
        const errors: string[] = [];

        for (const { file } of files) {
          const formData = new FormData();
          formData.append('bucketName', selectedBucket.name);
          formData.append('files', file);
          if (prefix) {
            formData.append('prefix', prefix);
          }

          const externalId = sourceExternalId.trim() || toDocumentRef(file.name);
          if (externalId) {
            formData.append('sourceExternalId', externalId);
          }

          let headersJson = sourceHeadersJson.trim();
          if (!headersJson) {
            headersJson = JSON.stringify({
              invoiceRef: externalId || file.name,
              documentType: 'invoice',
              sourceFilename: file.name,
            });
          }
          formData.append('sourceHeadersJson', headersJson);

          try {
            const response = await api.post('/api/s3-upload/upload-to-llmocr-bucket', formData, {
              headers: { 'Content-Type': 'multipart/form-data' },
            });
            const fileResult = response?.data?.data?.files?.[0];
            const success = fileResult?.success !== false;
            if (!success) {
              const err = fileResult?.error || 'Upload failed';
              errors.push(`${file.name}: ${err}`);
            }
            perFileResults.push({
              success,
              s3Key: fileResult?.s3Key,
              error: fileResult?.error,
            });
          } catch (fileError: any) {
            const err = fileError?.response?.data?.error || fileError?.response?.data?.message || 'Upload failed';
            errors.push(`${file.name}: ${err}`);
            perFileResults.push({ success: false, error: err });
          }
        }

        setFiles(prevFiles =>
          prevFiles.map((f, index) => ({
            ...f,
            status: perFileResults[index]?.success ? 'success' : 'error',
            error: perFileResults[index]?.error,
            s3Key: perFileResults[index]?.s3Key,
          }))
        );

        if (errors.length > 0) {
          setUploadError(`Some files failed: ${errors.join(', ')}`);
        }
      } else {
        const formData = new FormData();
        formData.append('bucketConfigId', selectedBucket.id!.toString());
        if (prefix) {
          formData.append('prefix', prefix);
        }
        files.forEach(({ file }) => {
          formData.append('files', file);
        });

        const response = await api.post('/api/s3-upload/upload', formData, {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        });

        const uploadResults = response.data.data?.files || [];
        setFiles(prevFiles =>
          prevFiles.map((f, index) => ({
            ...f,
            status: uploadResults[index]?.success !== false ? 'success' : 'error',
            error: uploadResults[index]?.error,
            s3Key: uploadResults[index]?.s3Key,
          }))
        );

        if (response.data.data?.errors && response.data.data.errors.length > 0) {
          setUploadError(`Some files failed: ${response.data.data.errors.join(', ')}`);
        }
      }
    } catch (error: any) {
      console.error('Upload failed:', error);
      setUploadError(error.response?.data?.error || error.response?.data?.message || 'Upload failed');
      setFiles(prevFiles =>
        prevFiles.map(f => ({
          ...f,
          status: 'error',
          error: 'Upload failed',
        }))
      );
    } finally {
      setUploading(false);
    }
  };

  const handleClear = () => {
    setFiles([]);
    setUploadError(null);
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
  };

  // selectedBucket is already in state

  return (
    <Box sx={{ maxWidth: 1200, mx: 'auto' }}>
      <Typography variant="h4" gutterBottom sx={{ mb: 1, fontWeight: 600 }}>
        S3 File Upload
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Upload files directly to your configured S3 buckets
      </Typography>

      <Box sx={{ display: 'flex', gap: 3, flexDirection: { xs: 'column', md: 'row' } }}>
        {/* Upload Configuration */}
        <Box sx={{ flex: { xs: '1 1 100%', md: '0 0 33%' } }}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom sx={{ fontWeight: 600 }}>
              Upload Settings
            </Typography>

            {bucketsLoading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', my: 3 }}>
                <CircularProgress />
              </Box>
            ) : buckets.length === 0 ? (
              <Alert severity="warning" sx={{ mb: 2 }}>
                No S3 buckets configured. Please configure an S3 bucket in Settings.
              </Alert>
            ) : (
              <>
                <FormControl fullWidth sx={{ mb: 2 }}>
                  <InputLabel>Select Bucket</InputLabel>
                  <Select
                    value={selectedBucket?.name || ''}
                    onChange={(e) => {
                      const bucket = buckets.find(b => b.name === e.target.value);
                      setSelectedBucket(bucket || null);
                      // Pre-fill prefix for LLMOCR buckets
                      if (bucket?.source === 'llmocr' && bucket.prefix) {
                        setPrefix(bucket.prefix);
                      }
                    }}
                    label="Select Bucket"
                    disabled={uploading}
                  >
                    {buckets.map((bucket) => (
                      <MenuItem key={bucket.name} value={bucket.name}>
                        <Box sx={{ display: 'flex', alignItems: 'center', width: '100%', justifyContent: 'space-between' }}>
                          <Box sx={{ display: 'flex', alignItems: 'center' }}>
                            <Folder sx={{ mr: 1 }} />
                            {bucket.name}
                            {bucket.prefix && (
                              <Typography variant="caption" sx={{ ml: 1, color: 'text.secondary' }}>
                                ({bucket.prefix})
                              </Typography>
                            )}
                          </Box>
                          <Chip 
                            label={bucket.source === 'llmocr' ? 'LLMOCR' : 'Local'} 
                            size="small" 
                            color={bucket.source === 'llmocr' ? 'primary' : 'default'}
                            sx={{ ml: 1 }}
                          />
                        </Box>
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>

                {selectedBucket && selectedBucket.description && (
                  <Alert severity="info" sx={{ mb: 2 }}>
                    {selectedBucket.description}
                  </Alert>
                )}

                {selectedBucket?.source === 'llmocr' && (
                  <>
                    <TextField
                      fullWidth
                      label="Source External ID (optional)"
                      value={sourceExternalId}
                      onChange={(e) => setSourceExternalId(e.target.value)}
                      helperText="Example: invoice-id. If blank, each file auto-uses a filename-derived ID."
                      disabled={uploading}
                      sx={{ mb: 2 }}
                    />
                    <TextField
                      fullWidth
                      label="Source Headers JSON (optional)"
                      value={sourceHeadersJson}
                      onChange={(e) => setSourceHeadersJson(e.target.value)}
                      placeholder='{"candidateId":"cand-123","jobId":"job-456"}'
                      helperText="Name/value pairs forwarded to PaperIQ; if blank, defaults are auto-generated per file."
                      disabled={uploading}
                      multiline
                      minRows={3}
                      sx={{ mb: 2 }}
                    />
                  </>
                )}

                {selectedBucket && (
                  <Box sx={{ mb: 2, p: 1.5, bgcolor: 'grey.100', borderRadius: 1 }}>
                    <Typography variant="caption" color="text.secondary" display="block">
                      {selectedBucket.source === 'llmocr' ? 'Upload via LLMOCR' : 'Endpoint'}
                    </Typography>
                    <Typography variant="body2" sx={{ wordBreak: 'break-all' }}>
                      {selectedBucket.source === 'llmocr' 
                        ? `Files will be uploaded to: ${selectedBucket.name}/${prefix || selectedBucket.prefix || ''}`
                        : selectedBucket.endpoint}
                    </Typography>
                  </Box>
                )}

                {/* Only show prefix field for local buckets - LLMOCR prefix is shown in dropdown */}
                {selectedBucket?.source !== 'llmocr' && (
                  <TextField
                    fullWidth
                    label="Prefix (Optional)"
                    value={prefix}
                    onChange={(e) => setPrefix(e.target.value)}
                    helperText="Organize files in folders, e.g., 'invoices/2024'"
                    disabled={uploading}
                    sx={{ mb: 2 }}
                  />
                )}

                <Button
                  variant="outlined"
                  component="label"
                  fullWidth
                  startIcon={<CloudUpload />}
                  disabled={uploading}
                  sx={{ mb: 2 }}
                >
                  Select Files
                  <input
                    type="file"
                    hidden
                    multiple
                    onChange={handleFileSelect}
                  />
                </Button>

                <Box sx={{ display: 'flex', gap: 1 }}>
                  <Button
                    variant="contained"
                    fullWidth
                    onClick={handleUpload}
                    disabled={files.length === 0 || uploading || !selectedBucket}
                    startIcon={<CloudUpload />}
                  >
                    {uploading ? 'Uploading...' : 'Upload'}
                  </Button>
                  <Button
                    variant="outlined"
                    onClick={handleClear}
                    disabled={files.length === 0 || uploading}
                  >
                    Clear
                  </Button>
                </Box>

                {uploading && (
                  <Box sx={{ mt: 2 }}>
                    <LinearProgress />
                    <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                      Uploading {files.length} file(s)...
                    </Typography>
                  </Box>
                )}

                {uploadError && (
                  <Alert severity="error" sx={{ mt: 2 }}>
                    {uploadError}
                  </Alert>
                )}
              </>
            )}
          </Paper>
        </Box>

        {/* File List */}
        <Box sx={{ flex: { xs: '1 1 100%', md: '1 1 66%' } }}>
          <Paper sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom sx={{ fontWeight: 600 }}>
              Files ({files.length})
            </Typography>

            {files.length === 0 ? (
              <Alert severity="info">
                No files selected. Click "Select Files" to choose files for upload.
              </Alert>
            ) : (
              <List>
                {files.map((uploadedFile, index) => (
                  <ListItem
                    key={index}
                    secondaryAction={
                      uploadedFile.status === 'pending' && (
                        <IconButton
                          edge="end"
                          onClick={() => handleRemoveFile(index)}
                          disabled={uploading}
                        >
                          <Delete />
                        </IconButton>
                      )
                    }
                    sx={{
                      border: 1,
                      borderColor: 'divider',
                      borderRadius: 1,
                      mb: 1,
                    }}
                  >
                    <ListItemIcon>
                      {uploadedFile.status === 'success' && <CheckCircle color="success" />}
                      {uploadedFile.status === 'error' && <ErrorIcon color="error" />}
                      {(uploadedFile.status === 'pending' || uploadedFile.status === 'uploading') && (
                        <InsertDriveFile />
                      )}
                    </ListItemIcon>
                    <ListItemText
                      primary={uploadedFile.file.name}
                      secondary={
                        <>
                          {formatFileSize(uploadedFile.file.size)}
                          {uploadedFile.s3Key && ` • S3 Key: ${uploadedFile.s3Key}`}
                          {uploadedFile.error && (
                            <Typography component="span" color="error" sx={{ display: 'block' }}>
                              Error: {uploadedFile.error}
                            </Typography>
                          )}
                          {uploadedFile.status === 'uploading' && (
                            <LinearProgress sx={{ mt: 1 }} />
                          )}
                        </>
                      }
                    />
                  </ListItem>
                ))}
              </List>
            )}
          </Paper>
        </Box>
      </Box>
    </Box>
  );
};

export default S3Upload;

