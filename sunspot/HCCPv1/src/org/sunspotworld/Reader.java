package org.sunspotworld;

/*
 * Copyright (c) 2006 Sun Microsystems, Inc.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to 
 * deal in the Software without restriction, including without limitation the 
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or 
 * sell copies of the Software, and to permit persons to whom the Software is 
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 **/



import com.sun.spot.peripheral.*;
import com.sun.spot.peripheral.radio.*;

import com.sun.spot.util.Utils;



/**
 * A simple packet sniffer. Listens to all packets received by the radio
 * and prints out the packets seen. 
 *
 * This application requires that the SPOT it is on does not start up any
 * services that use the radio, e.g. OTA mode must be disabled.  It also
 * must be run on a SPOT, not as a host app, since it needs to access the
 * physical layer of the radio stack.
 * 
 * Note: while in promiscuous listening mode the radio chip will not send
 * out any ACKs, so any Spot sending us a packet will never be notified it
 * was delivered---the sender will get a No ACK exception.
 *
 * Author: Ron Goldman
 * Release: Orange after 11-30-06
 */
public class Reader extends Thread {
    
    private static IProprietaryRadio radio = Spot.getInstance().getIProprietaryRadio();
    private static I802_15_4_PHY i802phyRadio = Spot.getInstance().getI802_15_4_PHY();

    //private static I802_15_4_MAC mac = RadioFactory.getI802_15_4_MAC();
    
    private boolean isOff = false;
    private boolean stillOn = true;
        
    private IRadioUser callback;
    
    
    public Reader(IRadioUser callback) {
        this.callback = callback;
        
        setupRadio();
    }

    /*
     * Note: cannot use MAC layer since API does not support promiscuous mode.
     */
    private void setupRadio() {
        radio.reset();
        int channel = Utils.getSystemProperty("radio.channel", Utils.getManifestProperty("DefaultChannelNumber", 26));
        System.out.println("  Radio set to channel " + channel);
        i802phyRadio.plmeSet(I802_15_4_PHY.PHY_CURRENT_CHANNEL, channel);
        int result = i802phyRadio.plmeSetTrxState(I802_15_4_PHY.RX_ON);
        while (I802_15_4_PHY.SUCCESS != result && !radio.isActive())
        {
            result = i802phyRadio.plmeSetTrxState(I802_15_4_PHY.RX_ON);
            System.out.println("starting the radio again, failed the first time");
        }
    }

    public void readPackets() {
        
        int overflowCnt = radio.getRxOverflow();
        
        stillOn = true;
        while ( !isOff ) {
            RadioPacket rp = null;
            try {
                
                if (radio.isRxDataWaiting())
                {
                    rp = RadioPacket.getDataPacket();


                    //System.out.println("got something");
                    i802phyRadio.pdDataIndication(rp);  // receive new packet
                    //mac.mcpsDataIndication(rp);
                    rp.decodeFrameControl();

                    if (rp.isAck()) {
                        //printQueue.put(rp);
                        //callback.receiveComplete(rp.());

                        // care?
                    } 
                    else
                    {
                        LowPanPacket lpp = new LowPanPacket(rp);
                        //printQueue.put(lpp);
                        //callback.receiveComplete(lpp.toString());
                        callback.receiveComplete(getMessage(rp));
                    }
                    int o = radio.getRxOverflow();
                    if (o > overflowCnt) {
                        overflowCnt = o;
                        System.out.println("--- radio buffer overflow: " + overflowCnt);
                    }
                    else
                    {
                        callback.receiveComplete( getMessage(rp) );
                    }
                }
            }
            
            catch (Exception ex) {
                System.out.println("Error in receive loop: " + ex);
                if (rp != null) {
                    if (rp.isAck()) {
                        System.out.println("  while processing an ACK packet");
                    } else {
                        System.out.println("  while processing packet from " + Long.toString(rp.getSourceAddress(), 16) +
                                " to " + Long.toString(rp.getDestinationAddress(), 16));
                        byte buffer[] = new byte[rp.getMACPayloadLength()];
                        for (int i = 0; i < rp.getMACPayloadLength(); i++) {
                            buffer[i] = rp.getMACPayloadAt(i);
                        }
                        
                    }
                }
                //ex.printStackTrace();
            }
        }
        stillOn = false;
    }
    
    private String getMessage(RadioPacket rp)
    {
        LowPanPacket lpp = new LowPanPacket(rp);
        
       
        
        boolean done = false;
        //System.out.println("Got packet size " + (lpp.getHeaderLength() ) + " - payload length "+ rp.getMACPayloadLength() + " or " + lpp.getPayloadSize() + " ");
        //byte[] buffer = new byte[rp.getMACPayloadLength()];
        byte[] buffer = new byte[rp.getMACPayloadLength()];
        String retString = "";
        //for (int i = 0; i < rp.getMACPayloadLength(); i++)
        for (int i = 0; i < rp.getMACPayloadLength(); i++)
        {
            
            buffer[i] = rp.getMACPayloadAt(i);
            //System.out.print((char)buffer[i]);
        }
        //System.out.println();
        //return PrettyPrint.prettyPrint( buffer, 0, buffer.length);
        boolean allowRead = false;

        for (int i = 0; i < buffer.length && !done; i++)
        {
            //if (buffer[i] != 0)
           
            if (buffer[i] == '>')
                allowRead = true;
            else if ( buffer[i] == '<')
                allowRead = false;
            else if (allowRead)
      //          if ( ( in.charAt(i)  >= 'a' && in.charAt(i)  <= 'z' ) || ( in.charAt(i)  >= 'A' && in.charAt(i)  <= 'Z' ) || ( in.charAt(i)  >= '0' && in.charAt(i)  <= '9' ) || in.charAt(i) == ' ' || in.charAt(i) == '-' || in.charAt(i) == ':' || in.charAt(i) == ';' )
      //                m = m + in.charAt(i);

                if ((buffer[i] >= 'a' && buffer[i] <= 'z') || (buffer[i] >= 'A' && buffer[i] <= 'Z') ||
                        (buffer[i] >= '0' && buffer[i] <= '9') || buffer[i] == ' ' || 
                        buffer[i] == '-' || buffer[i] == ':' || buffer[i] == ';' )
                    retString += (char)buffer[i];
        //else
            //    done = true;
        }
        return retString;
        
    }

    public void run() {
        readPackets();
    }

     public void turnOn ()
    {
        isOff = false;
        readPackets();
    }
    
    public long turnOff()
    {
        long ticks = System.currentTimeMillis();
        if (isOff)
        {
            isOff = false;
            while (stillOn)
                try {
                Thread.sleep(1);
            } catch (InterruptedException ex) {
                // nothing!
            }
        }
        
        return  System.currentTimeMillis() - ticks;
        //return 0;
    }

}
