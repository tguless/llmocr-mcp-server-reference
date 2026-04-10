import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  IconButton,
  Tooltip,
  TextField,
  InputAdornment,
  Alert,
  CircularProgress,
  Button,
} from '@mui/material';
import {
  Edit as EditIcon,
  Search as SearchIcon,
  CheckCircle as SuccessIcon,
  Error as ErrorIcon,
  Warning as WarningIcon,
  Description as PdfIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import { rawJsonProcessingAPI } from '../services/api';
import RawJsonCorrectionDialog from './RawJsonCorrectionDialog';

const RawJsonProcessing: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const [records, setRecords] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedRecord, setSelectedRecord] = useState<any>(null);
  const [correctionDialogOpen, setCorrectionDialogOpen] = useState(false);

  const fetchRecords = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await rawJsonProcessingAPI.getAllRecords();
      setRecords(data.records || []);
    } catch (err: any) {
      setError(err.message || 'Failed to load records');
      console.error('Error fetching raw JSON processing records:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchRecords();
  }, []);

  // Filter records by search term
  const filteredRecords = records.filter((record: any) => {
    if (!searchTerm) return true;
    const term = searchTerm.toLowerCase();
    return (
      record.sourceFilename?.toLowerCase().includes(term) ||
      record.jobId?.toLowerCase().includes(term) ||
      record.toolName?.toLowerCase().includes(term) ||
      record.jsonSchema?.schemaName?.toLowerCase().includes(term) ||
      record.jsonSchema?.schemaVersion?.toLowerCase().includes(term)
    );
  });

  const getStatusChip = (status: string) => {
    switch (status) {
      case 'SUCCESS':
        return <Chip icon={<SuccessIcon />} label="Success" color="success" size="small" />;
      case 'ERROR':
        return <Chip icon={<ErrorIcon />} label="Error" color="error" size="small" />;
      case 'SCHEMA_MISSING':
        return <Chip icon={<WarningIcon />} label="Schema Missing" color="warning" size="small" />;
      case 'VALIDATION_FAILED':
        return <Chip icon={<ErrorIcon />} label="Validation Failed" color="error" size="small" />;
      default:
        return <Chip label={status} size="small" />;
    }
  };

  const handleOpenCorrection = (record: any) => {
    setSelectedRecord(record);
    setCorrectionDialogOpen(true);
  };

  const handleCloseCorrection = () => {
    setCorrectionDialogOpen(false);
    setSelectedRecord(null);
  };

  const handleSaved = () => {
    // Refresh records after save
    fetchRecords();
  };

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Box>
        <Alert severity="error">
          Failed to load raw JSON processing records: {error}
        </Alert>
        <Button
          startIcon={<RefreshIcon />}
          onClick={fetchRecords}
          sx={{ mt: 2 }}
        >
          Retry
        </Button>
      </Box>
    );
  }

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" fontWeight={600}>
          Raw JSON Processing
        </Typography>
        <Box display="flex" gap={2} alignItems="center">
          <Chip label={`${records.length} Total Records`} color="primary" />
          <IconButton onClick={fetchRecords} size="small">
            <RefreshIcon />
          </IconButton>
        </Box>
      </Box>

      {/* Search */}
      <Box mb={3}>
        <TextField
          fullWidth
          placeholder="Search by filename, job ID, tool name, schema name, or schema version..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon />
              </InputAdornment>
            ),
          }}
        />
      </Box>

      {/* Records Table */}
      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>PDF File</TableCell>
              <TableCell>Job ID</TableCell>
              <TableCell>Tool</TableCell>
              <TableCell>Schema</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Created</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {filteredRecords.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  <Typography variant="body2" color="text.secondary" py={4}>
                    {searchTerm ? 'No records match your search' : 'No records found'}
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              filteredRecords.map((record: any) => (
                <TableRow key={record.id} hover>
                  <TableCell>
                    <Box display="flex" alignItems="center" gap={1}>
                      <PdfIcon fontSize="small" color="action" />
                      <Typography variant="body2">
                        {record.sourceFilename || 'N/A'}
                      </Typography>
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" fontFamily="monospace">
                      {record.jobId || 'N/A'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" fontFamily="monospace">
                      {record.toolName}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {record.jsonSchema ? (
                      <Tooltip title={`Version ${record.jsonSchema.schemaVersion}`}>
                        <Chip
                          label={record.jsonSchema.schemaName}
                          size="small"
                          variant="outlined"
                        />
                      </Tooltip>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        No schema
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>{getStatusChip(record.processingStatus)}</TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">
                      {new Date(record.createdAt).toLocaleString()}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Correct JSON">
                      <IconButton
                        size="small"
                        onClick={() => handleOpenCorrection(record)}
                      >
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Correction Dialog */}
      <RawJsonCorrectionDialog
        open={correctionDialogOpen}
        onClose={handleCloseCorrection}
        record={selectedRecord}
        onSaved={handleSaved}
      />
    </Box>
  );
};

export default RawJsonProcessing;

