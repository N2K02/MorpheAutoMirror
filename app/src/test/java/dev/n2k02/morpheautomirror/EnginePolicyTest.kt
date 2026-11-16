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
    @Test fun pauseKeepsActiveCaptureWithoutRestartOrNewConsent() = assertEquals(NextStep.MIRROR,
        EnginePolicy.plan(MirrorState(morphePlaying = false, morphePaused = true, capturing = true,
            carSession = true, parkedConfirmed = true, surfaceReady = true, permissionGranted = true)))
    @Test fun pausedBeforeCaptureDoesNotStartProjection() = assertEquals(NextStep.IDLE,
        EnginePolicy.plan(MirrorState(morphePaused = true, carSession = true,
            parkedConfirmed = true, surfaceReady = true)))
    @Test fun stoppedPlaybackEndsActiveCapture() = assertEquals(NextStep.STOP,
        EnginePolicy.plan(MirrorState(morphePlaying = false, morphePaused = false,
            capturing = true, carSession = true, parkedConfirmed = true,
            surfaceReady = true, permissionGranted = true)))
    @Test fun pausedCaptureStopsWhenCarDisconnects() = assertEquals(NextStep.STOP,
        EnginePolicy.plan(MirrorState(morphePaused = true, capturing = true,
            carSession = false, parkedConfirmed = true, surfaceReady = true,
            permissionGranted = true)))
    @Test fun pausedCaptureStopsWhenParkedApprovalRevoked() = assertEquals(NextStep.STOP,
        EnginePolicy.plan(MirrorState(morphePaused = true, capturing = true,
            carSession = true, parkedConfirmed = false, surfaceReady = true,
            permissionGranted = true)))
    @Test fun carDisconnectStopsCapture() = assertEquals(NextStep.STOP,
        EnginePolicy.plan(MirrorState(morphePlaying = true, capturing = true, carSession = false)))
    @Test fun carParkedRevokeStopsCapture() = assertEquals(NextStep.STOP,
        EnginePolicy.plan(MirrorState(morphePlaying = true, capturing = true, carSession = true,
            parkedConfirmed = false)))
    @Test fun quitStopsCapture() = assertEquals(NextStep.STOP,
        EnginePolicy.plan(MirrorState(monitoring = false, morphePlaying = true, capturing = true,
            carSession = true)))
    @Test fun missingSurfaceStopsExistingCapture() = assertEquals(NextStep.STOP,
        EnginePolicy.plan(MirrorState(morphePlaying = true, carSession = true,
            parkedConfirmed = true, surfaceReady = false, permissionGranted = true,
            capturing = true)))
    @Test fun noConsentRequestedAfterParkedApprovalRevoked() = assertEquals(NextStep.WAIT_FOR_PARKED,
        EnginePolicy.plan(MirrorState(morphePlaying = true, carSession = true,
            parkedConfirmed = false, surfaceReady = true)))
    @Test fun noConsentRequestedWhenCarAppIsNotRunning() = assertEquals(NextStep.WAIT_FOR_CAR,
        EnginePolicy.plan(MirrorState(morphePlaying = true, carSession = false,
            parkedConfirmed = false, surfaceReady = false)))
    @Test fun noMonitoringNeverStartsCapture() = assertEquals(NextStep.IDLE,
        EnginePolicy.plan(MirrorState(monitoring = false, morphePlaying = true,
            carSession = true, parkedConfirmed = true, surfaceReady = true)))
}
