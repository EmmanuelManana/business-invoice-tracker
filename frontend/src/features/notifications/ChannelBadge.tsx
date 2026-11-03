import type { NotificationChannel } from '@/api/types';
import { Badge } from '@/components/ui/StatusBadge';

const labels: Record<NotificationChannel, { text: string; tone: 'blue' | 'green' | 'amber' }> = {
  EMAIL: { text: 'Email', tone: 'blue' },
  SMS: { text: 'SMS', tone: 'green' },
  IN_APP: { text: 'Alert', tone: 'amber' },
};

export function ChannelBadge({ channel }: { channel: NotificationChannel }) {
  const { text, tone } = labels[channel];
  return <Badge tone={tone}>{text}</Badge>;
}
