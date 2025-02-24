import java.util.Objects;
import java.util.function.Function;

public class CPU {

    private int pc;
    private final Register8Bit sp;
    private final Register8Bit a, x, y;
    private boolean carry;
    private boolean zero;
    private boolean interruptDisable;
    private boolean decimal;
    private boolean overflow;
    private boolean negative;

    private final Bus bus;

    private int remainingCycles;

    public CPU(Bus bus) {
        this.bus = bus;

        pc = 0x00;
        sp = new Register8Bit(0xFD);
        a = new Register8Bit(0x00);
        x = new Register8Bit(0x00);
        y = new Register8Bit(0x00);
        //TODO find the correct initial values for the status flags
        carry = false;
        zero = false;
        interruptDisable = true;
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
            this.sp.setValue(cpuState.getSp());
            this.a.setValue(cpuState.getA());
            this.x.setValue(cpuState.getX());
            this.y.setValue(cpuState.getY());
            this.carry = cpuState.isCarry();
            this.zero = cpuState.isZero();
            this.interruptDisable = cpuState.isInterruptDisable();
            this.decimal = cpuState.isDecimal();
            this.overflow = cpuState.isOverflow();
            this.negative = cpuState.isNegative();
        }
    }

    private static class InstructionState {
        Instruction instruction;
        AddressingMode addressingMode;

        int effectiveAddress;
        int operand;
        int tempLatch;
    }

    public CpuState getState(){
        return new CpuState.Builder()
                .pc(pc)
                .sp(sp.getValue())
                .a(a.getValue())
                .x(x.getValue())
                .y(y.getValue())
                .carry(carry)
                .zero(zero)
                .interruptDisable(interruptDisable)
                .decimal(decimal)
                .overflow(overflow)
                .negative(negative)
                .build();
    }

    private final InstructionState currInstruction = new InstructionState();

    public void fetchProgramCounter() {
        int highByte = bus.read(0xFFFD);
        int lowByte = bus.read(0xFFFC);
        int resetVector = (highByte << 8) | lowByte;
        System.out.printf("reset vector value: 0x%x\n", resetVector);
        this.pc = resetVector;
    }

    public int fetch() {
        return read(pc++) & 0xFF;
    }

    public int read(int address) {
        return bus.read(address) & 0xFF;
    }

    private void write(int effectiveAddress, int value) {
        bus.write(effectiveAddress, value);
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
            // LDY opcodes:
            case 0xA0 -> loadInstructionInitialState(2, Instruction.LDY, AddressingMode.IMM);
            case 0xA4 -> loadInstructionInitialState(3, Instruction.LDY, AddressingMode.ZPG);
            case 0xAC -> loadInstructionInitialState(4, Instruction.LDY, AddressingMode.ABS);
            case 0xB4 -> loadInstructionInitialState(4, Instruction.LDY, AddressingMode.ZPG_X);
            case 0xBC -> loadInstructionInitialState(5, Instruction.LDY, AddressingMode.ABS_X);
            // STA opcodes:
            case 0x85 -> loadInstructionInitialState(3, Instruction.STA, AddressingMode.ZPG);
            case 0x95 -> loadInstructionInitialState(4, Instruction.STA, AddressingMode.ZPG_X);
            case 0x8D -> loadInstructionInitialState(4, Instruction.STA, AddressingMode.ABS);
            case 0x9D -> loadInstructionInitialState(5, Instruction.STA, AddressingMode.ABS_X);
            case 0x99 -> loadInstructionInitialState(5, Instruction.STA, AddressingMode.ABS_Y);
            case 0x81 -> loadInstructionInitialState(6, Instruction.STA, AddressingMode.IND_X);
            case 0x91 -> loadInstructionInitialState(6, Instruction.STA, AddressingMode.IND_Y);
            // STX opcodes:
            case 0x86 -> loadInstructionInitialState(3, Instruction.STX, AddressingMode.ZPG);
            case 0x96 -> loadInstructionInitialState(4, Instruction.STX, AddressingMode.ZPG_Y);
            case 0x8E -> loadInstructionInitialState(4, Instruction.STX, AddressingMode.ABS);
            // STY opcodes:
            case 0x84 -> loadInstructionInitialState(3, Instruction.STY, AddressingMode.ZPG);
            case 0x94 -> loadInstructionInitialState(4, Instruction.STY, AddressingMode.ZPG_X);
            case 0x8C -> loadInstructionInitialState(4, Instruction.STY, AddressingMode.ABS);
            // TAX opcode
            case 0xAA -> loadInstructionInitialState(2, Instruction.TAX, AddressingMode.IMP);
            // TAY opcode:
            case 0xA8 -> loadInstructionInitialState(2, Instruction.TAY, AddressingMode.IMP);
            // TSX opcode
            case 0xBA -> loadInstructionInitialState(2, Instruction.TSX, AddressingMode.IMP);
            // TXA opcode:
            case 0x8A -> loadInstructionInitialState(2, Instruction.TXA, AddressingMode.IMP);
            // TXS opcode:
            case 0x9A -> loadInstructionInitialState(2, Instruction.TXS, AddressingMode.IMP);
            // TYA opcode:
            case 0x98 -> loadInstructionInitialState(2, Instruction.TYA, AddressingMode.IMP);
            // PHA opcode:
            case 0x48 -> loadInstructionInitialState(3, Instruction.PHA, AddressingMode.IMP);
            // PHP opcode:
            case 0x08 -> loadInstructionInitialState(3, Instruction.PHP, AddressingMode.IMP);
            // PLA opcode:
            case 0x68 -> loadInstructionInitialState(4, Instruction.PLA, AddressingMode.IMP);
            //PLP opcode
            case 0x28 -> loadInstructionInitialState(4, Instruction.PLP, AddressingMode.IMP);
            //DEC opcodes:
            case 0xC6 -> loadInstructionInitialState(5, Instruction.DEC, AddressingMode.ZPG);
            case 0xD6 -> loadInstructionInitialState(6, Instruction.DEC, AddressingMode.ZPG_X);
            case 0xCE -> loadInstructionInitialState(6, Instruction.DEC, AddressingMode.ABS);
            case 0xDE -> loadInstructionInitialState(7, Instruction.DEC, AddressingMode.ABS_X);

            default -> throw new RuntimeException(String.format("Invalid opcode: 0x%x at address 0x%x", opCode, --pc));
        }
    }

    private void executeInstruction() {
        switch (currInstruction.instruction) {
            case LDA -> LDA();
            case LDX -> LDX();
            case LDY -> LDY();
            case STA -> STA();
            case STX -> STX();
            case STY -> STY();
            case TAX -> TAX();
            case TAY -> TAY();
            case TSX -> TSX();
            case TXA -> TXA();
            case TXS -> TXS();
            case TYA -> TYA();
            case PHA -> PHA();
            case PHP -> PHP();
            case PLA -> PLA();
            case PLP -> PLP();
            case DEC -> DEC();
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
        if((address & 0xFF00) == (currInstruction.effectiveAddress & 0xFF00)) {
            register.setValue(read(currInstruction.effectiveAddress));
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

        if(remainingCycles == 1) {
            zero = (a.getValue() == 0);
            // Update Negative Flag (bit 7 of A)
            negative = (a.getValue() & 0x80) != 0;
        }
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

        if(remainingCycles == 1) {
            // Update Zero and Negative flags based on X
            zero = (x.getValue() == 0);
            negative = (x.getValue() & 0x80) != 0;
        }
    }

    private void LDY() {
        switch (currInstruction.addressingMode) {
            case IMM -> y.setValue(fetch());
            case ZPG -> handleLoad_ZeroPageMode(y);
            case ZPG_X -> handleLoad_ZeroPageIndexed(y, x);
            case ABS -> handleLoad_AbsoluteMode(y);
            case ABS_X -> handleLoad_AbsoluteIndexed(y, x);
            default -> throw new RuntimeException("Unsupported addressing mode for LDY: " + currInstruction.addressingMode);
        }

        if(remainingCycles == 1) {
            // Update Zero and Negative flags based on X
            zero = (x.getValue() == 0);
            negative = (x.getValue() & 0x80) != 0;
        }
    }

    private void STA() {
        switch (currInstruction.addressingMode) {
            case ZPG -> handleStore_ZeroPage(a);
            case ZPG_X -> handleStore_ZeroPageIndexed(a, x);
            case ABS -> handleStore_Absolute(a);
            case ABS_X -> handleSTAAbsoluteIndexed(x);
            case ABS_Y -> handleSTAAbsoluteIndexed(y);
            case IND_X -> handleSTAIndirectX();
            case IND_Y -> handleSTAIndirectY();
            default -> throw new RuntimeException("Unsupported addressing mode for STA: " + currInstruction.addressingMode);
        }
    }

    private void STX() {
        switch (currInstruction.addressingMode) {
            case ZPG -> handleStore_ZeroPage(x);
            case ZPG_Y -> handleStore_ZeroPageIndexed(x, y);
            case ABS -> handleStore_Absolute(x);
            default -> throw new RuntimeException("Unsupported addressing mode for STX: " + currInstruction.addressingMode);
        }
    }

    private void STY() {
        switch (currInstruction.addressingMode) {
            case ZPG -> handleStore_ZeroPage(y);
            case ZPG_X -> handleStore_ZeroPageIndexed(y, x);
            case ABS -> handleStore_Absolute(y);
            default -> throw new RuntimeException("Unsupported addressing mode for STY: " + currInstruction.addressingMode);
        }
    }

    private void TAX() {
        x.setValue(a.getValue());
        zero = (x.getValue() == 0);
        negative = (x.getValue() & 0x80) != 0;
    }

    private void TAY() {
        y.setValue(a.getValue());
        zero = (y.getValue() == 0);
        negative = (y.getValue() & 0x80) != 0;
    }

    private void TSX() {
        x.setValue(sp.getValue());
        zero = (x.getValue() == 0);
        negative = (x.getValue() & 0x80) != 0;
    }

    private void TXA() {
        a.setValue(x.getValue());
        zero = (a.getValue() == 0);
        negative = (a.getValue() & 0x80) != 0;
    }

    private void TXS() {
        sp.setValue(x.getValue());
    }

    private void TYA() {
        a.setValue(y.getValue());
        zero = (a.getValue() == 0);
        negative = (a.getValue() & 0x80) != 0;
    }

    private void PHA() {
        switch (remainingCycles) {
            case 2 -> write(0x0100 | sp.getValue(), a.getValue());
            case 1 -> sp.setValue((sp.getValue() - 1) & 0xFF);
        }
    }

    // Converts the current CPU flags into an 8-bit representation.
    // Bit layout: N V 1 B D I Z C
    private int flagsToBits() {
        int flags = 0;
        if (negative) flags |= 0x80;
        if (overflow) flags |= 0x40;
        flags |= 0x20;
        flags |= 0x10;
        if (decimal) flags |= 0x08;
        if (interruptDisable) flags |= 0x04;
        if (zero) flags |= 0x02;
        if (carry) flags |= 0x01;
        return flags;
    }

    private void PHP() {
        switch (remainingCycles) {
            case 2 -> write(0x0100 | sp.getValue(), flagsToBits());
            case 1 -> sp.setValue((sp.getValue() - 1) & 0xFF);
        }
    }

    private void PLA() {
        switch (remainingCycles) {
            case 3 -> {} //dummy read
            case 2 -> sp.setValue((sp.getValue() + 1) & 0xFF);
            case 1 -> a.setValue(read(0x0100 | sp.getValue()));
        }

        if(remainingCycles == 1) {
            zero = (a.getValue() == 0);
            negative = (a.getValue() & 0x80) != 0;
        }
    }

    private void PLP() {
        switch (remainingCycles) {
            case 3 -> {} //dummy read
            case 2 -> sp.setValue((sp.getValue() + 1) & 0xFF);
            case 1 -> {
                int pulled = read(0x0100 | sp.getValue());
                negative         = (pulled & 0x80) != 0;
                overflow         = (pulled & 0x40) != 0;
                // Bits 0x20 and 0x10 are constant in flagsToBits.
                decimal          = (pulled & 0x08) != 0;
                interruptDisable = (pulled & 0x04) != 0;
                zero             = (pulled & 0x02) != 0;
                carry            = (pulled & 0x01) != 0;
            }
        }
    }

    private void DEC() {
        Function<Integer, Integer> decrementOperation = value -> value - 1;
        switch (currInstruction.addressingMode) {
            case ZPG -> handleReadModifyWriteInstructions_ZeroPageMode(decrementOperation);
            case ZPG_X -> handleReadModifyWriteInstructions_ZeroPageIndexed(x, decrementOperation);
            case ABS -> handleReadModifyWriteInstructions_AbsoluteMode(decrementOperation);
            case ABS_X -> handleReadModifyWriteInstructions_AbsoluteIndexed(x, decrementOperation);
            default -> throw new RuntimeException("Unsupported addressing mode for DEC: " + currInstruction.addressingMode);
        }

        if(remainingCycles == 1) {
            zero = (currInstruction.tempLatch == 0);
            negative = (currInstruction.tempLatch & 0x80) != 0;
        }
    }

    private void handleReadModifyWriteInstructions_AbsoluteMode(Function<Integer, Integer> operation) {
        switch (remainingCycles) {
            case 5 -> currInstruction.effectiveAddress = fetch();
            case 4 -> currInstruction.effectiveAddress = (fetch() << 8) | currInstruction.effectiveAddress;
            case 3 -> currInstruction.tempLatch = read(currInstruction.effectiveAddress);
            case 2 -> currInstruction.tempLatch = operation.apply(currInstruction.tempLatch) & 0xFF;
            case 1 -> write(currInstruction.effectiveAddress, currInstruction.tempLatch);

        }
    }
    private void handleReadModifyWriteInstructions_AbsoluteIndexed(Register8Bit register, Function<Integer, Integer> operation) {
        switch (remainingCycles) {
            case 6 -> currInstruction.effectiveAddress = fetch();
            case 5 -> currInstruction.effectiveAddress = (fetch() << 8) | currInstruction.effectiveAddress;
            case 4 -> currInstruction.effectiveAddress = (currInstruction.effectiveAddress + register.getValue()) & 0xFFFF;
            case 3 -> currInstruction.tempLatch = read(currInstruction.effectiveAddress);
            case 2 -> currInstruction.tempLatch = operation.apply(currInstruction.tempLatch) & 0xFF;
            case 1 -> write(currInstruction.effectiveAddress, currInstruction.tempLatch);

        }
    }

    private void handleReadModifyWriteInstructions_ZeroPageMode(Function<Integer, Integer> operation) {
        switch (remainingCycles) {
            case 4 -> currInstruction.effectiveAddress = fetch();
            case 3 -> currInstruction.tempLatch = read(currInstruction.effectiveAddress);
            case 2 -> currInstruction.tempLatch = operation.apply(currInstruction.tempLatch) & 0xFF;
            case 1 -> write(currInstruction.effectiveAddress, currInstruction.tempLatch);
        }
    }

    private void handleReadModifyWriteInstructions_ZeroPageIndexed(Register8Bit register, Function<Integer, Integer> operation) {
        switch (remainingCycles) {
            case 5 -> currInstruction.effectiveAddress = fetch();
            case 4 -> currInstruction.effectiveAddress = (currInstruction.effectiveAddress + register.getValue()) & 0xFF;
            case 3 -> currInstruction.tempLatch = read(currInstruction.effectiveAddress);
            case 2 -> currInstruction.tempLatch = operation.apply(currInstruction.tempLatch) & 0xFF;
            case 1 -> write(currInstruction.effectiveAddress, currInstruction.tempLatch);
        }
    }

    // LOAD INSTRUCTIONS
    private void handleLoad_ZeroPageMode(Register8Bit register) {
        switch (remainingCycles) {
            case 2 -> currInstruction.effectiveAddress = fetch();
            case 1 -> register.setValue(read(currInstruction.effectiveAddress));
        }
    }

    private void handleLoad_AbsoluteMode(Register8Bit register) {
        switch (remainingCycles) {
            case 3 -> currInstruction.effectiveAddress = fetch();
            case 2 -> currInstruction.effectiveAddress = (fetch() << 8) | currInstruction.effectiveAddress;
            case 1 -> register.setValue(read(currInstruction.effectiveAddress));
        }
    }

    private void handleLoad_ZeroPageIndexed(Register8Bit register, Register8Bit indexRegister) {
        switch (remainingCycles) {
            case 3 -> currInstruction.effectiveAddress = fetch();
            case 2 -> currInstruction.effectiveAddress = (currInstruction.effectiveAddress + indexRegister.getValue()) & 0xFF;
            case 1 -> register.setValue(read(currInstruction.effectiveAddress));
        }
    }

    private void handleLoad_AbsoluteIndexed(Register8Bit register, Register8Bit indexRegister) {
        switch (remainingCycles) {
            case 4 -> currInstruction.effectiveAddress = fetch();
            case 3 -> currInstruction.effectiveAddress = (fetch() << 8) | currInstruction.effectiveAddress;
            case 2 -> {
                var address = currInstruction.effectiveAddress;
                currInstruction.effectiveAddress = (address + indexRegister.getValue()) & 0xFFFF;
                handlePageCrossingInLoadInstruction(address, register);
            }
            case 1 -> register.setValue(read(currInstruction.effectiveAddress));
        }
    }

    private void handleLDAIndirectXIndexed() {
        switch (remainingCycles) {
            case 5 -> currInstruction.operand = fetch();
            case 4 -> currInstruction.operand = (currInstruction.operand + x.getValue()) & 0xFF;
            case 3 -> currInstruction.effectiveAddress = read(currInstruction.operand);
            case 2 -> {
                int highByte = read((currInstruction.operand + 1) & 0xFF);
                currInstruction.effectiveAddress = ((highByte << 8) | currInstruction.effectiveAddress) & 0xFFFF;
            }
            case 1 -> a.setValue(read(currInstruction.effectiveAddress));
        }
    }

    private void handleLDAIndirectYIndexed() {
        switch (remainingCycles) {
            case 5 -> currInstruction.operand = fetch();
            case 4 -> currInstruction.effectiveAddress = read(currInstruction.operand & 0xFF) ;
            case 3 -> {
                int highByte = read((currInstruction.operand + 1) & 0xFF);
                currInstruction.effectiveAddress = ((highByte << 8) | currInstruction.effectiveAddress) & 0xFFFF;
            }
            case 2 -> {
                var address = currInstruction.effectiveAddress;
                currInstruction.effectiveAddress = (address + y.getValue()) & 0xFFFF;
                handlePageCrossingInLoadInstruction(address, a);
            }
            case 1 -> a.setValue(read(currInstruction.effectiveAddress));
        }
    }

    // STORE INSTRUCTIONS
    private void handleStore_ZeroPage(Register8Bit register) {
        switch (remainingCycles) {
            case 2 -> currInstruction.effectiveAddress = fetch();
            case 1 -> write(currInstruction.effectiveAddress, register.getValue());
        }
    }

    private void handleStore_ZeroPageIndexed(Register8Bit register, Register8Bit index) {
        switch (remainingCycles) {
            case 3 -> currInstruction.effectiveAddress = fetch();
            case 2 -> currInstruction.effectiveAddress = (currInstruction.effectiveAddress + index.getValue()) & 0xFF;
            case 1 -> bus.write(currInstruction.effectiveAddress, register.getValue());
        }
    }

    private void handleStore_Absolute(Register8Bit register) {
        switch (remainingCycles) {
            case 3 -> currInstruction.effectiveAddress = fetch();
            case 2 -> currInstruction.effectiveAddress = (fetch() << 8) | currInstruction.effectiveAddress;
            case 1 -> bus.write(currInstruction.effectiveAddress, register.getValue());
        }
    }

    private void handleSTAAbsoluteIndexed(Register8Bit index) {
        switch (remainingCycles) {
            case 4 -> currInstruction.effectiveAddress = fetch();
            case 3 -> currInstruction.effectiveAddress = (fetch() << 8) | currInstruction.effectiveAddress;
            case 2 -> currInstruction.effectiveAddress = (currInstruction.effectiveAddress + index.getValue()) & 0xFFFF;
            case 1 -> bus.write(currInstruction.effectiveAddress, a.getValue());
        }
    }

    private void handleSTAIndirectX() {
        switch (remainingCycles) {
            case 5 -> currInstruction.operand = fetch();
            case 4 -> currInstruction.operand = (currInstruction.operand + x.getValue()) & 0xFF;
            case 3 -> currInstruction.effectiveAddress = read(currInstruction.operand);
            case 2 -> {
                int highByte = read((currInstruction.operand + 1) & 0xFF);
                currInstruction.effectiveAddress = ((highByte << 8) | currInstruction.effectiveAddress) & 0xFFFF;
            }
            case 1 -> bus.write(currInstruction.effectiveAddress, a.getValue());
        }
    }

    private void handleSTAIndirectY() {
        switch (remainingCycles) {
            case 5 -> currInstruction.operand = fetch();
            case 4 -> currInstruction.effectiveAddress = read(currInstruction.operand & 0xFF);
            case 3 -> {
                int highByte = read((currInstruction.operand + 1) & 0xFF);
                currInstruction.effectiveAddress = ((highByte << 8) | currInstruction.effectiveAddress) & 0xFFFF;
            }
            case 2 -> currInstruction.effectiveAddress = (currInstruction.effectiveAddress + y.getValue()) & 0xFFFF;
            case 1 -> bus.write(currInstruction.effectiveAddress, a.getValue());
        }
    }


}
