import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
class CPUTest {

    @Test
    public void LDAImmediate(){
        int instructionCycles = 2;
        var wram = new WRAM();
        wram.memory = new int[0x10000];
        //reset vector
        wram.memory[0xFFFC] = 0x00;
        wram.memory[0xFFFD] = 0x80;
        //simulate LDA #$42
        wram.memory[0x8000] = 0xA9;
        wram.memory[0x8001] = 0x42;

        var cpu = initiateCpuWithCleanMemory();
        cpu.loadState(getEmulatorState(wram));

        cpu.fetchProgramCounter();
        runCpuCycles(instructionCycles, cpu);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAAbsolute(){
        int instructionCycles = 4;
        var wram = new WRAM();
        wram.memory = new int[0x10000];
        //reset vector
        wram.memory[0xFFFC] = 0x00;
        wram.memory[0xFFFD] = 0x80;
        //simulate LDA $9000
        wram.memory[0x9000] = 0x42;
        wram.memory[0x8000] = 0xAD;
        wram.memory[0x8001] = 0x00;
        wram.memory[0x8002] = 0x90;

        var cpu = initiateCpuWithCleanMemory();
        cpu.loadState(getEmulatorState(wram));
        cpu.fetchProgramCounter();
        runCpuCycles(instructionCycles, cpu);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAZeroPage(){
        int instructionCycles = 3;
        var wram = new WRAM();
        wram.memory = new int[0x10000];

        // Reset vector
        wram.memory[0xFFFC] = 0x00;
        wram.memory[0xFFFD] = 0x80;

        // Simulate LDA $10 (Zero Page)
        wram.memory[0x8000] = 0xA5;
        wram.memory[0x8001] = 0x10;
        wram.memory[0x0010] = 0x42;

        var cpu = initiateCpuWithCleanMemory();
        cpu.loadState(getEmulatorState(wram));
        cpu.fetchProgramCounter();
        runCpuCycles(instructionCycles, cpu);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAZeroPageXIndexed() {
        int instructionCycles = 4;
        var cpu = initiateCpuWithCleanMemory();
        var wram = new WRAM();
        wram.memory = new int[0x10000];

        // Reset vector setup
        wram.memory[0xFFFC] = 0x00;
        wram.memory[0xFFFD] = 0x80;

        //  - 0x10: Operand (base address)
        wram.memory[0x8000] = 0xB5;
        wram.memory[0x8001] = 0x10;
        var cpuState = new CpuState.Builder()
                .x(0x05)
                .build();

        // base (0x10) + X (0x05) = 0x15.
        wram.memory[0x0015] = 0x42;

        cpu.loadState(getEmulatorState(cpuState, wram));

        cpu.fetchProgramCounter();
        runCpuCycles(instructionCycles, cpu);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    private static CPU initiateCpuWithCleanMemory(){
        var wram = new WRAM();
        wram.memory = new int[0x10000];
        //reset vector
        wram.memory[0xFFFC] = 0x00;
        wram.memory[0xFFFD] = 0x80;

        var bus = new Bus(wram);
        return new CPU(bus);
    }

    private static EmulatorState getEmulatorState(WRAM wram) {
        return getEmulatorState(null, wram);
    }

    private static EmulatorState getEmulatorState(CpuState cpuState, WRAM wram) {
        return new EmulatorState(cpuState, wram);
    }

    private static void runCpuCycles(int n, CPU cpu) {
        for (int i = 0; i < n; i++) {
            cpu.runCycle();
        }
    }

}