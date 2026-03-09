-- AlterTable
ALTER TABLE "users" ADD COLUMN "reminder_time" TEXT;
ALTER TABLE "users" ADD COLUMN "reminder_days_of_week" JSONB;
ALTER TABLE "users" ADD COLUMN "reminder_enabled" BOOLEAN NOT NULL DEFAULT false;
