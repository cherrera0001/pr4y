/**
 * Vault: manages DEK lifecycle in the browser.
 *
 * Flow:
 * 1. User logs in → fetch wrappedDek from server
 * 2. User enters passphrase → derive KEK → unwrap DEK → store in memory
 * 3. DEK used to encrypt/decrypt content
 * 4. On logout/close → DEK cleared from memory
 *
 * First-time setup:
 * 1. User enters passphrase → derive KEK with random salt
 * 2. Generate random DEK
 * 3. Wrap DEK with KEK → store wrappedDek on server
 */

import {
  deriveKek,
  generateDek,
  wrapDek,
  unwrapDek,
  generateSaltB64,
} from './crypto';
import { authFetch } from './auth-client';

interface WrappedDekData {
  kdf: {
    name: string;
    params: { iterations: number };
    saltB64: string;
  };
  wrappedDekB64: string;
}

let memoryDek: CryptoKey | null = null;

/** Get the current DEK (null if vault is locked). */
export function getDek(): CryptoKey | null {
  return memoryDek;
}

/** Check if the vault is unlocked. */
export function isVaultUnlocked(): boolean {
  return memoryDek !== null;
}

/** Clear DEK from memory (lock the vault). */
export function lockVault(): void {
  memoryDek = null;
}

/** Fetch the wrapped DEK from the server. Returns null if not set up. */
export async function fetchWrappedDek(): Promise<WrappedDekData | null> {
  const res = await authFetch('/crypto/wrapped-dek');
  if (res.status === 404) return null;
  if (!res.ok) throw new Error('Failed to fetch wrapped DEK');
  return res.json();
}

/**
 * Unlock the vault with a passphrase.
 * Fetches wrappedDek from server, derives KEK, unwraps DEK.
 */
export async function unlockVault(passphrase: string): Promise<void> {
  const data = await fetchWrappedDek();
  if (!data) {
    throw new Error('SETUP_REQUIRED');
  }

  const kek = await deriveKek(passphrase, data.kdf.saltB64);
  try {
    memoryDek = await unwrapDek(data.wrappedDekB64, kek);
  } catch {
    throw new Error('WRONG_PASSPHRASE');
  }
}

/**
 * First-time setup: create DEK, wrap with KEK derived from passphrase,
 * store on server.
 */
export async function setupVault(passphrase: string): Promise<void> {
  const saltB64 = generateSaltB64();
  const kek = await deriveKek(passphrase, saltB64);
  const dek = await generateDek();
  const wrappedDekB64 = await wrapDek(dek, kek);

  const res = await authFetch('/crypto/wrapped-dek', {
    method: 'PUT',
    body: JSON.stringify({
      kdf: {
        name: 'pbkdf2',
        params: { iterations: 120000 },
        saltB64,
      },
      wrappedDekB64,
    }),
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body?.error?.message || 'Failed to store wrapped DEK');
  }

  memoryDek = dek;
}
