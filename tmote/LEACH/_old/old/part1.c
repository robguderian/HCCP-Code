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
/*---------------------------------------------------------------------------*/
PROCESS(example_abc_process, "ABC example");
AUTOSTART_PROCESSES(&example_abc_process);
/*---------------------------------------------------------------------------*/
static void
abc_recv(struct abc_conn *c)
{
	printf("abc message received '%s'\n", (char *)packetbuf_dataptr());
}
static const struct abc_callbacks abc_call = {abc_recv};
static struct abc_conn abc;
/*---------------------------------------------------------------------------*/
PROCESS_THREAD(example_abc_process, ev, data)
{
	//static struct etimer et;

	PROCESS_EXITHANDLER(abc_close(&abc);)

	PROCESS_BEGIN();
	int count = 0;	
	char buffer[32];	

	
	button_sensor.activate();

	abc_open(&abc, 128, &abc_call);

	while(1) {

		PROCESS_WAIT_EVENT_UNTIL(ev == sensors_event && data == &button_sensor);

		packetbuf_copyfrom("ping!", 6);
		abc_send(&abc);
		printf("abc message sent\n");
	}

	PROCESS_END();
}
/*---------------------------------------------------------------------------*/
