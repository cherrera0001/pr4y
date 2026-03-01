-- AddColumn: display_preferences JSONB en users
-- Almacena preferencias de visualización (tema, tipografía, modo contemplativo).
-- Nullable: si es NULL el cliente aplica los defaults sin requerir PUT previo.

ALTER TABLE "users" ADD COLUMN "display_preferences" JSONB;
