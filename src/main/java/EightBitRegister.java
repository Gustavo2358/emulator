public class EightBitRegister {
    private int value;

    public EightBitRegister(int value) {
        this.value = value & 0xFF;
    }

    public void decrement() {
        --value;
        value &= 0xFF;
    }

    public void increment() {
        ++value;
        value &= 0xFF;
    }

    public int getValue() {
        return value & 0xFF;
    }

    public void setValue(int value) {
        this.value = value & 0xFF;
    }
}

