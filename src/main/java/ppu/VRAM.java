package ppu;

public class VRAM {
    // Should be 2KB for two nametables
    private final int[] vramData = new int[0x800]; // 2KB (2048 bytes)

    public int read(int address) { // address is the 0-indexed internal VRAM address
        return vramData[address & 0x07FF]; // Ensure it's within 2KB bounds
    }

    public void write(int address, int value) { // address is the 0-indexed internal VRAM address
        vramData[address & 0x07FF] = value; // Ensure it's within 2KB bounds
    }
}