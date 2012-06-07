/*
 * Robert Guderian
 * WSN, Assignment started Oct 1, 2009
 * Base code borrowed from abc-example.c and test-button.c
 * A simple syn/ack program to test range of the motes 
 */

#include "contiki.h"
//#include "net/rime.h"
#include "random.h"

#include "dev/button-sensor.h"

#include "dev/leds.h"

#include <stdio.h>
#include <string.h>


#include "dev/cc2420.h"
//#include "net/mac/nullmac.h"
#include "net/mac/sicslowmac.h"
#include "net/rime.h"
#include "net/rime/channel.h"
#include "net/rime/packetbuf.h"


	


//static u16_t panId = 0x2024;



#define ATTRIBUTES


/*---------------------------------------------------------------------------*/
PROCESS(example_abc_process, "ABC example");
AUTOSTART_PROCESSES(&example_abc_process);
/*---------------------------------------------------------------------------*/
static void
recv( const struct mac_driver *c)
{
	packetbuf_set_attr(PACKETBUF_ATTR_CHANNEL, 34);
	int len = c->read();
	char cleanString [128];
	int cleanLen = 0;
	int i;
	char* in=  (char*)packetbuf_dataptr();
	for ( i = 0; i < len; i++)
	{
		
		if ( ( in[i]  >= ' ' && in[i]  <= 'z' ))
		{
			cleanString[cleanLen] =  in[i];
			cleanLen++;
		}
	}
	cleanString[cleanLen] = '\0';
	cleanLen++;
	printf("message received, len = %d, '%s'\n", cleanLen, cleanString);
	//printf("message received, len = %d, '%s'\n", len, (char *)packetbuf_dataptr());
	leds_toggle(LEDS_GREEN);
	//packetbuf_clear();
	//packetbuf_attr_clear();


}	

//static void
//recv(struct abc_conn *c)
//{
 /* printf("message received '%s'\n", (char *)packetbuf_dataptr());
	leds_toggle(LEDS_GREEN);
	packetbuf_clear();
	packetbuf_attr_clear();
  */
	
//}

/*static void
set_rime_addr(void)
{
  rimeaddr_t addr;
  addr.u8[0] = node_id & 0xff;
  addr.u8[1] = node_id >> 8;
  rimeaddr_set_node_addr(&addr);
}*/	

//static const struct mac_driver recv_call = {recv};

//static struct abc_conn abc;
//const static struct abc_callbacks abc_call = {recv};
static const struct mac_driver * driver;
static const struct packetbuf_attrlist attributes[] =
  { ATTRIBUTES PACKETBUF_ATTR_LAST };
/*---------------------------------------------------------------------------*/
PROCESS_THREAD(example_abc_process, ev, data)
{
	
	static struct channel *c;
	static struct etimer et;

	PROCESS_BEGIN();
	char buffer[32];	
	
	//button_sensor.activate();
	SENSORS_ACTIVATE(button_sensor);
	
	printf("ready to rock\n");
	
	//ds2411_init();
	static int i;
	
	
	//cc2420_init();
	//cc2420_set_pan_addr(panId, 0 /*XXX*/, ds2411_id);
	//cc2420_set_channel(26);

	//abc_open(&abc, 128, &abc_call);
//  set_rime_addr();	
//  cc2420_init();
  //cc2420_set_pan_addr(panId, 0 , ds2411_id);
//  cc2420_set_channel(RF_CHANNEL);

  //cc2420_set_txpower(31);
  //nullmac_init(&cc2420_driver);
  //rime_init(&nullmac_driver);


	//cc2420_set_txpower(31);

	//cc2420_on();
	channel_init();

	packetbuf_clear();

	//driver = nullmac_init(&cc2420_driver);	
        //rime_init(driver);
	driver = sicslowmac_init(&cc2420_driver);
	
	cc2420_set_channel(26);
	//cc2420_set_channel(34);
		
	channel_open(c, 34);		
	/*packetbuf_clear();*/
	
	driver->set_receive_function(recv);
	//packetbuf_clear();
	
	channel_set_attributes(34, attributes);
	//set_receive_function(driver);	

	driver->on();	
	leds_toggle(LEDS_RED);
	
	packetbuf_clear();
	etimer_set(&et, CLOCK_SECOND * 2 + random_rand() % (CLOCK_SECOND * 2));
	PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
	leds_toggle(LEDS_RED);
	PROCESS_WAIT_EVENT_UNTIL(ev == sensors_event && data == &button_sensor);
	

	while(1)
	{
		if (i % 20 == 19)
		{
			PROCESS_WAIT_EVENT_UNTIL(ev == sensors_event && data == &button_sensor);
		}
		else
		{

			printf("wait!");
			etimer_set(&et, CLOCK_SECOND * 0.5 +  random_rand() % (CLOCK_SECOND) * 0.25);
				
			PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
		}	
		sprintf(buffer, "ping %d\0",i);
		
		printf("Attempting to send %s\n", buffer);

		// send the message straight to the radio
		//cc2420_send ( buffer, (int)strlen(buffer) );
		packetbuf_clear();

		packetbuf_copyfrom(&buffer, (int)strlen(buffer));
                //driver->on();
		//cc2420_driver.send(&buffer, (int)strlen(buffer));
		//cc2420_send ( &buffer, (int)strlen(buffer) );
		
		driver->send();
		
		//rime_output();		
		//abc_send(&abc);
		//chameleon_output(c);
		//driver->off(1);		
		printf("Message sent\n");
		leds_toggle(LEDS_YELLOW);

		i = i + 1;
	}

	PROCESS_END();
}
/*---------------------------------------------------------------------------*/
