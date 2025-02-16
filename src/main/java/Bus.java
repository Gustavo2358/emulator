public interface Bus {
    int fetch(int address);

    void loadWRamState(WRAM wram);

    void write(int effectiveAddress, int value);
}
