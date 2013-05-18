package com.colorcloud.movementsensor.walksensor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;


public class StepCounter implements SensorEventListener {
    float accelAvgX;
    float accelAvgY;
    float accelAvgZ;
    float lastTimestamp;
    static final int AVERAGE_WINDOW = 2;
    
    static final int STATE_NONE = 0;
    static final int STATE_UP = 1;
    static final int STATE_DOWN = -1;
    int state;
    int halfSteps;
    
    StepCountListener mListener;
    TestMain tester;
    
    
    String stepAcclFileName=null;
    String stepComprehensiveFileName=null;
    
//BEGIN of Added SRC

    /*==========================================================================*/
    /* Quantization scales */
    static final int FILTER_Q                =10;
    static final int HISTORY_Q               =6;
    static final int INPUT_ZERO_BASE         =32768;            // in Q16, the 0 G value of input data
    static final int STANDARD_GRAVITY         =26*2;            // in Q8, the gravity unit to calculate the thresholds below.
    static final int STANDARD_FPS            =100;            // the FPS(frame per second) for the thresholds below.
    static final int ONE_IN_Q8            =256;
    static final int HAR_MIN_FPS             =5;    // (20)
    static final int HAR_MAX_FPS             =100;

    /*==========================================================================*/
    /* all thresholds are made for 8Bit input */
    static final int THRES_STABLE_FACT        =443;        // (1.732 * 256)

    static final int THRES_STABLE_V        =631;      // 0.154G in Q12
    static final int THRES_ENTER_LOSEW_V    =471;        // 0.115G in Q12
    static final int THRES_LEAVE_LOSEW_V    =1577;        // 0.385G in Q12, leave weightlessness status

    static final int THRES_DYN_PULSE_ADD_V    =888;        // 0.054G in Q14 (Q14=Q8<<HISTORY_Q)
    static final int THRES_DYN_PULSE_ADD_LOW_V =492;    // 0.030G in Q14 
    static final int THRES_DYN_PULSE_ADAPT   =888;
    static final int THRES_PULSE_HIGH_CONF_V =6554;     // 0.100G in Q16
    static final int THRES_RAW_PULSE_HIGH_V  =205;      // 0.8G in Q8

    static final int THRES_RESU_ACCEL_FIX_V  =THRES_DYN_PULSE_ADD_V * 2;

    static final int THRES_LW_RUN_JUMP_N        =19;
    static final int THRES_MAX_RUN_INTE_N    =100;        // max interval between two steps
    static final int THRES_MIN_JUMP_INTE_N    =30;        // min interval between two jumps
    static final int THRES_MAX_STEP_INTE_N    =150;        // no less than 0.6 step/s, for walk detect
    static final int THRES_MIN_STEP_INTE_N    =20;        // no more than 5 steps/s, 100/5=20
    static final int THRES_WALK_TO_STILL_N   =240;      // 2.4s

    static final int THRES_STEP_STABLE_FACT1 =128;      // 1/2 in Q8
    static final int THRES_STEP_STABLE_FACT2 =64;      // 1/4 in Q8

    static final int LEN_PEAK_INTERVAL_N     =60;
    static final int LEN_STABLE_CHECK_N      =Recognizer.STABLE_CHECK_LEN;
    static final int LEN_PULSE_DETECT_N      =64;            // 100Hz FPS, 1Hz step, half period=50
    // no more than PULSE_DETECT_LEN !
    static final int LEN_SEARCH_ACCEL_N      =12;            // to find a pulse in raw data
    // means 0.25step (1step/s), or 1step(4steps/s)

    /*==========================================================================*/
    /* filtering definition ( 41 point gauss filter, then 8 point mean filter ) */

    static final int[] FLT_GAUSS_WIN  = new int[]{  
        -107,   -61,    -8,    51,   117,   187,  
        261,   338,   416,   494,   572,   647,  
        718,   784,   844,   897,   942,   977,  
        1003,  1019,  1024,  1019,  1003,   977,  
        942,   897,   844,   784,   718,   647,  
        572,   494,   416,   338,   261,   187,  
        117,    51,    -8,   -61,  -107};

    
    
    public StepCounter(StepCountListener listener) {
        mListener = listener;
        
        reset();
        
        //Added src to instantiate TestMain and initialize Engine
        tester = new TestMain();
        
        tester.g_hareng = new Engine();
        tester.g_harcfg = new Engine.HarConfig();


        tester.g_harcfg.szUserName = "user";
        tester.g_harcfg.nUserGender = Engine.HAR_GENDER_MALE;
        tester.g_harcfg.nUserHeight = 68;
        tester.g_harcfg.nUserWeight = 150;
        tester.g_harcfg.nFlags |= Engine.HAR_CFG_CHK_STEP_STABLE;
        tester.g_harcfg.nFlags |= Engine.HAR_CFG_CHK_RAW_PULSE;
        tester.g_harcfg.nFlags |= Engine.HAR_CFG_DELAY_SHOW_STEP;
        tester.g_harcfg.nFlags |= Engine.HAR_CFG_CALC_PEDOMETER;
        tester.g_harcfg.nFlags |= Engine.HAR_CFG_CALC_ENERGY;
        
        tester.g_thres = 180;
        tester.g_predef_fps = 200;
        tester.g_samp_range = 10;
        tester.g_bit_1g = 10;

        tester.recognizer = new Recognizer(); 
        tester.InitHarEngine();

        int[][] data = tester.jump_data;
        
    }
    
    @Deprecated
    public void manageFileName(String flname){
    	
    	stepAcclFileName = flname.replaceFirst("sensor", "AcclForSteps");
    	stepComprehensiveFileName = flname.replaceFirst("sensor", "Comprehensive");
    	File logFile = new File(stepAcclFileName);
    	File logCompFile = new File(stepComprehensiveFileName);
        try {
        	//Log.e("StepCounter", "Trying to open file: " + stepAcclFileName);
        	tester.recognizer.mLogWriter = new FileOutputStream(logFile);
        	tester.recognizer.mCompLogWriter = new FileOutputStream(logCompFile);
        	tester.recognizer.mLogReader = new BufferedReader(new FileReader(flname));
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
    }
    @Deprecated
    public String closeFileStream(){
    	try {
    		tester.recognizer.mLogWriter.close();
    		tester.recognizer.mLogReader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "";			
		}
		
		return stepAcclFileName;
    }

    /************************************************************************/
    /* MotionVar related functions                                         */
    /************************************************************************/

    public static void MotionClear(MotionVar  pVar)
    { 
        pVar.type = pVar.prev_type = HAR_MOTION_STILL; 
        pVar.stat_count = pVar.type_count = pVar.total_count = 0; 
        pVar.updated = false; 
    }
    void MotionClearFlag(MotionVar pVar)
    { 
        pVar.updated = false; pVar.prev_type = pVar.type; 
    }
    void MotionClearCount(MotionVar pVar)
    { 
        pVar.stat_count = pVar.type_count = pVar.total_count = 0; 
        pVar.updated = false; 
    }
    void MotionUpdate(MotionVar pVar, int newType, int newCount)
    { 
        pVar.stat_count = newCount; 
        // clear type counter if type changed
        if (pVar.type != newType){ 
            pVar.type_count = 0; pVar.type = newType; 
            // LOCAL_PRINT("enter stat <%s>\n", GetMotionStatName(pVar.type)); 
        }
        // increase type counter anyway
        pVar.type_count ++ ;
        // notify outside the type data is changed.
        pVar.updated = true; 
    }
    void MotionCountOneStep(MotionVar pVar)
    { 
        pVar.total_count ++; pVar.updated = true; 
    }
    String GetMotionStatName(int type)
    {
        String ret = "Unknown";
        switch(type)
        {
        case HAR_MOTION_STILL:    ret = "STILL";    break;
        case HAR_MOTION_WALK:    ret = "WALK";    break;
        case HAR_MOTION_RUN:    ret = "RUN";    break;
        case HAR_MOTION_JUMP:    ret = "JUMP";    break;
        }
        return ret;
    }


    /************************************************************************/
    /* local functions implementation                                       */
    /************************************************************************/
    short  Ensureshort(int nVal)
    {
        return (short)(nVal < 0?0:(nVal > Short.MAX_VALUE?Short.MAX_VALUE:nVal));
    }

    public static void BufShift32Bit(int[] pBuf, int nLen)
    {
        for (--nLen; nLen>0; nLen--) 
        {
            pBuf[nLen] = pBuf[nLen-1];
        }
    }

    public static int Sqrt_int(int x)
    {
        return (int)Math.sqrt((double)x);
    }

    public static void ClearAll(HarCntStep pGlb)
    {
        pGlb = new HarCntStep();
        MotionClear(pGlb.m_Motion);
    }

    public static int HAR_SFT_INT(int v,int s){
        return (((v) + (1<<((s)-1))) >> (s));
    }
    public static int  HAR_DIV_INT(int x, int n){
        return (((x) + ((n)>>1)) / (n));
    }

    public static int  HAR_SCALE_VAL(int v, int f, int n){
        return ((v)=HAR_SFT_INT((v)*(f), n));
    }

    
    void InitThres(HarCntStep pGlb)
    {
        int    iFact, i;

        // Get the default thresholds
        pGlb.m_iThresStable = HAR_SFT_INT(THRES_STABLE_V * pGlb.m_iGUnit, 12); // Q16
        pGlb.m_iThresEnterLW = HAR_SFT_INT(THRES_ENTER_LOSEW_V * pGlb.m_iGUnit, 12); // Q16
        pGlb.m_iThresLeaveLW = HAR_SFT_INT(THRES_LEAVE_LOSEW_V * pGlb.m_iGUnit, 12); // Q16    
        pGlb.m_iThresDynPulseAdd = HAR_SFT_INT(THRES_DYN_PULSE_ADD_V * pGlb.m_iGUnit, 8); // Q14+Q16-Q8
        pGlb.m_iThresDynPulseAddLow = HAR_SFT_INT(THRES_DYN_PULSE_ADD_LOW_V * pGlb.m_iGUnit, 8); // Q14+Q16-Q8
        pGlb.m_iThresDynPulseAdpt = HAR_SFT_INT(THRES_DYN_PULSE_ADAPT * pGlb.m_iGUnit, 8); // Q14+Q16-Q8;
        pGlb.m_iThresResuAccelFix = HAR_SFT_INT(THRES_RESU_ACCEL_FIX_V * pGlb.m_iGUnit, 8+HISTORY_Q); // Q14+Q16-Q14
        pGlb.m_tSDS.pulse_high = HAR_SFT_INT((THRES_PULSE_HIGH_CONF_V * pGlb.m_iGUnit), 16);

        //  for N variables, keep it (100Hz)
        pGlb.m_iThresRunJump = THRES_LW_RUN_JUMP_N;
        pGlb.m_iThresMaxRunInte = THRES_MAX_RUN_INTE_N;
        pGlb.m_iThresMinJumpInte = THRES_MIN_JUMP_INTE_N;
        pGlb.m_iThresWalkToStill = THRES_MAX_STEP_INTE_N;
        pGlb.m_iThresMaxStepInte = THRES_MAX_STEP_INTE_N;
        pGlb.m_iThresMinStepInte = THRES_MIN_STEP_INTE_N;

        pGlb.m_iLenPeakInterval = LEN_PEAK_INTERVAL_N;
        pGlb.m_iLenPulseDetect = LEN_PULSE_DETECT_N;
        pGlb.m_iLenStableCheck = LEN_STABLE_CHECK_N;
        pGlb.m_iLenSearchAccel = LEN_SEARCH_ACCEL_N;

        // adjust for the new FPS
        iFact = HAR_DIV_INT((pGlb.m_iFPS << 8), STANDARD_FPS);
        if (iFact != ONE_IN_Q8)
        {
            // modify all N variables
            HAR_SCALE_VAL(pGlb.m_iThresRunJump, iFact, 8);
            HAR_SCALE_VAL(pGlb.m_iThresMaxRunInte, iFact, 8);
            HAR_SCALE_VAL(pGlb.m_iThresMinJumpInte, iFact, 8);
            HAR_SCALE_VAL(pGlb.m_iThresWalkToStill, iFact, 8);
            HAR_SCALE_VAL(pGlb.m_iThresMaxStepInte, iFact, 8);
            HAR_SCALE_VAL(pGlb.m_iThresMinStepInte, iFact, 8);

            HAR_SCALE_VAL(pGlb.m_iLenPeakInterval, iFact, 8);
            HAR_SCALE_VAL(pGlb.m_iLenPulseDetect, iFact, 8);
            HAR_SCALE_VAL(pGlb.m_iLenStableCheck, iFact, 8);
            HAR_SCALE_VAL(pGlb.m_iLenSearchAccel, iFact, 8);
            if (pGlb.m_iLenSearchAccel < 1) pGlb.m_iLenSearchAccel = 1;
        }

        // Init the m_iAccelHist, to ensure the begin is stable
        for (i=0; i<pGlb.m_iLenPulseDetect; i++)
        {
            pGlb.m_iAccelHist[i] = Integer.MAX_VALUE;
        }

    }

    int ResampleFltr(int[] pOut, int[] pIn, int nIn, int iFact)
    {
        int iHalfWin, iOrgHalfWin, iWin, i, iTemp, iPos1, iPos2;

        // get win length
        iOrgHalfWin = (nIn >> 1);
        iHalfWin = HAR_SFT_INT(iOrgHalfWin * iFact, 16);
        if (iHalfWin <=0 ) iHalfWin = 1;

        iWin = (iHalfWin << 1);         // symmetric
        if ( (nIn & 0x01) == 0x01)
        {
            iWin += 1;
            pOut[iHalfWin] = pIn[iOrgHalfWin];   // set mid-val
        }


        // re-sample the filter win, regular
        iFact = HAR_DIV_INT((iOrgHalfWin << 16), iHalfWin);  // N/newN, in Q8
        for (i=0; i<iHalfWin; i++)
        {
            iTemp = (iHalfWin - i) * iFact;
            iTemp = (iOrgHalfWin << 16) - iTemp;
            if (iTemp < 0) iTemp = 0;       // shouldn't happen if nFPS <= MAX_FPS
            iPos1 = (iTemp >> 16);
            iPos2 = ((iTemp + (1<<16) - 1) >> 16);
            iTemp = iTemp - (iPos1 << 16);   // fact

            pOut[i] = pIn[iPos1] + HAR_SFT_INT(((pIn[iPos2] - pIn[iPos1]) * iTemp), 16);
            pOut[iWin - i - 1] = pOut[i];
        }
        /*
    // re-sample the filter win, (lower sum)
    iFact = HAR_DIV_INT(((iOrgHalfWin + 1) << 8), (iHalfWin + 1));  // (N+1)/(newN+1), in Q8    
    for (i=0; i<iHalfWin; i++)
    {
        iTemp = (i + 1) * iFact;
        iPos1 = (iTemp >> 8);
        iPos2 = ((iTemp + 255) >> 8);
        iTemp = iTemp - (iPos1 << 8); // fact
        if (--iPos1 < 0) iPos1 = 0;   // shouldn't happen if nFPS <= MAX_FPS
        if (--iPos2 < 0) iPos2 = 0;   // shouldn't happen if nFPS <= MAX_FPS

        pOut[i] = pIn[iPos1] + HAR_SFT_INT(((pIn[iPos2] - pIn[iPos1]) * iTemp), 8);
        pOut[iWin - i - 1] = pOut[i];
    }
         */

        return iWin;
    }

    public static void C_MEMCPY(int[] to, int[] from, int len){
        for(int i=0;i<len;i++){
            to[i]=from[i];
        }
    }
    
    
    public static void InitFltWin(HarCntStep pGlb)
    {
        int[] GaussWin = FLT_GAUSS_WIN;
        int iFact, iSum, i;

        pGlb.m_iGaussWinLen = FILTER_GAUSS_LEN;
        pGlb.m_iMeanWinLen = FILTER_MEAN_LEN;
        C_MEMCPY(pGlb.m_iGaussWin, GaussWin, pGlb.m_iGaussWinLen);

        // adjust for different FPS, re-sample the filter
        if (pGlb.m_iFPS != HAR_MAX_FPS)
        {
            iFact = HAR_DIV_INT((pGlb.m_iFPS << 16), HAR_MAX_FPS);      // Q16
            pGlb.m_iGaussWinLen = 
                    Recognizer.ResampleFltr(pGlb.m_iGaussWin, GaussWin, pGlb.m_iGaussWinLen, iFact);
            pGlb.m_iMeanWinLen = HAR_SFT_INT((pGlb.m_iMeanWinLen * iFact), 16);
            if (pGlb.m_iMeanWinLen < 1) pGlb.m_iMeanWinLen = 1;    // must >= 1
        }

        // normalize, reuse var pos1, pos2
        iSum = 0;
        for (i=0; i<pGlb.m_iGaussWinLen; i++)  iSum += pGlb.m_iGaussWin[i];
        iFact = HAR_DIV_INT(((1 << FILTER_Q) << 16), iSum);
        for (i=0; i<pGlb.m_iGaussWinLen; i++)  
        {
            pGlb.m_iGaussWin[i] = HAR_SFT_INT((pGlb.m_iGaussWin[i] * iFact), 16);
        }

        // Calculate look-back len based on filter win len
        pGlb.m_iAbsResuAccelHistLen = 1 + (pGlb.m_iGaussWinLen >> 1) + (pGlb.m_iMeanWinLen >> 1);
        pGlb.m_iAbsResuAccelHistLen += pGlb.m_iLenSearchAccel;
        if (pGlb.m_iAbsResuAccelHistLen > PULSE_ENSURE_LEN)
        {
            //LOCAL_PRINT("Critical error! m_iAbsResuAccelHistLen(%d) > %d!!!\n", 
            //        pGlb.m_iAbsResuAccelHistLen, PULSE_ENSURE_LEN);
        }

        //if (pGlb.m_iFPS != HAR_MAX_FPS)
        //LOCAL_PRINT("FPS=%d, WinLen=%d,%d, GaussSum=%.2f, add=%.1f, len3=%d\n", \
        //        pGlb.m_iFPS, pGlb.m_iGaussWinLen, pGlb.m_iMeanWinLen, iSum/1024.f, \
        //        pGlb.m_iThresDynPulseAdd/64.0, pGlb.m_iAbsResuAccelHistLen);
    }

    static void SaveStepInterval(HarCntStep  pGlb)
    {
        int     iSum, iMin, iMax, i;

        if (pGlb.m_iCountAfterLastPulse == 0 
                || pGlb.m_iCountAfterLastPulse > pGlb.m_iFPS * 2)
        {
            // no step in long time, so re-init the history
            for (i=0; i<STEP_INTER_HIST_LEN; i++)
            {
                pGlb.m_iStepInteHist[i] = 0;
            }
        }

        // save to history buf
        BufShift32Bit(pGlb.m_iStepInteHist, STEP_INTER_HIST_LEN);
        pGlb.m_iStepInteHist[0] = (int)pGlb.m_iCountAfterLastPulse;

        // re-calc mean & diff
        iMin = iMax = pGlb.m_iCountAfterLastPulse;
        iSum = 0;
        for (i=0; i<STEP_INTER_HIST_LEN; i++)
        {
            // maybe the buf is not full
            if (pGlb.m_iStepInteHist[i] <= 0) break;
            // get sum, min, max
            iSum += pGlb.m_iStepInteHist[i];
            if (pGlb.m_iStepInteHist[i] < iMin) iMin = pGlb.m_iStepInteHist[i];
            if (pGlb.m_iStepInteHist[i] > iMax) iMax = pGlb.m_iStepInteHist[i];
        }
        if (i <= 1)
        {
            // only the 1st element
            pGlb.m_iStepInteMean = 0; 
            pGlb.m_iStepInteDiff = 0;
            pGlb.m_iStepInteThres= 0;
            return;
        }
        else if (i == 2)
        {
            // the 1st step interval is not right! replace it with the 2nd
            pGlb.m_iStepInteHist[1] = pGlb.m_iStepInteHist[0];
            // re-calc the sum & max, min
            iSum = (pGlb.m_iStepInteHist[0] << 1);
            iMax = iMin = pGlb.m_iStepInteHist[0];
        }

        pGlb.m_iStepInteMean = HAR_DIV_INT((iSum << 8), i);    // Q8
        pGlb.m_iStepInteDiff = HAR_DIV_INT(((iMax - iMin) << 16), pGlb.m_iStepInteMean);  // Q8
        pGlb.m_iStepInteThres = HAR_SFT_INT((pGlb.m_iStepInteMean * THRES_STEP_STABLE_FACT2), 8); // Q8

        return;
    }

    boolean IsStepInteStable(HarCntStep  pGlb)
    {
        int        iVal = 0;
        boolean    bRet = false;

        // if not set, always return false
        if ( (pGlb.m_iFlags & Engine.HAR_CFG_CHK_STEP_STABLE) != Engine.HAR_CFG_CHK_STEP_STABLE) return false;

        if (pGlb.m_iStepInteMean <= 0) return false;

        if (pGlb.m_iStepInteDiff >= THRES_STEP_STABLE_FACT1) return false;

        // (v - mean) / mean
        iVal = (pGlb.m_iCountAfterLastPulse << 8) - pGlb.m_iStepInteMean;
        if (iVal < 0) iVal = -iVal;
        if (iVal < pGlb.m_iStepInteThres)
        {
            bRet = true;
        }
        else
        {
            // (v - 2*mean) / mean, to handle "lost one step".
            iVal = (pGlb.m_iCountAfterLastPulse << 8) - (pGlb.m_iStepInteMean << 1);
            if (iVal < 0) iVal = -iVal;
            if (iVal < pGlb.m_iStepInteThres)
            {
                bRet = true;
            }
        }

        return bRet;
    }

    public boolean CountOneStep(HarCntStep  pGlb)
    {
        StepDelayShowVar   pSDS = pGlb.m_tSDS;
        int     nF1, nF2, i;
        boolean    bCounted = true;

        // if not set, just count this step and return
        if ((pGlb.m_iFlags & Engine.HAR_CFG_DELAY_SHOW_STEP) != Engine.HAR_CFG_DELAY_SHOW_STEP)
        {
            MotionCountOneStep(pGlb.m_Motion);
            return bCounted;
        }

        // re-init delay show step var if no step appears in long time
        if (pGlb.m_iCountAfterLastPulse > pGlb.m_iFPS * 2)
        {
            if (pSDS.temp_counter != 0)
            {
                //LOCAL_PRINT("SKIP %d steps at beginning.\n", pSDS.temp_counter);
            }

            pSDS.is_walking = 0;
            pSDS.temp_counter = 0;
            pSDS.score = (short)((pGlb.m_iCountAfterLastPulse < pGlb.m_iFPS * 6) ? 20 : 0);
        }

        // not walking now
        if (pSDS.is_walking == 0)
        {
            // save step in temp counter
            pSDS.temp_counter ++ ;

            // get score for current step
            nF1 = (pGlb.m_iPulseVal > pGlb.m_tSDS.pulse_high) ? (20) : (5);
            pSDS.score += nF1;
            if (pSDS.temp_counter > 2)
            {
                nF2 = (pGlb.m_iStepInteDiff <= THRES_STEP_STABLE_FACT2) ? (30) : (0);
                pSDS.score += nF2;
            }

            if (pSDS.score < 100)
            {
                // not walking yet, wait for next step.
                bCounted = false;
            }
            else
            {
                // is walking now!
                pSDS.is_walking = 1;

                for (i=0; i<pSDS.temp_counter; i++)
                {
                    MotionCountOneStep(pGlb.m_Motion);
                }
                pSDS.temp_counter = 0;
            }
        }
        else
        {
            MotionCountOneStep(pGlb.m_Motion);
        }

        return bCounted;
    }

    boolean FeedData(HarCntStep pGlb, int x, int y, int z)
    {
        if (pGlb.m_bInitOK == false) return false;

        // Clear the updated flag
        MotionClearFlag(pGlb.m_Motion);

        // increase the frame counter
        pGlb.m_iTotalFrm ++ ;

        // store the input, must be in Q16
        pGlb.m_iX = (int)(x & 0xFFFF) - INPUT_ZERO_BASE;
        pGlb.m_iY = (int)(y & 0xFFFF) - INPUT_ZERO_BASE;
        pGlb.m_iZ = (int)(z & 0xFFFF) - INPUT_ZERO_BASE;

        // get resultant force
        pGlb.m_iEnergy = pGlb.m_iX * pGlb.m_iX + pGlb.m_iY * pGlb.m_iY + pGlb.m_iZ * pGlb.m_iZ;
        pGlb.m_iResuAccel = Sqrt_int(pGlb.m_iEnergy);
        pGlb.m_iAbsResuAccel = (int)(pGlb.m_iResuAccel - pGlb.m_iGUnit);
        if (pGlb.m_iAbsResuAccel < Short.MIN_VALUE) pGlb.m_iAbsResuAccel = Short.MIN_VALUE;
        if (pGlb.m_iAbsResuAccel > Short.MAX_VALUE) pGlb.m_iAbsResuAccel = Short.MAX_VALUE;

        // update the current position (angle) if possible
        if (IsDeviceStable(pGlb))
        {
            // LOCAL_PRINT("re-calc position\n");
            CalcDevicePos(pGlb);
        }

        // get the acceleration in gravity direction
        pGlb.m_iAccelInGrav = pGlb.m_iX * pGlb.m_iAngleX 
        + pGlb.m_iY * pGlb.m_iAngleY 
        + pGlb.m_iZ * pGlb.m_iAngleZ;// dot product
        pGlb.m_iAccelInGrav = pGlb.m_iResuAccel;

        // prepare the absolute acceleration in gravity direction
        pGlb.m_iAccelHist[pGlb.m_iAccelHistPos] = SmoothAccel(pGlb, pGlb.m_iAbsResuAccel);
        pGlb.m_iFltrdAccel = (int)pGlb.m_iAccelHist[pGlb.m_iAccelHistPos];
        if (++pGlb.m_iAccelHistPos >= pGlb.m_iLenPulseDetect) pGlb.m_iAccelHistPos = 0;

        // Check if is STILL, update the flags for 1st run/jump/walk
        //  Note! even if the STILL is true, the motion type may be changed below!
        // CheckStill(pGlb); // removed for Hong, by Alex, 2008-11-06

        // Check if the function is under the weightlessness status.
        //  when running  jumping, it must be some time that gravity absent
        CheckWeightless(pGlb);

        // Check wave top (pulse) to count the steps.
        pGlb.m_iPulseVal = CheckPulse(pGlb);
        if (pGlb.m_iPulseVal != 0 && pGlb.m_bEnterLoseWeight == false)
        {
            //    CheckWalk(pGlb);    // removed for Hong, by Alex, 2008-11-06
        }

        // update the total steps, total counter only increase in RUN or WALK
        //    if (pGlb.m_iPulseVal != 0 && (pGlb.m_Motion.type == MOTION_RUN || pGlb.m_Motion.type == MOTION_WALK))
        if (pGlb.m_iPulseVal != 0)
        {
            if (pGlb.m_iCountAfterLastPulse < pGlb.m_iThresMinStepInte)
            {
                // too close to previous step. remove it!
                //LOCAL_PRINT("pulse RMV! step too close (interval=%d) frm=%d!\n", \
                //        pGlb.m_iCountAfterLastPulse, pGlb.m_iTotalFrm);

                if ((pGlb.m_iFlags & Engine.HAR_CFG_DBG_CHG_FLTRD)==Engine.HAR_CFG_DBG_CHG_FLTRD)
                {
                    pGlb.m_iFltrdAccel -= (pGlb.m_iGUnit << (HISTORY_Q - 1));
                }

                // count current frame as other non-pulse frame.
                pGlb.m_iCountAfterLastPulse ++ ;            
            }
            else
            {
                // Save the interval
                SaveStepInterval(pGlb);

                // count the step, maybe save in temp counter at the beginning.
                //  will use pGlb.m_iCountAfterLastPulse !
                CountOneStep(pGlb);

                // clear the counter
                pGlb.m_iCountAfterLastPulse = 0;
            }
        }
        else
        {
            if (pGlb.m_iCountAfterLastPulse < Short.MAX_VALUE) pGlb.m_iCountAfterLastPulse ++ ;
        }

        // update result
        pGlb.m_Result.nCurMotion = pGlb.m_Motion.type;
        pGlb.m_Result.nPrevMotion= pGlb.m_Motion.prev_type;
        pGlb.m_Result.nTotalSteps= pGlb.m_Motion.total_count;
        pGlb.m_Result.nResuAccel = (short)(pGlb.m_iResuAccel + Short.MAX_VALUE);
        pGlb.m_Result.nPulseVal  = pGlb.m_iPulseVal;

        return pGlb.m_Motion.updated;
    }

    boolean IsDeviceStable(HarCntStep pGlb)
    {
        int        i, j, max, min;
        int        iThres;

        // save the history data for smooth
        pGlb.m_iHist[0][pGlb.m_iHistPos] = pGlb.m_iX;
        pGlb.m_iHist[1][pGlb.m_iHistPos] = pGlb.m_iY;
        pGlb.m_iHist[2][pGlb.m_iHistPos] = pGlb.m_iZ;
        if (++pGlb.m_iHistPos >= pGlb.m_iLenStableCheck) pGlb.m_iHistPos = 0;

        // check if current resultant acceleration is close to gravity
        iThres = (int)(HAR_SFT_INT((pGlb.m_iThresStable * THRES_STABLE_FACT), 8));
        if (pGlb.m_iAbsResuAccel > iThres || pGlb.m_iAbsResuAccel < -iThres)
            return false;

        // check if the history are stable enough
        for (i=0; i<3; i++)
        {
            max=Integer.MIN_VALUE;    min=Integer.MAX_VALUE;
            for (j=0; j<(int)pGlb.m_iLenStableCheck; j++)
            {
                if (pGlb.m_iHist[i][j] < min) min = pGlb.m_iHist[i][j];
                if (pGlb.m_iHist[i][j] > max) max = pGlb.m_iHist[i][j];
            }
            // if the variance is too large, return unstable
            if (max - min > (int)pGlb.m_iThresStable)
                return false;
        }

        return true;
    }


    void CalcDevicePos(HarCntStep pGlb)
    {
        int     iFact;

        iFact = (1 << 16) / pGlb.m_iResuAccel;     // Q16
        pGlb.m_iAngleX = HAR_SFT_INT(pGlb.m_iX * iFact, 8);// Q8
        pGlb.m_iAngleY = HAR_SFT_INT(pGlb.m_iY * iFact, 8);// Q8
        pGlb.m_iAngleZ = HAR_SFT_INT(pGlb.m_iZ * iFact, 8);// Q8
    }

    int SmoothAccel(HarCntStep pGlb, int iAccel)
    {
        int        i, sum = 0;

        // Store iAccel first, for ensure pulse in CheckPulse()
        BufShift32Bit(pGlb.m_iAbsResuAccelHist, pGlb.m_iAbsResuAccelHistLen);
        pGlb.m_iAbsResuAccelHist[0] = iAccel;

        // Shift Gaussian buffer
        BufShift32Bit(pGlb.m_iGaussBuf, pGlb.m_iGaussWinLen);
        pGlb.m_iGaussBuf[0] = iAccel;

        // Do Gaussian filtering
        for (i=0; i<pGlb.m_iGaussWinLen; i++)
            sum += pGlb.m_iGaussBuf[i] * pGlb.m_iGaussWin[i];
        if (sum < 0) sum = 0;
        // shift back from Q10
        sum = HAR_SFT_INT(sum, (FILTER_Q - HISTORY_Q));

        // Shift mean buffer
        BufShift32Bit(pGlb.m_iMeanBuf, pGlb.m_iMeanWinLen);
        pGlb.m_iMeanBuf[0] = sum;

        // Do mean filtering
        sum = 0;
        for (i=0; i<pGlb.m_iMeanWinLen; i++)
            sum += pGlb.m_iMeanBuf[i];
        sum /= pGlb.m_iMeanWinLen;

        // pGlb.m_iMeanBuf[0] = sum;

        // remove the negative half!!!
        if (sum < 0) sum = 0;

        return sum;
    }

    boolean CheckWeightless(HarCntStep pGlb)
    {
        int     iAccel = pGlb.m_iAccelInGrav;
        if (iAccel < 0) iAccel = -iAccel;

        if ( ! pGlb.m_bEnterLoseWeight )
        {
            // not in weightlessness status
            if ((int)iAccel < pGlb.m_iThresEnterLW)    
            {
                // enter the weightlessness status
                pGlb.m_bEnterLoseWeight = true;
                pGlb.m_iLostWeightCount = 0;
            }
            else ;
        }
        else
        {
            // in weightlessness status now
            pGlb.m_iLostWeightCount++;
            pGlb.m_bFirstWalk = false;
            if ((int)iAccel > pGlb.m_iThresLeaveLW)
            {
                // leave weightlessness status
                pGlb.m_bEnterLoseWeight = false;
                pGlb.m_Motion.updated = true;
                if (pGlb.m_iLostWeightCount > pGlb.m_iThresRunJump)
                {
                    // is jump
                    MotionUpdate(pGlb.m_Motion, HAR_MOTION_JUMP, pGlb.m_iLostWeightCount);
                }
                else
                {
                    // is run
                    if ( ! pGlb.m_bFirstRun )
                    {
                        // to anti-noise, not enter RUN in 1st sample
                        pGlb.m_bFirstRun = true;
                        pGlb.m_iCountTwoRuns += pGlb.m_iCountAfterLastRun;
                    }
                    else
                    {
                        pGlb.m_iCountTwoRuns += pGlb.m_iCountAfterLastRun;
                        MotionUpdate(pGlb.m_Motion, HAR_MOTION_RUN, pGlb.m_iCountTwoRuns + pGlb.m_iLostWeightCount*2);
                        pGlb.m_iCountTwoRuns = 0;
                        pGlb.m_bFirstRun = false;
                    }
                    pGlb.m_iCountAfterLastRun = 0;
                }
            }
            else ;
        }

        return ((pGlb.m_Motion.type != HAR_MOTION_RUN) && (pGlb.m_Motion.type != HAR_MOTION_JUMP));
    }

    boolean EnsurePulse(HarCntStep pGlb, int nPulse, int nDeltaPulse)
    {
        int nOffset, i, iMax = Integer.MAX_VALUE, iMin = Integer.MIN_VALUE, iThres, iSum, iMean = 0;
        boolean iRet = false;

        // Get corresponding raw pulse value
        nOffset = pGlb.m_iAbsResuAccelHistLen - 1 - (pGlb.m_iLenSearchAccel << 1);
        if (nOffset < 0) nOffset = 0;

        for (i=nOffset; i<pGlb.m_iAbsResuAccelHistLen; i++)
        {
            if (pGlb.m_iAbsResuAccelHist[i] > iMax) iMax = pGlb.m_iAbsResuAccelHist[i];
            if (pGlb.m_iAbsResuAccelHist[i] < iMin) iMin = pGlb.m_iAbsResuAccelHist[i];
        }

        // perform NO_WAVE check
        if ((pGlb.m_iFlags & Engine.HAR_CFG_CHK_RAW_PULSE)==Engine.HAR_CFG_CHK_RAW_PULSE)
        {
            iThres = (iMax >> 1);
            if (iThres > (int)(pGlb.m_iThresResuAccelFix >> 1)) iThres = pGlb.m_iThresResuAccelFix >> 1;
        iRet = ((iMax >= (int)pGlb.m_iThresResuAccelFix)
                && (iMax - iMin) > iThres);
        if (iRet == false)
        {
            //LOCAL_PRINT("pulse RMV! no wave. pulse=%d,raw=%d-%d,raw_thres=%d,frm=%d\n", \
            //        nPulse, iMax, iMin, pGlb.m_iThresResuAccelFix, pGlb.m_iTotalFrm);
            if ((pGlb.m_iFlags & Engine.HAR_CFG_DBG_CHG_FLTRD) == Engine.HAR_CFG_DBG_CHG_FLTRD)
            {
                pGlb.m_iFltrdAccel -= (pGlb.m_iGUnit << HISTORY_Q);
            }
            // not real pulse! maybe caused by noise
            return iRet;
        }
        }

        // perform HEAVY MOVING check (pocket mode)
        if ((pGlb.m_iFlags & Engine.HAR_CFG_TRI_THRES) == Engine.HAR_CFG_TRI_THRES)
        {
            if (pGlb.m_iCountAfterLastPulse == 0 
                    || pGlb.m_iCountAfterLastPulse > pGlb.m_iFPS * 2)
            {
                // no step in long time, so re-init the history
                for (i=0; i<RAW_PLUSE_HIST_LEN; i++)
                {
                    pGlb.m_iRawPulseHist[i] = 0;
                }
            }

            // save to history buf
            BufShift32Bit(pGlb.m_iRawPulseHist, RAW_PLUSE_HIST_LEN);
            pGlb.m_iRawPulseHist[0] = (int)iMax;

            // re-calc mean
            iSum = 0;
            for (i=0; i<RAW_PLUSE_HIST_LEN; i++)
            {
                // maybe the buf is not full
                if (pGlb.m_iRawPulseHist[i] <= 0) break;
                // get sum, min, max
                iSum += pGlb.m_iRawPulseHist[i];
            }
            if (i > 1)
            {
                iMean = HAR_DIV_INT(iSum, i); 
            }
            iThres = HAR_SFT_INT(THRES_RAW_PULSE_HIGH_V * pGlb.m_iGUnit, 8);
            if (iMean > iThres)
            {
                // may pocket mode, use high threshold
                pGlb.m_iActIntensity = 2;
            }
            else if (iMean > (iThres >> 1))
            {
                pGlb.m_iActIntensity = 1;
            }
            else
            {
                pGlb.m_iActIntensity = 0;
            }

            if (pGlb.m_iActIntensity >= 2)
            {
                if (nDeltaPulse < (int)pGlb.m_iThresDynPulseAdpt)
                {
                    //LOCAL_PRINT("pulse RMV! deltaV=%d<%d,frm=%d\n", nDeltaPulse, 
                    //        pGlb.m_iThresDynPulseAdpt, 
                    //        pGlb.m_iTotalFrm);
                    if ((pGlb.m_iFlags & Engine.HAR_CFG_DBG_CHG_FLTRD) == Engine.HAR_CFG_DBG_CHG_FLTRD)
                    {
                        pGlb.m_iFltrdAccel -= (pGlb.m_iGUnit << (HISTORY_Q - 2));
                    }
                    return false;
                }
            }
        }    

        return true;
    }

    int CheckPulse(HarCntStep pGlb)
    {
        int        iMin = Integer.MAX_VALUE, iVal, iThreshold;

        int        i0, i1, i2;

        // Get dynamic threshold, concern the latest 256 samples
        for (i0=0; i0<pGlb.m_iLenPulseDetect; i0++)
        {
            if (pGlb.m_iAccelHist[i0] < iMin) iMin = pGlb.m_iAccelHist[i0];
        }

        // get index of the latest 3 samples
        i0 = pGlb.m_iAccelHistPos + pGlb.m_iLenPulseDetect - 1;
        while (i0 >= pGlb.m_iLenPulseDetect) i0 -= pGlb.m_iLenPulseDetect;
        i1 = pGlb.m_iAccelHistPos + pGlb.m_iLenPulseDetect - 2;
        while (i1 >= pGlb.m_iLenPulseDetect) i1 -= pGlb.m_iLenPulseDetect;
        i2 = pGlb.m_iAccelHistPos + pGlb.m_iLenPulseDetect - 3;
        while (i2 >= pGlb.m_iLenPulseDetect) i2 -= pGlb.m_iLenPulseDetect;

        // get threshold
        if (IsStepInteStable(pGlb) == true)
        {
            // high confidence, use low threshold
            iThreshold = iMin + pGlb.m_iThresDynPulseAddLow;
        }
        else
        {
            iThreshold = iMin + pGlb.m_iThresDynPulseAdd;
        }

        // be the top & larger than the dynamic threshold
        if ( (pGlb.m_iAccelHist[i1] > pGlb.m_iAccelHist[i0]) && 
                (pGlb.m_iAccelHist[i1] > pGlb.m_iAccelHist[i2]) &&
                (pGlb.m_iAccelHist[i1] > iThreshold)
        )
        {
            iVal = HAR_SFT_INT(pGlb.m_iAccelHist[i1], HISTORY_Q);
            //        LOCAL_PRINT("Pulse=%.2f, add=%.2f\n", pGlb.m_iAccelHist[i1]/64.f, pGlb.m_iThresDynPulseAdd/64.f);
            if (EnsurePulse(pGlb, iVal, pGlb.m_iAccelHist[i1] - iThreshold) == true)
            {
                // Just show debug info.
                if (pGlb.m_iAccelHist[i1] < iMin + pGlb.m_iThresDynPulseAdd)
                {
                    //LOCAL_PRINT("pulse BACK! stepC=(%d/%d), frm=%d\n", 
                    //        HAR_SFT_INT(pGlb.m_iStepInteMean, 8), 
                    //        pGlb.m_iCountAfterLastPulse, 
                    //        pGlb.m_iTotalFrm);

                    // to show result on real-time wave
                    if ((pGlb.m_iFlags & Engine.HAR_CFG_DBG_CHG_FLTRD)== Engine.HAR_CFG_DBG_CHG_FLTRD)
                    {
                        pGlb.m_iFltrdAccel = (int)((iVal + pGlb.m_iGUnit * 2) << HISTORY_Q);
                    }
                }

                // pulse found, step counted.
                return iVal;
            }
            else
            {
                return 0;
            }
        }

        return 0;
    }

    public HarCntStep HarCS_Init(HarCntStep pEng, int nBufSize, int nGUnit, int nFps)
    {

        if ( nGUnit == 0 || nFps < HAR_MIN_FPS || nFps > HAR_MAX_FPS) return null;

        ClearAll(pEng);

        pEng.m_iGUnit = nGUnit;
        pEng.m_iFPS = nFps;
        pEng.m_iPeakIntervalCount = 45;

        InitThres(pEng);

        InitFltWin(pEng);

        pEng.m_bInitOK = true;

        return pEng;
    }

    int HarCS_SetConfig(HarCntStep  pEng, int nFlags)
    {
        if (pEng == null) return 0;

        pEng.m_iFlags = nFlags;

        return 1;
    }

    int HarCS_GetConfig(HarCntStep  pEng)
    {
        if (pEng == null) return 0;

        return pEng.m_iFlags;
    }

    int HarCS_Reset(HarCntStep  pEng)
    {
        if (pEng == null) return 0;

        MotionClearCount(pEng.m_Motion);

        return 1;
    }

    int  HarCS_FeedData(HarCntStep  pEng, int x, int y, int z)
    {
        if (pEng == null) return 0;

        return FeedData(pEng, x, y, z) ? (1) : (0);
    }

    HarResultCS HarCS_GetResult(HarCntStep pEng)
    {
        if (pEng == null) return null;

        return (pEng.m_Result);
    }

    /*======================================================================*/
    /* debug functions                                                      */
    /*======================================================================*/

    int  HarCS_GetPulseAdd(HarCntStep  pEng)
    {
        int          iVal;

        if (pEng== null) return -1;

        iVal = pEng.m_iThresDynPulseAdd;       // Q16+6
        iVal = HAR_SFT_INT(iVal, HISTORY_Q);    // Q16
        iVal = HAR_DIV_INT((iVal * 1000), pEng.m_iGUnit);

        // iVal = HAR_DIV_INT((iVal * STANDARD_GRAVITY), pEng.m_iGUnit);

        return iVal;
    }

    int  HarCS_SetPulseAdd(HarCntStep pEng, int nPulseAdd)
    {
        int          iVal;

        if (pEng == null) return -1;
        if (nPulseAdd < 0) nPulseAdd = 0;

        // 
        iVal = HAR_DIV_INT((nPulseAdd * pEng.m_iGUnit), 1000);
        pEng.m_iThresDynPulseAdd = (iVal << HISTORY_Q);

        return iVal;
    }

    // required by Hong, 2008-09-28
    int  HarCS_SetGlobalThres(HarCntStep pEng, int nThres)
    {
        int nPrevThres = HarCS_GetPulseAdd(pEng);

        // if nThres < 0, just return the current setting
        if (nThres >= 0)
        {
            if (nThres > 1000) nThres = 1000;    // 1G 
            HarCS_SetPulseAdd(pEng, nThres);
        }

        return nPrevThres;
    }

    public static final int STABLE_CHECK_LEN    =(30);
    public static final int FILTER_GAUSS_LEN    =(41);
    public static final int FILTER_MEAN_LEN        =(8);
    public static final int PULSE_DETECT_LEN    =(128);
    public static final int PULSE_ENSURE_LEN    =(48);
    public static final int STEP_INTER_HIST_LEN =(4);
    public static final int RAW_PLUSE_HIST_LEN  =(4);

    /*=============================================================================*/
    public static class MotionVar{
        int        type;
        int        prev_type;        // reset at the beginning in each frame
        int        stat_count;
        int        type_count;
        int        total_count;
        boolean    updated;
    }

    public static class StepDelayShowVar{
        byte      is_walking;     /* is walking or not */
        byte      temp_counter;   /* save steps before walking, then move to main step counter */
        short     score;          /* determine when to switch to "walking" state */
        int     pulse_high;     /* Pulse is high enough to confide */
    }
    /*=============================================================================*/
    public static class HarCntStep{

        MotionVar m_Motion = new MotionVar();            /* Output, current motion status */

        HarResultCS m_Result = new HarResultCS();

        int     m_iFlags;               /* combination of EHarFlag */

        /* Basic information from the input */
        int     m_iTotalFrm;            /* total input frame, 4G/100Hz/3600/24h = 497 days */
        int        m_iX;                    /* nX - base */
        int        m_iY;                    /* nY - base */
        int        m_iZ;                    /* nZ - base */
        int        m_iEnergy;                /* x^2 + y^2 + z^2 */

        int     m_iResuAccel;
        int     m_iAccelInGrav;

        int        m_iAbsResuAccel;        /* short(m_fResuAccel - gravity) */
        int        m_iPulseVal;            /* (in pulse top) ? (the pulse height) : (0) */
        int        m_iFltrdAccel;            /* filtered m_iAbsResuAccel */

        int     m_iAngleX;              /* Q8 */
        int     m_iAngleY;              /* Q8 */
        int     m_iAngleZ;              /* Q8 */

        boolean    m_bEnterLoseWeight;
        boolean    m_bFirstWalk;
        boolean    m_bFirstRun ;
        boolean    m_bAfterJump;

        boolean    m_bInitOK;

        int        m_iCountTwoSteps;
        int        m_iCountTwoRuns;
        int        m_iCountAfterLastRun;
        int        m_iCountAfterJump;
        int     m_iCountAfterLastPulse; /* points # after last pulse (step), to remove too close pulses. */
        int        m_iPeakIntervalCount;
        int        m_iLostWeightCount;

        int        m_iGUnit;                /* the absolute gravity unit, in Q16*/
        int        m_iFPS;                    /* the FPS unit, in Q0*/

        int        m_iThresStable;            /* the threshold to check absolute acceleration is close to gravity*/
        int        m_iThresEnterLW;        /* the threshold to enter weightlessness status*/
        int        m_iThresLeaveLW;        /* the threshold to leave weightlessness status*/
        int        m_iThresDynPulseAdd;    /* the dynamic threshold of pulse detect = min + @_*/
        int        m_iThresDynPulseAddLow;    /* use lower m_iThresDynPulseAdd, is walking is stable */
        int     m_iThresDynPulseAdpt;   /* m_iThresDynPulseAdd + @_ to adapt holding mode */
        int     m_iThresResuAccelFix;   /* the threshold to ensure the pulse, compare with m_iAbsResuAccel */
        int        m_iThresRunJump;        /* weightlessness point number, <= is Run, > is Jump*/
        int        m_iThresMaxRunInte;        /* max interval between two steps of run, one can not run too slow*/
        int        m_iThresMinJumpInte;    /* min interval between two jumps, one can not jump too fast*/
        int        m_iThresMaxStepInte;    /* max interval between two steps of run or walk, for walk detect */
        int     m_iThresMinStepInte;    /* min interval between two steps of run or walk, for step count */
        int     m_iThresWalkToStill;    /* max non-pulse interval after which the status turn to STILL */

        int     m_iLenPeakInterval;
        int     m_iLenPulseDetect;
        int     m_iLenStableCheck;
        int     m_iLenSearchAccel;      /* ResuAccelHist search len for ensure pulse */

        int[][]        m_iHist =new int[3][STABLE_CHECK_LEN];
        int        m_iHistPos;

        int[]        m_iAccelHist =new int[PULSE_DETECT_LEN];
        int        m_iAccelHistPos;

        int[]        m_iGaussBuf = new int[FILTER_GAUSS_LEN];
        int[]     m_iGaussWin =new int[FILTER_GAUSS_LEN];
        int[]     m_iGaussWinnew =new int[FILTER_GAUSS_LEN];
        int     m_iGaussWinLen = FILTER_GAUSS_LEN;
        int[]        m_iMeanBuf = new int[FILTER_MEAN_LEN];
        int     m_iMeanWinLen;

        int[]     m_iAbsResuAccelHist = new int[PULSE_ENSURE_LEN];
        int     m_iAbsResuAccelHistLen;

        int[]     m_iStepInteHist = new int[STEP_INTER_HIST_LEN];
        int     m_iStepInteMean;        /* in Q8 */
        int     m_iStepInteDiff;        /* in Q8 */
        int     m_iStepInteThres;       /* in Q8 */

        int[]     m_iRawPulseHist = new int[RAW_PLUSE_HIST_LEN];
        int        m_iActIntensity;        /* 0->tiny(watch), 1->middle, 2->hard(pocket) */

        StepDelayShowVar   m_tSDS = new StepDelayShowVar(); /* Do not show steps at beginning until walking state ensured. */
    }

    public static final int HAR_MOTION_STILL = 0;
    public static final int HAR_MOTION_WALK= 1;
    public static final int HAR_MOTION_RUN = 2;
    public static final int HAR_MOTION_JUMP = 3;
    public static final int HAR_MOTION_MAX = 4;

/*
    public static final int HAR_CFG_NO_USE_NOW        = 0x0001;
    public static final int HAR_CFG_CHK_STEP_STABLE    = 0x0002;
    public static final int HAR_CFG_CHK_RAW_PULSE    = 0x0004;
    public static final int HAR_CFG_ADAPT_WINLEN    = 0x0008;
    public static final int HAR_CFG_DBG_CHG_FLTRD    = 0x0010;
    public static final int HAR_CFG_DBG_SHOW_LOG    = 0x0020;
    public static final int HAR_CFG_DELAY_SHOW_STEP = 0x0040;
    public static final int HAR_CFG_TRI_THRES       = 0x0080;
    public static final int HAR_CFG_CALC_ENERGY        = 0x0100;
    public static final int HAR_CFG_CALC_PEDOMETER  = 0x0200;
    public static final int HAR_CFG_CALC_GESTURE    = 0x0400;
    public static final int HAR_CFG_SET_ALL            = 0xFFFF;
*/
    public static class HarResultCS    {
        int nPrevMotion;
        int nCurMotion;
        int    nTotalSteps;
        int    nResuAccel;        /* resultant acceleration, energy^0.5 */
        int nPulseVal;        /* (is pulse top) ? (the pulse height) : (0) */
    }
    
//END of added SRC
         
    public void reset() {
        accelAvgX = 0;
        accelAvgY = 0;
        accelAvgZ = 0;
        lastTimestamp = 0;//System.currentTimeMillis() / 1.0e3f;
        halfSteps = 0;
        state = STATE_NONE;
        mListener.onStep(0,0);
    }
    
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    
    public void onSensorChanged(SensorEvent event) {
    	
        if (Sensor.TYPE_ACCELEROMETER == event.sensor.getType()) {
            
            Engine.HarResult result = null;
            
            //tester.recognizer.FeedData(tester.g_hareng, 
            //		(int)((event.values[SensorManager.DATA_X])*1000), 
            //		(int)((event.values[SensorManager.DATA_Y])*1000),
            //		(int)((event.values[SensorManager.DATA_Z])*1000));
            
            tester.recognizer.HarFeedData(tester.g_hareng, event.timestamp, 
            		(int)((event.values[0])*1000),  // 0 is x
            		(int)((event.values[1])*1000),  // 1 is y
            		(int)((event.values[2])*1000)); // 2 is z

            tester.recognizer.HarCalcRecord(tester.g_hareng);
            result = tester.recognizer.HarGetResult(tester.g_hareng);
            //System.out.println(" Results <Total Steps> : "+ result.nTotalSteps);
            //System.out.println(" Results <Distance> : "+ result.nDistance);
            
            mListener.onStep(result.nTotalSteps, result.preciseDist); //send the number of Steps
        }
        
    }
    
    public interface StepCountListener {
        //void onStep(int totalSteps, int tot_distance_covered);
    	void onStep(int totalSteps, double tot_distance_covered);
    }
    
}

