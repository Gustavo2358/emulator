package ppu;

// OAM.java
public class OAM {
    private final int[] oamData = new int[256];

    public int read(int address) {
        return oamData[address & 0xFF];
    }

    public void write(int address, int value) {
        oamData[address & 0xFF] = value;
    }
}