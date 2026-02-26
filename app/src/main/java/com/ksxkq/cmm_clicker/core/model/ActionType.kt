package com.ksxkq.cmm_clicker.core.model

enum class ActionType(val raw: String) {
    UNKNOWN("unknown"),
    DUP_CLICK("dupClick"),
    CLICK("click"),
    SWIPE("swipe"),
    RECORD("record"),
    BACK("back"),
    HOME("home"),
    RECENT("recent"),
    FOLDER("folder"),
    JUMP("jump"),
    LAUNCH_APP("launchApp"),
    IMAGE_RECOGNIZE("imageRecognize"),
    BUTTON_RECOGNIZE("buttonRecognize"),
    COLOR_RECOGNIZE("colorRecognize"),
    SET_TXT_TO_INPUT("setTxtToInput"),
    REQUEST_INPUT("requestInput"),
    PAUSE_TASK("pauseTask"),
    RANDOM_WAIT("randomWait"),
    COPY_TEXT("copyText"),
    CHECK_TIME("checkTime"),
    NET_REQUEST("netRequest"),
    ALERT("alert"),
    SUB_TASK("subTask"),
    LOCK_SCREEN("lockScreen"),
    INTENT_URI("intentUri"),
    SCREENSHOT("screenshot"),
    STOP_TASK("stopTask"),
    AREA_CLICK("areaClick"),
    CHECK_ACTIVITY("checkActivity"),
    MINI_PROGRAM("miniProgram"),
    SET_VARIABLE("setVariable"),
    CHECK_VARIABLE("checkVariable"),
    OPERATE_VARIABLE("operateVariable"),
    SET_VARIABLE_CHOICE_DIALOG("setVariableChoiceDialog"),
    SET_VARIABLE_BATCH("setVariableBatch"),
    CHECK_BRANCH("checkBranch"),
    CLOSE_CURRENT_UI("closeCurrentUI"),
    CREATE_VARIABLE_VALUE("createVariableValue"),
    SCROLL("scroll"),
    ;

    companion object {
        private val rawTypeMap = entries.associateBy { it.raw }

        fun fromRaw(raw: String?): ActionType {
            if (raw.isNullOrBlank()) {
                return UNKNOWN
            }
            return rawTypeMap[raw] ?: UNKNOWN
        }
    }
}
