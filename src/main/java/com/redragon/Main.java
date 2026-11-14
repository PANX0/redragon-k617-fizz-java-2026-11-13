package com.redragon;

import java.util.ArrayList;
import java.util.List;

/**
 * CLI para el controlador Redragon K617 Fizz.
 *
 * Uso:
 *   RedragonK617.exe color 255 0 0
 *   RedragonK617.exe color 0 255 128 --brightness 90
 *   RedragonK617.exe effect breathing --r 0 --g 255 --b 0 --speed 40
 *   RedragonK617.exe effect wave
 *   RedragonK617.exe brightness 50
 *   RedragonK617.exe off
 *   RedragonK617.exe demo colors
 *   RedragonK617.exe demo effects
 */
public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            System.exit(0);
        }

        String command = args[0].toLowerCase();

        try (RedragonK617 kbd = new RedragonK617().connect()) {
            switch (command) {

                case "color" -> {
                    if (args.length < 4) { System.err.println("Uso: color <R> <G> <B> [--brightness N]"); System.exit(1); }
                    int r = Integer.parseInt(args[1]);
                    int g = Integer.parseInt(args[2]);
                    int b = Integer.parseInt(args[3]);
                    int brightness = getIntArg(args, "--brightness", 70);
                    kbd.setColor(r, g, b, brightness);
                }

                case "effect" -> {
                    if (args.length < 2) { System.err.println("Uso: effect <nombre> [--r N] [--g N] [--b N] [--brightness N] [--speed N]"); System.exit(1); }
                    RedragonK617.Effect effect = RedragonK617.Effect.fromName(args[1]);
                    int r          = getIntArg(args, "--r",          0);
                    int g          = getIntArg(args, "--g",        150);
                    int b          = getIntArg(args, "--b",        255);
                    int brightness = getIntArg(args, "--brightness", 70);
                    int speed      = getIntArg(args, "--speed",      50);
                    kbd.setEffect(effect, r, g, b, brightness, speed);
                }

                case "brightness" -> {
                    if (args.length < 2) { System.err.println("Uso: brightness <0-100>"); System.exit(1); }
                    kbd.setBrightness(Integer.parseInt(args[1]));
                }

                case "off" -> kbd.off();

                case "demo" -> {
                    String tipo = args.length > 1 ? args[1].toLowerCase() : "colors";
                    if (tipo.equals("effects")) {
                        kbd.demoEffects(3000);
                    } else {
                        kbd.demoColors(1500);
                    }
                }

                default -> {
                    System.err.println("[ERROR] Comando desconocido: " + command);
                    printHelp();
                    System.exit(1);
                }
            }

        } catch (RuntimeException e) {
            System.err.println("\n[ERROR] " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("no encontrado")) {
                System.err.println("  → Verifica que el teclado esté conectado");
                System.err.println("  → Ejecuta como Administrador");
            }
            System.exit(1);
        } catch (InterruptedException e) {
            System.out.println("[Interrumpido]");
        } catch (Exception e) {
            System.err.println("\n[ERROR inesperado] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int getIntArg(String[] args, String flag, int defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase(flag)) {
                try { return Integer.parseInt(args[i + 1]); }
                catch (NumberFormatException ignored) {}
            }
        }
        return defaultValue;
    }

    private static void printHelp() {
        System.out.println("""
            ╔══════════════════════════════════════════════════════╗
            ║        Redragon K617 Fizz — RGB Controller           ║
            ║        VID: 0x258A  PID: 0x0049  (SinoWealth)       ║
            ╚══════════════════════════════════════════════════════╝

            Uso: RedragonK617.exe <comando> [opciones]

            Comandos:
              color <R> <G> <B> [--brightness N]
                  Color estático. R/G/B: 0-255. Brillo: 0-100 (default 70)

              effect <nombre> [--r N] [--g N] [--b N] [--brightness N] [--speed N]
                  Efectos disponibles:
                  off, static, breathing, wave, reactive,
                  ripple, stars, neon, rain, shadow, custom

              brightness <N>
                  Solo ajusta el brillo (0-100)

              off
                  Apaga todos los LEDs

              demo colors
                  Cicla por 7 colores (prueba básica de funcionamiento)

              demo effects
                  Muestra todos los efectos

            Ejemplos:
              RedragonK617.exe color 255 0 0
              RedragonK617.exe color 0 255 128 --brightness 90
              RedragonK617.exe effect breathing --r 0 --g 255 --b 0 --speed 40
              RedragonK617.exe effect wave
              RedragonK617.exe brightness 50
              RedragonK617.exe demo colors
            """);
    }
}
