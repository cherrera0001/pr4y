-- Record: estado espiritual del pedido (PENDING, IN_PROCESS, ANSWERED)
ALTER TABLE "records" ADD COLUMN IF NOT EXISTS "status" TEXT NOT NULL DEFAULT 'PENDING';

CREATE INDEX IF NOT EXISTS "records_user_id_status_idx" ON "records"("user_id", "status");

-- Recordatorios: múltiples horarios por pedido
CREATE TABLE IF NOT EXISTS "reminders" (
    "id" TEXT NOT NULL,
    "record_id" TEXT NOT NULL,
    "time" TEXT NOT NULL,
    "days_of_week" JSONB NOT NULL,
    "is_enabled" BOOLEAN NOT NULL DEFAULT true,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "reminders_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "reminders_record_id_idx" ON "reminders"("record_id");

ALTER TABLE "reminders" ADD CONSTRAINT "reminders_record_id_fkey" FOREIGN KEY ("record_id") REFERENCES "records"("record_id") ON DELETE CASCADE ON UPDATE CASCADE;

-- Testimonios: cuándo/cómo respondió Dios
CREATE TABLE IF NOT EXISTS "answers" (
    "id" TEXT NOT NULL,
    "record_id" TEXT NOT NULL,
    "answered_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "testimony" TEXT,

    CONSTRAINT "answers_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "answers_record_id_idx" ON "answers"("record_id");

ALTER TABLE "answers" ADD CONSTRAINT "answers_record_id_fkey" FOREIGN KEY ("record_id") REFERENCES "records"("record_id") ON DELETE CASCADE ON UPDATE CASCADE;
