public class Register8Bit {
    private int value;

    public Register8Bit(int value) {
        this.value = value & 0xFF;
    }

    public int getValue() {
        return value & 0xFF;
    }

    public void setValue(int value) {
        this.value = value & 0xFF;
    }
}

