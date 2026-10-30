# Criba

Organizador de fotos y documentos para tu PC (Windows). Todo se analiza en tu computadora; no se sube nada.

## Qué hace

**Fotos**
- Biblioteca por momentos (fecha EXIF real), como Google Fotos, con tamaño de miniatura ajustable.
- Carpetas como álbumes.
- Limpiar: duplicados exactos, fotos casi iguales (ráfagas, conserva la más nítida), borrosas, muy oscuras o quemadas, capturas de pantalla, WhatsApp y videos pesados.
- Visor a pantalla completa: flechas para avanzar, Supr para eliminar, Esc para cerrar.

**Documentos**
- Agrupados por tipo (PDF, Word, hojas, presentaciones…) y por tema (facturas, contratos, académico…).
- Limpiar: duplicados, versiones anteriores (final, final2, v1, v2, (1)…), temporales, instaladores, archivos pesados y antiguos.

**Ordenar**
- Mueve los archivos a `Ordenado/` por año y mes, día u origen (fotos), o por tipo, tema o año (documentos).
- Vista previa antes de mover y botón para deshacer.

**Seguro**
- Eliminar manda a la Papelera de reciclaje.
- Funciona con la carpeta de OneDrive: lo que ordenes se sincroniza solo. Los archivos que están solo en la nube no se descargan.

## Descargar

En **Releases**, `Criba-windows.msi`. Si Windows muestra "Windows protegió su PC", elige *Más información → Ejecutar de todas formas* (la app no está firmada).

## Cómo se compila

GitHub Actions compila en cada cambio a `main` y publica en Releases.

## Estructura

- `desktopApp/`: Criba para PC (Kotlin + Compose Desktop).
- `androidApp/` y `shared/`: versión para Android conectada a OneDrive (requiere registrar la app en Azure; en pausa).
