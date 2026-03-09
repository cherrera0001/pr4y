-- AddColumn: reminder_schedules (multi-schedule, replaces single reminderTime)
ALTER TABLE "users" ADD COLUMN "reminder_schedules" JSONB;
