public interface WRAM {
    int read(int address);

    void write(int address, int value);

    void loadMemoryState(WRAM sourceMemory);

    int[] getMemory();
}
