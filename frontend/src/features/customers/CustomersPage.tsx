import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { customerApi } from '@/api/endpoints';
import type { Customer, CustomerInput } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { Pagination } from '@/components/ui/Pagination';
import { LoadingState } from '@/components/ui/Spinner';
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/States';
import { errorMessage } from '@/lib/errors';
import { queryKeys } from '@/lib/queryClient';
import { useDebounced } from '@/lib/useDebounced';
import { CustomerForm } from './CustomerForm';

type Editing = { mode: 'create' } | { mode: 'edit'; customer: Customer } | null;

export function CustomersPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Editing>(null);
  const [deleting, setDeleting] = useState<Customer | null>(null);
  const debouncedSearch = useDebounced(search);

  const query = { search: debouncedSearch, page, size: 20 };
  const customers = useQuery({
    queryKey: queryKeys.customerList(query),
    queryFn: () => customerApi.list(query),
    placeholderData: keepPreviousData,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.customers });

  const save = useMutation({
    mutationFn: (input: CustomerInput) =>
      editing?.mode === 'edit' ? customerApi.update(editing.customer.id, input) : customerApi.create(input),
    onSuccess: (customer) => {
      toast.success(editing?.mode === 'edit' ? 'Customer updated' : `${customer.name} added`);
      setEditing(null);
      invalidate();
    },
  });

  const remove = useMutation({
    mutationFn: (id: string) => customerApi.remove(id),
    onSuccess: () => {
      toast.success('Customer deleted');
      setDeleting(null);
      invalidate();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <>
      <PageHeader
        title="Customers"
        description="The clients you invoice"
        actions={
          <Button onClick={() => setEditing({ mode: 'create' })}>
            <Plus className="h-4 w-4" aria-hidden="true" /> Add customer
          </Button>
        }
      />

      <div className="card">
        <div className="border-b border-slate-200 p-4">
          <label htmlFor="customer-search" className="sr-only">
            Search customers
          </label>
          <div className="relative max-w-sm">
            <Search
              className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-400"
              aria-hidden="true"
            />
            <input
              id="customer-search"
              type="search"
              className="input pl-9"
              placeholder="Search by name or email"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
          </div>
        </div>

        {customers.isLoading && <LoadingState />}
        {customers.error && <ErrorState error={customers.error} onRetry={() => customers.refetch()} />}
        {customers.data && customers.data.items.length === 0 && (
          <EmptyState
            title={debouncedSearch ? 'No matching customers' : 'No customers yet'}
            description={
              debouncedSearch ? 'Try a different search.' : 'Add the clients you bill to start invoicing.'
            }
            action={
              !debouncedSearch && <Button onClick={() => setEditing({ mode: 'create' })}>Add customer</Button>
            }
          />
        )}
        {customers.data && customers.data.items.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200">
                <thead className="bg-slate-50">
                  <tr>
                    <th scope="col" className="table-head">
                      Name
                    </th>
                    <th scope="col" className="table-head">
                      Email
                    </th>
                    <th scope="col" className="table-head hidden md:table-cell">
                      Phone
                    </th>
                    <th scope="col" className="table-head hidden lg:table-cell">
                      City
                    </th>
                    <th scope="col" className="table-head text-right">
                      <span className="sr-only">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 bg-white">
                  {customers.data.items.map((c) => (
                    <tr key={c.id}>
                      <td className="table-cell font-medium text-slate-900">{c.name}</td>
                      <td className="table-cell text-slate-600">{c.email}</td>
                      <td className="table-cell hidden text-slate-600 md:table-cell">{c.phone ?? '—'}</td>
                      <td className="table-cell hidden text-slate-600 lg:table-cell">
                        {c.billingAddress.city ?? '—'}
                      </td>
                      <td className="table-cell text-right">
                        <div className="flex justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            aria-label={`Edit ${c.name}`}
                            onClick={() => setEditing({ mode: 'edit', customer: c })}
                          >
                            <Pencil className="h-4 w-4" aria-hidden="true" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            aria-label={`Delete ${c.name}`}
                            onClick={() => setDeleting(c)}
                          >
                            <Trash2 className="h-4 w-4" aria-hidden="true" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={customers.data.page}
              totalPages={customers.data.totalPages}
              totalItems={customers.data.totalItems}
              onPageChange={setPage}
            />
          </>
        )}
      </div>

      <Modal
        open={editing !== null}
        title={editing?.mode === 'edit' ? 'Edit customer' : 'Add customer'}
        onClose={() => setEditing(null)}
      >
        {editing && (
          <CustomerForm
            customer={editing.mode === 'edit' ? editing.customer : undefined}
            onSubmit={(input) => save.mutateAsync(input)}
            onCancel={() => setEditing(null)}
          />
        )}
      </Modal>

      <Modal
        open={deleting !== null}
        title="Delete customer?"
        onClose={() => setDeleting(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setDeleting(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={remove.isPending}
              onClick={() => deleting && remove.mutate(deleting.id)}
            >
              Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-slate-600">
          <strong>{deleting?.name}</strong> will be removed. Customers with invoices cannot be deleted.
        </p>
      </Modal>
    </>
  );
}
