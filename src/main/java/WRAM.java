public class WRAM {
    public int[] memory = new int[2048];

    public WRAM() {
        for (int i = 0; i < memory.length; i++) {
            memory[i] = 0;
        }
    }

    public int read(int address) {
        if (address < 0x2000) {
            return memory[address & 0x07FF];
        }
        return 0;
    }

    public void write(int address, int value) {
        if (address < 0x2000) {
            memory[address & 0x07FF] = value;
        }
    }

    public void loadMemoryState(int[] sourceMemory) {
        if (sourceMemory.length != memory.length) {
            throw new IllegalArgumentException("Source memory must be exactly " + memory.length + " bytes");
        }

        System.arraycopy(sourceMemory, 0, memory, 0, memory.length);
    }
}