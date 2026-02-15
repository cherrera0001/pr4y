-- AlterTable: add status to users (active | banned)
ALTER TABLE "users" ADD COLUMN IF NOT EXISTS "status" TEXT NOT NULL DEFAULT 'active';

-- CreateTable: global content (prayers, announcements) managed by admin
CREATE TABLE IF NOT EXISTS "global_content" (
    "id" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "body" TEXT NOT NULL,
    "published" BOOLEAN NOT NULL DEFAULT false,
    "sort_order" INTEGER NOT NULL DEFAULT 0,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "global_content_pkey" PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS "global_content_type_published_idx" ON "global_content"("type", "published");
