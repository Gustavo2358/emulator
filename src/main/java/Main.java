public class Main {
    public static void main(String[] args) {
        var wram = new WRAM();
        wram.memory = new int[0x10000];
        //reset vector
        wram.memory[0xFFFC] = 0x00;
        wram.memory[0xFFFD] = 0x80;
        //simulate LDA #$42
        wram.memory[0x8000] = 0xA9;
        wram.memory[0x8001] = 0x42;
        //simulate LDA $9000
        wram.memory[0x9000] = 0x43;
        wram.memory[0x8002] = 0xAD;
        wram.memory[0x8003] = 0x00;
        wram.memory[0x8004] = 0x90;
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
