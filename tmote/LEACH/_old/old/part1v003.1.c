/*
 * Robert Guderian
 * WSN, Assignment started Oct 1, 2009
 * Base code borrowed from abc-example.c and test-button.c
 * A simple syn/ack program to test range of the motes 
 */

#include "contiki.h"
#include "net/rime.h"
#include "random.h"

#include "dev/button-sensor.h"

#include "dev/leds.h"

#include <stdio.h>
#include <string.h>
/*---------------------------------------------------------------------------*/
PROCESS(example_abc_process, "ABC example");
AUTOSTART_PROCESSES(&example_abc_process);
/*---------------------------------------------------------------------------*/
static void
abc_recv(struct abc_conn *c)
{
	printf("message received '%s'\n", (char *)packetbuf_dataptr());
	leds_toggle(LEDS_GREEN);
	packetbuf_clear();
	packetbuf_attr_clear();

}	
static const struct abc_callbacks abc_call = {abc_recv};
static struct abc_conn abc;
/*---------------------------------------------------------------------------*/
PROCESS_THREAD(example_abc_process, ev, data)
{
	static struct etimer et;

	PROCESS_EXITHANDLER(abc_close(&abc);)

	PROCESS_BEGIN();
	char buffer[32];	
	
	button_sensor.activate();
	
	printf("ready to rock\n");
	
	static int i;
	while (1) {}
	

	abc_open(&abc, 128, &abc_call);

	PROCESS_WAIT_EVENT_UNTIL(ev == sensors_event && data == &button_sensor);

	
	
	while(1) {
		
		etimer_set(&et, CLOCK_SECOND * 0.5);
		
		PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
			
		sprintf(buffer, "ping %d\0",i);
		
		printf("Attempting to send %s\n", buffer);
		
		packetbuf_clear();

		packetbuf_copyfrom(&buffer, (int)strlen(buffer));
		abc_send(&abc);
		printf("Message sent\n");
		leds_toggle(LEDS_RED);

		i = i + 1;
	}

	PROCESS_END();
}
/*---------------------------------------------------------------------------*/
