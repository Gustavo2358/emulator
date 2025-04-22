package mapper;

public class NROMMapper implements Mapper {
    private final int prgRomSize;

    private static final int PRG_ROM_16KB = 16384;
    private static final int PRG_ROM_32KB = 32768;
    private static final int CPU_PRG_ROM_START = 0x8000;
    private static final int CPU_UPPER_BANK_START = 0xC000;
    private static final int MAPPER_ID = 0;

    public NROMMapper(int prgRomSize) {
        this.prgRomSize = prgRomSize;
    }

    @Override
    public int mapPrgRomAddress(int cpuAddress) {
        if (cpuAddress < CPU_PRG_ROM_START || cpuAddress > 0xFFFF) {
            throw new IllegalArgumentException("Address out of range: " + cpuAddress);
        }

        if (prgRomSize == PRG_ROM_16KB) {
            // Mirror the 16KB ROM for addresses 0xC000-0xFFFF
            if (cpuAddress >= CPU_UPPER_BANK_START) {
                return (cpuAddress - CPU_UPPER_BANK_START);
            }
            return cpuAddress - CPU_PRG_ROM_START;
        } else if (prgRomSize == PRG_ROM_32KB) {
            // Map the full 32KB ROM directly
            return cpuAddress - CPU_PRG_ROM_START;
        }
        throw new IllegalStateException("Unsupported PRG ROM size: " + prgRomSize);
    }

    @Override
    public int mapChrRomAddress(int ppuAddress) {
        return ppuAddress;
    }

    @Override
    public int getId() {
        return MAPPER_ID;
    }
}