-- Restringe admin: solo estas dos cuentas pueden tener rol super_admin. Cualquier otra queda user.
-- Ejecutar en Railway: Database → Query / Data → pegar y ejecutar.

-- 1) Quitar rol admin a todos (por si alguien tenía admin/super_admin)
UPDATE users SET role = 'user' WHERE role IN ('admin', 'super_admin');

-- 2) Asignar super_admin solo a las cuentas permitidas
UPDATE users SET role = 'super_admin' WHERE LOWER(email) = 'herrera.jara.cristobal@gmail.com';
UPDATE users SET role = 'super_admin' WHERE LOWER(email) = 'crherrera@c4a.cl';

-- Si un usuario aún no existe (no ha hecho login con Google), se creará con role 'user';
-- después vuelve a ejecutar este SQL o npm run set-admin-users en apps/api.
