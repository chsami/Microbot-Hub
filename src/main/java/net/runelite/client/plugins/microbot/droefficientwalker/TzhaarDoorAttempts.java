package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.function.BooleanSupplier;

/** Normal travel has two observed pass attempts, with no cape actions or silent retry loop. */
final class TzhaarDoorAttempts {
    private TzhaarDoorAttempts() { }
    static boolean pass(BooleanSupplier allowed,BooleanSupplier landed,BooleanSupplier click,BooleanSupplier awaitLanding){
        return pass(allowed,landed,click,awaitLanding,()->false,()->true);
    }
    static boolean pass(BooleanSupplier allowed,BooleanSupplier landed,BooleanSupplier click,BooleanSupplier awaitLanding,
                        BooleanSupplier busy,BooleanSupplier awaitIdle){
        for(int attempt=0;attempt<2;attempt++){
            if(landed.getAsBoolean())return true;
            if(!allowed.getAsBoolean())return false;
            if(busy.getAsBoolean()&&!awaitIdle.getAsBoolean())return false;
            if(landed.getAsBoolean())return true;
            if(!allowed.getAsBoolean())return false;
            click.getAsBoolean();
            if(awaitLanding.getAsBoolean())return true;
            // A timeout is not permission to interrupt a pass animation with another click.
            if(busy.getAsBoolean()){
                if(!awaitIdle.getAsBoolean())return false;
                if(landed.getAsBoolean())return true;
            }
        }
        return landed.getAsBoolean();
    }
}
