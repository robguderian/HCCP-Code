package Util;

import com.sun.spot.peripheral.radio.RadioFactory;
import java.util.Date;
import java.util.Random;

public class Utility {
    
    private static Random rand = new Random( RadioFactory.getRadioPolicyManager().getIEEEAddress() + new Date().getTime());

    public static boolean isInteger( String input )  
    {  
       try  
       {  
          Integer.parseInt( input );  
          return true;  
       }  
       catch( Exception e)  
       {  
          return false;  
       }  
    }  
    
    public static boolean isDouble( String input )  
    {  
       try  
       {  
          Double.parseDouble( input );  
          return true;  
       }  
       catch( Exception e)  
       {  
          return false;  
       }  
    }  
    
    
    
    public static int randXY(int start, int end)
    {
        return start + (rand.nextInt(end-start+1));
    }
    
    public static float rand01()
    {
        return rand.nextFloat();
    }
}
