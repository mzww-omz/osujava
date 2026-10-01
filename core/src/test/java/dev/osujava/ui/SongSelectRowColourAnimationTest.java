package dev.osujava.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectRowColourAnimationTest {
    private static final int BLUE = 0x0096ecf0, WHITE = 0xffffffdc, HOVER = 0x26c7fff0;
    private final SongSelectRowColourAnimation animation = new SongSelectRowColourAnimation();

    private void frame(int state, int base, boolean focus, boolean hover, long now, int delta) {
        animation.update(state, base, focus, hover, now, delta);
    }
    private void blue(boolean focus, boolean hover, long now) { frame(3, BLUE, focus, hover, now, 0); }

    @Test void creationUsesTheBaseImmediately() {
        blue(false,false,100);
        assertEquals(BLUE,animation.rgba());
    }
    @Test void stateChangeInterpolatesEveryByteForThreeHundredMilliseconds() {
        frame(4,WHITE,false,false,0,0);
        blue(false,false,100);
        assertEquals(WHITE,animation.rgba());
        blue(false,false,250);
        assertEquals(0x7fcaf5e6,animation.rgba());
        blue(false,false,400);
        assertEquals(BLUE,animation.rgba());
    }
    @Test void newTransformStartsOneIntegerFrameBeforeNow() {
        frame(4,WHITE,false,false,0,0);
        frame(3,BLUE,false,false,100,16);
        assertEquals(0xf2f9fedd,animation.rgba());
        blue(false,false,242);
        assertEquals(0x7fcaf5e6,animation.rgba());
    }
    @Test void hoverIsAOneSecondFlashEvenWhileThePointerStays() {
        blue(false,false,0);
        blue(false,true,100);
        assertEquals(HOVER,animation.rgba());
        blue(false,true,600);
        assertEquals(0x13aef5f0,animation.rgba());
        blue(false,true,1101);
        assertEquals(BLUE,animation.rgba());
        blue(false,true,2000);
        assertEquals(BLUE,animation.rgba());
    }
    @Test void leavingDoesNotCancelTheFlashAndRehoverRetainsItsOriginalReturnColour() {
        blue(false,false,0);
        blue(false,true,100);
        blue(false,false,600);
        assertEquals(0x13aef5f0,animation.rgba());
        blue(false,true,700);
        assertEquals(HOVER,animation.rgba());
        blue(false,true,1701);
        assertEquals(BLUE,animation.rgba());
    }
    @Test void stateTransitionBlocksHoverWithoutQueuingIt() {
        frame(4,WHITE,false,false,0,0);
        blue(false,false,100);
        blue(false,true,250);
        assertEquals(0x7fcaf5e6,animation.rgba());
        blue(false,true,401);
        blue(false,true,1500);
        assertEquals(BLUE,animation.rgba());
        blue(false,false,1501);
        blue(false,true,1502);
        assertEquals(HOVER,animation.rgba());
    }
    @Test void focusInterruptsFlashFromLastDisplayedColourAndFinishesInFiftyMilliseconds() {
        blue(false,false,0);
        blue(false,true,100);
        blue(false,true,600);
        blue(true,true,700);
        assertEquals(0x13aef5f0,animation.rgba(),"Do not advance the old flash before handling focus");
        blue(true,true,725);
        assertEquals(0x09c0faf0,animation.rgba());
        blue(true,true,751);
        assertEquals(0x00d2fff0,animation.rgba());
        blue(true,false,752);
        blue(true,true,753);
        assertEquals(0x26fffff0,animation.rgba(),"Flash derives from the stored focus target");
    }
    @Test void focusReleaseInterpolatesBackToTheBaseAndBlocksSimultaneousHover() {
        blue(false,false,0);
        blue(true,false,100);
        blue(true,false,151);
        blue(false,true,200);
        assertEquals(0x00d2fff0,animation.rgba());
        blue(false,true,225);
        assertEquals(0x00b4f5f0,animation.rgba());
        blue(false,true,251);
        assertEquals(BLUE,animation.rgba());
    }
    @Test void stateChangeReplacesFocusTargetWithoutReapplyingUnchangedFocus() {
        frame(1,0xeb4999f0,false,false,0,0);
        frame(1,0xeb4999f0,true,false,100,0);
        frame(1,0xeb4999f0,true,false,151,0);
        blue(true,false,200);
        blue(true,false,501);
        assertEquals(BLUE,animation.rgba());
    }
    @Test void paletteChangeInSameStateAnimatesPlayedAndGroupContainmentChanges() {
        frame(1,0x23328fff,false,false,0,0);
        frame(1,0x235ac1ff,false,false,100,0);
        frame(1,0x235ac1ff,false,false,250,0);
        assertEquals(0x2346a8ff,animation.rgba());
        frame(1,0x235ac1ff,false,false,401,0);
        assertEquals(0x235ac1ff,animation.rgba());
    }
    @Test void completedTransformRemainsUntilASpriteUpdateStrictlyAfterItsEnd() {
        frame(4,WHITE,false,false,0,0);
        blue(false,false,100);
        blue(false,false,400);
        blue(false,true,401);
        assertEquals(BLUE,animation.rgba(),"The event sees the transform left by the preceding frame");
        blue(false,false,402);
        blue(false,true,403);
        assertEquals(HOVER,animation.rgba());
    }
    @ParameterizedTest @ValueSource(ints={30,60,144})
    void frameRatesUseAbsoluteIntegerTimeAndReachTheSameEndpoint(int fps) {
        frame(4,WHITE,false,false,0,0);
        for (int i=1;i<=fps;i++) frame(3,BLUE,false,false,(long)(i*1000.0/fps),(int)(1000.0/fps));
        assertEquals(BLUE,animation.rgba());
        long begin=1000+(long)(1000.0/fps);
        for (int i=0;i<=fps+1;i++) frame(3,BLUE,false,true,begin+(long)(i*1000.0/fps),(int)(1000.0/fps));
        assertEquals(BLUE,animation.rgba());
    }
    @Test void unevenAndLongFramesFinishWithoutColourOvershoot() {
        frame(4,WHITE,false,false,0,0);
        frame(3,BLUE,false,false,7,7);
        frame(3,BLUE,false,false,39,32);
        frame(3,BLUE,false,false,48,9);
        frame(3,BLUE,false,false,409,361);
        assertEquals(BLUE,animation.rgba());
    }
}
