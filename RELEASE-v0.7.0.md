# Raw4All 0.7.0 — firma de distribución

Esta versión conserva el código de cámara de 0.6.17 y estrena una clave de distribución dedicada RSA de 4096 bits. No implica una revisión independiente ni amplía la compatibilidad comprobada.

- Paquete: `com.secure.mathai.raw4all`.
- Versión: `0.7.0`; versionCode: `500802067`.
- APK: `Raw4All-v0.7.0.apk`.
- SHA-256 del APK: `86d685c8fffdd2bdb4e6c6a56e3b391da6bfb030de381b2c25c842e6294461ef`.
- SHA-256 del certificado: `e790cead37903bb387a14da90e1526bcd3d1858218ad2b3342af6d4a3affd7ad`.

[Descargar APK](https://github.com/mtapiagonzalez/Raw4All/releases/download/v0.7.0/Raw4All-v0.7.0.apk).

## Instalación y actualizaciones

La firma nueva no puede actualizar la instalación de pruebas firmada con Android Debug. Desinstala esa instalación antes de instalar 0.7.0: se perderán sus preferencias y datos privados; conserva aparte cualquier dato que necesites. Las capturas publicadas en la galería se guardan por separado. No es necesario desinstalar Expert RAW.

Las próximas actualizaciones deben conservar esta clave de distribución. La clave privada y su contraseña no se publican. Los hashes y el certificado son datos públicos de verificación, no secretos.

## Validación y límites

Compilación, verificación criptográfica del APK y comprobaciones locales de FPS, horizonte, RAW10 y reloj completadas. Manifest no depurable, sin backup, con permisos CAMERA y RECORD_AUDIO. No se repitieron pruebas físicas sobre el APK 0.7.0; las pruebas de cámara documentadas en SECURITY_AUDIT.md corresponden a 0.6.17.

Dispositivo previamente comprobado: Galaxy S23 Ultra SM-S918B, Android 16, firmware S918BXXSAFZG1. Puede fallar en otros dispositivos o tras actualizar el firmware. La revisión independiente sigue pendiente.
