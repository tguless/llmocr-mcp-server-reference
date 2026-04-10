import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Chip,
  TextField,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  CircularProgress,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Divider,
  Stack,
  FormControlLabel,
  Checkbox,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import EditIcon from '@mui/icons-material/Edit';
import SearchIcon from '@mui/icons-material/Search';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import { categoryAPI, CategoryInfo } from '../services/api';

const CategoryManagement: React.FC = () => {
  const [categories, setCategories] = useState<CategoryInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<CategoryInfo | null>(null);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [addDialogOpen, setAddDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [categoryToDelete, setCategoryToDelete] = useState<CategoryInfo | null>(null);
  const [newCategory, setNewCategory] = useState({
    categoryCode: '',
    categoryName: '',
    description: '',
    isPassThrough: false,
    isCredit: false,
    isBillable: false,
    keywords: '',
  });

  useEffect(() => {
    loadCategories();
  }, []);

  const loadCategories = async () => {
    try {
      setLoading(true);
      setError(null);
      const result = await categoryAPI.getCategories();
      setCategories(result);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to load categories');
      console.error('Error loading categories:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleEditClick = (category: CategoryInfo) => {
    setSelectedCategory(category);
    setEditDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setEditDialogOpen(false);
    setSelectedCategory(null);
  };

  const handleSaveCategory = async () => {
    if (!selectedCategory) return;

    try {
      setLoading(true);
      setError(null);
      
      // Get updated values from form inputs
      const descriptionInput = document.querySelector('[name="description"]') as HTMLTextAreaElement;
      const keywordsInput = document.querySelector('[name="keywords"]') as HTMLInputElement;
      
      const updates = {
        description: descriptionInput?.value || selectedCategory.description,
        keywords: keywordsInput?.value || '',
      };
      
      await categoryAPI.updateCategory(selectedCategory.categoryCode, updates);
      
      // Reload categories to show updated data
      await loadCategories();
      
      // Close dialog
      handleCloseDialog();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to save category');
      console.error('Error saving category:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleAddCategory = async () => {
    if (!newCategory.categoryCode || !newCategory.categoryName) {
      setError('Category code and name are required');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      
      await categoryAPI.createCategory(newCategory);
      
      // Reload categories to show new category
      await loadCategories();
      
      // Reset form and close dialog
      setNewCategory({
        categoryCode: '',
        categoryName: '',
        description: '',
        isPassThrough: false,
        isCredit: false,
        isBillable: false,
        keywords: '',
      });
      setAddDialogOpen(false);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to add category');
      console.error('Error adding category:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteClick = (category: CategoryInfo) => {
    setCategoryToDelete(category);
    setDeleteDialogOpen(true);
  };

  const handleConfirmDelete = async () => {
    if (!categoryToDelete) return;

    try {
      setLoading(true);
      setError(null);
      
      await categoryAPI.deleteCategory(categoryToDelete.categoryCode);
      
      // Reload categories to reflect deletion
      await loadCategories();
      
      // Close dialog
      setDeleteDialogOpen(false);
      setCategoryToDelete(null);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to delete category');
      console.error('Error deleting category:', err);
    } finally {
      setLoading(false);
    }
  };

  const filteredCategories = categories.filter(
    (cat) =>
      cat.categoryName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      cat.categoryCode.toLowerCase().includes(searchTerm.toLowerCase()) ||
      cat.description.toLowerCase().includes(searchTerm.toLowerCase())
  );

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1">
          Category Management
        </Typography>
        <Stack direction="row" spacing={2}>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setAddDialogOpen(true)}
          >
            Add Category
          </Button>
          <Button
            variant="outlined"
            startIcon={<SearchIcon />}
            onClick={loadCategories}
          >
            Refresh
          </Button>
        </Stack>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <TextField
        fullWidth
        variant="outlined"
        placeholder="Search categories..."
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
        sx={{ mb: 3 }}
        InputProps={{
          startAdornment: <SearchIcon sx={{ mr: 1, color: 'text.secondary' }} />,
        }}
      />

      {filteredCategories.length === 0 && !loading && (
        <Card>
          <CardContent>
            <Box textAlign="center" py={4}>
              <Typography variant="h6" color="text.secondary" gutterBottom>
                {categories.length === 0 ? 'No categories yet' : 'No matching categories'}
              </Typography>
              <Typography variant="body2" color="text.secondary" mb={3}>
                {categories.length === 0 
                  ? 'Get started by adding your first category'
                  : 'Try adjusting your search terms'
                }
              </Typography>
              {categories.length === 0 && (
                <Button
                  variant="contained"
                  startIcon={<AddIcon />}
                  onClick={() => setAddDialogOpen(true)}
                >
                  Add Your First Category
                </Button>
              )}
            </Box>
          </CardContent>
        </Card>
      )}

      <Stack spacing={3}>
        {filteredCategories.map((category) => (
          <Box key={category.categoryCode}>
            <Accordion>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Box display="flex" alignItems="center" width="100%" pr={2}>
                  <Box flexGrow={1}>
                    <Typography variant="h6">{category.categoryName}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {category.categoryCode}
                    </Typography>
                  </Box>
                  <Stack direction="row" spacing={1}>
                    {category.passThrough && (
                      <Chip label="Pass-Through" color="primary" size="small" />
                    )}
                    {category.isCredit && (
                      <Chip label="Credit" color="success" size="small" />
                    )}
                    {!category.passThrough && !category.isCredit && (
                      <Chip label="Internal" color="default" size="small" />
                    )}
                  </Stack>
                </Box>
              </AccordionSummary>
              <AccordionDetails>
                <Box>
                  {/* Description */}
                  <Box mb={2}>
                    <Typography variant="subtitle2" color="primary" gutterBottom>
                      Description
                    </Typography>
                    <Typography variant="body2">{category.description}</Typography>
                  </Box>

                  <Divider sx={{ my: 2 }} />

                  {/* Keywords */}
                  <Box mb={2}>
                    <Typography variant="subtitle2" color="primary" gutterBottom>
                      Trigger Keywords
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {category.keywords?.map((keyword, idx) => (
                        <Chip key={idx} label={keyword} size="small" variant="outlined" />
                      ))}
                    </Stack>
                  </Box>

                  <Divider sx={{ my: 2 }} />

                  {/* Examples */}
                  {category.examples && category.examples.length > 0 && (
                    <Box mb={2}>
                      <Typography variant="subtitle2" color="primary" gutterBottom>
                        Example Line Items
                      </Typography>
                      <ul style={{ margin: 0, paddingLeft: 20 }}>
                        {category.examples.map((example, idx) => (
                          <li key={idx}>
                            <Typography variant="body2" color="text.secondary">
                              {example}
                            </Typography>
                          </li>
                        ))}
                      </ul>
                    </Box>
                  )}

                  {/* Statistics */}
                  {category.statistics && (
                    <>
                      <Divider sx={{ my: 2 }} />
                      <Box>
                        <Typography variant="subtitle2" color="primary" gutterBottom>
                          Usage Statistics
                        </Typography>
                        <Typography variant="body2">
                          Total items categorized: {category.statistics.totalItemsCategorized}
                        </Typography>
                      </Box>
                    </>
                  )}

                  {/* Edit and Delete Buttons */}
                  <Box mt={2} display="flex" justifyContent="flex-end" gap={2}>
                    <Button
                      variant="outlined"
                      color="error"
                      startIcon={<DeleteIcon />}
                      onClick={() => handleDeleteClick(category)}
                    >
                      Delete
                    </Button>
                    <Button
                      variant="contained"
                      startIcon={<EditIcon />}
                      onClick={() => handleEditClick(category)}
                    >
                      Edit Category
                    </Button>
                  </Box>
                </Box>
              </AccordionDetails>
            </Accordion>
          </Box>
        ))}
      </Stack>

      {/* Add Category Dialog */}
      <Dialog
        open={addDialogOpen}
        onClose={() => setAddDialogOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Add New Category</DialogTitle>
        <DialogContent>
          <Alert severity="info" sx={{ mb: 2 }}>
            Create a new category for your tenant. This will not affect other tenants.
          </Alert>
          <TextField
            fullWidth
            required
            label="Category Code"
            value={newCategory.categoryCode}
            onChange={(e) => setNewCategory({ ...newCategory, categoryCode: e.target.value.toUpperCase() })}
            margin="normal"
            helperText="Unique identifier (e.g., CUSTOM_ENERGY). Will be converted to uppercase."
          />
          <TextField
            fullWidth
            required
            label="Category Name"
            value={newCategory.categoryName}
            onChange={(e) => setNewCategory({ ...newCategory, categoryName: e.target.value })}
            margin="normal"
            helperText="Display name (e.g., Custom: Energy Charges)"
          />
          <TextField
            fullWidth
            label="Description"
            multiline
            rows={3}
            value={newCategory.description}
            onChange={(e) => setNewCategory({ ...newCategory, description: e.target.value })}
            margin="normal"
            helperText="Describe what types of charges belong in this category"
          />
          <TextField
            fullWidth
            label="Keywords (comma-separated)"
            value={newCategory.keywords}
            onChange={(e) => setNewCategory({ ...newCategory, keywords: e.target.value })}
            margin="normal"
            helperText="Keywords help automatically categorize line items"
          />
          <Box mt={2}>
            <FormControlLabel
              control={
                <Checkbox
                  checked={newCategory.isPassThrough}
                  onChange={(e) => setNewCategory({ ...newCategory, isPassThrough: e.target.checked })}
                />
              }
              label="Pass-Through (charges passed to customers)"
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={newCategory.isCredit}
                  onChange={(e) => setNewCategory({ ...newCategory, isCredit: e.target.checked })}
                />
              }
              label="Credit (reduces customer bills)"
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={newCategory.isBillable}
                  onChange={(e) => setNewCategory({ ...newCategory, isBillable: e.target.checked })}
                />
              }
              label="Billable"
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddDialogOpen(false)}>Cancel</Button>
          <Button 
            variant="contained" 
            onClick={handleAddCategory}
            disabled={loading || !newCategory.categoryCode || !newCategory.categoryName}
          >
            {loading ? 'Creating...' : 'Create Category'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit Dialog */}
      <Dialog
        open={editDialogOpen}
        onClose={handleCloseDialog}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Edit Category: {selectedCategory?.categoryName}</DialogTitle>
        <DialogContent>
          <Alert severity="info" sx={{ mb: 2 }}>
            Update the category description and keywords. Changes are saved to your tenant's
            category configuration and won't affect other tenants.
          </Alert>
          {selectedCategory && (
            <Box>
              <TextField
                fullWidth
                name="description"
                label="Description"
                multiline
                rows={3}
                defaultValue={selectedCategory.description}
                margin="normal"
                helperText="Describe what types of charges belong in this category"
              />
              <TextField
                fullWidth
                name="keywords"
                label="Keywords (comma-separated)"
                defaultValue={selectedCategory.keywords?.join(', ') || ''}
                margin="normal"
                helperText="Keywords help automatically categorize line items"
              />
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog}>Cancel</Button>
          <Button 
            variant="contained" 
            onClick={handleSaveCategory}
            disabled={loading}
          >
            {loading ? 'Saving...' : 'Save Changes'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteDialogOpen}
        onClose={() => setDeleteDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Confirm Delete Category</DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: 2 }}>
            Are you sure you want to delete this category?
          </Alert>
          {categoryToDelete && (
            <Box>
              <Typography variant="body1" gutterBottom>
                <strong>Category:</strong> {categoryToDelete.categoryName}
              </Typography>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                <strong>Code:</strong> {categoryToDelete.categoryCode}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                This will deactivate the category and it will no longer appear in category lists.
                Existing line items with this category will retain their categorization.
              </Typography>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>
            Cancel
          </Button>
          <Button
            onClick={handleConfirmDelete}
            color="error"
            variant="contained"
            disabled={loading}
          >
            {loading ? 'Deleting...' : 'Delete Category'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default CategoryManagement;

