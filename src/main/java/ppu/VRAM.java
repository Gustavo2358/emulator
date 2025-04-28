package ppu;

// VRAM.java
public class VRAM {
    private final int[] vramData = new int[0x2000];

    public int read(int address) {
        return vramData[address];
    }

    public void write(int address, int value) {
        vramData[address] = value;
    }
}