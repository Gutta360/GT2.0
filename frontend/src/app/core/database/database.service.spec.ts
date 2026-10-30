import { describe, expect, it, vi } from 'vitest';
import { DatabaseService } from './database.service';

describe('DatabaseService atomic batch', () => {
  it('runs the complete write set in one native transaction', async () => {
    const connection = { executeSet: vi.fn().mockResolvedValue({ changes: { changes: 2 } }) };
    const service = new DatabaseService();
    (service as unknown as { connection: typeof connection }).connection = connection;
    service.state.set('ready');
    const statements = [{ statement: 'UPDATE one SET value = ?', values: [1] }, { statement: 'INSERT INTO two VALUES (?)', values: [2] }];
    await expect(service.executeBatch(statements)).resolves.toEqual({ changes: { changes: 2 } });
    expect(connection.executeSet).toHaveBeenCalledWith(statements, true);
  });

  it('propagates native transaction failures', async () => {
    const connection = { executeSet: vi.fn().mockRejectedValue(new Error('rolled back')) };
    const service = new DatabaseService();
    (service as unknown as { connection: typeof connection }).connection = connection;
    service.state.set('ready');
    await expect(service.executeBatch([{ statement: 'BROKEN' }])).rejects.toThrow('rolled back');
  });
});
