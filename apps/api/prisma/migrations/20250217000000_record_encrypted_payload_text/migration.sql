-- Ensure encrypted_payload_b64 supports large payloads (API limit 512KB). Explicit TEXT type.
ALTER TABLE "records" ALTER COLUMN "encrypted_payload_b64" SET DATA TYPE TEXT;
