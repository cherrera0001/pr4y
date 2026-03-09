-- CreateTable
CREATE TABLE "public_requests" (
    "id" TEXT NOT NULL,
    "title" TEXT NOT NULL DEFAULT 'Pedido de oración',
    "body" TEXT NOT NULL,
    "prayer_count" INTEGER NOT NULL DEFAULT 0,
    "status" TEXT NOT NULL DEFAULT 'approved',
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "public_requests_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "public_requests_status_idx" ON "public_requests"("status");

-- CreateIndex
CREATE INDEX "public_requests_created_at_idx" ON "public_requests"("created_at");
