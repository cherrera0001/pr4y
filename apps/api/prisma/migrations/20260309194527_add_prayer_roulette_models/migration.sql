-- CreateTable
CREATE TABLE "prayer_rooms" (
    "id" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'active',
    "started_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "ended_at" TIMESTAMP(3),
    "duration" INTEGER,
    "user_a_id" TEXT NOT NULL,
    "user_b_id" TEXT NOT NULL,
    "language" TEXT,

    CONSTRAINT "prayer_rooms_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "prayer_partners" (
    "id" TEXT NOT NULL,
    "user_id" TEXT NOT NULL,
    "partner_id" TEXT NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "last_prayed" TIMESTAMP(3),
    "prayer_count" INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT "prayer_partners_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "prayer_reports" (
    "id" TEXT NOT NULL,
    "room_id" TEXT NOT NULL,
    "reporter_id" TEXT NOT NULL,
    "reason" TEXT NOT NULL,
    "details" TEXT,
    "status" TEXT NOT NULL DEFAULT 'pending',
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "prayer_reports_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "matching_queue" (
    "id" TEXT NOT NULL,
    "user_id" TEXT NOT NULL,
    "joined_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "filters" JSONB,
    "status" TEXT NOT NULL DEFAULT 'waiting',

    CONSTRAINT "matching_queue_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "prayer_rooms_status_idx" ON "prayer_rooms"("status");

-- CreateIndex
CREATE INDEX "prayer_rooms_user_a_id_idx" ON "prayer_rooms"("user_a_id");

-- CreateIndex
CREATE INDEX "prayer_rooms_user_b_id_idx" ON "prayer_rooms"("user_b_id");

-- CreateIndex
CREATE INDEX "prayer_partners_user_id_idx" ON "prayer_partners"("user_id");

-- CreateIndex
CREATE INDEX "prayer_partners_partner_id_idx" ON "prayer_partners"("partner_id");

-- CreateIndex
CREATE UNIQUE INDEX "prayer_partners_user_id_partner_id_key" ON "prayer_partners"("user_id", "partner_id");

-- CreateIndex
CREATE INDEX "prayer_reports_room_id_idx" ON "prayer_reports"("room_id");

-- CreateIndex
CREATE INDEX "prayer_reports_reporter_id_idx" ON "prayer_reports"("reporter_id");

-- CreateIndex
CREATE INDEX "prayer_reports_status_idx" ON "prayer_reports"("status");

-- CreateIndex
CREATE UNIQUE INDEX "matching_queue_user_id_key" ON "matching_queue"("user_id");

-- CreateIndex
CREATE INDEX "matching_queue_status_joined_at_idx" ON "matching_queue"("status", "joined_at");

-- AddForeignKey
ALTER TABLE "prayer_rooms" ADD CONSTRAINT "prayer_rooms_user_a_id_fkey" FOREIGN KEY ("user_a_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "prayer_rooms" ADD CONSTRAINT "prayer_rooms_user_b_id_fkey" FOREIGN KEY ("user_b_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "prayer_partners" ADD CONSTRAINT "prayer_partners_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "prayer_partners" ADD CONSTRAINT "prayer_partners_partner_id_fkey" FOREIGN KEY ("partner_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "prayer_reports" ADD CONSTRAINT "prayer_reports_room_id_fkey" FOREIGN KEY ("room_id") REFERENCES "prayer_rooms"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "prayer_reports" ADD CONSTRAINT "prayer_reports_reporter_id_fkey" FOREIGN KEY ("reporter_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "matching_queue" ADD CONSTRAINT "matching_queue_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
