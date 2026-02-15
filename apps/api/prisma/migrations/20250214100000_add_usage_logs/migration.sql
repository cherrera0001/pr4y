-- CreateTable: metadatos de uso (cuándo/cuánto sincroniza cada usuario, sin contenido; E2EE respetado)
CREATE TABLE "usage_logs" (
    "id" TEXT NOT NULL,
    "user_id" TEXT NOT NULL,
    "day" DATE NOT NULL,
    "bytes_pushed" BIGINT NOT NULL DEFAULT 0,
    "bytes_pulled" BIGINT NOT NULL DEFAULT 0,
    "push_count" INTEGER NOT NULL DEFAULT 0,
    "pull_count" INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT "usage_logs_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "usage_logs_user_id_day_key" ON "usage_logs"("user_id", "day");
CREATE INDEX "usage_logs_day_idx" ON "usage_logs"("day");

-- AddForeignKey
ALTER TABLE "usage_logs" ADD CONSTRAINT "usage_logs_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
