import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  TextField,
  Alert,
  CircularProgress,
  Stack,
  Divider,
  Avatar,
} from '@mui/material';
import {
  Upload as UploadIcon,
  Delete as DeleteIcon,
  Save as SaveIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import api from '../services/api';
import { useBranding } from '../contexts/BrandingContext';
import { getEffectiveLogoUrl, getEffectiveLoginLogoUrl } from '../services/brandingApi';

const BrandingManagement: React.FC = () => {
  const { branding, refreshBranding } = useBranding();
  
  const [displayName, setDisplayName] = useState('');
  const [tagline, setTagline] = useState('');
  const [primaryColor, setPrimaryColor] = useState('#1976d2');
  const [secondaryColor, setSecondaryColor] = useState('#42a5f5');
  
  const [mainLogoFile, setMainLogoFile] = useState<File | null>(null);
  const [loginLogoFile, setLoginLogoFile] = useState<File | null>(null);
  
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error', text: string } | null>(null);

  // Load current branding on mount
  useEffect(() => {
    if (branding) {
      setDisplayName(branding.displayName || '');
      setTagline(branding.tagline || '');
      setPrimaryColor(branding.primaryColor || '#1976d2');
      setSecondaryColor(branding.secondaryColor || '#42a5f5');
    }
  }, [branding]);

  const handleMainLogoUpload = async () => {
    if (!mainLogoFile) return;

    setLoading(true);
    setMessage(null);

    try {
      const formData = new FormData();
      formData.append('file', mainLogoFile);

      await api.post('/api/admin/branding/logo/main', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });

      setMessage({ type: 'success', text: 'Main logo uploaded successfully!' });
      setMainLogoFile(null);
      await refreshBranding();
    } catch (err: any) {
      setMessage({ type: 'error', text: err.response?.data?.error || 'Failed to upload logo' });
    } finally {
      setLoading(false);
    }
  };

  const handleLoginLogoUpload = async () => {
    if (!loginLogoFile) return;

    setLoading(true);
    setMessage(null);

    try {
      const formData = new FormData();
      formData.append('file', loginLogoFile);

      await api.post('/api/admin/branding/logo/login', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });

      setMessage({ type: 'success', text: 'Login logo uploaded successfully!' });
      setLoginLogoFile(null);
      await refreshBranding();
    } catch (err: any) {
      setMessage({ type: 'error', text: err.response?.data?.error || 'Failed to upload logo' });
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteMainLogo = async () => {
    if (!window.confirm('Are you sure you want to delete the main logo?')) return;

    setLoading(true);
    setMessage(null);

    try {
      await api.delete('/api/admin/branding/logo/main');
      setMessage({ type: 'success', text: 'Main logo deleted successfully!' });
      await refreshBranding();
    } catch (err: any) {
      setMessage({ type: 'error', text: err.response?.data?.error || 'Failed to delete logo' });
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteLoginLogo = async () => {
    if (!window.confirm('Are you sure you want to delete the login logo?')) return;

    setLoading(true);
    setMessage(null);

    try {
      await api.delete('/api/admin/branding/logo/login');
      setMessage({ type: 'success', text: 'Login logo deleted successfully!' });
      await refreshBranding();
    } catch (err: any) {
      setMessage({ type: 'error', text: err.response?.data?.error || 'Failed to delete logo' });
    } finally {
      setLoading(false);
    }
  };

  const handleSaveSettings = async () => {
    setLoading(true);
    setMessage(null);

    try {
      await api.put('/api/admin/branding', {
        displayName,
        tagline,
        primaryColor,
        secondaryColor,
      });

      setMessage({ type: 'success', text: 'Branding settings saved successfully!' });
      await refreshBranding();
      
      // Reload page to apply new theme colors
      setTimeout(() => window.location.reload(), 1000);
    } catch (err: any) {
      setMessage({ type: 'error', text: err.response?.data?.error || 'Failed to save settings' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Branding Management
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Customize your tenant's branding including logos, colors, and display information.
      </Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 3 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <Stack spacing={3}>
        {/* Logo Upload Section */}
        <Box sx={{ display: 'flex', gap: 3, flexDirection: { xs: 'column', md: 'row' } }}>
          {/* Main Logo */}
          <Box sx={{ flex: 1 }}>
            <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Main Logo (Header)
              </Typography>
              <Typography variant="body2" color="text.secondary" paragraph>
                Displayed in the application header and sidebar. Recommended: 400x100px PNG/SVG
              </Typography>

              {branding?.logoUrl && (
                <Box sx={{ mb: 2, textAlign: 'center' }}>
                  <Avatar
                    src={getEffectiveLogoUrl(branding)}
                    alt="Main Logo"
                    variant="square"
                    sx={{ width: 200, height: 'auto', mx: 'auto', mb: 1 }}
                  />
                  <Button
                    size="small"
                    startIcon={<DeleteIcon />}
                    onClick={handleDeleteMainLogo}
                    disabled={loading}
                    color="error"
                  >
                    Delete
                  </Button>
                </Box>
              )}

              <Stack spacing={2}>
                <Button variant="outlined" component="label" fullWidth>
                  Choose File
                  <input
                    type="file"
                    hidden
                    accept="image/png,image/jpeg,image/svg+xml,image/webp"
                    onChange={(e) => setMainLogoFile(e.target.files?.[0] || null)}
                  />
                </Button>
                {mainLogoFile && (
                  <Typography variant="body2" color="text.secondary">
                    Selected: {mainLogoFile.name}
                  </Typography>
                )}
                <Button
                  variant="contained"
                  startIcon={<UploadIcon />}
                  onClick={handleMainLogoUpload}
                  disabled={!mainLogoFile || loading}
                  fullWidth
                >
                  {loading ? <CircularProgress size={24} /> : 'Upload Main Logo'}
                </Button>
              </Stack>
            </CardContent>
          </Card>
          </Box>

          {/* Login Logo */}
          <Box sx={{ flex: 1 }}>
            <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Login Logo
              </Typography>
              <Typography variant="body2" color="text.secondary" paragraph>
                Displayed on the login page. Recommended: 400x400px or 400x200px PNG/SVG
              </Typography>

              {branding?.loginLogoUrl && (
                <Box sx={{ mb: 2, textAlign: 'center' }}>
                  <Avatar
                    src={getEffectiveLoginLogoUrl(branding)}
                    alt="Login Logo"
                    variant="square"
                    sx={{ width: 200, height: 'auto', mx: 'auto', mb: 1 }}
                  />
                  <Button
                    size="small"
                    startIcon={<DeleteIcon />}
                    onClick={handleDeleteLoginLogo}
                    disabled={loading}
                    color="error"
                  >
                    Delete
                  </Button>
                </Box>
              )}

              <Stack spacing={2}>
                <Button variant="outlined" component="label" fullWidth>
                  Choose File
                  <input
                    type="file"
                    hidden
                    accept="image/png,image/jpeg,image/svg+xml,image/webp"
                    onChange={(e) => setLoginLogoFile(e.target.files?.[0] || null)}
                  />
                </Button>
                {loginLogoFile && (
                  <Typography variant="body2" color="text.secondary">
                    Selected: {loginLogoFile.name}
                  </Typography>
                )}
                <Button
                  variant="contained"
                  startIcon={<UploadIcon />}
                  onClick={handleLoginLogoUpload}
                  disabled={!loginLogoFile || loading}
                  fullWidth
                >
                  {loading ? <CircularProgress size={24} /> : 'Upload Login Logo'}
                </Button>
              </Stack>
            </CardContent>
          </Card>
          </Box>
        </Box>

        {/* Branding Settings */}
        <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Branding Settings
              </Typography>
              <Divider sx={{ my: 2 }} />

              <Stack spacing={3}>
                {/* Name and Tagline Row */}
                <Box sx={{ display: 'flex', gap: 3, flexDirection: { xs: 'column', md: 'row' } }}>
                  <Box sx={{ flex: 1 }}>
                    <TextField
                      fullWidth
                      label="Display Name"
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      helperText="The name displayed in the UI (e.g., 'Acme Energy Portal')"
                    />
                  </Box>
                  <Box sx={{ flex: 1 }}>
                    <TextField
                      fullWidth
                      label="Tagline"
                      value={tagline}
                      onChange={(e) => setTagline(e.target.value)}
                      helperText="Shown below the logo (e.g., 'AI-Powered Invoice Management')"
                    />
                  </Box>
                </Box>

                {/* Colors Row */}
                <Box sx={{ display: 'flex', gap: 3, flexDirection: { xs: 'column', md: 'row' } }}>
                  <Box sx={{ flex: 1 }}>
                    <TextField
                      fullWidth
                      label="Primary Color"
                      type="color"
                      value={primaryColor}
                      onChange={(e) => setPrimaryColor(e.target.value)}
                      helperText="Main brand color for buttons, headers, etc."
                      InputLabelProps={{ shrink: true }}
                    />
                    <Box
                      sx={{
                        mt: 1,
                        height: 40,
                        backgroundColor: primaryColor,
                        borderRadius: 1,
                        border: '1px solid #ddd',
                      }}
                    />
                  </Box>
                  <Box sx={{ flex: 1 }}>
                    <TextField
                      fullWidth
                      label="Secondary Color"
                      type="color"
                      value={secondaryColor}
                      onChange={(e) => setSecondaryColor(e.target.value)}
                      helperText="Accent color for secondary elements"
                      InputLabelProps={{ shrink: true }}
                    />
                    <Box
                      sx={{
                        mt: 1,
                        height: 40,
                        backgroundColor: secondaryColor,
                        borderRadius: 1,
                        border: '1px solid #ddd',
                      }}
                    />
                  </Box>
                </Box>

                {/* Buttons */}
                <Stack direction="row" spacing={2}>
                  <Button
                    variant="contained"
                    startIcon={<SaveIcon />}
                    onClick={handleSaveSettings}
                    disabled={loading}
                  >
                    Save Settings
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<RefreshIcon />}
                    onClick={() => refreshBranding()}
                    disabled={loading}
                  >
                    Reset to Current
                  </Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
      </Stack>
    </Box>
  );
};

export default BrandingManagement;

