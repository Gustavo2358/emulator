import java.util.Objects;

public class CPU {

    private int pc;
    private int sp;
    private int a, x, y;
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
        a = 0x00;
        x = 0x00;
        y = 0x00;
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
            this.a = cpuState.getA();
            this.x = cpuState.getX();
            this.y = cpuState.getY();
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
    private static enum Register {
        A, X, Y
    }


    public CpuState getState(){
        return new CpuState.Builder()
                .pc(pc)
                .sp(sp)
                .a(a)
                .x(x)
                .y(y)
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
            case 0xBD -> loadInstructionInitialState(5, Instruction.LDA, AddressingMode.ABS_X);
            default -> throw new RuntimeException(String.format("Invalid opcode: 0x%x at address 0x%x", opCode, --pc));
        }
    }

    private void executeInstruction() {
        switch (currInstruction.instruction) {
            case LDA -> LDA();
        }
    }

    private void loadInstructionInitialState(
            int cycles, Instruction instruction, AddressingMode addressingMode
    ) {
        this.remainingCycles = cycles;
        this.currInstruction.instruction = instruction;
        this.currInstruction.addressingMode = addressingMode;
    }

    private void handlePageCrossingInLoadInstruction(int address, Register register) {
        if((address & 0xFF00) == (currInstruction.absoluteAddress & 0xFF00)) {
            switch (register) {
                case A -> a = fetch(currInstruction.absoluteAddress);
                case X -> x = fetch(currInstruction.absoluteAddress);
                case Y -> y = fetch(currInstruction.absoluteAddress);
            }
            //avoid case 1 if no extra cycle is needed. This variable is already
            //decremented outside of this function, so if we decremented here we
            //make sure it will not reach case 1.
            remainingCycles--;
        }
    }

    private void LDA() {
        switch (currInstruction.addressingMode) {
            case IMM -> a = fetch();
            case ZPG -> handleLdaZeroPageMode();
            case ZPG_X -> handleLdaZeroPageXIndexed();
            case ABS -> handleLdaAbsoluteMode();
            case ABS_X -> handleLdaAbsoluteXIndexed();
            case IND_X -> handleLdaIndirectXIndexed();
            case IND_Y -> handleLdaIndirectYIndexed();
        }
        // Update Zero Flag
        zero = (a == 0);
        // Update Negative Flag (bit 7 of A)
        negative = (a & 0x80) != 0;
    }

    private void handleLdaZeroPageMode() {
        switch (remainingCycles) {
            case 2 -> currInstruction.absoluteAddress = fetch();
            case 1 -> a = fetch(currInstruction.absoluteAddress);
        }
    }

    private void handleLdaAbsoluteMode() {
        switch (remainingCycles) {
            case 3 -> currInstruction.absoluteAddress = fetch();
            case 2 -> currInstruction.absoluteAddress = (fetch() << 8) | currInstruction.absoluteAddress;
            case 1 -> a = fetch(currInstruction.absoluteAddress);
        }
    }

    private void handleLdaZeroPageXIndexed() {
        switch (remainingCycles) {
            case 3 -> currInstruction.absoluteAddress = fetch();
            case 2 -> currInstruction.absoluteAddress = (currInstruction.absoluteAddress + x) & 0xFF;
            case 1 -> a = fetch(currInstruction.absoluteAddress);
        }
    }

    private void handleLdaAbsoluteXIndexed() {
        switch (remainingCycles) {
            case 4 -> currInstruction.absoluteAddress = fetch();
            case 3 -> currInstruction.absoluteAddress = (fetch() << 8) | currInstruction.absoluteAddress;
            case 2 -> {
                var address = currInstruction.absoluteAddress;
                currInstruction.absoluteAddress = (address + x) & 0xFFFF;
                handlePageCrossingInLoadInstruction(address, Register.A);
            }
            case 1 -> a = fetch(currInstruction.absoluteAddress);
        }
    }

    private void handleLdaIndirectXIndexed() {
        switch (remainingCycles) {
            case 5 -> currInstruction.operand = fetch();
            case 4 -> currInstruction.operand = (currInstruction.operand + x) & 0xFF;
            case 3 -> currInstruction.absoluteAddress = fetch(currInstruction.operand);
            case 2 -> {
                int highByte = fetch((currInstruction.operand + 1) & 0xFF);
                currInstruction.absoluteAddress = ((highByte << 8) | currInstruction.absoluteAddress) & 0xFFFF;
            }
            case 1 -> a = fetch(currInstruction.absoluteAddress);
        }
    }

    private void handleLdaIndirectYIndexed() {
        switch (remainingCycles) {
            case 5 -> currInstruction.operand = fetch();
            case 4 -> currInstruction.absoluteAddress = fetch(currInstruction.operand & 0xFF) ;
            case 3 -> {
                int highByte = fetch((currInstruction.operand + 1) & 0xFF);
                currInstruction.absoluteAddress = ((highByte << 8) | currInstruction.absoluteAddress) & 0xFFFF;
            }
            case 2 -> {
                var address = currInstruction.absoluteAddress;
                currInstruction.absoluteAddress = (address + y) & 0xFFFF;
                handlePageCrossingInLoadInstruction(address, Register.A);
            }
            case 1 -> a = fetch(currInstruction.absoluteAddress);
        }
    }

}
