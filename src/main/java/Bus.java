public class Bus {

    private WRAM wram;

    public Bus(WRAM wram) {
        this.wram = wram;
    }

    public int fetch(int address) {
        return wram.memory[address];
    }

    public void loadWRamState(WRAM wram) {
        this.wram = wram;
    }
}
