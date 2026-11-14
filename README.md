# Redragon K617 Fizz - Controlador RGB Java

Controlador de efectos LED para el teclado Redragon K617 Fizz (parte del combo S143).

## Hardware
- **Teclado**: Redragon K617 Fizz (60%, RGB, hot-swap, switches Red)
- **Chip**: SinoWealth
- **VID**: 0x258A | **PID**: 0x0049

## Requisitos
- Java 17+ (JDK)
- Maven 3.6+
- **Zadig** — Reemplazar driver de MI_01 con WinUSB (necesario en Windows)

## Compilar y ejecutar
```bat
build.bat
```
Genera: `dist\RedragonK617\RedragonK617.exe`

## Comandos disponibles
```
RedragonK617.exe color 255 0 0            # Color rojo
RedragonK617.exe color 0 255 0 --brightness 80
RedragonK617.exe effect breathing --r 0 --g 0 --b 255
RedragonK617.exe effect wave --speed 5
RedragonK617.exe brightness 70
RedragonK617.exe off
RedragonK617.exe demo colors
RedragonK617.exe demo effects
```

## Efectos soportados
`off`, `static`, `breathing`, `wave`, `reactive`, `ripple`, `stars`, `neon`, `rain`, `shadow`, `custom`

## Protocolo HID
- Paquetes de 1032 bytes, Report ID 0x06
- Control Transfer: bmRequest=0x21, bRequest=0x09 (SET_REPORT)
- wValue=0x0306 (Feature, ID=0x06), wIndex=0x0001 (Interface 1)
- Sync markers (0x5A 0xA5) en offsets 14, 78, 137

## Dependencias Maven
- `usb4java 1.3.0` (libusb wrapper para Java)

## Estado
Proyecto en pausa. Error "No such device" al abrir con libusb después de Zadig.
Posible solución: desconectar y reconectar el teclado después de instalar WinUSB.

## Fecha
2026-11-13
