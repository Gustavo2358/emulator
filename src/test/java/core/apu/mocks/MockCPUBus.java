package core.apu.mocks;

import core.Bus;
import core.Controller;
import core.WRAM;
import core.apu.APU; // May not be strictly needed here but good for context
import ppu.PPU;     // May not be strictly needed here

import java.util.HashMap;
import java.util.Map;

public class MockCPUBus implements Bus {
    private final Map<Integer, Byte> memory = new HashMap<>();
    private byte lastReadValue = 0x00; // For open bus behavior if needed

    // --- Methods needed by DMCChannel ---
    @Override
    public int read(int address) {
        address &= 0xFFFF;
        lastReadValue = memory.getOrDefault(address, lastReadValue); // Return last read for unmapped
        // System.out.printf("MockCPUBus Read: Address=0x%04X, Value=0x%02X%n", address, lastReadValue);
        return lastReadValue & 0xFF;
    }

    // --- Other Bus interface methods (minimal implementation) ---
    @Override
    public void write(int address, int value) {
        // Not directly used by DMCChannel for sample fetching, but part of Bus interface
        memory.put(address & 0xFFFF, (byte) (value & 0xFF));
    }

    // Helper for tests to load data into the mock memory
    public void mockMemoryLoad(int address, byte[] data) {
        for (int i = 0; i < data.length; i++) {
            memory.put((address + i) & 0xFFFF, data[i]);
        }
    }

    public void mockMemoryWrite(int address, byte value) {
        memory.put(address & 0xFFFF, value);
    }


    // --- Unused methods from Bus interface for these tests ---
    @Override
    public void loadWRamState(WRAM wram) {
        // No-op
    }

    @Override
    public Controller getController1() {
        return null; // Or a mock if needed by other components
    }

    @Override
    public Controller getController2() {
        return null;
    }

    @Override
    public void setController1(Controller controller1) {
        // No-op
    }

    @Override
    public void setController2(Controller controller2) {
        // No-op
    }

    // If APU getter is part of Bus interface (it is in current CPUBus)
    // This mock bus doesn't own an APU, so it returns null or a mock APU if needed.
    // For testing DMCChannel, DMCChannel itself is the unit under test, APU is not directly involved.
    public APU getAPU() {
        return null;
    }
}
