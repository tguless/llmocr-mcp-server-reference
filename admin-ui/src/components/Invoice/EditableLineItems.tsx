import React, { useState, useEffect } from 'react';
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
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  Alert,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
} from '@mui/material';
import { Edit, Delete, Save, Close, Add } from '@mui/icons-material';
import { categoryAPI } from '../../services/api';

interface LineItem {
  id: number;
  lineNumber: number;
  description: string;
  quantity?: number;
  unitPrice?: number;
  lineTotal: number;
  taxRate?: number;
  taxAmount?: number;
  productCode?: string;
  unitOfMeasure?: string;
  category?: string;
  categoryConfidence?: number;
  categorizedBy?: string;
  requiresReview: boolean;
  reviewedBy?: string;
  reviewedAt?: string;
  energy?: {
    unit: string;
    quantity: number;
    rate: number;
  };
  metadata?: any;
}

interface EditableLineItemsProps {
  lineItems: LineItem[];
  currency: string;
  onCategoryUpdate?: (lineItemId: number, category: string) => Promise<void>;
  onCategoryDelete?: (lineItemId: number) => Promise<void>;
  onLineItemAdd?: (lineItem: {
    description: string;
    quantity?: number;
    unitPrice?: number;
    lineTotal: number;
    taxRate?: number;
    taxAmount?: number;
    productCode?: string;
    unitOfMeasure?: string;
  }) => Promise<void>;
  onLineItemEdit?: (lineItemId: number, lineItem: {
    description?: string;
    quantity?: number;
    unitPrice?: number;
    lineTotal?: number;
    taxRate?: number;
    taxAmount?: number;
    productCode?: string;
    unitOfMeasure?: string;
  }) => Promise<void>;
  onLineItemDelete?: (lineItemId: number) => Promise<void>;
}

const formatCurrency = (amount: number, currency: string): string => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: currency || 'USD',
  }).format(amount);
};

const getCategoryColor = (category: string): 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning' => {
  const categoryColorMap: Record<string, 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning'> = {
    'PASS_THROUGH_ENERGY': 'primary',
    'PASS_THROUGH_DEMAND': 'primary',
    'PASS_THROUGH_FUEL': 'primary',
    'PASS_THROUGH_TRANSMISSION': 'primary',
    'PASS_THROUGH_ENVIRONMENTAL': 'primary',
    'PASS_THROUGH_OTHER': 'primary',
    'CREDIT_GENERATION': 'success',
    'CREDIT_REBATE': 'success',
    'INTERNAL_ADMIN': 'info',
    'INTERNAL_FEE': 'info',
    'INTERNAL_TAX': 'info',
    'INTERNAL_OTHER': 'info',
    'REVIEW_REQUIRED': 'warning',
  };
  return categoryColorMap[category] || 'default';
};

export const EditableLineItems: React.FC<EditableLineItemsProps> = ({
  lineItems,
  currency,
  onCategoryUpdate,
  onCategoryDelete,
  onLineItemAdd,
  onLineItemEdit,
  onLineItemDelete,
}) => {
  const [editingCategories, setEditingCategories] = useState<{ [key: number]: boolean }>({});
  const [categoryValues, setCategoryValues] = useState<{ [key: number]: string }>({});
  const [addCategoryDialogOpen, setAddCategoryDialogOpen] = useState(false);
  const [selectedLineItemId, setSelectedLineItemId] = useState<number | null>(null);
  const [newCategory, setNewCategory] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [deleteTargetType, setDeleteTargetType] = useState<'category' | 'lineitem'>('category');
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);
  
  // Fetch tenant-specific categories from API
  const [availableCategories, setAvailableCategories] = useState<string[]>([]);
  const [categoriesLoading, setCategoriesLoading] = useState(true);
  
  useEffect(() => {
    const fetchCategories = async () => {
      try {
        setCategoriesLoading(true);
        const categories = await categoryAPI.getCategories();
        // Extract category codes for the dropdown
        const categoryCodes = categories.map(cat => cat.categoryCode);
        setAvailableCategories(categoryCodes);
      } catch (err) {
        console.error('Failed to fetch categories:', err);
        // Fallback to empty array if fetch fails
        setAvailableCategories([]);
      } finally {
        setCategoriesLoading(false);
      }
    };
    
    fetchCategories();
  }, []);

  const handleEditCategory = (lineItemId: number, currentCategory?: string) => {
    setCategoryValues((prev) => ({
      ...prev,
      [lineItemId]: currentCategory || '',
    }));
    setEditingCategories((prev) => ({
      ...prev,
      [lineItemId]: true,
    }));
  };

  const handleSaveCategory = async (lineItemId: number) => {
    try {
      setLoading(true);
      setError(null);
      const newCat = categoryValues[lineItemId];
      if (onCategoryUpdate) {
        await onCategoryUpdate(lineItemId, newCat);
      }
      setEditingCategories((prev) => ({
        ...prev,
        [lineItemId]: false,
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update category');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelEditCategory = (lineItemId: number) => {
    setEditingCategories((prev) => ({
      ...prev,
      [lineItemId]: false,
    }));
    setCategoryValues((prev) => {
      const newValues = { ...prev };
      delete newValues[lineItemId];
      return newValues;
    });
  };

  const handleOpenDeleteConfirm = (type: 'category' | 'lineitem', id: number) => {
    setDeleteTargetType(type);
    setDeleteTargetId(id);
    setDeleteConfirmOpen(true);
  };

  const handleConfirmDelete = async () => {
    try {
      setLoading(true);
      setError(null);
      
      if (deleteTargetType === 'category' && deleteTargetId && onCategoryDelete) {
        await onCategoryDelete(deleteTargetId);
        setEditingCategories((prev) => {
          const newEdit = { ...prev };
          delete newEdit[deleteTargetId];
          return newEdit;
        });
      } else if (deleteTargetType === 'lineitem' && deleteTargetId && onLineItemDelete) {
        await onLineItemDelete(deleteTargetId);
      }
      
      setDeleteConfirmOpen(false);
      setDeleteTargetId(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete');
    } finally {
      setLoading(false);
    }
  };

  const handleOpenAddCategoryDialog = (lineItemId: number) => {
    setSelectedLineItemId(lineItemId);
    setNewCategory('');
    setAddCategoryDialogOpen(true);
  };

  const handleAddCategory = async () => {
    if (!newCategory.trim()) {
      setError('Category name is required');
      return;
    }
    try {
      setLoading(true);
      setError(null);
      if (selectedLineItemId && onCategoryUpdate) {
        await onCategoryUpdate(selectedLineItemId, newCategory);
      }
      setAddCategoryDialogOpen(false);
      setSelectedLineItemId(null);
      setNewCategory('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add category');
    } finally {
      setLoading(false);
    }
  };

  // Line Item Add/Edit State
  const [addLineItemDialogOpen, setAddLineItemDialogOpen] = useState(false);
  const [editLineItemDialogOpen, setEditLineItemDialogOpen] = useState(false);
  const [editingLineItemId, setEditingLineItemId] = useState<number | null>(null);
  const [newLineItem, setNewLineItem] = useState({
    description: '',
    quantity: '',
    unitPrice: '',
    lineTotal: '',
    taxRate: '',
    taxAmount: '',
    productCode: '',
    unitOfMeasure: '',
    category: '',
  });

  const handleOpenAddLineItemDialog = () => {
    setNewLineItem({
      description: '',
      quantity: '',
      unitPrice: '',
      lineTotal: '',
      taxRate: '',
      taxAmount: '',
      productCode: '',
      unitOfMeasure: '',
      category: '',
    });
    setAddLineItemDialogOpen(true);
  };

  const handleOpenEditLineItemDialog = (item: LineItem) => {
    setEditingLineItemId(item.id);
    setNewLineItem({
      description: item.description,
      quantity: item.quantity?.toString() || '',
      unitPrice: item.unitPrice?.toString() || '',
      lineTotal: item.lineTotal?.toString() || '',
      taxRate: item.taxRate?.toString() || '',
      taxAmount: item.taxAmount?.toString() || '',
      productCode: item.productCode || '',
      unitOfMeasure: item.unitOfMeasure || '',
      category: item.category || '',
    });
    setEditLineItemDialogOpen(true);
  };

  const handleAddLineItem = async () => {
    if (!newLineItem.description.trim() || !newLineItem.lineTotal) {
      setError('Description and line total are required');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const lineItemData = {
        description: newLineItem.description.trim(),
        quantity: newLineItem.quantity ? parseFloat(newLineItem.quantity) : undefined,
        unitPrice: newLineItem.unitPrice ? parseFloat(newLineItem.unitPrice) : undefined,
        lineTotal: parseFloat(newLineItem.lineTotal),
        taxRate: newLineItem.taxRate ? parseFloat(newLineItem.taxRate) : undefined,
        taxAmount: newLineItem.taxAmount ? parseFloat(newLineItem.taxAmount) : undefined,
        productCode: newLineItem.productCode || undefined,
        unitOfMeasure: newLineItem.unitOfMeasure || undefined,
        category: newLineItem.category || undefined,
      };

      if (onLineItemAdd) {
        await onLineItemAdd(lineItemData);
      }

      setAddLineItemDialogOpen(false);
      setNewLineItem({
        description: '',
        quantity: '',
        unitPrice: '',
        lineTotal: '',
        taxRate: '',
        taxAmount: '',
        productCode: '',
        unitOfMeasure: '',
        category: '',
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add line item');
    } finally {
      setLoading(false);
    }
  };

  const handleEditLineItem = async () => {
    if (!newLineItem.description.trim() || !newLineItem.lineTotal) {
      setError('Description and line total are required');
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const lineItemData: Partial<LineItem> = {
        description: newLineItem.description.trim(),
        quantity: newLineItem.quantity ? parseFloat(newLineItem.quantity) : undefined,
        unitPrice: newLineItem.unitPrice ? parseFloat(newLineItem.unitPrice) : undefined,
        lineTotal: parseFloat(newLineItem.lineTotal),
        taxRate: newLineItem.taxRate ? parseFloat(newLineItem.taxRate) : undefined,
        taxAmount: newLineItem.taxAmount ? parseFloat(newLineItem.taxAmount) : undefined,
        productCode: newLineItem.productCode || undefined,
        unitOfMeasure: newLineItem.unitOfMeasure || undefined,
        category: newLineItem.category || undefined,
      };

      if (editingLineItemId && onLineItemEdit) {
        await onLineItemEdit(editingLineItemId, lineItemData);
      }

      setEditLineItemDialogOpen(false);
      setEditingLineItemId(null);
      setNewLineItem({
        description: '',
        quantity: '',
        unitPrice: '',
        lineTotal: '',
        taxRate: '',
        taxAmount: '',
        productCode: '',
        unitOfMeasure: '',
        category: '',
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to edit line item');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      
      <Stack direction="row" justifyContent="flex-end" sx={{ mb: 2 }}>
        <Button
          variant="contained"
          startIcon={<Add />}
          onClick={handleOpenAddLineItemDialog}
        >
          Add Line Item
        </Button>
      </Stack>
      
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>#</TableCell>
              <TableCell>Description</TableCell>
              <TableCell align="right">Quantity</TableCell>
              <TableCell align="right">Unit Price</TableCell>
              <TableCell align="right">Total</TableCell>
              <TableCell>Category</TableCell>
              <TableCell sx={{ width: 120 }}>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {lineItems.map((item) => (
              <TableRow key={item.id}>
                <TableCell>{item.lineNumber}</TableCell>
                <TableCell>
                  <Typography variant="body2">{item.description}</Typography>
                  {item.productCode && (
                    <Typography variant="caption" color="text.secondary">
                      Code: {item.productCode}
                    </Typography>
                  )}
                </TableCell>
                <TableCell align="right">
                  {item.quantity ? `${item.quantity} ${item.unitOfMeasure || ''}` : '-'}
                </TableCell>
                <TableCell align="right">
                  {item.unitPrice ? formatCurrency(item.unitPrice, currency) : '-'}
                </TableCell>
                <TableCell align="right">
                  <Typography variant="body2" fontWeight={500}>
                    {formatCurrency(item.lineTotal, currency)}
                  </Typography>
                </TableCell>
                <TableCell>
                  {item.category ? (
                    <Stack spacing={0.5} sx={{ maxWidth: '100%' }}>
                      <Chip
                        label={item.category.replace(/_/g, ' ')}
                        size="small"
                        color={getCategoryColor(item.category)}
                        variant="outlined"
                        sx={{
                          maxWidth: '100%',
                          '& .MuiChip-label': {
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            fontSize: '0.75rem',
                          },
                        }}
                      />
                      {item.categoryConfidence && (
                        <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.65rem' }}>
                          Confidence: {(item.categoryConfidence * 100).toFixed(1)}%
                        </Typography>
                      )}
                    </Stack>
                  ) : (
                    <Typography variant="caption" color="text.secondary">
                      Uncategorized
                    </Typography>
                  )}
                </TableCell>
                <TableCell>
                  {editingCategories[item.id] ? (
                    <Stack direction="row" spacing={0.5}>
                      <IconButton
                        size="small"
                        onClick={() => handleSaveCategory(item.id)}
                        disabled={loading}
                      >
                        <Save fontSize="small" />
                      </IconButton>
                      <IconButton
                        size="small"
                        onClick={() => handleCancelEditCategory(item.id)}
                        disabled={loading}
                      >
                        <Close fontSize="small" />
                      </IconButton>
                    </Stack>
                  ) : (
                    <Stack direction="row" spacing={0.5}>
                      <IconButton
                        size="small"
                        color="primary"
                        onClick={() => handleOpenEditLineItemDialog(item)}
                        title="Edit line item and category"
                      >
                        <Edit fontSize="small" />
                      </IconButton>
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleOpenDeleteConfirm('lineitem', item.id)}
                        title="Delete line item"
                      >
                        <Delete fontSize="small" />
                      </IconButton>
                    </Stack>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Add Category Dialog */}
      <Dialog open={addCategoryDialogOpen} onClose={() => setAddCategoryDialogOpen(false)}>
        <DialogTitle>Add Category</DialogTitle>
        <DialogContent sx={{ minWidth: 400 }}>
          <FormControl fullWidth sx={{ mt: 2 }}>
            <InputLabel>Category</InputLabel>
            <Select
              value={newCategory}
              onChange={(e) => setNewCategory(e.target.value)}
              disabled={loading}
              label="Category"
            >
              <MenuItem value="">
                <em>Select category...</em>
              </MenuItem>
              {availableCategories.map((cat) => (
                <MenuItem key={cat} value={cat}>
                  {cat.replace(/_/g, ' ')}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddCategoryDialogOpen(false)} disabled={loading}>
            Cancel
          </Button>
          <Button onClick={handleAddCategory} variant="contained" disabled={loading || !newCategory.trim()}>
            Add
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteConfirmOpen} onClose={() => setDeleteConfirmOpen(false)}>
        <DialogTitle>Confirm Delete</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete this {deleteTargetType === 'category' ? 'category' : 'line item'}?
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirmOpen(false)} disabled={loading}>
            Cancel
          </Button>
          <Button onClick={handleConfirmDelete} variant="contained" color="error" disabled={loading}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>

      {/* Add Line Item Dialog */}
      <Dialog open={addLineItemDialogOpen} onClose={() => setAddLineItemDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Add New Line Item</DialogTitle>
        <DialogContent sx={{ minWidth: 500 }}>
          <Stack spacing={2} sx={{ mt: 2 }}>
            <TextField
              label="Description"
              value={newLineItem.description}
              onChange={(e) => setNewLineItem({ ...newLineItem, description: e.target.value })}
              fullWidth
              required
              disabled={loading}
            />
            <TextField
              label="Quantity"
              type="number"
              value={newLineItem.quantity}
              onChange={(e) => setNewLineItem({ ...newLineItem, quantity: e.target.value })}
              disabled={loading}
            />
            <TextField
              label="Unit Price"
              type="number"
              value={newLineItem.unitPrice}
              onChange={(e) => setNewLineItem({ ...newLineItem, unitPrice: e.target.value })}
              disabled={loading}
            />
            <Box sx={{ p: 1.5, backgroundColor: '#f5f5f5', borderRadius: 1, border: '1px solid #e0e0e0' }}>
              <Typography variant="caption" color="textSecondary">
                💡 Calculated Total (Quantity × Unit Price):
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 500, mt: 0.5 }}>
                {(() => {
                  const qty = parseFloat(newLineItem.quantity) || 0;
                  const price = parseFloat(newLineItem.unitPrice) || 0;
                  return qty && price ? `${(qty * price).toFixed(2)}` : '-';
                })()}
              </Typography>
              <Typography variant="caption" color="textSecondary" sx={{ display: 'block', mt: 0.5 }}>
                ℹ️ Enter the actual Line Total below (not auto-calculated)
              </Typography>
            </Box>
            <TextField
              label="Line Total"
              type="number"
              value={newLineItem.lineTotal}
              onChange={(e) => setNewLineItem({ ...newLineItem, lineTotal: e.target.value })}
              fullWidth
              required
              disabled={loading}
              helperText="Enter the actual invoice line total (may differ from calculated total due to rounding/taxes)"
            />
            <TextField
              label="Tax Rate"
              type="number"
              value={newLineItem.taxRate}
              onChange={(e) => setNewLineItem({ ...newLineItem, taxRate: e.target.value })}
              disabled={loading}
            />
            <TextField
              label="Tax Amount"
              type="number"
              value={newLineItem.taxAmount}
              onChange={(e) => setNewLineItem({ ...newLineItem, taxAmount: e.target.value })}
              disabled={loading}
            />
            <TextField
              label="Product Code"
              value={newLineItem.productCode}
              onChange={(e) => setNewLineItem({ ...newLineItem, productCode: e.target.value })}
              disabled={loading}
            />
            <TextField
              label="Unit of Measure"
              value={newLineItem.unitOfMeasure}
              onChange={(e) => setNewLineItem({ ...newLineItem, unitOfMeasure: e.target.value })}
              disabled={loading}
            />
            <FormControl fullWidth>
              <InputLabel>Category</InputLabel>
              <Select
                value={newLineItem.category}
                onChange={(e) => setNewLineItem({ ...newLineItem, category: e.target.value })}
                disabled={loading}
                label="Category"
              >
                <MenuItem value="">
                  <em>No category</em>
                </MenuItem>
                {availableCategories.map((cat) => (
                  <MenuItem key={cat} value={cat}>
                    {cat.replace(/_/g, ' ')}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddLineItemDialogOpen(false)} disabled={loading}>
            Cancel
          </Button>
          <Button onClick={handleAddLineItem} variant="contained" disabled={loading}>
            Add
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit Line Item Dialog */}
      <Dialog open={editLineItemDialogOpen} onClose={() => setEditLineItemDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Edit Line Item</DialogTitle>
        <DialogContent sx={{ minWidth: 500 }}>
          <Stack spacing={2} sx={{ mt: 2 }}>
            <TextField
              label="Description"
              value={newLineItem.description}
              onChange={(e) => setNewLineItem({ ...newLineItem, description: e.target.value })}
              fullWidth
              required
              disabled={loading}
            />
            <TextField
              label="Quantity"
              type="number"
              value={newLineItem.quantity}
              onChange={(e) => setNewLineItem({ ...newLineItem, quantity: e.target.value })}
              disabled={loading}
            />
            <TextField
              label="Unit Price"
              type="number"
              value={newLineItem.unitPrice}
              onChange={(e) => setNewLineItem({ ...newLineItem, unitPrice: e.target.value })}
              disabled={loading}
            />
            <Box sx={{ p: 1.5, backgroundColor: '#f5f5f5', borderRadius: 1, border: '1px solid #e0e0e0' }}>
              <Typography variant="caption" color="textSecondary">
                💡 Calculated Total (Quantity × Unit Price):
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 500, mt: 0.5 }}>
                {(() => {
                  const qty = parseFloat(newLineItem.quantity) || 0;
                  const price = parseFloat(newLineItem.unitPrice) || 0;
                  return qty && price ? `${(qty * price).toFixed(2)}` : '-';
                })()}
              </Typography>
              <Typography variant="caption" color="textSecondary" sx={{ display: 'block', mt: 0.5 }}>
                ℹ️ Enter the actual Line Total below (not auto-calculated)
              </Typography>
            </Box>
            <TextField
              label="Line Total"
              type="number"
              value={newLineItem.lineTotal}
              onChange={(e) => setNewLineItem({ ...newLineItem, lineTotal: e.target.value })}
              fullWidth
              required
              disabled={loading}
              helperText="Enter the actual invoice line total (may differ from calculated total due to rounding/taxes)"
            />
            <TextField
              label="Tax Rate"
              type="number"
              value={newLineItem.taxRate}
              onChange={(e) => setNewLineItem({ ...newLineItem, taxRate: e.target.value })}
              disabled={loading}
            />
            <TextField
              label="Tax Amount"
              type="number"
              value={newLineItem.taxAmount}
              onChange={(e) => setNewLineItem({ ...newLineItem, taxAmount: e.target.value })}
              disabled={loading}
            />
            <TextField
              label="Product Code"
              value={newLineItem.productCode}
              onChange={(e) => setNewLineItem({ ...newLineItem, productCode: e.target.value })}
              disabled={loading}
            />
            <TextField
              label="Unit of Measure"
              value={newLineItem.unitOfMeasure}
              onChange={(e) => setNewLineItem({ ...newLineItem, unitOfMeasure: e.target.value })}
              disabled={loading}
            />
            <FormControl fullWidth>
              <InputLabel>Category</InputLabel>
              <Select
                value={newLineItem.category}
                onChange={(e) => setNewLineItem({ ...newLineItem, category: e.target.value })}
                disabled={loading}
                label="Category"
              >
                <MenuItem value="">
                  <em>No category</em>
                </MenuItem>
                {availableCategories.map((cat) => (
                  <MenuItem key={cat} value={cat}>
                    {cat.replace(/_/g, ' ')}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditLineItemDialogOpen(false)} disabled={loading}>
            Cancel
          </Button>
          <Button onClick={handleEditLineItem} variant="contained" disabled={loading}>
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default EditableLineItems;

