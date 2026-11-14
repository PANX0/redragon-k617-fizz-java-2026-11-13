package com.redragon;

import org.usb4java.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Map;

/**
 * Controlador para el Redragon K617 Fizz RGB.
 * VID: 0x258A  PID: 0x0049  (chip SinoWealth)
 *
 * Requiere WinUSB instalado en Interface 1 (MI_01) via Zadig.
 * Usa usb4java (libusb) para control transfer directo.
 */
public class RedragonK617 implements AutoCloseable {

    // ── Identificación ────────────────────────────────────────────────────────
    public static final short VID = (short) 0x258A;
    public static final short PID = (short) 0x0049;

    // ── Protocolo ─────────────────────────────────────────────────────────────
    private static final int   PACKET_SIZE  = 1032;
    private static final byte  REPORT_ID    = 0x06;
    private static final byte  SUB_LED      = 0x03;
    private static final byte  MAGIC_LED    = (byte) 0xC6;
    private static final byte  SUB_REMAP    = 0x04;
    private static final byte  MAGIC_REMAP  = (byte) 0xD8;
    private static final byte  SUB_MACRO    = 0x05;
    private static final byte  MAGIC_MACRO  = (byte) 0xC5;

    // HID SET_FEATURE control transfer params
    private static final byte  BM_REQUEST   = 0x21;  // Host→Device, Class, Interface
    private static final byte  B_REQUEST    = 0x09;  // SET_REPORT
    private static final short W_VALUE      = 0x0306; // Feature report, ID=0x06
    private static final short W_INDEX      = 0x0001; // Interface 1
    private static final int   INTERFACE_NR = 1;
    private static final int   TIMEOUT_MS   = 5000;

    // Sync markers
    private static final int[] SYNC_OFFSETS = {14, 78, 137};

    // ── Efectos ───────────────────────────────────────────────────────────────
    public enum Effect {
        OFF(0x00), STATIC(0x01), BREATHING(0x02), WAVE(0x03),
        REACTIVE(0x04), RIPPLE(0x05), STARS(0x06),
        NEON(0x07), RAIN(0x08), SHADOW(0x09), CUSTOM(0x20);

        public final int code;
        Effect(int code) { this.code = code; }

        public static Effect fromName(String name) {
            for (Effect e : values())
                if (e.name().equalsIgnoreCase(name)) return e;
            throw new IllegalArgumentException("Efecto desconocido: " + name +
                "\nOpciones: off, static, breathing, wave, reactive, ripple, stars, neon, rain, shadow, custom");
        }
    }

    // ── Estado ────────────────────────────────────────────────────────────────
    private Context      context;
    private DeviceHandle handle;

    // ── Conexión ──────────────────────────────────────────────────────────────

    public RedragonK617 connect() {
        context = new Context();
        int r = LibUsb.init(context);
        if (r != LibUsb.SUCCESS)
            throw new RuntimeException("Error inicializando libusb: " + LibUsb.strError(r));

        // Buscar el dispositivo por VID/PID
        DeviceList list = new DeviceList();
        r = LibUsb.getDeviceList(context, list);
        if (r < 0) throw new RuntimeException("Error listando dispositivos USB: " + LibUsb.strError(r));

        Device target = null;
        try {
            for (Device device : list) {
                DeviceDescriptor desc = new DeviceDescriptor();
                LibUsb.getDeviceDescriptor(device, desc);
                if (desc.idVendor() == VID && desc.idProduct() == PID) {
                    target = device;
                    LibUsb.refDevice(device);
                    break;
                }
            }
        } finally {
            LibUsb.freeDeviceList(list, true);
        }

        if (target == null) {
            LibUsb.exit(context);
            throw new RuntimeException(
                "Teclado no encontrado (VID=" + String.format("0x%04X", VID & 0xFFFF) +
                " PID=" + String.format("0x%04X", PID & 0xFFFF) + ").\n" +
                "  → ¿Está conectado el teclado?\n" +
                "  → ¿Instalaste WinUSB con Zadig en la interfaz MI_01?"
            );
        }

        handle = new DeviceHandle();
        r = LibUsb.open(target, handle);
        LibUsb.unrefDevice(target);
        if (r != LibUsb.SUCCESS)
            throw new RuntimeException("Error abriendo dispositivo: " + LibUsb.strError(r));

        // Desconectar driver del kernel si está activo (Linux) — en Windows no hace nada
        LibUsb.setAutoDetachKernelDriver(handle, true);

        // Reclamar interfaz 1 (MI_01, LED/config — la que tiene WinUSB)
        r = LibUsb.claimInterface(handle, INTERFACE_NR);
        if (r != LibUsb.SUCCESS) {
            LibUsb.close(handle);
            LibUsb.exit(context);
            throw new RuntimeException(
                "Error reclamando Interface " + INTERFACE_NR + ": " + LibUsb.strError(r) + "\n" +
                "  → Verifica que instalaste WinUSB en MI_01 con Zadig (no en MI_00)."
            );
        }

        System.out.println("[OK] Conectado al Redragon K617 Fizz");
        return this;
    }

    @Override
    public void close() {
        if (handle != null) {
            LibUsb.releaseInterface(handle, INTERFACE_NR);
            LibUsb.close(handle);
        }
        if (context != null) LibUsb.exit(context);
    }

    // ── Construcción y envío de paquetes ──────────────────────────────────────

    private ByteBuffer buildPacket(byte subCmd, byte magic) {
        ByteBuffer buf = ByteBuffer.allocateDirect(PACKET_SIZE);
        buf.put(0, REPORT_ID);
        buf.put(1, subCmd);
        buf.put(2, magic);
        for (int base : SYNC_OFFSETS) {
            buf.put(base,     (byte) 0x5A);
            buf.put(base + 1, (byte) 0xA5);
        }
        return buf;
    }

    private void send(ByteBuffer pkt) {
        if (handle == null) throw new RuntimeException("No conectado.");
        IntBuffer transferred = IntBuffer.allocate(1);
        int r = LibUsb.controlTransfer(handle,
            BM_REQUEST, B_REQUEST, W_VALUE, W_INDEX,
            pkt, TIMEOUT_MS);
        if (r < 0)
            throw new RuntimeException("Error controlTransfer: " + LibUsb.strError(r));
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
    }

    // ── Control de LEDs ───────────────────────────────────────────────────────

    public void setEffect(Effect effect, int r, int g, int b, int brightness, int speed) {
        int brt = (int)(clamp(brightness, 0, 100) / 100.0 * 0x7F);
        int spd = (int)((1.0 - clamp(speed, 0, 100) / 100.0) * 0xFF);

        ByteBuffer pkt = buildPacket(SUB_LED, MAGIC_LED);
        pkt.put(21, (byte) effect.code);
        pkt.put(22, (byte) brt);
        pkt.put(28, (byte)(spd & 0xFF));
        pkt.put(29, (byte)((spd >> 8) & 0xFF));
        pkt.put(36, (byte)(r & 0xFF));
        pkt.put(37, (byte)(g & 0xFF));
        pkt.put(38, (byte)(b & 0xFF));

        send(pkt);
        System.out.printf("[LED] %-12s RGB=(%3d,%3d,%3d)  Brillo=%d%%  Velocidad=%d%%%n",
            effect.name(), r, g, b, brightness, speed);
    }

    public void setColor(int r, int g, int b, int brightness) {
        setEffect(Effect.STATIC, r, g, b, brightness, 50);
    }

    public void setBrightness(int level) {
        setEffect(Effect.STATIC, 255, 255, 255, level, 50);
    }

    public void off() {
        setEffect(Effect.OFF, 0, 0, 0, 0, 0);
    }

    public void setPerKeyRgb(Map<Integer, int[]> keyColors) {
        ByteBuffer pkt = buildPacket(SUB_LED, MAGIC_LED);
        pkt.put(21, (byte) Effect.CUSTOM.code);
        pkt.put(22, (byte) 0x7F);
        for (Map.Entry<Integer, int[]> entry : keyColors.entrySet()) {
            int idx = entry.getKey();
            int[] rgb = entry.getValue();
            if (idx >= 0 && idx <= 61) {
                int off = 106 + idx * 3;
                if (off + 2 < PACKET_SIZE) {
                    pkt.put(off,     (byte)(rgb[0] & 0xFF));
                    pkt.put(off + 1, (byte)(rgb[1] & 0xFF));
                    pkt.put(off + 2, (byte)(rgb[2] & 0xFF));
                }
            }
        }
        send(pkt);
        System.out.println("[LED] Per-key RGB: " + keyColors.size() + " teclas  ⚠ índices no verificados");
    }

    // ── Remapeo ───────────────────────────────────────────────────────────────

    public void remapKey(int originalHid, int replacementHid) {
        System.out.println("[WARN] remapKey: protocolo NO verificado.");
        ByteBuffer pkt = buildPacket(SUB_REMAP, MAGIC_REMAP);
        pkt.put(3, (byte)(originalHid & 0xFF));
        pkt.put(4, (byte)(replacementHid & 0xFF));
        send(pkt);
        System.out.printf("[REMAP] HID 0x%02X → 0x%02X%n", originalHid, replacementHid);
    }

    // ── Macros ────────────────────────────────────────────────────────────────

    public void programMacro(int slot, java.util.List<MacroAction> actions) {
        if (slot < 0 || slot > 11) throw new IllegalArgumentException("Slot debe ser 0-11");
        System.out.println("[WARN] programMacro: protocolo NO verificado.");
        ByteBuffer pkt = buildPacket(SUB_MACRO, MAGIC_MACRO);
        pkt.put(3, (byte)(slot & 0xFF));
        int offset = 4;
        for (MacroAction action : actions) {
            if (offset + 4 >= PACKET_SIZE) break;
            pkt.put(offset,     (byte)(action.hidCode & 0xFF));
            pkt.put(offset + 1, action.keyDown ? (byte)0x00 : (byte)0x01);
            pkt.put(offset + 2, (byte)(action.delayMs & 0xFF));
            pkt.put(offset + 3, (byte)((action.delayMs >> 8) & 0xFF));
            offset += 4;
        }
        send(pkt);
        System.out.println("[MACRO] Slot " + slot + ": " + actions.size() + " acciones");
    }

    // ── Demos ─────────────────────────────────────────────────────────────────

    public void demoColors(long pauseMs) throws InterruptedException {
        int[][] colors = {{255,0,0},{0,255,0},{0,0,255},{0,255,255},{255,0,255},{255,255,0},{255,255,255}};
        String[] names = {"Rojo","Verde","Azul","Cyan","Magenta","Amarillo","Blanco"};
        System.out.println("[DEMO] Iniciando ciclo de colores...");
        for (int i = 0; i < colors.length; i++) {
            System.out.println("  → " + names[i]);
            setColor(colors[i][0], colors[i][1], colors[i][2], 70);
            Thread.sleep(pauseMs);
        }
        System.out.println("[DEMO] Finalizado.");
    }

    public void demoEffects(long pauseMs) throws InterruptedException {
        System.out.println("[DEMO] Recorriendo efectos...");
        for (Effect e : Effect.values()) {
            if (e == Effect.OFF || e == Effect.CUSTOM) continue;
            System.out.println("  → " + e.name().toLowerCase());
            setEffect(e, 0, 150, 255, 80, 60);
            Thread.sleep(pauseMs);
        }
        System.out.println("[DEMO] Finalizado.");
    }

    // ── Utilitarios ───────────────────────────────────────────────────────────

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public static class MacroAction {
        public final int hidCode;
        public final boolean keyDown;
        public final int delayMs;
        public MacroAction(int hidCode, boolean keyDown, int delayMs) {
            this.hidCode = hidCode; this.keyDown = keyDown; this.delayMs = delayMs;
        }
    }
}
