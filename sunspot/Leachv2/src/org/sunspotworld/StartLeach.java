/*
 * To configure this properly, OTA needs to be off
 * ant 
 */



package org.sunspotworld;



import javax.microedition.midlet.*;
import Util.Utility;
import com.sun.spot.peripheral.ISleepManager;
import com.sun.spot.peripheral.Spot;
import com.sun.spot.peripheral.radio.I802_15_4_PHY;
import com.sun.spot.peripheral.radio.RadioFactory;
import com.sun.spot.util.IEEEAddress;
import java.util.Vector;
import com.sun.spot.util.Utils;
import com.sun.squawk.GC;
import com.sun.squawk.GarbageCollector;
import com.sun.squawk.VM;
import java.util.Date;

public  class StartLeach  {

    // This version of Java can't do enums. 
    final static int RECLUSTER =1, CHOOSE_CLUSTER=2,  WAIT_FOR_SCHEDULE=3, WAIT_FOR_TURN=4, RUN=5, WAIT_FOR_RECLUSTER=6, BOOT=7;
    
    final static int MIN_MEM_SIZE = 20000;
    
    final static long SLEEP_WAKE_TIME = 30;
    
    final int debugLevel = 2;
    
    private String name = "Thing!";
    
    private CallbackTimer nextStage;
    private CallbackTimer nextReading;
    private CallbackTimer scheduledSend;
    private CallbackTimer myWatchdog;
    private ICallbackable nextStageCallback;
    private ICallbackable nextReadingCallback;
    private ICallbackable scheduledSendCallback;
    private ICallbackable myWatchdogCallback;
    
    private IRadioUser receiver;
    
    
    final static int DEFAULT_READING_FREQ = 60000;
    final static double DEFAULT_PERCENTAGE_CH = 0.05;
    final static int DEFAULT_BEACON_RANK = 100;
    final static double RELIABLE_TIME = 60; // 60 s
    private double chanceOfBeingCH;
    private boolean debug = true;
    private int round = 0;
    private int lastRoundAsClusterhead = 0;
    
    private int runtimeAsClusterhead = 0;
    private int runtimeAsSensor = 0;
    private int numberOfMessagesSent = 0;
    
    String  id;
    
    int LStage;
    private boolean isClusterhead = true; // <-- true for startup sequence
    
    private int following = -1;
    
    long delay = 0;
    long runtime = 0;
    
    int numberOfMessagesCreated = 0;
    int messagesThrough = 0;
    // beacon routing - keep track of when beacon was heard.
    int beaconRank = DEFAULT_BEACON_RANK;
    double beaconTime = Double.MIN_VALUE; // TODO send time from sink, propogate.
    //ArrayList<Node> clustermembers = new ArrayList<Node>();
    final int MAX_QUEUE_SIZE = 200;
    Vector clustermembers = new Vector();
    Vector messageQueue = new Vector(MAX_QUEUE_SIZE);
    
    private String msgToSend;
    
    private Sender send = new Sender();
    
    Reader reader;
    
    ISleepManager sleepManager;
    I802_15_4_PHY phy = RadioFactory.getI802_15_4_PHY();
    
    long lastNextStage;
    
    int delayToChooseCluster = 0;

    boolean error = false;
    
    
    
    
    public void startLeach()  {


        setup();


        System.out.println("Booted! - Go time! May 29");
        while(!error)
        {
            //System.out.println("goodbye cruel world :P");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
           
        }
        
        
    }

    protected void setup() {
        
        receiver = new ReceiveComplete();
        
        
        lastNextStage = new Date().getTime();
        
        // make a callback to this class              
        nextStageCallback = new NextStage();
        nextReadingCallback = new NextReading();
        scheduledSendCallback = new ScheduledSend();
        myWatchdogCallback = new Watchdog();
        nextStage = new CallbackTimer(nextStageCallback);
        nextReading = new CallbackTimer(nextReadingCallback);
        scheduledSend = new CallbackTimer(scheduledSendCallback);
        myWatchdog = new CallbackTimer(myWatchdogCallback);
    
        
        
        LStage = BOOT;

        chanceOfBeingCH = DEFAULT_PERCENTAGE_CH;

        // Set id.
        // the system call gets the entire address.
        // mod by a reasonably large number to make the number more tolerable
        
        //id = (int)RadioFactory.getRadioPolicyManager().getIEEEAddress() % LeachConstants.ADDRESS_MOD_BY;
        id = IEEEAddress.toDottedHex(RadioFactory.getRadioPolicyManager().getIEEEAddress() ).substring(15);
        
        
        /*
         * Start up the radio.
         */
        reader = new Reader(receiver);
        reader.start();
        
        
        //nextReading.scheduleCallback(1);
        
        sleepManager = Spot.getInstance().getSleepManager();
        sleepManager.enableDeepSleep();
        sleepManager.enableDiagnosticMode();
        
        myWatchdog.scheduleCallback( LeachConstants.TOTAL_RUN_TIME );     
        
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            throw new MIDletStateChangeException();
        }
    }

    protected void sleep(long milliseconds) {

        System.out.println("Requested sleep: " + milliseconds);
        if (milliseconds > 0)
        {
            //reader.interrupt();
           reader.turnOff();
           
           //TODO check for any callbacks that could finish during that time
           //if (nextReading)
           
            int error = phy.plmeSetTrxState(I802_15_4_PHY.TRX_OFF);
            RadioFactory.getRadioPolicyManager().setRxOn(false);
            
            System.out.println("turned off in " + reader.turnOff());
       
            if (error == I802_15_4_PHY.SUCCESS)
                Utils.sleep(milliseconds);
            else
                System.out.println("Didn't sleep");
            
            System.out.println("System has slept " + sleepManager.getTotalDeepSleepTime() + " total " + sleepManager.getDeepSleepCount() + " sleeps");
            phy.plmeSetTrxState(I802_15_4_PHY.RX_ON);
            RadioFactory.getRadioPolicyManager().setRxOn(true);
            
            reader.turnOn();
            
        }
        System.out.println("done sleep");
    }

    public void timerCallback() {
        nextLeachStage();
    }

    private void nextLeachStage() {
        
        
        lastNextStage = new Date().getTime();
        
        
        switch (LStage) {
            case RECLUSTER:
                LStage = CHOOSE_CLUSTER;
                startChooseCluster();
                break;
            case CHOOSE_CLUSTER:
                LStage = WAIT_FOR_SCHEDULE;
                startWaitForSchedule();
                break;
            case WAIT_FOR_SCHEDULE:
                LStage = WAIT_FOR_TURN;
                startWaitForTurn();
                break;
            case WAIT_FOR_TURN:
                LStage = RUN;
                startRun();
                break;
            case RUN:
                LStage = WAIT_FOR_RECLUSTER;
                startWaitForRecluster();
                break;
            case WAIT_FOR_RECLUSTER:
                LStage = RECLUSTER;
                startRecluster();
                break;
            case BOOT:
                LStage = RECLUSTER;
                startRecluster();
                break;


        }
        System.out.println("memory: "+ GC.freeMemory() + " mq:" + messageQueue.size());
        while(GC.freeMemory() < MIN_MEM_SIZE && messageQueue.size() > 5 )
        {
            messageQueue.removeElementAt(0);
            messageQueue.removeElementAt(0);
            messageQueue.removeElementAt(0);
            messageQueue.removeElementAt(0);
            messageQueue.removeElementAt(0);
            messageQueue.trimToSize();
            //System.gc();
            VM.collectGarbage(true);
        }
        
        GarbageCollector g = GC.getCollector();
        //System.gc();
        VM.collectGarbage(true);
        System.out.println("memory: "+ GC.freeMemory() + " " + g.getBytesFreedTotal() + " " +  g.getTotalFullGCTime() + " " + Thread.activeCount() );
        
        
    }
   

    public boolean isBaseStation() {
        return false;
    }

    private boolean chooseToBeClusterhead() {
        // the probability of a node becoming a clusterhead
        // is a double... from 0-1
        boolean clusterhead = false;
        double threshold;

        // the sink is always a clusterhead
        if (chanceOfBeingCH > 0 && (1 / chanceOfBeingCH) - lastRoundAsClusterhead <= 0 && chanceOfBeingCH > 0 && chanceOfBeingCH < 1) // check if this node is allowed to be a clusterhead (n in G)
        {
            threshold = (chanceOfBeingCH) / (1 - chanceOfBeingCH * (round % (int) (1 / chanceOfBeingCH)));

            // now, choose a random number
            // if it's less than the threshold, become a clusterhead
            if (Utility.rand01() < threshold) {
                clusterhead = true;
            }
        } else if (chanceOfBeingCH >= 1) {
            clusterhead = true;
        }

        return clusterhead;

    }

    public double getRSSI() {
        // TODO
        return 2;
    }

    public boolean isClusterhead() {
        return isClusterhead;
    }

    public int getBeaconRank() {
        return beaconRank;
    }

    private int getBeaconRank(String message) {
        int theRank = DEFAULT_BEACON_RANK;

        String[] parts = Utils.split(message, ';');
        if (parts.length > 2) {
            if (Utility.isInteger(parts[2])) {
                theRank = Integer.parseInt(parts[2]);
            }

        }

        return theRank;
    }
    
    private int getReclusterTime(String message) {
        int theRank = -99;

        String[] parts = Utils.split(message, ';');
        if (parts.length > 1) {
            if (Utility.isInteger(parts[1])) {
                theRank = Integer.parseInt(parts[1]);
            }

        }

        return theRank;
    }

    private double getBeaconTime(String message) {
        double theTime = 0;

        String[] parts = Utils.split(message, ';');
        if (parts.length > 1) {
            if (Utility.isDouble(parts[2])) {
                theTime = Double.parseDouble(parts[2]);
            }

        }

        return theTime;
    }

    
    protected void readingCallback() {
        // add a message to the outgoing queue
        //if (debug)
        //System.out.println("reading created. Making message");
        numberOfMessagesCreated++;
        addMessageToList("m"+id+":reading from " + name + " "+numberOfMessagesCreated);

        // create the next reading event

        //new SensorReading().schedule(Rand1.expon(readings, readingFrequency));
        //nextReading.cancel();
        //nextReading = new CallbackTimer(nextReadingCallback);
        //nextReading.scheduleCallback(DEFAULT_READING_FREQ);

    }

    public void sendMessage() {
        // send the message to this node's clusterhead
        //Packet toSend = new Packet( this, getMessage(), baud);

        //following.assignMessage(toSend);

        // send the message to all nodes in listening range
        //msgHandler.broadcast( toSend );

        if (debug) {
            System.out.println(this.name + " sending a message");
        }

        String m = getMessage();
        if (m == null) {
            System.out.println("Message is null in send?");
        }
        //if (!msgHandler.isSending()) {
        else
        {
            send.send(m);
        }


    }

    
    public void sendComplete() {
        // TODO Auto-generated method stub


        if (LStage == RUN) {
            numberOfMessagesSent++;
            // blast next packet
            if (messageQueue.size() > 0 && following != -1) {
                send.send(this.getMessage());
            }
        }

        // else
        // shut up! We'll send again when possible

    }

    
    public void receiveCompleteCallback(String m) {
               
        // TODO - process it
        
        if (m.length() > 0)
        {
            System.out.println("Sensor Node received a message! '" +m + "'");
            if (LStage == RECLUSTER)
            {

                // TODO - care about RSSI and/or beacon rank
                //if (m.startsWith("CH") && !isClusterhead) {
                if (startsWith(m, "CH") && !isClusterhead)
                {
                    int icIn = -1;
                    /*if (Utility.isInteger( Utils.split(m, '-')[1] ))
                        icIn = Integer.parseInt(Utils.split(m, '-')[1]);*/
                    String[] parts = Utils.split(m,';');
                    if (parts.length >= 1 && Utility.isInteger( parts[0].substring(2) ))
                        icIn = Integer.parseInt( parts[0].substring(2) );
                    if (parts.length >= 2 && Utility.isInteger( parts[1] ))
                    {
                        // reset the timer
                        nextStage.cancel();
                        nextStage = new CallbackTimer(nextStageCallback);
                        nextStage.scheduleCallback(Integer.parseInt(parts[1]));
                        
                    }
                    if (parts.length >= 3 && Utility.isInteger( parts[2] ))
                        beaconRank = Integer.parseInt( parts[2] );
                    /*if (beaconRank > icIn.getBeaconRank() && beaconTime + RELIABLE_TIME >= getBeaconTime(message)) {
                        beaconRank = icIn.getBeaconRank() + 1;
                        beaconTime = getBeaconTime(message);
                    }*/

                    if (following == -1) {
                        following = icIn;
                    }
                    System.out.println(id + " going to follow " + following);
                    /*else {
                        IClusterhead icFollowing = (IClusterhead) following;
                        //IClusterhead icNew = (IClusterhead)  Node.getNodeFromId(from);
                        int chRank = getBeaconRank(message);

                        if (chRank < icFollowing.getBeaconRank()) {
                            following = Node.getNodeFromId(from);
                        } else if (getRSSI(following) < getRSSI(Node.getNodeFromId(from))) {
                            following = Node.getNodeFromId(from);
                        }


                    }*/
                }

            } 
            else if (LStage == CHOOSE_CLUSTER) 
            {

                if (startsWith(m, "FL") && isClusterhead)
                {
                    // the rest of the message should be the id of 'who' this node is going to be following

                    String[] parts = Utils.split(m,'-');
                    if (parts.length > 1) {
                        //int newId = Integer.parseInt(parts[1]);
                        
                        boolean inList = false;
                        for (int i = 0; i < clustermembers.size(); i++)
                        {
                            String temp = (String)clustermembers.elementAt(i);
                            if ( temp.equals(parts[1]) )
                                    inList = true;
                        }
                        if (!inList)
                        {
                            System.out.println("being followed by "+parts[1]);
                            clustermembers.addElement(  parts[1] );
                        }
                    }
                    
                }
            } 
            else if (LStage == WAIT_FOR_SCHEDULE) 
            {
                // parse the input, if possible

                if (!isClusterhead) {
                    System.out.println("Parsing schedule");
                    delay = parseSchedule(m) * 1000; // *1000 to make s ms

                    if (m.length() > 0 && delay >= 0) {
                        if (Utility.isInteger(Utils.split(m, ';')[0]))
                        {
                            runtime = Integer.parseInt(Utils.split(m, ';')[0]) *1000;
                            System.out.println("Found runtime of: " + runtime);
                        }
                    }
                }

            } //else if (isClusterhead && c.isForBS() && LStage == RUN) {
            else if (isClusterhead  && LStage == RUN) {
                if (startsWith(m, "m") && m.length() > 1)
                {
                    String[] parts = Utils.split(m.substring(1), ':');
                    if ( inList(parts[0]) )
                    {
                        this.addMessageToList(parts[1]); // pass it on
                        messagesThrough++;
                    }
                    
                }
                
            }
            else if (LStage == BOOT)
            {
                System.out.println("in boot");
                //if (m.startsWith("CH")) 
                if (startsWith(m, "CH"))
                {
                    /*if (Utility.isInteger(Utils.split(m, '-')[1]))
                        following = Integer.parseInt(Utils.split(m, '-')[1]);*/
                    String[] parts = Utils.split(m,';');
                    if (parts.length >= 1 && Utility.isInteger( parts[0].substring(2) ));
                        following = Integer.parseInt( parts[0].substring(2) );
                    if (parts.length >= 2 && Utility.isInteger( parts[1] ));
                        delayToChooseCluster = Integer.parseInt( parts[1] );
                    if (parts.length >= 3 && Utility.isInteger( parts[2] ));
                        beaconRank = Integer.parseInt( parts[2] );
                        
                    System.out.println("going to wait for " + delayToChooseCluster);
                        
                    isClusterhead=false;
                    nextStage.cancel();
                    System.out.println(id + " was listening for announcments. Got one. Following " + following);
                    nextLeachStage();
                }
            }
            
            
            // more watchdog checks
            long now= new Date().getTime();
            if (now > lastNextStage + LeachConstants.TOTAL_RUN_TIME * 5 && LStage != BOOT )
            {
                // reset everything
                System.out.println("stalled? resetting.");
                
                error = true; // this will basically kill everything.
                
                new Exception().printStackTrace();
                LStage = BOOT;
                nextStage.cancel(); // HUP. now.
                
            }

            /*else if (c.getDestination() == this.id || c instanceof BroadcastMessage) {
                System.out.println("Got a message for me... now what? Says " + message);
            }*/
            /*
             * else 
             * 		eavesdrop?
             */

        }

    }
    
    
    private boolean startsWith(String word, String prefix)
    {
        for (int i = 0; i < prefix.length(); i++)
        {
            //System.out.println(">" + word.charAt(i) + "< >" +  prefix.charAt(i) + "< " +( word.charAt(i) != prefix.charAt(i)));
            if (word.charAt(i) != prefix.charAt(i))
                return false;
        }
        return true;
    }
    
    private void addMessageToList(String m)
    {
        System.out.println("Adding message to queue");
        if (m != null && m.length() > 1 && GC.freeMemory() > MIN_MEM_SIZE && messageQueue.size() < MAX_QUEUE_SIZE)
        {
            messageQueue.addElement(m);            
        }
        else 
        {
            System.out.println("message queue full!");
            while(GC.freeMemory() < MIN_MEM_SIZE && messageQueue.size() > 5 )
            {
                messageQueue.removeElementAt(0);
                messageQueue.removeElementAt(0);
                messageQueue.removeElementAt(0);
                messageQueue.removeElementAt(0);
                messageQueue.removeElementAt(0);
                messageQueue.trimToSize();
                GarbageCollector g = GC.getCollector();
                //System.gc();
                VM.collectGarbage(true);
            }
        }
    }
    
    private String getMessage()
    {
        String retString = null;
        if (messageQueue.size() > 0)
        {
            retString = (String)messageQueue.firstElement();
            messageQueue.removeElementAt(0);
        }
        
        return retString;
            
    }
            

    private String createSchedule() {
        int delay = 0;
        int runtime = 0;
        if (clustermembers.size() > 0) {
            runtime = (int) Math.ceil(LeachConstants.TOTAL_RUN_TIME / clustermembers.size()) / 1000; // put it into seconds
        }
        String theString = runtime + ";";
        //for (Node n : clustermembers) {
        for (int i = 0; i < clustermembers.size(); i++)
        {
            
            theString += (String)clustermembers.elementAt(i) + ":" + delay + ";"; // put it into seconds
            delay += runtime;
        }
        return theString;
    }

    
    
    
    
    
    
    /*
     * Leach stage methods
     */
    private void startRecluster() {
        // Turn the radio on (probably waking from sleep)
        //this.msgHandler.on();
        
        if (debugLevel > 1)
                System.out.println("Start Recluster");
        
        
        delay = 0;
        runtime = 0;
        clustermembers.removeAllElements();
        
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        
        if ((isClusterhead || chooseToBeClusterhead()) && delayToChooseCluster == 0) {
            // send a message
            isClusterhead = true;

            // send a 'isClusterhead' announcement
            // broadcast will happen 'all at once', but dealt with with backoffs.

            //if (debug)
            System.out.println(name + "  chooses to be a clusterhead");

            //msgHandler.broadcast( new Packet(this, new BroadcastMessage("CH"+this.id, id, 4 ), this.baud ));
            //msgHandler.scheduleBroadcast(new BroadcastMessage("CH"+this.id, id, 4 ), Utility.randXY( 0, LeachConstants.RECLUSTER_TIME/2 ) );
            //String message = "CH" + this.id;
            int timeTo = Utility.randXY(  (int)LeachConstants.RECLUSTER_TIME / 8 ,(int)LeachConstants.RECLUSTER_TIME / 2);
            String message = "CH" + this.id + ";" + (LeachConstants.RECLUSTER_TIME - timeTo) + ";" + beaconRank ;
            //if (beaconRank < DEFAULT_BEACON_RANK) {  // is the sink, will always have a beacon rank
            //}
            scheduleBroadcast(message, timeTo);
            nextStage.scheduleCallback(LeachConstants.RECLUSTER_TIME);
        } else {
            isClusterhead = false;
            
            if (delayToChooseCluster > 0)
                nextStage.scheduleCallback(delayToChooseCluster);
            else
                nextStage.scheduleCallback(LeachConstants.RECLUSTER_TIME);
            delayToChooseCluster = 0;

            // wait until next phase
            // listen for CH announcements

        }
        
        
        // create some messages
        for (int i = 0 ;i < LeachConstants.NUMBER_OF_MESSAGE_TO_CREATE_PER_ROUND; i++)
            readingCallback();
        
        

    }

    private void startChooseCluster() {
        // TODO Auto-generated method stub

        //msgHandler.on();
        if (debugLevel > 1)
                System.out.println("Start Choose Cluster");

        if (following != -1) {
            //msgHandler.scheduleBroadcast(new BroadcastMessage("FL" + following.getId(), this.id, 3, following.getId()), Utility.randXY( 0, LeachConstants.CHOOSE_CLUSTERHEAD_TIME / 2));
            scheduleBroadcast("FL-" + id + "-" + following , Utility.randXY( (int) LeachConstants.CHOOSE_CLUSTERHEAD_TIME / 10, (int) LeachConstants.CHOOSE_CLUSTERHEAD_TIME / 2));
        }
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(LeachConstants.CHOOSE_CLUSTERHEAD_TIME);

    }

    private void startWaitForSchedule() {

        // restart the radio
        //msgHandler.off();
        //msgHandler.on();
        if (debugLevel > 1)
                System.out.println("Start Wait for Schedule");

        if (isClusterhead) {
            // announce schedule
            String schedule = createSchedule();
            // delay a broadcast, to try to space the annoucements.

            //msgHandler.scheduleBroadcast(new BroadcastMessage(schedule, this.id, schedule.length()), Utility.randXY( 0, LeachConstants.WAIT_FOR_SCHEDULE_TIME / 2));
            scheduleBroadcast(schedule, Utility.randXY( (int) LeachConstants.WAIT_FOR_SCHEDULE_TIME / 8,(int) LeachConstants.WAIT_FOR_SCHEDULE_TIME / 2));
            System.out.println(name + "  broadcasting " + schedule);
            runtime = LeachConstants.TOTAL_RUN_TIME;
        }

        // listen for schedule. 
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(LeachConstants.WAIT_FOR_SCHEDULE_TIME);

    }

    private void startWaitForTurn() {
        //msgHandler.off();
        if (debugLevel > 1)
                System.out.println("Waiting for turn");
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(delay);

        sleep(delay - SLEEP_WAKE_TIME);
    }

    private void startRun() {

        long startTime = new Date().getTime();
        long endTime = startTime + runtime;
        if (debugLevel > 1)
                System.out.println("Running... for " + runtime);
        
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(runtime);
        
        
        if (isClusterhead) {
            runtimeAsClusterhead += runtime;
        } 
        else if (following != -1)
        {
            runtimeAsSensor += runtime;
            System.out.println(name + " is going to run for " + runtime);
            
            while (new Date().getTime() < endTime - LeachConstants.EARLY_END_SEND && messageQueue.size() > 0)
            {
                // get the next message
                String message =(String)messageQueue.elementAt(0);
                
                // TDMA - no acking. Assume it sent properly
                System.out.println("Sending: "+ message);
                send.send(message);
                messageQueue.removeElementAt(0);
            }
            
            // sleep!
            if (new Date().getTime() + 1000 < endTime) // if endtime is more than a second away
                sleep(endTime - new Date().getTime());
            
        }


        
        
        
        

    }

    private void startWaitForRecluster() {
        
        if (debugLevel > 1)
                System.out.println("Waiting for recluster");
        
        // print out the message queue
        lastRoundAsClusterhead++;
        System.out.println("messageQueue has " + messageQueue.size() + " messages");
        //msgHandler.off();
        
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(LeachConstants.TOTAL_RUN_TIME - runtime - delay);
        
        following = -1;
        isClusterhead = false;
        
        System.out.println("sleep!");
        sleep(LeachConstants.TOTAL_RUN_TIME - runtime - delay - SLEEP_WAKE_TIME);

        /*Message m = (Message)this.messageQueue.view(List.FIRST);
        System.out.println("messageQueue has " + messageQueue.size()+ " messages" );
        while (m != null)
        {
        System.out.println(m.getInfo());
        m = (Message)messageQueue.view(List.NEXT);
        }*/

    }

    private int parseSchedule(String schedule) {
        // return the delay for this node
        int thisDelay = 0;
        String[] pairs = Utils.split(schedule, ';');
        String idAsString = "" + id;
        boolean foundDelay = false;
        for (int i = 0; i < pairs.length && !foundDelay; i++) {
            String[] nameValue = Utils.split(pairs[i], ':');
            if (nameValue.length == 2 && nameValue[0].equals(idAsString)) {
                
                thisDelay = Integer.parseInt(nameValue[1]);
                foundDelay = true;
                System.out.println("Found my crap... id " + idAsString + " " + thisDelay);
                        
            }
        }

        return thisDelay;

    }

    
    /*
     * There is a message in the send queue that is ready to be sent.
     * Handle appropriately (send at the right time)
     */
    protected void messageReadyToSend() {

        // Just send the message
        //if (debug)
        //	System.out.println("Sending a message");
        //if (!msgHandler.isSending() && LStage == RUN && !isClusterhead) {
        if ( LStage == RUN && !isClusterhead) {
            this.sendMessage();
        }
    }

    private void scheduleBroadcast(String m, long time)
    {
        msgToSend = m;
        if (scheduledSend != null)
            scheduledSend.cancel();
        scheduledSend = new CallbackTimer(scheduledSendCallback);
        scheduledSend.scheduleCallback(time);
    }
    
    
    
    
    boolean inList(String toCheck)
    {
        boolean isInList = false;
        for (int i = 0; i < clustermembers.size(); i++)
        {
            String temp = (String)clustermembers.elementAt(i);
            if ( temp.equals(toCheck) )
                    isInList = true;
        }
        return isInList;
        
    }

   

   
    
    class NextStage implements ICallbackable 
    {
        
  
        public void timerCallback()
        {
            if (debugLevel > 1)
                System.out.println("Next stage");
            nextLeachStage();
        }
    }
    
    class NextReading implements ICallbackable
    {
        public void timerCallback()
        {
            if (debugLevel > 1)
                System.out.println("Next reading");
            readingCallback();
        }
    }
    
    class ScheduledSend implements ICallbackable
    {
        public void timerCallback()
        {
            if (debugLevel > 1)
                System.out.println("scheduled send");
            if (msgToSend != null)
                send.send(msgToSend);
            msgToSend = null;
                       
        }
    }
    
    class Watchdog implements ICallbackable
    {
        private final long tooLong = LeachConstants.TOTAL_RUN_TIME * 5;
        
        public void timerCallback()
        {
            // check to see if the scheudle is still running.
            long now= new Date().getTime();
            if (now > lastNextStage + tooLong && LStage != BOOT )
            {
                // reset everything
                System.out.println("stalled? resetting.");
                
                LStage = BOOT;
                nextStage.scheduleCallback(0); // HUP. now.
            }
            else
            {
                lastNextStage = now;
            }
            
            // create the next check.
            myWatchdog.scheduleCallback(LeachConstants.TOTAL_RUN_TIME * 3);
        }
    }
    
    
    class ReceiveComplete implements IRadioUser
    {

        public void receiveComplete(String message) {
            //System.out.println("got message >" + message+"<");
            receiveCompleteCallback(message);
        }
        
    }
    
}



