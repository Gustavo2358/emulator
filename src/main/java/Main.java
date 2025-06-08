import core.*;
import ppu.PPUImpl;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if(args.length != 1) {
            System.err.println("Usage: java Main <path_to_nes_file>");
            System.exit(1);
        }

        String romPath = args[0];
        byte[] romData = loadRomFile(romPath);

        Cartridge cartridge = Cartridge.fromNesFile(romData);
        WRAMImpl wram = new WRAMImpl();
        PPUImpl ppu = new PPUImpl(cartridge); // Pass cartridge to PPU constructor
        CPUBus bus = new CPUBus(wram, cartridge, ppu);
        CPU cpu = new CPU(bus);
        bus.setCpu(cpu); // Set the CPU instance in the bus

        ppu.setCpu(cpu); // Set core.CPU instance in PPU for NMI
        ppu.setCpuBus(bus); // Ensure PPUImpl gets a reference to CPUBus

        cpu.fetchProgramCounter();

        SwingUtilities.invokeLater(() -> {
            EmulatorUI emulatorUI = new EmulatorUI(cpu, ppu);
            emulatorUI.setVisible(true);
            emulatorUI.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            bus.setController1(emulatorUI.getController());
            emulatorUI.start();
        });
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
