import { useQuery } from '@tanstack/react-query';
import { businessApi } from '@/api/endpoints';
import { todayIso } from './format';
import { queryKeys } from './queryClient';

/**
 * The business's current date as yyyy-mm-dd, from the server. The browser's own date can differ
 * (time zones, or the demo clock being moved), and the server is the one that validates dates.
 * Until the server answers (or if it can't), the browser's date is used; {@code ready} tells
 * forms when it is safe to read the value as a default.
 */
export function useBusinessToday(): { today: string; ready: boolean } {
  const query = useQuery({
    queryKey: queryKeys.businessToday,
    queryFn: businessApi.today,
    staleTime: 60_000,
  });
  return { today: query.data?.today ?? todayIso(), ready: !query.isPending };
}
