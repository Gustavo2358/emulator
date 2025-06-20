package core;

import core.apu.APU; // Updated import
import java.util.Objects;
import java.util.function.Function;

public class CPU {

    private int pc;
    private final EightBitRegister sp;
    private final EightBitRegister a, x, y;
    private boolean carry;
    private boolean zero;
    private boolean interruptDisable;
    private boolean decimal;
    private boolean overflow;
    private boolean negative;

    private final Bus bus;
    private final APU apu; // Added direct reference to APU

    private int remainingCycles;

    private boolean nmiPending = false;
    private boolean processingNMI = false;
    private int dmaStallCycles = 0; // Added for OAMDMA
    private boolean irqLineAsserted = false; // CPU's internal IRQ line state
    private boolean processingIRQ = false; // Flag to indicate CPU is in IRQ sequence

    public CPU(Bus bus) {
        this.bus = bus;
        if (bus instanceof CPUBus) { // Get APU from CPUBus
            this.apu = ((CPUBus) bus).getAPU();
        } else {
            // This case should ideally not happen if CPUBus is always used.
            // Or, throw an IllegalArgumentException if APU is essential.
            System.err.println("Warning: CPUBus not used, APU functionality might be missing.");
            this.apu = null; // Or a NullAPU object
        }

        pc = 0x00;
        sp = new EightBitRegister(0xFD);
        a = new EightBitRegister(0x00);
        x = new EightBitRegister(0x00);
        y = new EightBitRegister(0x00);
        //TODO find the correct initial values for the status flags
        carry = false;
        zero = false;
        interruptDisable = true;
        decimal = false;
        overflow = false;
        negative = false;

        remainingCycles = 0;
    }

    public Bus getBus() { // Added getter for the bus
        return bus;
    }

    public void stallForDMA(int cycles) { // Added for OAMDMA
        this.dmaStallCycles += cycles;
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

    public void triggerNMI() {
        nmiPending = true;
    }

    /**
     * Asserts the CPU's IRQ line. The IRQ will only be processed if CPU interrupts are not disabled (I flag is clear).
     */
    public void assertIRQLine() {
        this.irqLineAsserted = true;
    }

    /**
     * De-asserts the CPU's IRQ line. This is typically done by the interrupting device once the condition is cleared.
     */
    public void deassertIRQLine() {
        this.irqLineAsserted = false;
    }

    public void runCycle() {
        // Clock the APU at the beginning of each CPU cycle
        if (apu != null) {
            apu.clock();
            // After APU clock, check for APU triggered IRQs and assert/de-assert CPU's IRQ line
            boolean apuWantsIRQ = apu.isDmcIrqAsserted() || apu.isFrameIrqAsserted();
            if (apuWantsIRQ) {
                assertIRQLine();
            } else {
                // If the APU is the only source controlling the IRQ line via this mechanism,
                // or if no other source is currently asserting IRQ.
                deassertIRQLine(); // Sets this.irqLineAsserted = false;
            }
        }

        if (dmaStallCycles > 0) {
            dmaStallCycles--;
            // PPU clocking should continue during DMA stall
            // Assuming PPU clocking is handled elsewhere or also needs to be added here if not.
            return;
        }

        if (remainingCycles == 0) {
            // Interrupt polling happens before fetching the next instruction.
            if (nmiPending && !processingNMI) {
                processingNMI = true; // Start NMI sequence
                remainingCycles = 7;  // NMI takes 7 cycles
                // NMI processing will begin in the next block
            } else if (irqLineAsserted && !interruptDisable && !processingNMI && !processingIRQ) {
                // Only process IRQ if I flag is clear, and not already in NMI or another IRQ sequence.
                processingIRQ = true; // Start IRQ sequence
                remainingCycles = 7;  // IRQ also takes 7 cycles
                // IRQ processing will begin in the next block
            }
        }

        if (processingNMI) {
            handleNMI();
        } else if (processingIRQ) {
            handleIRQ();
        } else if (isOpCode()) { // No active interrupt sequence, proceed with instruction
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
            // Branch instructions opcode:
            case 0x90 -> loadInstructionInitialState(4, Instruction.BCC, AddressingMode.REL);
            case 0xB0 -> loadInstructionInitialState(4, Instruction.BCS, AddressingMode.REL);
            case 0xF0 -> loadInstructionInitialState(4, Instruction.BEQ, AddressingMode.REL);
            case 0x30 -> loadInstructionInitialState(4, Instruction.BMI, AddressingMode.REL);
            case 0xD0 -> loadInstructionInitialState(4, Instruction.BNE, AddressingMode.REL);
            case 0x10 -> loadInstructionInitialState(4, Instruction.BPL, AddressingMode.REL);
            case 0x50 -> loadInstructionInitialState(4, Instruction.BVC, AddressingMode.REL);
            case 0x70 -> loadInstructionInitialState(4, Instruction.BVS, AddressingMode.REL);
            //JMP opcodes
            case 0x4C -> loadInstructionInitialState(3, Instruction.JMP, AddressingMode.ABS);
            case 0x6C -> loadInstructionInitialState(5, Instruction.JMP, AddressingMode.IND);
            //JSR opcode
            case 0x20 -> loadInstructionInitialState(6, Instruction.JSR, AddressingMode.ABS);
            //RTS opcode:
            case 0x60 -> loadInstructionInitialState(6, Instruction.RTS, AddressingMode.IMP);
            //BRK opcode:
            case 0x00 -> loadInstructionInitialState(7,Instruction.BRK, AddressingMode.IMP);
            //RTI opcode:
            case 0x40 -> loadInstructionInitialState(6, Instruction.RTI, AddressingMode.IMP);
            //NOP opcode:
            case 0xEA -> loadInstructionInitialState(2, Instruction.NOP, AddressingMode.IMP);
            //ADC opcodes
            case 0x69 -> loadInstructionInitialState(2, Instruction.ADC, AddressingMode.IMM);
            case 0x65 -> loadInstructionInitialState(3, Instruction.ADC, AddressingMode.ZPG);
            case 0x75 -> loadInstructionInitialState(4, Instruction.ADC, AddressingMode.ZPG_X);
            case 0x6D -> loadInstructionInitialState(4, Instruction.ADC, AddressingMode.ABS);
            case 0x7D -> loadInstructionInitialState(5, Instruction.ADC, AddressingMode.ABS_X);
            case 0x79 -> loadInstructionInitialState(5, Instruction.ADC, AddressingMode.ABS_Y);
            case 0x61 -> loadInstructionInitialState(6, Instruction.ADC, AddressingMode.IND_X);
            case 0x71 -> loadInstructionInitialState(6, Instruction.ADC, AddressingMode.IND_Y);
            // SBC opcodes
            case 0xE9 -> loadInstructionInitialState(2, Instruction.SBC, AddressingMode.IMM);
            case 0xE5 -> loadInstructionInitialState(3, Instruction.SBC, AddressingMode.ZPG);
            case 0xF5 -> loadInstructionInitialState(4, Instruction.SBC, AddressingMode.ZPG_X);
            case 0xED -> loadInstructionInitialState(4, Instruction.SBC, AddressingMode.ABS);
            case 0xFD -> loadInstructionInitialState(5, Instruction.SBC, AddressingMode.ABS_X);
            case 0xF9 -> loadInstructionInitialState(5, Instruction.SBC, AddressingMode.ABS_Y);
            case 0xE1 -> loadInstructionInitialState(6, Instruction.SBC, AddressingMode.IND_X);
            case 0xF1 -> loadInstructionInitialState(6, Instruction.SBC, AddressingMode.IND_Y);
            // BIT opcodes
            case 0x24 -> loadInstructionInitialState(3, Instruction.BIT, AddressingMode.ZPG);
            case 0x2C -> loadInstructionInitialState(4, Instruction.BIT, AddressingMode.ABS);
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
            case BCC -> BCC();
            case BCS -> BCS();
            case BEQ -> BEQ();
            case BMI -> BMI();
            case BNE -> BNE();
            case BPL -> BPL();
            case BVC -> BVC();
            case BVS -> BVS();
            case JMP -> JMP();
            case RTS -> RTS();
            case JSR -> JSR();
            case BRK -> BRK();
            case RTI -> RTI();
            case NOP -> NOP();
            case ADC -> ADC();
            case SBC -> SBC();
            case BIT -> BIT();
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

    private void handlePageCrossingInLoadInstruction(int address, EightBitRegister register, Function<Integer, Integer> operation) {
        if((address & 0xFF00) == (currInstruction.effectiveAddress & 0xFF00)) {

            int read = read(currInstruction.effectiveAddress);
            register.setValue(operation.apply(read));
            //avoid case 1 if no extra cycle is needed. This variable is already
            //decremented outside of this function, so if we decrement it here, we
            //make sure it will not reach case 1.
            remainingCycles--;
        }
    }

    private void handleNMI() {
        switch (remainingCycles) {
            case 7 -> {
                // Dummy cycle; you might perform a dummy read here if desired.
            }
            case 6 -> {
                // Push the high byte of the PC onto the stack.
                write(0x0100 | sp.getValue(), (pc >> 8) & 0xFF);
                sp.decrement();
            }
            case 5 -> {
                // Push the low byte of the PC onto the stack.
                write(0x0100 | sp.getValue(), pc & 0xFF);
                sp.decrement();
            }
            case 4 -> {
                // Push the processor status onto the stack.
                // For NMI, use flagsToBits(false) so that the B flag is not set.
                write(0x0100 | sp.getValue(), flagsToBits(false));
                sp.decrement();
            }
            case 3 -> {
                // Read the low byte of the NMI vector from 0xFFFA.
                currInstruction.effectiveAddress = read(0xFFFA) & 0xFF;
            }
            case 2 -> {
                // Read the high byte of the NMI vector from 0xFFFB and update PC.
                int pch = read(0xFFFB) & 0xFF;
                pc = (pch << 8) | currInstruction.effectiveAddress;
            }
            case 1 -> {
                // Final cycle: finish the NMI sequence.
                processingNMI = false;
                nmiPending = false;
                interruptDisable = true; // NMI sets the I flag
            }
        }
    }

    private void handleIRQ() {
        // Standard IRQ sequence (7 cycles)
        // Cycle 1: (Internal operation / Fetch next opcode, but it's ignored)
        // Cycle 2: Push PCH
        // Cycle 3: Push PCL
        // Cycle 4: Push P (Status register with B flag = 0, bit 5 = 1)
        // Cycle 5: Fetch low byte of IRQ vector ($FFFE)
        // Cycle 6: Fetch high byte of IRQ vector ($FFFF), set PC
        // Cycle 7: Set I flag, clear processingIRQ

        switch (7 - remainingCycles + 1) { // Current cycle of the IRQ sequence (1 to 7)
            case 1: // Cycle 1: Internal operations, fetch of next instruction opcode (ignored)
                // read(pc); // Simulate fetch, though it's discarded
                break;
            case 2: // Cycle 2: Push PCH to stack
                write(0x0100 | sp.getValue(), (pc >> 8) & 0xFF);
                sp.decrement();
                break;
            case 3: // Cycle 3: Push PCL to stack
                write(0x0100 | sp.getValue(), pc & 0xFF);
                sp.decrement();
                break;
            case 4: // Cycle 4: Push Status Register to stack (B flag is 0 for IRQ)
                write(0x0100 | sp.getValue(), flagsToBits(false)); // B flag (bit 4) is 0
                sp.decrement();
                break;
            case 5: // Cycle 5: Fetch low byte of IRQ vector from $FFFE
                currInstruction.effectiveAddress = read(0xFFFE);
                break;
            case 6: // Cycle 6: Fetch high byte of IRQ vector from $FFFF, update PC
                int pch = read(0xFFFF);
                pc = (pch << 8) | currInstruction.effectiveAddress;
                break;
            case 7: // Cycle 7: Finalize IRQ sequence
                interruptDisable = true; // Set I flag
                processingIRQ = false;   // Clear processing IRQ flag
                // The irqLineAsserted flag should be cleared by the device or by the CPU acknowledging the source.
                // For APU, reading $4015 clears frame IRQ. DMC IRQ clears on $4010 write or $4015 DMC disable.
                // For now, we assume the game/APU logic will handle de-assertion of the source.
                // If irqLineAsserted is not cleared by an external source, it might re-trigger if I flag is cleared.
                break;
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
            zero = (y.getValue() == 0);
            negative = (y.getValue() & 0x80) != 0;
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
            case 1 -> sp.decrement();
        }
    }

    // Converts the current core.CPU flags into an 8-bit representation.
    // Bit layout: N V 1 B D I Z C
    private int flagsToBits(boolean bflag) {
        int flags = 0;
        if (negative) flags |= 0x80;
        if (overflow) flags |= 0x40;
        flags |= 0x20;
        if (bflag) flags |= 0x10;
        if (decimal) flags |= 0x08;
        if (interruptDisable) flags |= 0x04;
        if (zero) flags |= 0x02;
        if (carry) flags |= 0x01;
        return flags;
    }

    private void bitsToFlags(int status) {
        // Ignore the Break flag (bit 4) and unused bit (bit 5)
        status = status & ~(0x10 | 0x20);

        negative = (status & 0x80) != 0;
        overflow = (status & 0x40) != 0;
        decimal = (status & 0x08) != 0;
        interruptDisable = (status & 0x04) != 0;
        zero = (status & 0x02) != 0;
        carry = (status & 0x01) != 0;
    }

    private void PHP() {
        switch (remainingCycles) {
            case 2 -> write(0x0100 | sp.getValue(), flagsToBits(true));
            case 1 -> sp.decrement();
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

    private Function<Integer, Integer> getCmpFunction(EightBitRegister register) {
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

    private void BCC() {
        handleRelativeInstructions(carry);
    }

    private void BCS() {
        handleRelativeInstructions(!carry);
    }

    private void BEQ() {
        handleRelativeInstructions(!zero);
    }

    private void BMI() {
        handleRelativeInstructions(!negative);
    }

    private void BNE() {
        handleRelativeInstructions(zero);
    }

    private void BPL() {
        handleRelativeInstructions(negative);
    }

    private void BVC() {
        handleRelativeInstructions(overflow);
    }

    private void BVS() {
        handleRelativeInstructions(!overflow);
    }

    private void JMP() {
        switch (currInstruction.addressingMode){
            case ABS -> {
                switch (remainingCycles){
                    case 2 -> currInstruction.effectiveAddress = fetch();
                    case 1 -> {
                        currInstruction.effectiveAddress = (fetch() << 8) | currInstruction.effectiveAddress;
                        pc = currInstruction.effectiveAddress;
                    }
                }
            }
            case IND -> {
                switch (remainingCycles) {
                    case 4 -> currInstruction.operand = fetch();
                    case 3 -> currInstruction.operand = (fetch() << 8) | currInstruction.operand;
                    case 2 -> currInstruction.effectiveAddress = read(currInstruction.operand) & 0xFF;
                    case 1 -> {
                        int pointerHighAddress = ((currInstruction.operand & 0xFF) == 0xFF)
                                ? (currInstruction.operand & 0xFF00)
                                : (currInstruction.operand + 1);
                        currInstruction.effectiveAddress = (read(pointerHighAddress) & 0xFF) << 8 | currInstruction.effectiveAddress;
                        pc = currInstruction.effectiveAddress;
                    }
                }
            }
        }
    }

    private void RTS() {
        switch (remainingCycles) {
            case 5 -> { /*Dummy read*/ }
            case 4 -> sp.increment();
            case 3 -> {
                pc = read(0x100 | sp.getValue()) & 0xFF;
                sp.increment();
            }
            case 2 -> {
                int pch = read(0x100 | sp.getValue()) & 0xFF;
                pc = (pch << 8) & 0xFF00 | pc;
            }
            case 1 -> pc = (pc + 1) % 0x10000;
        }
    }

    private void JSR() {
        switch (remainingCycles) {
            case 5 -> currInstruction.effectiveAddress = fetch();
            case 4 -> currInstruction.effectiveAddress = (fetch() << 8 | currInstruction.effectiveAddress) & 0xFFFF;
            case 3 -> {
                --pc;
                write(0x100 | sp.getValue(),(pc >> 8) & 0xFF);
                sp.decrement();
            }
            case 2 -> {
                write(0x100 | sp.getValue(),pc & 0xFF);
                sp.decrement();
            }
            case 1 -> pc = currInstruction.effectiveAddress;
        }
    }

    private void BRK() {
        switch (remainingCycles) {
            case 6 -> fetch();
            case 5 -> {
                write(0x0100 | sp.getValue(), (pc >> 8) & 0xFF);
                sp.decrement();
            }
            case 4 -> {
                write(0x0100 | sp.getValue(), pc & 0xFF);
                sp.decrement();
            }
            case 3 -> {
                write(0x0100 | sp.getValue(), flagsToBits(true));
                sp.decrement();
            }
            case 2 -> {
                currInstruction.effectiveAddress = read(0xFFFE) & 0xFF;
            }
            case 1 -> {
                int pch = read(0xFFFF) & 0xFF;
                pc = (pch << 8) | currInstruction.effectiveAddress;
                interruptDisable = true;
            }
        }
    }

    private void RTI() {
        switch (remainingCycles) {
            case 5 -> fetch();
            case 4 ->  sp.increment();
            case 3 -> {
                int pulledStatus = read(0x0100 | sp.getValue()) & 0xFF;
                sp.increment();
                bitsToFlags(pulledStatus);
            }
            case 2 -> {
                int pcl = read(0x0100 | sp.getValue()) & 0xFF;
                sp.increment();
                currInstruction.effectiveAddress = pcl;
            }
            case 1 -> {
                int pch = read(0x0100 | sp.getValue()) & 0xFF;
                pc = (pch << 8) | currInstruction.effectiveAddress;
            }
        }
    }

    private void NOP() {
        switch (remainingCycles) {
            case 1 -> {
                // NOP
            }
        }
    }

    private void ADC() {
        Function<Integer, Integer> op = (fetched) -> {
            currInstruction.operand = fetched;
            currInstruction.tempLatch = a.getValue() + fetched + (carry ? 1 : 0);
            setADCFlags();
            return currInstruction.tempLatch;
        };
        switch (currInstruction.addressingMode) {
            case IMM -> {
                int fetched = fetch();
                a.setValue(op.apply(fetched));
            }
            case ZPG -> handleRead_ZeroPageMode(a, op);
            case ZPG_X -> handleRead_ZeroPageIndexed(a, x, op);
            case ABS -> handleRead_AbsoluteMode(a, op);
            case ABS_X -> handleRead_AbsoluteIndexed(a, x, op);
            case ABS_Y -> handleRead_AbsoluteIndexed(a, y, op);
            case IND_X -> handleRead_IndirectXIndexed(a, op);
            case IND_Y -> handleRead_IndirectYIndexed(a, op);
        }
    }

    private void setADCFlags() {
        carry = currInstruction.tempLatch > 0xFF;
        zero = (currInstruction.tempLatch & 0xFF) == 0;
        negative = (currInstruction.tempLatch & 0x80) != 0;
        overflow = (((~a.getValue() ^ currInstruction.operand) & (a.getValue() ^ currInstruction.tempLatch)) & 0x80) != 0;
    }

    private void SBC() {
        // Define an operation lambda that computes the subtraction result
        // For SBC: A = A - fetched - (carry ? 0 : 1)
        Function<Integer, Integer> op = (fetched) -> {
            currInstruction.operand = fetched;
            int borrow = (carry ? 0 : 1);
            currInstruction.tempLatch = a.getValue() - fetched - borrow;
            setSBCFlags();
            return currInstruction.tempLatch;
        };

        switch (currInstruction.addressingMode) {
            case IMM -> {
                int fetched = fetch();
                a.setValue(op.apply(fetched));
            }
            case ZPG -> handleRead_ZeroPageMode(a, op);
            case ZPG_X -> handleRead_ZeroPageIndexed(a, x, op);
            case ABS -> handleRead_AbsoluteMode(a, op);
            case ABS_X -> handleRead_AbsoluteIndexed(a, x, op);
            case ABS_Y -> handleRead_AbsoluteIndexed(a, y, op);
            case IND_X -> handleRead_IndirectXIndexed(a, op);
            case IND_Y -> handleRead_IndirectYIndexed(a, op);
        }
    }

    private void setSBCFlags() {
        int originalA = a.getValue();
        int borrow = (carry ? 0 : 1);
        // For SBC, the carry flag is set if no borrow occurred:
        // That is, if originalA >= (operand + borrow)
        carry = originalA >= (currInstruction.operand + borrow);
        zero = (currInstruction.tempLatch & 0xFF) == 0;
        negative = (currInstruction.tempLatch & 0x80) != 0;
        // Overflow flag for SBC is computed using:
        // overflow = (((originalA ^ result) & (originalA ^ operand)) & 0x80) != 0;
        overflow = (((originalA ^ currInstruction.tempLatch) & (originalA ^ currInstruction.operand)) & 0x80) != 0;
    }

    private void BIT() {
        Function<Integer, Integer> op = (fetched) -> {
            currInstruction.operand = fetched;
            int andResult = a.getValue() & fetched;
            zero = (andResult == 0);
            negative = (fetched & 0x80) != 0;
            overflow = (fetched & 0x40) != 0;
            return a.getValue();
        };

        switch (currInstruction.addressingMode) {
            case ZPG -> handleRead_ZeroPageMode(a, op);
            case ABS -> handleRead_AbsoluteMode(a, op);
            default -> throw new RuntimeException("Unsupported addressing mode for BIT: " + currInstruction.addressingMode);
        }
    }

    private void handleRelativeInstructions(boolean branchNotTakenCondition) {
        switch (remainingCycles) {
            case 3 -> {
                currInstruction.operand = fetch();
                if(branchNotTakenCondition)
                    remainingCycles -= 2;
            }
            case 2 -> {
                int oldPC = pc;
                // Convert operand (8-bit) to signed:
                pc = (pc + (byte) currInstruction.operand) & 0xFFFF;
                if ((oldPC & 0xFF00) == (pc & 0xFF00)) {
                    remainingCycles--;
                }
            }
            case 1 -> {
                //Dummy cycle in case of page crossing
            }
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
    private void handleReadModifyWriteInstructions_AbsoluteIndexed(EightBitRegister register, Function<Integer, Integer> operation) {
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

    private void handleReadModifyWriteInstructions_ZeroPageIndexed(EightBitRegister register, Function<Integer, Integer> operation) {
        switch (remainingCycles) {
            case 5 -> currInstruction.effectiveAddress = fetch();
            case 4 -> currInstruction.effectiveAddress = (currInstruction.effectiveAddress + register.getValue()) & 0xFF;
            case 3 -> currInstruction.tempLatch = read(currInstruction.effectiveAddress);
            case 2 -> currInstruction.tempLatch = operation.apply(currInstruction.tempLatch) & 0xFF;
            case 1 -> write(currInstruction.effectiveAddress, currInstruction.tempLatch);
        }
    }

    private void handleRead_ZeroPageMode(EightBitRegister register, Function<Integer,Integer> operation) {
        switch (remainingCycles) {
            case 2 -> currInstruction.effectiveAddress = fetch();
            case 1 -> {
                int read = read(currInstruction.effectiveAddress);
                register.setValue(operation.apply(read));
            }
        }
    }

    private void handleRead_AbsoluteMode(EightBitRegister register, Function<Integer,Integer> operation) {
        switch (remainingCycles) {
            case 3 -> currInstruction.effectiveAddress = fetch();
            case 2 -> currInstruction.effectiveAddress = (fetch() << 8) | currInstruction.effectiveAddress;
            case 1 -> {
                int read = read(currInstruction.effectiveAddress);
                register.setValue(operation.apply(read));
            }
        }
    }

    private void handleRead_ZeroPageIndexed(EightBitRegister register, EightBitRegister indexRegister, Function<Integer,Integer> operation) {
        switch (remainingCycles) {
            case 3 -> currInstruction.effectiveAddress = fetch();
            case 2 -> currInstruction.effectiveAddress = (currInstruction.effectiveAddress + indexRegister.getValue()) & 0xFF;
            case 1 -> {
                int read = read(currInstruction.effectiveAddress);
                register.setValue(operation.apply(read));
            }
        }
    }

    private void handleRead_AbsoluteIndexed(EightBitRegister register, EightBitRegister indexRegister, Function<Integer, Integer> operation) {
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

    private void handleRead_IndirectXIndexed(EightBitRegister register, Function<Integer, Integer> operation) {
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

    private void handleRead_IndirectYIndexed(EightBitRegister register, Function<Integer, Integer> operation) {
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
    private void handleStore_ZeroPage(EightBitRegister register) {
        switch (remainingCycles) {
            case 2 -> currInstruction.effectiveAddress = fetch();
            case 1 -> write(currInstruction.effectiveAddress, register.getValue());
        }
    }

    private void handleStore_ZeroPageIndexed(EightBitRegister register, EightBitRegister index) {
        switch (remainingCycles) {
            case 3 -> currInstruction.effectiveAddress = fetch();
            case 2 -> currInstruction.effectiveAddress = (currInstruction.effectiveAddress + index.getValue()) & 0xFF;
            case 1 -> bus.write(currInstruction.effectiveAddress, register.getValue());
        }
    }

    private void handleStore_Absolute(EightBitRegister register) {
        switch (remainingCycles) {
            case 3 -> currInstruction.effectiveAddress = fetch();
            case 2 -> currInstruction.effectiveAddress = (fetch() << 8) | currInstruction.effectiveAddress;
            case 1 -> bus.write(currInstruction.effectiveAddress, register.getValue());
        }
    }

    private void handleSTAAbsoluteIndexed(EightBitRegister index) {
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
