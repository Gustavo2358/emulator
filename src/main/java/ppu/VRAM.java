package ppu;

public class VRAM {
    private final int[] vramData = new int[0x2000]; // 8KB of VRAM

    public int read(int address) {
        // Map PPU address to internal array index
        // e.g., PPU 0x2000 -> vramData[0], PPU 0x2FFF -> vramData[0xFFF], PPU 0x3FFF -> vramData[0x1FFF]
        return vramData[address & 0x1FFF];
    }

    public void write(int address, int value) {
        // Map PPU address to internal array index
        vramData[address & 0x1FFF] = value;
    }
}