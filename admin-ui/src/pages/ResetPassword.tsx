import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  TextField,
  Button,
  Typography,
  Alert,
  CircularProgress,
  Stack,
  InputAdornment,
  IconButton,
} from '@mui/material';
import { useNavigate, useSearchParams } from 'react-router-dom';
import api from '../services/api';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';

const ResetPassword: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);
  const [validating, setValidating] = useState(true);
  const [tokenValid, setTokenValid] = useState(false);

  useEffect(() => {
    // Validate token on mount
    const validateToken = async () => {
      if (!token) {
        setError('Invalid or missing reset token');
        setValidating(false);
        return;
      }

      try {
        const response = await api.get(`/api/password-reset/validate?token=${token}`);
        if (response.data.valid) {
          setTokenValid(true);
        } else {
          setError('This reset link is invalid or has expired');
        }
      } catch (err: any) {
        setError('This reset link is invalid or has expired');
      } finally {
        setValidating(false);
      }
    };

    validateToken();
  }, [token]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    
    // Validation
    if (newPassword.length < 8) {
      setError('Password must be at least 8 characters long');
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    setLoading(true);

    try {
      await api.post('/api/password-reset/confirm', {
        token,
        newPassword,
      });
      
      setSuccess(true);
      
      // Redirect to login after 3 seconds
      setTimeout(() => {
        navigate('/login');
      }, 3000);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to reset password. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  if (validating) {
    return (
      <Box
        sx={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundImage: 'linear-gradient(135deg, #EE1C25 0%, #FF6B6B 100%)',
        }}
      >
        <CircularProgress size={60} sx={{ color: 'white' }} />
      </Box>
    );
  }

  if (success) {
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
          <CardContent sx={{ p: 4, textAlign: 'center' }}>
            <CheckCircleIcon sx={{ fontSize: 80, color: 'success.main', mb: 2 }} />
            <Typography variant="h5" component="h1" fontWeight="bold" gutterBottom>
              Password Reset Successful!
            </Typography>
            <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
              Your password has been changed successfully.
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Redirecting to login...
            </Typography>
          </CardContent>
        </Card>
      </Box>
    );
  }

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
              Set New Password
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Enter your new password below
            </Typography>
          </Box>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          {tokenValid ? (
            <form onSubmit={handleSubmit}>
              <Stack spacing={2}>
                <TextField
                  fullWidth
                  label="New Password"
                  type={showPassword ? 'text' : 'password'}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  required
                  autoFocus
                  helperText="Must be at least 8 characters"
                  InputProps={{
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton
                          onClick={() => setShowPassword(!showPassword)}
                          edge="end"
                        >
                          {showPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                        </IconButton>
                      </InputAdornment>
                    ),
                  }}
                />

                <TextField
                  fullWidth
                  label="Confirm Password"
                  type={showConfirmPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                  InputProps={{
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton
                          onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                          edge="end"
                        >
                          {showConfirmPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                        </IconButton>
                      </InputAdornment>
                    ),
                  }}
                />

                <Button
                  fullWidth
                  type="submit"
                  variant="contained"
                  size="large"
                  disabled={loading}
                  sx={{ mt: 2 }}
                >
                  {loading ? <CircularProgress size={24} /> : 'Reset Password'}
                </Button>
              </Stack>
            </form>
          ) : (
            <Box sx={{ textAlign: 'center' }}>
              <Button
                variant="contained"
                onClick={() => navigate('/forgot-password')}
                sx={{ mt: 2 }}
              >
                Request New Reset Link
              </Button>
            </Box>
          )}

          <Box sx={{ mt: 3, textAlign: 'center' }}>
            <Button
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

export default ResetPassword;

