import React, { useState } from 'react';
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
  Alert,
  Card,
  CardContent,
  Button,
  Divider,
  Stack,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  IconButton,
  Tooltip,
} from '@mui/material';
import CompareArrowsIcon from '@mui/icons-material/CompareArrows';
import WarningIcon from '@mui/icons-material/Warning';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import DownloadIcon from '@mui/icons-material/Download';
import EditIcon from '@mui/icons-material/Edit';
import LinkIcon from '@mui/icons-material/Link';
import CloseIcon from '@mui/icons-material/Close';

interface VarianceRecord {
  siteNumber: string;
  plantName: string;
  pfSiteId: string;
  accountNumber: string;
  period: string;
  
  // Georgia Power (Offtaker Claimed)
  georgiaPowerGeneration: number;
  georgiaPowerRate: number;
  georgiaPowerCredit: number;
  
  // SAP HANA (Owner Actual)
  sapHanaGeneration: number;
  sapHanaRate: number;
  sapHanaCredit: number;
  
  // Variance
  generationVariance: number;
  generationVariancePercent: number;
  creditVariance: number;
  
  status: 'Match' | 'Minor Discrepancy' | 'Major Discrepancy';
  matchConfidence: number; // 0-100, how confident the auto-match is
  matchMethod: 'Auto - Account Number' | 'Auto - Fuzzy Match' | 'Manual Override';
}

// MOCK DATA - Based on Inman Production Report
const mockVarianceData: VarianceRecord[] = [
  {
    siteNumber: 'M2302',
    plantName: 'Oxendale 2',
    pfSiteId: '3051.171.M2302',
    accountNumber: '15035-51050',
    period: 'Sep 2025',
    georgiaPowerGeneration: 503866,
    georgiaPowerRate: 0.04822,
    georgiaPowerCredit: 24291.38,
    sapHanaGeneration: 503866,
    sapHanaRate: 0.04822,
    sapHanaCredit: 24291.38,
    generationVariance: 0,
    generationVariancePercent: 0.0,
    creditVariance: 0,
    status: 'Match',
    matchConfidence: 100,
    matchMethod: 'Auto - Account Number',
  },
  {
    siteNumber: 'M2303',
    plantName: 'Oxendale 3',
    pfSiteId: '3051.171.M2303',
    accountNumber: '20894-77021',
    period: 'Sep 2025',
    georgiaPowerGeneration: 354565,
    georgiaPowerRate: 0.04821,
    georgiaPowerCredit: 17097.12,
    sapHanaGeneration: 354565,
    sapHanaRate: 0.04821,
    sapHanaCredit: 17097.12,
    generationVariance: 0,
    generationVariancePercent: 0.0,
    creditVariance: 0,
    status: 'Match',
    matchConfidence: 100,
    matchMethod: 'Auto - Account Number',
  },
  {
    siteNumber: 'M2304',
    plantName: 'Oxendale 4',
    pfSiteId: '3051.171.M2304',
    accountNumber: '15035-51051',
    period: 'Sep 2025',
    georgiaPowerGeneration: 439868,
    georgiaPowerRate: 0.04822,
    georgiaPowerCredit: 21214.09,
    sapHanaGeneration: 439868,
    sapHanaRate: 0.04822,
    sapHanaCredit: 21214.09,
    generationVariance: 0,
    generationVariancePercent: 0.0,
    creditVariance: 0,
    status: 'Match',
    matchConfidence: 100,
    matchMethod: 'Auto - Account Number',
  },
  {
    siteNumber: 'M2308',
    plantName: 'Tift Moultrie',
    pfSiteId: '3051.171.M2308',
    accountNumber: '14567-89012',
    period: 'Sep 2025',
    georgiaPowerGeneration: 141020,
    georgiaPowerRate: 0.04819,
    georgiaPowerCredit: 6795.25,
    sapHanaGeneration: 140634,
    sapHanaRate: 0.04819,
    sapHanaCredit: 6776.65,
    generationVariance: 386,
    generationVariancePercent: 0.27,
    creditVariance: 18.60,
    status: 'Minor Discrepancy',
    matchConfidence: 85,
    matchMethod: 'Auto - Fuzzy Match',
  },
  {
    siteNumber: 'M2309',
    plantName: 'Decatur Belcher',
    pfSiteId: '3051.171.M2309',
    accountNumber: '18765-43210',
    period: 'Sep 2025',
    georgiaPowerGeneration: 671435,
    georgiaPowerRate: 0.04821,
    georgiaPowerCredit: 32372.82,
    sapHanaGeneration: 671435,
    sapHanaRate: 0.04821,
    sapHanaCredit: 32372.82,
    generationVariance: 0,
    generationVariancePercent: 0.0,
    creditVariance: 0,
    status: 'Match',
    matchConfidence: 100,
    matchMethod: 'Auto - Account Number',
  },
  {
    siteNumber: 'M2310',
    plantName: 'Decatur Carter 1',
    pfSiteId: '3051.171.M2310',
    accountNumber: '23456-78901',
    period: 'Sep 2025',
    georgiaPowerGeneration: 579409,
    georgiaPowerRate: 0.04820,
    georgiaPowerCredit: 27928.71,
    sapHanaGeneration: 556498,
    sapHanaRate: 0.04820,
    sapHanaCredit: 26823.20,
    generationVariance: 22911,
    generationVariancePercent: 4.12,
    creditVariance: 1105.51,
    status: 'Major Discrepancy',
    matchConfidence: 72,
    matchMethod: 'Auto - Fuzzy Match',
  },
];

const VarianceAnalysis: React.FC = () => {
  const [selectedRecord, setSelectedRecord] = useState<VarianceRecord | null>(null);
  const [correctionDialogOpen, setCorrectionDialogOpen] = useState(false);
  const [recordToCorrect, setRecordToCorrect] = useState<VarianceRecord | null>(null);
  const [correctionComment, setCorrectionComment] = useState('');
  const [selectedSapRecord, setSelectedSapRecord] = useState('');

  // Mock list of available SAP records (unmatched or alternative matches)
  const availableSapRecords = [
    '3051.171.M2302 - Oxendale 2 (503,866 kWh)',
    '3051.171.M2303 - Oxendale 3 (354,565 kWh)',
    '3051.171.M2308 - Tift Moultrie (140,634 kWh)',
    '3051.171.M2309 - Decatur Belcher (671,435 kWh)',
    '3051.171.M2310 - Decatur Carter 1 (556,498 kWh)',
    '3051.171.M2311 - Decatur Carter 2 (545,031 kWh)',
  ];

  // Calculate summary statistics
  const totalRecords = mockVarianceData.length;
  const matchCount = mockVarianceData.filter(r => r.status === 'Match').length;
  const minorDiscrepancyCount = mockVarianceData.filter(r => r.status === 'Minor Discrepancy').length;
  const majorDiscrepancyCount = mockVarianceData.filter(r => r.status === 'Major Discrepancy').length;
  const totalVariance = mockVarianceData.reduce((sum, r) => sum + Math.abs(r.creditVariance), 0);

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'Match':
        return 'success';
      case 'Minor Discrepancy':
        return 'warning';
      case 'Major Discrepancy':
        return 'error';
      default:
        return 'default';
    }
  };

  const formatNumber = (num: number) => {
    return num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  const formatCurrency = (num: number) => {
    return '$' + formatNumber(num);
  };

  const handleExportToSAP = () => {
    alert('Mock Export: In production, this would export variance data to SAP/Blueprints for posting');
  };

  const handleOpenCorrection = (record: VarianceRecord) => {
    setRecordToCorrect(record);
    setSelectedSapRecord(record.pfSiteId);
    setCorrectionComment('');
    setCorrectionDialogOpen(true);
  };

  const handleCloseCorrection = () => {
    setCorrectionDialogOpen(false);
    setRecordToCorrect(null);
    setCorrectionComment('');
    setSelectedSapRecord('');
  };

  const handleSubmitCorrection = () => {
    alert(`Mock Correction Submitted:\n\nGeorgia Power Invoice: ${recordToCorrect?.accountNumber} (${recordToCorrect?.plantName})\nNew SAP Match: ${selectedSapRecord}\nComment: ${correctionComment}\n\nIn production, this would:\n1. Update the correlation in the database\n2. Recalculate variances\n3. Log the correction with user ID and timestamp\n4. Trigger approval workflow if variance threshold exceeded\n5. Send notification to approvers`);
    handleCloseCorrection();
  };

  return (
    <Box>
      {/* Header with MOCK DATA warning */}
      <Alert severity="info" icon={<WarningIcon />} sx={{ mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          🎭 MOCK DATA - Variance Analysis Dashboard
        </Typography>
        <Typography variant="body2">
          This is a demonstration screen showing how AI-extracted Georgia Power invoice data would be 
          compared against the energy company's SAP HANA meter readings. This mockup illustrates the automated 
          variance analysis workflow described in the POC.
        </Typography>
      </Alert>

      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 600 }}>
          Offtaker Self-Billing Variance Analysis
        </Typography>
        <Button
          variant="contained"
          color="primary"
          startIcon={<DownloadIcon />}
          onClick={handleExportToSAP}
        >
          Export to SAP/Blueprints
        </Button>
      </Box>

      {/* Summary Cards */}
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 3, mb: 3 }}>
        <Box sx={{ flex: '1 1 200px', minWidth: '200px' }}>
          <Card>
            <CardContent>
              <Typography color="text.secondary" gutterBottom variant="body2">
                Total Invoices Processed
              </Typography>
              <Typography variant="h4" component="div">
                {totalRecords}
              </Typography>
            </CardContent>
          </Card>
        </Box>
        <Box sx={{ flex: '1 1 200px', minWidth: '200px' }}>
          <Card sx={{ backgroundColor: '#e8f5e9' }}>
            <CardContent>
              <Typography color="text.secondary" gutterBottom variant="body2">
                <CheckCircleIcon fontSize="small" sx={{ verticalAlign: 'middle', mr: 0.5 }} />
                Perfect Matches
              </Typography>
              <Typography variant="h4" component="div" color="success.main">
                {matchCount}
              </Typography>
            </CardContent>
          </Card>
        </Box>
        <Box sx={{ flex: '1 1 200px', minWidth: '200px' }}>
          <Card sx={{ backgroundColor: '#fff3e0' }}>
            <CardContent>
              <Typography color="text.secondary" gutterBottom variant="body2">
                <WarningIcon fontSize="small" sx={{ verticalAlign: 'middle', mr: 0.5 }} />
                Minor Discrepancies
              </Typography>
              <Typography variant="h4" component="div" color="warning.main">
                {minorDiscrepancyCount}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {'<1% variance'}
              </Typography>
            </CardContent>
          </Card>
        </Box>
        <Box sx={{ flex: '1 1 200px', minWidth: '200px' }}>
          <Card sx={{ backgroundColor: '#ffebee' }}>
            <CardContent>
              <Typography color="text.secondary" gutterBottom variant="body2">
                <WarningIcon fontSize="small" sx={{ verticalAlign: 'middle', mr: 0.5 }} />
                Major Discrepancies
              </Typography>
              <Typography variant="h4" component="div" color="error.main">
                {majorDiscrepancyCount}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {'>1% variance'}
              </Typography>
            </CardContent>
          </Card>
        </Box>
      </Box>

      <Box sx={{ mb: 3 }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Total Variance Impact
            </Typography>
            <Typography variant="h3" component="div" color={totalVariance > 0 ? 'error.main' : 'success.main'}>
              {formatCurrency(totalVariance)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Absolute value of all generation credit variances requiring review
            </Typography>
          </CardContent>
        </Card>
      </Box>

      {/* Variance Table */}
      <Paper sx={{ width: '100%', overflow: 'hidden', mb: 3 }}>
        <Box sx={{ p: 2, backgroundColor: '#f5f5f5', borderBottom: '1px solid #e0e0e0' }}>
          <Typography variant="h6" sx={{ display: 'flex', alignItems: 'center' }}>
            <CompareArrowsIcon sx={{ mr: 1 }} />
            Georgia Power Invoice vs. SAP HANA Comparison
          </Typography>
          <Typography variant="caption" color="text.secondary">
            September 2025 Billing Period
          </Typography>
        </Box>

        <TableContainer sx={{ maxHeight: 600 }}>
          <Table stickyHeader>
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600, backgroundColor: '#fafafa' }}>Site</TableCell>
                <TableCell sx={{ fontWeight: 600, backgroundColor: '#fafafa' }}>Plant Name</TableCell>
                <TableCell sx={{ fontWeight: 600, backgroundColor: '#fafafa' }}>Account #</TableCell>
                <TableCell align="center" sx={{ fontWeight: 600, backgroundColor: '#e8f5e9' }}>
                  Match<br />Confidence
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 600, backgroundColor: '#e3f2fd' }}>
                  Georgia Power<br />Generation (kWh)
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 600, backgroundColor: '#e3f2fd' }}>
                  GP Credit
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 600, backgroundColor: '#f3e5f5' }}>
                  SAP HANA<br />Generation (kWh)
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 600, backgroundColor: '#f3e5f5' }}>
                  SAP Credit
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 600, backgroundColor: '#fff3e0' }}>
                  Variance<br />(kWh)
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 600, backgroundColor: '#fff3e0' }}>
                  Variance<br />(%)
                </TableCell>
                <TableCell align="right" sx={{ fontWeight: 600, backgroundColor: '#fff3e0' }}>
                  $ Variance
                </TableCell>
                <TableCell align="center" sx={{ fontWeight: 600, backgroundColor: '#fafafa' }}>Status</TableCell>
                <TableCell align="center" sx={{ fontWeight: 600, backgroundColor: '#fafafa' }}>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {mockVarianceData.map((record) => (
                <TableRow
                  key={record.siteNumber}
                  hover
                  onClick={() => setSelectedRecord(record)}
                  sx={{
                    cursor: 'pointer',
                    backgroundColor: record.status === 'Major Discrepancy' ? '#ffebee' : 
                                    record.status === 'Minor Discrepancy' ? '#fffef0' : 'inherit',
                    '&:hover': {
                      backgroundColor: record.status === 'Major Discrepancy' ? '#ffcdd2' : 
                                      record.status === 'Minor Discrepancy' ? '#fff9c4' : '#f5f5f5',
                    },
                  }}
                >
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {record.siteNumber}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">
                      {record.plantName}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {record.pfSiteId}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                      {record.accountNumber}
                    </Typography>
                  </TableCell>
                  <TableCell align="center" sx={{ backgroundColor: '#f0f8f0' }}>
                    <Box>
                      <Chip
                        label={`${record.matchConfidence}%`}
                        size="small"
                        color={record.matchConfidence >= 95 ? 'success' : record.matchConfidence >= 80 ? 'warning' : 'error'}
                        icon={<LinkIcon />}
                      />
                      <Typography variant="caption" display="block" sx={{ mt: 0.5, fontSize: '0.7rem' }}>
                        {record.matchMethod}
                      </Typography>
                    </Box>
                  </TableCell>
                  <TableCell align="right" sx={{ backgroundColor: '#f5f9ff' }}>
                    <Typography variant="body2">
                      {formatNumber(record.georgiaPowerGeneration)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={{ backgroundColor: '#f5f9ff' }}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {formatCurrency(record.georgiaPowerCredit)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={{ backgroundColor: '#faf5ff' }}>
                    <Typography variant="body2">
                      {formatNumber(record.sapHanaGeneration)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={{ backgroundColor: '#faf5ff' }}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {formatCurrency(record.sapHanaCredit)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={{ backgroundColor: record.generationVariance !== 0 ? '#fff9e6' : 'inherit' }}>
                    <Typography
                      variant="body2"
                      sx={{
                        fontWeight: record.generationVariance !== 0 ? 600 : 400,
                        color: record.generationVariance !== 0 ? 'error.main' : 'inherit',
                      }}
                    >
                      {record.generationVariance > 0 ? '+' : ''}{formatNumber(record.generationVariance)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={{ backgroundColor: record.generationVariance !== 0 ? '#fff9e6' : 'inherit' }}>
                    <Typography
                      variant="body2"
                      sx={{
                        fontWeight: record.generationVariancePercent !== 0 ? 600 : 400,
                        color: record.generationVariancePercent > 1 ? 'error.main' : 
                               record.generationVariancePercent > 0 ? 'warning.main' : 'inherit',
                      }}
                    >
                      {record.generationVariancePercent > 0 ? '+' : ''}{formatNumber(record.generationVariancePercent)}%
                    </Typography>
                  </TableCell>
                  <TableCell align="right" sx={{ backgroundColor: record.creditVariance !== 0 ? '#fff9e6' : 'inherit' }}>
                    <Typography
                      variant="body2"
                      sx={{
                        fontWeight: record.creditVariance !== 0 ? 600 : 400,
                        color: record.creditVariance !== 0 ? 'error.main' : 'inherit',
                      }}
                    >
                      {record.creditVariance > 0 ? '+' : ''}{formatCurrency(Math.abs(record.creditVariance))}
                    </Typography>
                  </TableCell>
                  <TableCell align="center">
                    <Chip
                      label={record.status}
                      color={getStatusColor(record.status) as any}
                      size="small"
                      icon={record.status === 'Match' ? <CheckCircleIcon /> : <WarningIcon />}
                    />
                  </TableCell>
                  <TableCell align="center">
                    <Tooltip title="Correct Correlation" arrow>
                      <IconButton
                        size="small"
                        color="primary"
                        onClick={(e) => {
                          e.stopPropagation();
                          handleOpenCorrection(record);
                        }}
                      >
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      {/* Detail View */}
      {selectedRecord && (
        <Paper sx={{ p: 3, mb: 3, backgroundColor: '#fafafa' }}>
          <Typography variant="h6" gutterBottom>
            Variance Detail: {selectedRecord.plantName} ({selectedRecord.siteNumber})
          </Typography>
          <Divider sx={{ my: 2 }} />
          
          <Stack spacing={3}>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 3 }}>
              <Box sx={{ flex: '1 1 300px', minWidth: '300px' }}>
                <Card sx={{ backgroundColor: '#e3f2fd', height: '100%' }}>
                  <CardContent>
                    <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                      📄 Georgia Power Invoice Data (Offtaker Claimed)
                    </Typography>
                    <Typography variant="body2" sx={{ mt: 1 }}>
                      <strong>Account Number:</strong> {selectedRecord.accountNumber}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Generated kWh:</strong> {formatNumber(selectedRecord.georgiaPowerGeneration)}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Rate:</strong> ${formatNumber(selectedRecord.georgiaPowerRate)}/kWh
                    </Typography>
                    <Typography variant="body2" sx={{ mt: 1, fontWeight: 600 }}>
                      <strong>Generation Credit:</strong> {formatCurrency(selectedRecord.georgiaPowerCredit)}
                    </Typography>
                  </CardContent>
                </Card>
              </Box>

              <Box sx={{ flex: '1 1 300px', minWidth: '300px' }}>
                <Card sx={{ backgroundColor: '#f3e5f5', height: '100%' }}>
                  <CardContent>
                    <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                      💾 SAP HANA Data (Owner Actual)
                    </Typography>
                    <Typography variant="body2" sx={{ mt: 1 }}>
                      <strong>PF Site ID:</strong> {selectedRecord.pfSiteId}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Generated kWh:</strong> {formatNumber(selectedRecord.sapHanaGeneration)}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Rate:</strong> ${formatNumber(selectedRecord.sapHanaRate)}/kWh
                    </Typography>
                    <Typography variant="body2" sx={{ mt: 1, fontWeight: 600 }}>
                      <strong>Generation Credit:</strong> {formatCurrency(selectedRecord.sapHanaCredit)}
                    </Typography>
                  </CardContent>
                </Card>
              </Box>
            </Box>

            <Card sx={{ backgroundColor: selectedRecord.status === 'Match' ? '#e8f5e9' : '#fff3e0' }}>
              <CardContent>
                <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                  📊 Variance Analysis
                </Typography>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mt: 1 }}>
                  <Box sx={{ flex: '1 1 150px', minWidth: '150px' }}>
                    <Typography variant="body2">
                      <strong>Generation Variance:</strong>
                    </Typography>
                    <Typography
                      variant="h6"
                      sx={{ color: selectedRecord.generationVariance !== 0 ? 'error.main' : 'success.main' }}
                    >
                      {selectedRecord.generationVariance > 0 ? '+' : ''}{formatNumber(selectedRecord.generationVariance)} kWh
                    </Typography>
                  </Box>
                  <Box sx={{ flex: '1 1 150px', minWidth: '150px' }}>
                    <Typography variant="body2">
                      <strong>Variance Percentage:</strong>
                    </Typography>
                    <Typography
                      variant="h6"
                      sx={{ color: selectedRecord.generationVariancePercent > 1 ? 'error.main' : 
                                   selectedRecord.generationVariancePercent > 0 ? 'warning.main' : 'success.main' }}
                    >
                      {selectedRecord.generationVariancePercent > 0 ? '+' : ''}{formatNumber(selectedRecord.generationVariancePercent)}%
                    </Typography>
                  </Box>
                  <Box sx={{ flex: '1 1 150px', minWidth: '150px' }}>
                    <Typography variant="body2">
                      <strong>Credit Variance:</strong>
                    </Typography>
                    <Typography
                      variant="h6"
                      sx={{ color: selectedRecord.creditVariance !== 0 ? 'error.main' : 'success.main' }}
                    >
                      {selectedRecord.creditVariance > 0 ? '+' : ''}{formatCurrency(Math.abs(selectedRecord.creditVariance))}
                    </Typography>
                  </Box>
                </Box>

                <Alert
                  severity={selectedRecord.status === 'Match' ? 'success' : selectedRecord.status === 'Minor Discrepancy' ? 'warning' : 'error'}
                  sx={{ mt: 2 }}
                >
                  {selectedRecord.status === 'Match' && 'Perfect match - No action required. Ready for SAP posting.'}
                  {selectedRecord.status === 'Minor Discrepancy' && 'Minor discrepancy detected (<1%). May require review before SAP posting.'}
                  {selectedRecord.status === 'Major Discrepancy' && 'Major discrepancy detected (>1%). Requires investigation and resolution before SAP posting.'}
                </Alert>
              </CardContent>
            </Card>
          </Stack>
        </Paper>
      )}

      {/* Footer Info */}
      <Alert severity="info" sx={{ mt: 3 }}>
        <Typography variant="body2">
          <strong>How this works:</strong> The platform uses AI to extract generation amounts from Georgia Power utility invoices 
          that offtakers use for self-billing. These amounts are automatically compared against the energy company's SAP HANA meter readings. 
          Variances are highlighted for review, and validated data can be exported directly to SAP/Blueprints for accounts receivable posting.
        </Typography>
      </Alert>

      {/* Correlation Correction Dialog */}
      <Dialog 
        open={correctionDialogOpen} 
        onClose={handleCloseCorrection}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle sx={{ backgroundColor: '#f5f5f5', borderBottom: '2px solid #EE1C25' }}>
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Box display="flex" alignItems="center">
              <EditIcon sx={{ mr: 1, color: 'primary.main' }} />
              <Typography variant="h6">
                Correct Correlation Match
              </Typography>
            </Box>
            <IconButton onClick={handleCloseCorrection} size="small">
              <CloseIcon />
            </IconButton>
          </Box>
        </DialogTitle>
        
        <DialogContent sx={{ mt: 2 }}>
          <Alert severity="warning" sx={{ mb: 3 }}>
            <Typography variant="body2">
              🎭 <strong>MOCK INTERFACE:</strong> This demonstrates the correlation correction workflow. In production, 
              this would allow users to manually match Georgia Power invoices with SAP HANA records when auto-matching 
              fails or needs adjustment.
            </Typography>
          </Alert>

          {recordToCorrect && (
            <Stack spacing={3}>
              {/* Georgia Power Invoice Info */}
              <Card sx={{ backgroundColor: '#e3f2fd' }}>
                <CardContent>
                  <Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 600 }}>
                    📄 Georgia Power Invoice (Source Data)
                  </Typography>
                  <Box sx={{ mt: 1 }}>
                    <Typography variant="body2">
                      <strong>Plant:</strong> {recordToCorrect.plantName} ({recordToCorrect.siteNumber})
                    </Typography>
                    <Typography variant="body2">
                      <strong>Account Number:</strong> {recordToCorrect.accountNumber}
                    </Typography>
                    <Typography variant="body2">
                      <strong>Generation:</strong> {formatNumber(recordToCorrect.georgiaPowerGeneration)} kWh
                    </Typography>
                    <Typography variant="body2">
                      <strong>Period:</strong> {recordToCorrect.period}
                    </Typography>
                  </Box>
                </CardContent>
              </Card>

              {/* Current Match */}
              <Card sx={{ backgroundColor: '#fff3e0' }}>
                <CardContent>
                  <Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 600 }}>
                    🔗 Current SAP HANA Match
                  </Typography>
                  <Box sx={{ mt: 1 }}>
                    <Box display="flex" alignItems="center" gap={1} mb={1}>
                      <Chip
                        label={`${recordToCorrect.matchConfidence}% Confidence`}
                        size="small"
                        color={recordToCorrect.matchConfidence >= 95 ? 'success' : recordToCorrect.matchConfidence >= 80 ? 'warning' : 'error'}
                      />
                      <Chip
                        label={recordToCorrect.matchMethod}
                        size="small"
                        variant="outlined"
                      />
                    </Box>
                    <Typography variant="body2">
                      <strong>PF Site ID:</strong> {recordToCorrect.pfSiteId}
                    </Typography>
                    <Typography variant="body2">
                      <strong>SAP Generation:</strong> {formatNumber(recordToCorrect.sapHanaGeneration)} kWh
                    </Typography>
                    {recordToCorrect.generationVariance !== 0 && (
                      <Alert severity="warning" sx={{ mt: 1 }}>
                        <Typography variant="caption">
                          <strong>Variance:</strong> {formatNumber(recordToCorrect.generationVariance)} kWh 
                          ({formatNumber(recordToCorrect.generationVariancePercent)}%)
                        </Typography>
                      </Alert>
                    )}
                  </Box>
                </CardContent>
              </Card>

              {/* Select New SAP Record */}
              <FormControl fullWidth>
                <InputLabel>Select Correct SAP HANA Record</InputLabel>
                <Select
                  value={selectedSapRecord}
                  onChange={(e) => setSelectedSapRecord(e.target.value)}
                  label="Select Correct SAP HANA Record"
                >
                  {availableSapRecords.map((sapRecord) => (
                    <MenuItem key={sapRecord} value={sapRecord}>
                      {sapRecord}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>

              {/* Correction Comment */}
              <TextField
                fullWidth
                multiline
                rows={4}
                label="Correction Comment (Required)"
                placeholder="Explain why you're changing this correlation match. Include any supporting information or references..."
                value={correctionComment}
                onChange={(e) => setCorrectionComment(e.target.value)}
                required
                helperText="This comment will be included in the audit trail and may be required for approval."
              />

              {/* Workflow Info */}
              <Alert severity="info">
                <Typography variant="body2" gutterBottom>
                  <strong>What happens next:</strong>
                </Typography>
                <Typography variant="caption" component="div">
                  1. Correlation will be updated with your selected SAP record<br />
                  2. Variances will be recalculated automatically<br />
                  3. Change logged with your user ID and timestamp<br />
                  4. If variance exceeds threshold (&gt;1%), approval workflow triggered<br />
                  5. Approvers notified for review before SAP posting
                </Typography>
              </Alert>
            </Stack>
          )}
        </DialogContent>

        <DialogActions sx={{ p: 2, backgroundColor: '#f5f5f5', borderTop: '1px solid #e0e0e0' }}>
          <Button onClick={handleCloseCorrection} color="inherit">
            Cancel
          </Button>
          <Button 
            onClick={handleSubmitCorrection} 
            variant="contained" 
            color="primary"
            disabled={!correctionComment.trim() || !selectedSapRecord}
            startIcon={<CheckCircleIcon />}
          >
            Submit Correction
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default VarianceAnalysis;

