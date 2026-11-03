import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Clock, FastForward, Play, RotateCcw } from 'lucide-react';
import { toast } from 'sonner';
import { ApiError } from '@/api/client';
import { devApi } from '@/api/endpoints';
import type { OverdueRunResult } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { errorMessage } from '@/lib/errors';
import { formatDate } from '@/lib/format';
import { queryKeys } from '@/lib/queryClient';

/**
 * Demo "time machine": shifts the server's business clock and runs the overdue job immediately,
 * so reviewers can watch invoices go overdue without waiting. Hidden when the server has dev tools
 * disabled (the endpoint returns 404).
 */
export function DevToolsCard() {
  const queryClient = useQueryClient();
  const clock = useQuery({
    queryKey: queryKeys.devClock,
    queryFn: devApi.clock,
    retry: false,
    staleTime: 0,
  });

  const refreshEverything = () => queryClient.invalidateQueries();

  const advance = useMutation({
    mutationFn: devApi.advance,
    onSuccess: (status) => {
      toast.info(`Business date is now ${formatDate(status.today)}`);
      refreshEverything();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const reset = useMutation({
    mutationFn: devApi.reset,
    onSuccess: () => {
      toast.info('Clock reset to real time');
      refreshEverything();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const run = useMutation({
    mutationFn: devApi.runOverdueCheck,
    onSuccess: (result) => {
      toast.success(describe(result));
      refreshEverything();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  if (clock.error instanceof ApiError && clock.error.status === 404) return null;
  if (!clock.data) return null;

  const busy = advance.isPending || reset.isPending || run.isPending;

  return (
    <section
      aria-labelledby="dev-tools-title"
      className="card border-dashed border-amber-300 bg-amber-50/40 p-4"
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 id="dev-tools-title" className="flex items-center gap-2 text-sm font-semibold text-amber-900">
            <Clock className="h-4 w-4" aria-hidden="true" /> Demo time machine
          </h2>
          <p className="mt-1 text-sm text-amber-900/80" data-testid="business-date">
            Business date: <strong>{formatDate(clock.data.today)}</strong>
            {clock.data.offsetDays > 0 && ` (+${clock.data.offsetDays} days simulated)`}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {[1, 7, 30].map((days) => (
            <Button
              key={days}
              variant="secondary"
              size="sm"
              disabled={busy}
              onClick={() => advance.mutate(days)}
            >
              <FastForward className="h-3.5 w-3.5" aria-hidden="true" /> +{days} day{days > 1 ? 's' : ''}
            </Button>
          ))}
          <Button
            variant="secondary"
            size="sm"
            disabled={busy || clock.data.offsetDays === 0}
            onClick={() => reset.mutate()}
          >
            <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" /> Reset
          </Button>
          <Button size="sm" loading={run.isPending} disabled={busy} onClick={() => run.mutate()}>
            <Play className="h-3.5 w-3.5" aria-hidden="true" /> Run overdue check now
          </Button>
        </div>
      </div>
    </section>
  );
}

function describe(result: OverdueRunResult): string {
  if (result.skipped) return 'Another overdue run is in progress; try again in a moment.';
  return `Overdue check done: ${result.invoicesMarkedOverdue} newly overdue, ${result.remindersQueued} reminder(s) and ${result.alertsQueued} alert(s) sent.`;
}
