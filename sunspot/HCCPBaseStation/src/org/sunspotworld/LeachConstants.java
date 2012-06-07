package org.sunspotworld;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */



public class LeachConstants {
	public static final long RECLUSTER_TIME = 10* 1000;
	public static final long CHOOSE_CLUSTERHEAD_TIME = 10 * 1000;
	public static final long WAIT_FOR_SCHEDULE_TIME = 10 * 1000;
	public static final long TOTAL_RUN_TIME = 20 * 1000; // n minutes times 60 seconds
        
        public static final int NUMBER_OF_SCHEDULED_RUNS = 1;
        
        public static final int ADDRESS_MOD_BY = 1000;
        
        public static final int EARLY_END_SEND = 300;
        
        public static final int NUMBER_OF_MESSAGE_TO_CREATE_PER_ROUND = 5;
	
	
        public static final boolean hccp_allow_firstorder_ch = true;
        public static final double hccp_firstorder_ch_perc = 0.1;
        
        public static final boolean allClusterheads = false;
        
        
        
        public static double SENSOR_MISSION_WEIGHT = 0;
	public static double MESSAGE_QUEUE_WEIGHT = 0.9;
	public static double BATTERY_POWER_WEIGHT = 0;
	public static double RANDOM_WEIGHT = 0.1;
	public static double DUTY_CYCLE_WEIGHT = 0;
        
        public static boolean allowSuboptimalClusterheads = true;
        public static double suboptimalClusterheadPercentage = 0.1;
        public static double firstOrderSuboptimalPercentage = 0.2;
        
        public static long SLEEP_TIME   = 100; // in ms
        public static long SLEEP_NO_RECLUSTER_TIME = 100;
        public static long ROUNDTABLE_TIME = 100;
        
        
        
        public static double CRITICAL_BATTERY_LEVEL = 0.05;
}
