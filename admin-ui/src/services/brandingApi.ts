import api from './api';

// Get the API base path for logo URLs
const getApiBasePath = () => {
  return process.env.REACT_APP_API_URL || 
    (process.env.NODE_ENV === 'production' 
      ? '/invoice'  // Production: context root
      : 'http://localhost:8081/mcp-invoice');  // Dev: full URL
};

export interface TenantBranding {
  tenantId: string;
  tenantName: string;
  displayName?: string;
  tagline?: string;
  logoUrl?: string;
  loginLogoUrl?: string;
  primaryColor?: string;
  secondaryColor?: string;
}

/**
 * Get branding for a specific tenant
 */
export const getBrandingByTenantId = async (tenantId: string): Promise<TenantBranding> => {
  const response = await api.get(`/api/branding/${tenantId}`);
  return response.data;
};

/**
 * Get default branding
 */
export const getDefaultBranding = async (): Promise<TenantBranding> => {
  const response = await api.get('/api/branding/default');
  return response.data;
};

/**
 * Get effective display name (falls back to tenant name)
 */
export const getEffectiveDisplayName = (branding: TenantBranding): string => {
  return branding.displayName || branding.tenantName;
};

/**
 * Get effective primary color (falls back to default blue)
 */
export const getEffectivePrimaryColor = (branding: TenantBranding): string => {
  return branding.primaryColor || '#1976d2';
};

/**
 * Get effective secondary color (falls back to default light blue)
 */
export const getEffectiveSecondaryColor = (branding: TenantBranding): string => {
  return branding.secondaryColor || '#42a5f5';
};

/**
 * Get effective logo URL (falls back to default)
 * Converts S3 keys to proxied URLs or handles local paths
 */
export const getEffectiveLogoUrl = (branding: TenantBranding): string => {
  const logoUrl = branding.logoUrl || '/image.png';
  
  // If it's an S3 key (starts with "branding/" or "logos/"), proxy through backend
  if (logoUrl.startsWith('branding/') || logoUrl.startsWith('logos/')) {
    const basePath = getApiBasePath();
    return `${basePath}/api/branding/logo/${logoUrl}`;
  }
  
  // If it's a relative path (starts with /), prepend PUBLIC_URL
  if (logoUrl.startsWith('/') && !logoUrl.startsWith('//')) {
    return `${process.env.PUBLIC_URL}${logoUrl}`;
  }
  
  // Otherwise return as-is (absolute URL)
  return logoUrl;
};

/**
 * Get effective login logo URL (falls back to logo URL, then default)
 * Converts S3 keys to proxied URLs or handles local paths
 */
export const getEffectiveLoginLogoUrl = (branding: TenantBranding): string => {
  const logoUrl = branding.loginLogoUrl || branding.logoUrl || '/image.png';
  
  // If it's an S3 key (starts with "branding/" or "logos/"), proxy through backend
  if (logoUrl.startsWith('branding/') || logoUrl.startsWith('logos/')) {
    const basePath = getApiBasePath();
    return `${basePath}/api/branding/logo/${logoUrl}`;
  }
  
  // If it's a relative path (starts with /), prepend PUBLIC_URL
  if (logoUrl.startsWith('/') && !logoUrl.startsWith('//')) {
    return `${process.env.PUBLIC_URL}${logoUrl}`;
  }
  
  // Otherwise return as-is (absolute URL)
  return logoUrl;
};

