// arduino include
#include <WProgram.h>
#include "WProgram.h"
#include "Leach.h"

#include <avr/sleep.h>
#include <avr/wdt.h>
#ifndef cbi
#define cbi(sfr, bit) (_SFR_BYTE(sfr) &= ~_BV(bit))
#endif
#ifndef sbi
#define sbi(sfr, bit) (_SFR_BYTE(sfr) |= _BV(bit))
#endif


#include "timer.h"


#include <stdbool.h>
//#include <stdio.h>
//#include <stdlib.h>

#include <inttypes.h>

#include "xBee.h"
#include "XbeeRadio.h"

/*#include "dev/cc2420.h"
#include "net/mac/sicslowmac.h"
#include "net/mac/csma.h"
#include "net/rime.h"
#include "net/rime/channel.h"
#include "dev/leds.h"
#include "net/rime/packetbuf.h"

// for seeding the random generator
#include "dev/light-sensor.h"
*/
// for handling memory


/* Structure to define what messages look like */

struct followMessage{
    int following;
    int follower;
};




#define ATTRIBUTES

static const struct mac_driver * csmaDriver;
//static const struct packetbuf_attrlist attributes[] =  { ATTRIBUTES PACKETBUF_ATTR_LAST };

/*
 * Defines for constants
 */
#define debugLevel  0
#define MESSAGE_QUEUE_SIZE 2
#define MAX_MESSAGE_SIZE 16 //20   // can move these to EEPROM - http://www.arduino.cc/en/Reference/EEPROM
#define MAX_CLUSTER_SIZE 10 //24


enum  {RECLUSTER, CHOOSE_CLUSTER,  WAIT_FOR_SCHEDULE, WAIT_FOR_TURN, RUN, WAIT_FOR_RECLUSTER, UNCLUSTERED } leachStage;


struct timer ct;
struct timer delayedMessageTimer;
struct timer wd;

volatile bool goToNextStage = false;
bool booted = false;

int following = 0;
bool isClusterhead = false;
int lastRoundAsCH = 0;
uint8_t myid;
bool allowSend = false;
int messageCount = 0;
int totalMessageCount = 0;
int currentMessage = 0;
int numberOfFollowers = 0;
short timeBeforeChooseCluster = 0;
int beaconRank = 99;
int notRunning = 0;

clock_time_t lastTime = 0;

// current iteration run info
int runtimeDelay = 0;
int runtime = 0;

int numberOfMessagesCreated = 0;
int messagesThrough = 0;
int roundCount = 0;
int lastRoundAsClusterhead = 0;

unsigned char lastMessageFrom = 0;


// send buffer - must not fall off the stack (can't be in function)
char sendbuf[32];

char delayedMessage[32];

XBeeRadio xbee = XBeeRadio();
Rx16Response rx = Rx16Response();


/*
 * Function prototypes in this file
 */
static void nextLeachStage ();
static void runNextLeachStage();
static bool chooseToBeClusterhead();
static int getRandInt(int min, int max);
static double rand01();


static void nextReadingCB (void* in);
static void nextReadingCB ();


static void sendDelayedMessageCallback();
static void sendDelayedMessage(char* in, int min, int max);
static void sendDelayedMessageNoRand(char* in, int delay);



static void clustermoteRecv( char* in );
static void clusterheadRecv( char* in );

static void LeachSend(char* in);

static int parseClusterheadMessage(char* message);
static uint16_t parseClusterheadMessageGetDelay(char* message);
static int parseClusterheadMessageGetRank(char* message);

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


// sleep handler functions
void setup_watchdog(int ii);
void system_sleep();

/*****
* MAC handler callback function pointer.
*/

void (*leachMacHandler) (char[]);



/*********
 *
 * Lists - one for the list of clustermemebers, one for the message queue
 *
 *****/



int clusterMembers [MAX_CLUSTER_SIZE];
char messageQueue [MESSAGE_QUEUE_SIZE][MAX_MESSAGE_SIZE];


/*
 * Printf code!
 */ 
#if debugLevel > 0
#include <stdarg.h>
void a_printf(char *fmt, ... ){
        char tmp[128]; // resulting string limited to 128 chars
        va_list args;
        va_start (args, fmt );
        vsnprintf(tmp, 128, fmt, args);
        va_end (args);
        Serial.print(tmp);
}
#endif

/**********************
 * leachStage code
 */
 
static void start_recluster()
{
    bool dontBeCH = false;
    char tempbuf[10];
    roundCount++;
    lastRoundAsClusterhead++;
    
#if debugLevel > 1
    a_printf("reclustering\n");
#endif
    
    //LeachSend("narp1");
    //sendDelayedMessage("narp2", 0, 2);
    
    //timer_set ( &ct, RECLUSTER_TIME, nextLeachStage);
    //a_printf("scheduling next stage in %i recluster time is %i\n", timeBeforeChooseCluster, RECLUSTER_TIME);
    if (timeBeforeChooseCluster > 0)
    {
        //a_printf("scheduling next stage in %i\n", timeBeforeChooseCluster);
        timer_set ( &ct, timeBeforeChooseCluster, nextLeachStage);
        dontBeCH = true;
        isClusterhead = false;
    }
    else
    {
        timer_set ( &ct, RECLUSTER_TIME, nextLeachStage);
    }
    timeBeforeChooseCluster = 0;
 
    dumpFollowers();

    
    // check if this is opting to be a CH.
    if( !dontBeCH && (isClusterhead || chooseToBeClusterhead()  )) // isClusterhead is there for the bootstrap
    {
        isClusterhead = true;
        
        //  make announcement
#if debugLevel > 1
            a_printf("Chooses to be clusterhead\n");
#endif
        
        uint16_t thisDelay = getRandInt(RECLUSTER_TIME/8, RECLUSTER_TIME/2) ;
        thisDelay = thisDelay*1000/CLOCK_SECOND; // convert to milliseconds - TODO - will this overflow?
        sprintf(tempbuf, "CH%i;%i",myid, thisDelay);
        
        sendDelayedMessageNoRand((char*)&tempbuf, thisDelay); 
        
    }
    
    // create some messages
    for (int i = 0; i < NUMBER_OF_MESSAGES_PER_ROUND; i++)
        nextReadingCB();
    
}
static void start_choose_cluster()
{
    char tempbuf[20];
    
    
    
    
    //if (ctimer_expired(&ct) != 0)
    //    ctimer_stop(&ct);
    timer_set ( &ct, RECLUSTER_TIME, nextLeachStage);
#if debugLevel > 1
        a_printf("choose cluster\n");
#endif
        
    if (following != 0 && !isClusterhead)
    {
        sprintf (tempbuf, "FL-%i-%i", myid,  following);
        sendDelayedMessage((char*)&tempbuf, 0, 10);
    }
}

static void start_wait_for_schedule()
{
    //if (ctimer_expired(&ct) != 0)
    //    ctimer_stop(&ct);
    timer_set ( &ct, WAIT_FOR_SCHEDULE_TIME, nextLeachStage);


#if debugLevel > 1
        a_printf("wait for schedule\n");
#endif
        
    
    
    if (isClusterhead)
    {
        makeAndSendSchedule();
        
        
        // send the schedule after a short wait.
        // choose random time to wait
    }

}

static void start_wait_for_turn()
{
    //if (ctimer_expired(&ct) != 0)
    //    ctimer_stop(&ct);
    int runtimeDelay_here = runtimeDelay;
    timer_set ( &ct, runtimeDelay, nextLeachStage);
    
#if debugLevel > 1
    a_printf("wait for turn - %i\n", runtimeDelay);
#endif

    while (runtimeDelay_here > CLOCK_SECOND)
    {
        system_sleep();
        runtimeDelay_here = runtimeDelay_here - CLOCK_SECOND;
    }
}
static void start_run()
{
    
    struct messageListEntry *mle;
    
    //if (ctimer_expired(&ct) != 0)
    //    ctimer_stop(&ct);
    timer_set ( &ct, runtime, nextLeachStage);
    
#if debugLevel > 2
        a_printf("running for %i\n", runtime);
        a_printf("number of messages in queue %i", messageCount);
        a_printf("Following %i", following);
#endif
        
    if (!isClusterhead && following != 0 && runtime > 0 )
    {
        //sei(); // allow interrupts, so more readings etc can take place	
        allowSend = true;
        // send like crazy!
        
        
        //a_printf("ct time : %i %i\n",timer_remaining(&(ct.etimer.timer)), MIN_SEND_TIME);
        //while (allowSend && ctimer_expired(&ct) != 0 && timer_remaining(&(ct.etimer.timer)) > MIN_SEND_TIME && mle) // this is overkill...


        while (allowSend && timer_remaining(&ct) > MIN_SEND_TIME && messageCount > 0) // need to be able to quit.
        {
            LeachSend((char*)&(messageQueue[currentMessage]));
            currentMessage = (currentMessage + 1)%MESSAGE_QUEUE_SIZE;
            messageCount--;            
        }
        
        // sleep
        while (timer_remaining(&ct) > CLOCK_SECOND )
            system_sleep();
        
    }
}

static void start_wait_for_recluster()
{
    allowSend = false;
    //if (ctimer_expired(&ct) != 0)
    //    ctimer_stop(&ct);
        
    int waitTime = TOTAL_RUN_TIME - runtime - runtimeDelay;
    timer_set ( &ct, waitTime , nextLeachStage);
    
#if debugLevel > 1
        a_printf ("waiting to recluster for %i\n", waitTime);
#endif        
    
    // if is CH, stay on and listen
    if (isClusterhead)
    {
#if debugLevel > 2
        a_printf("Clusterhead waiting and listening for messages");
#endif
    }
    else
    {
#if debugLevel > 2        
        a_printf("Plebe is waiting for recluster");
#endif
        // TODO - turn off radio
        
        //sleep for a second, as long as there is a second left
        // waittime is in clock_seconds. 
        while (waitTime > CLOCK_SECOND)
        {
            system_sleep();
            waitTime = waitTime - CLOCK_SECOND;
        }
            
        
    }

}




/*********
 *  End Leach methods
 */


/*
 * Callback for the next stage timer
 */
static void nextLeachStage ()
{
#if debugLevel > 2    
    Serial.println("Next Leach Stage");
#endif
    goToNextStage = true;

}

static void runNextLeachStage()
{
    notRunning = 0;
    switch (leachStage)
    {
        case RECLUSTER:
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
            leachStage = RECLUSTER;
            
            isClusterhead = false;
            // reset some control variables
            runtimeDelay = 0;
            runtime = 0;
            following = 0;
            
            start_recluster();
            break;
        default:
            // this node is unclustered - start a CH election
            leachStage = RECLUSTER;
            start_recluster();
        
    }
}


/*
 * callback for next reading timer
 */
static void nextReadingCB ()
{
    nextReadingCB((void*)NULL);
}
static void nextReadingCB (void* in)
{
    
    
    // read a sensor. Make a message    
    // for now, just make a random number.    
    
    // add it to the message queue
#if debugLevel > 2
        a_printf("Next Reading");
#endif        
    char message[20]; 
    sprintf(message, "m%i:m%i from%i", myid, totalMessageCount, myid);
    totalMessageCount++;
    addMessageToQueue((char*)&message);
    // schedule the next reading
    //timer_set ( &nextReading, getRandInt(MIN_NEXT_READING_TIME,MAX_NEXT_READING_TIME), nextReadingCB, (void*)NULL);
    
    // reset the timer first, so it stops going off.
    //timer_set ( &nextReading, getRandInt(MIN_NEXT_READING_TIME,MAX_NEXT_READING_TIME));
}


static void addMessageToQueue(char *in)
{
    //struct messageListEntry *newMessage = (struct messageListEntry*)memb_alloc(&message_memb);
    
    if (messageCount < MESSAGE_QUEUE_SIZE)
    {
        strcpy(messageQueue[(currentMessage + messageCount)%MESSAGE_QUEUE_SIZE], in);
        messageCount++;
            
#if debugLevel > 1
            a_printf("Message Queued - %d\n", messageCount);
#endif        
         

    }
    else
    {
#if debugLevel > 1
            a_printf("message queue full");
#endif            
    }
}





void initLeach()
{
    // wait 2 * CLUSTER_TIME listening for clusterhead announcements.
    // after that, start a CH election
    
    
      //channel_init();  // handled by xbee chip. Could do AT commands if need be
    //packetbuf_clear();
    //driver = sicslowmac_init(&cc2420_driver); 
    //cc2420_set_channel(CHANNEL);  
    //driver->set_receive_function(recv);
    //channel_set_attributes(CHANNEL, attributes);
    //driver->on(); 
    //leds_toggle(LEDS_RED);
    //packetbuf_clear();
    
    //leachMacHandler = leachCSMAMacHandler;
    //myid = rimeaddr_node_addr.u8[0];
    myid = analogRead(0);  // TODO - find a way to set actual, static id (#defines?)
    
    // seed the random generator
    //unsigned int iseed = (unsigned int)time(NULL);
#if debugLevel > 2    
    Serial.println("Seeding random generator");
#endif
    srand (myid); // use temperature reading? or residual power? other sensor? Time is the same if flashed at the same time.
    
    
    /* init CSMA to work on top of sicslowmac */
    //csmaDriver = csma_init(driver);
    //csmaDriver->set_receive_function(recv);
    
    clock_init();
#if debugLevel > 2    
    Serial.println("Setting initial wait timer");
#endif    
    //ctimer_set ( &ct, TOTAL_RUN_TIME * 2 + getRandInt(0,10), nextLeachStage, (void*)NULL);
    timer_set ( &ct, TOTAL_RUN_TIME * 3 + getRandInt(0,10), nextLeachStage);
    leachStage = UNCLUSTERED;  // start off unclustered
    
    // schedule the first reading
    //ctimer_set ( &nextReading, getRandInt(MIN_NEXT_READING_TIME,MAX_NEXT_READING_TIME), nextReadingCB, (void*)NULL);
#if debugLevel > 2    
    Serial.println("Scheduling readings");
#endif
    //timer_set ( &nextReading, getRandInt(MIN_NEXT_READING_TIME,MAX_NEXT_READING_TIME), nextReadingCB);
    
    delayedMessageTimer.callback = &sendDelayedMessageCallback;
    delayedMessageTimer.handled = false;
    
    //a_printf ("%i clock second\n", CLOCK_SECOND);  // apparently 128 on the tmote.
    // turn on radio
    // listen for CH elections
    

    // initialize the lists, and the place they are stored.
#if debugLevel > 2    
    Serial.println("Init radio");
#endif
    xbee.begin(9600);
    //xbee.init();

  
    isClusterhead = true; // start as a clusterhead. If no other clusterhead announcements are made, be one.
#if debugLevel > 2    
    Serial.println("Ready to rock!");

    //Serial.print(availableMemory());
    Serial.println (" <- memory available");
#endif
    
    
    clock_init();
    setup_watchdog(6);
    cbi(ADCSRA,ADEN);                    // switch Analog to Digitalconverter OFF
    
    cli();
    sei();
    sei();
    
    //LeachSend("Waiting for clock\n");
    
    while (!booted)
    {
        //delay(1);
        //Serial.print(".");
    }
    
    //Serial.println("Done init");
    wd.handled = true;        
    timer_set ( &wd, WATCHDOG, check_still_running);
    
    while (1)
    {
        if (goToNextStage)
        {
            goToNextStage = false;
            runNextLeachStage();

        }
    }
    

    
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
#if debugLevel > 2
        a_printf("CH threshold %i - %i\n", threshold, CHANCE_OF_BEING_CH);
#endif

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
#if debugLevel > 2
        a_printf("still in rand. rando %i,  min %i, max %i,  %i\n", rando, min, max, rando%(max-min));
#endif        
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
    uint8_t i;
    sprintf(sendbuf, ">>%s<...", in);
#if debugLevel > 2    
    a_printf("Attempting to send >>%s<<\n", in);
#endif

 
    /*uint8_t payload [ strlen(in)  + 10];
    
    payload[0] = '>';
    payload[1] = '>';
    
    for (i = 0; i < strlen(in); i++)
        payload[i+2] = (uint8_t)in[i];
    payload[i] = '<';

    */
    
    // put the obligitory >> < around the string
    
    // TODO - this might be kind of slow? 
#if debugLevel > 1     
     a_printf("Attempting to send senfbuf '%s' %i\n", sendbuf,  strlen(sendbuf));
#endif
    Tx16Request tx = Tx16Request(0xffff, (uint8_t*)sendbuf, strlen(sendbuf));
    xbee.send(tx);
    
}


/*
 * triages received messages
 */
static void leachRecv( char* in )
{
   // depending on the state of LEACH and whether or not it's a clusterhead change the meanings
   // of the messages 
   //a_printf("i got %s\n", in);
   
   if (isClusterhead)
       clusterheadRecv( in );
   else
       clustermoteRecv( in );

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


/* 
 *  The receive message called if the mote is a clusterhead
 */
static void clusterheadRecv( char* in )
{
    
    struct followerListEntry *fle;
    struct followMessage fm;
#if debugLevel > 2
        a_printf("clusterhead got %s\n", in);
#endif        
        
        
    switch(leachStage){   
        case (RECLUSTER): 
        break;
    
        case CHOOSE_CLUSTER:
            if ( startsWith (in, "FL"))
            {
                // add this node to the list of clustermembers
                fm = parseFollowMessage(in);
                
                if (fm.follower != -1 && fm.following == myid)
                {
                    if (numberOfFollowers < MAX_CLUSTER_SIZE)
                    {
                        clusterMembers[numberOfFollowers]= fm.follower;
                        numberOfFollowers++;
#if debugLevel > 2
                            a_printf("added!\n");
#endif                            
                    }
                    else
                    {
#if debugLevel > 2
                            a_printf("cluster full");
#endif               
                    }         
                }
            
                
            }
            break;
    
        case WAIT_FOR_SCHEDULE:
            // wait a random amount of time, publish the schedule - not really listening.
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
            if (startsWith (in, "CH") )
            {
                // follow this node
                following = parseClusterheadMessage(in);
                timeBeforeChooseCluster = parseClusterheadMessageGetDelay(in);
                
                if ( beaconRank > parseClusterheadMessageGetRank(in) )
                {
#if debugLevel > 1                    
                    a_printf("beacon rank was %i, now is %i + 1",beaconRank, parseClusterheadMessageGetRank(in));
#endif                    
                    beaconRank = parseClusterheadMessageGetRank(in) + 1;
                }
                isClusterhead = false;
                // jump to recluster NOW!
                nextLeachStage();
            }
        break;
#if debugLevel > 2    
        default:
            a_printf("nuffin!");
#endif
    }
    
}


/*
 * the receive function called if the mote is not a clusterhead
 */
static void clustermoteRecv( char* in )
{
#if debugLevel > 2
        a_printf("clustermote got %s\n", in);
#endif        
        
    switch (leachStage)
    {
        case RECLUSTER:
            if (startsWith (in, "CH") )
            {
                if (following == 0)
                {
                    following = parseClusterheadMessage(in);
                    timeBeforeChooseCluster = parseClusterheadMessageGetDelay(in);
                    
                    // reset the nextStage timer, since we're already in recluster
                    //ctimer_set ( &ct, timeBeforeChooseCluster, nextLeachStage, (void*)NULL);
                    timer_set ( &ct, timeBeforeChooseCluster, nextLeachStage);
                    timeBeforeChooseCluster = 0;
                    
                    if ( beaconRank > parseClusterheadMessageGetRank(in) )
                    {
#if debugLevel > 1                        
                        a_printf("beacon rank was %i, now is %i + 1",beaconRank, parseClusterheadMessageGetRank(in));
#endif                        
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
                // follow this node
                following = parseClusterheadMessage(in);
                isClusterhead = false;
                // jump to recluster NOW!
                nextLeachStage();
            }
        break;
#if debugLevel > 2        
        default:
            a_printf("nuffin!");
#endif

    }
}



void recv( )
{
    if (xbee.checkForData())
    {
        xbee.getResponse().getRx16Response(rx);
        //get the first byte

        char cleanString[32];
        bool allowRead = false;

        uint8_t cleanLen = 0;

        //Serial.print("got: ");    
        for (int i = 0 ; i < xbee.getResponse().getDataLength() ; i++)
        {
            char curr= xbee.getResponse().getData(i);
            //Serial.print(curr);

            if (curr == '>')
                allowRead = true;
            else if (curr == '<')
                allowRead = false;
            else if (allowRead)
            {
                if ( ( curr  >= 'a' && curr  <= 'z' ) || ( curr  >= 'A' && curr  <= 'Z' ) || ( curr  >= '0' && curr  <= '9' ) || curr == ' ' || curr == '-' || curr == ':' || curr == ';' )
                {
                    cleanString[cleanLen] =  curr;
                    cleanLen++;
                }
            }
        }
        cleanString[cleanLen] = '\0';
        cleanLen++;
        //Serial.println();
        //Serial.println(cleanString);
        // TODO CSMA work here - currently, using built-in csma (no CA)
        // if csma done, then
                
        leachRecv((char*)&cleanString);
    }
}   


/*
 * Callback to send a message
 * Would be called if there is a message already being heard.
 * Or can be called to send a message later.
 */
static void sendDelayedMessageCallback()
{
    delayedMessageTimer.handled = true;
    LeachSend((char*)&delayedMessage);
}
 
/*
 * min and max are sent in 10ths of a second.
 */ 
static void sendDelayedMessage(char* in, int min, int max)
{
    int thisDelay = getRandInt(min, max) * (CLOCK_SECOND/10);
    sprintf(delayedMessage, "%s",in);
#if debugLevel > 2    
    a_printf(delayedMessage, "Delayed Message: %s, %i",in, thisDelay);
#endif
    
    // TODO - make the timer configurable.
    //ctimer_set ( &delayedMessageTimer, thisDelay, sendDelayedMessageCallback, (void*)NULL);
    timer_set ( &delayedMessageTimer, thisDelay, sendDelayedMessageCallback);
}

static void sendDelayedMessageNoRand(char* in, int delay)
{
    
    sprintf(delayedMessage, "%s",in);
#if debugLevel > 2    
    a_printf(delayedMessage, "Delayed Message: %s, %i",in, delay);
#endif
    
    // TODO - make the timer configurable.
    //ctimer_set ( &delayedMessageTimer, thisDelay, sendDelayedMessageCallback, (void*)NULL);
    timer_set ( &delayedMessageTimer, delay, sendDelayedMessageCallback);
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
    parsedMessage.following = 0;
    parsedMessage.follower = 0;
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
#if debugLevel > 2                
            a_printf("parsing follow message: follower%s following%s ", followerBuf, followingBuf);
#endif
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
    
    return chId;
    
}

static int parseClusterheadMessageGetRank(char* message)
{
    char tempbuf [20];
            
    int rank = -1;
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
                if (count == 2)
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

static uint16_t parseClusterheadMessageGetDelay(char* message)
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
    
    //a_printf ("pre delay is %i, Delay is %i CS, cleanDelay is %i, which is %i s\n",getdelay, cleandelay, cleanestDelay, (cleandelay/CLOCK_SECOND));
    return cleanestDelay;
    
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

    for (int i = 0; i < numberOfFollowers; i++)
        if (clusterMembers[i] == id)
            return true;
    return false;
}

static void dumpFollowers()
{
    numberOfFollowers = 0;
}


static void makeAndSendSchedule()
{
    struct followerListEntry* s;
    int runtime = TOTAL_RUN_TIME / (numberOfFollowers + 1);  // +1 to leave some space at the end (and avoid div/0)
    
    char buildstr [64];
    char temp[20];
    sprintf (buildstr, "%i;", runtime);
    int thisDelay = 0;
#if debugLevel > 2    
    a_printf ("building schedule\n");
#endif
    
    for (int i = 0; i < numberOfFollowers; i++)
    {
#if debugLevel > 2
        a_printf ("%i:%i;", clusterMembers[i], thisDelay);
#endif
        sprintf(temp, "%i:%i;", clusterMembers[i], thisDelay);
        strcat(buildstr, temp);
        thisDelay += runtime;
    }
    
    sendDelayedMessage((char*)&buildstr, 0 , 10);
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
#if debugLevel > 2    
    a_printf("parsing schedule\n");
#endif
    
    for (i = 0; i < strlen(schedule) && !done; i++)
    {
#if debugLevel > 2        
        a_printf("%c", schedule[i]);
#endif
        if (schedule[i] == ';' || schedule[i] == ':')
        {
            // marks the end of a number
            
            // set back to copying stuff in.
            temp[currLen] = '\0';
            numberIn = atoi(temp);
#if debugLevel > 2            
            a_printf("\n%i\n", numberIn);
#endif            
            
            if (stage == 0)
            {
                runtimeIn = numberIn;
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
                    runtimeDelay = numberIn * CLOCK_SECOND;
                    runtime = runtimeIn * CLOCK_SECOND;     // convert to clock seconds.
                    done = true;
#if debugLevel > 2                    
                    a_printf("Found my junk! %i, %i\n",runtimeDelay, runtime);
#endif                    
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

bool allowListen()
{
    return true;
    return allowSend; // TODO - make this a little smarter
}



void checkTimers()
{
    
    /*struct timer ct;
    struct timer delayedMessageTimer;
    struct timer nextReading;*/
    
    //a_printf("c");

    if (timer_expired(&ct))
    {
        ct.handled = true;
        ct.callback();
        
    }
    if ( !delayedMessageTimer.handled && timer_expired(&delayedMessageTimer))
    {
        delayedMessageTimer.handled = true;        
        delayedMessageTimer.callback();
    }
    
    if ( !wd.handled && timer_expired(&wd))
    {
        wd.handled = true;        
        wd.callback();
    }
    
//    if ( !nextReading.handled && timer_expired(&nextReading)) // ditched. adding 5 (or n) readings each round
//    {
//        nextReading.handled = true;
//        nextReading.callback(); 
//    }
    
    /*
     * occasionally reset, and recluster.
     */
/*    if (lastTime < clock_time())
    {
        lastTime = clock_time();
    }
    else
    {
        // reset!
        leachStage = UNCLUSTERED;
        timer_set ( &ct, TOTAL_RUN_TIME * 2 + getRandInt(0,10), nextLeachStage);
        a_printf ("reclustering due to overflow!");
    }
*/    
    /*if ( allowListen() )
        if ( xbee.checkForData() )
            recv();
            */
            
        booted = true;
}


// this function will return the number of bytes currently free in RAM
// written by David A. Mellis
// based on code by Rob Faludi http://www.faludi.com
/*int availableMemory() {
  int size = 1024; // Use 2048 with ATmega328
  byte *buf;

  while ((buf = (byte *) malloc(--size)) == NULL)
    ;

  free(buf);

  return size;
}

*/


void system_sleep() {

//#if debugLevel > 1
//        a_printf("nap! 1second\n");
//#endif  

    // from http://www.arduino.cc/playground/Learning/ArduinoSleepCode
    //set_sleep_mode(SLEEP_MODE_STANDBY); // sleep mode is set here
    //set_sleep_mode(SLEEP_MODE_PWR_SAVE);
    
    set_sleep_mode(SLEEP_MODE_IDLE);
    sleep_mode();                        // System sleeps here
}



void check_still_running()
{
    // check to see if the process is still running.
    // if not, restart it.
    notRunning++;
    if ( notRunning > 3 )
    {
        leachStage = UNCLUSTERED;
        timer_set ( &ct, TOTAL_RUN_TIME * 2 + getRandInt(0,10), nextLeachStage);
        timer_set ( &wd, WATCHDOG, check_still_running);
    }
    else
    {
        // set up the watchdog again
        timer_set ( &wd, WATCHDOG, check_still_running);
    }
    
}



//****************************************************************
// 0=16ms, 1=32ms,2=64ms,3=128ms,4=250ms,5=500ms
// 6=1 sec,7=2 sec, 8=4 sec, 9= 8sec
void setup_watchdog(int ii) {

  byte bb;
  int ww;
  if (ii > 9 ) ii=9;
  bb=ii & 7;
  if (ii > 7) bb|= (1<<5);
  bb|= (1<<WDCE);
  ww=bb;
  Serial.println(ww);


  MCUSR &= ~(1<<WDRF);
  // start timed sequence
  WDTCSR |= (1<<WDCE) | (1<<WDE);
  // set new watchdog timeout value
  WDTCSR = bb;
  WDTCSR |= _BV(WDIE);


}
//****************************************************************  
// Watchdog Interrupt Service / is executed when  watchdog timed out
ISR(WDT_vect) {
}

