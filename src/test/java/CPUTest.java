import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
class CPUTest {

    @Test
    public void LDAImmediate() {
        final int instructionCycles = 2;
        final int LDA_IMMEDIATE_OPCODE = 0xA9; // Opcode for LDA Immediate

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDA_IMMEDIATE_OPCODE, 0x42) // Simulate LDA #$42
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAAbsolute() {
        final int instructionCycles = 4;
        final int LDA_ABSOLUTE_OPCODE = 0xAD; // Opcode for LDA Absolute

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDA_ABSOLUTE_OPCODE, 0x00, 0x90) // LDA $9000
                .withMemoryValue(0x9000, 0x42) // Set operand at effective address 0x9000
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAZeroPage() {
        final int instructionCycles = 3;
        final int LDA_ZERO_PAGE_OPCODE = 0xA5; // Opcode for LDA Zero Page

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDA_ZERO_PAGE_OPCODE, 0x10) // Instruction: LDA $10
                .withMemoryValue(0x0010, 0x42) // Set memory at address 0x0010 to 0x42
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAZeroPageXIndexed() {
        final int instructionCycles = 4;
        final int LDA_ZERO_PAGE_X_OPCODE = 0xB5; // Opcode for LDA Zero Page,X

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDA_ZERO_PAGE_X_OPCODE, 0x10)
                .withRegisterX(0x05) // X = 0x05; effective address: 0x10 + 0x05 = 0x15
                .withMemoryValue(0x0015, 0x42)// Place the operand at address 0x15
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAAbsoluteXIndexed_NoPageCrossing() {
        final int instructionCycles = 4;
        final int LDA_ABSOLUTE_X_OPCODE = 0xBD; // Opcode for LDA absolute, X

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDA_ABSOLUTE_X_OPCODE, 0x00, 0x90)  // Base address: 0x9000
                .withRegisterX(0x05) // X = 0x05; effective address = 0x9000 + 0x05 = 0x9005
                .withMemoryValue(0x9005, 0x42) // Place the operand at effective address 0x9005
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAAbsoluteXIndexed_PageCrossing() {
        final int instructionCycles = 5;
        final int LDA_ABSOLUTE_X_OPCODE = 0xBD; // Opcode for LDA absolute, X

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDA_ABSOLUTE_X_OPCODE, 0xFF, 0x90)
                .withRegisterX(0x01) // Set X = 0x01, so effective address = 0x90FF + 0x01 = 0x9100.
                .withMemoryValue(0x9100, 0x42) // Place operand at effective address 0x9100.
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAIndirectX() {
        final int instructionCycles = 6;
        final int LDA_INDIRECT_X_OPCODE = 0xA1;

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDA_INDIRECT_X_OPCODE, 0x10)
                .withRegisterX(0x05)
                // Effective zero page pointer = (0x10 + 0x05) mod 256 = 0x15.
                // Set the pointer at zero page 0x15 to point to effective address 0x9000.
                .withZeroPagePointer(0x15, 0x00, 0x90)
                // Place the operand at the effective address 0x9000.
                .withMemoryValue(0x9000, 0x42)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }


    @Test
    public void testLDAIndirectY_NoPageCrossing() {
        final int LDA_INDIRECT_Y_OPCODE = 0xB1;
        int instructionCycles = 5;

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDA_INDIRECT_Y_OPCODE, 0x20)
                .withZeroPagePointer(0x20, 0x00, 0x90)
                .withRegisterY(0x05) // Y = 0x05, so effective address = 0x9005
                .withMemoryValue(0x9005, 0x42)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @Test
    public void LDAIndirectY_PageCrossing() {
        final int instructionCycles = 6;
        final int LDA_INDIRECT_Y_OPCODE = 0xB1; // Opcode for (indirect),Y addressing mode

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDA_INDIRECT_Y_OPCODE, 0x30)
                .withZeroPagePointer(0x30, 0xFF, 0x90)  // Pointer at zero page 0x30 to base address 0x90FF
                .withRegisterY(0x01)  // Y register = 0x01; effective address becomes 0x90FF + 0x01 = 0x9100
                .withMemoryValue(0x9100, 0x42) // Place operand at effective address 0x9100
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }
}