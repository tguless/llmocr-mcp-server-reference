import React, { useEffect, useMemo, useState } from 'react';
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
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { useBranding } from '../contexts/BrandingContext';
import { getEffectiveDisplayName, getEffectiveLoginLogoUrl, getEffectivePrimaryColor } from '../services/brandingApi';

const extractApiErrorMessage = (err: any, fallback: string): string => {
  const payload = err?.response?.data;
  if (typeof payload?.error === 'string' && payload.error.trim()) {
    return payload.error;
  }
  if (typeof payload?.message === 'string' && payload.message.trim()) {
    return payload.message;
  }
  if (typeof payload?.data?.error === 'string' && payload.data.error.trim()) {
    return payload.data.error;
  }
  if (typeof payload?.data?.message === 'string' && payload.data.message.trim()) {
    return payload.data.message;
  }
  if (typeof err?.message === 'string' && err.message.trim()) {
    return err.message;
  }
  return fallback;
};

const Login: React.FC = () => {
  const navigate = useNavigate();
  const { branding } = useBranding();
  const [usernameOrEmail, setUsernameOrEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [ssoLoading, setSsoLoading] = useState(false);
  const [ssoMessage, setSsoMessage] = useState<string | null>(null);
  const [localPasswordLoginEnabled, setLocalPasswordLoginEnabled] = useState(false);
  const [policyLoading, setPolicyLoading] = useState(true);
  const defaultPaperIqBaseUrl =
    window.location.hostname === 'localhost' ? 'http://localhost:3000/llmocr' : 'https://paperiq.ai/llmocr';
  const launchEndpoint =
    process.env.REACT_APP_PAPERIQ_SSO_LAUNCH_URL || `${defaultPaperIqBaseUrl}/api/mcp/admin-sso/launch`;
  const loginEndpoint =
    process.env.REACT_APP_PAPERIQ_LOGIN_URL || `${defaultPaperIqBaseUrl}/login`;

  const searchParams = useMemo(() => new URLSearchParams(window.location.search), []);
  const ssoCode = searchParams.get('code');

  const startPaperIqLogin = () => {
    const redirectUri = `${window.location.origin}${window.location.pathname}`;
    const state = window.crypto?.randomUUID?.() || `${Date.now()}`;
    const launchUrl = new URL(launchEndpoint);
    launchUrl.searchParams.set('redirectUri', redirectUri);
    launchUrl.searchParams.set('state', state);

    const loginUrl = new URL(loginEndpoint);
    loginUrl.searchParams.set('next', launchUrl.toString());
    window.location.href = loginUrl.toString();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      console.log('🔍 Attempting login...');
      const response = await api.post('/api/auth/login', {
        usernameOrEmail,
        password,
      });

      const { accessToken, refreshToken, user } = response.data;

      // Store tokens and user info
      localStorage.setItem('authToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);
      localStorage.setItem('user', JSON.stringify(user));

      // Dispatch custom event to trigger branding reload
      window.dispatchEvent(new Event('userLoggedIn'));

      // Redirect to main app
      navigate('/categories');
    } catch (err: any) {
      setError(extractApiErrorMessage(err, 'Login failed. Please try again.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const loadPolicy = async () => {
      try {
        const response = await api.get('/api/auth/policy');
        setLocalPasswordLoginEnabled(Boolean(response.data.localPasswordLoginEnabled));
      } catch {
        setLocalPasswordLoginEnabled(false);
      } finally {
        setPolicyLoading(false);
      }
    };
    void loadPolicy();
  }, []);

  useEffect(() => {
    const exchangeSsoCode = async () => {
      if (!ssoCode) return;
      const exchangeLockKey = `invoice-sso-exchange:${ssoCode}`;
      if (sessionStorage.getItem(exchangeLockKey) === 'in_progress') {
        return;
      }
      sessionStorage.setItem(exchangeLockKey, 'in_progress');
      // Remove the code from the URL immediately to avoid duplicate exchanges
      // from dev-mode double effect execution or rapid remounts.
      window.history.replaceState({}, document.title, window.location.pathname);
      setSsoLoading(true);
      setError(null);
      setSsoMessage('Completing PaperIQ SSO sign-in...');
      try {
        const redirectUri = `${window.location.origin}${window.location.pathname}`;
        const response = await api.post('/api/auth/sso/exchange', {
          code: ssoCode,
          redirectUri,
        });

        const { accessToken, refreshToken, user } = response.data;
        localStorage.setItem('authToken', accessToken);
        localStorage.setItem('refreshToken', refreshToken);
        localStorage.setItem('user', JSON.stringify(user));
        window.dispatchEvent(new Event('userLoggedIn'));
        sessionStorage.setItem(exchangeLockKey, 'done');
        navigate('/categories', { replace: true });
      } catch (err: any) {
        sessionStorage.removeItem(exchangeLockKey);
        setError(extractApiErrorMessage(err, 'SSO login failed. You can sign in with username/password.'));
      } finally {
        setSsoLoading(false);
        setSsoMessage(null);
      }
    };

    void exchangeSsoCode();
  }, [navigate, ssoCode]);

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#f5f5f5',
        backgroundImage: branding ? `linear-gradient(135deg, ${getEffectivePrimaryColor(branding)} 0%, ${getEffectivePrimaryColor(branding)}88 100%)` : 'linear-gradient(135deg, #EE1C25 0%, #FF6B6B 100%)',
      }}
    >
      <Card sx={{ maxWidth: 450, width: '100%', m: 2 }}>
        <CardContent sx={{ p: 4 }}>
          {/* Logo */}
          <Box sx={{ textAlign: 'center', mb: 3, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <img
              src={branding ? getEffectiveLoginLogoUrl(branding) : `${process.env.PUBLIC_URL}/image.png`}
              alt={branding ? getEffectiveDisplayName(branding) : "Logo"}
              style={{ height: 180, marginBottom: 24 }}
            />
            <Typography variant="h5" component="h1" fontWeight="bold" textAlign="center">
              {branding ? getEffectiveDisplayName(branding) : 'PaperIQ.ai Invoice Intelligence Platform'}
            </Typography>
            <Typography variant="body2" color="text.secondary" textAlign="center">
              {branding?.tagline || 'Sign in to your account'}
            </Typography>
          </Box>

          {/* Marketing Blurb */}
          <Box 
            sx={{ 
              mb: 3, 
              p: 2, 
              backgroundColor: 'rgba(0, 0, 0, 0.02)', 
              borderRadius: 1,
              borderLeft: '4px solid',
              borderColor: 'primary.main'
            }}
          >
            <Typography variant="body2" color="text.primary" sx={{ mb: 1, fontWeight: 500 }}>
              What is PaperIQ.ai Invoice Intelligence Platform?
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ fontSize: '0.875rem', lineHeight: 1.6, mb: 1.5 }}>
              PaperIQ.ai Invoice Intelligence Platform is an AI-powered invoice processing platform that automatically extracts, 
              categorizes, and validates line items from invoices—helping you process and audit 
              billing data with precision and speed.
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ fontSize: '0.875rem', lineHeight: 1.6 }}>
              This is a technology showcase demonstrating the capabilities of{' '}
              <Link 
                href="https://eyesense.ai/llmocr/" 
                target="_blank" 
                rel="noopener noreferrer"
                sx={{ fontWeight: 500 }}
              >
                LLM-OCR
              </Link>
              , our multi-modal AI platform for intelligent document processing.
            </Typography>
          </Box>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}
          {ssoMessage && (
            <Alert severity="info" sx={{ mb: 2 }}>
              {ssoMessage}
            </Alert>
          )}

          <Stack spacing={2}>
            <Button
              fullWidth
              variant="contained"
              size="large"
              disabled={ssoLoading || policyLoading}
              onClick={startPaperIqLogin}
            >
              Continue with PaperIQ
            </Button>
            {!localPasswordLoginEnabled && (
              <Alert severity="info">
                Local username/password login is disabled for this workspace. Use PaperIQ SSO.
              </Alert>
            )}
          </Stack>

          {localPasswordLoginEnabled && (
            <form onSubmit={handleSubmit}>
              <Stack spacing={2} sx={{ mt: 2 }}>
                <TextField
                  fullWidth
                  label="Username or Email"
                  value={usernameOrEmail}
                  onChange={(e) => setUsernameOrEmail(e.target.value)}
                  required
                  autoFocus
                />

                <TextField
                  fullWidth
                  label="Password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />

                <Button
                  fullWidth
                  type="submit"
                  variant="outlined"
                  size="large"
                  disabled={loading || ssoLoading}
                  sx={{ mt: 2 }}
                >
                  {loading ? <CircularProgress size={24} /> : 'Sign In with local account'}
                </Button>
              </Stack>
            </form>
          )}

          {localPasswordLoginEnabled && (
            <Box sx={{ mt: 2, textAlign: 'center' }}>
              <Link
                component="button"
                onClick={() => navigate('/forgot-password')}
                sx={{ cursor: 'pointer', fontSize: '0.875rem' }}
              >
                Forgot password?
              </Link>
            </Box>
          )}

          <Box sx={{ mt: 2, textAlign: 'center' }}>
            <Typography variant="body2" color="text.secondary">
              Don't have an account?{' '}
              <Link
                component="button"
                onClick={() => navigate('/register')}
                sx={{ cursor: 'pointer' }}
              >
                Register now
              </Link>
            </Typography>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
};

export default Login;

