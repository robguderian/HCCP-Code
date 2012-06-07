package org.sunspotworld;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


import java.util.Date;
import java.util.Timer;
/**
 *
 * @author robg
 */
public class CallbackTimer extends Timer {
    
    final int debugLevel = 2;
    
    private CallbackTimerTask ctt;
    
    
    public CallbackTimer(ICallbackable objToCall)
    {
        registerCallback(objToCall);
    }
    
    public void registerCallback(ICallbackable objToCall)
    {
         ctt = new CallbackTimerTask(objToCall);
    }
    
    
    public void scheduleCallback(long delay)
    {
        if (debugLevel > 1)
                System.out.println("scheduling for " + delay);
        schedule(ctt, delay);
    }
    
    
            
}
