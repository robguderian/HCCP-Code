
if [ $1 ]; then
../Contiki-2.4/tools/sky/serialdump-linux -b115200 $1
else
../Contiki-2.4/tools/sky/serialdump-linux -b115200 /dev/ttyUSB0
fi

