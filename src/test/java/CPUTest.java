import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest
    @CsvSource({
            "0xBD, X", // LDA absolute,X
            "0xB9, Y"  // LDA absolute,Y
    })
    public void LDAAbsoluteIndexed_NoPageCrossing(int opCode, char register) {
        final int instructionCycles = 4;

        var builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, opCode, 0x00, 0x90)  // Base address: 0x9000
                .withMemoryValue(0x9005, 0x42); // Place the operand at effective address 0x9005

        if(register == 'X'){
            builder.withRegisterX(0x05);
        } else {
            builder.withRegisterY(0x05);
        }

        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @ParameterizedTest
    @CsvSource({ "0xBD, X", "0xB9, Y" })
    public void LDAAbsoluteXIndexed_PageCrossing(int opCode, char register) {
        final int instructionCycles = 5;

        var builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, opCode, 0xFF, 0x90)
                .withMemoryValue(0x9100, 0x42); // Place operand at effective address 0x9100.

        // Set register = 0x01, so effective address = 0x90FF + 0x01 = 0x9100.
        if(register == 'X'){
            builder.withRegisterX(0x01);
        } else {
            builder.withRegisterY(0x01);
        }

        CPU cpu = builder.buildAndRun(instructionCycles);
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

    @Test
    public void LDXImmediate() {
        final int instructionCycles = 2;
        final int LDX_IMMEDIATE_OPCODE = 0xA2; // Opcode for LDX Immediate

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDX_IMMEDIATE_OPCODE, 0x42) // Simulate LDX #$42
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getX());
    }

    @Test
    public void LDXZeroPage() {
        final int instructionCycles = 3;
        final int LDX_ZEROPAGE_OPCODE = 0xA6; // Opcode for LDX Zero Page

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDX_ZEROPAGE_OPCODE, 0x10) // LDX $10
                .withMemoryValue(0x0010, 0x42) // Set memory at address 0x0010 to 0x42
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getX());
    }

    @Test
    public void LDXZeroPageYIndexed() {
        final int instructionCycles = 4;
        final int LDX_ZEROPAGE_Y_OPCODE = 0xB6; // Opcode for LDX Zero Page,Y

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDX_ZEROPAGE_Y_OPCODE, 0x10) // LDX $10,Y
                .withRegisterY(0x05) // Y = 0x05; effective address = (0x10 + 0x05) mod 256 = 0x15
                .withMemoryValue(0x0015, 0x42)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getX());
    }

    @Test
    public void LDXAbsolute() {
        final int instructionCycles = 4;
        final int LDX_ABSOLUTE_OPCODE = 0xAE; // Opcode for LDX Absolute

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDX_ABSOLUTE_OPCODE, 0x00, 0x90) // LDX $9000
                .withMemoryValue(0x9000, 0x42) // Place the operand at effective address 0x9000
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getX());
    }

    @Test
    public void LDXAbsoluteY_NoPageCrossing() {
        final int instructionCycles = 4;
        final int LDX_ABSOLUTE_Y_OPCODE = 0xBE; // Opcode for LDX Absolute,Y

        // Base address 0x9000, Y = 0x05 results in effective address 0x9005 (no page crossing)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDX_ABSOLUTE_Y_OPCODE, 0x00, 0x90)
                .withRegisterY(0x05)
                .withMemoryValue(0x9005, 0x42)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getX());
    }

    @Test
    public void LDXAbsoluteY_PageCrossing() {
        final int instructionCycles = 5;
        final int LDX_ABSOLUTE_Y_OPCODE = 0xBE; // Opcode for LDX Absolute,Y

        // Base address 0x90FF with Y = 0x01 results in effective address 0x9100 (page crossing occurs)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDX_ABSOLUTE_Y_OPCODE, 0xFF, 0x90)
                .withRegisterY(0x01)
                .withMemoryValue(0x9100, 0x42)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getX());
    }
}