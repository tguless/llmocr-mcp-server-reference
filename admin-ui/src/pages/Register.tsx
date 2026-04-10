import React, { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  TextField,
  Button,
  Typography,
  Alert,
  Link,
  CircularProgress,
  Stack,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';

const Register: React.FC = () => {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    firstName: '',
    lastName: '',
  });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [needsBootstrap, setNeedsBootstrap] = useState<boolean | null>(null);
  const [bootstrapLoading, setBootstrapLoading] = useState(true);
  const [localRegistrationEnabled, setLocalRegistrationEnabled] = useState(false);
  const [policyLoading, setPolicyLoading] = useState(true);
  const defaultPaperIqBaseUrl =
    window.location.hostname === 'localhost' ? 'http://localhost:3000/llmocr' : 'https://paperiq.ai/llmocr';
  const launchEndpoint =
    process.env.REACT_APP_PAPERIQ_SSO_LAUNCH_URL || `${defaultPaperIqBaseUrl}/api/mcp/admin-sso/launch`;
  const loginEndpoint =
    process.env.REACT_APP_PAPERIQ_LOGIN_URL || `${defaultPaperIqBaseUrl}/login`;

  const startPaperIqLogin = () => {
    const redirectUri = `${window.location.origin}${window.location.pathname.replace('/register', '/login')}`;
    const state = window.crypto?.randomUUID?.() || `${Date.now()}`;
    const launchUrl = new URL(launchEndpoint);
    launchUrl.searchParams.set('redirectUri', redirectUri);
    launchUrl.searchParams.set('state', state);

    const loginUrl = new URL(loginEndpoint);
    loginUrl.searchParams.set('next', launchUrl.toString());
    window.location.href = loginUrl.toString();
  };

  // Check bootstrap status + auth policy on component mount
  React.useEffect(() => {
    const loadPagePolicy = async () => {
      try {
        const [bootstrapResponse, policyResponse] = await Promise.all([
          api.get('/api/auth/bootstrap-status'),
          api.get('/api/auth/policy'),
        ]);
        setNeedsBootstrap(bootstrapResponse.data.needsBootstrap);
        setLocalRegistrationEnabled(Boolean(policyResponse.data.localRegistrationEnabled));
      } catch (err) {
        console.error('Failed to load auth policy/bootstrap status:', err);
        setNeedsBootstrap(null);
        setLocalRegistrationEnabled(false);
      } finally {
        setBootstrapLoading(false);
        setPolicyLoading(false);
      }
    };
    
    loadPagePolicy();
  }, []);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value,
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    // Validate passwords match
    if (formData.password !== formData.confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    // Validate password strength
    if (formData.password.length < 8) {
      setError('Password must be at least 8 characters long');
      return;
    }

    setLoading(true);

    try {
      console.log('🔍 Attempting registration...');
      const response = await api.post('/api/auth/register', {
        username: formData.username,
        email: formData.email,
        password: formData.password,
        firstName: formData.firstName,
        lastName: formData.lastName,
      });

      const { accessToken, refreshToken, user } = response.data;

      // Store tokens and user info
      localStorage.setItem('authToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);
      localStorage.setItem('user', JSON.stringify(user));

      // Redirect to main app
      navigate('/categories');
    } catch (err: any) {
      setError(err.response?.data?.error || 'Registration failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#f5f5f5',
        backgroundImage: 'linear-gradient(135deg, #EE1C25 0%, #FF6B6B 100%)',
        py: 4,
      }}
    >
      <Card sx={{ maxWidth: 600, width: '100%', m: 2 }}>
        <CardContent sx={{ p: 4 }}>
          {/* Logo */}
          <Box sx={{ textAlign: 'center', mb: 3 }}>
            <img
              src={`${process.env.PUBLIC_URL}/image.png`}
              alt="Invoice Platform"
              style={{ height: 60, marginBottom: 16 }}
            />
            <Typography variant="h5" component="h1" fontWeight="bold">
              Create Your Account
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Join the Invoice Management Platform
            </Typography>
          </Box>

          {/* Bootstrap Notice - Only show if database is empty */}
          {!policyLoading && !localRegistrationEnabled && (
            <Alert severity="info" sx={{ mb: 2 }}>
              Local registration is disabled. Use PaperIQ to register and sign in.
            </Alert>
          )}

          {!bootstrapLoading && needsBootstrap === true && (
            <Alert severity="success" sx={{ mb: 2 }} icon={<CheckCircleIcon />}>
              <strong>🎉 System Bootstrap:</strong> You will be the first user! Your account will automatically 
              become the <strong>Global Administrator</strong> with full system access to manage all tenants and users.
            </Alert>
          )}

          {/* Regular Registration Notice - Show if users exist */}
          {!bootstrapLoading && needsBootstrap === false && (
            <Alert severity="info" sx={{ mb: 2 }}>
              Your account will be created in <strong>pending</strong> status. An administrator will need to 
              assign you to a tenant before you can access the system.
            </Alert>
          )}

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          {localRegistrationEnabled ? (
            <form onSubmit={handleSubmit}>
              <Stack spacing={2}>
                <Box display="flex" gap={2}>
                  <TextField
                    fullWidth
                    label="First Name"
                    name="firstName"
                    value={formData.firstName}
                    onChange={handleChange}
                  />
                  <TextField
                    fullWidth
                    label="Last Name"
                    name="lastName"
                    value={formData.lastName}
                    onChange={handleChange}
                  />
                </Box>

                <TextField
                  fullWidth
                  label="Username"
                  name="username"
                  value={formData.username}
                  onChange={handleChange}
                  required
                  autoFocus
                  helperText="Choose a unique username"
                />

                <TextField
                  fullWidth
                  label="Email Address"
                  name="email"
                  type="email"
                  value={formData.email}
                  onChange={handleChange}
                  required
                  helperText="We'll never share your email"
                />

                <TextField
                  fullWidth
                  label="Password"
                  name="password"
                  type="password"
                  value={formData.password}
                  onChange={handleChange}
                  required
                  helperText="Minimum 8 characters"
                />

                <TextField
                  fullWidth
                  label="Confirm Password"
                  name="confirmPassword"
                  type="password"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  required
                />

                <Button
                  fullWidth
                  type="submit"
                  variant="contained"
                  size="large"
                  disabled={loading}
                  sx={{ mt: 2 }}
                >
                  {loading ? <CircularProgress size={24} /> : 'Create Account'}
                </Button>
              </Stack>
            </form>
          ) : (
            <Button fullWidth variant="contained" size="large" onClick={startPaperIqLogin} sx={{ mt: 1 }}>
              Continue with PaperIQ
            </Button>
          )}

          <Box sx={{ mt: 3, textAlign: 'center' }}>
            <Typography variant="body2" color="text.secondary">
              Already have an account?{' '}
              <Link
                component="button"
                onClick={() => navigate('/login')}
                sx={{ cursor: 'pointer' }}
              >
                Sign in
              </Link>
            </Typography>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
};

export default Register;

