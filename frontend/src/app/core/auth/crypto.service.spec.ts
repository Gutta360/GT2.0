import { describe, expect, it } from 'vitest';
import { CryptoService } from './crypto.service';

describe('CryptoService', () => {
  it('derives a salted hash and verifies only the matching password', async () => {
    const service = new CryptoService();
    const digest = await service.hashPassword('a long offline passphrase', undefined, 1_000);
    expect(digest.hash).not.toContain('a long offline passphrase');
    await expect(service.verify('a long offline passphrase', digest)).resolves.toBe(true);
    await expect(service.verify('wrong passphrase', digest)).resolves.toBe(false);
  });
});
