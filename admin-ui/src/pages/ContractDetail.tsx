import React from 'react';
import {
  Box,
  Paper,
  Typography,
  Tabs,
  Tab,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Card,
  CardContent,
  Divider,
  IconButton,
  Button,
  Stack,
  Alert
} from '@mui/material';
import {
  ArrowBack as BackIcon,
  Download as DownloadIcon,
  Warning as WarningIcon,
  CheckCircle as CheckIcon,
  Error as ErrorIcon
} from '@mui/icons-material';
import { useParams, useNavigate } from 'react-router-dom';
import { mockContractReviews } from '../mockData/contractReviewMockData';
import { ContractReviewData } from '../types/contractReview';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`contract-tabpanel-${index}`}
      aria-labelledby={`contract-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
}

const ContractReviewDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [currentTab, setCurrentTab] = React.useState(0);

  const contract: ContractReviewData | undefined = mockContractReviews.find(
    (c) => c.id === parseInt(id || '0')
  );

  if (!contract) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">Contract not found</Alert>
      </Box>
    );
  }

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setCurrentTab(newValue);
  };

  const getCategoryColor = (category: string) => {
    const colors: Record<string, 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info'> = {
      'Reporting': 'info',
      'Deliverable': 'primary',
      'Performance': 'success',
      'Financial': 'warning',
      'Regulatory': 'error',
      'Insurance': 'secondary',
      'Maintenance': 'info',
      'Testing': 'primary',
      'Documentation': 'default' as any,
      'Compliance': 'success',
      'Other': 'default' as any
    };
    return colors[category] || 'default';
  };

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Box sx={{ mb: 3 }}>
        <Button
          startIcon={<BackIcon />}
          onClick={() => navigate('/contracts')}
          sx={{ mb: 2 }}
        >
          Back to Contracts
        </Button>
        
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
          <Box>
            <Typography variant="h4" gutterBottom>
              {contract.metadata.contractName}
            </Typography>
            <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
              {contract.metadata.technologies?.map((tech) => (
                <Chip key={tech} label={tech} color="primary" size="small" />
              ))}
              <Chip label={contract.metadata.contractType} variant="outlined" size="small" />
              {contract.metadata.codDate && (
                <Chip label={`COD: ${contract.metadata.codDate}`} variant="outlined" size="small" />
              )}
            </Stack>
          </Box>
          <Button
            variant="outlined"
            startIcon={<DownloadIcon />}
          >
            Export Excel
          </Button>
        </Box>
      </Box>

      {/* Summary Cards */}
      <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
        <Card sx={{ flex: '1 1 calc(25% - 12px)', minWidth: 200 }}>
          <CardContent>
            <Typography color="text.secondary" gutterBottom>
              Total Obligations
            </Typography>
            <Typography variant="h3" color="info.main">
              {contract.summary.totalObligationsExtracted}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Pre-COD: {contract.extraction.preCodObligations.length} | 
              Post-COD: {contract.extraction.postCodObligations.length}
            </Typography>
          </CardContent>
        </Card>
        <Card sx={{ flex: '1 1 calc(25% - 12px)', minWidth: 200 }}>
          <CardContent>
            <Typography color="text.secondary" gutterBottom>
              Missing Obligations
            </Typography>
            <Typography variant="h3" color="warning.main">
              {contract.summary.totalMissingObligations}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Requires review
            </Typography>
          </CardContent>
        </Card>
        <Card sx={{ flex: '1 1 calc(25% - 12px)', minWidth: 200 }}>
          <CardContent>
            <Typography color="text.secondary" gutterBottom>
              Risks & Unclear
            </Typography>
            <Typography variant="h3" color="error.main">
              {contract.summary.totalRisksUnclear}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Legal review needed
            </Typography>
          </CardContent>
        </Card>
        <Card sx={{ flex: '1 1 calc(25% - 12px)', minWidth: 200 }}>
          <CardContent>
            <Typography color="text.secondary" gutterBottom>
              Assumptions
            </Typography>
            <Typography variant="h3" color="default">
              {contract.assumptionsLog.length}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Pending: {contract.assumptionsLog.filter(a => a.status === 'Pending Review').length}
            </Typography>
          </CardContent>
        </Card>
      </Box>

      {/* Self-Check Alert */}
      {contract.summary.selfCheckAnswer && (
        <Alert
          severity={contract.summary.selfCheckAnswer === 'Yes' ? 'warning' : 'success'}
          icon={contract.summary.selfCheckAnswer === 'Yes' ? <WarningIcon /> : <CheckIcon />}
          sx={{ mb: 3 }}
        >
          <Typography variant="body2" fontWeight={600}>
            {contract.summary.selfCheckQuestion} {contract.summary.selfCheckAnswer}
          </Typography>
          <Typography variant="body2">
            {contract.summary.selfCheckExplanation}
          </Typography>
        </Alert>
      )}

      {/* Metadata Section */}
      <Paper sx={{ mb: 3, p: 2 }}>
        <Typography variant="h6" gutterBottom>
          Contract Metadata
        </Typography>
        <Divider sx={{ mb: 2 }} />
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
          <Box>
            <Typography variant="caption" color="text.secondary">Fund Name</Typography>
            <Typography variant="body1">{contract.metadata.fundName}</Typography>
          </Box>
          <Box>
            <Typography variant="caption" color="text.secondary">Project Name</Typography>
            <Typography variant="body1">{contract.metadata.projectName}</Typography>
          </Box>
          <Box>
            <Typography variant="caption" color="text.secondary">Contract Type</Typography>
            <Typography variant="body1">{contract.metadata.contractType}</Typography>
          </Box>
          <Box>
            <Typography variant="caption" color="text.secondary">Commercial Operation Date</Typography>
            <Typography variant="body1">{contract.metadata.codDate || 'Not specified'}</Typography>
          </Box>
        </Box>
      </Paper>

      {/* Tabs */}
      <Paper>
        <Tabs
          value={currentTab}
          onChange={handleTabChange}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab label={`Rate Schedules (${contract.rateSchedules?.reduce((sum, s) => sum + s.rates.length, 0) || 0})`} />
          <Tab label={`Obligations (${contract.summary.totalObligationsExtracted})`} />
          <Tab label={`Missing (${contract.summary.totalMissingObligations})`} />
          <Tab label={`Risks (${contract.summary.totalRisksUnclear})`} />
          <Tab label={`Assumptions (${contract.assumptionsLog.length})`} />
        </Tabs>

        {/* Tab 0: Rate Schedules */}
        <TabPanel value={currentTab} index={0}>
          <Box sx={{ p: 2 }}>
            {contract.rateSchedules && contract.rateSchedules.length > 0 ? (
              contract.rateSchedules.map((schedule, idx) => (
                <Box key={idx} sx={{ mb: 4 }}>
                  <Typography variant="h6" gutterBottom color="primary">
                    {schedule.scheduleName}
                  </Typography>
                  {schedule.accountNumbers && schedule.accountNumbers.length > 0 && (
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                      Applies to accounts: {schedule.accountNumbers.join(', ')}
                    </Typography>
                  )}
                  <TableContainer>
                    <Table size="small">
                      <TableHead>
                        <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                          <TableCell><strong>ID</strong></TableCell>
                          <TableCell><strong>Type</strong></TableCell>
                          <TableCell><strong>Description</strong></TableCell>
                          <TableCell align="right"><strong>Unit Price</strong></TableCell>
                          <TableCell><strong>Unit</strong></TableCell>
                          <TableCell><strong>Time of Use</strong></TableCell>
                          <TableCell><strong>Effective Date</strong></TableCell>
                          <TableCell><strong>Clause</strong></TableCell>
                          <TableCell><strong>Notes</strong></TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {schedule.rates.map((rate) => (
                          <TableRow key={rate.rateId} hover>
                            <TableCell>{rate.rateId}</TableCell>
                            <TableCell>
                              <Chip
                                label={rate.rateType}
                                size="small"
                                color={
                                  rate.rateType === 'Energy' ? 'primary' :
                                  rate.rateType === 'Demand' ? 'secondary' :
                                  rate.rateType === 'Fixed' ? 'success' : 'default'
                                }
                              />
                            </TableCell>
                            <TableCell>
                              <Typography variant="body2" fontWeight={500}>
                                {rate.description}
                              </Typography>
                            </TableCell>
                            <TableCell align="right">
                              <Typography variant="body2" fontWeight={600} color="success.main">
                                ${rate.unitPrice.toFixed(rate.unitPrice < 1 ? 4 : 2)}
                              </Typography>
                            </TableCell>
                            <TableCell>{rate.unitOfMeasure}</TableCell>
                            <TableCell>{rate.timeOfUse || '-'}</TableCell>
                            <TableCell>{rate.effectiveDate || '-'}</TableCell>
                            <TableCell>
                              <Typography variant="caption">
                                {rate.clauseReference || '-'}
                              </Typography>
                            </TableCell>
                            <TableCell>
                              <Typography variant="caption" color="text.secondary">
                                {rate.notes || '-'}
                              </Typography>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </Box>
              ))
            ) : (
              <Alert severity="warning">
                No rate schedules extracted. Rate data is required for invoice variance assessment.
              </Alert>
            )}
          </Box>
        </TabPanel>

        {/* Tab 1: Obligations */}
        <TabPanel value={currentTab} index={1}>
          <Box sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom color="primary">
              Pre-COD Obligations ({contract.extraction.preCodObligations.length})
            </Typography>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                    <TableCell><strong>ID</strong></TableCell>
                    <TableCell><strong>Category</strong></TableCell>
                    <TableCell><strong>Description</strong></TableCell>
                    <TableCell><strong>Clause</strong></TableCell>
                    <TableCell><strong>Responsible</strong></TableCell>
                    <TableCell><strong>Due Date</strong></TableCell>
                    <TableCell><strong>Frequency</strong></TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {contract.extraction.preCodObligations.map((obl) => (
                    <TableRow key={obl.ppaId} hover>
                      <TableCell>{obl.ppaId}</TableCell>
                      <TableCell>
                        <Chip
                          label={obl.obligationCategory}
                          size="small"
                          color={getCategoryColor(obl.obligationCategory)}
                        />
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" fontWeight={500}>
                          {obl.obligationDescription}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {obl.task}
                        </Typography>
                      </TableCell>
                      <TableCell>{obl.clauseReference}</TableCell>
                      <TableCell>{obl.responsibleParty}</TableCell>
                      <TableCell>{obl.firstTaskDueDate || '-'}</TableCell>
                      <TableCell>{obl.obligationFrequency || '-'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>

            <Typography variant="h6" gutterBottom color="success.main" sx={{ mt: 4 }}>
              Post-COD Obligations ({contract.extraction.postCodObligations.length})
            </Typography>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                    <TableCell><strong>ID</strong></TableCell>
                    <TableCell><strong>Category</strong></TableCell>
                    <TableCell><strong>Description</strong></TableCell>
                    <TableCell><strong>Clause</strong></TableCell>
                    <TableCell><strong>Responsible</strong></TableCell>
                    <TableCell><strong>Frequency</strong></TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {contract.extraction.postCodObligations.map((obl) => (
                    <TableRow key={obl.ppaId} hover>
                      <TableCell>{obl.ppaId}</TableCell>
                      <TableCell>
                        <Chip
                          label={obl.obligationCategory}
                          size="small"
                          color={getCategoryColor(obl.obligationCategory)}
                        />
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" fontWeight={500}>
                          {obl.obligationDescription}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {obl.task}
                        </Typography>
                      </TableCell>
                      <TableCell>{obl.clauseReference}</TableCell>
                      <TableCell>{obl.responsibleParty}</TableCell>
                      <TableCell>{obl.obligationFrequency || '-'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        </TabPanel>

        {/* Tab 2: Missing Obligations */}
        <TabPanel value={currentTab} index={2}>
          <Box sx={{ p: 2 }}>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                    <TableCell><strong>ID</strong></TableCell>
                    <TableCell><strong>Missing Obligation</strong></TableCell>
                    <TableCell><strong>Reference</strong></TableCell>
                    <TableCell><strong>Severity</strong></TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {contract.obligationsNotFound?.map((missing) => (
                    <TableRow key={missing.ppaId}>
                      <TableCell>{missing.ppaId}</TableCell>
                      <TableCell>{missing.missingObligation}</TableCell>
                      <TableCell>{missing.reference || '-'}</TableCell>
                      <TableCell>
                        <Chip
                          label={missing.severity}
                          size="small"
                          color={missing.severity === 'High' ? 'error' : missing.severity === 'Medium' ? 'warning' : 'default'}
                          icon={missing.severity === 'High' ? <ErrorIcon /> : <WarningIcon />}
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        </TabPanel>

        {/* Tab 3: Risks & Unclear */}
        <TabPanel value={currentTab} index={3}>
          <Box sx={{ p: 2 }}>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                    <TableCell><strong>ID</strong></TableCell>
                    <TableCell><strong>Text Excerpt</strong></TableCell>
                    <TableCell><strong>Issue Type</strong></TableCell>
                    <TableCell><strong>Comment</strong></TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {contract.risksUnclear?.map((risk) => (
                    <TableRow key={risk.ppaId}>
                      <TableCell>{risk.ppaId}</TableCell>
                      <TableCell>
                        <Typography variant="body2" fontStyle="italic">
                          "{risk.textExcerpt}"
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={risk.issueType}
                          size="small"
                          color={risk.issueType === 'Scope Gap' ? 'error' : 'warning'}
                        />
                      </TableCell>
                      <TableCell>{risk.comment || '-'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        </TabPanel>

        {/* Tab 4: Assumptions Log */}
        <TabPanel value={currentTab} index={4}>
          <Box sx={{ p: 2 }}>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                    <TableCell><strong>ID</strong></TableCell>
                    <TableCell><strong>Trigger</strong></TableCell>
                    <TableCell><strong>Proposed Assumption</strong></TableCell>
                    <TableCell><strong>Status</strong></TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {contract.assumptionsLog.map((assumption) => (
                    <TableRow key={assumption.logId}>
                      <TableCell>{assumption.logId}</TableCell>
                      <TableCell>{assumption.trigger}</TableCell>
                      <TableCell>{assumption.proposedAssumption}</TableCell>
                      <TableCell>
                        <Chip
                          label={assumption.status}
                          size="small"
                          color={
                            assumption.status === 'Approved' ? 'success' :
                            assumption.status === 'Rejected' ? 'error' :
                            assumption.status === 'Clarified' ? 'info' : 'warning'
                          }
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        </TabPanel>
      </Paper>
    </Box>
  );
};

export default ContractReviewDetail;

