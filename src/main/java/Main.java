import ppu.PPU;
import ppu.PPUImpl;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        if(args.length != 1) {
            System.err.println("Usage: java Main <path_to_nes_file>");
            System.exit(1);
        }

        String romPath = args[0];
        byte[] romData = loadRomFile(romPath);

        var cartridge = Cartridge.fromNesFile(romData);
        var wram = new WRAMImpl();
        var ppu = new PPUImpl();
        var bus = new CPUBus(wram, cartridge, ppu);
        var cpu = new CPU(bus);

        cpu.fetchProgramCounter();

        SwingUtilities.invokeLater(() -> {
            EmulatorUI emulatorUI = new EmulatorUI(cpu, ppu);
            emulatorUI.setVisible(true);
            emulatorUI.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            bus.setController1(emulatorUI.getController());
            emulatorUI.start();
        });
    }

    private static void scheduleEmulation(CPU cpu, PPU ppu) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        long cycleDuration = 559; // nanoseconds for NTSC

        scheduler.scheduleAtFixedRate(() -> {
            cpu.runCycle();
            ppu.runCycle();
            ppu.runCycle();
            ppu.runCycle();
        }, 0, cycleDuration, TimeUnit.NANOSECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdownNow));
    }

    private static byte[] loadRomFile(String filePath) {
        try {
            return Files.readAllBytes(Paths.get(filePath));
        } catch (IOException e) {
            System.err.println("Error loading ROM file: " + e.getMessage());
            System.exit(1);
            return null;
        }
    }
}
