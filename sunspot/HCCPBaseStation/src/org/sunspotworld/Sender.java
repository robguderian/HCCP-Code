package org.sunspotworld;

/*
 *
 * Created on Feb 8, 2010 5:41:11 PM;
 *
 * Track the social interactions of people.  See who has contacted who.
 * Exchange ids with other surrounding spots
 *
 * Broadcast the id of this spot every n seconds
 * Always listen for incoming ids
 * We don't explicitly send a return id since there is a set
 * broadcast interval
 *
 */



//import com.sun.spot.peripheral.Spot;


import javax.microedition.io.*;







// for radio power control
import com.sun.spot.peripheral.ISpot;
import com.sun.spot.peripheral.Spot;
import com.sun.spot.peripheral.radio.*;

/**
 * The startApp method of this class is called by the VM to start the
 * application.
 *
 * The manifest specifies this class as MIDlet-1, which means it will
 * be selected for execution.
 */
public class Sender
{


    private static final short PAN_ID = (short)0x3;
    private I802_15_4_MAC mac = null;
    
    long thisAddressAsNumber;
    DatagramConnection dgBConn;

    //  private static IProprietaryRadio radio = Spot.getInstance().getIProprietaryRadio();
    private static I802_15_4_PHY i802phyRadio = Spot.getInstance().getI802_15_4_PHY();
    

    // for radio power control
    private static final ISpot SPOT = Spot.getInstance();
    private static final IRadioPolicyManager radioPolicy = SPOT.getRadioPolicyManager();

    private int radioPower = -9;

    boolean radioBusy = false;


    public Sender()
    {

        thisAddressAsNumber = RadioFactory.getRadioPolicyManager().getIEEEAddress();

        // set the radio power
        radioPolicy.setOutputPower(radioPower);

        mac = RadioFactory.getI802_15_4_MAC();
        mac.mlmeStart(PAN_ID, 26);
        mac.mlmeSet(I802_15_4_MAC.MAC_RX_ON_WHEN_IDLE, 1);

    }


    /*
     *  Send message Logic
     */



    public void send(String m)
    {
        sendBroadcastMessage(m);
    }

    public void sendBroadcastMessage(String in){


        //Blast packets staight from the radio!
        String m = ">>" + in +"<...";
        RadioPacket radioPacket = RadioPacket.getBroadcastPacket();

        
        radioBusy = true;

        // Check if the air is clear
        /*while (i802phyRadio.plmeCCARequest() != i802phyRadio.IDLE)
        {
            Utils.sleep(100);
        }*/
        if (i802phyRadio.plmeCCARequest() == i802phyRadio.IDLE)
        {

            
            System.out.println("Sending " + m);
            radioPacket.setDestinationPanID(PAN_ID);
            //radioPacket.setSourceAddress(addrToLong(packet.getLinkSource()));
            radioPacket.setMACPayloadLength(m.length() + 1);
            //radioPacket.setMACPayloadAt(0, (byte)0x03);
            for (int i = 0; i< m.length(); i++)
            {
              radioPacket.setMACPayloadAt(i + 1, (byte)m.toCharArray()[i]);
            }
            /* remove ack request... since we do not handle that currently -
             *  works on data packets...*/
            //	try {
            //	  radioPacket.setMACPayloadAt(-21, (byte) (radioPacket.getMACPayloadAt(-21) & (~0x20)));
            //	} catch (Exception e) {}
            mac.mcpsDataRequest(radioPacket);



        }

        radioBusy = false;
    }





}

