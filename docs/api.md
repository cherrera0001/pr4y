# PR4Y – API Docs (Contrato v1)

Base URL: `https://api.pr4y.tld/v1`  
Formato: JSON  
Auth: Bearer token (JWT) o session token (según implementación)  
Errores estándar:
```json
{ "error": { "code": "string", "message": "string", "details": {} } }
```

---

## Especificación OpenAPI

La especificación completa y actualizada de la API está disponible en:

- [api-openapi.yaml](api-openapi.yaml)

Este archivo puede usarse para generar documentación interactiva (Swagger UI, Redoc), clientes automáticos y validación de contratos.
```
