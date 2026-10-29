# Criba

Galería rápida para tus fotos de OneDrive, en **Android** y **Windows**, con la misma interfaz.

- Línea de tiempo por día, pellizco para cambiar el tamaño y barra para saltar entre meses.
- Álbumes a partir de tus carpetas de OneDrive.
- Selección múltiple para **mover** y **eliminar** (a la papelera de OneDrive).
- Pestaña **Limpiar**: duplicados exactos, capturas de pantalla, videos pesados y WhatsApp.
- Rápida porque guarda un índice local y solo pide a OneDrive lo que cambió (delta query). Las miniaturas se guardan en caché (2 GB).

## Cómo se compila

GitHub Actions compila todo en cada cambio a `main` y publica en **Releases**:

- `Criba-android.apk`: ábrelo desde el celular e instálalo (Android pedirá permitir apps de origen desconocido para el navegador).
- `Criba-windows.msi`: instalador para Windows.

## Configuración (una sola vez)

### 1. Registrar la app en Azure

1. Entra a <https://entra.microsoft.com> → **Aplicaciones** → **Registros de aplicaciones** → **Nuevo registro**.
   Si es la primera vez con una cuenta personal, Microsoft puede pedirte crear un directorio gratuito.
2. Nombre: `Criba`. Tipos de cuenta: **Cuentas de cualquier directorio organizativo y cuentas Microsoft personales**.
3. En **Autenticación** → **Agregar una plataforma**:
   - **Android**: paquete `app.criba.galeria`, hash de firma `xOCboFTu1LlBh42UY1BkHhM8CZA=`.
   - **Aplicaciones móviles y de escritorio**: URI de redirección `http://localhost`.
4. En **Permisos de API** → Microsoft Graph → Permisos delegados → `Files.ReadWrite`.
5. Copia el **Id. de aplicación (cliente)**.

### 2. Guardar el ID en GitHub

En el repositorio: **Settings → Secrets and variables → Actions → Variables → New repository variable**

- Nombre: `AZURE_CLIENT_ID`
- Valor: el ID que copiaste.

Luego en **Actions → Compilar Criba → Run workflow**.

## Firma

La llave `firma/criba.keystore` es fija para que el inicio de sesión de Microsoft siempre reconozca la app.
Está en el repositorio por comodidad: mantén el repositorio **privado**.

## Estructura

- `shared/src`: interfaz (Compose) y lógica común: OneDrive, índice, limpieza.
- `androidApp`: arranque en Android e inicio de sesión con MSAL.
- `desktopApp`: arranque en Windows e inicio de sesión con MSAL4J.

## Privacidad

Criba habla directo con Microsoft Graph desde tu dispositivo. No hay servidores intermedios.
Puedes quitarle el acceso desde tu cuenta Microsoft → Privacidad → Aplicaciones y servicios.
