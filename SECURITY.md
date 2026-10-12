# Seguridad y revisión

## Estado

Se completó una revisión local de seguridad de Raw4All 0.6.17 y se corrigieron los hallazgos descritos en [SECURITY_AUDIT.md](SECURITY_AUDIT.md). Incluye código sensible, manifest, contenido del APK y pruebas en el teléfono. No es una auditoría exhaustiva ni una certificación independiente; el informe especifica pruebas, alcance, hash y pendientes.

## Comportamiento previsto

- Cámara: vista previa, fotografías y video en primer plano.
- Micrófono: audio durante la grabación.
- Almacenamiento: capturas propias mediante MediaStore en `DCIM/Raw4All` y registros locales de diagnóstico.
- El manifest actual no declara `INTERNET`. No se incorporan anuncios ni analítica.
- Desde 0.6.16 no se declara `SYSTEM_CAMERA`. El acceso a W/ST depende de la política de nombres de paquetes del firmware Samsung probado; no concede la firma ni la identidad de Samsung.
- Desde 0.6.17, `debuggable=false`, sin órdenes de captura mediante extras externos ni registros detallados de la app. La firma sigue usando la clave de desarrollo existente.

## Revisión independiente y distribución pendientes

1. Revisar permisos, componentes exportados, extras de diagnóstico y ciclo de vida de cámara/micrófono.
2. Examinar fuente y contenido del APK: DEX, recursos, manifest y cualquier biblioteca incorporada.
3. Comprobar ausencia de comunicaciones, descarga de código, ejecución inesperada o acceso a archivos ajenos a las funciones declaradas.
4. Verificar almacenamiento, publicación de archivos, tratamiento de fallos y comportamiento al salir de la app.
5. Revisar herramientas de compilación, procedencia de recursos y configuración de firma; conservar las claves fuera del repositorio.
6. Registrar versión, hash SHA-256 del APK analizado, métodos, resultados y limitaciones. Cualquier análisis antivirus será evidencia adicional, no una garantía absoluta.

## Revisar tu propia versión

Puedes descargar el repositorio y apoyarte en Codex, Claude Code u otras herramientas para entender y mejorar el código. Comprueba sus conclusiones y ejecuta pruebas locales antes de instalar cambios. Una versión modificada y firmada con otra clave no actualizará directamente la APK existente.

Si encuentras un problema de seguridad, comunícalo al responsable del repositorio por un canal privado disponible. Evita publicar datos personales, grabaciones privadas o claves de firma.
