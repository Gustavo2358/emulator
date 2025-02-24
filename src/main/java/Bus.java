public interface Bus {
    int read(int address);

    void loadWRamState(WRAM wram);

    void write(int effectiveAddress, int value);
}
