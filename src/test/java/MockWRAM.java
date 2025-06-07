import core.WRAM;

public class MockWRAM implements WRAM {
    public int[] memory;

    /**
     * Create a MockWRAM with default size of 2048 bytes
     */
    public MockWRAM() {
        this(new int[2048]);
    }

    public MockWRAM(int[] sourceMemory) {
        this.memory = sourceMemory;
    }

    @Override
    public int read(int address) {
        // Allow reading from any address by using modulo to wrap around if needed
        if (address >= 0) {
            return memory[address % memory.length];
        }
        return 0;
    }

    @Override
    public void write(int address, int value) {
        // Allow writing to any address by using modulo to wrap around if needed
        if (address >= 0) {
            memory[address % memory.length] = value;
        }
    }

    @Override
    public void loadMemoryState(WRAM sourceMemory) {
        memory = sourceMemory.getMemory();
    }

    public int[] getMemory() {
        return memory;
    }
}
