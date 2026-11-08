import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ApiError } from '@/api/client';
import { businessApi, customerApi, invoiceApi } from '@/api/endpoints';
import { LoadingState } from '@/components/ui/Spinner';
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/States';
import { addDaysIso } from '@/lib/format';
import { useBusinessToday } from '@/lib/useBusinessToday';
import { queryKeys } from '@/lib/queryClient';
import { InvoiceForm } from './InvoiceForm';
import { emptyLine, fromInvoice, toInvoiceInput, type InvoiceFormValues } from './invoiceSchema';

/** Create a new draft (/invoices/new) or edit an existing draft (/invoices/:id/edit). */
export function InvoiceEditorPage() {
  const { id } = useParams();
  const isEdit = Boolean(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const customers = useQuery({
    queryKey: queryKeys.customerList({ all: true }),
    queryFn: () => customerApi.list({ size: 100 }),
  });
  const business = useQuery({ queryKey: queryKeys.business, queryFn: businessApi.get });
  const { today: businessToday, ready: todayReady } = useBusinessToday();
  const invoice = useQuery({
    queryKey: queryKeys.invoice(id ?? ''),
    queryFn: () => invoiceApi.get(id!),
    enabled: isEdit,
  });

  const loading = !todayReady || customers.isLoading || business.isLoading || (isEdit && invoice.isLoading);
  const error = customers.error ?? business.error ?? invoice.error;

  async function handleSubmit(values: InvoiceFormValues) {
    const input = toInvoiceInput(values);
    try {
      const saved = isEdit
        ? await invoiceApi.update(id!, invoice.data!.version, input)
        : await invoiceApi.create(input);
      queryClient.invalidateQueries({ queryKey: queryKeys.invoices });
      queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
      toast.success(isEdit ? `${saved.number} updated` : `Draft ${saved.number} created`);
      navigate(`/invoices/${saved.id}`);
    } catch (e) {
      if (e instanceof ApiError && e.status === 412) {
        toast.error('Someone else changed this invoice. The latest version has been loaded.');
        await invoice.refetch();
        return;
      }
      throw e;
    }
  }

  const title = isEdit ? `Edit ${invoice.data?.number ?? 'invoice'}` : 'New invoice';

  if (loading) return <LoadingState />;
  if (error) return <ErrorState error={error} />;
  if (customers.data?.items.length === 0) {
    return (
      <>
        <PageHeader title={title} />
        <div className="card">
          <EmptyState
            title="Add a customer first"
            description="Invoices are addressed to a customer."
            action={
              <Link to="/customers" className="text-sm font-medium text-brand-600 hover:underline">
                Go to customers
              </Link>
            }
          />
        </div>
      </>
    );
  }
  if (isEdit && invoice.data && invoice.data.status !== 'DRAFT') {
    return (
      <>
        <PageHeader title={title} />
        <div className="card">
          <EmptyState
            title="This invoice can no longer be edited"
            description="Only draft invoices can be changed. Cancel it and create a new one if it is wrong."
            action={
              <Link to={`/invoices/${id}`} className="text-sm font-medium text-brand-600 hover:underline">
                Back to invoice
              </Link>
            }
          />
        </div>
      </>
    );
  }

  const today = businessToday;
  const defaults: InvoiceFormValues =
    isEdit && invoice.data
      ? fromInvoice(invoice.data)
      : {
          customerId: '',
          issueDate: today,
          dueDate: addDaysIso(today, business.data?.paymentTermsDays ?? 30),
          notes: '',
          lineItems: [emptyLine()],
        };

  return (
    <>
      <PageHeader
        title={title}
        description={isEdit ? 'Drafts can be edited until they are sent.' : 'Saved as a draft.'}
      />
      <InvoiceForm
        key={invoice.data?.version ?? 'new'}
        customers={customers.data?.items ?? []}
        defaultValues={defaults}
        currency={business.data?.currency}
        submitLabel={isEdit ? 'Save changes' : 'Save draft'}
        onSubmit={handleSubmit}
        onCancel={() => navigate(isEdit ? `/invoices/${id}` : '/invoices')}
      />
    </>
  );
}
