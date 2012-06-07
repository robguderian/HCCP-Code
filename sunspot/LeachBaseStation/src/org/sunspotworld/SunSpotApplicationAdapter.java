package org.sunspotworld;

import com.sun.spot.peripheral.*;
import com.sun.spot.sensorboard.EDemoBoard;
import com.sun.spot.sensorboard.io.*;
import com.sun.spot.sensorboard.peripheral.*;
import com.sun.spot.util.*;
import javax.microedition.midlet.*;

public abstract class SunSpotApplicationAdapter extends MIDlet {
    protected ISpot spot = Spot.getInstance();
    protected ISleepManager sleepManager = spot.getSleepManager();
    protected EDemoBoard eDemo = EDemoBoard.getInstance();
    protected ITriColorLED[] leds = eDemo.getLEDs();
    protected IScalarInput[] scalarInputs = eDemo.getScalarInputs();
    protected IIOPin[] ioPins = eDemo.getIOPins();
    protected IOutputPin[] outputPins = eDemo.getOutputPins();
    protected ISwitch[] switches = eDemo.getSwitches();
    protected ITriColorLED led1 = leds[EDemoBoard.LED1];
    protected ITriColorLED led2 = leds[EDemoBoard.LED2];
    protected ITriColorLED led3 = leds[EDemoBoard.LED3];
    protected ITriColorLED led4 = leds[EDemoBoard.LED4];
    protected ITriColorLED led5 = leds[EDemoBoard.LED5];
    protected ITriColorLED led6 = leds[EDemoBoard.LED6];
    protected ITriColorLED led7 = leds[EDemoBoard.LED7];
    protected ITriColorLED led8 = leds[EDemoBoard.LED8];
    protected IScalarInput a0 = scalarInputs[EDemoBoard.A0];
    protected IScalarInput a1 = scalarInputs[EDemoBoard.A1];
    protected IScalarInput a2 = scalarInputs[EDemoBoard.A2];
    protected IScalarInput a3 = scalarInputs[EDemoBoard.A3];
    protected IScalarInput a4 = scalarInputs[EDemoBoard.A4];
    protected IScalarInput a5 = scalarInputs[EDemoBoard.A5];
    protected IIOPin d0 = ioPins[EDemoBoard.D0];
    protected IIOPin d1 = ioPins[EDemoBoard.D1];
    protected IIOPin d2 = ioPins[EDemoBoard.D2];
    protected IIOPin d3 = ioPins[EDemoBoard.D3];
    protected IIOPin d4 = ioPins[EDemoBoard.D4];
    protected IOutputPin h0 = outputPins[EDemoBoard.H0];
    protected IOutputPin h1 = outputPins[EDemoBoard.H1];
    protected IOutputPin h2 = outputPins[EDemoBoard.H2];
    protected IOutputPin h3 = outputPins[EDemoBoard.H3];
    protected ISwitch sw1 = switches[EDemoBoard.SW1];
    protected ISwitch sw2 = switches[EDemoBoard.SW2];

    protected void startApp() throws MIDletStateChangeException {
        new BootloaderListener().start(); // monitor the USB (if connected) and recognize commands from host
   
        try {
            setup();
            while (true) {
                loop();
            }
        } catch (InterruptedException ignore) {
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }

        notifyDestroyed(); // cause the MIDlet to exit
    }

    protected void setup() throws Exception {
    }

    protected void loop() throws Exception {
        exitLoop();
    }

    protected void exitLoop() throws InterruptedException {
        throw new InterruptedException();
    }

    protected void cleanup() throws Exception {
    }

    protected void pauseApp() {
        // This is not currently called by the Squawk VM
    }

    /**
     * Called if the MIDlet is terminated by the system.
     * I.e. if startApp throws any exception other than MIDletStateChangeException,
     * if the isolate running the MIDlet is killed with Isolate.exit(), or
     * if VM.stopVM() is called.
     *
     * It is not called if MIDlet.notifyDestroyed() was called.
     *
     * @param unconditional If true when this method is called, the MIDlet must
     *    cleanup and release all resources. If false the MIDlet may throw
     *    MIDletStateChangeException to indicate it does not want to be destroyed
     *    at this time.
     */
    protected void destroyApp(boolean unconditional) throws MIDletStateChangeException {
        if (unconditional) {
            try {
                cleanup();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            throw new MIDletStateChangeException();
        }
    }

    protected static void sleep(long milliseconds) {
        Utils.sleep(milliseconds);
    }
}
