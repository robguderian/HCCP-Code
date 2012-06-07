CONTIKI = ../../Contiki-2.4
#CFLAGS = -DWITH_NULLMAC=1  -DIEEE802154_CONF_PANID=3
CFLAGS = -DWITH_SICSLOWMAC=1 -DIEEE802154_CONF_PANID=3
ifndef TARGET
TARGET=sky
endif

all: hetero 


%.tgz: %.ihex
	mkdir $(basename $<) ; \
	mv $< $(basename $<) ; \
	echo $(basename $<)/$(basename $<).ihex 600 > $(basename $<)/runfile ; \
	tar czf $@ $(basename $<)

%.class: %.java
	javac $(basename $<).java

viewrssi: ViewRSSI.class
	make login | java ViewRSSI

include $(CONTIKI)/Makefile.include
