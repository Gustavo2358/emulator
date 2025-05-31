public class Controller {
    private boolean[] buttons = new boolean[8]; // A, B, Select, Start, Up, Down, Left, Right
    private int strobe = 0;
    private int shiftRegister = 0;

    public void setButton(int button, boolean pressed) {
        buttons[button] = pressed;
    }

    public void write(int value) {
        strobe = value & 1;
        if (strobe == 1) {
            shiftRegister = getButtonState();
        }
    }

    public int read() {
        if (strobe == 1) {
            return buttons[0] ? 1 : 0; // Return A button when strobing
        } else {
            int result = shiftRegister & 1;
            shiftRegister >>= 1;
            return result;
        }
    }

    private int getButtonState() {
        int state = 0;
        for (int i = 0; i < 8; i++) {
            if (buttons[i]) state |= (1 << i);
        }
        return state;
    }
}
