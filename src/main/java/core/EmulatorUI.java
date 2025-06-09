package core;

import ppu.PPU;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferStrategy;
import java.io.File;

public class EmulatorUI extends JFrame {
    private static final int SCALE = 2;
    private static final int WIDTH = 256;
    private static final int HEIGHT = 240;

    private final Canvas canvas;
    private final PPU ppu;
    private final CPU cpu;
    private final core.apu.APU apu; // Updated to specific package
    private final Controller controller;

    private boolean isRunning = false;

    public EmulatorUI(CPU cpu, PPU ppu, core.apu.APU apu) { // Added APU to constructor
        this.cpu = cpu;
        this.ppu = ppu;
        this.apu = apu; // Store APU instance
        this.controller = new Controller();

        setTitle("NES Emulator");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Handle close manually
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                stop(); // Call our stop method which should include apu.stopAudio()
                System.exit(0);
            }
        });
        setResizable(false);

        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(WIDTH * SCALE, HEIGHT * SCALE));
        canvas.setFocusable(true);

        add(canvas);
        pack();
        setLocationRelativeTo(null);

        setupMenuBar();
        setupKeyboardInput();
    }

    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem openRomItem = new JMenuItem("Open ROM");
        openRomItem.addActionListener(e -> openRomFile());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(openRomItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
    }

    private void setupKeyboardInput() {
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyEvent(e, true);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                handleKeyEvent(e, false);
            }
        });
    }

    private void handleKeyEvent(KeyEvent e, boolean isPressed) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_Z:
                controller.setButton(0, isPressed); // A
                break;
            case KeyEvent.VK_X:
                controller.setButton(1, isPressed); // B
                break;
            case KeyEvent.VK_SPACE:
                controller.setButton(2, isPressed); // Select
                break;
            case KeyEvent.VK_ENTER:
                controller.setButton(3, isPressed); // Start
                break;
            case KeyEvent.VK_UP:
                controller.setButton(4, isPressed); // Up
                break;
            case KeyEvent.VK_DOWN:
                controller.setButton(5, isPressed); // Down
                break;
            case KeyEvent.VK_LEFT:
                controller.setButton(6, isPressed); // Left
                break;
            case KeyEvent.VK_RIGHT:
                controller.setButton(7, isPressed); // Right
                break;
        }
    }

    private void openRomFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Open ROM File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".nes");
            }

            @Override
            public String getDescription() {
                return "NES ROM Files (*.nes)";
            }
        });

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            loadRom(selectedFile.getAbsolutePath());
        }
    }

    private void loadRom(String path) {
        stop();

        try {
            start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error loading ROM: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void start() {
        if (isRunning) return;

        isRunning = true;
        setVisible(true);

        Thread renderThread = new Thread(this::renderLoop);
        renderThread.start();
    }

    public void stop() {
        isRunning = false;
        if (apu != null) {
            apu.stopAudio();
        }
    }

    private void renderLoop() {
        canvas.createBufferStrategy(2);
        BufferStrategy bufferStrategy = canvas.getBufferStrategy();

        long wallClock_lastTime = System.nanoTime(); // Tracks wall-clock time for the accumulator
        final double nsPerFrame = 1_000_000_000.0 / 60.0; // Target duration of one NES frame (for emulation and visuals)
        double accumulatorNs = 0.0; // Accumulates unprocessed real time

        final int cpuCyclesPerFrame = 29833; // Defined CPU cycles per NES frame

        while (isRunning) {
            long wallClock_currentTime = System.nanoTime(); // Current wall-clock time at the start of this iteration
            double elapsedRealNs = (double)(wallClock_currentTime - wallClock_lastTime);
            wallClock_lastTime = wallClock_currentTime;

            // Cap elapsedRealNs to prevent spiral of death if system hangs for too long.
            // e.g., cap at 4 frames worth of time (approx 66.67ms for 60FPS).
            final double maxElapsedTimeNs = 4.0 * nsPerFrame;
            if (elapsedRealNs > maxElapsedTimeNs) {
                elapsedRealNs = maxElapsedTimeNs;
            }

            accumulatorNs += elapsedRealNs;

            // Emulate NES frames if enough accumulated time
            while (accumulatorNs >= nsPerFrame) {
                int cyclesThisEmulationStep = 0;
                while (cyclesThisEmulationStep < cpuCyclesPerFrame) {
                    cpu.runCycle(); // CPU cycle now also clocks the APU internally
                    ppu.runCycle(); // PPU runs 3x CPU speed
                    ppu.runCycle();
                    ppu.runCycle();
                    cyclesThisEmulationStep++;
                }
                accumulatorNs -= nsPerFrame;
            }

            // Render the current state once per visual frame iteration
            render(bufferStrategy);

            // Sleep logic to maintain overall visual frame rate and avoid busy-waiting
            long visualFrameProcessingEndTime = System.nanoTime();
            // wallClock_currentTime was the time at the START of this visual frame's processing (emulation + render)
            long iterationActualDurationNs = visualFrameProcessingEndTime - wallClock_currentTime;

            if (iterationActualDurationNs < nsPerFrame) {
                long timeToWaitNanos = (long)nsPerFrame - iterationActualDurationNs;
                if (timeToWaitNanos > 0) {
                    long millisToWait = timeToWaitNanos / 1_000_000;
                    int nanosToWait = (int) (timeToWaitNanos % 1_000_000);
                    try {
                        Thread.sleep(millisToWait, nanosToWait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        isRunning = false; // Stop the loop if interrupted
                    }
                }
            }
            // If iterationActualDurationNs >= nsPerFrame, we are on time or lagging behind visually,
            // so no sleep is performed. The accumulator handles emulation catch-up.
        }

        bufferStrategy.dispose();
    }

    private void render(BufferStrategy bufferStrategy) {
        do {
            do {
                Graphics g = bufferStrategy.getDrawGraphics();

                g.setColor(Color.BLACK);
                g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

                g.drawImage(ppu.getFrameBuffer(), 0, 0, WIDTH * SCALE, HEIGHT * SCALE, null);

                g.dispose();
            } while (bufferStrategy.contentsRestored());

            bufferStrategy.show();
        } while (bufferStrategy.contentsLost());
    }

    public Controller getController() {
        return controller;
    }
}
