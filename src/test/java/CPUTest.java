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

        // Reset vector
        wram.memory[0xFFFC] = 0x00;
        wram.memory[0xFFFD] = 0x80;

        // 0x10: base address
        wram.memory[0x8000] = 0xB5;
        wram.memory[0x8001] = 0x10;
        var cpuState = new CpuState.Builder()
                .x(0x05)
                .build();

        // simulate LDA $10,X
        // base (0x10) + X (0x05) = 0x15.
        wram.memory[0x0015] = 0x42;

        cpu.loadState(getEmulatorState(cpuState, wram));

        cpu.fetchProgramCounter();
        runCpuCycles(instructionCycles, cpu);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAAbsoluteXIndexed_NoPageCrossing() {
        int instructionCycles = 4;
        var wram = new WRAM();
        wram.memory = new int[0x10000];

        // Set reset vector to 0x8000.
        wram.memory[0xFFFC] = 0x00;
        wram.memory[0xFFFD] = 0x80;

        // Simulate LDA $9000,X.
        // Opcode for LDA absolute,X is 0xBD.
        // Base address: 0x9000, X = 0x05, so effective address: 0x9000 + 0x05 = 0x9005.
        wram.memory[0x8000] = 0xBD;   // LDA absolute,X opcode.
        wram.memory[0x8001] = 0x00;   // Low byte of base address.
        wram.memory[0x8002] = 0x90;   // High byte of base address.

        // Place the operand at the effective address.
        wram.memory[0x9005] = 0x42;

        // Set CPU state with X = 0x05.
        var cpuState = new CpuState.Builder()
                .x(0x05)
                .build();
        var cpu = initiateCpuWithCleanMemory();
        cpu.loadState(getEmulatorState(cpuState, wram));

        // Fetch the reset vector, setting PC to 0x8000.
        cpu.fetchProgramCounter();
        runCpuCycles(instructionCycles, cpu);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAAbsoluteXIndexed_PageCrossing() {
        int instructionCycles = 5;
        var wram = new WRAM();
        wram.memory = new int[0x10000];

        // Set reset vector to 0x8000.
        wram.memory[0xFFFC] = 0x00;
        wram.memory[0xFFFD] = 0x80;

        // Simulate LDA $90FF,X.
        // Opcode for LDA absolute,X is 0xBD.
        // Base address: 0x90FF, with X = 0x01, the effective address becomes 0x90FF + 0x01 = 0x9100.
        wram.memory[0x8000] = 0xBD;   // LDA absolute,X opcode.
        wram.memory[0x8001] = 0xFF;   // Low byte of base address.
        wram.memory[0x8002] = 0x90;   // High byte of base address.

        // Place the operand at the effective address (0x9100).
        wram.memory[0x9100] = 0x42;

        // Set CPU state with X = 0x01.
        var cpuState = new CpuState.Builder()
                .x(0x01)
                .build();
        var cpu = initiateCpuWithCleanMemory();
        cpu.loadState(getEmulatorState(cpuState, wram));

        // Fetch the reset vector to set PC to 0x8000.
        cpu.fetchProgramCounter();
        runCpuCycles(instructionCycles, cpu);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAIndirectX() {
        int instructionCycles = 6;
        var wram = new WRAM();
        wram.memory = new int[0x10000];

        // Reset vector set to 0x8000
        wram.memory[0xFFFC] = 0x00;
        wram.memory[0xFFFD] = 0x80;

        // Simulate LDA ($10, X)
        // Opcode for LDA Indirect, X is 0xA1.
        // At address 0x8000: opcode 0xA1, at 0x8001: operand 0x10.
        wram.memory[0x8000] = 0xA1;
        wram.memory[0x8001] = 0x10;

        // Set CPU state with X = 0x05.
        // The effective zero page pointer will be (0x10 + 0x05) % 256 = 0x15.
        var cpuState = new CpuState.Builder().x(0x05).build();

        // At zero page address 0x15, store the low byte of the effective address.
        // At 0x16, store the high byte.
        // For example, let's use effective address 0x9000.
        wram.memory[0x15] = 0x00; // Low byte of 0x9000.
        wram.memory[0x16] = 0x90; // High byte of 0x9000.

        // Place the operand at the effective address.
        wram.memory[0x9000] = 0x42;

        var cpu = initiateCpuWithCleanMemory();
        cpu.loadState(getEmulatorState(cpuState, wram));
        cpu.fetchProgramCounter();
        runCpuCycles(instructionCycles, cpu);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAIndirectY_NoPageCrossing() {
        int instructionCycles = 5;
        var wram = new WRAM();
        wram.memory = new int[0x10000];

        // Reset vector: points to 0x8000.
        wram.memory[0xFFFC] = 0x00;
        wram.memory[0xFFFD] = 0x80;

        // Simulate LDA (indirect),Y:
        // Opcode 0xB1 for (indirect),Y; operand at 0x8001 is the zero-page pointer.
        wram.memory[0x8000] = 0xB1;
        wram.memory[0x8001] = 0x20; // Zero-page address where the pointer is stored.

        // Set the pointer in zero page at 0x20 to point to base address 0x9000.
        wram.memory[0x20] = 0x00; // Low byte of 0x9000.
        wram.memory[0x21] = 0x90; // High byte of 0x9000.

        // Set the Y register so that adding it to the base address does NOT cross a page.
        // For example, Y = 0x05 gives effective address: 0x9000 + 0x05 = 0x9005.
        var cpuState = new CpuState.Builder()
                .y(0x05)
                .build();
        // Place the operand at the effective address.
        wram.memory[0x9005] = 0x42;

        var cpu = initiateCpuWithCleanMemory();
        cpu.loadState(getEmulatorState(cpuState, wram));

        // Set the program counter from the reset vector.
        cpu.fetchProgramCounter();
        runCpuCycles(instructionCycles, cpu);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAIndirectY_PageCrossing() {
        int instructionCycles = 6;
        var wram = new WRAM();
        wram.memory = new int[0x10000];

        // Reset vector: points to 0x8000.
        wram.memory[0xFFFC] = 0x00;
        wram.memory[0xFFFD] = 0x80;

        // Simulate LDA (indirect),Y:
        // Opcode 0xB1 for (indirect),Y; operand at 0x8001 is the zero-page pointer.
        wram.memory[0x8000] = 0xB1;
        wram.memory[0x8001] = 0x30; // Zero-page address where the pointer is stored.

        // Set the pointer in zero page at 0x30 to point to base address 0x90FF.
        wram.memory[0x30] = 0xFF; // Low byte of 0x90FF.
        wram.memory[0x31] = 0x90; // High byte of 0x90FF.

        // Set the Y register so that adding it to the base address causes a page crossing.
        // For example, Y = 0x01 gives effective address: 0x90FF + 0x01 = 0x9100.
        var cpuState = new CpuState.Builder()
                .y(0x01)
                .build();
        // Place the operand at the effective address.
        wram.memory[0x9100] = 0x42;

        var cpu = initiateCpuWithCleanMemory();
        cpu.loadState(getEmulatorState(cpuState, wram));

        // Set the program counter from the reset vector.
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