import React from 'react';
import {
  Box,
  Paper,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Stack,
  Card,
  CardContent,
  Divider,
} from '@mui/material';
import { Event, Inventory2 } from '@mui/icons-material';

interface ServicePeriod {
  startDate?: string;
  endDate?: string;
}

interface CustomMetadata {
  key: string;
  value: string;
  createdBy?: string;
  updatedAt?: string;
}

interface InvoiceMetadataDisplayProps {
  servicePeriod?: ServicePeriod;
  customMetadata?: CustomMetadata[];
}

const formatDate = (dateString?: string): string => {
  if (!dateString) return 'N/A';
  try {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return dateString;
  }
};

export const InvoiceMetadataDisplay: React.FC<InvoiceMetadataDisplayProps> = ({
  servicePeriod,
  customMetadata,
}) => {
  const hasServicePeriod = servicePeriod?.startDate || servicePeriod?.endDate;
  const hasMetadata = customMetadata && customMetadata.length > 0;

  if (!hasServicePeriod && !hasMetadata) {
    return null;
  }

  return (
    <Stack spacing={3} sx={{ mb: 3 }}>
      {/* Service Period Section */}
      {hasServicePeriod && (
        <Card variant="outlined">
          <CardContent>
            <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 2 }}>
              <Event color="primary" />
              <Typography variant="h6" sx={{ fontWeight: 600 }}>
                Service Period
              </Typography>
            </Stack>
            <Divider sx={{ mb: 2 }} />
            <Stack direction="row" spacing={4}>
              <Box>
                <Typography variant="caption" color="textSecondary" sx={{ display: 'block', mb: 0.5 }}>
                  Start Date
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 500 }}>
                  {formatDate(servicePeriod?.startDate)}
                </Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="textSecondary" sx={{ display: 'block', mb: 0.5 }}>
                  End Date
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 500 }}>
                  {formatDate(servicePeriod?.endDate)}
                </Typography>
              </Box>
            </Stack>
          </CardContent>
        </Card>
      )}

      {/* Custom Metadata Section */}
      {hasMetadata && (
        <Card variant="outlined">
          <CardContent>
            <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 2 }}>
              <Inventory2 color="primary" />
              <Typography variant="h6" sx={{ fontWeight: 600 }}>
                Custom Metadata ({customMetadata.length})
              </Typography>
            </Stack>
            <Divider sx={{ mb: 2 }} />
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                    <TableCell sx={{ fontWeight: 600 }}>Key</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Value</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Created By</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>Updated</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {customMetadata.map((meta, index) => (
                    <TableRow key={index} hover>
                      <TableCell>
                        <Chip
                          label={meta.key}
                          variant="outlined"
                          size="small"
                          color="primary"
                        />
                      </TableCell>
                      <TableCell>
                        <Typography
                          variant="body2"
                          sx={{
                            fontFamily: 'monospace',
                            backgroundColor: '#f0f0f0',
                            padding: '4px 8px',
                            borderRadius: '4px',
                            maxWidth: '300px',
                            overflow: 'auto',
                          }}
                        >
                          {meta.value}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" color="textSecondary">
                          {meta.createdBy || 'System'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" color="textSecondary">
                          {meta.updatedAt ? formatDate(meta.updatedAt) : 'N/A'}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </CardContent>
        </Card>
      )}
    </Stack>
  );
};

export default InvoiceMetadataDisplay;


