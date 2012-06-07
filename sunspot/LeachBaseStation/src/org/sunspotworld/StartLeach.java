package org.sunspotworld;



import javax.microedition.midlet.*;
import Util.Utility;
import com.sun.spot.peripheral.radio.RadioFactory;
import java.util.Vector;
import com.sun.spot.util.Utils;
import com.sun.squawk.GC;
import com.sun.squawk.GarbageCollector;
import java.util.Date;
import java.lang.Thread;

public  class StartLeach  {

    // This version of Java can't do enums. 
    final static int RECLUSTER =1, CHOOSE_CLUSTER=2,  WAIT_FOR_SCHEDULE=3, WAIT_FOR_TURN=4, RUN=5, WAIT_FOR_RECLUSTER=6, BOOT=7;
    
    final static int MIN_MEM_SIZE = 5000;
    
    final int debugLevel = 2;
    
    private String name = "BaseStation!";
    
    private CallbackTimer nextStage;
    private CallbackTimer nextReading;
    private CallbackTimer scheduledSend;
    private ICallbackable nextStageCallback;
    private ICallbackable nextReadingCallback;
    private ICallbackable scheduledSendCallback;
    
    private IRadioUser receiver;
    
    
    final static int DEFAULT_READING_FREQ = 60000;
    final static double DEFAULT_PERCENTAGE_CH = 1;
    final static int DEFAULT_BEACON_RANK = 0;           // is the sink
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
    
    private String following = null;
    
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
    
    Thread reader;

    public void startLeach()  {


        setup();


        System.out.println("Booted BaseStation! - Go time!");
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
        
        // make a callback to this class              
        nextStageCallback = new NextStage();
        //nextReadingCallback = new NextReading();
        scheduledSendCallback = new ScheduledSend();
        nextStage = new CallbackTimer(nextStageCallback);
        //nextReading = new CallbackTimer(nextReadingCallback);
        scheduledSend = new CallbackTimer(scheduledSendCallback);
    
        
        
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
        
        nextStage.scheduleCallback( 60 ); // basically a random number.... just go.
        //nextReading.scheduleCallback(1);
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

    protected static void sleep(long milliseconds) {
        Utils.sleep(milliseconds);
    }

    public void timerCallback() {
        nextLeachStage();
    }

    private void nextLeachStage() {
        System.out.println("memory: "+ GC.freeMemory() );
        GarbageCollector g = GC.getCollector();
        System.gc();
        
        System.out.println("memory: "+ GC.freeMemory() + " " + g.getBytesFreedTotal() + " " +  g.getTotalFullGCTime() + " " + Thread.activeCount() );
        //if (GC.freeMemory() < 1000)
          
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
        addMessageToList("Sensor reading from " + name + ":"+numberOfMessagesCreated);

        // create the next reading event

        //new SensorReading().schedule(Rand1.expon(readings, readingFrequency));
        nextReading = new CallbackTimer(nextReadingCallback);
        nextReading.scheduleCallback(DEFAULT_READING_FREQ);

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
            if (messageQueue.size() > 0 && following != null) {
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
            System.out.println("BaseStation received a message! '" +m + "'");
            if (LStage == RECLUSTER || LStage == CHOOSE_CLUSTER)
            {

                // TODO - care about RSSI and/or beacon rank
                //if (m.startsWith("CH") && !isClusterhead) {
                if (startsWith(m, "CH") && !isClusterhead)
                {
                    String icIn = null;
                    int beaconIn= 99;
                    if (Utility.isInteger( Utils.split(m, ';')[1] ))
                        beaconIn = Integer.parseInt(Utils.split(m, ';')[1]);
                    //if (Utility.isInteger( m.substring(2) ));
                    //    icIn = Integer.parseInt( m.substring(2) );
                    /*if (beaconRank > icIn.getBeaconRank() && beaconTime + RELIABLE_TIME >= getBeaconTime(message)) {
                        beaconRank = icIn.getBeaconRank() + 1;
                        beaconTime = getBeaconTime(message);
                    }*/
                    if (beaconRank > beaconIn ){ // && beaconTime + RELIABLE_TIME >= getBeaconTime(message)) {
                        beaconRank = beaconIn + 1;
                        beaconTime = getBeaconTime(m);
                        
                        long reclusterDelay = getReclusterTime(m);
                        
                        if (reclusterDelay > 0)
                        {
                            //nextStage.cancel();
                            //nextStage.scheduleCallback(delay); // because it's in milliseconds
                            nextStage.cancel();
                            nextStage = new CallbackTimer(nextStageCallback);
                            nextStage.scheduleCallback(delay);
                        }
                    }

                    if (following == null) {
                        following =  Utils.split(m, ';')[0].substring(2);
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

                //if (m.startsWith("FL") && isClusterhead) {
                if (startsWith(m, "FL") && isClusterhead)
                {
                    // the rest of the message should be the id of 'who' this node is going to be following

                    String[] parts = Utils.split(m,'-');
                    if ( parts.length > 1 ) {
                        //int newId = Integer.parseInt(parts[1]);
                        
                        
                        if (!inList(parts[1]))
                        {
                            System.out.println("being followed by "+parts[1]);
                            clustermembers.addElement(  parts[1] );
                        }
                    }
                    /*if (Utility.isInteger( m.substring(2) ))
                    {

                        System.out.println("being followed by "+Integer.parseInt( m.substring(2) ));
                        clustermembers.addElement(  Integer.valueOf( m.substring(2) ) );
                    }*/
                }
            } 
            else if (LStage == WAIT_FOR_SCHEDULE) 
            {
                // parse the input, if possible

                if (!isClusterhead) {
                    delay = parseSchedule(m);

                    if (m.length() > 0 && delay >= 0) {
                        if (Utility.isInteger(Utils.split(m, ';')[0]))
                            runtime = Integer.parseInt(Utils.split(m, ';')[0]);
                    }
                }

            } //else if (isClusterhead && c.isForBS() && LStage == RUN) {
            else if (isClusterhead  && LStage == RUN) {
                
                //this.addMessageToList(m); // Keep?
                if (startsWith(m, "m") && m.length() > 1)
                {
                    String[] parts = Utils.split(m.substring(1), ':');
                    if ( inList(parts[0]) )
                    {
                        //ok. messages are m##:message
                        messagesThrough++;
                        System.out.println("Message successfully received at BS: "+m);
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
                    
                    following =  Utils.split(m, ';')[0].substring(2) ;
                    isClusterhead=false;
                    nextStage.cancel();
                    System.out.println(id + " was listening for announcments. Got one. Following " + following);
                    nextLeachStage();
                }
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
        System.out.println("Got message!");
        System.out.println(m);
        if (m != null && m.length() > 1 && GC.freeMemory() > MIN_MEM_SIZE)
            messageQueue.addElement(m);
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
        
        send.send("tick");  // to allow the sunspots to not be stupid.
        delay = 0;
        runtime = 0;
        clustermembers.removeAllElements();
        if (isClusterhead || chooseToBeClusterhead()) {
            // send a message
            isClusterhead = true;

            // send a 'isClusterhead' announcement
            // broadcast will happen 'all at once', but dealt with with backoffs.

            //if (debug)
            System.out.println(name + "  chooses to be a clusterhead");

            //msgHandler.broadcast( new Packet(this, new BroadcastMessage("CH"+this.id, id, 4 ), this.baud ));
            //msgHandler.scheduleBroadcast(new BroadcastMessage("CH"+this.id, id, 4 ), Utility.randXY( 0, LeachConstants.RECLUSTER_TIME/2 ) );
            int timeTo = Utility.randXY(  (int)LeachConstants.RECLUSTER_TIME / 4 , 2* (int)LeachConstants.RECLUSTER_TIME / 3);
            String message = "CH" + this.id + ";" + (LeachConstants.RECLUSTER_TIME - timeTo) + ";" + beaconRank ;
            //if (beaconRank < DEFAULT_BEACON_RANK) {  // is the sink, will always have a beacon rank
            //}
            scheduleBroadcast(message, timeTo);
        } else {
            isClusterhead = false;

            // wait until next phase
            // listen for CH announcements

        }
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(LeachConstants.RECLUSTER_TIME);

    }

    private void startChooseCluster() {
        // TODO Auto-generated method stub

        //msgHandler.on();
        if (debugLevel > 1)
                System.out.println("Start Choose Cluster");

        if (following != null) {
            //msgHandler.scheduleBroadcast(new BroadcastMessage("FL" + following.getId(), this.id, 3, following.getId()), Utility.randXY( 0, LeachConstants.CHOOSE_CLUSTERHEAD_TIME / 2));
            scheduleBroadcast("FL-" + id + "-" + following , Utility.randXY( (int) LeachConstants.CHOOSE_CLUSTERHEAD_TIME / 8, 2*(int) LeachConstants.CHOOSE_CLUSTERHEAD_TIME / 2));
        }
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(LeachConstants.CHOOSE_CLUSTERHEAD_TIME);
        System.out.println("Bing!");


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

    }

    private void startRun() {

        long startTime = new Date().getTime();
        long endTime = startTime + runtime;
        if (debugLevel > 1)
                System.out.println("Running...");
        
        nextStage.cancel();
        nextStage = new CallbackTimer(nextStageCallback);
        nextStage.scheduleCallback(runtime);
        
        
        if (isClusterhead) {
            runtimeAsClusterhead += runtime;
        } 
        else if (following != null)
        {
            runtimeAsSensor += runtime;
            System.out.println(name + " is going to run for " + runtime);
            
            while (new Date().getTime() < endTime - LeachConstants.EARLY_END_SEND && messageQueue.size() > 0)
            {
                // get the next message
                String message =(String)messageQueue.elementAt(0);
                
                // TDMA - no acking. Assume it sent properly
                send.send(message);
                messageQueue.removeElementAt(0);
            }
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
        
        following = null;
        isClusterhead = false;

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
            if ( temp.equals(toCheck))
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
    
    
    class ReceiveComplete implements IRadioUser
    {

        public void receiveComplete(String message) {
            //System.out.println("got message >" + message+"<");
            receiveCompleteCallback(message);
        }
        
    }
    
}



