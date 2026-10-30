import { Injectable } from '@angular/core';

export interface PasswordDigest { salt: string; hash: string; iterations: number }

@Injectable({ providedIn: 'root' })
export class CryptoService {
  static readonly ITERATIONS = 310_000;

  async hashPassword(password: string, saltBase64?: string, iterations = CryptoService.ITERATIONS): Promise<PasswordDigest> {
    const salt = saltBase64 ? this.fromBase64(saltBase64) : crypto.getRandomValues(new Uint8Array(16));
    const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits']);
    const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: salt as BufferSource, iterations }, key, 256);
    return { salt: this.toBase64(salt), hash: this.toBase64(new Uint8Array(bits)), iterations };
  }

  async verify(password: string, expected: PasswordDigest): Promise<boolean> {
    const actual = await this.hashPassword(password, expected.salt, expected.iterations);
    const left = this.fromBase64(actual.hash);
    const right = this.fromBase64(expected.hash);
    if (left.length !== right.length) return false;
    let mismatch = 0;
    for (let i = 0; i < left.length; i++) mismatch |= left[i]! ^ right[i]!;
    return mismatch === 0;
  }

  private toBase64(bytes: Uint8Array): string { return btoa(Array.from(bytes, b => String.fromCharCode(b)).join('')); }
  private fromBase64(value: string): Uint8Array { return Uint8Array.from(atob(value), char => char.charCodeAt(0)); }
}
