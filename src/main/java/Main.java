// `src/main/java/Main.java`
import ppu.PPU;
import ppu.PPUImpl;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        var wram = new WRAMImpl();
        var cartridge = Cartridge.fromNesFile(null);
        var ppu = new PPUImpl();
        var bus = new CPUBus(wram, cartridge, ppu);
        var cpu = new CPU(bus);

        cpu.fetchProgramCounter();
        scheduleEmulation(cpu, ppu);
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
}