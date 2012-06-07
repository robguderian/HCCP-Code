
// these are C header files
extern "C" {
#include "cStuff.h"
}


#include "clock-avr.h"

#include "clock.h"


#include "Leach.h"

bool hasBooted = false;
 
void setup()
{
    if (!hasBooted)
    {
	    Serial.begin(9600);
	    Serial.println("boot");	
    }
}

void loop()
{
	// for some unknown reason, this does NOT want to work in setup. 
	//clock_init(); // this only seems happy if I call it twice? great./
	
	if (!hasBooted)
	{
        initLeach();
        Serial.println("Done");

        hasBooted = true;

    	for(;;)
    		delay(100000);
	}
	
}


