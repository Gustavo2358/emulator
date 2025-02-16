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

    private static Stream<Arguments> provideLoadImmediateArguments(){
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

    private static Stream<Arguments> provideLoadAbsoluteArguments(){
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

    private static Stream<Arguments> provideLoadZeroPageArguments(){
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
    public void LDYZeroPageXIndexed() {
        final int instructionCycles = 4;
        final int LDX_ZERO_PAGE_X_OPCODE = 0xB4; // Opcode for LDY Zero Page,X

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
        assertEquals(0x42, bus.fetch(0x0010));
    }

    @Test
    public void STA_ZeroPageX() {
        final int instructionCycles = 4;
        final int STA_ZERO_PAGE_X_OPCODE = 0x95; // Opcode for STA Zero Page,X

        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x42)
                .withRegisterX(0x05) // X = 0x05; effective address: 0x10 + 0x05 = 0x15
                .withInstruction(0x8000, STA_ZERO_PAGE_X_OPCODE, 0x10) // STA $10,X
                .buildAndRun(instructionCycles, bus);

        assertEquals(0x42, bus.fetch(0x0015));
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

        assertEquals(0x42, bus.fetch(0x9000));
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

        assertEquals(0x42, bus.fetch(0x9005));
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

        assertEquals(0x42, bus.fetch(0x9005));
    }

    @Test
    public void STA_IndirectX() {
        final int instructionCycles = 6;
        final int STA_INDIRECT_X_OPCODE = 0x81; // Opcode for STA (Indirect,X)

        // For (Indirect,X): the operand (0x10) is added to X (0x05) to get the zero page pointer at 0x15.
        // The two-byte pointer at 0x15 points to the effective address.
        Bus bus = new MockBus();
        new CPUTestBuilder()
                .withResetVector(0x8000)
                .withRegisterA(0x42)
                .withRegisterX(0x05)
                .withInstruction(0x8000, STA_INDIRECT_X_OPCODE, 0x10)
                .withZeroPagePointer(0x15, 0x00, 0x90) // Pointer at 0x15 -> effective address 0x9000
                .buildAndRun(instructionCycles, bus);

        assertEquals(0x42, bus.fetch(0x9000));
    }

    @Test
    public void STA_IndirectY() {
        final int instructionCycles = 6;
        final int STA_INDIRECT_Y_OPCODE = 0x91; // Opcode for STA (Indirect),Y

        // For (Indirect),Y: the operand (0x10) gives a zero page pointer whose two-byte value is the base address.
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
        assertEquals(0x42, bus.fetch(0x9005));
    }
}