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
//        System.out.printf("reset vector value: 0x%x\n", resetVector);
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
            //DEX opcode:
            case 0xCA -> loadInstructionInitialState(2, Instruction.DEX, AddressingMode.IMP);
            //DEY opcode:
            case 0x88 -> loadInstructionInitialState(2, Instruction.DEY, AddressingMode.IMP);
            //INC opcode:
            case 0xE6 -> loadInstructionInitialState(5, Instruction.INC, AddressingMode.ZPG);
            case 0xF6 -> loadInstructionInitialState(6, Instruction.INC, AddressingMode.ZPG_X);
            case 0xEE -> loadInstructionInitialState(6, Instruction.INC, AddressingMode.ABS);
            case 0xFE -> loadInstructionInitialState(7, Instruction.INC, AddressingMode.ABS_X);
            //INX opcode:
            case 0xE8 -> loadInstructionInitialState(2, Instruction.INX, AddressingMode.IMP);
            //INY opcode:
            case 0xC8 -> loadInstructionInitialState(2, Instruction.INY, AddressingMode.IMP);
            // ORA opcodes:
            case 0x09 -> loadInstructionInitialState(2, Instruction.ORA, AddressingMode.IMM);
            case 0x05 -> loadInstructionInitialState(3, Instruction.ORA, AddressingMode.ZPG);
            case 0x15 -> loadInstructionInitialState(4, Instruction.ORA, AddressingMode.ZPG_X);
            case 0x0D -> loadInstructionInitialState(4, Instruction.ORA, AddressingMode.ABS);
            case 0x1D -> loadInstructionInitialState(5, Instruction.ORA, AddressingMode.ABS_X);
            case 0x19 -> loadInstructionInitialState(5, Instruction.ORA, AddressingMode.ABS_Y);
            case 0x01 -> loadInstructionInitialState(6, Instruction.ORA, AddressingMode.IND_X);
            case 0x11 -> loadInstructionInitialState(6, Instruction.ORA, AddressingMode.IND_Y);
            // EOR opcodes:
            case 0x49 -> loadInstructionInitialState(2, Instruction.EOR, AddressingMode.IMM);
            case 0x45 -> loadInstructionInitialState(3, Instruction.EOR, AddressingMode.ZPG);
            case 0x55 -> loadInstructionInitialState(4, Instruction.EOR, AddressingMode.ZPG_X);
            case 0x4D -> loadInstructionInitialState(4, Instruction.EOR, AddressingMode.ABS);
            case 0x5D -> loadInstructionInitialState(5, Instruction.EOR, AddressingMode.ABS_X);
            case 0x59 -> loadInstructionInitialState(5, Instruction.EOR, AddressingMode.ABS_Y);
            case 0x41 -> loadInstructionInitialState(6, Instruction.EOR, AddressingMode.IND_X);
            case 0x51 -> loadInstructionInitialState(6, Instruction.EOR, AddressingMode.IND_Y);
            // AND opcodes:
            case 0x29 -> loadInstructionInitialState(2, Instruction.AND, AddressingMode.IMM);
            case 0x25 -> loadInstructionInitialState(3, Instruction.AND, AddressingMode.ZPG);
            case 0x35 -> loadInstructionInitialState(4, Instruction.AND, AddressingMode.ZPG_X);
            case 0x2D -> loadInstructionInitialState(4, Instruction.AND, AddressingMode.ABS);
            case 0x3D -> loadInstructionInitialState(5, Instruction.AND, AddressingMode.ABS_X);
            case 0x39 -> loadInstructionInitialState(5, Instruction.AND, AddressingMode.ABS_Y);
            case 0x21 -> loadInstructionInitialState(6, Instruction.AND, AddressingMode.IND_X);
            case 0x31 -> loadInstructionInitialState(6, Instruction.AND, AddressingMode.IND_Y);
            // ASL opcodes:
            case 0x0A -> loadInstructionInitialState(2, Instruction.ASL, AddressingMode.ACC);
            case 0x06 -> loadInstructionInitialState(5, Instruction.ASL, AddressingMode.ZPG);
            case 0x16 -> loadInstructionInitialState(6, Instruction.ASL, AddressingMode.ZPG_X);
            case 0x0E -> loadInstructionInitialState(6, Instruction.ASL, AddressingMode.ABS);
            case 0x1E -> loadInstructionInitialState(7, Instruction.ASL, AddressingMode.ABS_X);
            // LSR opcodes:
            case 0x4A -> loadInstructionInitialState(2, Instruction.LSR, AddressingMode.ACC);
            case 0x46 -> loadInstructionInitialState(5, Instruction.LSR, AddressingMode.ZPG);
            case 0x56 -> loadInstructionInitialState(6, Instruction.LSR, AddressingMode.ZPG_X);
            case 0x4E -> loadInstructionInitialState(6, Instruction.LSR, AddressingMode.ABS);
            case 0x5E -> loadInstructionInitialState(7, Instruction.LSR, AddressingMode.ABS_X);
            // ROL opcodes:
            case 0x2A -> loadInstructionInitialState(2, Instruction.ROL, AddressingMode.ACC);
            case 0x26 -> loadInstructionInitialState(5, Instruction.ROL, AddressingMode.ZPG);
            case 0x36 -> loadInstructionInitialState(6, Instruction.ROL, AddressingMode.ZPG_X);
            case 0x2E -> loadInstructionInitialState(6, Instruction.ROL, AddressingMode.ABS);
            case 0x3E -> loadInstructionInitialState(7, Instruction.ROL, AddressingMode.ABS_X);
            // ROR opcodes:
            case 0x6A -> loadInstructionInitialState(2, Instruction.ROR, AddressingMode.ACC);
            case 0x66 -> loadInstructionInitialState(5, Instruction.ROR, AddressingMode.ZPG);
            case 0x76 -> loadInstructionInitialState(6, Instruction.ROR, AddressingMode.ZPG_X);
            case 0x6E -> loadInstructionInitialState(6, Instruction.ROR, AddressingMode.ABS);
            case 0x7E -> loadInstructionInitialState(7, Instruction.ROR, AddressingMode.ABS_X);
            // Clear Flag instructions:
            case 0x18 -> loadInstructionInitialState(2, Instruction.CLC, AddressingMode.IMP);
            case 0xD8 -> loadInstructionInitialState(2, Instruction.CLD, AddressingMode.IMP);
            case 0x58 -> loadInstructionInitialState(2, Instruction.CLI, AddressingMode.IMP);
            case 0xB8 -> loadInstructionInitialState(2, Instruction.CLV, AddressingMode.IMP);
            // Set Flag instructions:
            case 0x38 -> loadInstructionInitialState(2, Instruction.SEC, AddressingMode.IMP);
            case 0xF8 -> loadInstructionInitialState(2, Instruction.SED, AddressingMode.IMP);
            case 0x78 -> loadInstructionInitialState(2, Instruction.SEI, AddressingMode.IMP);
            // CMP opcodes:
            case 0xC9 -> loadInstructionInitialState(2, Instruction.CMP, AddressingMode.IMM);
            case 0xC5 -> loadInstructionInitialState(3, Instruction.CMP, AddressingMode.ZPG);
            case 0xD5 -> loadInstructionInitialState(4, Instruction.CMP, AddressingMode.ZPG_X);
            case 0xCD -> loadInstructionInitialState(4, Instruction.CMP, AddressingMode.ABS);
            case 0xDD -> loadInstructionInitialState(5, Instruction.CMP, AddressingMode.ABS_X);
            case 0xD9 -> loadInstructionInitialState(5, Instruction.CMP, AddressingMode.ABS_Y);
            case 0xC1 -> loadInstructionInitialState(6, Instruction.CMP, AddressingMode.IND_X);
            case 0xD1 -> loadInstructionInitialState(6, Instruction.CMP, AddressingMode.IND_Y);
            // CPX opcodes:
            case 0xE0 -> loadInstructionInitialState(2, Instruction.CPX, AddressingMode.IMM);
            case 0xE4 -> loadInstructionInitialState(3, Instruction.CPX, AddressingMode.ZPG);
            case 0xEC -> loadInstructionInitialState(4, Instruction.CPX, AddressingMode.ABS);
            // CPY opcodes:
            case 0xC0 -> loadInstructionInitialState(2, Instruction.CPY, AddressingMode.IMM);
            case 0xC4 -> loadInstructionInitialState(3, Instruction.CPY, AddressingMode.ZPG);
            case 0xCC -> loadInstructionInitialState(4, Instruction.CPY, AddressingMode.ABS);
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
            case DEX -> DEX();
            case DEY -> DEY();
            case INC -> INC();
            case INX -> INX();
            case INY -> INY();
            case ORA -> ORA();
            case EOR -> EOR();
            case AND -> AND();
            case ASL -> ASL();
            case LSR -> LSR();
            case ROL -> ROL();
            case ROR -> ROR();
            case CLC -> CLC();
            case CLD -> CLD();
            case CLI -> CLI();
            case CLV -> CLV();
            case SEC -> SEC();
            case SED -> SED();
            case SEI -> SEI();
            case CMP -> CMP();
            case CPX -> CPX();
            case CPY -> CPY();
            default -> throw new RuntimeException("Unimplemented instruction: " + currInstruction.instruction);
        }
    }

    private void loadInstructionInitialState(
            int cycles, Instruction instruction, AddressingMode addressingMode
    ) {
        this.remainingCycles = cycles;
        this.currInstruction.instruction = instruction;
        this.currInstruction.addressingMode = addressingMode;
    }

    private void handlePageCrossingInLoadInstruction(int address, Register8Bit register, Function<Integer, Integer> operation) {
        if((address & 0xFF00) == (currInstruction.effectiveAddress & 0xFF00)) {

            int read = read(currInstruction.effectiveAddress);
            register.setValue(operation.apply(read));
            //avoid case 1 if no extra cycle is needed. This variable is already
            //decremented outside of this function, so if we decrement it here, we
            //make sure it will not reach case 1.
            remainingCycles--;
        }
    }

    private void LDA() {
        switch (currInstruction.addressingMode) {
            case IMM -> a.setValue(fetch());
            case ZPG -> handleRead_ZeroPageMode(a, Function.identity());
            case ZPG_X -> handleRead_ZeroPageIndexed(a, x, Function.identity());
            case ABS -> handleRead_AbsoluteMode(a, Function.identity());
            case ABS_X -> handleRead_AbsoluteIndexed(a, x, Function.identity());
            case ABS_Y -> handleRead_AbsoluteIndexed(a, y, Function.identity());
            case IND_X -> handleRead_IndirectXIndexed(a, Function.identity());
            case IND_Y -> handleRead_IndirectYIndexed(a, Function.identity());
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
            case ZPG -> handleRead_ZeroPageMode(x, Function.identity());
            case ZPG_Y -> handleRead_ZeroPageIndexed(x, y, Function.identity());
            case ABS -> handleRead_AbsoluteMode(x, Function.identity());
            case ABS_Y -> handleRead_AbsoluteIndexed(x, y, Function.identity());
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
            case ZPG -> handleRead_ZeroPageMode(y, Function.identity());
            case ZPG_X -> handleRead_ZeroPageIndexed(y, x, Function.identity());
            case ABS -> handleRead_AbsoluteMode(y, Function.identity());
            case ABS_X -> handleRead_AbsoluteIndexed(y, x, Function.identity());
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

    private void DEX() {
        assert(remainingCycles == 1);
        x.setValue((x.getValue() - 1) & 0xFF);
        zero = (x.getValue() == 0);
        negative = (x.getValue() & 0x80) != 0;
    }

    private void DEY() {
        assert(remainingCycles == 1);
        y.setValue((y.getValue() - 1) & 0xFF);
        zero = (y.getValue() == 0);
        negative = (y.getValue() & 0x80) != 0;
    }

    private void INC() {
        Function<Integer, Integer> decrementOperation = value -> value + 1;
        switch (currInstruction.addressingMode) {
            case ZPG -> handleReadModifyWriteInstructions_ZeroPageMode(decrementOperation);
            case ZPG_X -> handleReadModifyWriteInstructions_ZeroPageIndexed(x, decrementOperation);
            case ABS -> handleReadModifyWriteInstructions_AbsoluteMode(decrementOperation);
            case ABS_X -> handleReadModifyWriteInstructions_AbsoluteIndexed(x, decrementOperation);
            default -> throw new RuntimeException("Unsupported addressing mode for INC: " + currInstruction.addressingMode);
        }

        if(remainingCycles == 1) {
            zero = (currInstruction.tempLatch == 0);
            negative = (currInstruction.tempLatch & 0x80) != 0;
        }
    }

    private void INX() {
        assert(remainingCycles == 1);
        x.setValue((x.getValue() + 1) & 0xFF);
        zero = (x.getValue() == 0);
        negative = (x.getValue() & 0x80) != 0;
    }

    private void INY() {
        assert(remainingCycles == 1);
        y.setValue((y.getValue() + 1) & 0xFF);
        zero = (y.getValue() == 0);
        negative = (y.getValue() & 0x80) != 0;
    }

    private void ORA() {
        switch (currInstruction.addressingMode) {
            case IMM -> a.setValue(a.getValue() | fetch());
            case ZPG -> handleRead_ZeroPageMode(a, operand -> a.getValue() | operand);
            case ZPG_X -> handleRead_ZeroPageIndexed(a, x, operand -> a.getValue() | operand);
            case ABS -> handleRead_AbsoluteMode(a, operand -> a.getValue() | operand);
            case ABS_X -> handleRead_AbsoluteIndexed(a, x, operand -> a.getValue() | operand);
            case ABS_Y -> handleRead_AbsoluteIndexed(a, y, operand -> a.getValue() | operand);
            case IND_X -> handleRead_IndirectXIndexed(a, operand -> a.getValue() | operand);
            case IND_Y -> handleRead_IndirectYIndexed(a, operand -> a.getValue() | operand);
            default -> throw new RuntimeException("Unsupported addressing mode for ORA: " + currInstruction.addressingMode);
        }
        if (remainingCycles == 1) {
            zero = (a.getValue() == 0);
            negative = (a.getValue() & 0x80) != 0;
        }
    }

    private void EOR() {
        switch (currInstruction.addressingMode) {
            case IMM -> a.setValue(a.getValue() ^ fetch());
            case ZPG -> handleRead_ZeroPageMode(a, operand -> a.getValue() ^ operand);
            case ZPG_X -> handleRead_ZeroPageIndexed(a, x, operand -> a.getValue() ^ operand);
            case ABS -> handleRead_AbsoluteMode(a, operand -> a.getValue() ^ operand);
            case ABS_X -> handleRead_AbsoluteIndexed(a, x, operand -> a.getValue() ^ operand);
            case ABS_Y -> handleRead_AbsoluteIndexed(a, y, operand -> a.getValue() ^ operand);
            case IND_X -> handleRead_IndirectXIndexed(a, operand -> a.getValue() ^ operand);
            case IND_Y -> handleRead_IndirectYIndexed(a, operand -> a.getValue() ^ operand);
            default -> throw new RuntimeException("Unsupported addressing mode for EOR: " + currInstruction.addressingMode);
        }
        if (remainingCycles == 1) {
            zero = (a.getValue() == 0);
            negative = (a.getValue() & 0x80) != 0;
        }
    }

    private void AND() {
        switch (currInstruction.addressingMode) {
            case IMM -> a.setValue(a.getValue() & fetch());
            case ZPG -> handleRead_ZeroPageMode(a, operand -> a.getValue() & operand);
            case ZPG_X -> handleRead_ZeroPageIndexed(a, x, operand -> a.getValue() & operand);
            case ABS -> handleRead_AbsoluteMode(a, operand -> a.getValue() & operand);
            case ABS_X -> handleRead_AbsoluteIndexed(a, x, operand -> a.getValue() & operand);
            case ABS_Y -> handleRead_AbsoluteIndexed(a, y, operand -> a.getValue() & operand);
            case IND_X -> handleRead_IndirectXIndexed(a, operand -> a.getValue() & operand);
            case IND_Y -> handleRead_IndirectYIndexed(a, operand -> a.getValue() & operand);
            default -> throw new RuntimeException("Unsupported addressing mode for AND: " + currInstruction.addressingMode);
        }
        if (remainingCycles == 1) {
            zero = (a.getValue() == 0);
            negative = (a.getValue() & 0x80) != 0;
        }
    }

    private void ASL() {
        Function<Integer, Integer> shiftOperation = value -> {
            carry = (value & 0x80) != 0;
            return (value << 1) & 0xFF;
        };
        switch (currInstruction.addressingMode) {
            case ACC -> {
                currInstruction.tempLatch = shiftOperation.apply(a.getValue());
                a.setValue(currInstruction.tempLatch);
            }
            case ZPG -> handleReadModifyWriteInstructions_ZeroPageMode(shiftOperation);
            case ZPG_X -> handleReadModifyWriteInstructions_ZeroPageIndexed(x, shiftOperation);
            case ABS -> handleReadModifyWriteInstructions_AbsoluteMode(shiftOperation);
            case ABS_X -> handleReadModifyWriteInstructions_AbsoluteIndexed(x, shiftOperation);
            default -> throw new RuntimeException("Unsupported addressing mode for ASL: " + currInstruction.addressingMode);
        }
        if (remainingCycles == 1) {
            int result = currInstruction.tempLatch;
            zero = (result == 0);
            negative = (result & 0x80) != 0;
            //carry flag is being updated in the shiftOperation Function.
        }
    }

    private void LSR() {
        Function<Integer, Integer> shiftOperation = value -> {
            carry = (value & 0x01) != 0;
            return (value >>> 1) & 0xFF;
        };
        switch (currInstruction.addressingMode) {
            case ACC -> {
                currInstruction.tempLatch = shiftOperation.apply(a.getValue());
                a.setValue(currInstruction.tempLatch);
            }
            case ZPG -> handleReadModifyWriteInstructions_ZeroPageMode(shiftOperation);
            case ZPG_X -> handleReadModifyWriteInstructions_ZeroPageIndexed(x, shiftOperation);
            case ABS -> handleReadModifyWriteInstructions_AbsoluteMode(shiftOperation);
            case ABS_X -> handleReadModifyWriteInstructions_AbsoluteIndexed(x, shiftOperation);
            default -> throw new RuntimeException("Unsupported addressing mode for LSR: " + currInstruction.addressingMode);
        }
        if (remainingCycles == 1) {
            int result = currInstruction.tempLatch;
            zero = (result == 0);
            negative = false;
        }
    }

    private void ROL() {
        Function<Integer, Integer> rotateOperation = value -> {
            int result = ((value << 1) | (carry ? 1 : 0)) & 0xFF;
            carry = (value & 0x80) != 0;
            return result;
        };
        switch (currInstruction.addressingMode) {
            case ACC -> {
                currInstruction.tempLatch = rotateOperation.apply(a.getValue());
                a.setValue(currInstruction.tempLatch);
            }
            case ZPG -> handleReadModifyWriteInstructions_ZeroPageMode(rotateOperation);
            case ZPG_X -> handleReadModifyWriteInstructions_ZeroPageIndexed(x, rotateOperation);
            case ABS -> handleReadModifyWriteInstructions_AbsoluteMode(rotateOperation);
            case ABS_X -> handleReadModifyWriteInstructions_AbsoluteIndexed(x, rotateOperation);
            default -> throw new RuntimeException("Unsupported addressing mode for ROL: " + currInstruction.addressingMode);
        }
        if (remainingCycles == 1) {
            int result = currInstruction.tempLatch;
            zero = (result == 0);
            negative = (result & 0x80) != 0;
        }
    }

    private void ROR() {
        Function<Integer, Integer> rotateOperation = value -> {
            int result = ((carry ? 0x80 : 0) | (value >>> 1)) & 0xFF;
            carry = (value & 0x01) != 0;
            return result;
        };
        switch (currInstruction.addressingMode) {
            case ACC -> {
                currInstruction.tempLatch = rotateOperation.apply(a.getValue());
                a.setValue(currInstruction.tempLatch);
            }
            case ZPG -> handleReadModifyWriteInstructions_ZeroPageMode(rotateOperation);
            case ZPG_X -> handleReadModifyWriteInstructions_ZeroPageIndexed(x, rotateOperation);
            case ABS -> handleReadModifyWriteInstructions_AbsoluteMode(rotateOperation);
            case ABS_X -> handleReadModifyWriteInstructions_AbsoluteIndexed(x, rotateOperation);
            default -> throw new RuntimeException("Unsupported addressing mode for ROR: " + currInstruction.addressingMode);
        }
        if (remainingCycles == 1) {
            int result = currInstruction.tempLatch;
            zero = (result == 0);
            negative = (result & 0x80) != 0;
        }
    }

    private void CLC() {
        carry = false;
    }

    private void CLD() {
        decimal = false;
    }

    private void CLI() {
        interruptDisable = false;
    }

    private void CLV() {
        overflow = false;
    }

    private void SEC() {
        carry = true;
    }

    private void SED() {
        decimal = true;
    }

    private void SEI() {
        interruptDisable = true;
    }

    private Function<Integer, Integer> getCmpFunction(Register8Bit register) {
        return operand -> {
            int regValue = register.getValue() & 0xFF;
            int diff = regValue - operand;
            carry = regValue >= operand;
            zero = (regValue == operand);
            negative = ((diff & 0x80) != 0);
            return regValue;
        };
    }

    private void CMP() {
        Function<Integer, Integer> cmpOperation = getCmpFunction(a);
        switch (currInstruction.addressingMode) {
            case IMM -> a.setValue(cmpOperation.apply(fetch()));
            case ZPG -> handleRead_ZeroPageMode(a, cmpOperation);
            case ZPG_X -> handleRead_ZeroPageIndexed(a, x, cmpOperation);
            case ABS -> handleRead_AbsoluteMode(a, cmpOperation);
            case ABS_X -> handleRead_AbsoluteIndexed(a, x, cmpOperation);
            case ABS_Y -> handleRead_AbsoluteIndexed(a, y, cmpOperation);
            case IND_X -> handleRead_IndirectXIndexed(a, cmpOperation);
            case IND_Y -> handleRead_IndirectYIndexed(a, cmpOperation);
            default -> throw new RuntimeException("Unsupported addressing mode for CMP: " + currInstruction.addressingMode);
        }
    }

    private void CPX() {
        Function<Integer, Integer> cpxOperation = getCmpFunction(x);
        switch (currInstruction.addressingMode) {
            case IMM -> x.setValue(cpxOperation.apply(fetch()));
            case ZPG -> handleRead_ZeroPageMode(x, cpxOperation);
            case ABS -> handleRead_AbsoluteMode(x, cpxOperation);
            default -> throw new RuntimeException("Unsupported addressing mode for CPX: " + currInstruction.addressingMode);
        }
    }

    private void CPY() {
        Function<Integer, Integer> cpyOperation = getCmpFunction(y);
        switch (currInstruction.addressingMode) {
            case IMM -> y.setValue(cpyOperation.apply(fetch()));
            case ZPG -> handleRead_ZeroPageMode(y, cpyOperation);
            case ABS -> handleRead_AbsoluteMode(y, cpyOperation);
            default -> throw new RuntimeException("Unsupported addressing mode for CPY: " + currInstruction.addressingMode);
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

    private void handleRead_ZeroPageMode(Register8Bit register, Function<Integer,Integer> operation) {
        switch (remainingCycles) {
            case 2 -> currInstruction.effectiveAddress = fetch();
            case 1 -> {
                int read = read(currInstruction.effectiveAddress);
                register.setValue(operation.apply(read));
            }
        }
    }

    private void handleRead_AbsoluteMode(Register8Bit register, Function<Integer,Integer> operation) {
        switch (remainingCycles) {
            case 3 -> currInstruction.effectiveAddress = fetch();
            case 2 -> currInstruction.effectiveAddress = (fetch() << 8) | currInstruction.effectiveAddress;
            case 1 -> {
                int read = read(currInstruction.effectiveAddress);
                register.setValue(operation.apply(read));
            }
        }
    }

    private void handleRead_ZeroPageIndexed(Register8Bit register, Register8Bit indexRegister, Function<Integer,Integer> operation) {
        switch (remainingCycles) {
            case 3 -> currInstruction.effectiveAddress = fetch();
            case 2 -> currInstruction.effectiveAddress = (currInstruction.effectiveAddress + indexRegister.getValue()) & 0xFF;
            case 1 -> {
                int read = read(currInstruction.effectiveAddress);
                register.setValue(operation.apply(read));
            }
        }
    }

    private void handleRead_AbsoluteIndexed(Register8Bit register, Register8Bit indexRegister, Function<Integer, Integer> operation) {
        switch (remainingCycles) {
            case 4 -> currInstruction.effectiveAddress = fetch();
            case 3 -> currInstruction.effectiveAddress = (fetch() << 8) | currInstruction.effectiveAddress;
            case 2 -> {
                var address = currInstruction.effectiveAddress;
                currInstruction.effectiveAddress = (address + indexRegister.getValue()) & 0xFFFF;
                handlePageCrossingInLoadInstruction(address, register, operation);
            }
            case 1 -> {
                int read = read(currInstruction.effectiveAddress);
                register.setValue(operation.apply(read));
            }
        }
    }

    private void handleRead_IndirectXIndexed(Register8Bit register, Function<Integer, Integer> operation) {
        switch (remainingCycles) {
            case 5 -> currInstruction.operand = fetch();
            case 4 -> currInstruction.operand = (currInstruction.operand + x.getValue()) & 0xFF;
            case 3 -> currInstruction.effectiveAddress = read(currInstruction.operand);
            case 2 -> {
                int highByte = read((currInstruction.operand + 1) & 0xFF);
                currInstruction.effectiveAddress = ((highByte << 8) | currInstruction.effectiveAddress) & 0xFFFF;
            }
            case 1 -> {
                int read = read(currInstruction.effectiveAddress);
                register.setValue(operation.apply(read));
            }
        }
    }

    private void handleRead_IndirectYIndexed(Register8Bit register, Function<Integer, Integer> operation) {
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
                handlePageCrossingInLoadInstruction(address, register, operation);
            }
            case 1 -> {
                int read = read(currInstruction.effectiveAddress);
                register.setValue(operation.apply(read));
            }
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
