public interface Mapper {
    int mapPrgRomAddress(int cpuAddress);
    int mapChrRomAddress(int ppuAddress);
    int getId();
}

