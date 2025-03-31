import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CPUTest {

    @ParameterizedTest
    @MethodSource("provideLoadImmediateArguments")
    public void LD_Immediate(int opCode, Function<CpuState, Integer> getRegister) {
        final int instructionCycles = 2;

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, opCode, 0x42) // Simulate LD_ #$42
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, getRegister.apply(state));
    }

    private static Stream<Arguments> provideLoadImmediateArguments() {
        final int LDA_IMMEDIATE_OPCODE = 0xA9; // Opcode for LDA Immediate
        final Function<CpuState, Integer> getA = CpuState::getA;
        final int LDX_IMMEDIATE_OPCODE = 0xA2; // Opcode for LDX Immediate
        final Function<CpuState, Integer> getX = CpuState::getX;
        final int LDY_IMMEDIATE_OPCODE = 0xA0; // Opcode for LDX Immediate
        final Function<CpuState, Integer> getY = CpuState::getY;
        return Stream.of(
                Arguments.of(LDA_IMMEDIATE_OPCODE, getA),
                Arguments.of(LDX_IMMEDIATE_OPCODE, getX),
                Arguments.of(LDY_IMMEDIATE_OPCODE, getY)
        );
    }

    @ParameterizedTest
    @MethodSource("provideLoadAbsoluteArguments")
    public void LD_Absolute(int opCode, Function<CpuState, Integer> getRegister) {
        final int instructionCycles = 4;

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, opCode, 0x00, 0x90)
                .withMemoryValue(0x9000, 0x42) // Set operand at effective address 0x9000
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, getRegister.apply(state));
    }

    private static Stream<Arguments> provideLoadAbsoluteArguments() {
        final int LDA_ABSOLUTE_OPCODE = 0xAD; // Opcode for LDA Absolute
        final Function<CpuState, Integer> getA = CpuState::getA;
        final int LDX_ABSOLUTE_OPCODE = 0xAE; // Opcode for LDX Absolute
        final Function<CpuState, Integer> getX = CpuState::getX;
        final int LDY_ABSOLUTE_OPCODE = 0xAC; // Opcode for LDX Absolute
        final Function<CpuState, Integer> getY = CpuState::getY;
        return Stream.of(
                Arguments.of(LDA_ABSOLUTE_OPCODE, getA),
                Arguments.of(LDX_ABSOLUTE_OPCODE, getX),
                Arguments.of(LDY_ABSOLUTE_OPCODE, getY)
        );
    }

    @ParameterizedTest
    @MethodSource("provideLoadZeroPageArguments")
    public void LD_ZeroPage(int opCode, Function<CpuState, Integer> getRegister) {
        final int instructionCycles = 3;

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, opCode, 0x10) // Instruction: LDA $10
                .withMemoryValue(0x0010, 0x42) // Set memory at address 0x0010 to 0x42
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, getRegister.apply(state));
    }

    private static Stream<Arguments> provideLoadZeroPageArguments() {
        final int LDA_ZERO_PAGE_OPCODE = 0xA5; // Opcode for LDA Zero Page
        final Function<CpuState, Integer> getA = CpuState::getA;
        final int LDX_ZERO_PAGE_OPCODE = 0xA6; // Opcode for LDX Zero Page
        final Function<CpuState, Integer> getX = CpuState::getX;
        final int LDY_ZERO_PAGE_OPCODE = 0xA4; // Opcode for LDX Zero Page
        final Function<CpuState, Integer> getY = CpuState::getY;
        return Stream.of(
                Arguments.of(LDA_ZERO_PAGE_OPCODE, getA),
                Arguments.of(LDX_ZERO_PAGE_OPCODE, getX),
                Arguments.of(LDY_ZERO_PAGE_OPCODE, getY)
        );
    }

    @Test
    public void LDAZeroPageXIndexed() {
        final int instructionCycles = 4;
        final int LDA_ZERO_PAGE_X_OPCODE = 0xB5; // Opcode for LDA Zero-Page, X

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

        if (register == 'X') {
            builder.withRegisterX(0x05);
        } else {
            builder.withRegisterY(0x05);
        }

        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
    }

    @ParameterizedTest
    @CsvSource({"0xBD, X", "0xB9, Y"})
    public void LDAAbsoluteXIndexed_PageCrossing(int opCode, char register) {
        final int instructionCycles = 5;

        var builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, opCode, 0xFF, 0x90)
                .withMemoryValue(0x9100, 0x42); // Place operand at effective address 0x9100.

        // Set register = 0x01, so effective address = 0x90FF + 0x01 = 0x9100.
        if (register == 'X') {
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
                // Effective zero-page pointer = (0x10 + 0x05) mod 256 = 0x15.
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
    public void LDXZeroPageYIndexed() {
        final int instructionCycles = 4;
        final int LDX_ZERO_PAGE_Y_OPCODE = 0xB6; // Opcode for LDX Zero-Page, Y

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDX_ZERO_PAGE_Y_OPCODE, 0x10) // LDX $10,Y
                .withRegisterY(0x05) // Y = 0x05; effective address = (0x10 + 0x05) mod 256 = 0x15
                .withMemoryValue(0x0015, 0x42)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getX());
    }

    @Test
    public void LDYZeroPageXIndexed() {
        final int instructionCycles = 4;
        final int LDX_ZERO_PAGE_X_OPCODE = 0xB4; // Opcode for LDY Zero-Page, X

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDX_ZERO_PAGE_X_OPCODE, 0x10) // LDX $10,Y
                .withRegisterX(0x05) // Y = 0x05; effective address = (0x10 + 0x05) mod 256 = 0x15
                .withMemoryValue(0x0015, 0x42)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getY());
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

    @Test
    public void LDYAbsoluteX_NoPageCrossing() {
        final int instructionCycles = 4;
        final int LDY_ABSOLUTE_X_OPCODE = 0xBC; // Opcode for LDY Absolute,X

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDY_ABSOLUTE_X_OPCODE, 0x00, 0x90)
                .withRegisterX(0x05)
                .withMemoryValue(0x9005, 0x42)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getY());
    }

    @Test
    public void LDYAbsoluteX_PageCrossing() {
        final int instructionCycles = 5;
        final int LDY_ABSOLUTE_X_OPCODE = 0xBC; // Opcode for LDY Absolute,X

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, LDY_ABSOLUTE_X_OPCODE, 0xFF, 0x90)
                .withRegisterX(0x01)
                .withMemoryValue(0x9100, 0x42)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getY());
    }

    // #### STA ####

    @Test
    public void STA_ZeroPage() {
        final int instructionCycles = 3;
        final int STA_ZERO_PAGE_OPCODE = 0x85; // Opcode for STA Zero Page

        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x42)
                .withInstruction(0x8000, STA_ZERO_PAGE_OPCODE, 0x10) // STA $10
                .buildAndRun(instructionCycles, bus);

        //Verify that memory at address 0x0010 now holds 0x42
        assertEquals(0x42, bus.read(0x0010));
    }

    @Test
    public void STA_ZeroPageX() {
        final int instructionCycles = 4;
        final int STA_ZERO_PAGE_X_OPCODE = 0x95; // Opcode for STA Zero-Page, X

        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x42)
                .withRegisterX(0x05) // X = 0x05; effective address: 0x10 + 0x05 = 0x15
                .withInstruction(0x8000, STA_ZERO_PAGE_X_OPCODE, 0x10) // STA $10,X
                .buildAndRun(instructionCycles, bus);

        assertEquals(0x42, bus.read(0x0015));
    }

    @Test
    public void STA_Absolute() {
        final int instructionCycles = 4;
        final int STA_ABSOLUTE_OPCODE = 0x8D; // Opcode for STA Absolute

        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x42)
                .withInstruction(0x8000, STA_ABSOLUTE_OPCODE, 0x00, 0x90) // STA $9000
                .buildAndRun(instructionCycles, bus);

        assertEquals(0x42, bus.read(0x9000));
    }

    @Test
    public void STA_AbsoluteX() {
        final int instructionCycles = 5;
        final int STA_ABSOLUTE_X_OPCODE = 0x9D; // Opcode for STA Absolute,X

        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x42)
                .withRegisterX(0x05) // Effective address: $9000 + 0x05 = 0x9005
                .withInstruction(0x8000, STA_ABSOLUTE_X_OPCODE, 0x00, 0x90)
                .buildAndRun(instructionCycles, bus);

        assertEquals(0x42, bus.read(0x9005));
    }

    @Test
    public void STA_AbsoluteY() {
        final int instructionCycles = 5;
        final int STA_ABSOLUTE_Y_OPCODE = 0x99; // Opcode for STA Absolute,Y

        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x42)
                .withRegisterY(0x05) // Effective address: $9000 + 0x05 = 0x9005
                .withInstruction(0x8000, STA_ABSOLUTE_Y_OPCODE, 0x00, 0x90)
                .buildAndRun(instructionCycles, bus);

        assertEquals(0x42, bus.read(0x9005));
    }

    @Test
    public void STA_IndirectX() {
        final int instructionCycles = 6;
        final int STA_INDIRECT_X_OPCODE = 0x81; // Opcode for STA (Indirect,X)

        // For (Indirect,X): the operand (0x10) is added to X (0x05) to get the zero-page pointer at 0x15.
        // The two-byte pointer at 0x15 points to the effective address.
        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x42)
                .withRegisterX(0x05)
                .withInstruction(0x8000, STA_INDIRECT_X_OPCODE, 0x10)
                .withZeroPagePointer(0x15, 0x00, 0x90) // Pointer at 0x15 -> effective address 0x9000
                .buildAndRun(instructionCycles, bus);

        assertEquals(0x42, bus.read(0x9000));
    }

    @Test
    public void STA_IndirectY() {
        final int instructionCycles = 6;
        final int STA_INDIRECT_Y_OPCODE = 0x91; // Opcode for STA (Indirect),Y

        // For (Indirect),Y: the operand (0x10) gives a zero-page pointer whose two-byte value is the base address.
        // Then Y (0x05) is added to form the effective address.
        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x42)
                .withRegisterY(0x05)
                .withInstruction(0x8000, STA_INDIRECT_Y_OPCODE, 0x10)
                .withZeroPagePointer(0x10, 0x00, 0x90) // Pointer at 0x10 -> base address 0x9000
                .buildAndRun(instructionCycles, bus);

        // Effective address: 0x9000 + 0x05 = 0x9005
        assertEquals(0x42, bus.read(0x9005));
    }

    // #### STX ####

    @Test
    public void STX_ZeroPage() {
        final int instructionCycles = 3;
        final int STX_ZERO_PAGE_OPCODE = 0x86; // Opcode for STX Zero Page

        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(0x42)
                .withInstruction(0x8000, STX_ZERO_PAGE_OPCODE, 0x10) // STA $10
                .buildAndRun(instructionCycles, bus);

        //Verify that memory at address 0x0010 now holds 0x42
        assertEquals(0x42, bus.read(0x0010));
    }

    @Test
    public void STX_ZeroPageY() {
        final int instructionCycles = 4;
        final int STA_ZERO_PAGE_Y_OPCODE = 0x96; // Opcode for STX Zero Page,X

        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(0x42)
                .withRegisterY(0x05) // Y = 0x05; effective address: 0x10 + 0x05 = 0x15
                .withInstruction(0x8000, STA_ZERO_PAGE_Y_OPCODE, 0x10) // STA $10,X
                .buildAndRun(instructionCycles, bus);

        assertEquals(0x42, bus.read(0x0015));
    }

    @Test
    public void STX_Absolute() {
        final int instructionCycles = 4;
        final int STY_ABSOLUTE_OPCODE = 0x8E; // Opcode for STX Absolute

        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(0x42)
                .withInstruction(0x8000, STY_ABSOLUTE_OPCODE, 0x00, 0x90) // STA $9000
                .buildAndRun(instructionCycles, bus);

        assertEquals(0x42, bus.read(0x9000));
    }

    // #### STY ####

    @Test
    public void STY_ZeroPage() {
        final int instructionCycles = 3;
        final int STY_ZERO_PAGE_OPCODE = 0x84; // Opcode for STY Zero Page

        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterY(0x42)
                .withInstruction(0x8000, STY_ZERO_PAGE_OPCODE, 0x10) // STX $10
                .buildAndRun(instructionCycles, bus);

        //Verify that memory at address 0x0010 now holds 0x42
        assertEquals(0x42, bus.read(0x0010));
    }

    @Test
    public void STY_ZeroPageX() {
        final int instructionCycles = 4;
        final int STA_ZERO_PAGE_X_OPCODE = 0x94; // Opcode for STX Zero Page,X

        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterY(0x42)
                .withRegisterX(0x05) // X = 0x05; effective address: 0x10 + 0x05 = 0x15
                .withInstruction(0x8000, STA_ZERO_PAGE_X_OPCODE, 0x10) // STX $10,X
                .buildAndRun(instructionCycles, bus);

        assertEquals(0x42, bus.read(0x0015));
    }

    @Test
    public void STY_Absolute() {
        final int instructionCycles = 4;
        final int STY_ABSOLUTE_OPCODE = 0x8C; // Opcode for STY Absolute

        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterY(0x42)
                .withInstruction(0x8000, STY_ABSOLUTE_OPCODE, 0x00, 0x90) // STA $9000
                .buildAndRun(instructionCycles, bus);

        assertEquals(0x42, bus.read(0x9000));
    }

    //#### TAX ####

    @Test
    public void TAX_NonZeroNonNegative() {
        final int instructionCycles = 2;
        final int TAX_IMPLIED_OPCODE = 0xAA;

        // Set accumulator to a non-zero, non-negative value (0x42)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x42)
                .withInstruction(0x8000, TAX_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getX());
        assertFalse(state.isZero());
        assertFalse(state.isNegative());
    }

    @Test
    public void TAX_ZeroFlag() {
        final int instructionCycles = 2;
        final int TAX_IMPLIED_OPCODE = 0xAA;

        // Set accumulator to zero
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x00)
                .withInstruction(0x8000, TAX_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x00, state.getX());
        assertTrue(state.isZero());
        assertFalse(state.isNegative());
    }

    @Test
    public void TAX_NegativeFlag() {
        final int instructionCycles = 2;
        final int TAX_IMPLIED_OPCODE = 0xAA;

        // Set accumulator to a value with the high bit set (e.g., 0x80)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x80)
                .withInstruction(0x8000, TAX_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x80, state.getX());
        assertFalse(state.isZero());
        assertTrue(state.isNegative());
    }

    //#### TAY ####

    @Test
    public void TAY_NonZeroNonNegative() {
        final int instructionCycles = 2;
        final int TAY_IMPLIED_OPCODE = 0xA8;

        // Set accumulator to a non-zero, non-negative value (0x42)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x42)
                .withInstruction(0x8000, TAY_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getY());
        assertFalse(state.isZero());
        assertFalse(state.isNegative());
    }

    @Test
    public void TAY_ZeroFlag() {
        final int instructionCycles = 2;
        final int TAY_IMPLIED_OPCODE = 0xA8;

        // Set accumulator to zero
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x00)
                .withInstruction(0x8000, TAY_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x00, state.getY());
        assertTrue(state.isZero());
        assertFalse(state.isNegative());
    }

    @Test
    public void TAY_NegativeFlag() {
        final int instructionCycles = 2;
        final int TAY_IMPLIED_OPCODE = 0xA8;

        // Set accumulator to a value with the high bit set (e.g., 0x80)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x80)
                .withInstruction(0x8000, TAY_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x80, state.getY());
        assertFalse(state.isZero());
        assertTrue(state.isNegative());
    }

    //#### TSX ####

    @Test
    public void TSX_NonZeroNonNegative() {
        final int instructionCycles = 2;
        final int TSX_IMPLIED_OPCODE = 0xBA;

        // Set stack pointer to a non-zero, non-negative value (0x42)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withStackPointer(0x42)
                .withInstruction(0x8000, TSX_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getX());
        assertFalse(state.isZero());
        assertFalse(state.isNegative());
    }

    @Test
    public void TSX_ZeroFlag() {
        final int instructionCycles = 2;
        final int TSX_IMPLIED_OPCODE = 0xBA;

        // Set stack pointer to zero
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withStackPointer(0x00)
                .withInstruction(0x8000, TSX_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x00, state.getX());
        assertTrue(state.isZero());
        assertFalse(state.isNegative());
    }

    @Test
    public void TSX_NegativeFlag() {
        final int instructionCycles = 2;
        final int TSX_IMPLIED_OPCODE = 0xBA;

        // Set stack pointer to a value with the high bit set (e.g., 0x80)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withStackPointer(0x80)
                .withInstruction(0x8000, TSX_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x80, state.getX());
        assertFalse(state.isZero());
        assertTrue(state.isNegative());
    }

    //#### TXA ####

    @Test
    public void TXA_NonZeroNonNegative() {
        final int instructionCycles = 2;
        final int TXA_IMPLIED_OPCODE = 0x8A;

        // Set X register to a non-zero, non-negative value (0x42)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(0x42)
                .withInstruction(0x8000, TXA_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
        assertFalse(state.isZero());
        assertFalse(state.isNegative());
    }

    @Test
    public void TXA_ZeroFlag() {
        final int instructionCycles = 2;
        final int TXA_IMPLIED_OPCODE = 0x8A;

        // Set X register to zero
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(0x00)
                .withInstruction(0x8000, TXA_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x00, state.getA());
        assertTrue(state.isZero());
        assertFalse(state.isNegative());
    }

    @Test
    public void TXA_NegativeFlag() {
        final int instructionCycles = 2;
        final int TXA_IMPLIED_OPCODE = 0x8A;

        // Set X register to a value with the high bit set (e.g., 0x80)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(0x80)
                .withInstruction(0x8000, TXA_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x80, state.getA());
        assertFalse(state.isZero());
        assertTrue(state.isNegative());
    }

    @Test
    public void TXS() {
        final int instructionCycles = 2;
        final int TXS_IMPLIED_OPCODE = 0x9A;

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(0x42)
                .withInstruction(0x8000, TXS_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getSp());
    }

    //#### TYA ####

    @Test
    public void TYA_NonZeroNonNegative() {
        final int instructionCycles = 2;
        final int TYA_IMPLIED_OPCODE = 0x98;

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterY(0x42)
                .withInstruction(0x8000, TYA_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x42, state.getA());
        assertFalse(state.isZero());
        assertFalse(state.isNegative());
    }

    @Test
    public void TYA_ZeroFlag() {
        final int instructionCycles = 2;
        final int TYA_IMPLIED_OPCODE = 0x98;

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterY(0x00)
                .withInstruction(0x8000, TYA_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x00, state.getA());
        assertTrue(state.isZero());
        assertFalse(state.isNegative());
    }

    @Test
    public void TYA_NegativeFlag() {
        final int instructionCycles = 2;
        final int TYA_IMPLIED_OPCODE = 0x98;

        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterY(0x80)
                .withInstruction(0x8000, TYA_IMPLIED_OPCODE)
                .buildAndRun(instructionCycles);

        CpuState state = cpu.getState();
        assertEquals(0x80, state.getA());
        assertFalse(state.isZero());
        assertTrue(state.isNegative());
    }

    // ### PHA ###

    @Test
    public void PHA_Implied() {
        final int instructionCycles = 3;
        final int PHA_OPCODE = 0x48;

        final int initialSP = 0xFD;
        final int accumulatorValue = 0x37;
        final int resetAddress = 0x8000;

        Bus bus = new MockBus();

        CPU cpu = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withRegisterA(accumulatorValue)
                .withStackPointer(initialSP)
                .withInstruction(resetAddress, PHA_OPCODE)
                .buildAndRun(instructionCycles, bus);

        // The PHA instruction pushes A onto the stack at 0x0100 + SP before decrementing SP.
        int expectedStackAddress = 0x0100 | initialSP;
        assertEquals(accumulatorValue, bus.read(expectedStackAddress));

        // Verify that the stack pointer is decremented by 1.
        int expectedSP = (initialSP - 1) & 0xFF;
        assertEquals(expectedSP, cpu.getState().getSp());
    }

    @ParameterizedTest
    @CsvSource({
            "false, false, false, false, false, false, 48",
            "false, false, false, false, false, true, 49",
            "false, false, false, false, true, false, 50",
            "false, false, false, false, true, true, 51",
            "false, false, false, true, false, false, 56",
            "false, false, false, true, false, true, 57",
            "false, false, false, true, true, false, 58",
            "false, false, false, true, true, true, 59",
            "false, false, true, false, false, false, 52",
            "false, false, true, false, false, true, 53",
            "false, false, true, false, true, false, 54",
            "false, false, true, false, true, true, 55",
            "false, false, true, true, false, false, 60",
            "false, false, true, true, false, true, 61",
            "false, false, true, true, true, false, 62",
            "false, false, true, true, true, true, 63",
            "false, true, false, false, false, false, 112",
            "false, true, false, false, false, true, 113",
            "false, true, false, false, true, false, 114",
            "false, true, false, false, true, true, 115",
            "false, true, false, true, false, false, 120",
            "false, true, false, true, false, true, 121",
            "false, true, false, true, true, false, 122",
            "false, true, false, true, true, true, 123",
            "false, true, true, false, false, false, 116",
            "false, true, true, false, false, true, 117",
            "false, true, true, false, true, false, 118",
            "false, true, true, false, true, true, 119",
            "false, true, true, true, false, false, 124",
            "false, true, true, true, false, true, 125",
            "false, true, true, true, true, false, 126",
            "false, true, true, true, true, true, 127",
            "true, false, false, false, false, false, 176",
            "true, false, false, false, false, true, 177",
            "true, false, false, false, true, false, 178",
            "true, false, false, false, true, true, 179",
            "true, false, false, true, false, false, 184",
            "true, false, false, true, false, true, 185",
            "true, false, false, true, true, false, 186",
            "true, false, false, true, true, true, 187",
            "true, false, true, false, false, false, 180",
            "true, false, true, false, false, true, 181",
            "true, false, true, false, true, false, 182",
            "true, false, true, false, true, true, 183",
            "true, false, true, true, false, false, 188",
            "true, false, true, true, false, true, 189",
            "true, false, true, true, true, false, 190",
            "true, false, true, true, true, true, 191",
            "true, true, false, false, false, false, 240",
            "true, true, false, false, false, true, 241",
            "true, true, false, false, true, false, 242",
            "true, true, false, false, true, true, 243",
            "true, true, false, true, false, false, 248",
            "true, true, false, true, false, true, 249",
            "true, true, false, true, true, false, 250",
            "true, true, false, true, true, true, 251",
            "true, true, true, false, false, false, 244",
            "true, true, true, false, false, true, 245",
            "true, true, true, false, true, false, 246",
            "true, true, true, false, true, true, 247",
            "true, true, true, true, false, false, 252",
            "true, true, true, true, false, true, 253",
            "true, true, true, true, true, false, 254",
            "true, true, true, true, true, true, 255"
    })
    public void PHP_FlagsToBits(boolean negative, boolean overflow, boolean interruptDisable,
                                boolean decimal, boolean zero, boolean carry, int expectedFlags) {
        final int instructionCycles = 3;
        final int PHP_OPCODE = 0x08;
        final int initialSP = 0xFD;
        final int resetAddress = 0x8000;

        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withFlagNegative(negative)
                .withFlagOverflow(overflow)
                .withFlagInterruptDisable(interruptDisable)
                .withFlagDecimal(decimal)
                .withFlagZero(zero)
                .withFlagCarry(carry)
                .withStackPointer(initialSP)
                .withInstruction(resetAddress, PHP_OPCODE)
                .buildAndRun(instructionCycles, bus);

        int expectedStackAddress = 0x0100 | initialSP;
        int actualFlags = bus.read(expectedStackAddress);
        assertEquals(expectedFlags, actualFlags);

        int expectedSP = (initialSP - 1) & 0xFF;
        assertEquals(expectedSP, cpu.getState().getSp());
    }

    @ParameterizedTest
    @CsvSource({
            "0",
            "42",
            "128",
            "255"
    })
    public void PLA_Parameterized(int valueToPull) {
        final int instructionCycles = 4;
        final int PLA_OPCODE = 0x68;
        final int initialSP = 0xFC;
        final int resetAddress = 0x8000;

        int expectedStackAddress = 0x0100 | (initialSP + 1);

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withStackPointer(initialSP)
                .withMemoryValue(expectedStackAddress, valueToPull)
                .withInstruction(resetAddress, PLA_OPCODE);

        CPU cpu = builder.buildAndRun(instructionCycles);

        assertEquals(valueToPull, cpu.getState().getA());
        assertEquals(valueToPull == 0, cpu.getState().isZero());
        assertEquals((valueToPull & 0x80) != 0, cpu.getState().isNegative());
        int expectedSP = (initialSP + 1) & 0xFF;
        assertEquals(expectedSP, cpu.getState().getSp());
    }

    @ParameterizedTest
    @CsvSource({
            "48",  // Only constant bits (0x30), no flags set.
            "49",  // Constant bits plus the Carry flag (0x30 | 0x01).
            "50",  // Constant bits plus the Zero flag (0x30 | 0x02).
            "52",  // Constant bits plus the Interrupt Disable flag (0x30 | 0x04).
            "56",  // Constant bits plus the Decimal flag (0x30 | 0x08).
            "112", // Constant bits plus the Overflow flag (0x30 | 0x40).
            "176", // Constant bits plus the Negative flag (0x30 | 0x80).
            "255"  // All bits set (i.e. all flags true).
    })
    public void PLP_Parameterized(int valueToPull) {
        final int instructionCycles = 4;
        final int PLP_OPCODE = 0x28; // PLP opcode
        final int initialSP = 0xFC;  // initial stack pointer value before PLP
        final int resetAddress = 0x8000;

        // PLP increments SP and pulls from address 0x0100 | (initialSP + 1)
        int expectedStackAddress = 0x0100 | (initialSP + 1);

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withStackPointer(initialSP)
                .withMemoryValue(expectedStackAddress, valueToPull)
                .withInstruction(resetAddress, PLP_OPCODE);

        CPU cpu = builder.buildAndRun(instructionCycles);

        // Determine expected flag booleans from the pulled value:
        boolean expectedNegative = (valueToPull & 0x80) != 0;
        boolean expectedOverflow = (valueToPull & 0x40) != 0;
        boolean expectedDecimal = (valueToPull & 0x08) != 0;
        boolean expectedInterruptDisable = (valueToPull & 0x04) != 0;
        boolean expectedZero = (valueToPull & 0x02) != 0;
        boolean expectedCarry = (valueToPull & 0x01) != 0;

        assertEquals(expectedNegative, cpu.getState().isNegative());
        assertEquals(expectedOverflow, cpu.getState().isOverflow());
        assertEquals(expectedDecimal, cpu.getState().isDecimal());
        assertEquals(expectedInterruptDisable, cpu.getState().isInterruptDisable());
        assertEquals(expectedZero, cpu.getState().isZero());
        assertEquals(expectedCarry, cpu.getState().isCarry());

        // Verify that the stack pointer has been incremented by 1.
        int expectedSP = (initialSP + 1) & 0xFF;
        assertEquals(expectedSP, cpu.getState().getSp());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 255, false, true",    // 0 - 1 wraps to 255 (0xFF), negative flag set (bit 7 = 1)
            "1, 0, true, false",      // 1 - 1 = 0, zero flag set
            "2, 1, false, false",     // 2 - 1 = 1, no flag set
            "128, 127, false, false", // 128 (0x80) - 1 = 127 (0x7F), zero flag clear, negative clear
            "255, 254, false, true"   // 255 (0xFF) - 1 = 254 (0xFE), negative flag set
    })
    public void DEC_ZeroPage(int initialValue, int expectedValue, boolean expectedZero, boolean expectedNegative) {
        final int instructionCycles = 5;
        final int DEC_OPCODE = 0xC6;
        final int effectiveAddress = 0x10;
        final int resetAddress = 0x8000;

        Bus bus = new MockBus();

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withMemoryValue(effectiveAddress, initialValue)
                .withInstruction(resetAddress, DEC_OPCODE, effectiveAddress);

        CPU cpu = builder.buildAndRun(instructionCycles, bus);

        assertEquals(expectedValue, bus.read(effectiveAddress));
        assertEquals(expectedZero, cpu.getState().isZero());
        assertEquals(expectedNegative, cpu.getState().isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 255, false, true",    // 0 - 1 wraps to 255 (0xFF), negative flag set (bit 7 = 1)
            "1, 0, true, false",      // 1 - 1 = 0, zero flag set
            "2, 1, false, false",     // 2 - 1 = 1, no flag set
            "128, 127, false, false", // 128 (0x80) - 1 = 127 (0x7F), zero flag clear, negative clear
            "255, 254, false, true"   // 255 (0xFF) - 1 = 254 (0xFE), negative flag set
    })
    public void DEC_ZeroPageX(int initialValue, int expectedValue, boolean expectedZero, boolean expectedNegative) {
        final int instructionCycles = 6;
        final int DEC_OPCODE = 0xD6;
        final int baseAddress = 0x10;
        final int resetAddress = 0x8000;
        final int registerX = 0x05;

        int effectiveAddress = (baseAddress + registerX) & 0xFF;

        Bus bus = new MockBus();

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withRegisterX(registerX)
                .withMemoryValue(effectiveAddress, initialValue)
                .withInstruction(resetAddress, DEC_OPCODE, baseAddress);

        CPU cpu = builder.buildAndRun(instructionCycles, bus);

        assertEquals(expectedValue, bus.read(effectiveAddress));
        assertEquals(expectedZero, cpu.getState().isZero());
        assertEquals(expectedNegative, cpu.getState().isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 255, false, true",    // 0 - 1 wraps to 255 (0xFF); negative flag set (bit 7 = 1)
            "1, 0, true, false",      // 1 - 1 = 0; zero flag set
            "2, 1, false, false",     // 2 - 1 = 1; no flag set
            "128, 127, false, false", // 128 (0x80) - 1 = 127 (0x7F); zero clear, negative clear
            "255, 254, false, true"   // 255 (0xFF) - 1 = 254 (0xFE); negative flag set
    })
    public void DEC_Absolute(int initialValue, int expectedValue, boolean expectedZero, boolean expectedNegative) {
        final int instructionCycles = 6;
        final int DEC_OPCODE = 0xCE;
        final int effectiveAddress = 0x1234;
        final int resetAddress = 0x8000;

        Bus bus = new MockBus();

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withMemoryValue(effectiveAddress, initialValue)
                .withInstruction(resetAddress, DEC_OPCODE, effectiveAddress & 0xFF, (effectiveAddress >> 8) & 0xFF);

        CPU cpu = builder.buildAndRun(instructionCycles, bus);

        assertEquals(expectedValue, bus.read(effectiveAddress));
        assertEquals(expectedZero, cpu.getState().isZero());
        assertEquals(expectedNegative, cpu.getState().isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 255, false, true",    // 0 - 1 wraps to 255 (0xFF); negative flag set (bit 7 = 1)
            "1, 0, true, false",      // 1 - 1 = 0; zero flag set
            "2, 1, false, false",     // 2 - 1 = 1; no flag set
            "128, 127, false, false", // 128 (0x80) - 1 = 127 (0x7F); zero clear, negative clear
            "255, 254, false, true"   // 255 (0xFF) - 1 = 254 (0xFE); negative flag set
    })
    public void DEC_AbsoluteX(int initialValue, int expectedValue, boolean expectedZero, boolean expectedNegative) {
        final int instructionCycles = 7;
        final int DEC_OPCODE = 0xDE;
        final int baseAddress = 0x1234;
        final int resetAddress = 0x8000;
        final int registerX = 0x10;

        int effectiveAddress = baseAddress + registerX;

        Bus bus = new MockBus();

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withRegisterX(registerX)
                .withMemoryValue(effectiveAddress, initialValue)
                .withInstruction(resetAddress, DEC_OPCODE,
                        baseAddress & 0xFF, (baseAddress >> 8) & 0xFF);

        CPU cpu = builder.buildAndRun(instructionCycles, bus);

        assertEquals(expectedValue, bus.read(effectiveAddress));
        assertEquals(expectedZero, cpu.getState().isZero());
        assertEquals(expectedNegative, cpu.getState().isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 255, false, true",   // 0 - 1 wraps to 255 (0xFF); negative flag set (bit 7 = 1)
            "1, 0, true, false",     // 1 - 1 = 0; zero flag set
            "2, 1, false, false",    // 2 - 1 = 1; no flag set
            "128, 127, false, false",// 128 (0x80) - 1 = 127 (0x7F); negative flag clear
            "255, 254, false, true"  // 255 (0xFF) - 1 = 254 (0xFE); negative flag set
    })
    public void DEX(int initialX, int expectedX, boolean expectedZero, boolean expectedNegative) {
        final int instructionCycles = 2;
        final int DEX_OPCODE = 0xCA;
        final int resetAddress = 0x8000;

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withRegisterX(initialX)
                .withInstruction(resetAddress, DEX_OPCODE);

        CPU cpu = builder.buildAndRun(instructionCycles);

        assertEquals(expectedX, cpu.getState().getX());
        assertEquals(expectedZero, cpu.getState().isZero());
        assertEquals(expectedNegative, cpu.getState().isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 255, false, true",   // 0 - 1 wraps to 255 (0xFF); negative flag set (bit 7 = 1)
            "1, 0, true, false",     // 1 - 1 = 0; zero flag set
            "2, 1, false, false",    // 2 - 1 = 1; no flag set
            "128, 127, false, false",// 128 (0x80) - 1 = 127 (0x7F); negative flag clear
            "255, 254, false, true"  // 255 (0xFF) - 1 = 254 (0xFE); negative flag set
    })
    public void DEY(int initialY, int expectedY, boolean expectedZero, boolean expectedNegative) {
        final int instructionCycles = 2;
        final int DEY_OPCODE = 0x88;
        final int resetAddress = 0x8000;

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withRegisterY(initialY)
                .withInstruction(resetAddress, DEY_OPCODE);

        CPU cpu = builder.buildAndRun(instructionCycles);

        assertEquals(expectedY, cpu.getState().getY());
        assertEquals(expectedZero, cpu.getState().isZero());
        assertEquals(expectedNegative, cpu.getState().isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, false, false",      // 0 + 1 = 1, no flags set.
            "255, 0, true, false",      // 255 + 1 wraps to 0, zero flag set.
            "2, 3, false, false",       // 2 + 1 = 3.
            "127, 128, false, true",     // 127 + 1 = 128, negative flag set.
            "254, 255, false, true"      // 254 + 1 = 255, negative flag set.
    })
    public void INC_ZeroPage(int initialValue, int expectedValue, boolean expectedZero, boolean expectedNegative) {
        final int instructionCycles = 5;
        final int INC_OPCODE = 0xE6;
        final int effectiveAddress = 0x10;
        final int resetAddress = 0x8000;

        Bus bus = new MockBus();

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withMemoryValue(effectiveAddress, initialValue)
                .withInstruction(resetAddress, INC_OPCODE, effectiveAddress);

        CPU cpu = builder.buildAndRun(instructionCycles, bus);

        assertEquals(expectedValue, bus.read(effectiveAddress));
        assertEquals(expectedZero, cpu.getState().isZero());
        assertEquals(expectedNegative, cpu.getState().isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, false, false",      // 0 + 1 = 1, no flags set.
            "255, 0, true, false",      // 255 + 1 wraps to 0, zero flag set.
            "2, 3, false, false",       // 2 + 1 = 3.
            "127, 128, false, true",     // 127 + 1 = 128, negative flag set.
            "254, 255, false, true"      // 254 + 1 = 255, negative flag set.
    })
    public void INC_ZeroPageX(int initialValue, int expectedValue, boolean expectedZero, boolean expectedNegative) {
        final int instructionCycles = 6;
        final int INC_OPCODE = 0xF6;
        final int baseAddress = 0x10;
        final int resetAddress = 0x8000;
        final int registerX = 0x05;

        int effectiveAddress = (baseAddress + registerX) & 0xFF;

        Bus bus = new MockBus();

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withRegisterX(registerX)
                .withMemoryValue(effectiveAddress, initialValue)
                .withInstruction(resetAddress, INC_OPCODE, baseAddress);

        CPU cpu = builder.buildAndRun(instructionCycles, bus);

        assertEquals(expectedValue, bus.read(effectiveAddress));
        assertEquals(expectedZero, cpu.getState().isZero());
        assertEquals(expectedNegative, cpu.getState().isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, false, false",      // 0 + 1 = 1, no flags set.
            "255, 0, true, false",      // 255 + 1 wraps to 0, zero flag set.
            "2, 3, false, false",       // 2 + 1 = 3.
            "127, 128, false, true",     // 127 + 1 = 128, negative flag set.
            "254, 255, false, true"      // 254 + 1 = 255, negative flag set.
    })
    public void INC_Absolute(int initialValue, int expectedValue, boolean expectedZero, boolean expectedNegative) {
        final int instructionCycles = 6;
        final int INC_OPCODE = 0xEE;
        final int effectiveAddress = 0x1234;
        final int resetAddress = 0x8000;

        Bus bus = new MockBus();

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withMemoryValue(effectiveAddress, initialValue)
                .withInstruction(resetAddress, INC_OPCODE, effectiveAddress & 0xFF, (effectiveAddress >> 8) & 0xFF);

        CPU cpu = builder.buildAndRun(instructionCycles, bus);

        assertEquals(expectedValue, bus.read(effectiveAddress));
        assertEquals(expectedZero, cpu.getState().isZero());
        assertEquals(expectedNegative, cpu.getState().isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, false, false",      // 0 + 1 = 1, no flags set.
            "255, 0, true, false",      // 255 + 1 wraps to 0, zero flag set.
            "2, 3, false, false",       // 2 + 1 = 3.
            "127, 128, false, true",     // 127 + 1 = 128, negative flag set.
            "254, 255, false, true"      // 254 + 1 = 255, negative flag set.
    })
    public void INC_AbsoluteX(int initialValue, int expectedValue, boolean expectedZero, boolean expectedNegative) {
        final int instructionCycles = 7;
        final int INC_OPCODE = 0xFE;
        final int baseAddress = 0x1234;
        final int resetAddress = 0x8000;
        final int registerX = 0x10;

        int effectiveAddress = baseAddress + registerX;

        Bus bus = new MockBus();

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withRegisterX(registerX)
                .withMemoryValue(effectiveAddress, initialValue)
                .withInstruction(resetAddress, INC_OPCODE,
                        baseAddress & 0xFF, (baseAddress >> 8) & 0xFF);

        CPU cpu = builder.buildAndRun(instructionCycles, bus);

        assertEquals(expectedValue, bus.read(effectiveAddress));
        assertEquals(expectedZero, cpu.getState().isZero());
        assertEquals(expectedNegative, cpu.getState().isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, false, false",     // 0 + 1 = 1, zero false, negative false.
            "255, 0, true, false",     // 255 + 1 wraps to 0, zero true.
            "2, 3, false, false",      // 2 + 1 = 3.
            "127, 128, false, true",    // 127 + 1 = 128 (0x80), negative flag set.
            "254, 255, false, true"     // 254 + 1 = 255, negative flag set.
    })
    public void INX(int initialX, int expectedX, boolean expectedZero, boolean expectedNegative) {
        final int instructionCycles = 2;
        final int INX_OPCODE = 0xE8;
        final int resetAddress = 0x8000;

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withRegisterX(initialX)
                .withInstruction(resetAddress, INX_OPCODE);

        CPU cpu = builder.buildAndRun(instructionCycles);

        assertEquals(expectedX, cpu.getState().getX());
        assertEquals(expectedZero, cpu.getState().isZero());
        assertEquals(expectedNegative, cpu.getState().isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, false, false",     // 0 + 1 = 1, zero false, negative false.
            "255, 0, true, false",     // 255 + 1 wraps to 0, zero true.
            "2, 3, false, false",      // 2 + 1 = 3.
            "127, 128, false, true",    // 127 + 1 = 128 (0x80), negative flag set.
            "254, 255, false, true"     // 254 + 1 = 255, negative flag set.
    })
    public void INY_Parameterized(int initialY, int expectedY, boolean expectedZero, boolean expectedNegative) {
        final int instructionCycles = 2;
        final int INY_OPCODE = 0xC8;
        final int resetAddress = 0x8000;

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withRegisterY(initialY)
                .withInstruction(resetAddress, INY_OPCODE);

        CPU cpu = builder.buildAndRun(instructionCycles);

        assertEquals(expectedY, cpu.getState().getY());
        assertEquals(expectedZero, cpu.getState().isZero());
        assertEquals(expectedNegative, cpu.getState().isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, true, false",
            "15, 240, 255, false, true",
            "128, 127, 255, false, true",
            "32, 16, 48, false, false"
    })
    public void ORA_Immediate(int initialA, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x09;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withInstruction(0x8000, opcode, operand)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, true, false",
            "15, 240, 255, false, true",
            "128, 127, 255, false, true",
            "32, 16, 48, false, false"
    })
    public void ORA_ZeroPage(int initialA, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x05;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withInstruction(0x8000, opcode, 0x10)
                .withMemoryValue(0x0010, operand)
                .buildAndRun(3);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, 0, 0, true, false",
            "15, 5, 240, 255, false, true",
            "128, 5, 127, 255, false, true",
            "32, 5, 16, 48, false, false"
    })
    public void ORA_ZeroPageX(int initialA, int regX, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x15;
        int baseAddress = 0x10;
        int effectiveAddress = (baseAddress + regX) & 0xFF;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress)
                .withMemoryValue(effectiveAddress, operand)
                .buildAndRun(4);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, true, false",
            "15, 240, 255, false, true",
            "128, 127, 255, false, true",
            "32, 16, 48, false, false"
    })
    public void ORA_Absolute(int initialA, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x0D;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withInstruction(0x8000, opcode, 0x00, 0x90)  // Address 0x9000
                .withMemoryValue(0x9000, operand)
                .buildAndRun(4);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            // initialA, regX, operand, expectedResult, expectedZero, expectedNegative
            "0, 5, 0, 0, true, false",
            "15, 5, 240, 255, false, true",
            "128, 5, 127, 255, false, true",
            "32, 5, 16, 48, false, false"
    })
    public void ORA_AbsoluteX_NoPageCrossing(int initialA, int regX, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x1D;
        int baseAddress = 0x9000;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regX, operand)
                .buildAndRun(4);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, 0, 0, true, false",
            "15, 1, 240, 255, false, true",
            "128, 1, 127, 255, false, true",
            "32, 1, 16, 48, false, false"
    })
    public void ORA_AbsoluteX_PageCrossing(int initialA, int regX, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x1D;
        int baseAddress = 0x90FF; // With regX = 1, effective address = 0x90FF + 1 = 0x9100 (page crossing)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regX, operand)
                .buildAndRun(5);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, 0, 0, true, false",
            "15, 5, 240, 255, false, true",
            "128, 5, 127, 255, false, true",
            "32, 5, 16, 48, false, false"
    })
    public void ORA_AbsoluteY_NoPageCrossing(int initialA, int regY, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x19;
        int baseAddress = 0x9000;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regY, operand)
                .buildAndRun(4);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, 0, 0, true, false",
            "15, 1, 240, 255, false, true",
            "128, 1, 127, 255, false, true",
            "32, 1, 16, 48, false, false"
    })
    public void ORA_AbsoluteY_PageCrossing(int initialA, int regY, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x19;
        int baseAddress = 0x90FF;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regY, operand)
                .buildAndRun(5);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            // initialA, regX, instrOperand, pointerLow, pointerHigh, memOperand, expectedResult, expectedZero, expectedNegative
            "0, 5, 16, 0, 144, 0, 0, true, false",
            "15, 5, 16, 0, 144, 240, 255, false, true",
            "128, 5, 16, 0, 144, 127, 255, false, true",
            "32, 5, 16, 0, 144, 16, 48, false, false"
    })
    public void ORA_IndirectX(int initialA, int regX, int instrOperand, int pointerLow, int pointerHigh, int memOperand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x01;
        int zpAddr = (instrOperand + regX) & 0xFF;
        int effectiveAddress = (pointerHigh << 8) | pointerLow;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, instrOperand)
                .withZeroPagePointer(zpAddr, pointerLow, pointerHigh)
                .withMemoryValue(effectiveAddress, memOperand)
                .buildAndRun(6);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, 0, 144, 0, 0, true, false", // Pointer at 0x9000, effective address = 0x9000+5 = 0x9005
            "15, 5, 0, 144, 240, 255, false, true",
            "128, 5, 0, 144, 127, 255, false, true",
            "32, 5, 0, 144, 16, 48, false, false"
    })
    public void ORA_IndirectY_NoPageCrossing(int initialA, int regY, int pointerLow, int pointerHigh, int memOperand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x11;
        int instrOperand = 0x20;
        int baseAddress = (pointerHigh << 8) | pointerLow;
        int effectiveAddress = baseAddress + regY; // Should be 0x9000 + 5 = 0x9005, no page crossing
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, instrOperand)
                .withZeroPagePointer(instrOperand, pointerLow, pointerHigh)
                .withMemoryValue(effectiveAddress, memOperand)
                .buildAndRun(5);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, 255, 144, 0, 0, true, false",      // Pointer at 0x90FF, effective address = 0x90FF+1 = 0x9100 (page crossing)
            "15, 1, 255, 144, 240, 255, false, true",
            "128, 1, 255, 144, 127, 255, false, true",
            "32, 1, 255, 144, 16, 48, false, false"
    })
    public void ORA_IndirectY_PageCrossing(int initialA, int regY, int pointerLow, int pointerHigh, int memOperand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x11;
        int instrOperand = 0x20;
        int baseAddress = (pointerHigh << 8) | pointerLow;
        int effectiveAddress = baseAddress + regY; // 0x90FF + 1 = 0x9100 (page crossing)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, instrOperand)
                .withZeroPagePointer(instrOperand, pointerLow, pointerHigh)
                .withMemoryValue(effectiveAddress, memOperand)
                .buildAndRun(6);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, true, false",
            "15, 240, 255, false, true",
            "128, 127, 255, false, true",
            "32, 16, 48, false, false"
    })
    public void EOR_Immediate(int initialA, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x49;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withInstruction(0x8000, opcode, operand)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, true, false",
            "15, 240, 255, false, true",
            "128, 127, 255, false, true",
            "32, 16, 48, false, false"
    })
    public void EOR_ZeroPage(int initialA, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x45;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withInstruction(0x8000, opcode, 0x10)
                .withMemoryValue(0x0010, operand)
                .buildAndRun(3);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, 0, 0, true, false",
            "15, 5, 240, 255, false, true",
            "128, 5, 127, 255, false, true",
            "32, 5, 16, 48, false, false"
    })
    public void EOR_ZeroPageX(int initialA, int regX, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x55;
        int baseAddress = 0x10;
        int effectiveAddress = (baseAddress + regX) & 0xFF;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress)
                .withMemoryValue(effectiveAddress, operand)
                .buildAndRun(4);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, true, false",
            "15, 240, 255, false, true",
            "128, 127, 255, false, true",
            "32, 16, 48, false, false"
    })
    public void EOR_Absolute(int initialA, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x4D;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withInstruction(0x8000, opcode, 0x00, 0x90)
                .withMemoryValue(0x9000, operand)
                .buildAndRun(4);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, 0, 0, true, false",
            "15, 5, 240, 255, false, true",
            "128, 5, 127, 255, false, true",
            "32, 5, 16, 48, false, false"
    })
    public void EOR_AbsoluteX_NoPageCrossing(int initialA, int regX, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x5D;
        int baseAddress = 0x9000; // No page crossing: 0x9000 + regX remains in page 0x90
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regX, operand)
                .buildAndRun(4);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, 0, 0, true, false",
            "15, 1, 240, 255, false, true",
            "128, 1, 127, 255, false, true",
            "32, 1, 16, 48, false, false"
    })
    public void EOR_AbsoluteX_PageCrossing(int initialA, int regX, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x5D;
        int baseAddress = 0x90FF; // With regX = 1, effective address = 0x90FF + 1 = 0x9100 (page crossing)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regX, operand)
                .buildAndRun(5);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, 0, true, false",
            "15, 5, 240, false, true",
            "128, 5, 127, false, true",
            "32, 5, 16, false, false"
    })
    public void EOR_AbsoluteY_NoPageCrossing(int initialA, int regY, int operand, boolean expectedZero, boolean expectedNegative) {
        int expected = initialA ^ operand;
        final int opcode = 0x59;
        int baseAddress = 0x9000;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regY, operand)
                .buildAndRun(4);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, 0, true, false",
            "15, 1, 240, false, true",
            "128, 1, 127, false, true",
            "32, 1, 16, false, false"
    })
    public void EOR_AbsoluteY_PageCrossing(int initialA, int regY, int operand, boolean expectedZero, boolean expectedNegative) {
        int expected = initialA ^ operand;
        final int opcode = 0x59;
        int baseAddress = 0x90FF;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regY, operand)
                .buildAndRun(5);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            // initialA, regX, instrOperand, pointerLow, pointerHigh, memOperand, expectedResult, expectedZero, expectedNegative
            "0, 5, 16, 0, 144, 0, 0, true, false",
            "15, 5, 16, 0, 144, 240, 255, false, true",
            "128, 5, 16, 0, 144, 127, 255, false, true",
            "32, 5, 16, 0, 144, 16, 48, false, false"
    })
    public void EOR_IndirectX(int initialA, int regX, int instrOperand, int pointerLow, int pointerHigh, int memOperand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x41;
        int zpAddr = (instrOperand + regX) & 0xFF;
        int effectiveAddress = (pointerHigh << 8) | pointerLow;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, instrOperand)
                .withZeroPagePointer(zpAddr, pointerLow, pointerHigh)
                .withMemoryValue(effectiveAddress, memOperand)
                .buildAndRun(6);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            // initialA, regY, pointerLow, pointerHigh, memOperand, expectedResult, expectedZero, expectedNegative
            "0, 5, 144, 0, 0, 0, true, false",   // Pointer at 0x9000, effective address = 0x9000+5
            "15, 5, 144, 0, 240, 255, false, true",
            "128, 5, 144, 0, 127, 255, false, true",
            "32, 5, 144, 0, 16, 48, false, false"
    })
    public void EOR_IndirectY_NoPageCrossing(int initialA, int regY, int pointerLow, int pointerHigh, int memOperand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x51;
        int instrOperand = 0x20;
        int baseAddress = (pointerHigh << 8) | pointerLow;
        int effectiveAddress = baseAddress + regY;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, instrOperand)
                .withZeroPagePointer(instrOperand, pointerLow, pointerHigh)
                .withMemoryValue(effectiveAddress, memOperand)
                .buildAndRun(5);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, 255, 144, 0, 0, true, false",      // Pointer at 0x90FF, effective address = 0x90FF+1 (page crossing)
            "15, 1, 255, 144, 240, 255, false, true",
            "128, 1, 255, 144, 127, 255, false, true",
            "32, 1, 255, 144, 16, 48, false, false"
    })
    public void EOR_IndirectY_PageCrossing(int initialA, int regY, int pointerLow, int pointerHigh, int memOperand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x51;
        int instrOperand = 0x20;
        int baseAddress = (pointerHigh << 8) | pointerLow;
        int effectiveAddress = baseAddress + regY;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, instrOperand)
                .withZeroPagePointer(instrOperand, pointerLow, pointerHigh)
                .withMemoryValue(effectiveAddress, memOperand)
                .buildAndRun(6);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, true, false",
            "255, 170, 170, false, true",  // 0xFF & 0xAA = 0xAA (170)
            "170, 85, 0, true, false",      // 0xAA & 0x55 = 0
            "200, 170, 136, false, true"     // 200 & 170 = 136
    })
    public void AND_Immediate(int initialA, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x29;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withInstruction(0x8000, opcode, operand)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, true, false",
            "255, 170, 170, false, true",
            "170, 85, 0, true, false",
            "200, 170, 136, false, true"
    })
    public void AND_ZeroPage(int initialA, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x25;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withInstruction(0x8000, opcode, 0x10)
                .withMemoryValue(0x0010, operand)
                .buildAndRun(3);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, 0, 0, true, false",
            "255, 5, 170, 170, false, true",
            "170, 5, 85, 0, true, false",
            "200, 5, 170, 136, false, true"
    })
    public void AND_ZeroPageX(int initialA, int regX, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x35;
        int baseAddress = 0x10;
        int effectiveAddress = (baseAddress + regX) & 0xFF;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress)
                .withMemoryValue(effectiveAddress, operand)
                .buildAndRun(4);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, true, false",
            "255, 170, 170, false, true",
            "170, 85, 0, true, false",
            "200, 170, 136, false, true"
    })
    public void AND_Absolute(int initialA, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x2D;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withInstruction(0x8000, opcode, 0x00, 0x90)  // Address 0x9000
                .withMemoryValue(0x9000, operand)
                .buildAndRun(4);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, 0, 0, true, false",
            "255, 5, 170, 170, false, true",
            "170, 5, 85, 0, true, false",
            "200, 5, 170, 136, false, true"
    })
    public void AND_AbsoluteX_NoPageCrossing(int initialA, int regX, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x3D;
        int baseAddress = 0x9000; // No page crossing: 0x9000 + regX remains in page 0x90
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regX, operand)
                .buildAndRun(4);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, 0, 0, true, false",
            "255, 1, 170, 170, false, true",
            "170, 1, 85, 0, true, false",
            "200, 1, 170, 136, false, true"
    })
    public void AND_AbsoluteX_PageCrossing(int initialA, int regX, int operand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x3D;
        int baseAddress = 0x90FF; // With regX = 1, effective address = 0x90FF + 1 = 0x9100 (page crossing)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regX, operand)
                .buildAndRun(5);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, 0, true, false",
            "255, 5, 170, false, true",
            "170, 5, 85, true, false",
            "200, 5, 170, false, true"
    })
    public void AND_AbsoluteY_NoPageCrossing(int initialA, int regY, int operand, boolean expectedZero, boolean expectedNegative) {
        int expected = initialA & operand;
        final int opcode = 0x39;
        int baseAddress = 0x9000; // With regY = 5, effective address = 0x9000 + 5, no page crossing
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regY, operand)
                .buildAndRun(4);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, 0, true, false",
            "255, 1, 170, false, true",
            "170, 1, 85, true, false",
            "200, 1, 170, false, true"
    })
    public void AND_AbsoluteY_PageCrossing(int initialA, int regY, int operand, boolean expectedZero, boolean expectedNegative) {
        int expected = initialA & operand;
        final int opcode = 0x39;
        int baseAddress = 0x90FF; // With regY = 1, effective address = 0x90FF + 1 = 0x9100 (page crossing)
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regY, operand)
                .buildAndRun(5);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            // initialA, regX, instrOperand, pointerLow, pointerHigh, memOperand, expected, expectedZero, expectedNegative
            "0, 5, 16, 0, 144, 0, 0, true, false",
            "255, 5, 16, 0, 144, 170, 170, false, true",
            "170, 5, 16, 0, 144, 85, 0, true, false",
            "200, 5, 16, 0, 144, 170, 136, false, true"
    })
    public void AND_IndirectX(int initialA, int regX, int instrOperand, int pointerLow, int pointerHigh, int memOperand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x21;
        int zpAddr = (instrOperand + regX) & 0xFF;
        int effectiveAddress = (pointerHigh << 8) | pointerLow;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, instrOperand)
                .withZeroPagePointer(zpAddr, pointerLow, pointerHigh)
                .withMemoryValue(effectiveAddress, memOperand)
                .buildAndRun(6);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            // initialA, regY, pointerLow, pointerHigh, memOperand, expected, expectedZero, expectedNegative
            "0, 5, 144, 0, 0, 0, true, false",   // Pointer at 0x9000, effective address = 0x9000+5
            "255, 5, 144, 0, 170, 170, false, true",
            "170, 5, 144, 0, 85, 0, true, false",
            "200, 5, 144, 0, 170, 136, false, true"
    })
    public void AND_IndirectY_NoPageCrossing(int initialA, int regY, int pointerLow, int pointerHigh, int memOperand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x31; // AND (Indirect),Y opcode
        int instrOperand = 0x20;
        int baseAddress = (pointerHigh << 8) | pointerLow;
        int effectiveAddress = baseAddress + regY;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, instrOperand)
                .withZeroPagePointer(instrOperand, pointerLow, pointerHigh)
                .withMemoryValue(effectiveAddress, memOperand)
                .buildAndRun(5);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1, 255, 144, 0, 0, true, false",      // Pointer at 0x90FF, effective address = 0x90FF+1 (page crossing)
            "255, 1, 255, 144, 170, 170, false, true",
            "170, 1, 255, 144, 85, 0, true, false",
            "200, 1, 255, 144, 170, 136, false, true"
    })
    public void AND_IndirectY_PageCrossing(int initialA, int regY, int pointerLow, int pointerHigh, int memOperand, int expected, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0x31;
        int instrOperand = 0x20;
        int baseAddress = (pointerHigh << 8) | pointerLow;
        int effectiveAddress = baseAddress + regY;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, instrOperand)
                .withZeroPagePointer(instrOperand, pointerLow, pointerHigh)
                .withMemoryValue(effectiveAddress, memOperand)
                .buildAndRun(6);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, true, false, false",
            "1, 2, false, false, false",
            "128, 0, true, false, true",  // 0x80 << 1 produces 0 with carry set
            "64, 128, false, true, false",
            "85, 170, false, true, false"
    })
    public void ASL_Accumulator(int initialA, int expected, boolean expectedZero, boolean expectedNegative, boolean expectedCarry) {
        final int opcode = 0x0A;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withInstruction(0x8000, opcode)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, true, false, false",
            "1, 2, false, false, false",
            "128, 0, true, false, true",
            "64, 128, false, true, false",
            "85, 170, false, true, false"
    })
    public void ASL_ZeroPage(int initial, int expected, boolean expectedZero, boolean expectedNegative, boolean expectedCarry) {
        final int opcode = 0x06;
        int address = 0x10;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, opcode, address)
                .withMemoryValue(address, initial)
                .buildAndRun(5, bus);
        CpuState state = cpu.getState();
        assertEquals(expected, bus.read(address));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            // initial, regX, expected, expectedZero, expectedNegative, expectedCarry
            "0, 5, 0, true, false, false",
            "1, 5, 2, false, false, false",
            "128, 5, 0, true, false, true",
            "64, 5, 128, false, true, false",
            "85, 5, 170, false, true, false"
    })
    public void ASL_ZeroPageX(int initial, int regX, int expected, boolean expectedZero, boolean expectedNegative, boolean expectedCarry) {
        final int opcode = 0x16;
        int baseAddress = 0x10;
        int effectiveAddress = (baseAddress + regX) & 0xFF;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress)
                .withMemoryValue(effectiveAddress, initial)
                .buildAndRun(6, bus);
        CpuState state = cpu.getState();
        assertEquals(expected, bus.read(effectiveAddress));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, true, false, false",
            "1, 2, false, false, false",
            "128, 0, true, false, true",
            "64, 128, false, true, false",
            "85, 170, false, true, false"
    })
    public void ASL_Absolute(int initial, int expected, boolean expectedZero, boolean expectedNegative, boolean expectedCarry) {
        final int opcode = 0x0E;
        int address = 0x9000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, opcode, address & 0xFF, (address >> 8) & 0xFF)
                .withMemoryValue(address, initial)
                .buildAndRun(6, bus);
        CpuState state = cpu.getState();
        assertEquals(expected, bus.read(address));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, 0, true, false, false",
            "1, 5, 2, false, false, false",
            "128, 5, 0, true, false, true",
            "64, 5, 128, false, true, false",
            "85, 5, 170, false, true, false",
            "0, 1, 0, true, false, false",
            "1, 1, 2, false, false, false",
            "128, 1, 0, true, false, true",
            "64, 1, 128, false, true, false",
            "85, 1, 170, false, true, false"
    })
    public void ASL_AbsoluteX(int initial, int regX, int expected, boolean expectedZero, boolean expectedNegative, boolean expectedCarry) {
        final int opcode = 0x1E;
        int baseAddress = (regX == 1) ? 0x90FF : 0x9000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regX, initial)
                .buildAndRun(7, bus);
        CpuState state = cpu.getState();
        assertEquals(expected, bus.read(baseAddress + regX));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, true, false, false",
            "1, 0, true, false, true",   // 1 (00000001) >> 1 = 0, carry=1
            "2, 1, false, false, false", // 2 (00000010) >> 1 = 1, carry=0
            "85, 42, false, false, true", // 85 (01010101) >> 1 = 42, carry=1
            "170, 85, false, false, false"// 170 (10101010) >> 1 = 85, carry=0
    })
    public void LSR_Accumulator(int initialA, int expected, boolean expectedZero, boolean expectedNegative, boolean expectedCarry) {
        final int opcode = 0x4A;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withInstruction(0x8000, opcode)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, true, false, false",
            "1, 0, true, false, true",
            "2, 1, false, false, false",
            "85, 42, false, false, true",
            "170, 85, false, false, false"
    })
    public void LSR_ZeroPage(int initial, int expected, boolean expectedZero, boolean expectedNegative, boolean expectedCarry) {
        final int opcode = 0x46;
        int address = 0x10;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, opcode, address)
                .withMemoryValue(address, initial)
                .buildAndRun(5, bus);
        CpuState state = cpu.getState();
        assertEquals(expected, bus.read(address));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, 0, true, false, false",
            "1, 5, 0, true, false, true",
            "2, 5, 1, false, false, false",
            "85, 5, 42, false, false, true",
            "170, 5, 85, false, false, false"
    })
    public void LSR_ZeroPageX(int initial, int regX, int expected, boolean expectedZero, boolean expectedNegative, boolean expectedCarry) {
        final int opcode = 0x56;
        int baseAddress = 0x10;
        int effectiveAddress = (baseAddress + regX) & 0xFF;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress)
                .withMemoryValue(effectiveAddress, initial)
                .buildAndRun(6, bus);
        CpuState state = cpu.getState();
        assertEquals(expected, bus.read(effectiveAddress));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, true, false, false",
            "1, 0, true, false, true",
            "2, 1, false, false, false",
            "85, 42, false, false, true",
            "170, 85, false, false, false"
    })
    public void LSR_Absolute(int initial, int expected, boolean expectedZero, boolean expectedNegative, boolean expectedCarry) {
        final int opcode = 0x4E;
        int address = 0x9000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withInstruction(0x8000, opcode, address & 0xFF, (address >> 8) & 0xFF)
                .withMemoryValue(address, initial)
                .buildAndRun(6, bus);
        CpuState state = cpu.getState();
        assertEquals(expected, bus.read(address));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, 0, true, false, false",
            "1, 5, 0, true, false, true",
            "2, 5, 1, false, false, false",
            "85, 5, 42, false, false, true",
            "170, 5, 85, false, false, false",
            "0, 1, 0, true, false, false",
            "1, 1, 0, true, false, true",
            "2, 1, 1, false, false, false",
            "85, 1, 42, false, false, true",
            "170, 1, 85, false, false, false"
    })
    public void LSR_AbsoluteX(int initial, int regX, int expected, boolean expectedZero, boolean expectedNegative, boolean expectedCarry) {
        final int opcode = 0x5E;
        int baseAddress = 0x9000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regX, initial)
                .buildAndRun(7, bus);
        CpuState state = cpu.getState();
        assertEquals(expected, bus.read(baseAddress + regX));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, false, 0, true, false, false",
            "0, true, 1, false, false, false",
            "1, false, 2, false, false, false",
            "1, true, 3, false, false, false",
            "128, false, 0, true, false, true",   // 0x80 -> result=0, carry=1
            "128, true, 1, false, false, true",
            "85, false, 170, false, true, false",  // 0x55 -> 0xAA
            "85, true, 171, false, true, false",   // 0x55 -> 0xAB
            "255, false, 254, false, true, true",  // 0xFF -> 0xFE, carry=1
            "255, true, 255, false, true, true"
    })
    public void ROL_Accumulator(int initialA, boolean initialCarry, int expected,
                                boolean expectedZero, boolean expectedNegative, boolean expectedNewCarry) {
        final int opcode = 0x2A;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode)
                .buildAndRun(2);
        CpuState state = cpu.getState();

        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedNewCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, false, 0, true, false, false",
            "0, true, 1, false, false, false",
            "1, false, 2, false, false, false",
            "1, true, 3, false, false, false",
            "128, false, 0, true, false, true",
            "128, true, 1, false, false, true",
            "85, false, 170, false, true, false",
            "85, true, 171, false, true, false"
    })
    public void ROL_ZeroPage(int initial, boolean initialCarry, int expected,
                             boolean expectedZero, boolean expectedNegative, boolean expectedNewCarry) {
        final int opcode = 0x26;
        int address = 0x10;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, address)
                .withMemoryValue(address, initial)
                .buildAndRun(5, bus);
        CpuState state = cpu.getState();

        assertEquals(expected, bus.read(address));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedNewCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, false, 0, true, false, false",
            "0, 5, true, 1, false, false, false",
            "1, 5, false, 2, false, false, false",
            "1, 5, true, 3, false, false, false",
            "128, 5, false, 0, true, false, true",
            "128, 5, true, 1, false, false, true",
            "85, 5, false, 170, false, true, false",
            "85, 5, true, 171, false, true, false"
    })
    public void ROL_ZeroPageX(int initial, int regX, boolean initialCarry, int expected,
                              boolean expectedZero, boolean expectedNegative, boolean expectedNewCarry) {
        final int opcode = 0x36;
        int baseAddress = 0x10;
        int effectiveAddress = (baseAddress + regX) & 0xFF;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(regX)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, baseAddress)
                .withMemoryValue(effectiveAddress, initial)
                .buildAndRun(6, bus);
        CpuState state = cpu.getState();

        assertEquals(expected, bus.read(effectiveAddress));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedNewCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, false, 0, true, false, false",
            "0, true, 1, false, false, false",
            "1, false, 2, false, false, false",
            "1, true, 3, false, false, false",
            "128, false, 0, true, false, true",
            "128, true, 1, false, false, true",
            "85, false, 170, false, true, false",
            "85, true, 171, false, true, false"
    })
    public void ROL_Absolute(int initial, boolean initialCarry, int expected,
                             boolean expectedZero, boolean expectedNegative, boolean expectedNewCarry) {
        final int opcode = 0x2E;
        int address = 0x9000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, address & 0xFF, (address >> 8) & 0xFF)
                .withMemoryValue(address, initial)
                .buildAndRun(6, bus);
        CpuState state = cpu.getState();

        assertEquals(expected, bus.read(address));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedNewCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, false, 0, true, false, false",
            "0, 5, true, 1, false, false, false",
            "1, 5, false, 2, false, false, false",
            "1, 5, true, 3, false, false, false",
            "128, 5, false, 0, true, false, true",
            "128, 5, true, 1, false, false, true",
            "85, 5, false, 170, false, true, false",
            "85, 5, true, 171, false, true, false",
            "0, 1, false, 0, true, false, false",
            "0, 1, true, 1, false, false, false",
            "1, 1, false, 2, false, false, false",
            "1, 1, true, 3, false, false, false",
            "128, 1, false, 0, true, false, true",
            "128, 1, true, 1, false, false, true",
            "85, 1, false, 170, false, true, false",
            "85, 1, true, 171, false, true, false"
    })
    public void ROL_AbsoluteX(int initial, int regX, boolean initialCarry, int expected,
                              boolean expectedZero, boolean expectedNegative, boolean expectedNewCarry) {
        final int opcode = 0x3E;
        int baseAddress = (regX == 1) ? 0x90FF : 0x9000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(regX)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regX, initial)
                .buildAndRun(7, bus);
        CpuState state = cpu.getState();

        assertEquals(expected, bus.read(baseAddress + regX));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedNewCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, false, 0, true, false, false",
            "0, true, 128, false, true, false",
            "1, false, 0, true, false, true",   // 1 (00000001) >> 1 = 0, carry becomes true.
            "1, true, 128, false, true, true",    // 1 with carry -> result = 0x80, negative set.
            "128, false, 64, false, false, false",// 0x80 (10000000) >> 1 = 64 (01000000)
            "128, true, 192, false, true, false",  // 0x80 with carry -> 0xC0 (11000000)
            "85, false, 42, false, false, true",   // 0x55 (01010101) >> 1 = 0x2A (00101010), carry true.
            "85, true, 170, false, true, true",    // 0x55 with carry -> 0xAA (10101010)
            "255, false, 127, false, false, true", // 0xFF >> 1 = 127 (01111111), carry true.
            "255, true, 255, false, true, true"     // 0xFF with carry -> 0xFF, negative set.
    })
    public void ROR_Accumulator(int initialA, boolean initialCarry, int expected,
                                boolean expectedZero, boolean expectedNegative, boolean expectedNewCarry) {
        final int opcode = 0x6A;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertEquals(expected, state.getA());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedNewCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, false, 0, true, false, false",
            "0, true, 128, false, true, false",
            "1, false, 0, true, false, true",
            "1, true, 128, false, true, true",
            "128, false, 64, false, false, false",
            "128, true, 192, false, true, false",
            "85, false, 42, false, false, true",
            "85, true, 170, false, true, true",
            "255, false, 127, false, false, true",
            "255, true, 255, false, true, true"
    })
    public void ROR_ZeroPage(int initial, boolean initialCarry, int expected,
                             boolean expectedZero, boolean expectedNegative, boolean expectedNewCarry) {
        final int opcode = 0x66;
        int address = 0x10;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, address)
                .withMemoryValue(address, initial)
                .buildAndRun(5, bus);
        CpuState state = cpu.getState();
        assertEquals(expected, bus.read(address));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedNewCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, false, 0, true, false, false",
            "0, 5, true, 128, false, true, false",
            "1, 5, false, 0, true, false, true",
            "1, 5, true, 128, false, true, true",
            "128, 5, false, 64, false, false, false",
            "128, 5, true, 192, false, true, false",
            "85, 5, false, 42, false, false, true",
            "85, 5, true, 170, false, true, true",
            "255, 5, false, 127, false, false, true",
            "255, 5, true, 255, false, true, true"
    })
    public void ROR_ZeroPageX(int initial, int regX, boolean initialCarry, int expected,
                              boolean expectedZero, boolean expectedNegative, boolean expectedNewCarry) {
        final int opcode = 0x76;
        int baseAddress = 0x10;
        int effectiveAddress = (baseAddress + regX) & 0xFF;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(regX)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, baseAddress)
                .withMemoryValue(effectiveAddress, initial)
                .buildAndRun(6, bus);
        CpuState state = cpu.getState();
        assertEquals(expected, bus.read(effectiveAddress));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedNewCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, false, 0, true, false, false",
            "0, true, 128, false, true, false",
            "1, false, 0, true, false, true",
            "1, true, 128, false, true, true",
            "128, false, 64, false, false, false",
            "128, true, 192, false, true, false",
            "85, false, 42, false, false, true",
            "85, true, 170, false, true, true",
            "255, false, 127, false, false, true",
            "255, true, 255, false, true, true"
    })
    public void ROR_Absolute(int initial, boolean initialCarry, int expected,
                             boolean expectedZero, boolean expectedNegative, boolean expectedNewCarry) {
        final int opcode = 0x6E;
        int address = 0x9000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, address & 0xFF, (address >> 8) & 0xFF)
                .withMemoryValue(address, initial)
                .buildAndRun(6, bus);
        CpuState state = cpu.getState();
        assertEquals(expected, bus.read(address));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedNewCarry, state.isCarry());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 5, false, 0, true, false, false",
            "0, 5, true, 128, false, true, false",
            "1, 5, false, 0, true, false, true",
            "1, 5, true, 128, false, true, true",
            "128, 5, false, 64, false, false, false",
            "128, 5, true, 192, false, true, false",
            "85, 5, false, 42, false, false, true",
            "85, 5, true, 170, false, true, true",
            "255, 5, false, 127, false, false, true",
            "255, 5, true, 255, false, true, true",
            "0, 1, false, 0, true, false, false",
            "0, 1, true, 128, false, true, false",
            "1, 1, false, 0, true, false, true",
            "1, 1, true, 128, false, true, true",
            "128, 1, false, 64, false, false, false",
            "128, 1, true, 192, false, true, false",
            "85, 1, false, 42, false, false, true",
            "85, 1, true, 170, false, true, true",
            "255, 1, false, 127, false, false, true",
            "255, 1, true, 255, false, true, true"
    })
    public void ROR_AbsoluteX(int initial, int regX, boolean initialCarry, int expected,
                              boolean expectedZero, boolean expectedNegative, boolean expectedNewCarry) {
        final int opcode = 0x7E;
        int baseAddress = (regX == 1) ? 0x90FF : 0x9000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(regX)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regX, initial)
                .buildAndRun(7, bus);
        CpuState state = cpu.getState();
        assertEquals(expected, bus.read(baseAddress + regX));
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedNewCarry, state.isCarry());
    }

    @Test
    public void CLC_ClearsCarryFlag() {
        final int opcode = 0x18;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagCarry(true)
                .withInstruction(0x8000, opcode)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertFalse(state.isCarry());
    }

    @Test
    public void CLD_ClearsDecimalFlag() {
        final int opcode = 0xD8;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagDecimal(true)
                .withInstruction(0x8000, opcode)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertFalse(state.isDecimal());
    }

    @Test
    public void CLI_ClearsInterruptDisableFlag() {
        final int opcode = 0x58;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagInterruptDisable(true)
                .withInstruction(0x8000, opcode)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertFalse(state.isInterruptDisable());
    }

    @Test
    public void CLV_ClearsOverflowFlag() {
        final int opcode = 0xB8;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagOverflow(true)
                .withInstruction(0x8000, opcode)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertFalse(state.isOverflow());
    }

    @Test
    public void SEC_SetsCarryFlag() {
        final int opcode = 0x38;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagCarry(false)
                .withInstruction(0x8000, opcode)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertTrue(state.isCarry());
    }

    @Test
    public void SED_SetsDecimalFlag() {
        final int opcode = 0xF8;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagDecimal(false)
                .withInstruction(0x8000, opcode)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertTrue(state.isDecimal());
    }

    @Test
    public void SEI_SetsInterruptDisableFlag() {
        final int opcode = 0x78;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagInterruptDisable(false)
                .withInstruction(0x8000, opcode)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertTrue(state.isInterruptDisable());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, true, true, false",   // A==M: Zero set, Carry set
            "50, 40, true, false, false",   // A > M: Carry set, result positive
            "40, 50, false, false, true",   // A < M: Carry clear, result negative
            "255, 0, true, false, true",    // 255 >= 0: Carry set; 255-0 = 255 => negative (0xFF is -1)
            "0, 1, false, false, true"      // 0 < 1: Carry clear, negative
    })
    public void CMP_Immediate(int initialA, int operand,
                              boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xC9;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withInstruction(0x8000, opcode, operand)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertEquals(initialA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, true, true, false",
            "50, 40, true, false, false",
            "40, 50, false, false, true",
            "255, 0, true, false, true",
            "0, 1, false, false, true"
    })
    public void CMP_ZeroPage(int initialA, int operand,
                             boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xC5;
        int address = 0x10;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withInstruction(0x8000, opcode, address)
                .withMemoryValue(address, operand)
                .buildAndRun(3, bus);
        CpuState state = cpu.getState();
        assertEquals(initialA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, 5, true, true, false",
            "50, 40, 5, true, false, false",
            "40, 50, 5, false, false, true",
            "255, 0, 5, true, false, true",
            "0, 1, 5, false, false, true"
    })
    public void CMP_ZeroPageX(int initialA, int operand, int regX,
                              boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xD5;
        int baseAddress = 0x10;
        int effectiveAddress = (baseAddress + regX) & 0xFF;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress)
                .withMemoryValue(effectiveAddress, operand)
                .buildAndRun(4, bus);
        CpuState state = cpu.getState();
        assertEquals(initialA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, true, true, false",
            "50, 40, true, false, false",
            "40, 50, false, false, true",
            "255, 0, true, false, true",
            "0, 1, false, false, true"
    })
    public void CMP_Absolute(int initialA, int operand,
                             boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xCD;
        int address = 0x9000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withInstruction(0x8000, opcode, address & 0xFF, (address >> 8) & 0xFF)
                .withMemoryValue(address, operand)
                .buildAndRun(4, bus);
        CpuState state = cpu.getState();
        assertEquals(initialA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, 5, true, true, false",
            "50, 40, 5, true, false, false",
            "40, 50, 5, false, false, true",
            "255, 0, 5, true, false, true",
            "0, 1, 5, false, false, true"
    })
    public void CMP_AbsoluteX_NoPageCrossing(int initialA, int operand, int regX,
                                             boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xDD;
        int baseAddress = 0x9000; // No page crossing: 0x9000 + regX remains in the same page.
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regX, operand)
                .buildAndRun(4, bus);
        CpuState state = cpu.getState();
        assertEquals(initialA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, 1, true, true, false",
            "50, 40, 1, true, false, false",
            "40, 50, 1, false, false, true",
            "255, 0, 1, true, false, true",
            "0, 1, 1, false, false, true"
    })
    public void CMP_AbsoluteX_PageCrossing(int initialA, int operand, int regX,
                                           boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xDD;
        int baseAddress = 0x90FF; // With regX=1, effective address = 0x90FF + 1 = 0x9100 (page crossing)
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regX, operand)
                .buildAndRun(5, bus);
        CpuState state = cpu.getState();
        assertEquals(initialA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, 5, true, true, false",
            "50, 40, 5, true, false, false",
            "40, 50, 5, false, false, true",
            "255, 0, 5, true, false, true",
            "0, 1, 5, false, false, true"
    })
    public void CMP_AbsoluteY_NoPageCrossing(int initialA, int operand, int regY,
                                             boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xD9;
        int baseAddress = 0x9000; // no page crossing: effective address = 0x9000 + regY
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regY, operand)
                .buildAndRun(4, bus);
        CpuState state = cpu.getState();
        assertEquals(initialA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, 1, true, true, false",
            "50, 40, 1, true, false, false",
            "40, 50, 1, false, false, true",
            "255, 0, 1, true, false, true",
            "0, 1, 1, false, false, true"
    })
    public void CMP_AbsoluteY_PageCrossing(int initialA, int operand, int regY,
                                           boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xD9;
        int baseAddress = 0x90FF; // effective address = 0x90FF + regY, page crossing when regY=1
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(baseAddress + regY, operand)
                .buildAndRun(5, bus);
        CpuState state = cpu.getState();
        assertEquals(initialA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, 5, true, true, false",
            "50, 40, 5, true, false, false",
            "40, 50, 5, false, false, true",
            "255, 0, 5, true, false, true",
            "0, 1, 5, false, false, true"
    })
    public void CMP_IndirectX(int initialA, int operand, int regX,
                              boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xC1;
        int instrOperand = 0x10;
        int zpAddr = (instrOperand + regX) & 0xFF;
        int targetAddress = 0x9000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(regX)
                .withInstruction(0x8000, opcode, instrOperand)
                .withZeroPagePointer(zpAddr, targetAddress & 0xFF, (targetAddress >> 8) & 0xFF)
                .withMemoryValue(targetAddress, operand)
                .buildAndRun(6, bus);
        CpuState state = cpu.getState();
        assertEquals(initialA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, 5, true, true, false",
            "50, 40, 5, true, false, false",
            "40, 50, 5, false, false, true",
            "255, 0, 5, true, false, true",
            "0, 1, 5, false, false, true"
    })
    public void CMP_IndirectY_NoPageCrossing(int initialA, int operand, int regY,
                                             boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xD1;
        int instrOperand = 0x20;
        int targetAddress = 0x9000;
        int effectiveAddress = targetAddress + regY;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, instrOperand)
                .withZeroPagePointer(instrOperand, targetAddress & 0xFF, (targetAddress >> 8) & 0xFF)
                .withMemoryValue(effectiveAddress, operand)
                .buildAndRun(5, bus);
        CpuState state = cpu.getState();
        assertEquals(initialA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, 1, true, true, false",
            "50, 40, 1, true, false, false",
            "40, 50, 1, false, false, true",
            "255, 0, 1, true, false, true",
            "0, 1, 1, false, false, true"
    })
    public void CMP_IndirectY_PageCrossing(int initialA, int operand, int regY,
                                           boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xD1;
        int instrOperand = 0x20;
        int targetAddress = 0x90FF;
        int effectiveAddress = targetAddress + regY;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(regY)
                .withInstruction(0x8000, opcode, instrOperand)
                .withZeroPagePointer(instrOperand, targetAddress & 0xFF, (targetAddress >> 8) & 0xFF)
                .withMemoryValue(effectiveAddress, operand)
                .buildAndRun(6, bus);
        CpuState state = cpu.getState();
        assertEquals(initialA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            // initialX, operand, expectedCarry, expectedZero, expectedNegative
            "50, 50, true, true, false",   // X == M
            "50, 40, true, false, false",   // X > M
            "40, 50, false, false, true",   // X < M
            "255, 0, true, false, true",    // 255 >= 0, result=255 (0xFF, negative)
            "0, 1, false, false, true"      // 0 < 1
    })
    public void CPX_Immediate(int initialX, int operand,
                              boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xE0;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(initialX)
                .withInstruction(0x8000, opcode, operand)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertEquals(initialX, state.getX());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, true, true, false",
            "50, 40, true, false, false",
            "40, 50, false, false, true",
            "255, 0, true, false, true",
            "0, 1, false, false, true"
    })
    public void CPX_ZeroPage(int initialX, int operand,
                             boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xE4;
        int address = 0x10;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(initialX)
                .withInstruction(0x8000, opcode, address)
                .withMemoryValue(address, operand)
                .buildAndRun(3, bus);
        CpuState state = cpu.getState();
        assertEquals(initialX, state.getX());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, true, true, false",
            "50, 40, true, false, false",
            "40, 50, false, false, true",
            "255, 0, true, false, true",
            "0, 1, false, false, true"
    })
    public void CPX_Absolute(int initialX, int operand,
                             boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xEC;
        int address = 0x9000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterX(initialX)
                .withInstruction(0x8000, opcode, address & 0xFF, (address >> 8) & 0xFF)
                .withMemoryValue(address, operand)
                .buildAndRun(4, bus);
        CpuState state = cpu.getState();
        assertEquals(initialX, state.getX());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            // initialY, operand, expectedCarry, expectedZero, expectedNegative
            "50, 50, true, true, false",   // Equal: zero set, carry set
            "50, 40, true, false, false",   // Y > M: carry set, result positive
            "40, 50, false, false, true",   // Y < M: carry clear, negative result
            "255, 0, true, false, true",    // 255 >= 0, 255-0 = 255 (0xFF, negative)
            "0, 1, false, false, true"      // 0 < 1: carry clear, negative result
    })
    public void CPY_Immediate(int initialY, int operand,
                              boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xC0;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterY(initialY)
                .withInstruction(0x8000, opcode, operand)
                .buildAndRun(2);
        CpuState state = cpu.getState();
        assertEquals(initialY, state.getY());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, true, true, false",
            "50, 40, true, false, false",
            "40, 50, false, false, true",
            "255, 0, true, false, true",
            "0, 1, false, false, true"
    })
    public void CPY_ZeroPage(int initialY, int operand,
                             boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xC4;
        int address = 0x10;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterY(initialY)
                .withInstruction(0x8000, opcode, address)
                .withMemoryValue(address, operand)
                .buildAndRun(3, bus);
        CpuState state = cpu.getState();
        assertEquals(initialY, state.getY());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            "50, 50, true, true, false",
            "50, 40, true, false, false",
            "40, 50, false, false, true",
            "255, 0, true, false, true",
            "0, 1, false, false, true"
    })
    public void CPY_Absolute(int initialY, int operand,
                             boolean expectedCarry, boolean expectedZero, boolean expectedNegative) {
        final int opcode = 0xCC;
        int address = 0x9000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterY(initialY)
                .withInstruction(0x8000, opcode, address & 0xFF, (address >> 8) & 0xFF)
                .withMemoryValue(address, operand)
                .buildAndRun(4, bus);
        CpuState state = cpu.getState();
        assertEquals(initialY, state.getY());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
    }

    @ParameterizedTest
    @CsvSource({
            // initialCarry, branchOffset, expectedPC (hex)
            "true, 5, 0x8002",       // Carry set => branch not taken, PC remains at 0x8002
            "false, 5, 0x8007",      // Carry clear => branch taken: 0x8002 + 5 = 0x8007
            "false, -3, 0x7FFF",     // Branch taken: 0x8002 - 3 = 0x7FFF
            "true, -3, 0x8002"       // Carry set => branch not taken
    })
    public void BCC_Branch_Default(boolean initialCarry, int branchOffset, int expectedPC) {
        final int opcode = 0x90;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, branchOffset)
                .buildAndRun(initialCarry ? 2 : 3);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // With reset vector 0x80F0, after fetching the branch instruction, PC becomes 0x80F2.
            // For branch taken with an offset of +20 and a page crossing, the target becomes 0x80F2 + 20 = 0x8106.
            // In this case, we expect 4 cycles if the branch is taken.
            "false, 20, 0x8106",  // Carry clear => branch taken with page crossing
            "true, 20, 0x80F2"    // Carry set => branch not taken, PC remains 0x80F2.
    })
    public void BCC_Branch_PageCrossing(boolean initialCarry, int branchOffset, int expectedPC) {
        final int opcode = 0x90;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x80F0)
                .withFlagCarry(initialCarry)
                .withInstruction(0x80F0, opcode, branchOffset)
                .buildAndRun(initialCarry ? 2 : 4);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // initialCarry, branchOffset, expectedPC (hex)
            // With reset vector 0x8000, PC after fetching branch opcode & offset is 0x8002.
            // For BCS: if carry is set then branch is taken.
            "true, 5, 0x8007",   // Branch taken: 0x8002 + 5 = 0x8007.
            "false, 5, 0x8002",  // Not taken: PC remains 0x8002.
            "true, -3, 0x7FFF",  // Branch taken: 0x8002 - 3 = 0x7FFF.
            "false, -3, 0x8002"  // Not taken.
    })
    public void BCS_Branch_Default(boolean initialCarry, int branchOffset, int expectedPC) {
        final int opcode = 0xB0;
        int cycles = initialCarry ? 3 : 2;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, branchOffset)
                .buildAndRun(cycles);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // Using a reset vector near the end of a page, e.g. 0x80F0.
            // After fetching the branch instruction, PC becomes 0x80F2.
            // If branch is taken and page crossing occurs, the target is PC+offset with an extra cycle.
            "true, 20, 0x8106",  // Carry set => branch taken: 0x80F2 + 20 = 0x8106.
            "false, 20, 0x80F2"  // Not taken: PC remains 0x80F2.
    })
    public void BCS_Branch_PageCrossing(boolean initialCarry, int branchOffset, int expectedPC) {
        final int opcode = 0xB0;
        int cycles = initialCarry ? 4 : 2;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x80F0)
                .withFlagCarry(initialCarry)
                .withInstruction(0x80F0, opcode, branchOffset)
                .buildAndRun(cycles);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // initialZero, branchOffset, expectedPC (hex)
            "true, 5, 0x8007",   // Zero set => branch taken: 0x8002 + 5 = 0x8007.
            "false, 5, 0x8002",  // Zero clear => branch not taken.
            "true, -3, 0x7FFF",  // Zero set => branch taken: 0x8002 - 3 = 0x7FFF.
            "false, -3, 0x8002"  // Zero clear => branch not taken.
    })
    public void BEQ_Branch_Default(boolean initialZero, int branchOffset, int expectedPC) {
        final int opcode = 0xF0;
        int cycles = initialZero ? 3 : 2;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagZero(initialZero)
                .withInstruction(0x8000, opcode, branchOffset)
                .buildAndRun(cycles);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // With reset vector 0x80F0, after fetching the branch, PC becomes 0x80F2.
            // If branch is taken with an offset of +20 and page crossing occurs, target = 0x80F2 + 20 = 0x8106.
            "true, 20, 0x8106",  // Zero set => branch taken with page crossing (4 cycles)
            "false, 20, 0x80F2"   // Zero clear => branch not taken (2 cycles)
    })
    public void BEQ_Branch_PageCrossing(boolean initialZero, int branchOffset, int expectedPC) {
        final int opcode = 0xF0;
        int cycles = initialZero ? 4 : 2;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x80F0)
                .withFlagZero(initialZero)
                .withInstruction(0x80F0, opcode, branchOffset)
                .buildAndRun(cycles);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // initialNegative, branchOffset, expectedPC (hex)
            "true, 5, 0x8007",   // Negative flag set => branch taken: 0x8002 + 5 = 0x8007.
            "false, 5, 0x8002",  // Negative flag clear => branch not taken.
            "true, -3, 0x7FFF",  // Negative flag set => branch taken: 0x8002 - 3 = 0x7FFF.
            "false, -3, 0x8002"  // Negative flag clear => branch not taken.
    })
    public void BMI_Branch_Default(boolean initialNegative, int branchOffset, int expectedPC) {
        final int opcode = 0x30;
        int cycles = initialNegative ? 3 : 2;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagNegative(initialNegative)
                .withInstruction(0x8000, opcode, branchOffset)
                .buildAndRun(cycles);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // Using a reset vector of 0x80F0, after fetching the branch instruction, PC becomes 0x80F2.
            // If the Negative flag is set and the branch offset is +20, then the target is 0x80F2 + 20 = 0x8106.
            "true, 20, 0x8106",  // Negative set => branch taken with page crossing.
            "false, 20, 0x80F2"   // Negative clear => branch not taken.
    })
    public void BMI_Branch_PageCrossing(boolean initialNegative, int branchOffset, int expectedPC) {
        final int opcode = 0x30;
        int cycles = initialNegative ? 4 : 2;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x80F0)
                .withFlagNegative(initialNegative)
                .withInstruction(0x80F0, opcode, branchOffset)
                .buildAndRun(cycles);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // initialZero, branchOffset, expectedPC (hex)
            "false, 5, 0x8007",   // Zero clear => branch taken: 0x8002 + 5 = 0x8007.
            "true, 5, 0x8002",    // Zero set => branch not taken, PC remains 0x8002.
            "false, -3, 0x7FFF",  // Zero clear => branch taken: 0x8002 - 3 = 0x7FFF.
            "true, -3, 0x8002"    // Zero set => branch not taken.
    })
    public void BNE_Branch_Default(boolean initialZero, int branchOffset, int expectedPC) {
        final int opcode = 0xD0;
        int cycles = (!initialZero) ? 3 : 2;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagZero(initialZero)
                .withInstruction(0x8000, opcode, branchOffset)
                .buildAndRun(cycles);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // Using a reset vector near the end of a page: after fetching, PC becomes 0x80F2.
            // If branch is taken with an offset of +20, target = 0x80F2 + 20 = 0x8106.
            "false, 20, 0x8106",  // Zero clear => branch taken (with page crossing): 4 cycles.
            "true, 20, 0x80F2"     // Zero set => branch not taken, PC remains 0x80F2.
    })
    public void BNE_Branch_PageCrossing(boolean initialZero, int branchOffset, int expectedPC) {
        final int opcode = 0xD0;
        int cycles = (!initialZero) ? 4 : 2;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x80F0)
                .withFlagZero(initialZero)
                .withInstruction(0x80F0, opcode, branchOffset)
                .buildAndRun(cycles);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // initialNegative, branchOffset, expectedPC (hex)
            "false, 5, 0x8007",   // Negative false => branch taken: 0x8002 + 5 = 0x8007.
            "true, 5, 0x8002",    // Negative true  => branch not taken, PC remains 0x8002.
            "false, -3, 0x7FFF",  // Negative false => branch taken: 0x8002 - 3 = 0x7FFF.
            "true, -3, 0x8002"    // Negative true  => branch not taken.
    })
    public void BPL_Branch_Default(boolean initialNegative, int branchOffset, int expectedPC) {
        final int opcode = 0x10;
        int cycles = (!initialNegative) ? 3 : 2;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagNegative(initialNegative)
                .withInstruction(0x8000, opcode, branchOffset)
                .buildAndRun(cycles);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // With reset vector 0x80F0, after fetching the branch, PC becomes 0x80F2.
            // If branch is taken with an offset of +20 and page crossing occurs, the target becomes 0x80F2 + 20 = 0x8106.
            "false, 20, 0x8106",  // Negative false => branch taken with page crossing (4 cycles).
            "true, 20, 0x80F2"     // Negative true  => branch not taken, PC remains 0x80F2.
    })
    public void BPL_Branch_PageCrossing(boolean initialNegative, int branchOffset, int expectedPC) {
        final int opcode = 0x10;
        int cycles = (!initialNegative) ? 4 : 2;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x80F0)
                .withFlagNegative(initialNegative)
                .withInstruction(0x80F0, opcode, branchOffset)
                .buildAndRun(cycles);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // initialOverflow, branchOffset, expectedPC (hex)
            "false, 5, 0x8007",   // Overflow clear => branch taken: 0x8002 + 5 = 0x8007.
            "true, 5, 0x8002",    // Overflow set => branch not taken, PC remains 0x8002.
            "false, -3, 0x7FFF",  // Overflow clear => branch taken: 0x8002 - 3 = 0x7FFF.
            "true, -3, 0x8002"    // Overflow set => branch not taken.
    })
    public void BVC_Branch_Default(boolean initialOverflow, int branchOffset, int expectedPC) {
        final int opcode = 0x50;
        int cycles = (!initialOverflow) ? 3 : 2;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagOverflow(initialOverflow)
                .withInstruction(0x8000, opcode, branchOffset)
                .buildAndRun(cycles);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // Using a reset vector of 0x80F0, PC becomes 0x80F2 after fetch.
            // If branch is taken with an offset of +20, target = 0x80F2 + 20 = 0x8106.
            "false, 20, 0x8106",  // Overflow clear => branch taken with page crossing (4 cycles).
            "true, 20, 0x80F2"    // Overflow set => branch not taken.
    })
    public void BVC_Branch_PageCrossing(boolean initialOverflow, int branchOffset, int expectedPC) {
        final int opcode = 0x50;
        int cycles = (!initialOverflow) ? 4 : 2;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x80F0)
                .withFlagOverflow(initialOverflow)
                .withInstruction(0x80F0, opcode, branchOffset)
                .buildAndRun(cycles);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // initialOverflow, branchOffset, expectedPC (hex)
            "true, 5, 0x8007",   // Overflow set => branch taken: 0x8002 + 5 = 0x8007.
            "false, 5, 0x8002",  // Overflow clear => branch not taken.
            "true, -3, 0x7FFF",  // Overflow set => branch taken: 0x8002 - 3 = 0x7FFF.
            "false, -3, 0x8002"  // Overflow clear => branch not taken.
    })
    public void BVS_Branch_Default(boolean initialOverflow, int branchOffset, int expectedPC) {
        final int opcode = 0x70;
        int cycles = (initialOverflow) ? 3 : 2;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withFlagOverflow(initialOverflow)
                .withInstruction(0x8000, opcode, branchOffset)
                .buildAndRun(cycles);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // With reset vector 0x80F0, after fetching, PC becomes 0x80F2.
            // If branch is taken with an offset of +20 and page crossing occurs, target = 0x80F2 + 20 = 0x8106.
            "true, 20, 0x8106",   // Overflow set => branch taken with page crossing (4 cycles).
            "false, 20, 0x80F2"    // Overflow clear => branch not taken.
    })
    public void BVS_Branch_PageCrossing(boolean initialOverflow, int branchOffset, int expectedPC) {
        final int opcode = 0x70;
        int cycles = (initialOverflow) ? 4 : 2;
        CPU cpu = new CPUTestBuilder()
                .withResetVector(0x80F0)
                .withFlagOverflow(initialOverflow)
                .withInstruction(0x80F0, opcode, branchOffset)
                .buildAndRun(cycles);
        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            "0x00, 0x90, 0x9000",  // Jump to 0x9000 when low byte = 0x00, high = 0x90.
            "0x34, 0x12, 0x1234"   // Jump to 0x1234 when low byte = 0x34, high = 0x12.
    })
    public void JMP_Absolute(int low, int high, int expectedAddress) {
        final int opcode = 0x4C; // JMP Absolute opcode
        int startAddress = 0x8000; // Where the instruction is placed.
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(startAddress)
                .withInstruction(startAddress, opcode, low, high)
                .buildAndRun(3, bus);
        CpuState state = cpu.getState();
        assertEquals(expectedAddress, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            // Normal indirect jump cases: pointer address is 0x1000.
//            "0x1000, 0x00, 0x90, 0x9000",  // Memory at 0x1000 holds 0x00, at 0x1001 holds 0x90.
//            "0x1000, 0x34, 0x12, 0x1234",  // Memory at 0x1000 holds 0x34, at 0x1001 holds 0x12.
            // Bug case: pointer address is 0x10FF. Due to the 6502 bug, the high byte is read from 0x1000.
            "0x10FF, 0xEF, 0xBE, 0xBEEF"   // Memory at 0x10FF holds 0xEF, and memory at 0x1000 holds 0xBE.
    })
    public void JMP_Indirect(int pointerAddress, int pointerLow, int pointerHigh, int expectedAddress) {
        final int opcode = 0x6C;
        int startAddress = 0x8000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(startAddress)
                .withInstruction(startAddress, opcode, pointerAddress & 0xFF, (pointerAddress >> 8) & 0xFF)
                .withMemoryValue(pointerAddress, pointerLow)
                // For the high byte, if pointer low byte is 0xFF (bug condition),
                // write it to the wrapped-around address (pointerAddress & 0xFF00); otherwise, use pointerAddress + 1.
                .withMemoryValue(((pointerAddress & 0xFF) == 0xFF) ? (pointerAddress & 0xFF00) : (pointerAddress + 1), pointerHigh)
                .buildAndRun(5, bus);
        CpuState state = cpu.getState();
        assertEquals(expectedAddress, state.getPc());
    }

    @ParameterizedTest
    @CsvSource({
            "0x00, 0x90, 0x9001",   // When stack holds 0x9000, RTS returns 0x9001.
            "0x34, 0x12, 0x1235",   // When stack holds 0x1234, RTS returns 0x1235.
            "0xFF, 0xFF, 0x0000"    // When stack holds 0xFFFF, RTS returns 0x0000 (wrap-around).
    })
    public void RTS(int stackLow, int stackHigh, int expectedAddress) {
        final int opcode = 0x60;
        int startAddress = 0x8000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(startAddress)
                .withStackPointer(0xFD)
                .withInstruction(startAddress, opcode)
                .withMemoryValue(0x01FE, stackLow)
                .withMemoryValue(0x01FF, stackHigh)
                .buildAndRun(6, bus);
        CpuState state = cpu.getState();
        assertEquals(expectedAddress, state.getPc());

    }

    @ParameterizedTest
    @CsvSource({
            // For a JSR at 0x8000, the return address pushed should always be 0x8002 (high: 0x80, low: 0x02) regardless of the target.
            "0x9000, 0x9000, 0x80, 0x02",
            "0x1234, 0x1234, 0x80, 0x02"
    })
    public void JSR(int targetAddress, int expectedPC, int expectedPushedHigh, int expectedPushedLow) {
        final int opcode = 0x20;
        int startAddress = 0x8000;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(startAddress)
                .withStackPointer(0xFF)
                .withInstruction(startAddress, opcode, targetAddress & 0xFF, (targetAddress >> 8) & 0xFF)
                .buildAndRun(6, bus);

        CpuState state = cpu.getState();
        assertEquals(expectedPC, state.getPc());
        // Verify that the return address (PC - 1, which is startAddress + 2) was pushed onto the stack.
        // With an initial SP of 0xFF, JSR pushes two bytes:
        //   - First, the high byte is pushed at address 0x01FF.
        //   - Then, the low byte is pushed at address 0x01FE.
        assertEquals(expectedPushedHigh, bus.read(0x0100 | 0xFF) & 0xFF);
        assertEquals(expectedPushedLow, bus.read(0x0100 | 0xFE) & 0xFF);
        assertEquals(0xFD, state.getSp());
    }

    @Test
    public void testJSR_RTS() {
        final int jsrOpcode = 0x20;
        final int rtsOpcode = 0x60;
        int startAddress = 0x8000;
        int subroutineAddress = 0x9000;
        int initialStackPointer = 0xFF;
        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(startAddress)
                .withStackPointer(initialStackPointer)
                .withInstruction(startAddress, jsrOpcode, subroutineAddress & 0xFF, (subroutineAddress >> 8) & 0xFF)
                .withInstruction(subroutineAddress, rtsOpcode)
                .buildAndRun(12, bus);

        CpuState state = cpu.getState();

        assertEquals(startAddress + 3, state.getPc());
        assertEquals(initialStackPointer, state.getSp());
    }

    @Test
    public void testBRK() {
        final int brkOpcode = 0x00;
        int startAddress = 0x8000;
        int interruptVector = 0x9000;
        int returnAddress = startAddress + 2;
        int expectedStatusPushed = 0x30;

        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(startAddress)
                .withStackPointer(0xFF)
                .withInstruction(startAddress, brkOpcode)
                .withFlagNegative(false)
                .withFlagZero(false)
                .withFlagCarry(false)
                .withFlagInterruptDisable(false)
                .withFlagDecimal(false)
                .withFlagOverflow(false)
                .withMemoryValue(0xFFFE, interruptVector & 0xFF)
                .withMemoryValue(0xFFFF, (interruptVector >> 8) & 0xFF)
                .buildAndRun(7, bus);

        CpuState state = cpu.getState();

        assertEquals(interruptVector, state.getPc());
        assertEquals(0xFC, state.getSp());
        assertEquals((returnAddress >> 8) & 0xFF, bus.read(0x0100 | 0xFF) & 0xFF);
        assertEquals(returnAddress & 0xFF, bus.read(0x0100 | 0xFE) & 0xFF);
        assertEquals(expectedStatusPushed, bus.read(0x0100 | 0xFD) & 0xFF);
    }

    @Test
    public void testRTI() {
        final int rtiOpcode = 0x40;
        int startAddress = 0x8000;
        int returnAddress = 0x1234;
        int pushedStatus = 0xF0;
        int initialSP = 0xFC;

        Bus bus = new MockBus();
        CPU cpu = new CPUTestBuilder()
                .withResetVector(startAddress)
                .withInstruction(startAddress, rtiOpcode)
                .withStackPointer(initialSP)
                .withMemoryValue(0x0100 | 0xFD, pushedStatus)
                .withMemoryValue(0x0100 | 0xFE, returnAddress & 0xFF)
                .withMemoryValue(0x0100 | 0xFF, (returnAddress >> 8) & 0xFF)
                .buildAndRun(6, bus);

        CpuState state = cpu.getState();

        assertEquals(returnAddress, state.getPc());
        assertTrue(state.isNegative());
        assertTrue(state.isOverflow());
        assertFalse(state.isDecimal());
        assertFalse(state.isInterruptDisable());
        assertFalse(state.isZero());
        assertFalse(state.isCarry());
        assertEquals(0xFF, state.getSp());
    }

    @Test
    public void testNOP() {
        final int nopOpcode = 0xEA; // NOP opcode
        int startAddress = 0x8000;
        Bus bus = new MockBus();

        CPU cpu = new CPUTestBuilder()
                .withResetVector(startAddress)
                .withInstruction(startAddress, nopOpcode)
                .buildAndRun(2, bus);

        CpuState state = cpu.getState();

        assertEquals(startAddress + 1, state.getPc());
    }

    private static Stream<Arguments> provideADCArguments() {
        // initialA, operand, initialCarry, expectedA, expectedCarry, expectedZero, expectedNegative, expectedOverflow
        return Stream.of(
                // Simple addition: 0x14 (20) + 0x28 (40) = 0x3C (60)
                Arguments.of(0x14, 0x28, false, (0x14 + 0x28) & 0xFF, false, false, false, false),
                // Overflow: 0x70 (112) + 0x70 (112) = 0xE0 (224) -> overflow expected (sign change from positive to negative)
                Arguments.of(0x70, 0x70, false, (0x70 + 0x70) & 0xFF, false, false, true, true),
                // Overflow: 0x90 (-112) + 0x90 (-112) = 0x20 (32) -> overflow expected (sign change from negative to positive)
                Arguments.of(0x90, 0x90, false, (0x90 + 0x90) & 0xFF, true, false, false, true),
                // With initial carry: 0x14 + 0x28 + 1 = 0x3D (61)
                Arguments.of(0x14, 0x28, true, (0x14 + 0x28 + 1) & 0xFF, false, false, false, false),
                // Carry out: 0xFF (255) + 0x02 (2) = 0x01 with carry (since 0x101 > 0xFF)
                Arguments.of(0xFF, 0x02, false, (0xFF + 0x02) & 0xFF, true, false, false, false)
        );
    }

    @ParameterizedTest
    @MethodSource("provideADCArguments")
    public void ADC_Immediate_Flags(int initialA, int operand, boolean initialCarry,
                                    int expectedA, boolean expectedCarry, boolean expectedZero,
                                    boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 2;
        int opcode = 0x69;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedOverflow, state.isOverflow());
    }

    @ParameterizedTest
    @MethodSource("provideADCArguments")
    public void ADC_ZeroPage_Flags(int initialA, int operand, boolean initialCarry,
                                   int expectedA, boolean expectedCarry, boolean expectedZero,
                                   boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 3;
        int opcode = 0x65;
        int zpAddress = 0x10;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, zpAddress)
                .withMemoryValue(zpAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedOverflow, state.isOverflow());
    }

    @ParameterizedTest
    @MethodSource("provideADCArguments")
    public void ADC_ZeroPageX_Flags(int initialA, int operand, boolean initialCarry,
                                    int expectedA, boolean expectedCarry, boolean expectedZero,
                                    boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 4;
        int opcode = 0x75;
        int zpBase = 0x10;
        int registerX = 0x05;
        int effectiveAddress = (zpBase + registerX) & 0xFF;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(registerX)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, zpBase)
                .withMemoryValue(effectiveAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedOverflow, state.isOverflow());
    }

    @ParameterizedTest
    @MethodSource("provideADCArguments")
    public void ADC_Absolute_Flags(int initialA, int operand, boolean initialCarry,
                                   int expectedA, boolean expectedCarry, boolean expectedZero,
                                   boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 4;
        int opcode = 0x6D;
        int absAddress = 0x9000;
        int low = absAddress & 0xFF;
        int high = (absAddress >> 8) & 0xFF;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, low, high)
                .withMemoryValue(absAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedOverflow, state.isOverflow());
    }

    @ParameterizedTest
    @MethodSource("provideADCArguments")
    public void ADC_AbsoluteX_Flags(int initialA, int operand, boolean initialCarry,
                                    int expectedA, boolean expectedCarry, boolean expectedZero,
                                    boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 4;
        int opcode = 0x7D;
        int absBase = 0x9000;
        int registerX = 0x05;
        int effectiveAddress = absBase + registerX;
        int low = absBase & 0xFF;
        int high = (absBase >> 8) & 0xFF;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(registerX)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, low, high)
                .withMemoryValue(effectiveAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedOverflow, state.isOverflow());
    }

    @ParameterizedTest
    @MethodSource("provideADCArguments")
    public void ADC_AbsoluteY_Flags(int initialA, int operand, boolean initialCarry,
                                    int expectedA, boolean expectedCarry, boolean expectedZero,
                                    boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 4;
        int opcode = 0x79;
        int absBase = 0x9000;
        int registerY = 0x05;
        int effectiveAddress = absBase + registerY;
        int low = absBase & 0xFF;
        int high = (absBase >> 8) & 0xFF;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(registerY)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, low, high)
                .withMemoryValue(effectiveAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedOverflow, state.isOverflow());
    }

    @ParameterizedTest
    @MethodSource("provideADCArguments")
    public void ADC_IndirectX_Flags(int initialA, int operand, boolean initialCarry,
                                    int expectedA, boolean expectedCarry, boolean expectedZero,
                                    boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 6;
        int opcode = 0x61;
        int zpBase = 0x10;
        int registerX = 0x05;
        int pointerAddress = (zpBase + registerX) & 0xFF;
        int effectiveAddress = 0x9000;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(registerX)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, zpBase)
                .withZeroPagePointer(pointerAddress, effectiveAddress & 0xFF, (effectiveAddress >> 8) & 0xFF)
                .withMemoryValue(effectiveAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedOverflow, state.isOverflow());
    }

    @ParameterizedTest
    @MethodSource("provideADCArguments")
    public void ADC_IndirectY_Flags(int initialA, int operand, boolean initialCarry,
                                    int expectedA, boolean expectedCarry, boolean expectedZero,
                                    boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 5;
        int opcode = 0x71;
        int zpAddress = 0x10;
        int baseAddress = 0x9000;
        int registerY = 0x05;
        int effectiveAddress = baseAddress + registerY;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(registerY)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, zpAddress)
                .withZeroPagePointer(zpAddress, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(effectiveAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedOverflow, state.isOverflow());
    }

    // using base address 0x90FF with X = 0x01 gives effective address 0x9100.
    @Test
    public void ADC_AbsoluteX_PageCrossing_Flags() {
        final int instructionCycles = 5;
        int opcode = 0x7D;
        int absBase = 0x90FF; // Base address near end of page.
        int registerX = 0x01;
        int effectiveAddress = absBase + registerX; // 0x9100.

        int initialA = 0x10;
        int operand = 0x20;
        boolean initialCarry = false;
        int expectedA = (initialA + operand) & 0xFF;
        boolean expectedCarry = false;
        boolean expectedZero = (expectedA == 0);
        boolean expectedNegative = (expectedA & 0x80) != 0;
        boolean expectedOverflow = false;

        int low = absBase & 0xFF;
        int high = (absBase >> 8) & 0xFF; // 0x90.

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(registerX)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, low, high)
                .withMemoryValue(effectiveAddress, operand);

        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedOverflow, state.isOverflow());
    }

    // using base address 0x90FF with Y = 0x01 gives effective address 0x9100.
    @Test
    public void ADC_AbsoluteY_PageCrossing_Flags() {
        final int instructionCycles = 5;
        int opcode = 0x79;
        int absBase = 0x90FF;
        int registerY = 0x01;
        int effectiveAddress = absBase + registerY; // 0x9100.

        int initialA = 0x10;
        int operand = 0x20;
        boolean initialCarry = false;
        int expectedA = (initialA + operand) & 0xFF;
        boolean expectedCarry = false;
        boolean expectedZero = (expectedA == 0);
        boolean expectedNegative = (expectedA & 0x80) != 0;
        boolean expectedOverflow = false;

        int low = absBase & 0xFF;
        int high = (absBase >> 8) & 0xFF; // 0x90.

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(registerY)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, low, high)
                .withMemoryValue(effectiveAddress, operand);

        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedOverflow, state.isOverflow());
    }

    //A zero-page pointer pointing to 0x90FF and Y = 0x01 gives effective address 0x9100.
    @Test
    public void ADC_IndirectY_PageCrossing_Flags() {
        final int instructionCycles = 6;
        int opcode = 0x71;
        int zpAddress = 0x10;
        int baseAddress = 0x90FF;
        int registerY = 0x01;
        int effectiveAddress = baseAddress + registerY; // 0x9100.

        int initialA = 0x10;
        int operand = 0x20;
        boolean initialCarry = false;
        int expectedA = (initialA + operand) & 0xFF;
        boolean expectedCarry = false;
        boolean expectedZero = (expectedA == 0);
        boolean expectedNegative = (expectedA & 0x80) != 0;
        boolean expectedOverflow = false;

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(registerY)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, zpAddress)
                // Set the zero-page pointer at zpAddress to point to baseAddress (0x90FF).
                .withZeroPagePointer(zpAddress, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(effectiveAddress, operand);

        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA());
        assertEquals(expectedCarry, state.isCarry());
        assertEquals(expectedZero, state.isZero());
        assertEquals(expectedNegative, state.isNegative());
        assertEquals(expectedOverflow, state.isOverflow());
    }

    private static Stream<Arguments> provideSBCArguments() {
        // initialA, operand, initialCarry, expectedA, expectedCarry, expectedZero, expectedNegative, expectedOverflow.
        return Stream.of(
                // Simple subtraction: 0x50 (80) - 0x10 (16) with carry=true (no borrow) => 0x50 - 0x10 = 0x40 (64)
                Arguments.of(0x50, 0x10, true, (0x50 - 0x10) & 0xFF, true, false, false, false),
                // Subtraction with borrow: 0x50 (80) - 0x10 (16) with carry=false => 0x50 - 0x10 - 1 = 0x3F (63)
                Arguments.of(0x50, 0x10, false, (0x50 - 0x10 - 1) & 0xFF, true, false, false, false),
                // Borrow case: 0x10 (16) - 0x20 (32) with carry=true => 0x10 - 0x20 = -16 -> 0xF0, borrow occurs so carry=false,
                // negative flag set, no signed overflow.
                Arguments.of(0x10, 0x20, true, (0x10 - 0x20) & 0xFF, false, false, true, false),
                // Overflow case: subtracting a positive value from a negative number can trigger overflow.
                // Example: 0x80 (-128) - 0x10 (16) with carry=true: result = 0x80 - 0x10 = 0x70 (112).
                // Here, -128 (0x80) is negative and 0x70 is positive, so overflow is expected.
                Arguments.of(0x80, 0x10, true, (0x80 - 0x10) & 0xFF, true, false, false, true),
                // Similar overflow case with borrow: 0x80 (-128) - 0x10 (16) with carry=false: 0x80 - 0x10 - 1 = 0x6F (111)
                // => overflow expected.
                Arguments.of(0x80, 0x10, false, (0x80 - 0x10 - 1) & 0xFF, true, false, false, true)
        );
    }

    @ParameterizedTest
    @MethodSource("provideSBCArguments")
    public void SBC_Immediate_Flags(int initialA, int operand, boolean initialCarry,
                                    int expectedA, boolean expectedCarry, boolean expectedZero,
                                    boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 2;
        int opcode = 0xE9;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA(), "Accumulator result");
        assertEquals(expectedCarry, state.isCarry(), "Carry flag");
        assertEquals(expectedZero, state.isZero(), "Zero flag");
        assertEquals(expectedNegative, state.isNegative(), "Negative flag");
        assertEquals(expectedOverflow, state.isOverflow(), "Overflow flag");
    }

    @ParameterizedTest
    @MethodSource("provideSBCArguments")
    public void SBC_ZeroPage_Flags(int initialA, int operand, boolean initialCarry,
                                   int expectedA, boolean expectedCarry, boolean expectedZero,
                                   boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 3;
        int opcode = 0xE5;
        int zpAddress = 0x10;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, zpAddress)
                .withMemoryValue(zpAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA(), "Accumulator result");
        assertEquals(expectedCarry, state.isCarry(), "Carry flag");
        assertEquals(expectedZero, state.isZero(), "Zero flag");
        assertEquals(expectedNegative, state.isNegative(), "Negative flag");
        assertEquals(expectedOverflow, state.isOverflow(), "Overflow flag");
    }

    @ParameterizedTest
    @MethodSource("provideSBCArguments")
    public void SBC_ZeroPageX_Flags(int initialA, int operand, boolean initialCarry,
                                    int expectedA, boolean expectedCarry, boolean expectedZero,
                                    boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 4;
        int opcode = 0xF5;
        int zpBase = 0x10;
        int registerX = 0x05;
        int effectiveAddress = (zpBase + registerX) & 0xFF;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(registerX)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, zpBase)
                .withMemoryValue(effectiveAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA(), "Accumulator result");
        assertEquals(expectedCarry, state.isCarry(), "Carry flag");
        assertEquals(expectedZero, state.isZero(), "Zero flag");
        assertEquals(expectedNegative, state.isNegative(), "Negative flag");
        assertEquals(expectedOverflow, state.isOverflow(), "Overflow flag");
    }

    @ParameterizedTest
    @MethodSource("provideSBCArguments")
    public void SBC_Absolute_Flags(int initialA, int operand, boolean initialCarry,
                                   int expectedA, boolean expectedCarry, boolean expectedZero,
                                   boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 4;
        int opcode = 0xED;
        int absAddress = 0x9000;
        int low = absAddress & 0xFF;
        int high = (absAddress >> 8) & 0xFF;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, low, high)
                .withMemoryValue(absAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA(), "Accumulator result");
        assertEquals(expectedCarry, state.isCarry(), "Carry flag");
        assertEquals(expectedZero, state.isZero(), "Zero flag");
        assertEquals(expectedNegative, state.isNegative(), "Negative flag");
        assertEquals(expectedOverflow, state.isOverflow(), "Overflow flag");
    }

    // SBC Absolute,X (opcode 0xFD, 5 cycles; non-page-crossing case)
    @ParameterizedTest
    @MethodSource("provideSBCArguments")
    public void SBC_AbsoluteX_Flags(int initialA, int operand, boolean initialCarry,
                                    int expectedA, boolean expectedCarry, boolean expectedZero,
                                    boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 4;
        int opcode = 0xFD;
        int absBase = 0x9000;
        int registerX = 0x05;
        int effectiveAddress = absBase + registerX;
        int low = absBase & 0xFF;
        int high = (absBase >> 8) & 0xFF;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(registerX)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, low, high)
                .withMemoryValue(effectiveAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA(), "Accumulator result");
        assertEquals(expectedCarry, state.isCarry(), "Carry flag");
        assertEquals(expectedZero, state.isZero(), "Zero flag");
        assertEquals(expectedNegative, state.isNegative(), "Negative flag");
        assertEquals(expectedOverflow, state.isOverflow(), "Overflow flag");
    }

    @ParameterizedTest
    @MethodSource("provideSBCArguments")
    public void SBC_AbsoluteY_Flags(int initialA, int operand, boolean initialCarry,
                                    int expectedA, boolean expectedCarry, boolean expectedZero,
                                    boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 4;
        int opcode = 0xF9;
        int absBase = 0x9000;
        int registerY = 0x05;
        int effectiveAddress = absBase + registerY;
        int low = absBase & 0xFF;
        int high = (absBase >> 8) & 0xFF;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(registerY)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, low, high)
                .withMemoryValue(effectiveAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA(), "Accumulator result");
        assertEquals(expectedCarry, state.isCarry(), "Carry flag");
        assertEquals(expectedZero, state.isZero(), "Zero flag");
        assertEquals(expectedNegative, state.isNegative(), "Negative flag");
        assertEquals(expectedOverflow, state.isOverflow(), "Overflow flag");
    }

    @ParameterizedTest
    @MethodSource("provideSBCArguments")
    public void SBC_IndirectX_Flags(int initialA, int operand, boolean initialCarry,
                                    int expectedA, boolean expectedCarry, boolean expectedZero,
                                    boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 6;
        int opcode = 0xE1;
        int zpBase = 0x10;
        int registerX = 0x05;
        int pointerAddress = (zpBase + registerX) & 0xFF;
        int effectiveAddress = 0x9000;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(registerX)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, zpBase)
                .withZeroPagePointer(pointerAddress, effectiveAddress & 0xFF, (effectiveAddress >> 8) & 0xFF)
                .withMemoryValue(effectiveAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA(), "Accumulator result");
        assertEquals(expectedCarry, state.isCarry(), "Carry flag");
        assertEquals(expectedZero, state.isZero(), "Zero flag");
        assertEquals(expectedNegative, state.isNegative(), "Negative flag");
        assertEquals(expectedOverflow, state.isOverflow(), "Overflow flag");
    }

    @ParameterizedTest
    @MethodSource("provideSBCArguments")
    public void SBC_IndirectY_Flags(int initialA, int operand, boolean initialCarry,
                                    int expectedA, boolean expectedCarry, boolean expectedZero,
                                    boolean expectedNegative, boolean expectedOverflow) {
        final int instructionCycles = 5;
        int opcode = 0xF1;
        int zpAddress = 0x10;
        int baseAddress = 0x9000;
        int registerY = 0x05;
        int effectiveAddress = baseAddress + registerY;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(registerY)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, zpAddress)
                .withZeroPagePointer(zpAddress, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(effectiveAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();
        assertEquals(expectedA, state.getA(), "Accumulator result");
        assertEquals(expectedCarry, state.isCarry(), "Carry flag");
        assertEquals(expectedZero, state.isZero(), "Zero flag");
        assertEquals(expectedNegative, state.isNegative(), "Negative flag");
        assertEquals(expectedOverflow, state.isOverflow(), "Overflow flag");
    }

    // Page crossing: Base address 0x90FF with X = 0x01 results in effective address 0x9100.
    @Test
    public void SBC_AbsoluteX_PageCrossing_Flags() {
        final int instructionCycles = 5;
        int opcode = 0xFD;
        int absBase = 0x90FF;
        int registerX = 0x01;
        int effectiveAddress = absBase + registerX; // 0x9100.

        int initialA = 0x50;
        int operand = 0x10;
        boolean initialCarry = true;
        int expectedA = (initialA - operand) & 0xFF;
        boolean expectedCarry = true;
        boolean expectedZero = (expectedA == 0);
        boolean expectedNegative = (expectedA & 0x80) != 0;
        boolean expectedOverflow = false;

        int low = absBase & 0xFF;
        int high = (absBase >> 8) & 0xFF;

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterX(registerX)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, low, high)
                .withMemoryValue(effectiveAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();

        assertEquals(expectedA, state.getA(), "Accumulator result");
        assertEquals(expectedCarry, state.isCarry(), "Carry flag");
        assertEquals(expectedZero, state.isZero(), "Zero flag");
        assertEquals(expectedNegative, state.isNegative(), "Negative flag");
        assertEquals(expectedOverflow, state.isOverflow(), "Overflow flag");
    }

    // Page crossing: Base address 0x90FF with Y = 0x01 results in effective address 0x9100.
    @Test
    public void SBC_AbsoluteY_PageCrossing_Flags() {
        final int instructionCycles = 5;
        int opcode = 0xF9;
        int absBase = 0x90FF;
        int registerY = 0x01;
        int effectiveAddress = absBase + registerY; // 0x9100.

        int initialA = 0x50;
        int operand = 0x10;
        boolean initialCarry = true;
        int expectedA = (initialA - operand) & 0xFF; // 0x40.
        boolean expectedCarry = true;
        boolean expectedZero = (expectedA == 0);
        boolean expectedNegative = (expectedA & 0x80) != 0;
        boolean expectedOverflow = false;

        int low = absBase & 0xFF;
        int high = (absBase >> 8) & 0xFF;

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(registerY)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, low, high)
                .withMemoryValue(effectiveAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();

        assertEquals(expectedA, state.getA(), "Accumulator result");
        assertEquals(expectedCarry, state.isCarry(), "Carry flag");
        assertEquals(expectedZero, state.isZero(), "Zero flag");
        assertEquals(expectedNegative, state.isNegative(), "Negative flag");
        assertEquals(expectedOverflow, state.isOverflow(), "Overflow flag");
    }

    // Page crossing: Zero-page pointer at 0x10 points to base address 0x90FF; with Y = 0x01, effective address becomes 0x9100.
    @Test
    public void SBC_IndirectY_PageCrossing_Flags() {
        final int instructionCycles = 6;
        int opcode = 0xF1;
        int zpAddress = 0x10;
        int baseAddress = 0x90FF;
        int registerY = 0x01;
        int effectiveAddress = baseAddress + registerY; // 0x9100.

        int initialA = 0x50;
        int operand = 0x10;
        boolean initialCarry = true;
        int expectedA = (initialA - operand) & 0xFF;
        boolean expectedCarry = true;
        boolean expectedZero = (expectedA == 0);
        boolean expectedNegative = (expectedA & 0x80) != 0;
        boolean expectedOverflow = false;

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(initialA)
                .withRegisterY(registerY)
                .withFlagCarry(initialCarry)
                .withInstruction(0x8000, opcode, zpAddress)
                .withZeroPagePointer(zpAddress, baseAddress & 0xFF, (baseAddress >> 8) & 0xFF)
                .withMemoryValue(effectiveAddress, operand);
        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();

        assertEquals(expectedA, state.getA(), "Accumulator result");
        assertEquals(expectedCarry, state.isCarry(), "Carry flag");
        assertEquals(expectedZero, state.isZero(), "Zero flag");
        assertEquals(expectedNegative, state.isNegative(), "Negative flag");
        assertEquals(expectedOverflow, state.isOverflow(), "Overflow flag");
    }

    @Test
    public void BIT_ZeroPage_Flags_SetZero() {
        final int instructionCycles = 3;
        int opcode = 0x24;
        int zpAddress = 0x10;

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x0F)
                .withInstruction(0x8000, opcode, zpAddress)
                .withMemoryValue(zpAddress, 0xF0);

        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();

        assertTrue(state.isZero(), "Zero flag should be set because (A & operand) == 0");
        assertTrue(state.isNegative(), "Negative flag should be set as per bit 7 of operand (0xF0 -> 1)");
        assertTrue(state.isOverflow(), "Overflow flag should be set as per bit 6 of operand (0xF0 -> 1)");
    }

    @Test
    public void BIT_Absolute_Flags_ClearZero() {
        final int instructionCycles = 4;
        int opcode = 0x2C;
        int absAddress = 0x9000;

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0xFF)
                .withInstruction(0x8000, opcode, absAddress & 0xFF, absAddress >> 8)
                .withMemoryValue(absAddress, 0x55);

        CPU cpu = builder.buildAndRun(instructionCycles);
        CpuState state = cpu.getState();

        assertFalse(state.isZero(), "Zero flag should be clear because (A & operand) != 0");
        assertFalse(state.isNegative(), "Negative flag should be clear as per bit 7 of operand (0x55 -> 0)");
        assertTrue(state.isOverflow(), "Overflow flag should be set as per bit 6 of operand (0x55 -> 1)");
    }

    @Test
    public void testNMIInterrupt() {
        int resetAddress = 0x8000;
        int nmiLow = 0x34;
        int nmiHigh = 0x12;

        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                //NMI vector values.
                .withMemoryValue(0xFFFA, nmiLow)
                .withMemoryValue(0xFFFB, nmiHigh);

        CPU cpu = builder.buildAndRun(0);

        cpu.triggerNMI();

        for (int i = 0; i < 7; i++) {
            cpu.runCycle();
        }

        CpuState stateAfterNMI = cpu.getState();
        assertEquals(0x1234, stateAfterNMI.getPc(), "PC should be set to the NMI vector (0x1234)");

        // The stack pointer should be decremented by 3:
        // Typically, the initial SP is 0xFD; after three pushes it should be 0xFA.
        assertEquals(0xFA, stateAfterNMI.getSp(), "Stack pointer should be decremented by 3 after NMI");

        // If CPU exposes the NMI flags (nmiPending and processingNMI), you could assert that they are false.
        // For example:
        // assertFalse(cpu.isNMIPending(), "NMI pending flag should be cleared after servicing NMI");
        // assertFalse(cpu.isProcessingNMI(), "NMI processing flag should be cleared after NMI completes");
    }

    @Test
    public void testNMIInterruptAndRTI() {
        int resetAddress = 0x8000;
        int nmiLow = 0x34;
        int nmiHigh = 0x12;
        int nmiVector = (nmiHigh << 8) | nmiLow;

        // At 0x8000, place a NOP (opcode 0xEA) that takes 2 cycles.
        // At the NMI vector (0x1234), place an RTI instruction (opcode 0x40) that takes 6 cycles.
        int nopOpcode = 0xEA;
        int rtiOpcode = 0x40;
        CPUTestBuilder builder = new CPUTestBuilder()
                .withResetVector(resetAddress)
                .withInstruction(0x8000, nopOpcode)
                .withMemoryValue(0xFFFA, nmiLow)
                .withMemoryValue(0xFFFB, nmiHigh)
                .withInstruction(nmiVector, rtiOpcode);

        CPU cpu = builder.buildAndRun(0);

        // Run 1 cycle: the NOP has started but is not yet complete.
        cpu.runCycle();

        // Trigger NMI in the middle of executing the NOP.
        cpu.triggerNMI();

        // Complete the current instruction (NOP).
        // Since NOP is a 2-cycle instruction, run one more cycle.
        cpu.runCycle();

        assertEquals(0x8001, cpu.getState().getPc(), "PC should point to the next instruction after NOP");

        // Now that the current instruction finished, the pending NMI is recognized.
        for (int i = 0; i < 7; i++) {
            cpu.runCycle();
        }

        CpuState stateAfterNMI = cpu.getState();
        assertEquals(nmiVector, stateAfterNMI.getPc(), "PC should be set to the NMI vector (0x1234) after NMI");
        assertEquals(0xFA, stateAfterNMI.getSp(), "SP should be decremented by 3 after NMI");

        // RTI takes 6 cycles to restore PC and status.
        for (int i = 0; i < 6; i++) {
            cpu.runCycle();
        }

        CpuState finalState = cpu.getState();
        assertEquals(0x8001, finalState.getPc(), "After RTI, PC should be restored to 0x8001 (continuing the interrupted flow)");
        assertEquals(0xFD, finalState.getSp(), "After RTI, SP should be restored to its initial value (0xFD)");
    }
}