package core;

public record EmulatorState(
        CpuState cpuState,
        WRAM wram
) {
}
