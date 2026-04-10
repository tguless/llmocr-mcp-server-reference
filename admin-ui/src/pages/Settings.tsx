import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  Alert,
  Stack,
  Divider,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  IconButton,
  Tooltip,
  CircularProgress,
  Paper,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import SaveIcon from '@mui/icons-material/Save';
import DeleteIcon from '@mui/icons-material/Delete';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import LogoutIcon from '@mui/icons-material/Logout';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import StorageIcon from '@mui/icons-material/Storage';
import TestTubeIcon from '@mui/icons-material/Science';
import LockIcon from '@mui/icons-material/Lock';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import LinkIcon from '@mui/icons-material/Link';
import VpnKeyIcon from '@mui/icons-material/VpnKey';
import api from '../services/api';

const Settings: React.FC = () => {
  const navigate = useNavigate();
  const [token, setToken] = useState('');
  const [saved, setSaved] = useState(false);
  const [hasToken, setHasToken] = useState(false);
  const [user, setUser] = useState<any>(null);
  
  // S3 Configuration state
  const [s3Configs, setS3Configs] = useState<any[]>([]);
  const [s3Loading, setS3Loading] = useState(false);
  const [s3DialogOpen, setS3DialogOpen] = useState(false);
  const [editingS3Config, setEditingS3Config] = useState<any>(null);
  const [s3FormData, setS3FormData] = useState({
    bucketName: '',
    endpoint: '',
    accessKeyId: '',
    secretAccessKey: '',
    region: '',
    description: '',
  });
  const [s3Error, setS3Error] = useState('');
  const [s3TestLoading, setS3TestLoading] = useState<number | null>(null);

  // Change Password state
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);
  const [passwordFormData, setPasswordFormData] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [passwordError, setPasswordError] = useState('');
  const [passwordSuccess, setPasswordSuccess] = useState(false);
  const [passwordLoading, setPasswordLoading] = useState(false);

  // Tenant Validation Settings state
  const [servicePeriodRequired, setServicePeriodRequired] = useState(true);
  const [validationLoading, setValidationLoading] = useState(false);
  const [validationSaved, setValidationSaved] = useState(false);
  const [validationError, setValidationError] = useState('');

  // LLMOCR Integration state
  const [llmOcrApiKey, setLlmOcrApiKey] = useState('');
  const [llmOcrApiKeyMasked, setLlmOcrApiKeyMasked] = useState('');
  const [hasLlmOcrApiKey, setHasLlmOcrApiKey] = useState(false);
  const [llmOcrLoading, setLlmOcrLoading] = useState(false);
  const [llmOcrSaved, setLlmOcrSaved] = useState(false);
  const [llmOcrError, setLlmOcrError] = useState('');
  const [showLlmOcrApiKey, setShowLlmOcrApiKey] = useState(false);

  useEffect(() => {
    const storedToken = localStorage.getItem('authToken');
    const storedUser = localStorage.getItem('user');
    
    if (storedToken) {
      setToken(storedToken);
      setHasToken(true);
    }
    
    if (storedUser) {
      try {
        setUser(JSON.parse(storedUser));
      } catch (e) {
        console.error('Error parsing user data:', e);
      }
    }
    
    // Load S3 configurations
    loadS3Configs();
    
    // Load tenant validation settings
    loadValidationSettings();
    
    // Load LLMOCR integration settings
    loadLlmOcrSettings();
  }, []);

  const loadS3Configs = async () => {
    setS3Loading(true);
    try {
      const response = await api.get('/api/admin/s3-configurations');
      setS3Configs(response.data.data || []);
    } catch (err: any) {
      console.error('Failed to load S3 configurations:', err);
      setS3Error('Failed to load S3 configurations');
    } finally {
      setS3Loading(false);
    }
  };

  const loadValidationSettings = async () => {
    try {
      const response = await api.get('/api/admin/tenant/validation-settings');
      if (response.data.success) {
        setServicePeriodRequired(response.data.data.servicePeriodRequired ?? true);
      }
    } catch (err: any) {
      console.error('Failed to load validation settings:', err);
    }
  };

  const loadLlmOcrSettings = async () => {
    try {
      const response = await api.get('/api/admin/tenant-settings');
      if (response.data.success) {
        const data = response.data.data;
        setHasLlmOcrApiKey(data.hasLlmOcrApiKey || false);
        setLlmOcrApiKeyMasked(data.llmOcrApiKeyMasked || '');
      }
    } catch (err: any) {
      console.error('Failed to load LLMOCR settings:', err);
    }
  };

  const saveLlmOcrApiKey = async () => {
    if (!llmOcrApiKey.trim()) {
      setLlmOcrError('API key is required');
      return;
    }

    if (!llmOcrApiKey.startsWith('llmocr_svc_')) {
      setLlmOcrError('Invalid API key format. Expected: llmocr_svc_...');
      return;
    }

    setLlmOcrLoading(true);
    setLlmOcrError('');
    setLlmOcrSaved(false);

    try {
      const response = await api.put('/api/admin/tenant-settings/llmocr-api-key', {
        apiKey: llmOcrApiKey
      });

      if (response.data.success) {
        setHasLlmOcrApiKey(true);
        setLlmOcrApiKeyMasked(response.data.data.llmOcrApiKeyMasked);
        setLlmOcrApiKey(''); // Clear the input
        setLlmOcrSaved(true);
        setTimeout(() => setLlmOcrSaved(false), 3000);
      }
    } catch (err: any) {
      setLlmOcrError(err.response?.data?.error || 'Failed to save API key');
    } finally {
      setLlmOcrLoading(false);
    }
  };

  const removeLlmOcrApiKey = async () => {
    if (!window.confirm('Are you sure you want to remove the LLMOCR API key?')) {
      return;
    }

    setLlmOcrLoading(true);
    setLlmOcrError('');

    try {
      await api.delete('/api/admin/tenant-settings/llmocr-api-key');
      setHasLlmOcrApiKey(false);
      setLlmOcrApiKeyMasked('');
      setLlmOcrSaved(true);
      setTimeout(() => setLlmOcrSaved(false), 3000);
    } catch (err: any) {
      setLlmOcrError(err.response?.data?.error || 'Failed to remove API key');
    } finally {
      setLlmOcrLoading(false);
    }
  };

  const saveValidationSettings = async () => {
    setValidationLoading(true);
    setValidationError('');
    setValidationSaved(false);
    try {
      const response = await api.put('/api/admin/tenant/validation-settings', {
        servicePeriodRequired
      });
      if (response.data.success) {
        setValidationSaved(true);
        setTimeout(() => setValidationSaved(false), 3000);
      }
    } catch (err: any) {
      console.error('Failed to save validation settings:', err);
      setValidationError(err.response?.data?.message || 'Failed to save validation settings');
    } finally {
      setValidationLoading(false);
    }
  };

  const handleOpenS3Dialog = async (config?: any) => {
    if (config) {
      setEditingS3Config(config);
      setS3FormData({
        bucketName: config.bucketName,
        endpoint: config.endpoint,
        accessKeyId: config.accessKeyId,
        secretAccessKey: config.secretAccessKey,
        region: config.region || '',
        description: config.description || '',
      });
    } else {
      // Fetch default S3 configuration values for new configurations
      let defaultEndpoint = '';
      let defaultAccessKey = '';
      let defaultSecretKey = '';
      try {
        const response = await api.get('/api/admin/s3-configurations/default-endpoint');
        defaultEndpoint = response.data.defaultEndpoint || '';
        defaultAccessKey = response.data.defaultAccessKey || '';
        defaultSecretKey = response.data.defaultSecretKey || '';
      } catch (err) {
        console.error('Failed to fetch default S3 configuration:', err);
        // Fallback to empty strings if fetch fails
      }
      
      setEditingS3Config(null);
      setS3FormData({
        bucketName: '',
        endpoint: defaultEndpoint,
        accessKeyId: defaultAccessKey,
        secretAccessKey: defaultSecretKey,
        region: '',
        description: '',
      });
    }
    setS3Error('');
    setS3DialogOpen(true);
  };

  const handleCloseS3Dialog = () => {
    setS3DialogOpen(false);
    setEditingS3Config(null);
  };

  const handleSaveS3Config = async () => {
    if (!s3FormData.bucketName || !s3FormData.endpoint || !s3FormData.accessKeyId || !s3FormData.secretAccessKey) {
      setS3Error('Bucket name, endpoint, access key, and secret key are required');
      return;
    }

    try {
      if (editingS3Config) {
        await api.put(`/api/admin/s3-configurations/${editingS3Config.id}`, s3FormData);
      } else {
        await api.post('/api/admin/s3-configurations', s3FormData);
      }
      handleCloseS3Dialog();
      await loadS3Configs();
    } catch (err: any) {
      setS3Error(err.response?.data?.error || 'Failed to save S3 configuration');
    }
  };

  const handleTestS3Connection = async (configId: number) => {
    setS3TestLoading(configId);
    try {
      await api.post(`/api/admin/s3-configurations/${configId}/test-connection`);
      alert('Connection successful!');
    } catch (err: any) {
      alert(`Connection failed: ${err.response?.data?.error || 'Unknown error'}`);
    } finally {
      setS3TestLoading(null);
    }
  };

  const handleDeleteS3Config = async (configId: number) => {
    if (window.confirm('Are you sure you want to delete this S3 configuration?')) {
      try {
        await api.delete(`/api/admin/s3-configurations/${configId}`);
        await loadS3Configs();
      } catch (err: any) {
        setS3Error(err.response?.data?.error || 'Failed to delete S3 configuration');
      }
    }
  };

  const handleSave = () => {
    if (token.trim()) {
      localStorage.setItem('authToken', token.trim());
      setHasToken(true);
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    }
  };

  const handleClear = () => {
    localStorage.removeItem('authToken');
    setToken('');
    setHasToken(false);
  };

  const handleLogout = () => {
    // Clear all auth data
    localStorage.removeItem('authToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    // Redirect to login
    navigate('/login');
  };

  const handleOpenPasswordDialog = () => {
    setPasswordFormData({
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    });
    setPasswordError('');
    setPasswordSuccess(false);
    setPasswordDialogOpen(true);
  };

  const handleClosePasswordDialog = () => {
    setPasswordDialogOpen(false);
    setPasswordFormData({
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    });
    setPasswordError('');
    setPasswordSuccess(false);
  };

  const handleChangePassword = async () => {
    setPasswordError('');
    setPasswordSuccess(false);

    // Validation
    if (!passwordFormData.currentPassword || !passwordFormData.newPassword || !passwordFormData.confirmPassword) {
      setPasswordError('All fields are required');
      return;
    }

    if (passwordFormData.newPassword.length < 8) {
      setPasswordError('New password must be at least 8 characters long');
      return;
    }

    if (passwordFormData.newPassword !== passwordFormData.confirmPassword) {
      setPasswordError('New password and confirmation do not match');
      return;
    }

    if (passwordFormData.currentPassword === passwordFormData.newPassword) {
      setPasswordError('New password must be different from current password');
      return;
    }

    setPasswordLoading(true);

    try {
      await api.post('/api/auth/change-password', passwordFormData);
      setPasswordSuccess(true);
      setTimeout(() => {
        handleClosePasswordDialog();
      }, 2000);
    } catch (err: any) {
      setPasswordError(err.response?.data?.error || 'Failed to change password');
    } finally {
      setPasswordLoading(false);
    }
  };

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1">
          Settings
        </Typography>
        <Button
          variant="outlined"
          color="error"
          startIcon={<LogoutIcon />}
          onClick={handleLogout}
        >
          Logout
        </Button>
      </Box>

      {/* User Info Card */}
      {user && (
        <Card sx={{ maxWidth: 800, mb: 3 }}>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Account Information
            </Typography>
            <Divider sx={{ my: 2 }} />
            <Stack spacing={1.5}>
              <Box display="flex" alignItems="center">
                <Typography variant="body2" color="text.secondary" sx={{ width: 120 }}>
                  Username:
                </Typography>
                <Typography variant="body1" fontWeight="medium">
                  {user.username}
                </Typography>
              </Box>
              <Box display="flex" alignItems="center">
                <Typography variant="body2" color="text.secondary" sx={{ width: 120 }}>
                  Email:
                </Typography>
                <Typography variant="body1">{user.email}</Typography>
              </Box>
              <Box display="flex" alignItems="center">
                <Typography variant="body2" color="text.secondary" sx={{ width: 120 }}>
                  Full Name:
                </Typography>
                <Typography variant="body1">{user.fullName || 'Not set'}</Typography>
              </Box>
              <Box display="flex" alignItems="center">
                <Typography variant="body2" color="text.secondary" sx={{ width: 120 }}>
                  Tenant ID:
                </Typography>
                <Chip label={user.tenantId} color="primary" size="small" />
              </Box>
              <Box display="flex" alignItems="center">
                <Typography variant="body2" color="text.secondary" sx={{ width: 120 }}>
                  Role:
                </Typography>
                <Chip label={user.role} color="secondary" size="small" />
              </Box>
            </Stack>
          </CardContent>
        </Card>
      )}

      {/* Change Password Card */}
      <Card sx={{ maxWidth: 800, mb: 3 }}>
        <CardContent>
          <Box display="flex" justifyContent="space-between" alignItems="center">
            <Box>
              <Typography variant="h6" gutterBottom>
                Security
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Change your password to keep your account secure
              </Typography>
            </Box>
            <Button
              variant="outlined"
              startIcon={<LockIcon />}
              onClick={handleOpenPasswordDialog}
            >
              Change Password
            </Button>
          </Box>
        </CardContent>
      </Card>

      <Card sx={{ maxWidth: 800 }}>
        <CardContent>
          <Stack spacing={3}>
            <Box>
              <Typography variant="h6" gutterBottom>
                Authentication Token
              </Typography>
              <Typography variant="body2" color="text.secondary" paragraph>
                Configure your JWT authentication token to access the MCP Invoice Server API.
              </Typography>
            </Box>

            <Divider />

            {saved && (
              <Alert severity="success" icon={<CheckCircleIcon />}>
                Token saved successfully! API calls will now use this token.
              </Alert>
            )}

            {hasToken && (
              <Alert severity="info">
                <Stack direction="row" spacing={1} alignItems="center">
                  <Typography variant="body2">
                    Token is currently configured
                  </Typography>
                  <Chip label="Active" color="success" size="small" />
                </Stack>
              </Alert>
            )}

            {!hasToken && (
              <Alert severity="warning">
                No authentication token configured. API calls will fail with 401 errors.
              </Alert>
            )}

            <TextField
              fullWidth
              label="JWT Token"
              placeholder="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
              value={token}
              onChange={(e) => setToken(e.target.value)}
              multiline
              rows={4}
              helperText="Paste your JWT Bearer token here"
            />

            <Stack direction="row" spacing={2}>
              <Button
                variant="contained"
                startIcon={<SaveIcon />}
                onClick={handleSave}
                disabled={!token.trim()}
              >
                Save Token
              </Button>
              <Button
                variant="outlined"
                color="error"
                startIcon={<DeleteIcon />}
                onClick={handleClear}
                disabled={!hasToken}
              >
                Clear Token
              </Button>
            </Stack>

            <Divider />

            <Box>
              <Typography variant="subtitle2" color="primary" gutterBottom>
                How to Get a Token
              </Typography>
              <Typography variant="body2" color="text.secondary" component="div">
                <ol style={{ margin: 0, paddingLeft: 20 }}>
                  <li>Authenticate with the main LLM-OCR application</li>
                  <li>Copy your JWT token from the browser's localStorage or developer tools</li>
                  <li>Paste it into the field above and click "Save Token"</li>
                </ol>
              </Typography>
            </Box>

            <Box>
              <Typography variant="subtitle2" color="primary" gutterBottom>
                For Development/Testing
              </Typography>
              <Typography variant="body2" color="text.secondary">
                If you're running locally and don't need authentication, you can configure the
                backend to disable JWT validation for development. Check the MCP Invoice Server
                security configuration.
              </Typography>
            </Box>

            <Alert severity="info">
              <Typography variant="body2">
                <strong>API Endpoint:</strong> {process.env.REACT_APP_API_URL || 'http://localhost:8081/mcp-invoice'}
              </Typography>
            </Alert>
          </Stack>
        </CardContent>
      </Card>

      {/* Tenant Validation Settings Card */}
      <Card sx={{ maxWidth: 1200, mt: 3 }}>
        <CardContent>
          <Stack spacing={3}>
            <Box display="flex" alignItems="center" gap={1}>
              <CheckCircleIcon />
              <Typography variant="h6">
                Tenant Validation Settings
              </Typography>
            </Box>

            <Typography variant="body2" color="text.secondary">
              Configure invoice validation requirements for your tenant. These settings determine
              what fields must be present for an invoice to pass validation.
            </Typography>

            {validationError && (
              <Alert severity="error" onClose={() => setValidationError('')}>
                {validationError}
              </Alert>
            )}

            {validationSaved && (
              <Alert severity="success">
                Validation settings saved successfully!
              </Alert>
            )}

            <Box>
              <Typography variant="subtitle2" gutterBottom fontWeight={600}>
                Required Fields
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Select which fields must be present for an invoice to be considered complete.
              </Typography>

              <Stack spacing={2}>
                <Box display="flex" alignItems="center" gap={2}>
                  <input
                    type="checkbox"
                    id="servicePeriodRequired"
                    checked={servicePeriodRequired}
                    onChange={(e) => setServicePeriodRequired(e.target.checked)}
                    style={{ width: 20, height: 20, cursor: 'pointer' }}
                  />
                  <Box>
                    <Typography variant="body1" component="label" htmlFor="servicePeriodRequired" sx={{ cursor: 'pointer', fontWeight: 500 }}>
                      Service Period Required
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Require service period start and end dates on all invoices
                    </Typography>
                  </Box>
                </Box>

                <Alert severity="info">
                  <Typography variant="body2">
                    <strong>Note:</strong> Required metadata keys and categories are configured separately in their
                    respective management pages. The validation tool will check for all required fields configured
                    across the system.
                  </Typography>
                </Alert>
              </Stack>
            </Box>

            <Box>
              <Button
                variant="contained"
                startIcon={<SaveIcon />}
                onClick={saveValidationSettings}
                disabled={validationLoading}
              >
                {validationLoading ? 'Saving...' : 'Save Validation Settings'}
              </Button>
            </Box>
          </Stack>
        </CardContent>
      </Card>

      {/* LLMOCR Integration Card */}
      <Card sx={{ maxWidth: 1200, mt: 3 }}>
        <CardContent>
          <Stack spacing={3}>
            <Box display="flex" alignItems="center" gap={1}>
              <LinkIcon color="primary" />
              <Typography variant="h6">
                LLM-OCR Integration
              </Typography>
            </Box>

            <Typography variant="body2" color="text.secondary">
              Configure your LLM-OCR API key to enable integration with the main LLM-OCR application.
              This allows this MCP server to access your S3 bucket configurations and upload files securely.
            </Typography>

            {llmOcrError && (
              <Alert severity="error" onClose={() => setLlmOcrError('')}>
                {llmOcrError}
              </Alert>
            )}

            {llmOcrSaved && (
              <Alert severity="success" icon={<CheckCircleIcon />}>
                Settings saved successfully!
              </Alert>
            )}

            {hasLlmOcrApiKey && (
              <Alert severity="info">
                <Stack direction="row" spacing={1} alignItems="center">
                  <Typography variant="body2">
                    API Key configured: <strong>{llmOcrApiKeyMasked}</strong>
                  </Typography>
                  <Chip label="Active" color="success" size="small" />
                </Stack>
              </Alert>
            )}

            <Box>
              <Typography variant="subtitle2" gutterBottom fontWeight={600}>
                <VpnKeyIcon sx={{ fontSize: 16, mr: 0.5, verticalAlign: 'text-bottom' }} />
                LLM-OCR Service API Key
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Enter your service integration API key from LLM-OCR. You can find this in your LLM-OCR
                profile under "API Keys" section.
              </Typography>
              
              <Stack direction="row" spacing={2} alignItems="flex-start">
                <TextField
                  fullWidth
                  label={hasLlmOcrApiKey ? "Update API Key" : "API Key"}
                  value={llmOcrApiKey}
                  onChange={(e) => setLlmOcrApiKey(e.target.value)}
                  placeholder="llmocr_svc_..."
                  type={showLlmOcrApiKey ? 'text' : 'password'}
                  InputProps={{
                    endAdornment: (
                      <IconButton
                        onClick={() => setShowLlmOcrApiKey(!showLlmOcrApiKey)}
                        edge="end"
                      >
                        {showLlmOcrApiKey ? <VisibilityOffIcon /> : <VisibilityIcon />}
                      </IconButton>
                    ),
                  }}
                  helperText="Service integration keys start with 'llmocr_svc_'"
                  size="small"
                  sx={{ maxWidth: 500 }}
                />
                <Button
                  variant="contained"
                  onClick={saveLlmOcrApiKey}
                  disabled={!llmOcrApiKey.trim() || llmOcrLoading}
                  startIcon={llmOcrLoading ? <CircularProgress size={16} /> : <SaveIcon />}
                >
                  Save
                </Button>
                {hasLlmOcrApiKey && (
                  <Button
                    variant="outlined"
                    color="error"
                    onClick={removeLlmOcrApiKey}
                    disabled={llmOcrLoading}
                    startIcon={<DeleteIcon />}
                  >
                    Remove
                  </Button>
                )}
              </Stack>
            </Box>

            <Alert severity="info">
              <Typography variant="body2">
                <strong>How to get your API key:</strong>
              </Typography>
              <ol style={{ margin: '8px 0 0 0', paddingLeft: 20 }}>
                <li>Log in to your LLM-OCR account at eyesense.ai</li>
                <li>Go to Profile → API Keys</li>
                <li>Create a new "Service Integration" key</li>
                <li>Copy the key and paste it above (the key is only shown once)</li>
              </ol>
            </Alert>
          </Stack>
        </CardContent>
      </Card>

      {/* S3 Configuration Card */}
      <Card sx={{ maxWidth: 1200, mt: 3 }}>
        <CardContent>
          <Stack spacing={3}>
            <Box display="flex" justifyContent="space-between" alignItems="center">
              <Box display="flex" alignItems="center" gap={1}>
                <StorageIcon />
                <Typography variant="h6">
                  S3/MinIO Bucket Configuration
                </Typography>
              </Box>
              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={() => handleOpenS3Dialog()}
                size="small"
              >
                Add Bucket
              </Button>
            </Box>

            <Typography variant="body2" color="text.secondary">
              Configure S3 or MinIO bucket credentials to enable PDF storage and retrieval for invoices.
            </Typography>

            {s3Error && (
              <Alert severity="error" onClose={() => setS3Error('')}>
                {s3Error}
              </Alert>
            )}

            {s3Loading ? (
              <Box display="flex" justifyContent="center" py={3}>
                <CircularProgress />
              </Box>
            ) : s3Configs.length === 0 ? (
              <Paper sx={{ p: 3, textAlign: 'center', bgcolor: 'action.hover' }}>
                <StorageIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 1 }} />
                <Typography color="text.secondary">
                  No S3 bucket configurations yet. Add one to enable PDF storage.
                </Typography>
              </Paper>
            ) : (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ bgcolor: 'action.hover' }}>
                      <TableCell><strong>Bucket Name</strong></TableCell>
                      <TableCell><strong>Endpoint</strong></TableCell>
                      <TableCell><strong>Region</strong></TableCell>
                      <TableCell><strong>Description</strong></TableCell>
                      <TableCell align="right"><strong>Actions</strong></TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {s3Configs.map((config: any) => (
                      <TableRow key={config.id}>
                        <TableCell>{config.bucketName}</TableCell>
                        <TableCell sx={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {config.endpoint}
                        </TableCell>
                        <TableCell>{config.region || '-'}</TableCell>
                        <TableCell sx={{ maxWidth: 150, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {config.description || '-'}
                        </TableCell>
                        <TableCell align="right">
                          <Tooltip title="Test Connection">
                            <IconButton
                              size="small"
                              onClick={() => handleTestS3Connection(config.id)}
                              disabled={s3TestLoading === config.id}
                            >
                              {s3TestLoading === config.id ? (
                                <CircularProgress size={20} />
                              ) : (
                                <TestTubeIcon fontSize="small" />
                              )}
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Edit">
                            <IconButton
                              size="small"
                              onClick={() => handleOpenS3Dialog(config)}
                            >
                              <EditIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Delete">
                            <IconButton
                              size="small"
                              color="error"
                              onClick={() => handleDeleteS3Config(config.id)}
                            >
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Stack>
        </CardContent>
      </Card>

      {/* S3 Configuration Dialog */}
      <Dialog open={s3DialogOpen} onClose={handleCloseS3Dialog} maxWidth="sm" fullWidth>
        <DialogTitle>
          {editingS3Config ? 'Edit S3 Bucket Configuration' : 'Add S3 Bucket Configuration'}
        </DialogTitle>
        <DialogContent sx={{ pt: 2 }}>
          <Stack spacing={2}>
            {s3Error && (
              <Alert severity="error">{s3Error}</Alert>
            )}
            <TextField
              fullWidth
              label="Bucket Name"
              value={s3FormData.bucketName}
              onChange={(e) => setS3FormData({ ...s3FormData, bucketName: e.target.value })}
              placeholder="my-invoices"
              disabled={!!editingS3Config}
              helperText="S3 bucket name (cannot be changed after creation)"
            />
            <TextField
              fullWidth
              label="Endpoint URL"
              value={s3FormData.endpoint}
              onChange={(e) => setS3FormData({ ...s3FormData, endpoint: e.target.value })}
              placeholder="https://s3.amazonaws.com or http://localhost:9000"
              helperText="S3 or MinIO endpoint URL"
            />
            <TextField
              fullWidth
              label="Access Key ID"
              value={s3FormData.accessKeyId}
              onChange={(e) => setS3FormData({ ...s3FormData, accessKeyId: e.target.value })}
              placeholder="AKIA..."
              type="password"
            />
            <TextField
              fullWidth
              label="Secret Access Key"
              value={s3FormData.secretAccessKey}
              onChange={(e) => setS3FormData({ ...s3FormData, secretAccessKey: e.target.value })}
              placeholder="wJalrXUtnFEMI..."
              type="password"
            />
            <TextField
              fullWidth
              label="Region (optional)"
              value={s3FormData.region}
              onChange={(e) => setS3FormData({ ...s3FormData, region: e.target.value })}
              placeholder="us-east-1"
            />
            <TextField
              fullWidth
              label="Description (optional)"
              value={s3FormData.description}
              onChange={(e) => setS3FormData({ ...s3FormData, description: e.target.value })}
              placeholder="Production invoice storage"
              multiline
              rows={2}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseS3Dialog}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleSaveS3Config}
            startIcon={<SaveIcon />}
          >
            Save
          </Button>
        </DialogActions>
      </Dialog>

      {/* Change Password Dialog */}
      <Dialog open={passwordDialogOpen} onClose={handleClosePasswordDialog} maxWidth="sm" fullWidth>
        <DialogTitle>
          <Box display="flex" alignItems="center" gap={1}>
            <LockIcon />
            Change Password
          </Box>
        </DialogTitle>
        <DialogContent sx={{ pt: 2 }}>
          <Stack spacing={2.5}>
            {passwordSuccess && (
              <Alert severity="success" icon={<CheckCircleIcon />}>
                Password changed successfully!
              </Alert>
            )}

            {passwordError && (
              <Alert severity="error">{passwordError}</Alert>
            )}

            <TextField
              fullWidth
              label="Current Password"
              type={showCurrentPassword ? 'text' : 'password'}
              value={passwordFormData.currentPassword}
              onChange={(e) => setPasswordFormData({ ...passwordFormData, currentPassword: e.target.value })}
              disabled={passwordLoading || passwordSuccess}
              InputProps={{
                endAdornment: (
                  <IconButton
                    onClick={() => setShowCurrentPassword(!showCurrentPassword)}
                    edge="end"
                  >
                    {showCurrentPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                  </IconButton>
                ),
              }}
            />

            <TextField
              fullWidth
              label="New Password"
              type={showNewPassword ? 'text' : 'password'}
              value={passwordFormData.newPassword}
              onChange={(e) => setPasswordFormData({ ...passwordFormData, newPassword: e.target.value })}
              disabled={passwordLoading || passwordSuccess}
              helperText="Must be at least 8 characters long"
              InputProps={{
                endAdornment: (
                  <IconButton
                    onClick={() => setShowNewPassword(!showNewPassword)}
                    edge="end"
                  >
                    {showNewPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                  </IconButton>
                ),
              }}
            />

            <TextField
              fullWidth
              label="Confirm New Password"
              type={showConfirmPassword ? 'text' : 'password'}
              value={passwordFormData.confirmPassword}
              onChange={(e) => setPasswordFormData({ ...passwordFormData, confirmPassword: e.target.value })}
              disabled={passwordLoading || passwordSuccess}
              InputProps={{
                endAdornment: (
                  <IconButton
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    edge="end"
                  >
                    {showConfirmPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                  </IconButton>
                ),
              }}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClosePasswordDialog} disabled={passwordLoading}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleChangePassword}
            disabled={passwordLoading || passwordSuccess}
            startIcon={passwordLoading ? <CircularProgress size={20} /> : <LockIcon />}
          >
            {passwordLoading ? 'Changing...' : 'Change Password'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default Settings;

