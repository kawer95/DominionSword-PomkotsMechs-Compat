package com.arxyt.dominionsword.pomkotscompat.control;

public interface MechControlBridge {
    void dominion$setControlFrame(MechControlFrame frame);
    MechControlFrame dominion$getControlFrame();
    void dominion$queueDriverInput(short bits);
    short dominion$getLastAppliedDriverInput();
}
