public class Main {
    public static void main(String[] args) {
        var wram = new WRAM();
        var bus = new Bus(wram);
        var cpu = new CPU(bus);
        startEmulation(cpu);
    }

    private static void startEmulation(CPU cpu) {
        cpu.fetchProgramCounter();
        while(true) {
            cpu.runCycle();
//            ppu.runCycle();
//            ppu.runCycle();
//            ppu.runCycle();
        }
    }
}
