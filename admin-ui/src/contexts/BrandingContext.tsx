import React, { createContext, useContext, useEffect, useState } from 'react';
import { TenantBranding, getDefaultBranding, getBrandingByTenantId } from '../services/brandingApi';

interface BrandingContextType {
  branding: TenantBranding | null;
  loading: boolean;
  error: string | null;
  refreshBranding: () => Promise<void>;
}

const BrandingContext = createContext<BrandingContextType | undefined>(undefined);

export const BrandingProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [branding, setBranding] = useState<TenantBranding | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadBranding = async () => {
    try {
      setLoading(true);
      setError(null);
      
      // Get current user's tenant ID from localStorage
      const storedUser = localStorage.getItem('user');
      let brandingData: TenantBranding;
      
      if (storedUser) {
        try {
          const user = JSON.parse(storedUser);
          if (user.tenantId) {
            // Load branding for the user's tenant
            brandingData = await getBrandingByTenantId(user.tenantId);
          } else {
            // No tenant ID, load default
            brandingData = await getDefaultBranding();
          }
        } catch (parseError) {
          console.error('Failed to parse user data:', parseError);
          brandingData = await getDefaultBranding();
        }
      } else {
        // No user logged in (e.g., on login page), load default branding
        brandingData = await getDefaultBranding();
      }
      
      setBranding(brandingData);
    } catch (err) {
      console.error('Failed to load branding:', err);
      setError('Failed to load branding');
      
      // Set fallback branding
      setBranding({
        tenantId: 'default',
        tenantName: 'PaperIQ.ai Invoice Intelligence Platform',
        displayName: 'PaperIQ.ai Invoice Intelligence Platform',
        tagline: 'AI-Powered Document Processing',
        logoUrl: '/image.png',
        loginLogoUrl: '/image.png',
        primaryColor: '#1976d2',
        secondaryColor: '#42a5f5',
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadBranding();
    
    // Listen for storage changes (e.g., when user logs in/out)
    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === 'user') {
        console.log('User changed, reloading branding...');
        loadBranding();
      }
    };
    
    window.addEventListener('storage', handleStorageChange);
    
    // Also listen for custom login event (for same-tab login)
    const handleLogin = () => {
      console.log('Login detected, reloading branding...');
      loadBranding();
    };
    
    window.addEventListener('userLoggedIn', handleLogin);
    
    return () => {
      window.removeEventListener('storage', handleStorageChange);
      window.removeEventListener('userLoggedIn', handleLogin);
    };
  }, []);

  const value = {
    branding,
    loading,
    error,
    refreshBranding: loadBranding,
  };

  return <BrandingContext.Provider value={value}>{children}</BrandingContext.Provider>;
};

export const useBranding = () => {
  const context = useContext(BrandingContext);
  if (context === undefined) {
    throw new Error('useBranding must be used within a BrandingProvider');
  }
  return context;
};

