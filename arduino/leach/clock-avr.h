#ifndef CONTIKI_CLOCK_AVR_H
#define CONTIKI_CLOCK_AVR_H

// ONLY WORKS FOR 328P chip.

#define OCRSetup() \
  /* Select internal clock */ \
  ASSR = 0x00; 				  \
\
  /* Set counter to zero */   \
  TCNT0 = 0;				  \
\
  /*						  \
   * Set comparison register: \
   * Crystal freq. is 16000000,\
   * pre-scale factor is 1024, i.e. we have 125 "ticks" / sec: \
   * 16000000 = 1024 * 125 * 125 \

   * What I think.
	16000000/1024 = 15625 ticks/second. = 125*125

   */ \
  OCR0A = 125; \
\
  /* 								\
   * Set timer control register: 	\
   *  - prescale: 1024 (CS00 - CS02) \
   *  - counter reset via comparison register (WGM01) \
   */ 								\
  TCCR0A =  _BV(WGM01); \
  TCCR0B =  _BV(CS00) | _BV(CS02); \
\
  /* Clear interrupt flag register */ \
  TIFR0 = 0x00; \
\
  /* \
   * Raise interrupt when value in OCR0 is reached. Note that the \
   * counter value in TCNT0 is cleared automatically. \
   */ \
  TIMSK0 = _BV (OCIE0A);

#define AVR_OUTPUT_COMPARE_INT TIMER0_COMPA_vect


#endif //CONTIKI_CLOCK_AVR_H
