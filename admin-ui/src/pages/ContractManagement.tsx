import React, { useState } from 'react';
import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Chip,
  IconButton,
  Typography,
  TextField,
  InputAdornment,
  Button,
  Stack
} from '@mui/material';
import {
  Visibility as ViewIcon,
  Search as SearchIcon,
  FileDownload as DownloadIcon,
  FilterList as FilterIcon
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { ContractGridRow } from '../types/contractReview';
import { mockContractReviews } from '../mockData/contractReviewMockData';

const ContractManagement: React.FC = () => {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [searchTerm, setSearchTerm] = useState('');

  // Transform mock data to grid rows
  const gridData: ContractGridRow[] = mockContractReviews.map(contract => {
    const totalRates = contract.rateSchedules?.reduce((sum, s) => sum + s.rates.length, 0) || 0;
    const energyRate = contract.rateSchedules
      ?.flatMap(s => s.rates)
      .find(r => r.rateType === 'Energy')?.unitPrice;
    const demandRate = contract.rateSchedules
      ?.flatMap(s => s.rates)
      .find(r => r.rateType === 'Demand')?.unitPrice;
    
    return {
      id: contract.id!,
      contractName: contract.metadata.contractName,
      fundName: contract.metadata.fundName,
      projectName: contract.metadata.projectName,
      contractType: contract.metadata.contractType,
      codDate: contract.metadata.codDate,
      technologies: contract.metadata.technologies,
      totalObligations: contract.summary.totalObligationsExtracted,
      totalMissing: contract.summary.totalMissingObligations,
      totalRisks: contract.summary.totalRisksUnclear,
      totalRates,
      primaryEnergyRate: energyRate,
      primaryDemandRate: demandRate,
      createdAt: contract.createdAt,
      processedBy: contract.processedBy
    };
  });

  // Filter data based on search
  const filteredData = gridData.filter(row =>
    row.contractName.toLowerCase().includes(searchTerm.toLowerCase()) ||
    row.projectName.toLowerCase().includes(searchTerm.toLowerCase()) ||
    row.fundName.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const handleChangePage = (_event: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const handleViewContract = (contractId: number) => {
    navigate(`/contracts/${contractId}`);
  };

  const getSeverityColor = (missing: number, risks: number): 'success' | 'warning' | 'error' => {
    if (missing === 0 && risks < 5) return 'success';
    if (missing <= 3 && risks < 10) return 'warning';
    return 'error';
  };

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  };

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" gutterBottom>
          Contract Review Database
        </Typography>
        <Typography variant="body2" color="text.secondary">
          AI-extracted contract obligations for invoice variance assessment
        </Typography>
      </Box>

      {/* Toolbar */}
      <Stack direction="row" spacing={2} sx={{ mb: 3 }}>
        <TextField
          placeholder="Search contracts, projects, or funds..."
          variant="outlined"
          size="small"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          sx={{ flexGrow: 1 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon />
              </InputAdornment>
            ),
          }}
        />
        <Button
          variant="outlined"
          startIcon={<FilterIcon />}
        >
          Filters
        </Button>
        <Button
          variant="outlined"
          startIcon={<DownloadIcon />}
        >
          Export
        </Button>
      </Stack>

      {/* Data Grid */}
      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
              <TableCell><strong>Contract Name</strong></TableCell>
              <TableCell><strong>Project</strong></TableCell>
              <TableCell><strong>Type</strong></TableCell>
              <TableCell><strong>COD Date</strong></TableCell>
              <TableCell><strong>Tech</strong></TableCell>
              <TableCell align="right"><strong>Energy Rate</strong></TableCell>
              <TableCell align="right"><strong>Demand Rate</strong></TableCell>
              <TableCell align="center"><strong>Rates</strong></TableCell>
              <TableCell align="center"><strong>Obligations</strong></TableCell>
              <TableCell align="center"><strong>Missing</strong></TableCell>
              <TableCell align="center"><strong>Risks</strong></TableCell>
              <TableCell align="center"><strong>Status</strong></TableCell>
              <TableCell align="center"><strong>Actions</strong></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {filteredData
              .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
              .map((row) => (
                <TableRow
                  key={row.id}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => handleViewContract(row.id)}
                >
                  <TableCell>
                    <Typography variant="body2" fontWeight={500}>
                      {row.contractName}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" noWrap sx={{ maxWidth: 200 }}>
                      {row.projectName}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={row.contractType.includes('PPA') ? 'PPA' : 'O&M'}
                      size="small"
                      color={row.contractType.includes('PPA') ? 'primary' : 'default'}
                    />
                  </TableCell>
                  <TableCell>
                    {row.codDate || '-'}
                  </TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={0.5}>
                      {row.technologies?.map((tech) => (
                        <Chip
                          key={tech}
                          label={tech}
                          size="small"
                          variant="outlined"
                        />
                      ))}
                    </Stack>
                  </TableCell>
                  <TableCell align="right">
                    {row.primaryEnergyRate ? (
                      <Typography variant="body2" fontWeight={600} color="success.main">
                        ${row.primaryEnergyRate.toFixed(4)}/kWh
                      </Typography>
                    ) : (
                      <Typography variant="caption" color="text.secondary">-</Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {row.primaryDemandRate ? (
                      <Typography variant="body2" fontWeight={600} color="info.main">
                        ${row.primaryDemandRate.toFixed(2)}/kW
                      </Typography>
                    ) : (
                      <Typography variant="caption" color="text.secondary">-</Typography>
                    )}
                  </TableCell>
                  <TableCell align="center">
                    <Chip
                      label={row.totalRates}
                      size="small"
                      color={row.totalRates > 0 ? 'success' : 'error'}
                    />
                  </TableCell>
                  <TableCell align="center">
                    <Chip
                      label={row.totalObligations}
                      size="small"
                      color="info"
                    />
                  </TableCell>
                  <TableCell align="center">
                    <Chip
                      label={row.totalMissing}
                      size="small"
                      color={row.totalMissing > 5 ? 'error' : row.totalMissing > 0 ? 'warning' : 'success'}
                    />
                  </TableCell>
                  <TableCell align="center">
                    <Chip
                      label={row.totalRisks}
                      size="small"
                      color={row.totalRisks > 10 ? 'error' : row.totalRisks > 5 ? 'warning' : 'default'}
                    />
                  </TableCell>
                  <TableCell align="center">
                    <Chip
                      label={getSeverityColor(row.totalMissing, row.totalRisks) === 'success' ? 'Good' : 
                           getSeverityColor(row.totalMissing, row.totalRisks) === 'warning' ? 'Review' : 'Critical'}
                      size="small"
                      color={getSeverityColor(row.totalMissing, row.totalRisks)}
                    />
                  </TableCell>
                  <TableCell align="center">
                    <IconButton
                      size="small"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleViewContract(row.id);
                      }}
                    >
                      <ViewIcon />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
          </TableBody>
        </Table>
        <TablePagination
          rowsPerPageOptions={[5, 10, 25, 50]}
          component="div"
          count={filteredData.length}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={handleChangePage}
          onRowsPerPageChange={handleChangeRowsPerPage}
        />
      </TableContainer>

      {/* Summary Stats */}
      <Box sx={{ mt: 3, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <Paper sx={{ p: 2, flex: '1 1 calc(16.66% - 12px)', minWidth: 140 }}>
          <Typography variant="h3" color="primary">
            {filteredData.length}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Contracts
          </Typography>
        </Paper>
        <Paper sx={{ p: 2, flex: '1 1 calc(16.66% - 12px)', minWidth: 140 }}>
          <Typography variant="h3" color="success.main">
            {filteredData.reduce((sum, row) => sum + row.totalRates, 0)}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Rate Schedules
          </Typography>
        </Paper>
        <Paper sx={{ p: 2, flex: '1 1 calc(16.66% - 12px)', minWidth: 140 }}>
          <Typography variant="h4" color="success.main">
            ${(filteredData
              .filter(row => row.primaryEnergyRate)
              .reduce((sum, row) => sum + (row.primaryEnergyRate || 0), 0) / 
              filteredData.filter(row => row.primaryEnergyRate).length || 0
            ).toFixed(4)}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Avg Energy Rate
          </Typography>
        </Paper>
        <Paper sx={{ p: 2, flex: '1 1 calc(16.66% - 12px)', minWidth: 140 }}>
          <Typography variant="h3" color="info.main">
            {filteredData.reduce((sum, row) => sum + row.totalObligations, 0)}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Obligations
          </Typography>
        </Paper>
        <Paper sx={{ p: 2, flex: '1 1 calc(16.66% - 12px)', minWidth: 140 }}>
          <Typography variant="h3" color="warning.main">
            {filteredData.reduce((sum, row) => sum + row.totalMissing, 0)}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Missing
          </Typography>
        </Paper>
        <Paper sx={{ p: 2, flex: '1 1 calc(16.66% - 12px)', minWidth: 140 }}>
          <Typography variant="h3" color="error.main">
            {filteredData.reduce((sum, row) => sum + row.totalRisks, 0)}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Risks
          </Typography>
        </Paper>
      </Box>
    </Box>
  );
};

export default ContractManagement;

