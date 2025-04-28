import ppu.PPU;

public class CPUBus implements Bus {
    private final WRAM wram;
    private final Cartridge cartridge;
    private final PPU ppu;

    public CPUBus(WRAM wram, Cartridge cartridge, PPU ppu) {
        this.wram = wram;
        this.cartridge = cartridge;
        this.ppu = ppu;
    }

    @Override
    public int read(int address) {
        if (address < 0x2000) {
            return wram.read(address);
        } else if (address < 0x4000) {
            return ppu.read(address);
        } else if (address < 0x6000) {
            return cartridge.cpuRead(address);
        } else if (address < 0x8000) {
            return cartridge.cpuRead(address);
        }
        return 0;
    }

    @Override
    public void write(int address, int value) {
    }

    @Override
    public void loadWRamState(WRAM wram) {
        this.wram.loadMemoryState(wram);

    }
}