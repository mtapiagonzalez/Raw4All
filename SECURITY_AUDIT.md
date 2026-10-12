# Revisión local de seguridad — Raw4All 0.6.17

Fecha: 12 de octubre de 2026. Revisión y correcciones realizadas con asistencia de OpenAI Codex. No es una certificación independiente, una prueba de penetración exhaustiva ni una garantía «sin virus».

## Artefacto final

- Paquete: `com.secure.mathai.raw4all`.
- Versión: `0.6.17`; versionCode `500802066`.
- APK: `dist/Raw4All-v0.6.17.apk`.
- SHA-256: `7169399ddb817f0db960e9828fa84b2c1762965afb5931202ec513ae4d7c6d7a`.
- Certificado SHA-256: `0efb76e4063275e2a3db47dade84e23e91353702797c4278166b6c6201044ba3`.
- Firma: clave de desarrollo existente, certificado Android Debug. La app ya no es depurable, pero la firma sigue siendo de desarrollo.
- Teléfono: SM-S918B, Android 16, S918BXXSAFZG1; acceso sin root/Shizuku.

## Alcance

Revisión estática de las rutas sensibles de cámara, micrófono, almacenamiento, apertura de galería, configuración de arranque, ciclo de vida y adaptador RAW; manifest, compilación y contenido del APK. Comprobaciones de permisos instalados, firma, automatización por intents y cierre de sensores en el teléfono. Se inspeccionaron también empaquetado y patrones comunes de claves privadas/tokens en fuentes y recursos; no se encontraron coincidencias en ese examen limitado.

La revisión no audita el sistema Android, el HAL, la biblioteca del framework Samsung ni todas sus dependencias. El adaptador RAW carga únicamente el archivo fijo `/system/framework/scamera_sdk_util.jar`, presente en el sistema del teléfono; no carga un archivo elegido por un intent ni descarga código.

## Hallazgos y correcciones

| Hallazgo | Impacto observado o posible | Corrección |
| --- | --- | --- |
| Automatización desde la actividad exportada | Un llamador podía solicitar foto, RAW o video mediante extras de prueba, con los permisos previamente concedidos y la app en primer plano. No se demostró extracción de capturas ni grabación oculta en segundo plano. | Se retiraron todos los comandos/configuraciones entrantes: `auto_record`, `auto_photo`, `probe_raw50`, `test_seconds`, `camera_id`, `fps` y demás extras. La captura requiere interacción con los controles normales. |
| App depurable | Facilitaba inspección de datos privados a través de herramientas autorizadas de depuración. | `android:debuggable=false`; `run-as` rechazado en el teléfono. |
| Registros detallados sin límite | Acumulaban URIs de capturas y metadatos operativos. | No se emiten en la compilación final. Si se activa depuración al recompilar, el archivo privado se reinicia al superar aproximadamente 1 MiB. No se borraron los registros privados preexistentes. |
| Reutilización de clases de compilaciones anteriores | Podía incluir clases obsoletas en el APK. | Cada compilación usa carpetas nuevas de clases y DEX. El mínimo DEX se alinea con minSdk 29. |
| Excepción al publicar JPEG desde callback | Un fallo de publicación podía terminar la actividad y dejar pendiente la captura. | Se maneja la excepción, se intenta retirar la entrada propia pendiente y se restablecen los controles. No se realizó inyección de fallo del proveedor. |

La actividad de lanzamiento sigue exportada para que funcione el launcher. No acepta URIs, rutas de escritura ni órdenes de captura desde el intent. El APK no declara servicios, receptores ni proveedores propios exportados.

## Datos y superficie de acceso

- Solo `CAMERA` y `RECORD_AUDIO`. No `INTERNET`, `SYSTEM_CAMERA`, ubicación ni permisos generales de lectura/escritura de archivos.
- No se encontraron rutas de red, ejecución de comandos, WebView, descarga de código, publicidad o analítica en las rutas inspeccionadas. La ausencia de `INTERNET` y URLs no sustituye por sí sola el análisis de comportamiento.
- APK: 12 entradas ZIP, un DEX con 100 clases propias bajo `local/s23probe`; sin bibliotecas nativas, JARs, código de terceros empaquetado, claves ni assets ejecutables. No quedaron las cadenas de automatización retiradas en el DEX.
- Fotografías y videos se crean mediante MediaStore en `DCIM/Raw4All`, primero como pendientes y luego se publican. Son archivos de galería, no un almacén cifrado privado; las apps con autorización del sistema o del usuario pueden acceder a ellos.
- La acción del usuario para ver una captura concede lectura de esa URI al visor elegido. No se observó concesión de escritura ni de todos los archivos de la galería.
- `allowBackup=false`. Las preferencias y registros anteriores quedan en el sandbox privado. No se implementó geolocalización; el DNG contiene modelo, fecha, exposición y calibración de captura.

## Validación

- Compilación, firma y comprobaciones locales de FPS, horizonte, RAW10 y reloj: pasaron.
- Intent explícito mediante ADB con ID inválido, fps 999 y antiguos extras de foto/video/RAW: se abrió la cámara guardada en preferencias, no hubo captura automática y la lista de archivos propios de MediaStore permaneció igual. Se repitió sobre el APK final. Esto simula entradas externas, no es un pentest completo con una aplicación hostil.
- `run-as`: `package not debuggable`; manifest instalado sin `DEBUGGABLE` y con solo cámara/micrófono.
- Video W/ST iniciado desde los controles normales durante la validación 0.6.17: H.264 1920×1080 con AAC; archivos decodificados con FFmpeg. La reconstrucción final añadió únicamente manejo de error JPEG después de estas pruebas de video.
- Salir durante la grabación W publicó el video y dejó `Active Camera Clients: []`; AppOps mostró el uso de micrófono terminado, sin duración activa.
- APK final: JPEG ST guardado desde el disparador normal; RAW W 8160×6120 publicado desde selección de MP y disparador, 99.879.710 bytes. LibRaw abrió el DNG y lo reveló a media resolución.
- Expert RAW permaneció instalado junto a Raw4All; no se intentó utilizar una cámara desde ambas apps simultáneamente.

Los registros y muestras se conservan localmente en `../diagnostics/security-audit-v0617/`, fuera del repo publicable.

## Límites y pendientes

La revisión local quedó completada para este alcance. No se encontraron mecanismos de exfiltración o ejecución remota en las rutas inspeccionadas; no se certifica su ausencia absoluta. Quedan pendientes firma de distribución con una clave dedicada y plan de actualización, revisión independiente, análisis antimalware si se desea, pruebas prolongadas, otros firmwares y casos extremos de memoria/almacenamiento. No se subió el APK ni las capturas a servicios externos.

El acceso trasero sigue dependiendo de la política de prefijos Samsung documentada en `CAMERA_ACCESS.md`. Es compatibilidad específica del firmware, no una autorización oficial ni una vulnerabilidad con impacto de seguridad demostrado.

Referencias de criterio: [actividad exportada](https://developer.android.com/privacy-and-security/risks/android-exported), [app depurable](https://developer.android.com/privacy-and-security/risks/android-debuggable). Las conclusiones sobre Raw4All proceden del código, APK y pruebas locales indicados.

## Actualización de distribución 0.7.0

La firma dedicada ya está disponible en 0.7.0. El registro anterior identifica el APK histórico revisado y mantiene sus resultados originales. Consulta [RELEASE-v0.7.0.md](RELEASE-v0.7.0.md) para hashes, instalación y validación de la nueva distribución. La revisión independiente sigue pendiente.
