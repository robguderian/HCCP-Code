package org.sunspotworld;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


//import java.util.TimerTask;

/**
 *
 * @author robg
 */
public class CallbackTimerTask extends java.util.TimerTask {
    
    
    private ICallbackable myCallbackObject;
    
    public CallbackTimerTask(ICallbackable icb)
    {
        myCallbackObject = icb;
    }
            
            
    public void run()
    {
        myCallbackObject.timerCallback();
    }
}