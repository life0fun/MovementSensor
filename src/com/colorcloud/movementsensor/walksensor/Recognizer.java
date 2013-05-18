
package com.colorcloud.movementsensor.walksensor;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;

import com.colorcloud.movementsensor.MovementSensorApp.MSAppLog;


public class Recognizer{
    /*==========================================================================*/

    static final int STABLE_CHECK_LEN    =(30);
    static final int FILTER_GAUSS_LEN    =(41);
    static final int FILTER_MEAN_LEN    =(8);
    static final int PULSE_DETECT_LEN    =(128);
    static final int PULSE_ENSURE_LEN    =(48);
    static final int STEP_INTER_HIST_LEN =(4);
    static final int RAW_PLUSE_HIST_LEN  =(4);

    /* Quantization scales */
    static final int FILTER_Q                =(10);
    static final int HISTORY_Q               =(6);
    static final int INPUT_ZERO_BASE            =(32768);            // in Q16, the 0 G value of input data
    static final int STANDARD_GRAVITY        =(26*2);            // in Q8, the gravity unit to calculate the thresholds below.
    static final int STANDARD_FPS            =(100);            // the FPS(frame per second) for the thresholds below.
    static final int ONE_IN_Q8                =(256);
    static final int HAR_MIN_FPS             =(5);    // (20)
    static final int HAR_MAX_FPS             =(100);

    /*==========================================================================*/
    /* all thresholds are made for 8Bit input */
    static final int THRES_STABLE_FACT        =(443);        // (1.732 * 256)

    static final int THRES_STABLE_V            =(631);      // 0.154G in Q12
    //WY static final int THRES_ENTER_LOSEW_V        =(471);        // 0.115G in Q12
    static final int THRES_ENTER_LOSEW_V        =(1500);        // 0.115G in Q12
    static final int THRES_LEAVE_LOSEW_V        =(1577);        // 0.385G in Q12, leave weightlessness status

    static final int THRES_DYN_PULSE_ADD_V    =(888);        // 0.054G in Q14 (Q14=Q8<<HISTORY_Q)
    static final int THRES_DYN_PULSE_ADD_LOW_V =(492);    // 0.030G in Q14 
    static final int THRES_DYN_PULSE_ADAPT   =(888);
    static final int THRES_PULSE_HIGH_CONF_V =(6554);     // 0.100G in Q16
    static final int THRES_RAW_PULSE_HIGH_V  =(205);      // 0.8G in Q8

    static final int THRES_RESU_ACCEL_FIX_V  =(THRES_DYN_PULSE_ADD_V * 2);

    //static final int THRES_LW_RUN_JUMP_N        =(19);
    static final int THRES_LW_RUN_JUMP_N        =(8);
    static final int THRES_MAX_RUN_INTE_N    =(100);        // max interval between two steps
    static final int THRES_MIN_JUMP_INTE_N    =(30);        // min interval between two jumps
    static final int THRES_MAX_STEP_INTE_N    =(150);    // no less than 0.6 step/s, for walk detect
    static final int THRES_MIN_STEP_INTE_N    =(20);        // no more than 5 steps/s, 100/5=20
    static final int THRES_WALK_TO_STILL_N   =(240);      // 2.4s

    static final int THRES_STEP_STABLE_FACT1 =(128);      // 1/2 in Q8
    static final int THRES_STEP_STABLE_FACT2 =(64);      // 1/4 in Q8

    static final int LEN_PEAK_INTERVAL_N     =(60);
    static final int LEN_STABLE_CHECK_N      =(STABLE_CHECK_LEN);
    static final int LEN_PULSE_DETECT_N      =(64);            // 100Hz FPS, 1Hz step, half period=50
    // no more than PULSE_DETECT_LEN !
    static final int LEN_SEARCH_ACCEL_N      =(12);            // to find a pulse in raw data
    // means 0.25step (1step/s), or 1step(4steps/s)

    static final int LEN_PULSE_DISTANCE_N      =(256);
    /*==========================================================================*/
    /* filtering definition ( 41 point gauss filter, then 8 point mean filter ) */

    /*
////////////////////////////////////// 
// firls, stop=2.8Hz, -3dB=1.82Hz, -20dB=3.91Hz, in Q10
//////////////////////////////////////
     */

    static final int[] FLT_GAUSS_WIN   = new int[]{  
        -107,   -61,    -8,    51,   117,   187,  
        261,   338,   416,   494,   572,   647,  
        718,   784,   844,   897,   942,   977,  
        1003,  1019,  1024,  1019,  1003,   977,  
        942,   897,   844,   784,   718,   647,  
        572,   494,   416,   338,   261,   187,  
        117,    51,    -8,   -61,  -107 };

    private static final String TAG = "Recognizer";
    
    String printThis = "";
    FileOutputStream mLogWriter=null;
    FileOutputStream mLogReplacer=null;
    BufferedReader mLogReader=null;
    FileOutputStream mCompLogWriter=null;
    double measureDistance = 0.0;
    double[] printDist= new double[10];
    long totDist=0;
    
    /************************************************************************/
    /* TMotionVar related functions                                         */
    /************************************************************************/

    void MotionClear(StepCounter.MotionVar pVar)
    { 
        pVar.type = pVar.prev_type = StepCounter.HAR_MOTION_STILL; 
        pVar.stat_count = pVar.type_count = pVar.total_count = 0; 
        pVar.updated = false; 
    }
    static void MotionClearFlag(StepCounter.MotionVar pVar)
    { 
        pVar.updated = false; pVar.prev_type = pVar.type; 
    }
    void MotionClearCount(StepCounter.MotionVar pVar)
    { 
        pVar.stat_count = pVar.type_count = pVar.total_count = 0; 
        pVar.updated = false; 
    }
    void MotionUpdate(StepCounter.MotionVar pVar, int newType, int newCount)
    { 
        pVar.stat_count = newCount; 
        // clear type counter if type changed
        if (pVar.type != newType){ 
            pVar.type_count = 0; pVar.type = newType; 
            // LOCAL_PRINT("enter stat <%s>n", GetMotionStatName(pVar.type)); 
        }
        // increase type counter anyway
        pVar.type_count ++ ;
        // notify outside the type data is changed.
        pVar.updated = true; 
    }
    void MotionCountOneStep(StepCounter.MotionVar pVar)
    { 
        pVar.total_count ++; pVar.updated = true; 
    }
    String GetMotionStatName(int type)
    {
        String ret = "Unknown";
        switch(type)
        {
        case StepCounter.HAR_MOTION_STILL:    ret = "STILL";    break;
        case StepCounter.HAR_MOTION_WALK:    ret = "WALK";    break;
        case StepCounter.HAR_MOTION_RUN:    ret = "RUN";    break;
        case StepCounter.HAR_MOTION_JUMP:    ret = "JUMP";    break;
        }
        return ret;
    }

    /************************************************************************/
    /* local functions implementation                                       */
    /************************************************************************/
    short  EnsureU16(int nVal)
    {
        return (short)( (nVal < 0)?(0):((nVal >= Short.MAX_VALUE)?(Short.MAX_VALUE):nVal) );
    }

    void BufShift32Bit(int[] pBuf, int nLen)
    {
        for (--nLen; nLen>0; nLen--) 
        {
            pBuf[nLen] = pBuf[nLen-1];
        }
    }


    void ClearAll(Engine pGlb)
    {
        //C_MEMSET(pGlb, 0, sizeof(Engine));

        MotionClear(pGlb.m_Motion);
    }

    void InitThres(Engine pGlb)
    {
        int    iFact, i;

        // Get the default thresholds
        pGlb.m_iThresStable = StepCounter.HAR_SFT_INT(THRES_STABLE_V * pGlb.m_iGravity, 12); // Q16
        pGlb.m_iThresEnterLW = StepCounter.HAR_SFT_INT(THRES_ENTER_LOSEW_V * pGlb.m_iGravity, 12); // Q16
        pGlb.m_iThresLeaveLW = StepCounter.HAR_SFT_INT(THRES_LEAVE_LOSEW_V * pGlb.m_iGravity, 12); // Q16    
        pGlb.m_iThresDynPulseAdd = StepCounter.HAR_SFT_INT(THRES_DYN_PULSE_ADD_V * pGlb.m_iGravity, 8); // Q14+Q16-Q8
        pGlb.m_iThresDynPulseAddLow = StepCounter.HAR_SFT_INT(THRES_DYN_PULSE_ADD_LOW_V * pGlb.m_iGravity, 8); // Q14+Q16-Q8
        pGlb.m_iThresDynPulseAdpt = StepCounter.HAR_SFT_INT(THRES_DYN_PULSE_ADAPT * pGlb.m_iGravity, 8); // Q14+Q16-Q8;
        pGlb.m_iThresResuAccelFix = StepCounter.HAR_SFT_INT(THRES_RESU_ACCEL_FIX_V * pGlb.m_iGravity, 8+HISTORY_Q); // Q14+Q16-Q14
        pGlb.m_tSDS.pulse_high = StepCounter.HAR_SFT_INT((THRES_PULSE_HIGH_CONF_V * pGlb.m_iGravity), 16);

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
        iFact = StepCounter.HAR_DIV_INT((pGlb.m_iFPS << 8), STANDARD_FPS);
        if (iFact != ONE_IN_Q8)
        {
            // modify all N variables
            StepCounter.HAR_SCALE_VAL(pGlb.m_iThresRunJump, iFact, 8);
            StepCounter.HAR_SCALE_VAL(pGlb.m_iThresMaxRunInte, iFact, 8);
            StepCounter.HAR_SCALE_VAL(pGlb.m_iThresMinJumpInte, iFact, 8);
            StepCounter.HAR_SCALE_VAL(pGlb.m_iThresWalkToStill, iFact, 8);
            StepCounter.HAR_SCALE_VAL(pGlb.m_iThresMaxStepInte, iFact, 8);
            StepCounter.HAR_SCALE_VAL(pGlb.m_iThresMinStepInte, iFact, 8);

            StepCounter.HAR_SCALE_VAL(pGlb.m_iLenPeakInterval, iFact, 8);
            StepCounter.HAR_SCALE_VAL(pGlb.m_iLenPulseDetect, iFact, 8);
            StepCounter.HAR_SCALE_VAL(pGlb.m_iLenStableCheck, iFact, 8);
            StepCounter.HAR_SCALE_VAL(pGlb.m_iLenSearchAccel, iFact, 8);
            if (pGlb.m_iLenSearchAccel < 1) pGlb.m_iLenSearchAccel = 1;
        }

        // Init the m_iAccelHist, to ensure the begin is stable
        for (i=0; i<pGlb.m_iLenPulseDetect; i++)
        {
            pGlb.m_iAccelHist[i] = Integer.MAX_VALUE;
        }

    }

    public static int ResampleFltr(int[] pOut, int[] pIn, int nIn, int iFact)
    {
        int iHalfWin, iOrgHalfWin, iWin, i, iTemp, iPos1, iPos2;

        // get win length
        iOrgHalfWin = (nIn >> 1);
        iHalfWin = StepCounter.HAR_SFT_INT(iOrgHalfWin * iFact, 16);
        if (iHalfWin <=0 ) iHalfWin = 1;

        iWin = (iHalfWin << 1);         // symmetric
        if ((nIn & 0x01)==0x01)
        {
            iWin += 1;
            pOut[iHalfWin] = pIn[iOrgHalfWin];   // set mid-val
        }

        // re-sample the filter win, regular
        iFact = StepCounter.HAR_DIV_INT((iOrgHalfWin << 16), iHalfWin);  // N/newN, in Q8
        for (i=0; i<iHalfWin; i++)
        {
            iTemp = (iHalfWin - i) * iFact;
            iTemp = (iOrgHalfWin << 16) - iTemp;
            if (iTemp < 0) iTemp = 0;       // shouldn't happen if nFPS <= MAX_FPS
            iPos1 = (iTemp >> 16);
            iPos2 = ((iTemp + (1<<16) - 1) >> 16);
            iTemp = iTemp - (iPos1 << 16);   // fact

            pOut[i] = pIn[iPos1] + StepCounter.HAR_SFT_INT(((pIn[iPos2] - pIn[iPos1]) * iTemp), 16);
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

    void InitFltWin(Engine pGlb)
    {
        int[] GaussWin = FLT_GAUSS_WIN;
        int iFact, iSum, i;

        pGlb.m_iGaussWinLen = FILTER_GAUSS_LEN;
        pGlb.m_iMeanWinLen = FILTER_MEAN_LEN;
        for(i=0; i< FILTER_GAUSS_LEN;i++){
            pGlb.m_iGaussWin[i] = GaussWin[i];
        }

        // adjust for different FPS, re-sample the filter
        if (pGlb.m_iFPS != HAR_MAX_FPS)
        {
            iFact = StepCounter.HAR_DIV_INT((pGlb.m_iFPS << 16), HAR_MAX_FPS);      // Q16
            pGlb.m_iGaussWinLen = 
                ResampleFltr(pGlb.m_iGaussWin, GaussWin, pGlb.m_iGaussWinLen, iFact);
            pGlb.m_iMeanWinLen = StepCounter.HAR_SFT_INT((pGlb.m_iMeanWinLen * iFact), 16);
            if (pGlb.m_iMeanWinLen < 1) pGlb.m_iMeanWinLen = 1;    // must >= 1
        }

        // normalize, reuse var pos1, pos2
        iSum = 0;
        for (i=0; i<pGlb.m_iGaussWinLen; i++)  iSum += pGlb.m_iGaussWin[i];
        iFact = StepCounter.HAR_DIV_INT(((1 << FILTER_Q) << 16), iSum);
        for (i=0; i<pGlb.m_iGaussWinLen; i++)  
        {
            pGlb.m_iGaussWin[i] = StepCounter.HAR_SFT_INT((pGlb.m_iGaussWin[i] * iFact), 16);
        }

        // Calculate look-back len based on filter win len
        pGlb.m_iAbsResuAccelHistLen = 1 + (pGlb.m_iGaussWinLen >> 1) + (pGlb.m_iMeanWinLen >> 1);
        pGlb.m_iAbsResuAccelHistLen += pGlb.m_iLenSearchAccel;
        if (pGlb.m_iAbsResuAccelHistLen > PULSE_ENSURE_LEN)
        {
            //LOCAL_PRINT("Critical error! m_iAbsResuAccelHistLen(%d) > %d!!!n", 
            //        pGlb.m_iAbsResuAccelHistLen, PULSE_ENSURE_LEN);
        }

        //if (pGlb.m_iFPS != HAR_MAX_FPS)
        //LOCAL_PRINT("FPS=%d, WinLen=%d,%d, GaussSum=%.2f, add=%.1f, len3=%dn", 
        //        pGlb.m_iFPS, pGlb.m_iGaussWinLen, pGlb.m_iMeanWinLen, iSum/1024.f, 
        //        pGlb.m_iThresDynPulseAdd/64.0, pGlb.m_iAbsResuAccelHistLen);
    }

    void SaveStepInterval(Engine pGlb)
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
        StepCounter.BufShift32Bit(pGlb.m_iStepInteHist, STEP_INTER_HIST_LEN);
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

        pGlb.m_iStepInteMean = StepCounter.HAR_DIV_INT((iSum << 8), i);    // Q8
        pGlb.m_iStepInteDiff = StepCounter.HAR_DIV_INT(((iMax - iMin) << 16), pGlb.m_iStepInteMean);  // Q8
        pGlb.m_iStepInteThres = StepCounter.HAR_SFT_INT((pGlb.m_iStepInteMean * THRES_STEP_STABLE_FACT2), 8); // Q8

        return;
    }

    boolean IsStepInteStable(Engine pGlb)
    {
        int        iVal = 0;
        boolean    bRet = false;

        // if not set, always return false
        if (  (pGlb.m_Config.nFlags & Engine.HAR_CFG_CHK_STEP_STABLE) 
                !=Engine.HAR_CFG_CHK_STEP_STABLE) return false;

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

    boolean CountOneStep(Engine pGlb)
    {
        StepCounter.StepDelayShowVar   pSDS = pGlb.m_tSDS;
        int     nF1, nF2, i,pos=0;
        boolean    bCounted = true;
        double dst=0.0;
        
        pos = pGlb.m_iHistPos;
    	
    	if(pos == 0){
    		pos = pGlb.m_iLenStableCheck-1; // to be fixed replacing array wid Queue
    	} else{
    		pos = pos-1;
    	}
    	
        // if not set, just count this step and return
        if ((pGlb.m_Config.nFlags & Engine.HAR_CFG_DELAY_SHOW_STEP)
                != Engine.HAR_CFG_DELAY_SHOW_STEP)
        {
            MotionCountOneStep(pGlb.m_Motion);
            return bCounted;
        }

        // re-init delay show step var if no step appears in long time
        if (pGlb.m_iCountAfterLastPulse > pGlb.m_iFPS * 2)
        {
            if (pSDS.temp_counter != 0)
            {
                //LOCAL_PRINT("SKIP %d steps at beginning.n", pSDS.temp_counter);
            }
            
            measureDistance = 0.0;
            printThis = "";
            pSDS.is_walking = 0;
            pSDS.temp_counter = 0;
            pSDS.score = (short)((pGlb.m_iCountAfterLastPulse < pGlb.m_iFPS * 6) ? 20 : 0);
        }

        // not walking now
        if (pSDS.is_walking == 0)
        {
        	printThis = printThis + pGlb.m_iHist_timeStamp[pos]/1000000 + "\t" +
        							pGlb.m_iHist[0][pos] + "\t" + 
        							pGlb.m_iHist[1][pos] + "\t" + 
        							pGlb.m_iHist[2][pos] + "\n" ;
        	MSAppLog.n(TAG, "CountOneStep: Not walking now m_iHistPos = " + pos);
        	
        	dst = measureDist(pGlb);
        	measureDistance += dst;
        	printDist[pSDS.temp_counter] = dst;
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
                pGlb.distCovered += measureDistance;                
                
            // Code for printing accountable accl data in file                
                try {
                    if(mLogWriter != null) {
                    	MSAppLog.n(TAG, "CountOneStep  temp_counter: " + printThis.length());
                    	jugularStuff(printThis,pSDS.temp_counter,printDist );
                    	printThis = printThis + "\t" +"\t" +"\t" +"\t"+ pGlb.m_Motion.total_count +"\n";
                        mLogWriter.write(printThis.getBytes());
                        MSAppLog.n(TAG, "Trying to write string of this Length: " + printThis.length());
                    }
                } catch (IOException e) {
                    MSAppLog.n(TAG, "Error writing sensor data to log: " + e);
                } catch (NullPointerException e) {}
                
                printThis = "";                
            // end of new code
                pSDS.temp_counter = 0;
            }
        }
        else
        {
        	printThis = "";
            MotionCountOneStep(pGlb.m_Motion);
         // Code for printing accountable accl data in file
            printThis =             pGlb.m_iHist_timeStamp[pos]/1000000 + "\t" +
									pGlb.m_iHist[0][pos] + "\t" + 
									pGlb.m_iHist[1][pos] + "\t" + 
									pGlb.m_iHist[2][pos] + "\n" ;
            MSAppLog.n(TAG, "CountOneStep: Is walking now m_iHistPos = " + pos);
            dst = measureDist(pGlb);
        	printDist[pSDS.temp_counter] = dst;
            pGlb.distCovered += dst;
            
            try {
                if(mLogWriter != null) {
                	jugularStuff(printThis,pSDS.temp_counter,printDist );
                	printThis = printThis + "\t" +"\t" +"\t"+ pGlb.m_Motion.total_count +"\n";
                    mLogWriter.write(printThis.getBytes());
                    MSAppLog.n(TAG, "Trying to write string of this Length: " + printThis.length());
                }
            } catch (IOException e) {
                MSAppLog.n(TAG, "Error writing sensor data to log: " + e);
            } catch (NullPointerException e) {}
            
            printThis = "";                
        // end of new code
        }

        return bCounted;
    }
    
    public double measureDist(Engine pGlb){
    	double dist = 0,iMin=0, iMax=0;
    	int pos,initPos,sum=0;
    	pos = pGlb.m_iHistPos;
    	MSAppLog.n(TAG, "measureDist: m_iHistPos = " + pGlb.m_iHistPos);
    	if(pos == 0){
    		pos = pGlb.m_iLenStableCheck-1; // to be fixed replacing array wid Queue
    	} else{
    		pos = pos-1;
    	}
    	iMax = iMin = pGlb.m_iHist[2][pos];
    	
    	MSAppLog.n(TAG, "measureDist: Not goof =====  " +
    			pGlb.m_iHist[0][pos]+ "\t"+
    			pGlb.m_iHist[1][pos]+ "\t"+
    			pGlb.m_iHist[2][pos]+ "\n" );

    	//conditions to check for array index boundaries
    	if(pos < 5){
    		initPos = 0;
    	} else {
    		initPos = pos-4;
    	}
    	
    	try{
    		for(int i=initPos;i<=pos;i++){
    			if (pGlb.m_iHist[2][i] > iMax) iMax = pGlb.m_iHist[2][i];
    			if (pGlb.m_iHist[2][i] < iMin) iMin = pGlb.m_iHist[2][i];
    			sum += pGlb.m_iHist[2][i];
    		}
    	} catch(ArrayIndexOutOfBoundsException ex){
    		MSAppLog.n(TAG, "measureDist: " + pos);
    		ex.printStackTrace();
    	}
    	
    	MSAppLog.n(TAG, "measureDist: iMax = " + iMax);
    	MSAppLog.n(TAG, "measureDist: iMin = " + iMin);
    	if(iMax == iMin){
    		return 0; // for now in special case where steps counted with no change in accl 
    	}
    	//dist = Math.sqrt((double)(iMax/1000 - iMin/1000));
    	//dist = Math.sqrt(dist);
    	
    	double avg = 0.0;
    	if(pos<5){
    		avg = sum / (pos+1);
    		
    	} else {
    		avg = sum/5;
    	}
    	dist = (avg - iMin)/(iMax -iMin); // check for div by Zero
    	
    	MSAppLog.n(TAG, "measureDist: Avg " + avg);
    	MSAppLog.n(TAG, "measureDist: Dist" + dist);
    	return dist;
    }
    
    /*
    public double measureDist(Engine pGlb){
    	double dist = 0,iMin=0, iMax=0;
    	int pos,sum=0;
    	pos = pGlb.m_iHistForDistPos;
    	if(pos == 0){
    		pos = LEN_PULSE_DISTANCE_N-1; // to be fixed replacing array wid Queue
    	} else{
    		pos = pos-1;
    	}
    	iMax = iMin = pGlb.m_iHistForDist[2][pos];
    	
    	MSAppLog.n(TAG, "measureDist: goof     =====  " +
    			pGlb.m_iHistForDist[0][pGlb.m_iHistForDistPos]+ "\t"+
    			pGlb.m_iHistForDist[1][pGlb.m_iHistForDistPos]+ "\t"+
    			pGlb.m_iHistForDist[2][pGlb.m_iHistForDistPos]+ "\n" );
    	MSAppLog.n(TAG, "measureDist: Not goof =====  " +
    			pGlb.m_iHist[0][pGlb.m_iHistPos]+ "\t"+
    			pGlb.m_iHist[1][pGlb.m_iHistPos]+ "\t"+
    			pGlb.m_iHist[2][pGlb.m_iHistPos]+ "\n" );
    	
    	MSAppLog.n(TAG, "measureDist: goof =====  " + iMax);
    	//conditions to check for array index boundaries
    	if(pos < 5){
    		pos=4;
    	}
    	
    	try{
    		for(int i=pos-4;i<=pos;i++){
    			if (pGlb.m_iHistForDist[2][i] > iMax) iMax = pGlb.m_iHistForDist[2][i];
    			if (pGlb.m_iHistForDist[2][i] < iMin) iMin = pGlb.m_iHistForDist[2][i];
    			sum += pGlb.m_iHistForDist[2][i];
    		}
    	} catch(ArrayIndexOutOfBoundsException ex){
    		MSAppLog.n(TAG, "measureDist: " + pos);
    		ex.printStackTrace();
    	}
    	
    	MSAppLog.n(TAG, "measureDist: iMax = " + iMax);
    	MSAppLog.n(TAG, "measureDist: iMin = " + iMin);
    	if(iMax == iMin){
    		return 0; // for now in special case where steps counted with no change in accl 
    	}
    	//dist = Math.sqrt((double)(iMax/1000 - iMin/1000));
    	//dist = Math.sqrt(dist);
    	double avg = sum/5;
    	dist = (avg - iMin)/(iMax -iMin); // check for div by Zero
    	
    	MSAppLog.n(TAG, "measureDist: " + avg);
    	MSAppLog.n(TAG, "measureDist: " + dist);
    	return dist;
    }
    */
    
    public void jugularStuff(String printStuff, byte count, double [] readDist){
    	String readLineMain="", readLineAccl="";
    	byte dist_counter=0;
    	
    	//int posAt=0,prevPos=0;

			 try {
				 MSAppLog.n(TAG, "jugularStuff: " + printStuff.length());
				 MSAppLog.n(TAG, "jugularStuff: " + printStuff); // remove, big hit
				 String[] readArray = printStuff.split("\n");
				for(int i=0;i < readArray.length;i++){
					//prevPos = posAt+1;
					//posAt = printStuff.indexOf("\n", posAt);
					//readLineAccl = printStuff.substring(prevPos, posAt);
					readLineAccl = readArray[i];
					
					while((readLineMain = mLogReader.readLine()) != null){
						MSAppLog.n(TAG, "jugularStuff readLineMain: " + readLineMain);
					  if(readLineMain.contains("accl")){	
						if(readLineMain.contains(readLineAccl)){
							totDist += printDist[dist_counter]*(-10000);
							readLineMain = readLineMain + "\t" + "-10000"+ "\t" + 
							               (int)(printDist[dist_counter]*10000) + "\t" +
							               totDist+ "\n";
							mCompLogWriter.write(readLineMain.getBytes());
							MSAppLog.n(TAG, "jugularStuff readLineMain 1: " + readLineMain);
							readLineMain="";
		                    printDist[dist_counter] = 0.0;
							dist_counter++;
							break;
						}
						else{
							readLineMain = readLineMain + "\t" + "0"+ "\t"+
														"0" + "\t" + totDist +"\n";
							mCompLogWriter.write(readLineMain.getBytes());
							MSAppLog.n(TAG, "jugularStuff readLineMain 0: " + readLineMain);
							readLineMain="";
						}
					  }
							
					}
					
				}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

    }

    boolean FeedData(Engine pGlb, long timeStamp, int x, int y, int z)
    {
        if (pGlb.m_bInitOK == false) return false;

        // Clear the updated flag
        MotionClearFlag(pGlb.m_Motion);

        // increase the frame counter
        pGlb.m_iTotalFrm ++ ;

        // store the input, must be in Q16
        
        //pGlb.m_iX = (int)(x & 0xFFFF) - INPUT_ZERO_BASE;
        //pGlb.m_iY = (int)(y & 0xFFFF) - INPUT_ZERO_BASE;
        //pGlb.m_iZ = (int)(z & 0xFFFF) - INPUT_ZERO_BASE;

        
        pGlb.m_iX = x;
        pGlb.m_iY = y;
        pGlb.m_iZ = z;
        pGlb.m_timeStamp = timeStamp;
        
        // get resultant force
        pGlb.m_iEnergy = ((long)pGlb.m_iX * (long)pGlb.m_iX) + 
                        ((long)pGlb.m_iY * (long)pGlb.m_iY) + 
                        ((long)pGlb.m_iZ * (long)pGlb.m_iZ);
        //    pGlb.m_fResuAccel = (Float)(C_SQRT((Float)pGlb.m_iEnergy));
        pGlb.m_iResuAccel = (int)Math.sqrt((double)pGlb.m_iEnergy);
        pGlb.m_iAbsResuAccel = (int)(pGlb.m_iResuAccel - pGlb.m_iGravity);
        //if (pGlb.m_iAbsResuAccel < Short.MIN_VALUE) pGlb.m_iAbsResuAccel = Short.MIN_VALUE;
        //if (pGlb.m_iAbsResuAccel > Short.MAX_VALUE) pGlb.m_iAbsResuAccel = Short.MAX_VALUE;

        // update the current position (angle) if possible
        if (IsDeviceStable(pGlb))
        {
            // LOCAL_PRINT("re-calc positionn");
            CalcDevicePos(pGlb);
        }

        // get the acceleration in gravity direction
        /*
    pGlb.m_fAccelInGrav = pGlb.m_iX * pGlb.m_fAngleX 
                         + pGlb.m_iY * pGlb.m_fAngleY 
                         + pGlb.m_iZ * pGlb.m_fAngleZ;// dot product
    pGlb.m_fAccelInGrav = pGlb.m_fResuAccel;
         */
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
         CheckStill(pGlb);

        // Check if the function is under the weightlessness status.
        //  when running & jumping, it must be some time that gravity absent
        CheckWeightless(pGlb);

        // Check wave top (pulse) to count the steps.
        pGlb.m_iPulseVal = CheckPulse(pGlb);
        if (pGlb.m_iPulseVal != 0 && pGlb.m_bEnterLoseWeight == false)
        {
                CheckWalk(pGlb);
        }

        // update the total steps, total counter only increase in RUN or WALK
        //    if (pGlb.m_iPulseVal != 0 && (pGlb.m_Motion.type == MOTION_RUN || pGlb.m_Motion.type == MOTION_WALK))
        if (pGlb.m_iPulseVal != 0)
        {
            if (pGlb.m_iCountAfterLastPulse < pGlb.m_iThresMinStepInte)
            {
                // too close to previous step. remove it!
                //LOCAL_PRINT("pulse RMV! step too close (interval=%d) frm=%d!n", 
                //        pGlb.m_iCountAfterLastPulse, pGlb.m_iTotalFrm);

                if ((pGlb.m_Config.nFlags & Engine.HAR_CFG_DBG_CHG_FLTRD) 
                        == Engine.HAR_CFG_DBG_CHG_FLTRD)
                {
                    pGlb.m_iFltrdAccel -= (pGlb.m_iGravity << (HISTORY_Q - 1));
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
        pGlb.m_HarResult.nCurMotion = pGlb.m_Motion.type;
        pGlb.m_HarResult.nPrevMotion= pGlb.m_Motion.prev_type;
        pGlb.m_HarResult.nTotalSteps= pGlb.m_Motion.total_count;
        pGlb.m_HarResult.preciseDist= (int)pGlb.distCovered;
        //WY pGlb.m_HarResult.nResuAccel = EnsureU16((int)(pGlb.m_iResuAccel + Short.MAX_VALUE));
        pGlb.m_HarResult.nResuAccel = pGlb.m_iResuAccel;
        pGlb.m_HarResult.nPulseVal  = pGlb.m_iPulseVal;

        return pGlb.m_Motion.updated;
    }

    boolean IsDeviceStable(Engine pGlb)
    {
        int        i, j, max, min;
        int        iThres;

        // save the history data for smooth
        pGlb.m_iHist[0][pGlb.m_iHistPos] = pGlb.m_iX;
        pGlb.m_iHist[1][pGlb.m_iHistPos] = pGlb.m_iY;
        pGlb.m_iHist[2][pGlb.m_iHistPos] = pGlb.m_iZ;
        pGlb.m_iHist_timeStamp[pGlb.m_iHistPos] = pGlb.m_timeStamp;
        if (++pGlb.m_iHistPos >= pGlb.m_iLenStableCheck) pGlb.m_iHistPos = 0;
        
        // additions for dist calc
        pGlb.m_iHistForDist[0][pGlb.m_iHistForDistPos] = pGlb.m_iX;
        pGlb.m_iHistForDist[1][pGlb.m_iHistForDistPos] = pGlb.m_iY;
        pGlb.m_iHistForDist[2][pGlb.m_iHistForDistPos] = pGlb.m_iZ;
        if (++pGlb.m_iHistForDistPos >= LEN_PULSE_DISTANCE_N){	//once reached end of array blank it
        	pGlb.m_iHistForDistPos = 0;
        	for(i=0;i<3;i++){
        		for(j=0;j<5;j++){
        			pGlb.m_iHistForDist[i][j] = 0;
        		}
        	}
        }        
        
        // end of chng for dist calc
        
        

        // check if current resultant acceleration is close to gravity
        iThres = (int)(StepCounter.HAR_SFT_INT((pGlb.m_iThresStable * THRES_STABLE_FACT), 8));
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


    void CalcDevicePos(Engine pGlb)
    {
        int     iFact;

        /*
    pGlb.m_fAngleX = pGlb.m_iX / pGlb.m_fResuAccel;
    pGlb.m_fAngleY = pGlb.m_iY / pGlb.m_fResuAccel;
    pGlb.m_fAngleZ = pGlb.m_iZ / pGlb.m_fResuAccel;
         */

        iFact = (1 << 16) / pGlb.m_iResuAccel;     // Q16
        pGlb.m_iAngleX = StepCounter.HAR_SFT_INT(pGlb.m_iX * iFact, 8);// Q8
        pGlb.m_iAngleY = StepCounter.HAR_SFT_INT(pGlb.m_iY * iFact, 8);// Q8
        pGlb.m_iAngleZ = StepCounter.HAR_SFT_INT(pGlb.m_iZ * iFact, 8);// Q8
    }

    int SmoothAccel(Engine pGlb, int iAccel)
    {
        int        i, sum = 0;

        // Store iAccel first, for ensure pulse in CheckPulse()
        StepCounter.BufShift32Bit(pGlb.m_iAbsResuAccelHist, pGlb.m_iAbsResuAccelHistLen);
        pGlb.m_iAbsResuAccelHist[0] = iAccel;

        // Shift Gaussian buffer
        StepCounter.BufShift32Bit(pGlb.m_iGaussBuf, pGlb.m_iGaussWinLen);
        pGlb.m_iGaussBuf[0] = iAccel;

        // Do Gaussian filtering
        for (i=0; i<pGlb.m_iGaussWinLen; i++)
            sum += pGlb.m_iGaussBuf[i] * pGlb.m_iGaussWin[i];
        if (sum < 0) sum = 0;
        // shift back from Q10
        sum = StepCounter.HAR_SFT_INT(sum, (FILTER_Q - HISTORY_Q));

        // Shift mean buffer
        StepCounter.BufShift32Bit(pGlb.m_iMeanBuf, pGlb.m_iMeanWinLen);
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

    //
    // Check STILL
    //   update the flags for 1st run/jump/walk.
    //
    boolean CheckStill(Engine pGlb)
    {
        boolean    bStill = false;

        // 2nd run step must appears in 100 point.
        if (pGlb.m_bFirstRun)
        {
            if (++pGlb.m_iCountAfterLastRun > pGlb.m_iThresMaxRunInte)
            {
                pGlb.m_bFirstRun = false;
                pGlb.m_iCountTwoRuns = 0;
                pGlb.m_iCountAfterLastRun = 0;
                bStill = true;    // Set to STILL  
            }
            else ;
        }
        else ;

        // do not detect jump again in 30 points after 1st jump
        if (pGlb.m_bAfterJump)     
        {
            if (++pGlb.m_iCountAfterJump > pGlb.m_iThresMinJumpInte)
            {
                pGlb.m_bAfterJump = false;
            }
            else ;
        }
        else ;


        // after some pulse, if there's no new pulse in 240 points, go to STILL
        if (++pGlb.m_iPeakIntervalCount > pGlb.m_iThresWalkToStill)    
        {
            pGlb.m_bFirstWalk = false;
            pGlb.m_iPeakIntervalCount = pGlb.m_iLenPeakInterval;
            pGlb.m_iCountTwoSteps = 0;
            bStill = true;    // Set to STILL  
        }
        else ;

        // modify the motion variable
        if (bStill == true)
        {
            MotionUpdate(pGlb.m_Motion, Engine.HAR_MOTION_STILL, 0);
        }

        return bStill;
    }

    boolean CheckWeightless(Engine pGlb)
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
                    MotionUpdate(pGlb.m_Motion, Engine.HAR_MOTION_JUMP, pGlb.m_iLostWeightCount);
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
                        MotionUpdate(pGlb.m_Motion, Engine.HAR_MOTION_RUN, pGlb.m_iCountTwoRuns + pGlb.m_iLostWeightCount*2);
                        pGlb.m_iCountTwoRuns = 0;
                        pGlb.m_bFirstRun = false;
                    }
                    pGlb.m_iCountAfterLastRun = 0;
                }
            }
            else ;
        }

        return ((pGlb.m_Motion.type != Engine.HAR_MOTION_RUN) 
                && (pGlb.m_Motion.type != Engine.HAR_MOTION_JUMP));
    }

    boolean EnsurePulse(Engine pGlb, int nPulse, int nDeltaPulse)
    {
        int nOffset, i, iMax = Integer.MIN_VALUE, iMin = Integer.MAX_VALUE, iThres, iSum, iMean = 0;
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
        if ((pGlb.m_Config.nFlags & Engine.HAR_CFG_CHK_RAW_PULSE) == Engine.HAR_CFG_CHK_RAW_PULSE)
        {
            iThres = (iMax >> 1);
            if (iThres > (int)(pGlb.m_iThresResuAccelFix >> 1)) iThres = pGlb.m_iThresResuAccelFix >> 1;
        iRet = ((iMax >= (int)pGlb.m_iThresResuAccelFix) 
                && (iMax - iMin) > iThres);
        if (iRet == false )
        {
            //LOCAL_PRINT("pulse RMV! no wave. pulse=%d,raw=%d-%d,raw_thres=%d,frm=%dn", 
            //        nPulse, iMax, iMin, pGlb.m_iThresResuAccelFix, pGlb.m_iTotalFrm);
            if ((pGlb.m_Config.nFlags & Engine.HAR_CFG_DBG_CHG_FLTRD) == Engine.HAR_CFG_DBG_CHG_FLTRD)
            {
                pGlb.m_iFltrdAccel -= (pGlb.m_iGravity << HISTORY_Q);
            }
            // not real pulse! maybe caused by noise
            return iRet;
        }
        }

        // perform HEAVY MOVING check (pocket mode)
        if ((pGlb.m_Config.nFlags & Engine.HAR_CFG_TRI_THRES) == Engine.HAR_CFG_TRI_THRES)
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
            StepCounter.BufShift32Bit(pGlb.m_iRawPulseHist, RAW_PLUSE_HIST_LEN);
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
                iMean = StepCounter.HAR_DIV_INT(iSum, i); 
            }
            iThres = StepCounter.HAR_SFT_INT(THRES_RAW_PULSE_HIGH_V * pGlb.m_iGravity, 8);
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
                    //LOCAL_PRINT("pulse RMV! deltaV=%d<%d,frm=%dn", nDeltaPulse, 
                    //        pGlb.m_iThresDynPulseAdpt, 
                    //        pGlb.m_iTotalFrm);
                    if ((pGlb.m_Config.nFlags & Engine.HAR_CFG_DBG_CHG_FLTRD)
                            == Engine.HAR_CFG_DBG_CHG_FLTRD)
                    {
                        pGlb.m_iFltrdAccel -= (pGlb.m_iGravity << (HISTORY_Q - 2));
                    }
                    return false;
                }
            }
        }    

        return true;
    }

    int CheckPulse(Engine pGlb)
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
            iVal = StepCounter.HAR_SFT_INT(pGlb.m_iAccelHist[i1], HISTORY_Q);
            //        LOCAL_PRINT("Pulse=%.2f, add=%.2fn", pGlb.m_iAccelHist[i1]/64.f, pGlb.m_iThresDynPulseAdd/64.f);
            if (EnsurePulse(pGlb, iVal, pGlb.m_iAccelHist[i1] - iThreshold) == true)
            {
                // Just show debug info.
                if (pGlb.m_iAccelHist[i1] < iMin + pGlb.m_iThresDynPulseAdd)
                {
                    //LOCAL_PRINT("pulse BACK! stepC=(%d/%d), frm=%dn", 
                    //        StepCounter.HAR_SFT_INT(pGlb.m_iStepInteMean, 8), 
                    //        pGlb.m_iCountAfterLastPulse, 
                    //        pGlb.m_iTotalFrm);

                    // to show result on real-time wave
                    if ((pGlb.m_Config.nFlags & Engine.HAR_CFG_DBG_CHG_FLTRD)
                            == Engine.HAR_CFG_DBG_CHG_FLTRD)
                    {
                        pGlb.m_iFltrdAccel = (int)((iVal + pGlb.m_iGravity * 2) << HISTORY_Q);
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

    void CheckWalk(Engine pGlb)
    {
        if (pGlb.m_iPulseVal != 0 && pGlb.m_bEnterLoseWeight == false)
        {
            if ( ! pGlb.m_bFirstWalk)
            {
                // 1st step
                if ((pGlb.m_iPeakIntervalCount < pGlb.m_iThresMaxStepInte) && !pGlb.m_bAfterJump)    // walk
                {
                    pGlb.m_bFirstWalk = true;
                    pGlb.m_iCountTwoSteps += pGlb.m_iPeakIntervalCount;
                }
                else ;
            }
            else
            {
                if ((pGlb.m_iPeakIntervalCount < pGlb.m_iThresMaxStepInte) && !pGlb.m_bAfterJump)    // walk
                {
                    pGlb.m_iCountTwoSteps += pGlb.m_iPeakIntervalCount;
                    MotionUpdate(pGlb.m_Motion, Engine.HAR_MOTION_WALK, pGlb.m_iCountTwoSteps);
                    pGlb.m_bFirstWalk = false;
                    pGlb.m_iCountTwoSteps = 0;
                }
                else ;
            }
            pGlb.m_iPeakIntervalCount = 0;
        }
    }

    /*
int GetJumpHeight(Engine pGlb)
{
    Float    t, fH;
    int        iH;

    if (pGlb.m_iLostWeightCount == 0 || pGlb.m_iFPS == 0) return 0;

    t = (Float)pGlb.m_iLostWeightCount / pGlb.m_iFPS;

    fH = 0.5f * 9.8f * t * t;        // 1/2 * G * t^2

    iH = (int)(fH * 65536);            // to Q16

    return (iH >> 1);                    // for a jump, the time is for up + down
}
     */

    /************************************************************************/
    /* exported functions                                                   */
    /************************************************************************/

    /*int  HarGetGlbBufSize()
    {
        int nSize = (( (sizeof(Engine) + 3) >> 2) << 2);

        return nSize;
    }
     */
    void HarInit(Engine pEng, int nGravity, int nFps)
    {
        if (pEng == null || nGravity == 0 || nFps < HAR_MIN_FPS || nFps > HAR_MAX_FPS) return;

        ClearAll(pEng);

        pEng.m_iGravity = nGravity;
        pEng.m_iFPS = nFps;
        pEng.m_iPeakIntervalCount = 45;

        InitThres(pEng);

        InitFltWin(pEng);

        // init the Recorder with default value
        Record.MR_InitDefault(pEng.m_HarRecord);

        // init the gesture recognizer
        // Gesture is taken up for the time being.
        //GES_Init(pEng.m_HarGesture, nGravity, nFps);

        pEng.m_bInitOK = true;
    }

    int  HarSetConfig(Engine pEng, Engine.HarConfig pConfig)
    {
        boolean    bUserInfoChgd = false;

        if (pEng == null || pConfig == null) return 0;

        if ( pEng.m_Config != null &&(pConfig.szUserName.equals( pEng.m_Config.szUserName) || 
                pEng.m_Config.nUserGender != pConfig.nUserGender || 
                pEng.m_Config.nUserWeight != pConfig.nUserWeight || 
                pEng.m_Config.nUserHeight != pConfig.nUserHeight))
        {
            // user info changed, re-init the recorder
            bUserInfoChgd = true;
        }
        pEng.m_Config = pConfig;

        if (bUserInfoChgd)
        {
            Record.MR_Init(pEng.m_HarRecord, pEng.m_Config.szUserName, 
                    pEng.m_Config.nUserGender, pEng.m_Config.nUserHeight, 
                    pEng.m_Config.nUserWeight, pEng.m_iGravity, pEng.m_iFPS);
        }

        return 1;
    }

    Engine.HarConfig  HarGetConfig( Engine pEng)
    {
        if (pEng == null ) return null;
        return pEng.m_Config;
    }

    int  HarReset(Engine pEng)
    {
        if (pEng == null) return 0;

        MotionClearCount(pEng.m_Motion);

        return 1;
    }

    Engine.HarResult HarGetResult(Engine pEng)
    {
        if ( pEng == null) return null;

        return pEng.m_HarResult;
    }

    boolean  HarFeedData(Engine pEng, long timeStamp, int x, int y, int z)
    {
        boolean    iRet = false;

        if (pEng == null) return false;

        if ((pEng.m_Config.nFlags & Engine.HAR_CFG_CALC_PEDOMETER) == Engine.HAR_CFG_CALC_PEDOMETER)
        {
            iRet = FeedData(pEng, timeStamp,x, y, z);

            if (iRet && ((pEng.m_Config.nFlags & Engine.HAR_CFG_CALC_ENERGY) == Engine.HAR_CFG_CALC_ENERGY)){
                Record.MR_Update(pEng.m_HarRecord, pEng.m_HarResult.nTotalSteps, pEng.m_HarResult.nPulseVal);
            }
        }

        // Take out gesture for now.
        /*
        if ((pEng.m_Config.nFlags & Engine.HAR_CFG_CALC_GESTURE) == Engine.HAR_CFG_CALC_GESTURE)
        {
            GES_FeedData(pEng.m_HarGesture, x, y, z);
        }
         */
        return iRet;
    }

    public boolean  HarCalcRecord(Engine pEng)
    {
        if ( pEng == null) return false;
        if ( (pEng.m_Config.nFlags & Engine.HAR_CFG_CALC_PEDOMETER) !=Engine.HAR_CFG_CALC_PEDOMETER ) {
            return false;
        }

        if ( (pEng.m_Config.nFlags & Engine.HAR_CFG_CALC_ENERGY) != Engine.HAR_CFG_CALC_ENERGY){
            return false;
        }

        pEng.m_HarResult.nEnergy = Record.MR_GetTotalEnergy(pEng.m_HarRecord);
        pEng.m_HarResult.nDistance = Record.MR_GetDistance(pEng.m_HarRecord);
        pEng.m_HarResult.nVelocity = Record.MR_GetVelocity(pEng.m_HarRecord);
        pEng.m_HarResult.nJumpHeight = Record.MR_GetJumpHeight(pEng.m_HarRecord, pEng.m_iLostWeightCount);

        return true;
    }


    int HarGetGesMask(Engine pEng)
    {
        if (pEng == null) return 0;
        // Gesture has been taken out for now.
        //return GES_GetEventMask(pEng.m_HarGesture);
        return 0;
    }

    int  HarSetGesMask(Engine pEng, int nMask)
    {
        if (pEng == null) return 0;

        // Gesture has been taken out for now.
        //return GES_SetEventMask(pEng.m_HarGesture, nMask);
        return 0;
    }

    int  Har_GetGesEvent(Engine pEng, int[] pnEvt, 
            int[]pParam1, int[]pParam2)
    {

        if (pEng == null) return 0;
        // Gesture has been taken out for now.
        //return GES_GetEvent(pEng.m_HarGesture, pnEvt, pParam1, pParam2);
        return 0;
    }

    /*======================================================================*/
    /* debug functions                                                      */
    /*======================================================================*/

    int  HarGetPulseAdd(Engine pEng)
    {
        int          iVal;

        if ( pEng == null) return 0;

        iVal = pEng.m_iThresDynPulseAdd;       // Q16+6
        iVal = StepCounter.HAR_SFT_INT(iVal, HISTORY_Q);    // Q16
        iVal = StepCounter.HAR_DIV_INT((iVal * 1000), pEng.m_iGravity);

        // iVal = HAR_DIV_INT((iVal * STANDARD_GRAVITY), pEng.m_iGravity);

        return iVal;
    }

    int  HarSetPulseAdd(Engine pEng, int nPulseAdd)
    {
        int          iVal;

        if (pEng == null) return 0;
        if (nPulseAdd < 0) nPulseAdd = 0;

        // 
        iVal = StepCounter.HAR_DIV_INT(nPulseAdd*pEng.m_iGravity, 1000);
        pEng.m_iThresDynPulseAdd = (iVal << HISTORY_Q);

        return iVal;
    }

    // required by Hong, 2008-09-28
    int  HarSetGlobalThres(Engine pEng, int nThres)
    {
        int nPrevThres = HarGetPulseAdd(pEng);

        // if nThres < 0, just return the current setting
        if (nThres >= 0)
        {
            if (nThres > 1000) nThres = 1000;    // 1G 
            HarSetPulseAdd(pEng, nThres);
        }

        return nPrevThres;
    }
}
