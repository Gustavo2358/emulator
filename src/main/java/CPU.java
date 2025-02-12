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
    }


    public CpuState getState(){
        return new CpuState.Builder()
                .pc(pc)
                .sp(sp)
                .a(a)
                .x(x)
                .y(y)
                .carry(carry)
                .zero(carry)
                .interruptDisable(carry)
                .decimal(carry)
                .overflow(carry)
                .negative(carry)
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
        return bus.fetch(address);
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
            case 0xA5 -> loadInstructionInitialState(3, Instruction.LDA, AddressingMode.ZPG);
            case 0xA9 -> loadInstructionInitialState(2, Instruction.LDA, AddressingMode.IMM);
            case 0xAD -> loadInstructionInitialState(4, Instruction.LDA, AddressingMode.ABS);
            case 0xB5 -> loadInstructionInitialState(4, Instruction.LDA, AddressingMode.ZPG_X);
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

    private void LDA() {
        switch (currInstruction.addressingMode) {
            case IMM -> a = fetch();
            case ZPG -> handleLdaZeroPageMode();
            case ZPG_X -> handleLdaZeroPageXIndexed();
            case ABS -> handleLdaAbsoluteMode();
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
            case 3 ->
                // Fetch the base address from the instruction operand.
                    currInstruction.absoluteAddress = fetch();
            case 2 ->
                // Add the X register to the base address.
                // The '& 0xFF' ensures the result wraps within the zero page (0x00 to 0xFF).
                    currInstruction.absoluteAddress = (currInstruction.absoluteAddress + x) & 0xFF;
            case 1 ->
                // Fetch the value from the computed effective address and load it into the accumulator.
                    a = fetch(currInstruction.absoluteAddress);
        }
    }
}
