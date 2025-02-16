import java.util.Objects;

public class CPU {

    private int pc;
    private int sp;
    private final Register8Bit a, x, y;
    private boolean carry;
    private boolean zero;
    private boolean interrupt_disable;
    private boolean decimal;
    private boolean overflow;
    private boolean negative;

    private final Bus bus;

    private int remainingCycles;

    public CPU(Bus bus) {
        this.bus = bus;

        pc = 0x00;
        sp = 0xFD;
        a = new Register8Bit(0x00);
        x = new Register8Bit(0x00);
        y = new Register8Bit(0x00);
        //TODO find the correct initial values for the status flags
        carry = false;
        zero = false;
        interrupt_disable = true;
        decimal = false;
        overflow = false;
        negative = false;

        remainingCycles = 0;
    }

    public void loadState(EmulatorState emulatorState) {
        loadCpuState(emulatorState.cpuState());
        bus.loadWRamState(emulatorState.wram());
    }

    private void loadCpuState(CpuState cpuState) {
        if(Objects.nonNull(cpuState)) {
            this.pc = cpuState.getPc();
            this.sp = cpuState.getSp();
            this.a.setValue(cpuState.getA());
            this.x.setValue(cpuState.getX());
            this.y.setValue(cpuState.getY());
            this.carry = cpuState.isCarry();
            this.zero = cpuState.isZero();
            this.interrupt_disable = cpuState.isInterrupt_disable();
            this.decimal = cpuState.isDecimal();
            this.overflow = cpuState.isOverflow();
            this.negative = cpuState.isNegative();
        }
    }

    private static class InstructionState {
        Instruction instruction;
        AddressingMode addressingMode;

        int absoluteAddress;
        int operand;
    }

    public CpuState getState(){
        return new CpuState.Builder()
                .pc(pc)
                .sp(sp)
                .a(a.getValue())
                .x(x.getValue())
                .y(y.getValue())
                .carry(carry)
                .zero(zero)
                .interruptDisable(interrupt_disable)
                .decimal(decimal)
                .overflow(overflow)
                .negative(negative)
                .build();
    }

    private final InstructionState currInstruction = new InstructionState();

    public void fetchProgramCounter() {
        int highByte = bus.fetch(0xFFFD);
        int lowByte = bus.fetch(0xFFFC);
        int resetVector = (highByte << 8) | lowByte;
        System.out.printf("reset vector value: 0x%x\n", resetVector);
        this.pc = resetVector;
    }

    public int fetch() {
        return fetch(pc++) & 0xFF;
    }

    public int fetch(int address) {
        return bus.fetch(address) & 0xFF;
    }

    public void runCycle() {
        if(isOpCode()) {
            decodeOpCode(fetch());
        } else {
            executeInstruction();
        }
        remainingCycles--;
    }

    private boolean isOpCode() {
        return remainingCycles == 0;
    }

    //TODO change this to a table
    private void decodeOpCode(int opCode) {
        switch (opCode) {
            case 0xA1 -> loadInstructionInitialState(6, Instruction.LDA, AddressingMode.IND_X);
            case 0xA5 -> loadInstructionInitialState(3, Instruction.LDA, AddressingMode.ZPG);
            case 0xA9 -> loadInstructionInitialState(2, Instruction.LDA, AddressingMode.IMM);
            case 0xAD -> loadInstructionInitialState(4, Instruction.LDA, AddressingMode.ABS);
            case 0xB1 -> loadInstructionInitialState(6, Instruction.LDA, AddressingMode.IND_Y);
            case 0xB5 -> loadInstructionInitialState(4, Instruction.LDA, AddressingMode.ZPG_X);
            case 0xB9 -> loadInstructionInitialState(5, Instruction.LDA, AddressingMode.ABS_Y);
            case 0xBD -> loadInstructionInitialState(5, Instruction.LDA, AddressingMode.ABS_X);
            // LDX opcodes:
            case 0xA2 -> loadInstructionInitialState(2, Instruction.LDX, AddressingMode.IMM);
            case 0xA6 -> loadInstructionInitialState(3, Instruction.LDX, AddressingMode.ZPG);
            case 0xB6 -> loadInstructionInitialState(4, Instruction.LDX, AddressingMode.ZPG_Y);
            case 0xAE -> loadInstructionInitialState(4, Instruction.LDX, AddressingMode.ABS);
            case 0xBE -> loadInstructionInitialState(5, Instruction.LDX, AddressingMode.ABS_Y);
            default -> throw new RuntimeException(String.format("Invalid opcode: 0x%x at address 0x%x", opCode, --pc));
        }
    }

    private void executeInstruction() {
        switch (currInstruction.instruction) {
            case LDA -> LDA();
            case LDX -> LDX();

        }
    }

    private void loadInstructionInitialState(
            int cycles, Instruction instruction, AddressingMode addressingMode
    ) {
        this.remainingCycles = cycles;
        this.currInstruction.instruction = instruction;
        this.currInstruction.addressingMode = addressingMode;
    }

    private void handlePageCrossingInLoadInstruction(int address, Register8Bit register) {
        if((address & 0xFF00) == (currInstruction.absoluteAddress & 0xFF00)) {
            register.setValue(fetch(currInstruction.absoluteAddress));
            //avoid case 1 if no extra cycle is needed. This variable is already
            //decremented outside of this function, so if we decrement it here, we
            //make sure it will not reach case 1.
            remainingCycles--;
        }
    }

    private void LDA() {
        switch (currInstruction.addressingMode) {
            case IMM -> a.setValue(fetch());
            case ZPG -> handleLoad_ZeroPageMode(a);
            case ZPG_X -> handleLoad_ZeroPageIndexed(a, x);
            case ABS -> handleLoad_AbsoluteMode(a);
            case ABS_X -> handleLoad_AbsoluteIndexed(a, x);
            case ABS_Y -> handleLoad_AbsoluteIndexed(a, y);
            case IND_X -> handleLDAIndirectXIndexed();
            case IND_Y -> handleLDAIndirectYIndexed();
        }
        zero = (a.getValue() == 0);
        // Update Negative Flag (bit 7 of A)
        negative = (a.getValue() & 0x80) != 0;
    }

    private void LDX() {
        switch (currInstruction.addressingMode) {
            case IMM -> x.setValue(fetch());
            case ZPG -> handleLoad_ZeroPageMode(x);
            case ZPG_Y -> handleLoad_ZeroPageIndexed(x, y);
            case ABS -> handleLoad_AbsoluteMode(x);
            case ABS_Y -> handleLoad_AbsoluteIndexed(x, y);
            default -> throw new RuntimeException("Unsupported addressing mode for LDX: " + currInstruction.addressingMode);
        }
        // Update Zero and Negative flags based on X
        zero = (x.getValue() == 0);
        negative = (x.getValue() & 0x80) != 0;
    }

    private void handleLoad_ZeroPageMode(Register8Bit register) {
        switch (remainingCycles) {
            case 2 -> currInstruction.absoluteAddress = fetch();
            case 1 -> register.setValue(fetch(currInstruction.absoluteAddress));
        }
    }

    private void handleLoad_AbsoluteMode(Register8Bit register) {
        switch (remainingCycles) {
            case 3 -> currInstruction.absoluteAddress = fetch();
            case 2 -> currInstruction.absoluteAddress = (fetch() << 8) | currInstruction.absoluteAddress;
            case 1 -> register.setValue(fetch(currInstruction.absoluteAddress));
        }
    }

    private void handleLoad_ZeroPageIndexed(Register8Bit register, Register8Bit indexRegister) {
        switch (remainingCycles) {
            case 3 -> currInstruction.absoluteAddress = fetch();
            case 2 -> currInstruction.absoluteAddress = (currInstruction.absoluteAddress + indexRegister.getValue()) & 0xFF;
            case 1 -> register.setValue(fetch(currInstruction.absoluteAddress));
        }
    }

    private void handleLoad_AbsoluteIndexed(Register8Bit register, Register8Bit indexRegister) {
        switch (remainingCycles) {
            case 4 -> currInstruction.absoluteAddress = fetch();
            case 3 -> currInstruction.absoluteAddress = (fetch() << 8) | currInstruction.absoluteAddress;
            case 2 -> {
                var address = currInstruction.absoluteAddress;
                currInstruction.absoluteAddress = (address + indexRegister.getValue()) & 0xFFFF;
                handlePageCrossingInLoadInstruction(address, register);
            }
            case 1 -> register.setValue(fetch(currInstruction.absoluteAddress));
        }
    }

    private void handleLDAIndirectXIndexed() {
        switch (remainingCycles) {
            case 5 -> currInstruction.operand = fetch();
            case 4 -> currInstruction.operand = (currInstruction.operand + x.getValue()) & 0xFF;
            case 3 -> currInstruction.absoluteAddress = fetch(currInstruction.operand);
            case 2 -> {
                int highByte = fetch((currInstruction.operand + 1) & 0xFF);
                currInstruction.absoluteAddress = ((highByte << 8) | currInstruction.absoluteAddress) & 0xFFFF;
            }
            case 1 -> a.setValue(fetch(currInstruction.absoluteAddress));
        }
    }

    private void handleLDAIndirectYIndexed() {
        switch (remainingCycles) {
            case 5 -> currInstruction.operand = fetch();
            case 4 -> currInstruction.absoluteAddress = fetch(currInstruction.operand & 0xFF) ;
            case 3 -> {
                int highByte = fetch((currInstruction.operand + 1) & 0xFF);
                currInstruction.absoluteAddress = ((highByte << 8) | currInstruction.absoluteAddress) & 0xFFFF;
            }
            case 2 -> {
                var address = currInstruction.absoluteAddress;
                currInstruction.absoluteAddress = (address + y.getValue()) & 0xFFFF;
                handlePageCrossingInLoadInstruction(address, a);
            }
            case 1 -> a.setValue(fetch(currInstruction.absoluteAddress));
        }
    }
}
