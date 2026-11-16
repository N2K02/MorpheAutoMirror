package dev.n2k02.morpheautomirror
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class EnginePolicyTest {
    @Test fun onlyConfiguredPackageIsMatched() {
        assertTrue(EnginePolicy.isMorphe("app.morphe.android.youtube", "app.morphe.android.youtube"))
        assertFalse(EnginePolicy.isMorphe("com.google.android.youtube", "app.morphe.android.youtube"))
        assertFalse(EnginePolicy.isMorphe("app.morphe.android.music", "app.morphe.android.youtube"))
        assertFalse(EnginePolicy.isMorphe(null, "app.morphe.android.youtube"))
    }
    @Test fun idleUnlessMorphePlaying() = assertEquals(NextStep.IDLE,
        EnginePolicy.plan(MirrorState(morphePlaying = false, carSession = true)))
    @Test fun waitForActiveCarAppSurface() = assertEquals(NextStep.WAIT_FOR_CAR,
        EnginePolicy.plan(MirrorState(morphePlaying = true)))
    @Test fun parkedMustBeConfirmedEveryConnection() = assertEquals(NextStep.WAIT_FOR_PARKED,
        EnginePolicy.plan(MirrorState(morphePlaying = true, carSession = true)))
    @Test fun waitForCarSurface() = assertEquals(NextStep.WAIT_FOR_SURFACE,
        EnginePolicy.plan(MirrorState(morphePlaying = true, carSession = true, parkedConfirmed = true)))
    @Test fun askConsentPerSession() = assertEquals(NextStep.ASK_PERMISSION,
        EnginePolicy.plan(MirrorState(morphePlaying = true, carSession = true,
            parkedConfirmed = true, surfaceReady = true)))
    @Test fun startWhenAllConditionsMet() = assertEquals(NextStep.MIRROR,
        EnginePolicy.plan(MirrorState(morphePlaying = true, carSession = true,
            parkedConfirmed = true, surfaceReady = true, permissionGranted = true)))
    @Test fun pauseStopsCapture() = assertEquals(NextStep.STOP,
        EnginePolicy.plan(MirrorState(morphePlaying = false, capturing = true)))
    @Test fun carDisconnectStopsCapture() = assertEquals(NextStep.STOP,
        EnginePolicy.plan(MirrorState(morphePlaying = true, capturing = true, carSession = false)))
    @Test fun carParkedRevokeStopsCapture() = assertEquals(NextStep.STOP,
        EnginePolicy.plan(MirrorState(morphePlaying = true, capturing = true, carSession = true,
            parkedConfirmed = false)))
    @Test fun quitStopsCapture() = assertEquals(NextStep.STOP,
        EnginePolicy.plan(MirrorState(monitoring = false, morphePlaying = true, capturing = true,
            carSession = true)))
}

