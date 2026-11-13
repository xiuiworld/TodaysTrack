package dev.todaystrack.vehicle.ui

import org.junit.Assert.*
import org.junit.Test

class UiPolicyTest {
    private fun ready()=UiControls(true,true,false,false,false,true,true,null)
    @Test fun unknownRestrictionsAndActiveCaptureCannotGenerate() {
        assertTrue(ready().canGenerate)
        assertFalse(ready().copy(allowed=false).canGenerate)
        assertFalse(ready().copy(capturing=true).canGenerate)
        assertFalse(ready().copy(requesting=true).canGenerate)
    }
    @Test fun availableTrackCannotCreateAgainButUncertainCanLookup() {
        assertFalse(ready().copy(intentState="AVAILABLE").canGenerate)
        assertTrue(ready().copy(intentState="ACCEPTANCE_UNCERTAIN").canGenerate)
        assertFalse(ready().copy(sealed=false).canGenerate)
        assertFalse(ready().copy(hasFacts=false).canGenerate)
    }
    @Test fun recoveredOpenRecordLocksSettingsButAllowsResume() {
        val open=ready().copy(hasOpen=true)
        assertTrue(open.canStart)
        assertFalse(open.canChangeSettings)
        assertFalse(open.copy(capturing=true).canStart)
        assertTrue(open.copy(capturing=true,allowed=false).canStop)
        assertTrue(ready().canChangeSettings)
    }
    @Test fun unseenNewSealWinsOnceThenOlderSelectionPersists() {
        val ids=listOf("new","old")
        assertEquals("new",RecordSelection.choose(ids,"old","old","new","old"))
        assertEquals("old",RecordSelection.choose(ids,"old","new","new","new"))
    }
    @Test fun startupRestoresSavedRecordAndEmptyStateStaysEmpty() {
        assertEquals("old",RecordSelection.choose(listOf("new","old"),null,"old","new","new"))
        assertNull(RecordSelection.choose(emptyList(),"missing","old",null,null))
        assertEquals("new",RecordSelection.choose(listOf("new"),"missing","old","new","new"))
    }
}
