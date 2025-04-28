public class MockBus implements Bus {

    private WRAM wram = new MockWRAM();

    public MockBus() {

    }

    public MockBus(WRAM wram) {
        this.wram = wram;
    }

    @Override
    public int read(int address) {
        return wram.read(address);
    }

    @Override
    public void write(int effectiveAddress, int value) {
        this.wram.write(effectiveAddress, value);
    }

    @Override
    public void loadWRamState(WRAM wram) {
        this.wram.loadMemoryState(wram);
    }
}
