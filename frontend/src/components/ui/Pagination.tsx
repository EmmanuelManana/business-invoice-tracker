import { Button } from './Button';

interface PaginationProps {
  page: number;
  totalPages: number;
  totalItems: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, totalItems, onPageChange }: PaginationProps) {
  if (totalPages <= 1) {
    return <p className="px-4 py-3 text-xs text-slate-500">{totalItems} result(s)</p>;
  }
  return (
    <nav
      className="flex items-center justify-between border-t border-slate-200 px-4 py-3"
      aria-label="Pagination"
    >
      <p className="text-xs text-slate-500">
        Page {page + 1} of {totalPages} · {totalItems} result(s)
      </p>
      <div className="flex gap-2">
        <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => onPageChange(page - 1)}>
          Previous
        </Button>
        <Button
          variant="secondary"
          size="sm"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          Next
        </Button>
      </div>
    </nav>
  );
}
