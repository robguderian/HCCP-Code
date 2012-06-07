/*
 * Robert Guderian
 * WSN, Assignment started Oct 1, 2009
 * Base code borrowed from unicast.c and test-button.c
 * A simple syn/ack program to test range of the motes 
 */
#include "contiki.h"
#include "net/rime.h"

#include "radio.h"

#include "dev/button-sensor.h"

#include "dev/leds.h"

#include <stdio.h>

/*---------------------------------------------------------------------------*/
PROCESS(example_unicast_process, "Example unicast");
AUTOSTART_PROCESSES(&example_unicast_process);
/*---------------------------------------------------------------------------*/
static void
recv_uc(struct unicast_conn *c, rimeaddr_t *from)
{
	printf("unicast message received from %d.%d\n",
		   from->u8[0], from->u8[1]);
}
static const struct unicast_callbacks unicast_callbacks = {recv_uc};
static struct unicast_conn uc;
/*---------------------------------------------------------------------------*/
PROCESS_THREAD(example_unicast_process, ev, data)
{
	PROCESS_EXITHANDLER(unicast_close(&uc);)
    
	PROCESS_BEGIN();
	
	unicast_open(&uc, 128, &unicast_callbacks);
	
	radio_set_tx_power_level(88);
	
	while(1) {
		static struct etimer et;
		rimeaddr_t addr;
		
		etimer_set(&et, CLOCK_SECOND);
		
		PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
		
		packetbuf_copyfrom("Hello", 5);
		addr.u8[0] = 70;
		addr.u8[1] = 77;
		unicast_send(&uc, &addr);
		
	}
	
	PROCESS_END();
}
/*---------------------------------------------------------------------------*/
