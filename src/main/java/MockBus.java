public class MockBus implements Bus {

    private WRAM wram;

    public MockBus() {

    }

    public MockBus(WRAM wram) {
        this.wram = wram;
    }

    @Override
    public int read(int address) {
        return wram.memory[address];
    }

    @Override
    public void loadWRamState(WRAM wram) {
        this.wram = wram;
    }

    @Override
    public void write(int effectiveAddress, int value) {
        this.wram.memory[effectiveAddress] = value;
    }
}
