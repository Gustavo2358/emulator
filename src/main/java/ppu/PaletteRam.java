package ppu;

// PaletteRam.java
public class PaletteRam {
    private final int[] paletteData = new int[32];

    public int read(int index) {
        return paletteData[index];
    }

    public void write(int index, int value) {
        paletteData[index] = value & 0x3F;
    }
}