
package com.colorcloud.movementsensor.walksensor;

public class Engine {

    public StepCounter.MotionVar m_Motion = new StepCounter.MotionVar(); /*
                                                     * Output, current motion
                                                     * status
                                                     */

    public HarConfig m_Config = new HarConfig();

    public Record.HarRecord m_HarRecord = new Record.HarRecord();

    // public THarGesture m_HarGesture ;

    public HarResult m_HarResult = new HarResult();

    /* Basic information from the input */
    public int m_iTotalFrm; /* total input frame, 4G/100Hz/3600/24h = 497 days */
    public int m_iX; /* nX - base */
    public int m_iY; /* nY - base */
    public int m_iZ; /* nZ - base */
    public long m_iEnergy; /* x^2 + y^2 + z^2 */
    
    public long m_timeStamp;

    public int m_iResuAccel;
    public int m_iAccelInGrav;

    public int m_iAbsResuAccel; /* S16(m_fResuAccel - gravity) */
    public int m_iPulseVal; /* (in pulse top) ? (the pulse height) : (0) */
    public int m_iFltrdAccel; /* filtered m_iAbsResuAccel */

    public int m_iAngleX; /* Q8 */
    public int m_iAngleY; /* Q8 */
    public int m_iAngleZ; /* Q8 */

    public boolean m_bEnterLoseWeight;
    public boolean m_bFirstWalk;
    public boolean m_bFirstRun;
    public boolean m_bAfterJump;

    public boolean m_bInitOK;

    public int m_iCountTwoSteps;
    public int m_iCountTwoRuns;
    public int m_iCountAfterLastRun;
    public int m_iCountAfterJump;
    public int m_iCountAfterLastPulse; /*
                                         * points # after last pulse (step), to
                                         * remove too close pulses.
                                         */
    public int m_iPeakIntervalCount;
    public int m_iLostWeightCount;

    public int m_iGravity; /* the absolute gravity unit, in Q16 */
    public int m_iFPS; /* the FPS unit, in Q0 */

    public int m_iThresStable; /*
                                 * the threshold to check absolute acceleration
                                 * is close to gravity
                                 */
    public int m_iThresEnterLW; /* the threshold to enter weightlessness status */
    public int m_iThresLeaveLW; /* the threshold to leave weightlessness status */
    public int m_iThresDynPulseAdd; /*
                                     * the dynamic threshold of pulse detect =
                                     * min + @_
                                     */
    public int m_iThresDynPulseAddLow; /*
                                         * use lower m_iThresDynPulseAdd, is
                                         * walking is stable
                                         */
    public int m_iThresDynPulseAdpt; /*
                                     * m_iThresDynPulseAdd + @_ to adapt holding
                                     * mode
                                     */
    public int m_iThresResuAccelFix; /*
                                     * the threshold to ensure the pulse,
                                     * compare with m_iAbsResuAccel
                                     */
    public int m_iThresRunJump; /*
                                 * weightlessness point number, <= is Run, > is
                                 * Jump
                                 */
    public int m_iThresMaxRunInte; /*
                                     * max interval between two steps of run,
                                     * one can not run too slow
                                     */
    public int m_iThresMinJumpInte; /*
                                     * min interval between two jumps, one can
                                     * not jump too fast
                                     */
    public int m_iThresMaxStepInte; /*
                                     * max interval between two steps of run or
                                     * walk, for walk detect
                                     */
    public int m_iThresMinStepInte; /*
                                     * min interval between two steps of run or
                                     * walk, for step count
                                     */
    public int m_iThresWalkToStill; /*
                                     * max non-pulse interval after which the
                                     * status turn to STILL
                                     */

    public int m_iLenPeakInterval;
    public int m_iLenPulseDetect;
    public int m_iLenStableCheck;
    public int m_iLenSearchAccel; /* ResuAccelHist search len for ensure pulse */

    public int[][] m_iHist = new int[3][Recognizer.STABLE_CHECK_LEN];
    public long[] m_iHist_timeStamp = new long [Recognizer.STABLE_CHECK_LEN]; // to include time stamp in historical data
    public int m_iHistPos=0;

    public int[] m_iAccelHist = new int[Recognizer.PULSE_DETECT_LEN];
    public int m_iAccelHistPos=0;
    
    public int[][] m_iHistForDist = new int[3][Recognizer.LEN_PULSE_DISTANCE_N];
    public int m_iHistForDistPos=0;
    
    public double distCovered = 0.0;

    public int[] m_iGaussBuf = new int[Recognizer.FILTER_GAUSS_LEN];
    public int[] m_iGaussWin = new int[Recognizer.FILTER_GAUSS_LEN];
    public int m_iGaussWinLen;
    public int[] m_iMeanBuf = new int[Recognizer.FILTER_MEAN_LEN];
    public int m_iMeanWinLen;

    public int[] m_iAbsResuAccelHist = new int[Recognizer.PULSE_ENSURE_LEN];
    public int m_iAbsResuAccelHistLen;

    public int[] m_iStepInteHist = new int[Recognizer.STEP_INTER_HIST_LEN];
    public int m_iStepInteMean; /* in Q8 */
    public int m_iStepInteDiff; /* in Q8 */
    public int m_iStepInteThres; /* in Q8 */

    public int[] m_iRawPulseHist = new int[Recognizer.RAW_PLUSE_HIST_LEN];
    public int m_iActIntensity; /* 0->tiny(watch), 1->middle, 2->hard(pocket) */

    public StepCounter.StepDelayShowVar m_tSDS = new StepCounter.StepDelayShowVar(); /*
                                                                 * Do not show
                                                                 * steps at
                                                                 * beginning
                                                                 * until walking
                                                                 * state
                                                                 * ensured.
                                                                 */

    public static final int HAR_MOTION_STILL = 0;
    public static final int HAR_MOTION_WALK = 1;
    public static final int HAR_MOTION_RUN = 2;
    public static final int HAR_MOTION_JUMP = 3;
    public static final int HAR_MOTION_MAX = 4;

    public static final int HAR_CFG_CALC_ENERGY = 0x0001; /* calculate energy */
    public static final int HAR_CFG_CHK_STEP_STABLE = 0x0002; /* should be 1 */
    public static final int HAR_CFG_CHK_RAW_PULSE = 0x0004; /* should be 1 */
    public static final int HAR_CFG_ADAPT_WINLEN = 0x0008;
    public static final int HAR_CFG_DBG_CHG_FLTRD = 0x0010;
    public static final int HAR_CFG_DBG_SHOW_LOG = 0x0020;
    public static final int HAR_CFG_DELAY_SHOW_STEP = 0x0040; /*
                                                             * Do not show steps
                                                             * at beginning
                                                             * until walking
                                                             * state ensured
                                                             */
    public static final int HAR_CFG_TRI_THRES = 0x0080; /*
                                                         * Use triple threshold
                                                         * based on holding mode
                                                         * detection
                                                         */

    public static final int HAR_CFG_CALC_PEDOMETER = 0x0100; /*
                                                             * calculate
                                                             * pedometer
                                                             */
    public static final int HAR_CFG_CALC_GESTURE = 0x0200; /* calculate gesture */

    public static final int HAR_CFG_SET_ALL = 0xFFFF;
    public static final int HAR_ENGINE_MAX_SIZE = 4096;
    public static final int HAR_USER_NAME_LEN = 32;

    public static final int HAR_GENDER_FEMALE = 0;
    public static final int HAR_GENDER_MALE = 1;

    public static class HarConfig {
        /* User personal information. */
        public String szUserName;
        public int nUserGender;
        public int nUserHeight;
        public int nUserWeight;

        public int nFlags; /* Engine configuration, combination of EHarFlag */
        public int nReserved; /* not used now */

    }

    public static class HarResult {
        public int nPrevMotion;
        public int nCurMotion;
        public int nTotalSteps;
        public int nResuAccel; /* resultant acceleration, energy^0.5 */
        public int nPulseVal; /* (is pulse top) ? (the pulse height) : (0) */

        public long nEnergy;
        public int nDistance;
        public int nVelocity;
        public int nJumpHeight;
        public int preciseDist;
    }
}
