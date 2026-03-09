/**
 * Web Crypto E2EE — compatible with Android LocalCrypto + DekManager.
 *
 * AES-256-GCM, 12-byte IV, 128-bit tag.
 * PBKDF2: SHA-256, 120,000 iterations, 16-byte salt.
 * Format: Base64(IV || ciphertext+tag)
 */

const GCM_IV_LENGTH = 12;
const GCM_TAG_BITS = 128;
const PBKDF2_ITERATIONS = 120_000;
const SALT_LENGTH = 16;
const KEY_BITS = 256;

// ---------- Helpers ----------

function toBase64(buf: ArrayBuffer): string {
  const bytes = new Uint8Array(buf);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function fromBase64(b64: string): Uint8Array {
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

// ---------- AES-GCM ----------

/** Encrypt plainBytes with AES-256-GCM. Returns Base64(IV || ciphertext+tag). */
export async function encrypt(
  plainBytes: Uint8Array,
  key: CryptoKey
): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(GCM_IV_LENGTH));
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv, tagLength: GCM_TAG_BITS },
    key,
    plainBytes
  );
  const combined = new Uint8Array(iv.byteLength + ciphertext.byteLength);
  combined.set(iv, 0);
  combined.set(new Uint8Array(ciphertext), iv.byteLength);
  return toBase64(combined.buffer);
}

/** Decrypt Base64(IV || ciphertext+tag) with AES-256-GCM. Returns plainBytes. */
export async function decrypt(
  encryptedB64: string,
  key: CryptoKey
): Promise<Uint8Array> {
  const combined = fromBase64(encryptedB64);
  const iv = combined.slice(0, GCM_IV_LENGTH);
  const ciphertext = combined.slice(GCM_IV_LENGTH);
  const plain = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv, tagLength: GCM_TAG_BITS },
    key,
    ciphertext
  );
  return new Uint8Array(plain);
}

// ---------- PBKDF2 (KEK derivation) ----------

/** Derive a KEK from passphrase + salt using PBKDF2-SHA256, 120k iterations. */
export async function deriveKek(
  passphrase: string,
  saltB64: string
): Promise<CryptoKey> {
  const enc = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    enc.encode(passphrase),
    'PBKDF2',
    false,
    ['deriveKey']
  );
  const salt = fromBase64(saltB64);
  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt,
      iterations: PBKDF2_ITERATIONS,
      hash: 'SHA-256',
    },
    keyMaterial,
    { name: 'AES-GCM', length: KEY_BITS },
    true, // extractable needed for wrap/unwrap compatibility
    ['encrypt', 'decrypt']
  );
}

// ---------- DEK management ----------

/** Generate a random 256-bit AES-GCM key (DEK). */
export async function generateDek(): Promise<CryptoKey> {
  return crypto.subtle.generateKey(
    { name: 'AES-GCM', length: KEY_BITS },
    true,
    ['encrypt', 'decrypt']
  );
}

/** Wrap DEK with KEK → Base64(IV || ciphertext+tag). Same format as Android. */
export async function wrapDek(
  dek: CryptoKey,
  kek: CryptoKey
): Promise<string> {
  const rawDek = await crypto.subtle.exportKey('raw', dek);
  return encrypt(new Uint8Array(rawDek), kek);
}

/** Unwrap DEK from Base64(IV || ciphertext+tag) using KEK. */
export async function unwrapDek(
  wrappedDekB64: string,
  kek: CryptoKey
): Promise<CryptoKey> {
  const rawBytes = await decrypt(wrappedDekB64, kek);
  return crypto.subtle.importKey(
    'raw',
    rawBytes,
    { name: 'AES-GCM', length: KEY_BITS },
    true,
    ['encrypt', 'decrypt']
  );
}

/** Generate a random salt as Base64. */
export function generateSaltB64(): string {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_LENGTH));
  return toBase64(salt.buffer);
}

// ---------- Content helpers ----------

/** Encrypt a JSON object → encryptedPayloadB64. */
export async function encryptPayload(
  payload: Record<string, unknown>,
  dek: CryptoKey
): Promise<string> {
  const json = JSON.stringify(payload);
  const bytes = new TextEncoder().encode(json);
  return encrypt(bytes, dek);
}

/** Decrypt encryptedPayloadB64 → parsed JSON object. */
export async function decryptPayload<T = Record<string, unknown>>(
  encryptedB64: string,
  dek: CryptoKey
): Promise<T> {
  const bytes = await decrypt(encryptedB64, dek);
  const json = new TextDecoder().decode(bytes);
  return JSON.parse(json) as T;
}
