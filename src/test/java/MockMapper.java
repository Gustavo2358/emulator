import mapper.Mapper;

public class MockMapper implements Mapper {
    int mappedPrgAddress = 0;
    int mappedChrAddress = 0;

    @Override
    public int mapPrgRomAddress(int cpuAddress) {
        return mappedPrgAddress;
    }

    @Override
    public int mapChrRomAddress(int ppuAddress) {
        return mappedChrAddress;
    }

    @Override
    public int getId() {
        return 0;
    }
}
