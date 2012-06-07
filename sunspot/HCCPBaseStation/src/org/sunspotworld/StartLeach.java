/*
 * To configure this properly, OTA needs to be off
 * ant 
 */



package org.sunspotworld;



import javax.microedition.midlet.*;
import Util.Utility;
import com.sun.spot.peripheral.IBattery;
import com.sun.spot.peripheral.ISleepManager;
import com.sun.spot.peripheral.Spot;
import com.sun.spot.peripheral.radio.I802_15_4_PHY;
import com.sun.spot.peripheral.radio.RadioFactory;
import java.util.Vector;
import com.sun.spot.util.Utils;
import com.sun.squawk.GC;
import com.sun.squawk.GarbageCollector;
import java.util.Date;

public  class StartLeach  {

    // This version of Java can't do enums. 
    final static int ANNOUNCE_CANDIDACY =1, ANNOUNCE_CLUSTERHEAD=2, CHOOSE_CLUSTER=3,  WAIT_FOR_SCHEDULE=4, WAIT_FOR_TURN=5, RUN=6, WAIT_FOR_ROUNDTABLE=7, ROUNDTABLE = 8, BOOT=9, SLEEP_NO_RECLUSTER = 10, SLEEP = 11;
    
    final static int MIN_MEM_SIZE = 10000;
    
    final static long SLEEP_WAKE_TIME = 30;
    
    final int debugLevel = 2;
    
    private String name = "Thing!";
    
    private CallbackTimer nextStage;
    //private CallbackTimer nextReading;
    private CallbackTimer scheduledSend;
    private CallbackTimer myWatchdog;
    private ICallbackable nextStageCallback;
    //private ICallbackable nextReadingCallback;
    private ICallbackable scheduledSendCallback;
    private ICallbackable myWatchdogCallback;
    
    private IRadioUser receiver;
    
    
    final static int DEFAULT_READING_FREQ = 60000;
    final static double DEFAULT_PERCENTAGE_CH = 1;   // basestation always a clusterhead
    final static int DEFAULT_BEACON_RANK = 100;
    final static double RELIABLE_TIME = 60; // 60 s
    private double chanceOfBeingCH;
    private boolean debug = true;
    private int round = 0;
    private int lastRoundAsClusterhead = 0;
    
    private int runtimeAsClusterhead = 0;
    private int runtimeAsSensor = 0;
    private int numberOfMessagesSent = 0;
    
    int id;
    
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
    Vector clustermembers = new Vector();
    Vector messageQueue = new Vector();
    
    private String msgToSend;
    
    private Sender send = new Sender();
    
    Reader reader;
    
    ISleepManager sleepManager;
    I802_15_4_PHY phy = RadioFactory.getI802_15_4_PHY();
    
    long lastNextStage;
    
    int delayToChooseCluster = 0;
    
    double sensorMission = 0.5;   // This mote's cluster mission
    double startMemSize = GC.freeMemory();
    
    
    double thisRoundSuboptimalRating;
    
    IBattery battery;
    
    boolean isCandidate = false;
    int cycleCount = 0;
    boolean echoBeacon = false;

    public void startLeach()  {


        setup();


        System.out.println("Booted! HCCP basestation");
        while(true)
        {
            //System.out.println("goodbye cruel world :P");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
           // Thread.yield();
        }
        //notifyDestroyed(); // cause the MIDlet to exit
    }

    protected void setup() {
        
        receiver = new ReceiveComplete();
        
        
        lastNextStage = new Date().getTime();
        
        // make a callback to this class              
        nextStageCallback = new NextStage();
        //nextReadingCallback = new NextReading();  // basestation has no sensors
        scheduledSendCallback = new ScheduledSend();
        myWatchdogCallback = new Watchdog();
        nextStage = new CallbackTimer(nextStageCallback);
        //nextReading = new CallbackTimer(nextReadingCallback);
        scheduledSend = new CallbackTimer(scheduledSendCallback);
        myWatchdog = new CallbackTimer(myWatchdogCallback);
    
        
        
        LStage = BOOT;

        chanceOfBeingCH = DEFAULT_PERCENTAGE_CH;

        // Set id.
        // the system call gets the entire address.
        // mod by a reasonably large number to make the number more tolerable
        
        id = (int)RadioFactory.getRadioPolicyManager().getIEEEAddress() % LeachConstants.ADDRESS_MOD_BY;
        
        
        /*
         * Start up the radio.
         */
        reader = new Reader(receiver);
        reader.start();
        
        nextStage.scheduleCallback(60 );
        //nextReading.scheduleCallback(1);
        
        sleepManager = Spot.getInstance().getSleepManager();
        sleepManager.enableDeepSleep();
        sleepManager.enableDiagnosticMode();
        
        myWatchdog.scheduleCallback( LeachConstants.TOTAL_RUN_TIME);
        
        battery =  Spot.getInstance().getPowerController().getBattery();
        
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
        
        //System.out.println("memory: "+ GC.freeMemory() );
        GarbageCollector g = GC.getCollector();
        System.gc();
        
        lastNextStage = new Date().getTime();
        
        //System.out.println("memory: "+ GC.freeMemory() + " " + g.getBytesFreedTotal() + " " +  g.getTotalFullGCTime() + " " + Thread.activeCount() );
        switch (LStage) {
            case ANNOUNCE_CANDIDACY:
                LStage = ANNOUNCE_CLUSTERHEAD;
                startAnnounceClusterhead();
                break;
            case ANNOUNCE_CLUSTERHEAD:
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
                LStage = WAIT_FOR_ROUNDTABLE;
                startWaitForRecluster();
                break;
            case WAIT_FOR_ROUNDTABLE:
                    LStage = ROUNDTABLE;                   
                    startRoundtable();
                    break;
            case ROUNDTABLE:
                    
                    cycleCount++;
                    if (cycleCount < LeachConstants.NUMBER_OF_SCHEDULED_RUNS)
                    {
                            LStage = SLEEP_NO_RECLUSTER;
                            startNoReclusterSleep();
                    }
                    else
                    {
                            LStage = SLEEP;
                            startSleep();
                            cycleCount = 0;
                    }
                    break;
            case SLEEP:
                    LStage = ANNOUNCE_CANDIDACY;
                    lastRoundAsClusterhead++;
                    startAnnounceCandidacy();
                    break;
            case SLEEP_NO_RECLUSTER:
                    LStage = WAIT_FOR_TURN;
                    startWaitForTurn();
                    break;
                
            case BOOT:
                LStage = ANNOUNCE_CANDIDACY;
                startAnnounceCandidacy();
                break;


        }
    }
   

    public boolean isBaseStation() {
        return true;
    }

    private boolean chooseToBeClusterhead()
    {
       

        return true;

    }
    
    private double getClusterheadPercentage(double maxChChance)
	{
		
		double percentage = 0;
		percentage =  (maxChChance * LeachConstants.BATTERY_POWER_WEIGHT  * ( battery.getBatteryLevel() ) );
		
		// n% of available time based on mission
		// CH changce gets larger if this node wants to be a clusterhead
		percentage = percentage + (maxChChance * LeachConstants.SENSOR_MISSION_WEIGHT * (1-sensorMission) );
		
		// n% on messagequeue size
                
		percentage = percentage + (maxChChance * LeachConstants.MESSAGE_QUEUE_WEIGHT * (1- (  GC.freeMemory() - startMemSize )/(double)startMemSize ) );
		
		// n% of random
		percentage = percentage + (maxChChance * LeachConstants.RANDOM_WEIGHT * ( Utility.rand01() )); // TODO change RM
		
		// n% of duty cycling based on last round as CH.
		// less chance of being CH if I was just a clusterhead.
		// getting duty cycled somewhere else?
		percentage = percentage + (maxChChance * LeachConstants.DUTY_CYCLE_WEIGHT *  (1 -  Math.min(1, this.lastRoundAsClusterhead * maxChChance)));
		
		//System.out.println("Goodness delay is" + delay + " out of " + availableTime);
		
		
		// in case it overflows due to bad config.
		percentage = Math.min(percentage, maxChChance);
		return percentage;
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
            System.out.println("Base Station received a message! '" +m + "'");
            
            if (LStage == ANNOUNCE_CANDIDACY && startsWith(m, "CC"))
            {
                // if we hear anything, bail!
                if (isCandidate && scheduledSend != null && this.thisRoundSuboptimalRating < Utility.rand01())
                {
                    scheduledSend.cancel();
                    isCandidate = false;
                    isClusterhead = false;
                }
                String[] parts = Utils.split(m,';');
                if (parts.length >= 2 && Utility.isInteger( parts[1] ) )
                {
                    // reset the timer
                    nextStage.cancel();
                    nextStage = new CallbackTimer(nextStageCallback);
                    nextStage.scheduleCallback(Integer.parseInt(parts[1]));

                }
                
            }
            else if (LStage == ANNOUNCE_CLUSTERHEAD)
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
                    if (parts.length > 1 ) {
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

                    if (m.length() > 0 && delay > 0) {
                        if (Utility.isInteger(Utils.split(m, ';')[0]))
                            runtime = Integer.parseInt(Utils.split(m, ';')[0]) *1000;
                    }
                }

            } //else if (isClusterhead && c.isForBS() && LStage == RUN) {
            else if (isClusterhead  && LStage == RUN) {
                if (startsWith(m, "m") && m.length() > 1)
                {
                    String[] parts = Utils.split(m.substring(1), ':');
                    if ( inList(parts[0]) )
                    {
                        //this.addMessageToList(parts[1]); // pass it on
                        //messagesThrough++;
                        System.out.println("BaseStation completed a message: " + m);
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
            else if (LStage == ROUNDTABLE)
            {
               
                
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
        if (m != null && m.length() > 1 && GC.freeMemory() > MIN_MEM_SIZE)
            messageQueue.addElement(m);
        else 
            System.out.println("message queue full!");
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

    private void startAnnounceCandidacy(){
        //this.msgHandler.off();
    
        delay = 0;
        runtime = 0;
        clustermembers.removeAllElements();

        //isFailedClusterhead = false;  // TODO force failures
        
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(LeachConstants.RECLUSTER_TIME);



        if (debugLevel > 1)
                System.out.println("Start announce candidacy ");

        if (chooseToBeClusterhead() ){ // && !dealBreaker()) {


            if (LeachConstants.allowSuboptimalClusterheads) {
                //this.thisRoundSuboptimalRating = Utility.randU01(suboptimalClusterheadRM);
                //this.thisRoundSuboptimalRating = suboptimalClusterheadRM.nextDouble();

                // run leach-like
                if (LeachConstants.suboptimalClusterheadPercentage == 1) {
                    thisRoundSuboptimalRating = 1;
                } else if (beaconRank == 1) {
                    thisRoundSuboptimalRating = Math.min(1, (LeachConstants.firstOrderSuboptimalPercentage) / (1 - LeachConstants.firstOrderSuboptimalPercentage * (lastRoundAsClusterhead % (int) (1 / LeachConstants.firstOrderSuboptimalPercentage))));
                } else {
                    thisRoundSuboptimalRating = Math.min(1, (LeachConstants.suboptimalClusterheadPercentage) / (1 - LeachConstants.suboptimalClusterheadPercentage * (lastRoundAsClusterhead % (int) (1 / LeachConstants.suboptimalClusterheadPercentage))));
                }

                //System.out.println(thisRoundSuboptimalRating);
            }

            //this.msgHandler.on();
            
            // send a message
            isClusterhead = true;
            isCandidate = true;
            // send a 'isClusterhead' announcement
            // broadcast will happen 'all at once', but dealt with with backoffs.

            
            
            // send the CC message
            //clusterheadCandidate = new ClusterheadCandidateMessage();
            //clusterheadCandidate.schedule(getGoodnessDelay());
            long wait = getGoodnessDelay();
            scheduleBroadcast("CC"+this.id+";" + (LeachConstants.RECLUSTER_TIME-wait)+";0" , getGoodnessDelay());

        } else {
            isCandidate = false;
            isClusterhead = false;
            //msgHandler.off();
            // wait until next phase
            // listen for CH announcements

        }
       

    }


    private void startAnnounceClusterhead() {
        // actually announce the clusterhead, if this is one.

        // Turn the radio on (probably waking from sleep)

        //System.out.println(name + " starting announce at " + Sim.time());
       
        
        cycleCount = 0;
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(LeachConstants.RECLUSTER_TIME);
        
        if (debugLevel > 1)
                System.out.println("Start announce clusterhead");
        
        if (isClusterhead) {
            // send a message

            // send a 'isClusterhead' announcement
            // broadcast will happen 'all at once', but dealt with with backoffs.


            //msgHandler.broadcast( new Packet(this, new BroadcastMessage("CH"+this.id, id, 4 ), this.baud ));
            //msgHandler.scheduleBroadcast(new BroadcastMessage("CH"+this.id, id, 4 ), Rand1.uniform(clusterheadRM, 0, LeachConstants.RECLUSTER_TIME/2 ) );
            String message = "CH" + this.id;

            //numberOfTimesAsClusterhead++;
            lastRoundAsClusterhead = 0;

            // TODO re-add in the timing stuff.
            /*if (beaconRank < DEFAULT_BEACON_RANK) {
                int publishTime = (int) Math.ceil(Sim.time() - beaconReceivedAt + beaconTime) + 1;
                message += ";" + beaconRank + ";" + publishTime;
            }
            if (Configuration.verbose) {
                System.out.println(name + "  chooses to be a clusterhead " + message);
            }*/
            //msgHandler.scheduleBroadcast(new BroadcastMessage(message, id, 4), getGoodnessDelay());
            scheduleBroadcast("CH"+this.id, getGoodnessDelay());
        } else {
            // listen
        }

        
    }

    
    
    
    
    /*
     * Leach stage methods
     */
    

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

        // announce schedule
        String schedule = createSchedule();
        // delay a broadcast, to try to space the annoucements.

        //msgHandler.scheduleBroadcast(new BroadcastMessage(schedule, this.id, schedule.length()), Utility.randXY( 0, LeachConstants.WAIT_FOR_SCHEDULE_TIME / 2));
        scheduleBroadcast(schedule, Utility.randXY( (int) LeachConstants.WAIT_FOR_SCHEDULE_TIME / 8,(int) LeachConstants.WAIT_FOR_SCHEDULE_TIME / 2));
        System.out.println(name + "  broadcasting " + schedule);
        runtime = LeachConstants.TOTAL_RUN_TIME;

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
                System.out.println("Running...");
        
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(runtime);
        
                

    }

    private void startWaitForRecluster() {
        
        if (debugLevel > 1)
                System.out.println("Waiting for recluster");
        
        // print out the message queue
        lastRoundAsClusterhead++;
        //msgHandler.off();
        
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(LeachConstants.TOTAL_RUN_TIME - runtime - delay);
        
        following = -1;
        isClusterhead = false;
        
        sleep(LeachConstants.TOTAL_RUN_TIME - runtime - delay - SLEEP_WAKE_TIME);

        /*Message m = (Message)this.messageQueue.view(List.FIRST);
        System.out.println("messageQueue has " + messageQueue.size()+ " messages" );
        while (m != null)
        {
        System.out.println(m.getInfo());
        m = (Message)messageQueue.view(List.NEXT);
        }*/

    }
    
    private void startRoundtable()
    {
        // TODO
        // Send beacon messages. 
        // Opt out
        // All that good stuff.
        
        echoBeacon = false;
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(LeachConstants.ROUNDTABLE_TIME);
        
        if (LeachConstants.ROUNDTABLE_TIME > 0)
        {
            // send a G mesage
            scheduleBroadcast("G0", 10); // send a g message after a bit
        }
    }

    private void startSleep()
    {
        //msgHandler.off();
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(LeachConstants.SLEEP_TIME);

    }

    private void startNoReclusterSleep()
    {
            //msgHandler.off();
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(LeachConstants.SLEEP_NO_RECLUSTER_TIME);

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
   
    private long getGoodnessDelay() {
        double availableTime = 0.001;
        

        //String name = this.toString();

        //if (LStage ==  ROUNDTABLE_DISCUSSION)
        //	availableTime = HccpConstants.ROUNDTABLE_TIME;
        if (LStage == ANNOUNCE_CANDIDACY) {
            availableTime = LeachConstants.RECLUSTER_TIME;
        } else if (LStage == ANNOUNCE_CLUSTERHEAD) {
            availableTime = LeachConstants.RECLUSTER_TIME;
        }

       

        // delay a random amount of time.


        return (long)Utility.randXY(0, (int)availableTime/8);
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



