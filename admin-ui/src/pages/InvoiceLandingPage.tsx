import React, { useState } from 'react';
import {
  Box,
  Container,
  Typography,
  Button,
  Card,
  CardContent,
  Stack,
  Chip,
  Avatar,
  Paper,
  Divider,
} from '@mui/material';
import {
  Receipt,
  Psychology,
  Speed,
  Security,
  CheckCircle,
  Visibility,
  AutoAwesome,
  Description,
  Transform,
  Api,
  DataObject,
  Storage,
  Email,
  Code,
  VerifiedUser,
  TrendingUp,
  Gavel,
  CloudUpload,
  SmartToy,
  Cable,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { config } from '../config/environment';

const InvoiceLandingPage: React.FC = () => {
  const navigate = useNavigate();

  const features = [
    {
      icon: <Psychology />,
      title: 'AI Vision for Invoices',
      description: 'Multi-modal AI that can "see" scanned invoices just like humans - capturing line items, tables, totals, and complex layouts that traditional OCR misses.',
    },
    {
      icon: <Receipt />,
      title: 'Structured Invoice Data',
      description: 'Extract vendor details, invoice numbers, dates, line items, taxes, and totals into clean, validated JSON ready for your accounting systems.',
    },
    {
      icon: <Code />,
      title: 'Zero-Error JSON Validation',
      description: 'IDE-quality LSP integration ensures every extracted invoice matches your database schema perfectly - no cleanup, no post-processing.',
    },
    {
      icon: <Api />,
      title: 'Direct QuickBooks Integration',
      description: 'Extracted invoice data flows directly into QuickBooks, Xero, NetSuite, or your custom ERP via our extensible MCP framework.',
    },
    {
      icon: <Gavel />,
      title: 'Contract Language Processing',
      description: 'Beyond invoices - extract key terms, dates, parties, and obligations from legal contracts and agreements buried in PDFs.',
    },
    {
      icon: <Speed />,
      title: 'Real-Time Processing',
      description: 'Watch your invoices process in real-time with live progress tracking as AI analyzes each page and extracts structured data.',
    },
    {
      icon: <Security />,
      title: 'Multi-Tenant Security',
      description: 'Enterprise-grade tenant isolation ensures your invoices and financial data remain completely secure and separate.',
    },
    {
      icon: <AutoAwesome />,
      title: 'Extensible by Design',
      description: 'Built on LLM-OCR, our platform can handle any document type - invoices today, contracts tomorrow, medical records next week.',
    },
  ];

  const benefits = [
    {
      icon: <CheckCircle color="success" />,
      title: 'Eliminate Manual Data Entry',
      description: 'AI reads scanned invoices and automatically extracts all fields - vendor, amount, line items, taxes - directly into your systems.',
    },
    {
      icon: <CheckCircle color="success" />,
      title: 'Handle Complex Invoice Formats',
      description: 'Multi-modal vision understands tables, multi-column layouts, and visual structures that defeat traditional OCR.',
    },
    {
      icon: <CheckCircle color="success" />,
      title: 'Process Legal Documents Too',
      description: 'Same AI that reads invoices can extract key terms from contracts, leases, and agreements - natural language buried in PDFs.',
    },
    {
      icon: <CheckCircle color="success" />,
      title: 'Your Data, Your Control',
      description: 'Use local Ollama models for privacy or your own OpenAI keys. Connect to your own databases via MCP. Complete control.',
    },
    {
      icon: <CheckCircle color="success" />,
      title: 'Built on LLM-OCR Foundation',
      description: 'Powered by our proven multi-modal document processing platform - invoices are just the beginning.',
    },
  ];

  const stats = [
    { number: '99.9%', label: 'Extraction Accuracy', subtext: 'AI-validated data' },
    { number: '<5 min', label: 'Processing Time', subtext: 'Per invoice batch' },
    { number: 'Real-Time', label: 'Progress Updates', subtext: 'Live status tracking' },
    { number: '100%', label: 'Data Isolation', subtext: 'Multi-tenant security' },
  ];

  return (
    <Box>
      {/* Hero Section */}
      <Box
        sx={{
          background: 'linear-gradient(135deg, #42a5f5 0%, #2196f3 100%)',
          color: 'white',
          py: 12,
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        <Container maxWidth="md">
          <Stack spacing={4} alignItems="center" textAlign="center">
            <Box>
              <Box
                component="img"
                src={`${process.env.PUBLIC_URL}/image.png`}
                alt="PaperIQ.ai Invoice Intelligence Platform"
                sx={{
                  height: 250,
                  width: 'auto',
                  filter: 'drop-shadow(0 4px 8px rgba(0,0,0,0.3))',
                }}
              />
            </Box>
            <Box>
              <Typography
                variant="h3"
                sx={{
                  fontSize: { xs: '2rem', md: '2.6rem' },
                  fontWeight: 900,
                  letterSpacing: '0.01em',
                  lineHeight: 1.1,
                }}
              >
                PaperIQ.ai
              </Typography>
              <Typography
                variant="h1"
                sx={{
                  fontSize: { xs: '2.1rem', md: '3.2rem' },
                  fontWeight: 900,
                  letterSpacing: '0.005em',
                  lineHeight: 1.08,
                }}
              >
                Invoice Intelligence Platform
              </Typography>
            </Box>

            <Chip
              icon={<Receipt />}
              label="AI-Powered Invoice Intelligence"
              sx={{
                backgroundColor: 'rgba(255,255,255,0.2)',
                color: 'white',
                fontSize: '1rem',
                py: 2.5,
              }}
            />
            
            <Typography variant="h5" sx={{ opacity: 0.9, lineHeight: 1.6, maxWidth: 800, mx: 'auto' }}>
              Multi-modal AI vision reads scanned invoices like humans do - understanding tables, layouts, and complex formats. 
              Extract structured data directly into QuickBooks, ERPs, and custom systems. Powered by{' '}
              <Typography
                component="span"
                sx={{
                  color: '#FFD700',
                  fontWeight: 'bold',
                  fontSize: 'inherit',
                }}
              >
                LLM-OCR
              </Typography>
              , extensible to contracts and legal documents.
            </Typography>
            
            {/* Architecture Diagram - Invoice Specific */}
            <Box
              sx={{
                width: '100%',
                maxWidth: 1200,
                mx: 'auto',
                px: { xs: 2, sm: 0 },
              }}
            >
              <Box
                component="img"
                src={`${process.env.PUBLIC_URL}/invoice-processing-diagram.png`}
                alt="Invoice Processing Architecture - From PDF to Database"
                sx={{
                  width: '100%',
                  maxWidth: '100%',
                  height: 'auto',
                  borderRadius: 3,
                  boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
                  border: '3px solid rgba(255,255,255,0.3)',
                  display: 'block',
                  objectFit: 'contain',
                  backgroundColor: 'rgba(255,255,255,0.05)',
                }}
              />
            </Box>
            
            <Paper
              sx={{
                p: 3,
                backgroundColor: 'rgba(255,255,255,0.1)',
                backdropFilter: 'blur(10px)',
                border: '1px solid rgba(255,255,255,0.2)',
                maxWidth: 600,
                mx: 'auto',
              }}
            >
              <Typography variant="h6" gutterBottom sx={{ color: '#FFD700', fontWeight: 'bold' }}>
                💡 Built on LLM-OCR, Specialized for Finance
              </Typography>
              <Typography variant="body1" sx={{ opacity: 0.95, lineHeight: 1.6 }}>
                <strong>Scanned invoices are visual documents.</strong> Traditional OCR fails on complex layouts, multi-column tables, 
                and varied formats. Our multi-modal AI <strong>sees the invoice</strong> just like you do, understanding spatial 
                relationships and visual structure. <strong>Beyond invoices:</strong> Same technology extracts natural legal language 
                from contracts, leases, and agreements.
              </Typography>
            </Paper>
            
            <Stack 
              direction="row" 
              spacing={2} 
              justifyContent="center" 
              flexWrap="wrap"
              sx={{ gap: 2 }}
            >
              <Button
                variant="contained"
                size="large"
                sx={{
                  backgroundColor: 'white',
                  color: 'primary.main',
                  px: 4,
                  py: 1.5,
                  minWidth: '200px',
                  '&:hover': {
                    backgroundColor: 'rgba(255,255,255,0.9)',
                  },
                }}
                onClick={() => navigate('/register')}
              >
                Get Started Free
              </Button>
              <Button
                variant="outlined"
                size="large"
                sx={{
                  borderColor: 'white',
                  color: 'white',
                  px: 4,
                  py: 1.5,
                  minWidth: '200px',
                  '&:hover': {
                    borderColor: 'white',
                    backgroundColor: 'rgba(255,255,255,0.1)',
                  },
                }}
                onClick={() => navigate('/login')}
              >
                Sign In
              </Button>
              <Button
                variant="contained"
                size="large"
                startIcon={<Email />}
                sx={{
                  backgroundColor: '#FFD700',
                  color: '#1976d2',
                  px: 4,
                  py: 1.5,
                  minWidth: '200px',
                  fontWeight: 'bold',
                  '&:hover': {
                    backgroundColor: '#FFA500',
                  },
                }}
                onClick={() => window.open('mailto:support@paperiq.ai', '_blank')}
              >
                Contact Us
              </Button>
            </Stack>
          </Stack>
        </Container>
      </Box>

      {/* Stats Section */}
      <Container maxWidth="lg" sx={{ py: 8 }}>
        <Box display="flex" gap={2} flexWrap="wrap">
          {stats.map((stat, index) => (
            <Card key={index} sx={{ textAlign: 'center', p: 3, flex: 1, minWidth: 200 }}>
              <Typography variant="h3" color="primary.main" fontWeight="bold">
                {stat.number}
              </Typography>
              <Typography variant="h6" gutterBottom>
                {stat.label}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {stat.subtext}
              </Typography>
            </Card>
          ))}
        </Box>
      </Container>

      {/* Multi-Modal AI for Invoices */}
      <Container maxWidth="lg" sx={{ py: 10 }}>
        <Box textAlign="center" mb={6}>
          <Typography variant="h2" gutterBottom fontWeight="bold">
            AI That Sees Your Invoices
          </Typography>
          <Typography variant="h5" color="text.secondary" sx={{ maxWidth: 800, mx: 'auto', mb: 4 }}>
            Why multi-modal vision AI is the breakthrough for scanned invoice processing
          </Typography>
        </Box>
        
        <Box display="flex" gap={4} flexWrap="wrap" alignItems="center">
          <Card sx={{ p: 4, flex: 1, minWidth: 400 }}>
            <Stack spacing={3}>
              <Typography variant="h6" fontWeight="bold" color="error.main">
                ❌ The Problem with Traditional OCR
              </Typography>
              <Stack spacing={2}>
                <Typography variant="body1">
                  <strong>Can't understand layouts:</strong> Multi-column tables, nested line items, and complex invoice formats confuse text-only OCR
                </Typography>
                <Typography variant="body1">
                  <strong>Misses visual context:</strong> Totals, subtotals, tax lines rely on spatial position and visual hierarchy
                </Typography>
                <Typography variant="body1">
                  <strong>Fails on scans:</strong> Poor quality scans, rotated text, and mixed formats require human intervention
                </Typography>
              </Stack>
            </Stack>
          </Card>
          
          <Card sx={{ p: 4, flex: 1, minWidth: 400, backgroundColor: 'primary.main', color: 'white' }}>
            <Stack spacing={3}>
              <Typography variant="h6" fontWeight="bold">
                ✅ Our Multi-Modal AI Solution
              </Typography>
              <Stack spacing={2}>
                <Typography variant="body1">
                  <strong>Sees the invoice structure:</strong> AI understands tables, columns, headers, and visual layouts like humans do
                </Typography>
                <Typography variant="body1">
                  <strong>Extracts with context:</strong> Associates line items with quantities, prices, totals using spatial relationships
                </Typography>
                <Typography variant="body1">
                  <strong>Handles any format:</strong> Works on scanned PDFs, photos, rotated images, and varied invoice layouts
                </Typography>
              </Stack>
            </Stack>
          </Card>
        </Box>
        
        <Paper sx={{ p: 4, mt: 4, backgroundColor: 'success.50' }}>
          <Typography variant="h6" gutterBottom fontWeight="bold" color="success.main">
            🎯 The Result: Perfect Invoice Data Extraction
          </Typography>
          <Typography variant="body1" color="text.secondary" lineHeight={1.7}>
            Extract vendor name, invoice number, dates, all line items (description, quantity, price), subtotals, taxes, 
            and final total - all correctly associated and validated against your database schema. Ready to insert directly 
            into QuickBooks, NetSuite, or your custom ERP.
          </Typography>
        </Paper>
      </Container>

      {/* Features Section */}
      <Box sx={{ backgroundColor: 'grey.50', py: 10 }}>
        <Container maxWidth="lg">
          <Stack spacing={6}>
            <Box textAlign="center">
              <Typography variant="h2" gutterBottom fontWeight="bold">
                Powerful Invoice Processing Features
              </Typography>
              <Typography variant="h5" color="text.secondary" sx={{ maxWidth: 600, mx: 'auto' }}>
                From scanned invoices to validated database records - fully automated
              </Typography>
            </Box>
            
            <Box display="flex" gap={3} flexWrap="wrap">
              {features.map((feature, index) => (
                <Card key={index} sx={{ flex: 1, minWidth: 300, p: 3 }}>
                  <CardContent>
                    <Stack spacing={2}>
                      <Avatar
                        sx={{
                          backgroundColor: 'primary.main',
                          width: 56,
                          height: 56,
                        }}
                      >
                        {feature.icon}
                      </Avatar>
                      <Typography variant="h6" fontWeight="bold">
                        {feature.title}
                      </Typography>
                      <Typography variant="body1" color="text.secondary" lineHeight={1.6}>
                        {feature.description}
                      </Typography>
                    </Stack>
                  </CardContent>
                </Card>
              ))}
            </Box>
          </Stack>
        </Container>
      </Box>

      {/* How It Works Section */}
      <Container maxWidth="lg" sx={{ py: 10 }}>
        <Stack spacing={8}>
          <Box textAlign="center">
            <Typography variant="h2" gutterBottom fontWeight="bold">
              From Invoice PDF to Database in Minutes
            </Typography>
            <Typography variant="h5" color="text.secondary">
              Automated, accurate, and ready for your accounting systems
            </Typography>
          </Box>
          
          <Box display="flex" gap={3} flexWrap="wrap" alignItems="center">
            <Card sx={{ p: 4, textAlign: 'center', flex: 1, minWidth: 280 }}>
              <CloudUpload sx={{ fontSize: 64, color: 'primary.main', mb: 2 }} />
              <Typography variant="h5" gutterBottom fontWeight="bold">
                1. Upload Invoice
              </Typography>
              <Typography variant="body1" color="text.secondary">
                Upload scanned invoice PDFs or images. Batch upload hundreds at once.
              </Typography>
            </Card>
            
            <Card sx={{ p: 4, textAlign: 'center', flex: 1, minWidth: 280 }}>
              <SmartToy sx={{ fontSize: 64, color: 'secondary.main', mb: 2 }} />
              <Typography variant="h5" gutterBottom fontWeight="bold">
                2. AI Vision Analysis
              </Typography>
              <Typography variant="body1" color="text.secondary">
                Multi-modal AI sees and understands invoice layout, extracting all structured data.
              </Typography>
            </Card>
            
            <Card sx={{ p: 4, textAlign: 'center', flex: 1, minWidth: 280 }}>
              <DataObject sx={{ fontSize: 64, color: 'success.main', mb: 2 }} />
              <Typography variant="h5" gutterBottom fontWeight="bold">
                3. Validated JSON
              </Typography>
              <Typography variant="body1" color="text.secondary">
                LSP validation ensures perfect schema compliance - ready for direct database insert.
              </Typography>
            </Card>
            
            <Card sx={{ p: 4, textAlign: 'center', flex: 1, minWidth: 280 }}>
              <Api sx={{ fontSize: 64, color: 'info.main', mb: 2 }} />
              <Typography variant="h5" gutterBottom fontWeight="bold">
                4. Auto-Insert
              </Typography>
              <Typography variant="body1" color="text.secondary">
                MCP integration sends data directly to QuickBooks, Xero, NetSuite, or custom ERPs.
              </Typography>
            </Card>
          </Box>
        </Stack>
      </Container>

      {/* Beyond Invoices - Contracts Section */}
      <Container maxWidth="lg" sx={{ py: 10 }}>
        <Stack spacing={8}>
          <Box textAlign="center">
            <Typography variant="h2" gutterBottom fontWeight="bold">
              <Gavel sx={{ fontSize: '3rem', verticalAlign: 'middle', mr: 2, color: 'primary.main' }} />
              Beyond Invoices: Legal Document Processing
            </Typography>
            <Typography variant="h5" color="text.secondary" sx={{ maxWidth: 900, mx: 'auto', mb: 4 }}>
              The same multi-modal AI that reads invoices extracts natural legal language buried in contracts and agreements
            </Typography>
          </Box>
          
          <Box display="flex" gap={4} flexWrap="wrap" alignItems="stretch">
            <Card sx={{ p: 4, flex: 1, minWidth: 400 }}>
              <Stack spacing={3}>
                <Box display="flex" alignItems="center" gap={2}>
                  <Description sx={{ fontSize: 40, color: 'secondary.main' }} />
                  <Typography variant="h5" fontWeight="bold">
                    Contract Data Extraction
                  </Typography>
                </Box>
                <Typography variant="body1" color="text.secondary" lineHeight={1.6}>
                  <strong>Extract key terms from legal documents:</strong>
                </Typography>
                <Stack spacing={1} sx={{ pl: 2 }}>
                  <Typography variant="body2">• <strong>Parties & Entities:</strong> All signatories, companies, representatives</Typography>
                  <Typography variant="body2">• <strong>Critical Dates:</strong> Effective dates, expiration, renewal terms</Typography>
                  <Typography variant="body2">• <strong>Financial Terms:</strong> Payment obligations, penalties, rates</Typography>
                  <Typography variant="body2">• <strong>Legal Obligations:</strong> Duties, restrictions, termination clauses</Typography>
                </Stack>
              </Stack>
            </Card>
            
            <Card sx={{ p: 4, flex: 1, minWidth: 400, backgroundColor: 'secondary.main', color: 'white' }}>
              <Stack spacing={3}>
                <Box display="flex" alignItems="center" gap={2}>
                  <Psychology sx={{ fontSize: 40 }} />
                  <Typography variant="h5" fontWeight="bold">
                    Natural Language Understanding
                  </Typography>
                </Box>
                <Typography variant="body1" lineHeight={1.6}>
                  <strong>Not just text extraction - true comprehension:</strong>
                </Typography>
                <Stack spacing={1} sx={{ pl: 2 }}>
                  <Typography variant="body2">• <strong>Context-Aware:</strong> Understands legal terminology and clause relationships</Typography>
                  <Typography variant="body2">• <strong>Cross-References:</strong> Links related sections and dependencies</Typography>
                  <Typography variant="body2">• <strong>Risk Identification:</strong> Flags unusual terms or problematic clauses</Typography>
                  <Typography variant="body2">• <strong>Structured Output:</strong> Clean JSON ready for contract management systems</Typography>
                </Stack>
              </Stack>
            </Card>
          </Box>
          
          <Paper
            sx={{
              p: 5,
              background: 'linear-gradient(135deg, #f5f5f5 0%, #e8f5e8 100%)',
              border: '2px solid',
              borderColor: 'success.main',
            }}
          >
            <Stack spacing={4}>
              <Typography variant="h5" gutterBottom fontWeight="bold" color="success.dark" textAlign="center">
                🚀 Built on LLM-OCR: One Platform, Infinite Documents
              </Typography>
              <Typography variant="body1" textAlign="center" lineHeight={1.7} color="text.secondary">
                <strong>Invoices are just the beginning.</strong> Our platform is built on LLM-OCR's proven multi-modal architecture. 
                Process <strong>invoices today</strong>, add <strong>contracts tomorrow</strong>, handle <strong>medical records next week</strong>. 
                Same AI vision, same validation framework, same database integration - <strong>infinitely extensible</strong>.
              </Typography>
            </Stack>
          </Paper>
        </Stack>
      </Container>

      {/* Benefits Section */}
      <Box sx={{ backgroundColor: 'primary.main', color: 'white', py: 10 }}>
        <Container maxWidth="lg">
          <Stack spacing={6}>
            <Box textAlign="center">
              <Typography variant="h2" gutterBottom fontWeight="bold">
                Why Choose PaperIQ.ai Invoice Intelligence Platform?
              </Typography>
              <Typography variant="h5" sx={{ opacity: 0.9 }}>
                Built for finance teams who need accurate, automated invoice processing
              </Typography>
            </Box>
            
            <Box display="flex" gap={4} flexWrap="wrap">
              {benefits.map((benefit, index) => (
                <Box key={index} display="flex" gap={2} flex={1} minWidth={400}>
                  {benefit.icon}
                  <Stack spacing={1}>
                    <Typography variant="h6" fontWeight="bold">
                      {benefit.title}
                    </Typography>
                    <Typography variant="body1" sx={{ opacity: 0.9 }}>
                      {benefit.description}
                    </Typography>
                  </Stack>
                </Box>
              ))}
            </Box>
          </Stack>
        </Container>
      </Box>

      {/* Security Section */}
      <Box sx={{ backgroundColor: 'primary.dark', color: 'white', py: 10 }}>
        <Container maxWidth="lg">
          <Stack spacing={6}>
            <Box textAlign="center">
              <Typography variant="h2" gutterBottom fontWeight="bold">
                <Security sx={{ fontSize: '3rem', verticalAlign: 'middle', mr: 2 }} />
                Enterprise-Grade Security for Financial Data
              </Typography>
              <Typography variant="h5" sx={{ opacity: 0.9, maxWidth: 900, mx: 'auto' }}>
                Your invoices contain sensitive financial information. We protect them with multi-tenant isolation and zero-trust architecture.
              </Typography>
            </Box>
            
            <Box display="flex" gap={4} flexWrap="wrap">
              <Card sx={{ p: 4, flex: 1, minWidth: 350 }}>
                <Stack spacing={2}>
                  <Typography variant="h6" fontWeight="bold" color="primary.main">
                    Complete Data Isolation
                  </Typography>
                  <Typography variant="body1" lineHeight={1.7}>
                    Every invoice, every vendor record, every extracted field is completely isolated per tenant. 
                    Your financial data is invisible to other users. Database-level security ensures zero cross-tenant access.
                  </Typography>
                </Stack>
              </Card>
              
              <Card sx={{ p: 4, flex: 1, minWidth: 350 }}>
                <Stack spacing={2}>
                  <Typography variant="h6" fontWeight="bold" color="primary.main">
                    Your Infrastructure, Your Control
                  </Typography>
                  <Typography variant="body1" lineHeight={1.7}>
                    Use local Ollama models to keep invoices on-premises, or use your own OpenAI keys. 
                    Connect to your own databases via MCP. Complete control over where your financial data lives.
                  </Typography>
                </Stack>
              </Card>
            </Box>
            
            <Paper 
              sx={{ 
                p: 4, 
                backgroundColor: 'rgba(255,255,255,0.1)', 
                backdropFilter: 'blur(10px)',
              }}
            >
              <Stack spacing={2} textAlign="center">
                <Typography variant="h5" fontWeight="bold" color="warning.main">
                  🛡️ OAuth 2.1 Compliant Multi-Tenant Architecture
                </Typography>
                <Typography variant="body1" sx={{ opacity: 0.95, lineHeight: 1.7 }}>
                  Built on LLM-OCR's proven security foundation with complete tenant isolation, 
                  server-specific tokens, and comprehensive audit trails.
                </Typography>
              </Stack>
            </Paper>
          </Stack>
        </Container>
      </Box>

      {/* Use Cases */}
      <Box sx={{ backgroundColor: 'grey.50', py: 10 }}>
        <Container maxWidth="lg">
          <Stack spacing={6}>
            <Box textAlign="center">
              <Typography variant="h2" gutterBottom fontWeight="bold">
                Perfect for Every Business
              </Typography>
              <Typography variant="h5" color="text.secondary">
                From small businesses to enterprise finance teams
              </Typography>
            </Box>
            
            <Box display="flex" gap={3} flexWrap="wrap">
              <Card sx={{ p: 4, textAlign: 'center', flex: 1, minWidth: 280 }}>
                <TrendingUp sx={{ fontSize: 48, color: 'primary.main', mb: 2 }} />
                <Typography variant="h6" gutterBottom fontWeight="bold">
                  Accounting Firms
                </Typography>
                <Typography variant="body1" color="text.secondary">
                  Process hundreds of client invoices automatically. Extract to QuickBooks, Xero, or custom systems. 
                  Save hours on data entry.
                </Typography>
              </Card>
              
              <Card sx={{ p: 4, textAlign: 'center', flex: 1, minWidth: 280 }}>
                <Receipt sx={{ fontSize: 48, color: 'secondary.main', mb: 2 }} />
                <Typography variant="h6" gutterBottom fontWeight="bold">
                  Accounts Payable Teams
                </Typography>
                <Typography variant="body1" color="text.secondary">
                  Eliminate manual invoice entry. AI extracts all fields from scanned invoices directly into your ERP. 
                  Faster approvals, fewer errors.
                </Typography>
              </Card>
              
              <Card sx={{ p: 4, textAlign: 'center', flex: 1, minWidth: 280 }}>
                <Gavel sx={{ fontSize: 48, color: 'success.main', mb: 2 }} />
                <Typography variant="h6" gutterBottom fontWeight="bold">
                  Legal & Procurement
                </Typography>
                <Typography variant="body1" color="text.secondary">
                  Extract key terms from vendor contracts and procurement agreements. Track obligations, dates, and payment terms automatically.
                </Typography>
              </Card>
            </Box>
          </Stack>
        </Container>
      </Box>

      {/* CTA Section */}
      <Box sx={{ py: 10, backgroundColor: 'primary.dark', color: 'white' }}>
        <Container maxWidth="md">
          <Stack spacing={4} textAlign="center">
            <Typography variant="h2" fontWeight="bold">
              Ready to Automate Your Invoice Processing?
            </Typography>
            <Typography variant="h5" sx={{ opacity: 0.9 }}>
              AI that sees and understands invoices - from scanned PDFs to validated database records
            </Typography>
            
            <Paper sx={{ p: 4, backgroundColor: 'rgba(255,255,255,0.1)', backdropFilter: 'blur(10px)' }}>
              <Stack spacing={2}>
                <Typography variant="h6" fontWeight="bold" color="warning.main">
                  🎯 What You Get:
                </Typography>
                <Box display="flex" gap={2} flexWrap="wrap" justifyContent="center">
                  <Chip 
                    label="✅ Multi-Modal AI Vision" 
                    sx={{ backgroundColor: 'rgba(255,255,255,0.2)', color: 'white' }} 
                  />
                  <Chip 
                    label="✅ Perfect Data Extraction" 
                    sx={{ backgroundColor: 'rgba(255,255,255,0.2)', color: 'white' }} 
                  />
                  <Chip 
                    label="✅ QuickBooks Integration" 
                    sx={{ backgroundColor: 'rgba(255,255,255,0.2)', color: 'white' }} 
                  />
                  <Chip 
                    label="✅ Contract Processing Too" 
                    sx={{ backgroundColor: 'rgba(255,255,255,0.2)', color: 'white' }} 
                  />
                  <Chip 
                    label="✅ Built on LLM-OCR" 
                    sx={{ backgroundColor: 'rgba(255,255,255,0.2)', color: 'white' }} 
                  />
                </Box>
              </Stack>
            </Paper>
            
            <Stack direction="row" spacing={2} justifyContent="center" flexWrap="wrap" sx={{ gap: 2 }}>
              <Button
                variant="contained"
                size="large"
                sx={{
                  backgroundColor: 'white',
                  color: 'primary.main',
                  px: 6,
                  py: 2,
                  fontSize: '1.1rem',
                  '&:hover': {
                    backgroundColor: 'rgba(255,255,255,0.9)',
                  },
                }}
                onClick={() => navigate('/register')}
              >
                Start Processing Invoices
              </Button>
              <Button
                variant="outlined"
                size="large"
                sx={{
                  borderColor: 'white',
                  color: 'white',
                  px: 6,
                  py: 2,
                  fontSize: '1.1rem',
                  '&:hover': {
                    borderColor: 'white',
                    backgroundColor: 'rgba(255,255,255,0.1)',
                  },
                }}
                onClick={() => window.open(config.llmocrAppUrl, '_blank')}
              >
                View LLM-OCR Platform
              </Button>
            </Stack>
            
            <Typography variant="body1" sx={{ opacity: 0.8 }}>
              Free to get started • No credit card required • Built on LLM-OCR's proven platform
            </Typography>
          </Stack>
        </Container>
      </Box>

      {/* Footer */}
      <Box sx={{ backgroundColor: 'grey.900', color: 'white', py: 6 }}>
        <Container maxWidth="lg">
          <Box display="flex" gap={4} flexWrap="wrap">
            <Box flex={1} minWidth={300}>
              <Stack spacing={2}>
                <Typography variant="h5" fontWeight="bold">
                  PaperIQ.ai Invoice Intelligence Platform
                </Typography>
                <Typography variant="body2" sx={{ opacity: 0.9, fontWeight: 'bold', color: 'primary.light' }}>
                  Powered by LLM-OCR • Built by PaperIQ.ai
                </Typography>
                <Typography variant="body1" sx={{ opacity: 0.8 }}>
                  AI-powered invoice and contract processing platform. Multi-modal vision that sees and understands your documents.
                </Typography>
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<Email />}
                  sx={{
                    borderColor: 'primary.light',
                    color: 'primary.light',
                    mt: 2,
                    alignSelf: 'flex-start',
                    '&:hover': {
                      borderColor: 'primary.main',
                      backgroundColor: 'rgba(25, 118, 210, 0.1)',
                    },
                  }}
                  onClick={() => window.open('mailto:info@paperiq.ai', '_blank')}
                >
                  Contact PaperIQ.ai
                </Button>
              </Stack>
            </Box>
            
            <Box flex={1} minWidth={300}>
              <Stack spacing={2}>
                <Typography variant="h6" fontWeight="bold">
                  Invoice Features
                </Typography>
                <Stack spacing={1}>
                  <Typography variant="body2" sx={{ opacity: 0.8 }}>
                    • Multi-modal AI vision
                  </Typography>
                  <Typography variant="body2" sx={{ opacity: 0.8 }}>
                    • Perfect data extraction
                  </Typography>
                  <Typography variant="body2" sx={{ opacity: 0.8 }}>
                    • QuickBooks integration
                  </Typography>
                  <Typography variant="body2" sx={{ opacity: 0.8 }}>
                    • Contract processing
                  </Typography>
                </Stack>
              </Stack>
            </Box>
            
            <Box flex={1} minWidth={300}>
              <Stack spacing={2}>
                <Typography variant="h6" fontWeight="bold">
                  Built on LLM-OCR
                </Typography>
                <Stack spacing={1}>
                  <Typography 
                    variant="body2" 
                    sx={{ opacity: 0.8, cursor: 'pointer', '&:hover': { opacity: 1 } }}
                    onClick={() => window.open(config.llmocrAppUrl, '_blank')}
                  >
                    • View full platform
                  </Typography>
                  <Typography 
                    variant="body2" 
                    sx={{ opacity: 0.8, cursor: 'pointer', '&:hover': { opacity: 1 } }}
                    onClick={() => window.open(config.llmocrAppUrl, '_blank')}
                  >
                    • Security architecture
                  </Typography>
                </Stack>
              </Stack>
            </Box>
          </Box>
          
          <Divider sx={{ my: 4, backgroundColor: 'rgba(255,255,255,0.2)' }} />
          
          <Box textAlign="center">
            <Typography variant="body2" sx={{ opacity: 0.6 }}>
              © 2026 PaperIQ.ai. All rights reserved. Built with Spring Boot, React, and AI.
            </Typography>
          </Box>
        </Container>
      </Box>
    </Box>
  );
};

export default InvoiceLandingPage;

