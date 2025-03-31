public class CpuState {
    private final int pc, sp, a, x, y;
    private final boolean carry, zero, interruptDisable, decimal, overflow, negative;

    private CpuState(Builder builder) {
        this.pc = builder.pc;
        this.sp = builder.sp;
        this.a = builder.a;
        this.x = builder.x;
        this.y = builder.y;
        this.carry = builder.carry;
        this.zero = builder.zero;
        this.interruptDisable = builder.interruptDisable;
        this.decimal = builder.decimal;
        this.overflow = builder.overflow;
        this.negative = builder.negative;
    }

    public static class Builder {
        private int pc = 0x00, sp = 0xFD, a = 0x00, x = 0x00, y = 0x00;
        private boolean carry = false, zero = false, interruptDisable = true, decimal = false, overflow = false, negative = false;

        public Builder pc(int pc) { this.pc = pc; return this; }
        public Builder sp(int sp) { this.sp = sp; return this; }
        public Builder a(int a) { this.a = a; return this; }
        public Builder x(int x) { this.x = x; return this; }
        public Builder y(int y) { this.y = y; return this; }
        public Builder carry(boolean carry) { this.carry = carry; return this; }
        public Builder zero(boolean zero) { this.zero = zero; return this; }
        public Builder interruptDisable(boolean interruptDisable) { this.interruptDisable = interruptDisable; return this; }
        public Builder decimal(boolean decimal) { this.decimal = decimal; return this; }
        public Builder overflow(boolean overflow) { this.overflow = overflow; return this; }
        public Builder negative(boolean negative) { this.negative = negative; return this; }

        public CpuState build() {
            return new CpuState(this);
        }
    }

    public int getPc() {
        return pc;
    }

    public int getSp() {
        return sp;
    }

    public int getA() {
        return a;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isCarry() {
        return carry;
    }

    public boolean isZero() {
        return zero;
    }

    public boolean isInterruptDisable() {
        return interruptDisable;
    }

    public boolean isDecimal() {
        return decimal;
    }

    public boolean isOverflow() {
        return overflow;
    }

    public boolean isNegative() {
        return negative;
    }
}

