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
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';

const ForgotPassword: React.FC = () => {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(false);
    setLoading(true);

    try {
      await api.post('/api/password-reset/request', { email });
      setSuccess(true);
      setEmail('');
    } catch (err: any) {
      // Even on error, show success message to prevent email enumeration
      setSuccess(true);
      setEmail('');
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
      }}
    >
      <Card sx={{ maxWidth: 450, width: '100%', m: 2 }}>
        <CardContent sx={{ p: 4 }}>
          {/* Logo */}
          <Box sx={{ textAlign: 'center', mb: 3 }}>
            <img
              src={`${process.env.PUBLIC_URL}/image.png`}
              alt="Invoice Platform"
              style={{ height: 60, marginBottom: 16 }}
            />
            <Typography variant="h5" component="h1" fontWeight="bold">
              Reset Password
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Enter your email to receive a password reset link
            </Typography>
          </Box>

          {success && (
            <Alert severity="success" sx={{ mb: 2 }}>
              If an account exists with that email, a password reset link has been sent.
              Please check your inbox and spam folder.
            </Alert>
          )}

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          <form onSubmit={handleSubmit}>
            <Stack spacing={2}>
              <TextField
                fullWidth
                label="Email Address"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoFocus
                disabled={loading || success}
              />

              <Button
                fullWidth
                type="submit"
                variant="contained"
                size="large"
                disabled={loading || success}
                sx={{ mt: 2 }}
              >
                {loading ? <CircularProgress size={24} /> : 'Send Reset Link'}
              </Button>
            </Stack>
          </form>

          <Box sx={{ mt: 3, textAlign: 'center' }}>
            <Button
              startIcon={<ArrowBackIcon />}
              onClick={() => navigate('/login')}
              sx={{ textTransform: 'none' }}
            >
              Back to Login
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
};

export default ForgotPassword;

