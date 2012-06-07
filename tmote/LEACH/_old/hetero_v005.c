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
#include "net/mac/nullmac.h"
#include "net/rime.h"
#include "net/rime/channel.h"
#include "net/rime/packetbuf.h"
#include "dev/ds2411.h"
#include "node-id.h"


#include "net/mac/sicslowmac.h"


static u16_t panId = 0x2024;



#define ATTRIBUTES


/*---------------------------------------------------------------------------*/
PROCESS(example_abc_process, "ABC example");
AUTOSTART_PROCESSES(&example_abc_process);
/*---------------------------------------------------------------------------*/
/*static void
recv( const struct mac_driver *c)
{
	printf("message received '%s'\n", (char *)packetbuf_dataptr());
	leds_toggle(LEDS_GREEN);
	packetbuf_clear();
	packetbuf_attr_clear();

}*/	

static void
recv(struct abc_conn *c)
{
  printf("message received '%s'\n", (char *)packetbuf_dataptr());
	leds_toggle(LEDS_GREEN);
	packetbuf_clear();
	packetbuf_attr_clear();
  
}

static void
set_rime_addr(void)
{
  rimeaddr_t addr;
  addr.u8[0] = node_id & 0xff;
  addr.u8[1] = node_id >> 8;
  rimeaddr_set_node_addr(&addr);
}

//static const struct mac_driver recv_call = {recv};

static struct abc_conn abc;
const static struct abc_callbacks abc_call = {recv};
static const struct mac_driver * driver;
static const struct packetbuf_attrlist attributes[] =
  { ATTRIBUTES PACKETBUF_ATTR_LAST };
/*---------------------------------------------------------------------------*/
PROCESS_THREAD(example_abc_process, ev, data)
{
	//static struct etimer et;

	static struct channel *c;

	PROCESS_BEGIN();
	char buffer[32];	
	
	//button_sensor.activate();
	SENSORS_ACTIVATE(button_sensor);
	
	printf("ready to rock\n");
	
	ds2411_init();
	static int i, j, k;
	
	
	//cc2420_init();
	//cc2420_set_pan_addr(panId, 0 /*XXX*/, ds2411_id);
	//cc2420_set_channel(26);

	abc_open(&abc, 128, &abc_call);
/*  set_rime_addr();	
  cc2420_init();
  cc2420_set_pan_addr(panId, 0 , ds2411_id);
  cc2420_set_channel(RF_CHANNEL);

  cc2420_set_txpower(31);
  nullmac_init(&cc2420_driver);
  rime_init(&nullmac_driver);
*/

	//cc2420_set_txpower(31);

	//cc2420_on();

	/*driver = nullmac_init(&cc2420_driver);	
        rime_init(driver);
	//driver = sicslowmac_init(&cc2420_driver);
	//driver->on();	
	channel_open(c, 39);		
	packetbuf_clear();*/
	
	//driver->set_receive_function(recv);
	//packetbuf_clear();
	
	//channel_set_attributes(39, attributes);
	//set_receive_function(driver);	

	

	
	
	while(1) {
		//PROCESS_WAIT_EVENT_UNTIL(ev == sensors_event && data == &button_sensor);
		
		for (j = 0; j < 10000; j++)
			k = j/23;
			
		sprintf(buffer, "ping %d, %d",i, k);
		
		printf("Attempting to send %s\n", buffer);

		// send the message straight to the radio
		//cc2420_send ( buffer, (int)strlen(buffer) );
		

		packetbuf_copyfrom(&buffer, (int)strlen(buffer));
                //driver->on();
		//cc2420_driver.send(&buffer, (int)strlen(buffer));
		//cc2420_send ( &buffer, (int)strlen(buffer) );
		
		//driver->send();
		//rime_output();		
		abc_send(&abc);
		//chameleon_output(c);
		//driver->off(1);		
		printf("Message sent\n");
		leds_toggle(LEDS_RED);

		i = i + 1;
	}

	PROCESS_END();
}
/*---------------------------------------------------------------------------*/
