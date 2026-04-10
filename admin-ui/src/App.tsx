import React, { useState, useMemo } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation as useRouterLocation } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Box from '@mui/material/Box';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Container from '@mui/material/Container';
import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import IconButton from '@mui/material/IconButton';
import MenuIcon from '@mui/icons-material/Menu';
import CategoryIcon from '@mui/icons-material/Category';
import ReceiptIcon from '@mui/icons-material/Receipt';
import DescriptionIcon from '@mui/icons-material/Description';
import BusinessIcon from '@mui/icons-material/Business';
import PeopleIcon from '@mui/icons-material/People';
import SettingsIcon from '@mui/icons-material/Settings';
import CompareArrowsIcon from '@mui/icons-material/CompareArrows';
import AssignmentIcon from '@mui/icons-material/Assignment';
import StorageIcon from '@mui/icons-material/Storage';
import BackupIcon from '@mui/icons-material/Backup';
import PaletteIcon from '@mui/icons-material/Palette';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DataObjectIcon from '@mui/icons-material/DataObject';
import GavelIcon from '@mui/icons-material/Gavel';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useNavigate, useLocation } from 'react-router-dom';

import CategoryManagement from './pages/CategoryManagement';
import MetadataManagement from './pages/MetadataManagement';
import TransactionViewer from './pages/TransactionViewer';
import InvoiceManagement from './pages/InvoiceManagement';
import InvoiceLandingPage from './pages/InvoiceLandingPage';
import VendorManagement from './pages/VendorManagement';
import UserManagement from './pages/UserManagement';
import TenantHydration from './pages/TenantHydration';
import Settings from './pages/Settings';
import VarianceAnalysis from './pages/VarianceAnalysis';
import AuditReport from './pages/AuditReport';
import S3Upload from './pages/S3Upload';
import BrandingManagement from './pages/BrandingManagement';
import RawJsonProcessing from './pages/RawJsonProcessing';
import ContractManagement from './pages/ContractManagement';
import ContractDetail from './pages/ContractDetail';
import ContractViewer from './pages/ContractViewer';
import Login from './pages/Login';
import Register from './pages/Register';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import { useBranding } from './contexts/BrandingContext';
import { getEffectivePrimaryColor, getEffectiveSecondaryColor, getEffectiveDisplayName, getEffectiveLogoUrl } from './services/brandingApi';

const drawerWidth = 280;

// Protected Route wrapper
function ProtectedRoute({ children }: { children: React.ReactElement }) {
  const token = localStorage.getItem('authToken');
  const location = useLocation();

  if (!token) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
}

function AppContent() {
  const navigate = useNavigate();
  const location = useLocation();
  const { branding } = useBranding();
  
  // Create theme based on tenant branding
  const theme = useMemo(() => createTheme({
    palette: {
      primary: {
        main: branding ? getEffectivePrimaryColor(branding) : '#EE1C25',
        contrastText: '#FFFFFF',
      },
      secondary: {
        main: branding ? getEffectiveSecondaryColor(branding) : '#00AAFF',
      },
      background: {
        default: '#F5F5F5',
      },
    },
    typography: {
      fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
      h6: {
        fontWeight: 600,
      },
    },
  }), [branding]);
  
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen);
  };

  // Get current user from localStorage to check role
  const currentUser = JSON.parse(localStorage.getItem('user') || '{}');
  const isAdmin = currentUser.role === 'GLOBAL_ADMIN' || currentUser.role === 'TENANT_ADMIN';
  const isGlobalAdmin = currentUser.role === 'GLOBAL_ADMIN';
  const isTotalTenant = currentUser.tenantId === 'total';

  const menuItems = [
    { text: 'Invoices', icon: <DescriptionIcon />, path: '/invoices' },
    ...(isTotalTenant ? [
      { text: 'Contracts', icon: <GavelIcon />, path: '/contracts' },
      { text: 'Variance Analysis (MOCK)', icon: <CompareArrowsIcon />, path: '/variance-analysis' },
      { text: 'Audit Report', icon: <AssignmentIcon />, path: '/audit-report' },
    ] : []),
    { text: 'Vendors', icon: <BusinessIcon />, path: '/vendors' },
    { text: 'Category Management', icon: <CategoryIcon />, path: '/categories' },
    { text: 'Metadata Keys', icon: <StorageIcon />, path: '/metadata-keys' },
    { text: 'Raw JSON Processing', icon: <DataObjectIcon />, path: '/raw-json-processing' },
    { text: 'Transaction Viewer', icon: <ReceiptIcon />, path: '/transactions' },
    { text: 'S3 File Upload', icon: <BackupIcon />, path: '/s3-upload' },
    ...(isAdmin ? [
      { text: 'User Management', icon: <PeopleIcon />, path: '/users' }
    ] : []),
    ...(isGlobalAdmin ? [
      { text: 'Tenant Hydration', icon: <CloudUploadIcon />, path: '/tenant-hydration' }
    ] : []),
    { text: 'Branding', icon: <PaletteIcon />, path: '/branding' },
    { text: 'Settings', icon: <SettingsIcon />, path: '/settings' },
  ];

  const handleMenuItemClick = (path: string) => {
    navigate(path);
    if (isMobile) {
      setMobileOpen(false);
    }
  };

  const drawer = (
    <Box>
      <Toolbar
        sx={{
          backgroundColor: 'primary.main',
          color: 'white',
          minHeight: '180px !important',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          px: 2,
          py: 3,
        }}
      >
        <Box display="flex" flexDirection="column" alignItems="center" width="100%">
          <img
            src={branding ? getEffectiveLogoUrl(branding) : `${process.env.PUBLIC_URL}/image.png`}
            alt={branding ? getEffectiveDisplayName(branding) : "Logo"}
            style={{
              height: '120px',
              marginBottom: '12px',
            }}
          />
          <Typography variant="caption" sx={{ opacity: 0.9, textAlign: 'center' }}>
            {branding?.tagline || branding ? getEffectiveDisplayName(branding) : 'PaperIQ.ai Invoice Intelligence Platform'}
          </Typography>
        </Box>
      </Toolbar>
      <Box sx={{ overflow: 'auto', mt: 1 }}>
        <List>
          {menuItems.map((item) => (
            <ListItem key={item.text} disablePadding>
              <ListItemButton
                selected={location.pathname === item.path}
                onClick={() => handleMenuItemClick(item.path)}
                sx={{
                  mx: 1,
                  borderRadius: 1,
                  '&.Mui-selected': {
                    backgroundColor: 'rgba(238, 28, 37, 0.08)',
                    '&:hover': {
                      backgroundColor: 'rgba(238, 28, 37, 0.12)',
                    },
                  },
                }}
              >
                <ListItemIcon sx={{ color: location.pathname === item.path ? 'primary.main' : 'inherit' }}>
                  {item.icon}
                </ListItemIcon>
                <ListItemText 
                  primary={item.text}
                  primaryTypographyProps={{
                    fontWeight: location.pathname === item.path ? 600 : 400,
                  }}
                />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
      </Box>
    </Box>
  );

  return (
    <ThemeProvider theme={theme}>
    <Box sx={{ display: 'flex' }}>
      <CssBaseline />
      
      {/* App Bar */}
      <AppBar
        position="fixed"
        sx={{
          width: { md: `calc(100% - ${drawerWidth}px)` },
          ml: { md: `${drawerWidth}px` },
          backgroundColor: 'white',
          color: 'text.primary',
          boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
        }}
      >
        <Toolbar>
          {isMobile && (
            <IconButton
              color="inherit"
              aria-label="open drawer"
              edge="start"
              onClick={handleDrawerToggle}
              sx={{ mr: 2 }}
            >
              <MenuIcon />
            </IconButton>
          )}
          
          {isMobile && (
            <Box display="flex" alignItems="center" flexGrow={1}>
              <img
                src={branding ? getEffectiveLogoUrl(branding) : `${process.env.PUBLIC_URL}/image.png`}
                alt={branding ? getEffectiveDisplayName(branding) : "Logo"}
                style={{
                  height: '32px',
                  marginRight: '12px',
                }}
              />
            </Box>
          )}
          
          {!isMobile && (
            <Typography variant="h6" noWrap component="div" sx={{ color: 'text.secondary', fontWeight: 400 }}>
              {menuItems.find(item => item.path === location.pathname)?.text || 'Admin Console'}
            </Typography>
          )}
        </Toolbar>
      </AppBar>

      {/* Mobile Drawer */}
      {isMobile && (
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={handleDrawerToggle}
          ModalProps={{
            keepMounted: true, // Better open performance on mobile.
          }}
          sx={{
            display: { xs: 'block', md: 'none' },
            '& .MuiDrawer-paper': { boxSizing: 'border-box', width: drawerWidth },
          }}
        >
          {drawer}
        </Drawer>
      )}

      {/* Desktop Drawer */}
      <Drawer
        variant="permanent"
        sx={{
          display: { xs: 'none', md: 'block' },
          width: drawerWidth,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: drawerWidth,
            boxSizing: 'border-box',
            borderRight: '1px solid rgba(0, 0, 0, 0.08)',
          },
        }}
        open
      >
        {drawer}
      </Drawer>

      {/* Main Content */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 3,
          width: { xs: '100%', md: `calc(100% - ${drawerWidth}px)` },
          minHeight: '100vh',
          backgroundColor: 'background.default',
        }}
      >
        <Toolbar />
        <Container maxWidth="xl">
          <Routes>
            <Route path="/" element={<Navigate to="/invoices" replace />} />
            <Route path="/invoices" element={<InvoiceManagement />} />
            <Route path="/contracts" element={<ContractManagement />} />
            <Route path="/contracts/:id" element={<ContractViewer />} />
            <Route path="/contracts/:id/detail" element={<ContractDetail />} />
            <Route path="/variance-analysis" element={<VarianceAnalysis />} />
            <Route path="/audit-report" element={<AuditReport />} />
            <Route path="/vendors" element={<VendorManagement />} />
            <Route path="/categories" element={<CategoryManagement />} />
            <Route path="/metadata-keys" element={<MetadataManagement />} />
            <Route path="/raw-json-processing" element={<RawJsonProcessing />} />
            <Route path="/transactions" element={<TransactionViewer />} />
            <Route path="/s3-upload" element={<S3Upload />} />
            <Route path="/users" element={<UserManagement />} />
            <Route path="/tenant-hydration" element={<TenantHydration />} />
            <Route path="/branding" element={<BrandingManagement />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </Container>
      </Box>
    </Box>
    </ThemeProvider>
  );
}

function AppRoutes() {
  return (
    <Routes>
      {/* Public routes */}
      <Route path="/" element={<InvoiceLandingPage />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />

      {/* Protected routes */}
      <Route
        path="/*"
        element={
          <ProtectedRoute>
            <AppContent />
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}

function App() {
  return (
      <Router basename="/invoice">
        <AppRoutes />
      </Router>
  );
}

export default App;
