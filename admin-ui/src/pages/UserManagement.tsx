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
  TablePagination,
  Button,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  CircularProgress,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Stack,
  Tooltip,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import BlockIcon from '@mui/icons-material/Block';
import PendingIcon from '@mui/icons-material/Pending';
import api from '../services/api';

interface User {
  id: number;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  fullName: string;
  tenantId?: string;
  role: string;
  status: string;
  isActive: boolean;
  isEmailVerified: boolean;
  lastLoginAt?: string;
  createdAt: string;
  updatedAt: string;
}

interface Tenant {
  id: number;
  tenantId: string;
  tenantName: string;
  description?: string;
  active: boolean;
}

const UserManagement: React.FC = () => {
  const [users, setUsers] = useState<User[]>([]);
  const [filteredUsers, setFilteredUsers] = useState<User[]>([]);
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [assignDialogOpen, setAssignDialogOpen] = useState(false);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [tenantId, setTenantId] = useState('');
  const [newRole, setNewRole] = useState('');

  const currentUser = JSON.parse(localStorage.getItem('user') || '{}');

  useEffect(() => {
    loadUsers();
    loadTenants();
  }, []);

  useEffect(() => {
    filterUsers();
  }, [statusFilter, users]);

  const loadUsers = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.get('/api/admin/users');
      setUsers(response.data);
      setFilteredUsers(response.data);
    } catch (err: any) {
      console.error('Failed to load users:', err);
      setError(err.response?.data?.error || 'Failed to load users. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const loadTenants = async () => {
    try {
      const response = await api.get('/api/admin/users/tenants');
      setTenants(response.data);
    } catch (err: any) {
      console.error('Failed to load tenants:', err);
      // Don't show error to user - tenants list is not critical
    }
  };

  const filterUsers = () => {
    let filtered = [...users];

    if (statusFilter !== 'ALL') {
      filtered = filtered.filter((u) => u.status === statusFilter);
    }

    setFilteredUsers(filtered);
    setPage(0);
  };

  const handleChangePage = (event: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const handleAssignTenant = async () => {
    if (!selectedUser || !tenantId) return;

    try {
      await api.post(`/api/admin/users/${selectedUser.id}/assign-tenant`, {
        tenantId: tenantId,
      });
      setSuccess(`User ${selectedUser.username} assigned to tenant ${tenantId}`);
      setAssignDialogOpen(false);
      setTenantId('');
      loadUsers();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to assign tenant');
    }
  };

  const handleUpdateUser = async () => {
    if (!selectedUser) return;

    const updates: Promise<any>[] = [];
    const messages: string[] = [];

    // Update tenant if changed
    if (tenantId && tenantId !== selectedUser.tenantId) {
      updates.push(
        api.post(`/api/admin/users/${selectedUser.id}/assign-tenant`, {
          tenantId: tenantId,
        }).then(() => {
          messages.push(`tenant changed to ${tenantId}`);
        })
      );
    }

    // Update role if changed
    if (newRole && newRole !== selectedUser.role) {
      updates.push(
        api.put(`/api/admin/users/${selectedUser.id}/role`, {
          role: newRole,
        }).then(() => {
          messages.push(`role changed to ${newRole}`);
        })
      );
    }

    if (updates.length === 0) {
      setError('No changes to save');
      return;
    }

    try {
      await Promise.all(updates);
      setSuccess(`User ${selectedUser.username} updated: ${messages.join(', ')}`);
      setEditDialogOpen(false);
      setTenantId('');
      setNewRole('');
      loadUsers();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to update user');
    }
  };

  const handleUpdateStatus = async (userId: number, status: string) => {
    try {
      await api.put(`/api/admin/users/${userId}/status`, { status });
      setSuccess(`User status updated to ${status}`);
      loadUsers();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to update status');
    }
  };

  const handleDeleteUser = async (userId: number, username: string) => {
    if (!window.confirm(`Are you sure you want to deactivate user ${username}?`)) {
      return;
    }

    try {
      await api.delete(`/api/admin/users/${userId}`);
      setSuccess(`User ${username} deactivated successfully`);
      loadUsers();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to deactivate user');
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return <CheckCircleIcon color="success" />;
      case 'PENDING':
        return <PendingIcon color="warning" />;
      case 'SUSPENDED':
      case 'DEACTIVATED':
        return <BlockIcon color="error" />;
      default:
        return null;
    }
  };

  const getRoleColor = (role: string): 'error' | 'warning' | 'default' => {
    switch (role) {
      case 'GLOBAL_ADMIN':
        return 'error';
      case 'TENANT_ADMIN':
        return 'warning';
      default:
        return 'default';
    }
  };

  const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
    switch (status) {
      case 'ACTIVE':
        return 'success';
      case 'PENDING':
        return 'warning';
      case 'SUSPENDED':
      case 'DEACTIVATED':
        return 'error';
      default:
        return 'default';
    }
  };

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1">
          User Management
        </Typography>
        <Button
          variant="outlined"
          startIcon={<RefreshIcon />}
          onClick={loadUsers}
          disabled={loading}
        >
          Refresh
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {success && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess(null)}>
          {success}
        </Alert>
      )}

      {currentUser.status === 'PENDING' && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Your account is pending approval. You have limited access until an administrator assigns you to a tenant.
        </Alert>
      )}

      {loading ? (
        <Box display="flex" justifyContent="center" alignItems="center" py={8}>
          <CircularProgress />
        </Box>
      ) : (
        <>
          {/* Filters */}
          <Paper sx={{ p: 2, mb: 2 }}>
            <Stack direction="row" spacing={2} alignItems="center">
              <FormControl size="small" sx={{ minWidth: 200 }}>
                <InputLabel>Status Filter</InputLabel>
                <Select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  label="Status Filter"
                >
                  <MenuItem value="ALL">All Statuses</MenuItem>
                  <MenuItem value="PENDING">Pending</MenuItem>
                  <MenuItem value="ACTIVE">Active</MenuItem>
                  <MenuItem value="SUSPENDED">Suspended</MenuItem>
                  <MenuItem value="DEACTIVATED">Deactivated</MenuItem>
                </Select>
              </FormControl>
              <Typography variant="body2" color="text.secondary">
                Total: {filteredUsers.length} users
              </Typography>
            </Stack>
          </Paper>

          {/* Table */}
          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>ID</TableCell>
                  <TableCell>Username</TableCell>
                  <TableCell>Email</TableCell>
                  <TableCell>Full Name</TableCell>
                  <TableCell>Tenant ID</TableCell>
                  <TableCell>Role</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Last Login</TableCell>
                  <TableCell align="center">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredUsers
                  .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                  .map((user) => (
                    <TableRow key={user.id} hover>
                      <TableCell>{user.id}</TableCell>
                      <TableCell>{user.username}</TableCell>
                      <TableCell>{user.email}</TableCell>
                      <TableCell>{user.fullName}</TableCell>
                      <TableCell>
                        {user.tenantId || (
                          <Typography variant="body2" color="text.secondary" fontStyle="italic">
                            Not assigned
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Chip label={user.role} color={getRoleColor(user.role)} size="small" />
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={1} alignItems="center">
                          {getStatusIcon(user.status)}
                          <Chip
                            label={user.status}
                            color={getStatusColor(user.status)}
                            size="small"
                          />
                        </Stack>
                      </TableCell>
                      <TableCell>
                        {user.lastLoginAt ? (
                          <Typography variant="body2">
                            {new Date(user.lastLoginAt).toLocaleDateString()}
                          </Typography>
                        ) : (
                          <Typography variant="body2" color="text.secondary">
                            Never
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell align="center">
                        <Stack direction="row" spacing={1} justifyContent="center">
                          {user.status === 'PENDING' && (
                            <Tooltip title="Assign to Tenant">
                              <IconButton
                                size="small"
                                color="primary"
                                onClick={() => {
                                  setSelectedUser(user);
                                  setTenantId('');
                                  setAssignDialogOpen(true);
                                }}
                              >
                                <CheckCircleIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          )}
                          {(currentUser.role === 'GLOBAL_ADMIN' ||
                            (currentUser.role === 'TENANT_ADMIN' &&
                              user.tenantId === currentUser.tenantId)) && (
                            <>
                              <Tooltip title="Edit User">
                                <IconButton
                                  size="small"
                                  color="info"
                                  onClick={() => {
                                    setSelectedUser(user);
                                    setTenantId(user.tenantId || '');
                                    setNewRole(user.role);
                                    setEditDialogOpen(true);
                                  }}
                                >
                                  <EditIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              {user.id !== currentUser.id && (
                                <Tooltip title="Deactivate User">
                                  <IconButton
                                    size="small"
                                    color="error"
                                    onClick={() => handleDeleteUser(user.id, user.username)}
                                  >
                                    <DeleteIcon fontSize="small" />
                                  </IconButton>
                                </Tooltip>
                              )}
                            </>
                          )}
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
            <TablePagination
              rowsPerPageOptions={[5, 10, 25, 50]}
              component="div"
              count={filteredUsers.length}
              rowsPerPage={rowsPerPage}
              page={page}
              onPageChange={handleChangePage}
              onRowsPerPageChange={handleChangeRowsPerPage}
            />
          </TableContainer>

          {filteredUsers.length === 0 && !loading && (
            <Box textAlign="center" py={4}>
              <Typography variant="body1" color="text.secondary">
                No users found.
              </Typography>
            </Box>
          )}
        </>
      )}

      {/* Assign/Change Tenant Dialog */}
      <Dialog open={assignDialogOpen} onClose={() => setAssignDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          {selectedUser?.status === 'PENDING' ? 'Assign User to Tenant' : 'Change User Tenant'}
        </DialogTitle>
        <DialogContent>
          {selectedUser && (
            <Box mt={2}>
              <Typography variant="subtitle2" gutterBottom>
                User: {selectedUser.username} ({selectedUser.email})
              </Typography>
              {selectedUser.tenantId && (
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  Current Tenant: <strong>{selectedUser.tenantId}</strong>
                </Typography>
              )}
              <FormControl fullWidth margin="normal">
                <InputLabel>Tenant</InputLabel>
                <Select
                  value={tenantId}
                  onChange={(e) => setTenantId(e.target.value)}
                  label="Tenant"
                >
                  {tenants.map((tenant) => (
                    <MenuItem key={tenant.tenantId} value={tenant.tenantId}>
                      {tenant.tenantName} ({tenant.tenantId})
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <Alert severity={selectedUser.status === 'PENDING' ? 'info' : 'warning'} sx={{ mt: 2 }}>
                {selectedUser.status === 'PENDING' 
                  ? "After assigning a tenant, the user's status will be changed to ACTIVE."
                  : "Changing the tenant will affect the user's access to data. They will only be able to access data for the new tenant."}
              </Alert>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAssignDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleAssignTenant} disabled={!tenantId}>
            {selectedUser?.status === 'PENDING' ? 'Assign Tenant' : 'Change Tenant'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit User Dialog */}
      <Dialog open={editDialogOpen} onClose={() => setEditDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Edit User</DialogTitle>
        <DialogContent>
          {selectedUser && (
            <Box mt={2}>
              <Typography variant="subtitle2" gutterBottom>
                User: {selectedUser.username} ({selectedUser.email})
              </Typography>

              {/* Tenant Selection */}
              <FormControl fullWidth margin="normal">
                <InputLabel>Tenant</InputLabel>
                <Select
                  value={tenantId}
                  onChange={(e) => setTenantId(e.target.value)}
                  label="Tenant"
                >
                  {tenants.map((tenant) => (
                    <MenuItem key={tenant.tenantId} value={tenant.tenantId}>
                      {tenant.tenantName} ({tenant.tenantId})
                    </MenuItem>
                  ))}
                </Select>
                {selectedUser.tenantId && (
                  <Typography variant="caption" sx={{ mt: 0.5, display: 'block' }}>
                    Current: {selectedUser.tenantId}
                  </Typography>
                )}
              </FormControl>

              {/* Role Selection */}
              <FormControl fullWidth margin="normal">
                <InputLabel>Role</InputLabel>
                <Select
                  value={newRole}
                  onChange={(e) => setNewRole(e.target.value)}
                  label="Role"
                >
                  <MenuItem value="USER">User</MenuItem>
                  <MenuItem value="TENANT_ADMIN">Tenant Admin</MenuItem>
                  {currentUser.role === 'GLOBAL_ADMIN' && (
                    <MenuItem value="GLOBAL_ADMIN">Global Admin</MenuItem>
                  )}
                </Select>
              </FormControl>

              <Alert severity="info" sx={{ mt: 2 }}>
                <strong>Role Permissions:</strong>
                <br />
                <strong>User:</strong> Can view/manage their own data
                <br />
                <strong>Tenant Admin:</strong> Can manage users within their tenant
                <br />
                <strong>Global Admin:</strong> Can manage all tenants and users
              </Alert>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleUpdateUser}>
            Save Changes
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default UserManagement;

