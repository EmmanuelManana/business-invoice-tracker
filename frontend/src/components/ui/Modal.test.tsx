import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from './Modal';

/** A parent that re-renders on every keystroke and passes a fresh onClose each time. */
function Parent({ onClose }: { onClose: () => void }) {
  const [text, setText] = useState('');
  return (
    <Modal open title="Edit customer" onClose={() => onClose()}>
      <label>
        Name <input />
      </label>
      <label>
        City <input value={text} onChange={(e) => setText(e.target.value)} />
      </label>
    </Modal>
  );
}

describe('Modal', () => {
  it('focuses the first field when it opens', () => {
    render(<Parent onClose={vi.fn()} />);
    expect(screen.getByLabelText('Name')).toHaveFocus();
  });

  it('keeps focus in the field being typed in when the parent re-renders', async () => {
    render(<Parent onClose={vi.fn()} />);

    await userEvent.click(screen.getByLabelText('City'));
    await userEvent.keyboard('Cape Town');

    expect(screen.getByLabelText('City')).toHaveFocus();
    expect(screen.getByLabelText('City')).toHaveValue('Cape Town');
  });

  it('closes on Escape using the latest onClose', async () => {
    const onClose = vi.fn();
    render(<Parent onClose={onClose} />);

    await userEvent.click(screen.getByLabelText('City'));
    await userEvent.keyboard('x{Escape}');

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('is labelled by its title', () => {
    render(<Parent onClose={vi.fn()} />);
    expect(screen.getByRole('dialog', { name: 'Edit customer' })).toBeInTheDocument();
  });
});
