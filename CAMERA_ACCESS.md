# Acceso con paquete independiente

Pruebas del 12 de octubre de 2026 en Samsung Galaxy S23 Ultra SM-S918B, Android 16, firmware `S918BXXSAFZG1` (`samsung/dm3qxxx/dm3q:16/BP4A.251205.006/S918BXXSAFZG1:user/release-keys`).

## Resultado

Raw4All 0.6.16 usa `com.secure.mathai.raw4all`, su firma de desarrollo propia y las APIs Camera2 existentes. No declara `SYSTEM_CAMERA`; no requiere root ni Shizuku. La instalación anterior bajo `com.samsung.android.app.galaxyraw` permaneció instalada durante las pruebas. Son paquetes distintos, con datos privados, UID y permisos independientes.

El cambio elimina el conflicto de identificador con Expert RAW. No se reinstaló la aplicación original de Samsung durante esta sesión, por lo que no se comprobó su funcionamiento junto a Raw4All. Las dos aplicaciones tampoco pueden utilizar simultáneamente una cámara que el sistema reserva a un único cliente.

## Dos comprobaciones de autorización

El CameraManager recuperado del teléfono consulta `isHiddenIdPermittedPackage(getOpPackageName())` para IDs entre 20 y 99. Un resultado falso provoca `Unknown camera ID` antes de abrir la cámara.

En un diagnóstico bajo `local.raw4all.accessprobe`, cambiar únicamente el estado local `mHiddenCameraPermittedState` mediante reflexión permitió leer metadatos de 54/56, pero el servicio rechazó la apertura en `validateClientPermissionsLocked`, con `No camera device with ID ... available`. Esta prueba conservó el UID y la atribución propios. Confirma que evitar la comprobación Java no basta.

La app completa no incorpora ese cambio de estado ni reflexión para abrir cámaras: utiliza su propio contexto y la ruta Camera2 habitual.

## Consultas de nombres

Consultas de solo lectura a la política del servicio desde el diagnóstico:

| Nombre consultado | Resultado |
| --- | --- |
| `local.raw4all.accessprobe` | false |
| `com.mathai.raw4all` | false |
| `com.mathai.raw4all.galaxyraw` | false |
| `com.mathai.raw4all.samsungcompat` | false |
| `com.mathai.raw4all.com.samsung.android.app.raw4all` | false |
| `com.samsung.android.app.galaxyraw` | true |
| `com.samsung.android.app.raw4all` | true |
| `com.samsung.raw4all` | true |
| `com.samsungx.raw4all` | true |
| `org.samsung.raw4all` | false |
| `com.sec.android.app.camera` | true |
| `com.sec.raw4all` | true |
| `org.codeaurora.snapcam` | false |

Estos resultados indican una política de prefijos más amplia que una lista de nombres exactos. No se obtuvo la implementación nativa completa ni se caracterizaron todos los prefijos admitidos. Una consulta positiva por sí sola no prueba acceso: se validó además apertura y entrega de imágenes con el paquete instalado `com.secure.mathai.raw4all.accessprobe`, sin alterar la comprobación local.

## Validación física

| Prueba | Resultado |
| --- | --- |
| Diagnóstico independiente, W ID 56, YUV 1920×1080 | 89 imágenes y 89 resultados de captura |
| Diagnóstico independiente, ST ID 54, YUV 1920×1080 | 83 imágenes y 83 resultados de captura |
| Raw4All 0.6.16, W | MP4 H.264 1920×1080, aproximadamente 30 fps, AAC, 4,94925 s; guardado en MediaStore |
| Raw4All 0.6.16, ST | MP4 H.264 1920×1080, aproximadamente 30 fps, AAC, 4,94925 s; guardado en MediaStore |
| Raw4All 0.6.16, W RAW | DNG 8160×6120, 99.879.710 bytes; LibRaw lo abrió y reveló a media resolución |

El DNG conserva precisión RAW10: nivel blanco 1023 y niveles negros 64. No se modifica el procesamiento de captura de versiones anteriores.

Los dos videos se decodificaron con FFmpeg. Para verificar sin redondear el tiempo de salida al de un codificador por defecto: `ffmpeg -v error -i archivo.mp4 -fps_mode passthrough -enc_time_base:v demux -f null NUL`. La presencia de una pista AAC no constituye una evaluación subjetiva del sonido. Las pruebas cortas no validan grabación prolongada ni todos los controles de interfaz.

## Ruta pública alternativa

Con `local.raw4all.accessprobe`, la frontal 3 entregó 101 imágenes de control. La cámara lógica pública 0 abrió y anunció cámaras físicas 2, 5, 6 y 7, pero las configuraciones traseras probadas no entregaron imágenes:

- YUV y PRIVATE, 1920×1080, salida lógica o física 5/7.
- YUV 1280×720, salida física 5/7 junto a salida lógica.
- Salida lógica 1280×720, PRIVATE con zoom 10 y YUV con zoom 3.
- Pruebas anteriores de salida física YUV 640×480 para 5/6/7.

El servicio declaró compatibles varias de estas configuraciones y después falló al crearlas. Un registro aislado mostró `CHI ... Invalid sizes of moduleinfo, make camera fail (13 >= 8)`, seguido de `No such device (-19)` en el HAL y `Function not implemented (-38)` en CameraService. Esto localiza un fallo de configuración nativa; no identifica por sí solo una avería de sensor.

## Reproducción y límites

Compilar con `build.ps1`, instalar `build/Raw4All-v0.6.16.apk` y conceder cámara/micrófono. Comprobar W/ST, video y guardado; para RAW, seleccionar W → 50 MP (RAW). Los scripts `check.ps1` comprobaron FPS, horizonte, RAW10 y reloj de grabación.

Cambiar a un paquete arbitrario como `com.mathai.raw4all` no quedó habilitado. `com.secure.mathai.raw4all` sigue dependiendo de una política específica del fabricante que puede cambiar. No se obtiene la firma, instalación de sistema ni respaldo de Samsung. No se ha demostrado compatibilidad con otros modelos o firmwares, ni una vulnerabilidad con impacto de seguridad.

Los registros, diagnósticos y archivos de cámara permanecen en la carpeta local de investigación, fuera del repositorio y del APK distribuido.
