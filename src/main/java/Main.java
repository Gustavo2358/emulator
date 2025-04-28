import ppu.PPU;
import ppu.PPUImpl;

public class Main {
    public static void main(String[] args) {
        var wram = new WRAMImpl();
        var cartridge = Cartridge.fromNesFile(null);
        var ppu = new PPUImpl();
        var bus = new CPUBus(wram, cartridge, ppu);
        var cpu = new CPU(bus);
        startEmulation(cpu, ppu);
    }

    private static void startEmulation(CPU cpu, PPU ppu) {
        cpu.fetchProgramCounter();
        final long cycleDuration = 559; // in nanoseconds for NTSC
        long nextCycleTime = System.nanoTime();

        while (true) {
            long now = System.nanoTime();
            long remaining = nextCycleTime - now;

            if (remaining <= 0) {
                cpu.runCycle();
                ppu.runCycle();
                ppu.runCycle();
                ppu.runCycle();
                nextCycleTime += cycleDuration;
            } else if (remaining > 1_000) { // more than 1 microsecond remaining
                try {
                    Thread.sleep(0, (int) Math.max(remaining - 500, 0));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            // When remaining time is very short, just busy-wait without yielding
        }
    }
}