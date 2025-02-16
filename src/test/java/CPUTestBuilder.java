public class CPUTestBuilder {

    private final WRAM wram;
    private final CpuState.Builder cpuStateBuilder;

    public CPUTestBuilder() {
        // Initialize WRAM with a 64K memory array.
        wram = new WRAM();
        wram.memory = new int[0x10000];
        cpuStateBuilder = new CpuState.Builder();
    }

    /**
     * Sets the reset vector in memory.
     *
     * @param address The address where the CPU will start execution.
     * @return The builder instance.
     */
    public CPUTestBuilder withResetVector(int address) {
        wram.memory[0xFFFC] = address & 0xFF;
        wram.memory[0xFFFD] = (address >> 8) & 0xFF;
        return this;
    }

    /**
     * Loads an instruction at a specified address.
     *
     * @param address The starting memory address.
     * @param opcode  The opcode of the instruction.
     * @param operands The operands of the instruction.
     * @return The builder instance.
     */
    public CPUTestBuilder withInstruction(int address, int opcode, int... operands) {
        wram.memory[address] = opcode;
        for (int i = 0; i < operands.length; i++) {
            wram.memory[address + 1 + i] = operands[i];
        }
        return this;
    }

    /**
     * Sets a two-byte pointer in the zero page.
     *
     * @param zeroPageAddress The address in the zero page to set the pointer.
     * @param low The low byte of the target address.
     * @param high The high byte of the target address.
     * @return The builder instance.
     */
    public CPUTestBuilder withZeroPagePointer(int zeroPageAddress, int low, int high) {
        wram.memory[zeroPageAddress] = low;
        // Ensure that the second byte is also in the zero page (wrap-around if needed).
        wram.memory[(zeroPageAddress + 1) & 0xFF] = high;
        return this;
    }

    /**
     * Sets the value of the Y register.
     *
     * @param value The value to load into the Y register.
     * @return The builder instance.
     */
    public CPUTestBuilder withRegisterY(int value) {
        cpuStateBuilder.y(value);
        return this;
    }

    /**
     * Sets the value of the X register.
     *
     * @param value The value to load into the X register.
     * @return The builder instance.
     */
    public CPUTestBuilder withRegisterX(int value) {
        cpuStateBuilder.x(value);
        return this;
    }

    /**
     * Sets the value of the Accumulator.
     *
     * @param value The value to load into the Accumulator.
     * @return The builder instance.
     */
    public CPUTestBuilder withRegisterA(int value) {
        cpuStateBuilder.a(value);
        return this;
    }

    /**
     * Sets a memory value at the specified address.
     *
     * @param address The memory address.
     * @param value   The value to write.
     * @return The builder instance.
     */
    public CPUTestBuilder withMemoryValue(int address, int value) {
        wram.memory[address] = value;
        return this;
    }

    /**
     * Builds the CPU and runs it for the given number of cycles.
     *
     * @param cycles The number of CPU cycles to run.
     * @return The CPU after running the specified cycles.
     */
    public CPU buildAndRun(int cycles) {
        Bus bus = new MockBus(wram);
        CPU cpu = new CPU(bus);
        EmulatorState state = new EmulatorState(cpuStateBuilder.build(), wram);
        cpu.loadState(state);
        cpu.fetchProgramCounter();
        // Run the CPU for the specified number of cycles.
        for (int i = 0; i < cycles; i++) {
            cpu.runCycle();
        }
        return cpu;
    }

    /**
     * Builds the CPU and runs it for the given number of cycles.
     *
     * @param cycles The number of CPU cycles to run.
     * @param bus The bus.
     * @return The CPU after running the specified cycles.
     */
    public CPU buildAndRun(int cycles, Bus bus) {
        bus.loadWRamState(wram);
        CPU cpu = new CPU(bus);
        EmulatorState state = new EmulatorState(cpuStateBuilder.build(), wram);
        cpu.loadState(state);
        cpu.fetchProgramCounter();
        // Run the CPU for the specified number of cycles.
        for (int i = 0; i < cycles; i++) {
            cpu.runCycle();
        }
        return cpu;
    }
}
