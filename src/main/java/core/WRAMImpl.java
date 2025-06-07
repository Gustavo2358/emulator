package core;

import java.util.Arrays;

public class WRAMImpl implements WRAM {
    public int[] memory = new int[2048];

    public WRAMImpl() {
        Arrays.fill(memory, 0);
    }

    @Override
    public int read(int address) {
        if (address < 0x2000) {
            return memory[address & 0x07FF];
        }
        return 0;
    }

    @Override
    public void write(int address, int value) {
        if (address < 0x2000) {
            memory[address & 0x07FF] = value;
        }
    }

    @Override
    public void loadMemoryState(WRAM sourceMemory) {
        if (sourceMemory != null) {
            int[] source = sourceMemory.getMemory();
            if (source != null && source.length == memory.length) {
                System.arraycopy(source, 0, memory, 0, memory.length);
            } else {
                throw new IllegalArgumentException("Source memory is null or has an invalid length." +
                        " Expected length: " + memory.length + ", Actual length: " + (source != null ? source.length : "null"));
            }
        }
    }

    @Override
    public int[] getMemory() {
        return memory;
    }
}