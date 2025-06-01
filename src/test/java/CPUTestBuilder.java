import core.*;

public class CPUTestBuilder {

    private final WRAM wram;
    private final CpuState.Builder cpuStateBuilder;

    public CPUTestBuilder() {
        // Initialize core.WRAM with a 64K memory array.
        wram = new MockWRAM(new int[0x10000]);
        cpuStateBuilder = new CpuState.Builder();
    }

    /**
     * Sets the reset vector in memory.
     *
     * @param address The address where the core.CPU will start execution.
     * @return The builder instance.
     */
    public CPUTestBuilder withResetVector(int address) {
        wram.write(0xFFFC, address & 0xFF);
        wram.write(0xFFFD, (address >> 8) & 0xFF);
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
        wram.write(address, opcode);
        for (int i = 0; i < operands.length; i++) {
            wram.write(address + 1 + i, operands[i]);
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
        wram.write(zeroPageAddress, low);
        // Ensure that the second byte is also in the zero page (wrap-around if needed).
        wram.write((zeroPageAddress + 1) & 0xFF, high);
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
        wram.write(address, value);
        return this;
    }

    /**
     * Sets the value of the stack pointer.
     *
     * @param value The value to load into the stack pointer.
     * @return The builder instance.
     */
    public CPUTestBuilder withStackPointer(int value) {
        cpuStateBuilder.sp(value);
        return this;
    }

    /**
     * Sets the Negative flag.
     *
     * @param flag The value for the Negative flag.
     * @return The builder instance.
     */
    public CPUTestBuilder withFlagNegative(boolean flag) {
        cpuStateBuilder.negative(flag);
        return this;
    }

    /**
     * Sets the Overflow flag.
     *
     * @param flag The value for the Overflow flag.
     * @return The builder instance.
     */
    public CPUTestBuilder withFlagOverflow(boolean flag) {
        cpuStateBuilder.overflow(flag);
        return this;
    }

    /**
     * Sets the Interrupt Disable flag.
     *
     * @param flag The value for the Interrupt Disable flag.
     * @return The builder instance.
     */
    public CPUTestBuilder withFlagInterruptDisable(boolean flag) {
        cpuStateBuilder.interruptDisable(flag);
        return this;
    }

    /**
     * Sets the Decimal flag.
     *
     * @param flag The value for the Decimal flag.
     * @return The builder instance.
     */
    public CPUTestBuilder withFlagDecimal(boolean flag) {
        cpuStateBuilder.decimal(flag);
        return this;
    }

    /**
     * Sets the Zero flag.
     *
     * @param flag The value for the Zero flag.
     * @return The builder instance.
     */
    public CPUTestBuilder withFlagZero(boolean flag) {
        cpuStateBuilder.zero(flag);
        return this;
    }

    /**
     * Sets the Carry flag.
     *
     * @param flag The value for the Carry flag.
     * @return The builder instance.
     */
    public CPUTestBuilder withFlagCarry(boolean flag) {
        cpuStateBuilder.carry(flag);
        return this;
    }

    /**
     * Builds the core.CPU and runs it for the given number of cycles.
     *
     * @param cycles The number of core.CPU cycles to run.
     * @return The core.CPU after running the specified cycles.
     */
    public CPU buildAndRun(int cycles) {
        Bus bus = new MockBus(wram);
        CPU cpu = new CPU(bus);
        EmulatorState state = new EmulatorState(cpuStateBuilder.build(), wram);
        cpu.loadState(state);
        cpu.fetchProgramCounter();
        // Run the core.CPU for the specified number of cycles.
        for (int i = 0; i < cycles; i++) {
            cpu.runCycle();
        }
        return cpu;
    }

    /**
     * Builds the core.CPU and runs it for the given number of cycles.
     *
     * @param cycles The number of core.CPU cycles to run.
     * @param bus The bus.
     * @return The core.CPU after running the specified cycles.
     */
    public CPU buildAndRun(int cycles, Bus bus) {
        bus.loadWRamState(wram);
        CPU cpu = new CPU(bus);
        EmulatorState state = new EmulatorState(cpuStateBuilder.build(), wram);
        cpu.loadState(state);
        cpu.fetchProgramCounter();
        // Run the core.CPU for the specified number of cycles.
        for (int i = 0; i < cycles; i++) {
            cpu.runCycle();
        }
        return cpu;
    }
}
