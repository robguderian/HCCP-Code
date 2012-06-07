/*
 * Robert Guderian
 * First test to start clustering the motes into LEACH-like clusters
 */

#include "LeachMain.h"
#include "LeachConf.h"
#include "Leach.h"

#include "contiki.h"
#include "random.h"

#include "dev/button-sensor.h"

#include "dev/leds.h"

#include <stdio.h>
#include <string.h>


// for sleep
#include "lpm.h"	
#include "dev/watchdog.h"
#include <signal.h>






 #include "LeachConf.h"
 #include "Leach.h"

 #include "lib/list.h"
 #include "ctimer.h"

 #include <stdbool.h>
 #include <stdio.h>
 #include <stdlib.h>


 #include "dev/cc2420.h"
 #include "net/mac/sicslowmac.h"
 #include "net/mac/csma.h"
 #include "net/rime.h"
 #include "net/rime/channel.h"
 #include "dev/leds.h"
 #include "net/rime/packetbuf.h"

// sensors
 // light sensor for seeding the random generator
 #include "dev/light-sensor.h"
 #include "dev/battery-sensor.h";

 // for handling memory
 #include "lib/memb.h"



//HCCP stuff
// this mote's sensor mish
#define MAX_MESSAGE_SIZE 10
#define MAX_CLUSTER_SIZE 10

float sensorMission = 0.5;
int maxQueueSize = MAX_MESSAGE_SIZE;
float chanceOfBeingCH = 0.7;





volatile bool allowSleeping = false;

//#define SLEEP_1_SEC __bis_SR_register(LPM3_bits + GIE);
//#define SLEEP_1_SEC ;
struct ctimer c;

#define MY_TIMEOUT 1 * CLOCK_SECOND

void myTimerCallback(void* in)
{
    printf("In the callback!\n");
    //process_start(&callback_example, NULL);
    LPM_AWAKE();
    lpm_off();
}
#define SLEEP_1_SEC lpm_on(); ctimer_set ( &c, MY_TIMEOUT, myTimerCallback, (void*)NULL);LPM_SLEEP();



/*---------------------------------------------------------------------------*/
PROCESS(leach_main, "Leach-tmote");
AUTOSTART_PROCESSES(&leach_main);
/*---------------------------------------------------------------------------*/


/*---------------------------------------------------------------------------*/
PROCESS_THREAD(leach_main, ev, data)
{
	
	PROCESS_BEGIN();

	printf("ready to rock\n");
	


	
	
    printf("Starting leach\n");
	initLeach();
    printf("init complete\n");
    runLeach();
	while(1)
	{
        runLeach();
        
	    if (allowSleep())
        {
	 		turnStuffOff();
            while (allowSleep()) // TODO .... this seems weird.
            {
                printf("sleep!");
               
                //LPM_SLEEP();
                SLEEP_1_SEC
                printf("done sleep\n");
                
            }
			turnStuffOn();
            
        }
        else
        {
            //PROCESS_YIELD();
            PROCESS_PAUSE();
        }
	}

	PROCESS_END();
}




















/* Structure to define what messages look like */

struct followMessage{
    int following;
    int follower;
};




#define ATTRIBUTES
static const struct mac_driver * driver;
static const struct mac_driver * csmaDriver;
static const struct packetbuf_attrlist attributes[] =  { ATTRIBUTES PACKETBUF_ATTR_LAST };

/*
 * Defines for constants
 */
#define debugLevel  4

enum  {CANDIDACY, ANNOUNCE_CLUSTERHEAD, CHOOSE_CLUSTER,  WAIT_FOR_SCHEDULE, WAIT_FOR_TURN, RUN, WAIT_FOR_RECLUSTER, ROUNDTABLE, UNCLUSTERED } leachStage;


struct ctimer ct;
struct ctimer wd;
struct ctimer delayedMessageTimer;
struct ctimer nextReading;


int following = 0;
bool isClusterhead = false;
int lastRoundAsCH = 0;
uint8_t myid;
bool allowSend = false;
int messageCount = 0;
volatile bool goToNextStage = false;

// current iteration run info
int delay = 0;
int runtime = 0;
int numberOfMessagesCreated = 0;
int messagesThrough = 0;
int roundCount = 0;
int lastRoundAsClusterhead = 0;
clock_time_t timeBeforeChooseCluster = 0;
int notRunning = 0;

uint8_t beaconRank = 99;

unsigned char lastMessageFrom = 0;

bool sentCC;


// send buffer - must not fall off the stack (can't be in method)
char sendbuf[32];

char delayedMessage[32];

/*
 * Function prototypes in this file
 */
static void nextLeachStage (void* in);
static bool chooseToBeClusterhead();
static int getRandInt(int min, int max);
static double rand01();


static void nextReadingCB ();



static void sendDelayedMessage(char* in, int min, int max);
static void sendDelayedMessageNoRand(char* in, int delay);


static void recv( const struct mac_driver *c);
static void clustermoteRecv( char* in );
static void clusterheadRecv( char* in );

static void LeachSend(char* in);

static int parseClusterheadMessage(char* message);
static int parseClusterheadMessageGetRank(char* message);
static clock_time_t parseClusterheadMessageGetDelay(char* message);


static void parseSchedule(char* schedule);
static void makeAndSendSchedule();
static struct followMessage parseFollowMessage(char* message);
static void dumpFollowers();
static bool hasFollower(unsigned char id);
static bool startsWith (char* toCheck, char* prefix);

static void addMessageToQueue(char *in);


void leachCSMAMacHandler (char message[]);
void leachTDMAMacHandler (char message[]);

void check_still_running();


/*****
* MAC handler callback function pointer.
*/

void (*leachMacHandler) (char[]);



/*********
 *
 * Lists - one for the list of clustermemebers, one for the message queue
 *
 *****/


LIST(clusterMembers);
struct followerListEntry {
  struct followerListEntry *next;
  int id;
};

LIST(messageQueue);
struct messageListEntry{
    struct messageListEntry *next;
    char message[MAX_MESSAGE_SIZE];
};

// create a place to store the messages and  the list of followers
MEMB(message_memb, struct messageListEntry, MAX_MESSAGE_SIZE);
MEMB(cluster_memb, struct followerListEntry, MAX_CLUSTER_SIZE);



static float getBatteryPercentage()
{
    // ok... 
    // this compares to 5v... it's a 3v cell
    // 5/12bit = 5/4096 = each step is
    // 4096/5 * 3 = 2457.6=  3 v from ADC == full.
    // Say that 2 volts is dead 1638.4
    // do a gradient between the two.
     
    int in = battery_sensor.value(1);
    int out;
    
    if (in > 2457)
        return 1; // 100%
    else if (in < 1638)
        return 0; // 0%
    else
    {
        out = in - 1638;
        return out/(float)(1638); // (range)/(available range 2457-1638)
    }
}

static uint16_t getGoodnessDelay()
{
    uint16_t availableTime = 0;
    uint16_t delay = 0;
    uint16_t min = 1;

    //String name = this.toString();

    if (leachStage ==  ROUNDTABLE)
    	availableTime = ROUNDTABLE_TIME;
    else if (leachStage == CANDIDACY)
    	availableTime = RECLUSTER_TIME;
    else if (leachStage == ANNOUNCE_CLUSTERHEAD)
    	availableTime = RECLUSTER_TIME;

    // add delay times for stuff.

    // add up to n% of available time based on battery
    delay =  (availableTime * BATTERY_POWER_WEIGHT  * (1 - getBatteryPercentage() ) ); // the argument is thrown away

    // n% of available time based on mission
    // delay gets longer if this node wants to be a clustermote
    delay = delay + (availableTime * SENSOR_MISSION_WEIGHT * sensorMission );

    // n% on messagequeue size
    delay = delay + (availableTime * MESSAGE_QUEUE_WEIGHT * ( list_length(messageQueue) / (double)maxQueueSize ) );

    // n% of random
    delay = delay + (availableTime * RANDOM_WEIGHT * ( (rand01()/(float)100) ));

    // n% of duty cycling based on last round as CH.
    // more delay if I was just a clusterhead.
    if (min > lastRoundAsClusterhead * chanceOfBeingCH)
        min = lastRoundAsClusterhead * chanceOfBeingCH;
    delay = delay + (availableTime * DUTY_CYCLE_WEIGHT *  ( 1 - min ));

    


    // in case it overflows due to bad config.
    if (delay > availableTime)
        delay = availableTime;

    printf( "Goodness delay is %d out of %d\n", delay,  availableTime);

    return delay;
}






int isForMe(char* in)
{
    int skip = 0;
    int i;
    bool done = false;
    char getNum [4];
    for (i = 1; !done && i < strlen(in); i++)
    {
        if (in[i] != ':')
        {
            if(in[i] >='0' && in[i] <= '9')
            {        
                getNum[skip] = in[i];
                skip++;
            }
        }
        else
        {
            getNum[skip] ='\n';
            skip = skip+2; //will skip the : and the m
        }
    }
    
    if (myid != atoi(getNum))
        skip=0;
    return skip;
}




/**********************
 * leachStage code
 */
 
 
 
 static void start_announce_candidacy()
 {
     char tempbuf[10];
     bool dontBeCH = false;
     int i;

     // reset the watchdog
     ctimer_set ( &wd, TOTAL_RUN_TIME * 4, check_still_running, (void*)NULL);
     

     roundCount++;
     lastRoundAsClusterhead++;

     dumpFollowers();

     if (ctimer_expired(&ct) != 0)
         ctimer_stop(&ct);

     if (timeBeforeChooseCluster > 0)
     {
         printf("scheduling 2nd Stage for %i CS", timeBeforeChooseCluster);
         ctimer_set ( &ct, timeBeforeChooseCluster, nextLeachStage, (void*)NULL);
         dontBeCH = true;
     }
     else
         ctimer_set ( &ct, RECLUSTER_TIME, nextLeachStage, (void*)NULL);

     timeBeforeChooseCluster = 0;

     if (debugLevel > 1)
         printf("candidacy\n");

     // check if this is opting to be a CH.
     if( !dontBeCH && ( isClusterhead || chooseToBeClusterhead()) )  // isClusterhead is there for the bootstrap
     {
         sentCC = false;
         if (debugLevel > 1)
             printf("Chooses to be clusterhead\n");
         //uint16_t thisDelay = getRandInt(RECLUSTER_TIME/8, RECLUSTER_TIME/2) ;
         uint16_t thisDelay = getGoodnessDelay();
         thisDelay = thisDelay*1000/CLOCK_SECOND; // convert to milliseconds - TODO - will this overflow?
         sprintf(tempbuf, "CC%i;%i;%i",myid, thisDelay, beaconRank);

         sendDelayedMessageNoRand((char*)&tempbuf, thisDelay);
     }

     for (i = 0 ; i < NUMBER_OF_READINGS; i++)
         nextReadingCB();
     if (debugLevel > 1)
         printf ("Message queue size is %i\n", list_length(messageQueue));

 }

 
 
 
static void start_announce_clusterhead()
{
    char tempbuf[10];
    bool dontBeCH = false;
    int i;
    
    
    roundCount++;
    lastRoundAsClusterhead++;
    
    dumpFollowers();
    
    if (ctimer_expired(&ct) != 0)
        ctimer_stop(&ct);
        
    if (timeBeforeChooseCluster > 0)
    {
        printf("scheduling choose cluster for %i CS", timeBeforeChooseCluster);
        ctimer_set ( &ct, timeBeforeChooseCluster, nextLeachStage, (void*)NULL);
        dontBeCH = true;
    }
    else
        ctimer_set ( &ct, RECLUSTER_TIME, nextLeachStage, (void*)NULL);
 
    timeBeforeChooseCluster = 0;
    
    if (debugLevel > 1)
        printf("Announce clusterhead\n");
    
    // check if this is opting to be a CH.
    if( isClusterhead )  // isClusterhead is there for the bootstrap
    {
        isClusterhead = true;
        
        // TODO make announcement
        if (debugLevel > 1)
            printf("Chooses to be clusterhead\n");
        //sprintf(tempbuf, "CH%i;",myid);
        //sendDelayedMessage((char*)&tempbuf, 0, 10); // no delay, to a second
        //uint16_t thisDelay = getRandInt(RECLUSTER_TIME/8, RECLUSTER_TIME/2) ;
        uint16_t thisDelay = getGoodnessDelay();
        thisDelay = thisDelay*1000/CLOCK_SECOND; // convert to milliseconds - TODO - will this overflow?
        sprintf(tempbuf, "CH%i;%i;%i",myid, thisDelay, beaconRank);
        
        sendDelayedMessageNoRand((char*)&tempbuf, thisDelay);
    }
    
    for (i = 0 ; i < NUMBER_OF_READINGS; i++)
        nextReadingCB();
    if (debugLevel > 1)
        printf ("Message queue size is %i\n", list_length(messageQueue));
    
}
static void start_choose_cluster()
{
    char tempbuf[20];
    
    
    if (ctimer_expired(&ct) != 0)
        ctimer_stop(&ct);
    
    ctimer_set ( &ct, CHOOSE_CLUSTERHEAD_TIME, nextLeachStage, (void*)NULL);
        
    
    if (debugLevel > 1)
        printf("choose cluster\n");
        
    if (following != -1 && !isClusterhead)
    {
        sprintf (tempbuf, "FL-%i-%i", myid,  following);
        sendDelayedMessage((char*)&tempbuf, CHOOSE_CLUSTERHEAD_TIME/10,  CHOOSE_CLUSTERHEAD_TIME / 3);
    }
}

static void start_wait_for_schedule()
{
    if (ctimer_expired(&ct) != 0)
        ctimer_stop(&ct);
    ctimer_set ( &ct, WAIT_FOR_SCHEDULE_TIME, nextLeachStage, (void*)NULL);

    if (debugLevel > 1)
        printf("wait for schedule\n");
        
    
    
    if (isClusterhead)
    {
        makeAndSendSchedule();
        
        // TODO
        // send the schedule after a short wait.
        // choose random time to wait
    }
}

static void start_wait_for_turn()
{
    if (ctimer_expired(&ct) != 0)
        ctimer_stop(&ct);
    //if ( delay > 0)


    //    lpm_on();
    ctimer_set ( &ct, delay, nextLeachStage, (void*)NULL);
        
    //lpm_on();
    if (delay > 0)
        allowSleeping = true;
    
    if (debugLevel > 1)
        printf("wait for turn - %i\n", delay);
}
void start_run()
{
    
    struct messageListEntry *mle;
    
    if (ctimer_expired(&ct) != 0)
        ctimer_stop(&ct);
    ctimer_set ( &ct, runtime, nextLeachStage, (void*)NULL);
    
    if (debugLevel > 1)
        printf("running for %i\n", runtime);
        
    if (!isClusterhead && runtime > 0)
    {
        allowSend = true;
        // send like crazy!
        

        mle = list_pop(messageQueue);
        printf("ct time : %i %i\n",timer_remaining(&(ct.etimer.timer)), MIN_SEND_TIME);
        //while (allowSend && ctimer_expired(&ct) != 0 && timer_remaining(&(ct.etimer.timer)) > MIN_SEND_TIME && mle) // this is overkill...
        while (allowSend && timer_remaining(&(ct.etimer.timer)) > MIN_SEND_TIME && mle) 
        {
            LeachSend(mle->message);
            memb_free(&message_memb, mle);
            if (allowSend)
                mle = list_pop(messageQueue);
            
        }
        
    }
}

static void start_wait_for_recluster()
{
    allowSend = false;
    if (ctimer_expired(&ct) != 0)
        ctimer_stop(&ct);
        
    int waitTime = TOTAL_RUN_TIME - runtime - delay;
    if (debugLevel > 1)
    {
        printf ("waiting to recluster for %i\n", waitTime);
        printf ("Message queue size is %i\n", list_length(messageQueue));
    }
        
    ctimer_set ( &ct, waitTime , nextLeachStage, (void*)NULL);
    
    
    // if is CH, stay on and listen
    if (isClusterhead)
    {
        
    }
    else if (waitTime > CLOCK_SECOND)
    {
        //lpm_on();
        allowSleeping = true;
        //turn off radio
    }
    if (debugLevel > 1)
        printf ("Finished waiting to recluster for %i\n", waitTime);

}








/*********
 *  End Leach methods
 */


/*
 * Callback for the next stage timer
 */
static void nextLeachStage (void* in)
{
    // it's fine to do this if we're not waking from sleep
    //LPM_AWAKE();
    //lpm_off();
    
    //_EINT(); // allow nested interrupts.
    
    //printf("NextStage callback\n");
    goToNextStage = true;
    
}


void runLeach()
{    
    allowSleeping = false;
    
    
    //while(1)
	//{
    
        if (goToNextStage)
        {
            printf("NextStage\n");
            goToNextStage = false;
            switch (leachStage)
            {
                case CANDIDACY:
                    leachStage = ANNOUNCE_CLUSTERHEAD;
                    start_announce_clusterhead();
                    break;
                case ANNOUNCE_CLUSTERHEAD:
                    leachStage = CHOOSE_CLUSTER;
                    start_choose_cluster();
                    break;
                case CHOOSE_CLUSTER:
                    leachStage = WAIT_FOR_SCHEDULE;
                    start_wait_for_schedule();
                    break;
                case WAIT_FOR_SCHEDULE:
                    leachStage = WAIT_FOR_TURN;
                    start_wait_for_turn();
                    break;
                case WAIT_FOR_TURN:
                    leachStage = RUN;
                    start_run();
                    break;
                case RUN:
                    leachStage = WAIT_FOR_RECLUSTER;
                    start_wait_for_recluster();
                    break;
                case  WAIT_FOR_RECLUSTER:
                    leachStage = CANDIDACY;
            
                    isClusterhead = false;
                    // reset some control variables
                    delay = 0;
                    runtime = 0;
                    following = -1;
            
                    start_announce_candidacy();
                    break;
                default:
                    // this node is unclustered - start a CH election
                    leachStage = CANDIDACY;
                    start_announce_candidacy();
        
            }
        }
        /*else
        {
            if (allowSleep())
            {
                printf("sleep!");
                turnStuffOff();
                // LPM_SLEEP();
                //SLEEP_1_SEC
                printf("done sleep\n");
                turnStuffOn();
            }
            PROCESS_YIELD();
        }*/
    //}
}


/*
 * callback for next reading timer
 */
//static void nextReadingCB (void* in)
static void nextReadingCB ()
{
    
    
    // read a sensor. Make a message
    
    // for now, just make a random number.
    
    // add it to the message queue
    char message[20]; 
    sprintf(message, "m%i:m%i from%i", myid, messageCount, myid);
    messageCount++;
    addMessageToQueue(&message);
    // schedule the next reading
    //ctimer_set ( &nextReading, getRandInt(MIN_NEXT_READING_TIME,MAX_NEXT_READING_TIME), nextReadingCB, (void*)NULL);
    
}


static void addMessageToQueue(char *in)
{
    struct messageListEntry *newMessage = (struct messageListEntry*)memb_alloc(&message_memb);
    
    if (newMessage)
    {
        printf("Message Queued\n");
        strcpy(newMessage->message, in);
        
        list_add(messageQueue, newMessage);
        
    }
    else
    {
        printf("message not queued");
    }
}



void initLeach()
{
    // wait 2 * CLUSTER_TIME listening for clusterhead announcements.
    // after that, start a CH election
    
    
    channel_init();
	packetbuf_clear();
	driver = sicslowmac_init(&cc2420_driver);	
	cc2420_set_channel(CHANNEL);	
	driver->set_receive_function(recv);
	channel_set_attributes(CHANNEL, attributes);
	driver->on();	
	leds_toggle(LEDS_RED);
	packetbuf_clear();
    
    leachMacHandler = leachCSMAMacHandler;
    myid = rimeaddr_node_addr.u8[0];
    
    cc2420_set_txpower(31);
    
    // seed the random generator
    //unsigned int iseed = (unsigned int)time(NULL);
    
    SENSORS_ACTIVATE(light_sensor);
    SENSORS_ACTIVATE(battery_sensor);
    
    printf("seeding with %i, id set to %i\n", light_sensor.value(0), myid);  // could also use ds2411_id[7] to get the id.
    
    srand (light_sensor.value(0) + myid); // use temperature reading? or residual power? other sensor? Time is the same if flashed at the same time.
    SENSORS_DEACTIVATE(light_sensor);
    
    
    /* init CSMA to work on top of sicslowmac */
    csmaDriver = csma_init(driver);
    csmaDriver->set_receive_function(recv);
    
	//Actual run
    ctimer_set ( &ct, TOTAL_RUN_TIME * 4 + getRandInt(0,10), nextLeachStage, (void*)NULL);
	// test run
    //ctimer_set ( &ct, 4 + getRandInt(0,10) + 10, nextLeachStage, (void*)NULL);

	// watchdog timer
    ctimer_set ( &wd, TOTAL_RUN_TIME * 4, check_still_running, (void*)NULL);
    leachStage = UNCLUSTERED;  // start off unclustered
    
    // schedule the first reading
    //ctimer_set ( &nextReading, getRandInt(MIN_NEXT_READING_TIME,MAX_NEXT_READING_TIME), nextReadingCB, (void*)NULL);
    
    
    printf ("%i clock second\n", CLOCK_SECOND);  // apparently 128 on the tmote.
    // turn on radio
    // listen for CH elections
    

    // initialize the lists, and the place they are stored.
    
    // init sleep
    //BCSCTL1 |= DIVA_1;                        // runs WDT every second
    //WDTCTL = WDT_ADLY_1000;                   
    ////WDTCTL = WDTPW+WDTTMSEL+WDTCNTCL+WDTSSEL;
    //IE1 |= WDTIE;                             // Enable WDT interrupt
    //_EINT(); // enable interrupts
    
        
    memb_init(&message_memb);
    memb_init(&cluster_memb);
    
    list_init (messageQueue);
    list_init (clusterMembers);
    
    isClusterhead = true; // start as a clusterhead. If no other clusterhead announcements are made, be one.
    
    //SLEEP_1_SEC
    
}





static void handleMessage(char buffer[], int count)
{
    
}



static bool chooseToBeClusterhead()
{
	// the probability of a node becoming a clusterhead
	// is a double... from 0-1
	bool clusterhead = false;
	int threshold;

	// the sink is always a clusterhead
	if ( CHANCE_OF_BEING_CH > 0 && CHANCE_OF_BEING_CH < 100 && (100/CHANCE_OF_BEING_CH ) - lastRoundAsClusterhead <= 0) // check if this node is allowed to be a clusterhead (n in G)
	{
		threshold = ( CHANCE_OF_BEING_CH *100 )/( 100 - CHANCE_OF_BEING_CH * ( roundCount%(int)(100/CHANCE_OF_BEING_CH) ) );

        printf("CH threshold %i - %i\n", threshold, CHANCE_OF_BEING_CH);

		// now, choose a random number
		// if it's less than the threshold, become a clusterhead
		if ( rand01() < threshold )
			clusterhead = true;
	}
	else if (CHANCE_OF_BEING_CH >= 100)
		clusterhead = true;

	return clusterhead;

}

/*
 * get a random number between the two values
 */
static int getRandInt(int min, int max)
{
    int rando = rand();
    if (rando < 0)
        rando = rando * -1;
    //printf("still in rand. rando %i,  min %i, max %i,  %i\n", rando, min, max, rando%(max-min));
    return (min + rando%(max-min));
    
    //return min + (int)( (max - min + 1 ) *(float) rand() / ( (float)RAND_MAX ) );
}

/*
 * get a real number in (0,1)
 */
static double rand01()
{
    return getRandInt(0, 100);
}




/**********************************
 * Send and receive stuff
*/
 
static void LeachSend(char* in)
{
    
    sprintf(sendbuf, ">>>>%s<", in);
	
	printf("Attempting to send '%s'\n", sendbuf);

	packetbuf_clear();

	packetbuf_copyfrom(sendbuf, (int)strlen(sendbuf)+1);
 	
	driver->send();
 
}


/*
 * triages received messages
 */
static void leachRecv( char* in )
{
   // depending on the state of LEACH and whether or not it's a clusterhead change the meanings
   // of the messages 
   if (isClusterhead)
       clusterheadRecv( in);
   else
       clustermoteRecv( in);
}

/* 
 *  The receive message called if the mote is a clusterhead
 */
static void clusterheadRecv( char* in )
{
    
    struct followerListEntry *fle;
    struct followMessage fm;
    if (debugLevel > 1)
        printf("clusterhead got %s\n", in);
        
        
    switch(leachStage){   
        case (CANDIDACY): 
            if (!sentCC) // if I haven't sent my CC message
            {
                ctimer_stop(&delayedMessageTimer); // cancel the timer... do not be CH
                isClusterhead = false;
            }
        break;
    
        case CHOOSE_CLUSTER:
            if ( startsWith (in, "FL"))
            {
                
                // add this node to the list of clustermembers
                
                fle = memb_alloc(&cluster_memb);
                
                if (fle) // will return null if there is no space
                {
                    
                    fm = parseFollowMessage(in);
                    
                    if (fm.follower != -1 && fm.following == myid)
                    {
                        printf("added!\n");
                        fle->id = fm.follower;
                        list_add(clusterMembers, fle);
                    }
                }
                
            }
            break;
    
        case WAIT_FOR_SCHEDULE:
            // TODO
            // wait a random amount of time, publish the schedule
            break;
    
        case WAIT_FOR_RECLUSTER:
            // listen and accept messages from the cluster
            // add this message to the message queue
            if (startsWith(in, "m"))
            {
                int skip = isForMe(in);
                if (skip > 0)
                    addMessageToQueue(in + skip);
            }
            break;
        case UNCLUSTERED:
            // listen for CH elections
            if (startsWith (in, "CC") )
            {
                // f
                // TODO - follow this node
                //following = parseClusterheadMessage(in);
                
                following = parseClusterheadMessage(in);
                timeBeforeChooseCluster = parseClusterheadMessageGetDelay(in);

                
                if ( beaconRank > parseClusterheadMessageGetRank(in) )
                {
                    printf("beacon rank was %i, now is %i + 1",beaconRank, parseClusterheadMessageGetRank(in));
                    beaconRank = parseClusterheadMessageGetRank(in) + 1;
                }
                
                isClusterhead = false;
                // jump to recluster NOW!
                nextLeachStage(NULL);
            }
        break;
    
        default:
            printf("nuffin!");

    }
    
}


/*
 * the receive function called if the mote is not a clusterhead
 */
static void clustermoteRecv( char* in )
{
    if (debugLevel > 1)
        printf("clustermote got %s\n", in);
        
    switch (leachStage)
    {
        case ANNOUNCE_CLUSTERHEAD:
            if (startsWith (in, "CH") )
            {
                if (following == -1)
                {
                    following = parseClusterheadMessage(in);
                    timeBeforeChooseCluster = parseClusterheadMessageGetDelay(in);
                    printf ("The delay will be %i CS\n", timeBeforeChooseCluster);
                    
                    // reset the nextStage timer, since we're already in recluster
                    if (ctimer_expired(&ct) != 0)
                        ctimer_stop(&ct);
                    ctimer_set ( &ct, timeBeforeChooseCluster, nextLeachStage, (void*)NULL);
                    timeBeforeChooseCluster = 0;
                    

                    
                    if ( beaconRank > parseClusterheadMessageGetRank(in) )
                    {
                        printf("beacon rank was %i, now is %i + 1",beaconRank, parseClusterheadMessageGetRank(in));
                        beaconRank = parseClusterheadMessageGetRank(in) + 1;
                    }
                    
                }
                else
                {
                    // TODO - get RSSI or beacon rank
                }
                
            }
            break;
        
        case CHOOSE_CLUSTER:
            // care?
        break;
        
        case WAIT_FOR_SCHEDULE:
            // listen... don't need to ack
            parseSchedule(in);
        break;
        
        case RUN:
            // shouldn't be receiving here either
        break;
        
        case UNCLUSTERED:
            // listen for CH elections
            if (startsWith (in, "CH") )
            {
                // f
                // TODO - follow this node
                following = parseClusterheadMessage(in);
                if ( beaconRank > parseClusterheadMessageGetRank(in) )
                {
                    printf("beacon rank was %i, now is %i + 1",beaconRank, parseClusterheadMessageGetRank(in));
                    beaconRank = parseClusterheadMessageGetRank(in) + 1;
                }
                isClusterhead = false;
                // jump to recluster NOW!
                nextLeachStage(NULL);
            }
        break;
        
        default:
            printf("nuffin!");

    }
}


static void
recv( const struct mac_driver *c)
{
 	//packetbuf_set_attr(PACKETBUF_ATTR_CHANNEL, 34);
 	int len = c->read();
 	char cleanString [32];
 	int cleanLen = 0;
 	int i;
 	char* in=  (char*)packetbuf_dataptr();
 	
    rimeaddr_t* inAddr;
 	
    bool allowRead = false;
    
 	for ( i = 0; i < len; i++)
 	{
        //printf("'%c'\n", in[i]);
        if (allowRead)
        {
 		    if ( ( in[i]  >= 'a' && in[i]  <= 'z' ) || ( in[i]  >= 'A' && in[i]  <= 'Z' ) || ( in[i]  >= '0' && in[i]  <= '9' ) || in[i] == ' ' || in[i] == '-' || in[i] == ':' || in[i] == ';' )
     		{

     			cleanString[cleanLen] =  in[i];
     			//printf("%i %i\n",i,tempString[i]);
     			cleanLen++;
     		}
 		}
     	else if (in[i] == '>')
            allowRead = true;
        else if (in[i] == '<')
            allowRead =false;
 	}
 	cleanString[cleanLen] = '\0';
 	cleanLen++;
 	
    inAddr = packetbuf_addr(PACKETBUF_ADDR_SENDER);
    lastMessageFrom = inAddr->u8[0];
 	if (debugLevel >1)
 	{
 	    printf("message received, origlen = %d, cleanlen = %d, from %i, '%s'\n", len, cleanLen, inAddr->u8[0],cleanString);
        //printf("me %i  inaddr %i - %i\n", myid, inAddr->u8[0], packetbuf_addr(PACKETBUF_ADDR_SENDER)->u8[0]);
 	    //printf("message received, len = %d, '%s'\n", len, (char *)packetbuf_dataptr());
 	}
 	leds_toggle(LEDS_GREEN);
 	
 	
 	
 	// TODO CSMA work here - currently, using built-in csma (no CA)
 	// if csma done, then
    leachRecv((char*)&cleanString);
 	
 	
 	
 	//packetbuf_clear(); // what if another came while the first completed being handled?
 	//packetbuf_attr_clear();
    

}	


/*
 * Callback to send a message
 * Would be called if there is a message already being heard.
 * Or can be called to send a message later.
 */
static void sendDelayedMessageCallback(void* in)
{
    LeachSend((char*)&delayedMessage);
}
 
/*
 * min and max are sent in 10ths of a second.
 */ 
static void sendDelayedMessage(char* in, int min, int max)
{
    int delay = getRandInt(min, max);
    sprintf(delayedMessage, "%s",in);
    printf("Delayed Message: %s, %i",in, delay);
    if ( startsWith( in, "CC") )
    {
        sentCC = true;
    }
    
    // TODO - make the timer configurable.
    ctimer_set ( &delayedMessageTimer, delay, sendDelayedMessageCallback, (void*)NULL);
}


static void sendDelayedMessageNoRand(char* in, int delay)
{
    
    sprintf(delayedMessage, "%s",in);
#if debugLevel > 2    
    printf(delayedMessage, "Delayed Message: %s, %i",in, delay);
#endif
    
    // TODO - make the timer configurable.
    //ctimer_set ( &delayedMessageTimer, thisDelay, sendDelayedMessageCallback, (void*)NULL);
    //timer_set ( &delayedMessageTimer, delay, sendDelayedMessageCallback);
    ctimer_set ( &delayedMessageTimer, delay, sendDelayedMessageCallback, (void*)NULL);
}






/*
 * Random functions
 *
 */
 
 
static bool startsWith (char* toCheck, char* prefix)
{
    bool matches = true;
    int i;
    
    for (i = 0; i < strlen(prefix); i++)
    {
        if (toCheck[i] != prefix[i])
            matches = false;
    }
    return matches;
}

static struct followMessage parseFollowMessage(char* message)
{
    struct followMessage parsedMessage;
    
    char followerBuf[20];
    char followingBuf[20];
    int i = 0, dashcount = 0, j=0 ;
    // init to some sentinel values
    parsedMessage.following = -1;
    parsedMessage.follower = -1;
    if ( startsWith( message, "FL-"))
    {
        
        /*parsedMessage.following = -1; // doesn't compile...  scanf not found, also would make binary huge
        parsedMessage.follower = -1;
        sscanf(message, "FL-%i-%i", &parsedMessage.follower, &parsedMessage.following );*/
        
        // check for 2 dashes
        for (i = 0; i < strlen(message); i++)
        {
            if (message[i] == '-')
                dashcount++;
        }
        if (dashcount == 2)
        {
            dashcount = 0;
            
            for (i = 0; i < strlen(message); i++)
            {
                if (message[i]=='-')
                {
                    if (dashcount == 1)
                        followerBuf[j] = '\0';
                    if (dashcount == 2)
                        followingBuf[j] = '\0';
                    dashcount++;
                    j = 0;
                }
                else if ( message[i] >= '0' && message[i] <= '9' ) 
                {
                    
                    if (dashcount == 1)
                    {
                        followerBuf[j] = message[i];
                        j++;
                    }
                    else if (dashcount == 2)
                    {
                        followingBuf[j] = message[i];
                        j++;
                    }
                }
            }
            if (dashcount == 2)
                followingBuf[j] = '\0';
                
            printf("parsing follow message: follower%s following%s ", followerBuf, followingBuf);
            // make the numbers!
            parsedMessage.follower = atoi(followerBuf);
            parsedMessage.following = atoi(followingBuf);
            
        }
        
    }

    return parsedMessage;
}

static int parseClusterheadMessage(char* message)
{
    char tempbuf [20];
            
    int chId = -1;
    int i;
    int j=0;
    
    
    //sscanf(message, "CH%i", &chId); doesn't work. Also, would make the binary huge.
    
    for (i = 2; i < strlen(message); i++)
    {
        if (message[i] >='0' && message[i] <= '9')
        {
            tempbuf[j] = message[i];
            j++;
        }
        else if (message[i] == ';')
            break;
    }
    tempbuf[j] = '\0';
    chId = atoi(tempbuf);
    
    printf("Clusterhead message id send %i\n", chId);
    
    return chId;
    
}

static int parseClusterheadMessageGetRank(char* message)
{
    char tempbuf [20];
            
    int rank = -1;
    int i;
    int j=0;
    int part = 0;
    
    bool allowRead = false;
    
    
    //sscanf(message, "CH%i", &chId); doesn't work. Also, would make the binary huge.
    
    for (i = 2; i < strlen(message); i++)
    {
        if  (message[i] == ';')
        {
            if (allowRead)
                break;
            else
            {
                part++;
                if (part == 2)
                    allowRead = true;
            }
                
        }
        else if (allowRead && message[i] >='0' && message[i] <= '9')
        {
            tempbuf[j] = message[i];
            j++;
        }
    }
    tempbuf[j] = '\0';
    rank = atoi(tempbuf);
    
    return rank;
    
}

static clock_time_t parseClusterheadMessageGetDelay(char* message)
{
    char tempbuf [20];
            
    uint16_t getdelay = -1;
    int i;
    int j=0;
    int count = 0;
    bool allowRead = false;
    
    
    //sscanf(message, "CH%i", &chId); doesn't work. Also, would make the binary huge.
    
    for (i = 2; i < strlen(message); i++)
    {
        if  (message[i] == ';')
        {
            if (allowRead)
                break;
            else
            {
                count++;
                if (count == 1)
                    allowRead = true;
            }
        }
        else if (allowRead && message[i] >='0' && message[i] <= '9')
        {
            tempbuf[j] = message[i];
            j++;
        }
    }
    tempbuf[j] = '\0';
    getdelay = atoi(tempbuf);
    
    uint16_t cleandelay = (getdelay/100) * CLOCK_SECOND / 10; // convert to clock seconds
    clock_time_t cleanestDelay = cleandelay;
    
    printf ("pre delay is %i, Delay is %i CS, cleanDelay is %i, which is %i s\n",getdelay, cleandelay, cleanestDelay, (cleandelay/CLOCK_SECOND));
    return cleanestDelay; 
    //return (cleanestDelay - cleanestDelay/90) >> 1; 
    
}


void leachCSMAMacHandler (char message[])
{
    
}
/*
 * No Acks in TDMA... just accept messages.
 */
void leachTDMAMacHandler (char message[])
{
    leachRecv( (char*)&message);
}


static bool hasFollower(unsigned char id)
{
    struct followerListEntry* s;
    bool hasid = false;
    for(s = list_head(clusterMembers); s != NULL; s = s->next)
    {
        if ( id == s->id )
            hasid = true;
    }
    return hasid;
}

static void dumpFollowers()
{
    struct followerListEntry* s;
    
    s = list_pop(clusterMembers);
    while (s)
    {
        memb_free(&cluster_memb, s);
        s = list_pop(clusterMembers);
    }
}


static void makeAndSendSchedule()
{
    struct followerListEntry* s;
    int runtime = TOTAL_RUN_TIME_IN_SECONDS / ((int)list_length(clusterMembers) + 1);  // +1 to leave some space at the end (and avoid div/0)
    
    char buildstr [64];
    char temp[20];
    sprintf (buildstr, "%i;", runtime);
    int delay = 0;
    
    printf ("building schedule\n");
    
    for(s = list_head(clusterMembers); s != NULL; s = s->next)
    {
        printf ("%i:%i;", s->id, delay);
        sprintf(temp, "%i:%i;", s->id, delay);
        strcat(buildstr, temp);
        delay += runtime;
    }
    
    sendDelayedMessage((char*)&buildstr, WAIT_FOR_SCHEDULE_TIME/10 , WAIT_FOR_SCHEDULE_TIME / 2);
}

static void parseSchedule(char* schedule)
{
    // form: time;id:delay;id:delay
    // there are control variables in each node. Set these, and it should work.
    char temp [20];
    int i;
    int currLen = 0;
    
    int runtimeIn = 0;
    
    int numberIn;
    bool careAboutDelay = false;
    bool done = false;
    
    uint8_t stage = 0; // stage 0 = runtime, 1 = id, 2 = delay
    
    printf("parsing schedule\n");
    
    for (i = 0; i < strlen(schedule) && !done; i++)
    {
        printf("%c", schedule[i]);
        if (schedule[i] == ';' || schedule[i] == ':')
        {
            // marks the end of a number
            
            // set back to copying stuff in.
            temp[currLen] = '\0';
            numberIn = atoi(temp);
            
            printf("\n%i\n", numberIn);
            
            if (stage == 0)
            {
                runtimeIn = numberIn * CLOCK_SECOND;  //convert to clock seconds
                stage++;
            }
            else if (stage == 1)
            {
                if (numberIn == myid)
                    careAboutDelay = true;
                stage++;
            }
            else
            {
                if (careAboutDelay)
                {
                    // set the values in this node
                    delay = numberIn * CLOCK_SECOND;
                    runtime = runtimeIn;
                    done = true;
                    printf("Found my junk! %i, %i\n",delay, runtime);
                }
                stage--;
                careAboutDelay = false;
            }
            currLen = 0;
        }
        else 
        {
            
            temp[currLen] = schedule[i];
            currLen++;
        }
    }
}


/*
 * Watchdog timer callback
 */
interrupt(WDT_VECTOR)
     watchdog_ti(void)
{
  __bic_SR_register_on_exit(LPM3_bits);     // Clear LPM3 bits from 0(SR)
  printf("Herro\n");
}

void turnStuffOff()
{
    driver->off(false); // turn off the radio
    cc2420_driver.off();
}

void turnStuffOn()
{
    cc2420_driver.on();
    driver->on();
}


int allowSleep()
{
    // allow sleep, and there's at least a second before the next stage, and there's no message left to be sent
    //return allowSleeping && timer_remaining(&(ct.etimer.timer) )  > CLOCK_SECOND && !ctimer_expired(&delayedMessageTimer); 
    return ( leachStage == WAIT_FOR_RECLUSTER || leachStage == WAIT_FOR_TURN ) && allowSleeping && timer_remaining(&(ct.etimer.timer) )  > CLOCK_SECOND; 
}



void check_still_running()
{
    // check to see if the process is still running.
    // if not, restart it.
    notRunning++;
    if ( notRunning > 3 )
    {
        printf("shit stopped... resetting");
        leachStage = UNCLUSTERED;
        if (ctimer_expired(&ct) != 0)
            ctimer_stop(&ct);
        
        ctimer_set ( &ct, TOTAL_RUN_TIME * 2, nextLeachStage, (void*)NULL);
        
        ctimer_set ( &wd, WATCHDOG, check_still_running,(void*)NULL);
    }
    else
    {
        // set up the watchdog again
        ctimer_set ( &wd, WATCHDOG, check_still_running,(void*)NULL);
    }
}



