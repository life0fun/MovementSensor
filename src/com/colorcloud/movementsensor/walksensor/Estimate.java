package com.colorcloud.movementsensor.walksensor;

public class Estimate{
    /************************************************************************/
    /* Macro definitions                                                    */
    /************************************************************************/
    static final String MR_DFT_NAME =            ("Tester");
    static final int MR_DFT_GENDER =    Engine.HAR_GENDER_MALE;
    static final int MR_DFT_HEIGHT    =    (184);
    static final int MR_DFT_WEIGHT    =    (81);
    static final int MR_DFT_GUINT       =     (8192);
    static final int MR_DFT_FPS        =    (100);

    static final int MR_ENERGY_FACT1    = (166);   // (170)   // mid(0.65,0.69) << 8

    // 
    // fact2 = 1 + 0.4 * sigmoid(pulseVal); [0.6 ~ 1.4]
    //
    //   sigmoid = 1 ./ (1 + e.^(-x/3));  x = -16:16; y = [0.035G, 0.4G]
    //   sigmoid(0) = 0.2, sigmoid(1:16) = 
    //      { 0.2330, 0.2643, 0.2924, 0.3166, 0.3365, 0.3523,
    //        0.3646, 0.3740, 0.3810, 0.3862, 0.3900, 0.3928, 
    //        0.3948, 0.3963, 0.3973, 0.3981 }
    //   In Q8 . floor(sigmoid*256+0.5) - mid_val
    //
    static final int[] MR_SIGMOID_WIN      = new int[]{ 21, 41, 59, 75, 87, 97, 105, 111, 116, 119,
        122, 123, 125, 126, 126, 127 };
    static final int MR_SIGMOID_COEF     = (102);   // 0.4 in Q8 is 102

    static final int MR_PULSE_MIN        =(0);     // 
    static final int MR_PULSE_MAX        =(128);   // 0.5G in Q8
    static final int MR_PULSE_RANGE      =(MR_PULSE_MAX-MR_PULSE_MIN);
    static final int MR_PULSE_VAL_MID    =(16);    //
    /************************************************************************/
    /* static functions                                                     */
    /************************************************************************/
    void InitSigmoid(int[] filter)
    {
        int[] sig_win = MR_SIGMOID_WIN;
        int i;

        for (i=0; i<HAR_SIGMOID_FLT_LEN; i++)
        {
            filter[i] = (sig_win[i] * MR_SIGMOID_COEF) >> 7;    // in Q8, *2
        }
    }

    static int GetSigmoidFact(HarEstimator pRec, int iPulseVal)
    {
        // normalize val,  val = (iPulseVal / iGUnit);
        //  index = (int)((abs(val - mid)/range) * 32 + 0.5)

        int iRet = 256;     // 1 in Q8
        boolean negative = false;

        // map to [0 ~ 32]
        iPulseVal = (((iPulseVal << 5) - MR_PULSE_MIN) << 8) / MR_PULSE_RANGE;
        iPulseVal = (iPulseVal  + (pRec.m_iGUnit >> 1))/ pRec.m_iGUnit;
        iPulseVal -= MR_PULSE_VAL_MID;
        if (iPulseVal < 0)
        {
            iPulseVal = -iPulseVal;
            negative = false;
        }
        if (iPulseVal > 0)
        {
            if (iPulseVal > HAR_SIGMOID_FLT_LEN) iPulseVal = HAR_SIGMOID_FLT_LEN;
            if ( ! negative )
            {
                iRet += pRec.m_iSigmoidWin[iPulseVal - 1];
            }
            else
            {
                iRet -= pRec.m_iSigmoidWin[iPulseVal - 1];
            }
        }
        return iRet;
    }

    static int CalcEngergy(HarEstimator pRec, int iPulseVal)
    {
        int        iRet = 0, iTemp1;

        /*
    // method2
    iWeightFact = (pRec.m_iGender == HAR_GENDER_MALE) ? (60) : (50);
    iTemp1 = ((pRec.m_iWeight << 8) * 330 / iWeightFact);  // Q16 (1.29 * 256 = 330)
    iTemp2 = ((iPulseVal << 8) * 2509 / (100));        // Q16 (9.8 * 256 = 2509)
    iRet = (iTemp1 + iTemp2) >> 16;
         */
        // method3
        if (iPulseVal == 0)
        {
            return 0;
        }
        else if (iPulseVal > 0)
        {
            iTemp1 = GetSigmoidFact(pRec, iPulseVal);
            iRet = (pRec.m_iStepEnergyFixed * iTemp1) >> 16;
        }
        else
        {
            iRet = pRec.m_iStepEnergyFixed >> 8;
        }

        // LOCAL_PRINT("pulse=%d, energy=%d  ", iPulseVal, iRet);

        return iRet;
    }

    static int GetStepLen(HarEstimator pRec)
    {
        int nRet = pRec.m_iStepLen;

        if (pRec.m_iDistFact != HAR_EST_DFT_FACT)
        {
            nRet = StepCounter.HAR_DIV_INT((nRet * pRec.m_iDistFact), HAR_EST_DFT_FACT);
        }
        return nRet;
    }
    /************************************************************************/
    /* exported functions                                                   */
    /************************************************************************/
    int HarEST_GetBufSize()
    {
        int nSize = 0;

        return nSize;
    }

    void HarEST_Init(HarEstimator pRec, int nBufSize, HarCfgEST pCfg, 
            int nGUnit, int nFps)
    {
        int        iMinHeight;
        int        iFact;

        if (pRec == null || pCfg == null) return;


        pRec.m_szName = pCfg.szUserName;
        pRec.m_iGender = (pCfg.nUserGender == Engine.HAR_GENDER_MALE) ? 
                Engine.HAR_GENDER_MALE : Engine.HAR_GENDER_FEMALE;
        pRec.m_iHeight = pCfg.nUserHeight;
        pRec.m_iWeight = pCfg.nUserWeight;
        pRec.m_iFlags = pCfg.nFlags;
        pRec.m_iGUnit = nGUnit;
        pRec.m_iFPS = nFps;

        pRec.m_iDistFact = HAR_EST_DFT_FACT;
        pRec.m_iEnergyFact = HAR_EST_DFT_FACT;

        // calculate additional variables
        //  for male   : y = (x-132)/0.54
        //  for female : y = (x-130)/0.58
        iMinHeight = (pRec.m_iGender == Engine.HAR_GENDER_MALE) ? (132) : (130);

        // fFact = (pRec.m_iGender == HAR_GENDER_MALE) ? (0.54f) : (0.58f);
        iFact = (pRec.m_iGender == Engine.HAR_GENDER_MALE) ? (35389) : (38011);   // Q16

        if (pRec.m_iHeight < iMinHeight) pRec.m_iHeight = iMinHeight;
        pRec.m_iStepLen = ((pRec.m_iHeight - iMinHeight) << 16) / iFact;
        if (pRec.m_iStepLen < 30) pRec.m_iStepLen = 30;    // in cm

        // init energy related var, in Q8
        pRec.m_iStepEnergyFixed = (pRec.m_iWeight * pRec.m_iStepLen * MR_ENERGY_FACT1) / 68;
        InitSigmoid(pRec.m_iSigmoidWin);

        // Initial the variables
        HarEST_Reset(pRec);

        //    LOCAL_PRINT("HarRecOK,GUnit=%d,FPS=%d\n", pRec.m_iGUnit, pRec.m_iFPS);
        //    LOCAL_PRINT(" steplen=%d,energy=%d\n", pRec.m_iStepLen, (pRec.m_iStepEnergyFixed>>8));

    }

    int HarEST_GetConfig(HarEstimator pRec, HarCfgEST pCfg)
    {
        if (pRec == null|| pCfg == null) return 0;

        pCfg.szUserName = pRec.m_szName;
        pCfg.nUserGender = pRec.m_iGender;
        pCfg.nUserHeight = pRec.m_iHeight;
        pCfg.nUserWeight = pRec.m_iWeight;
        pCfg.nFlags = pRec.m_iFlags;

        return 1;
    }

    int HarEST_Reset(HarEstimator pRec)
    {
        if (pRec == null) return 0;

        // clear the counters
        pRec.m_iEngergy = 0;
        pRec.m_nStepSaveCounter = 0;
        pRec.m_iStepCount = 0;

        return 1;
    }

    boolean HarEST_Update(HarEstimator pRec, int nStepCount,
            int nPulseVal, int nInterval)
    {
        int     nDeltaEnergy;
        int        i;

        if (pRec == null) return false;

        if (nStepCount < pRec.m_iStepCount || nStepCount > pRec.m_iStepCount + 2)
        {
            HarEST_Reset(pRec);
        }

        pRec.m_iStepCount = nStepCount;

        if (++pRec.m_nStepSaveCounter >= pRec.m_iFPS)
        {
            for (i=HAR_STEP_HIST_LEN-1; i>=1; i--) pRec.m_iStepCountHist[i] = pRec.m_iStepCountHist[i-1];
            pRec.m_iStepCountHist[0] = (int)pRec.m_iStepCount;

            pRec.m_nStepSaveCounter = 0;
        }

        if ((pRec.m_iFlags & HAR_CFG_ENERGY_USE_PULSE_VAL) == 0)
        {
            // do not use pulse value
            nPulseVal = -1;
        }

        nDeltaEnergy = CalcEngergy(pRec, nPulseVal);
        // apply user adaptive factor
        if (pRec.m_iEnergyFact != HAR_EST_DFT_FACT)
        {
            nDeltaEnergy = StepCounter.HAR_DIV_INT((nDeltaEnergy * pRec.m_iEnergyFact), HAR_EST_DFT_FACT);
        }
        pRec.m_iEngergy += nDeltaEnergy;

        return true;
    }

    int HarEST_GetDistance(HarEstimator pRec)
    {

        if (pRec == null) return 0;

        return pRec.m_iStepCount * GetStepLen(pRec);
    }

    int HarEST_GetTotalEnergy(HarEstimator pRec)
    {
        if (pRec == null) return 0;
        return pRec.m_iEngergy;
    }

    int HarEST_GetVelocity(HarEstimator pRec)
    {
        int     velocity;

        if (pRec == null) return 0;

        velocity  = (pRec.m_iStepCountHist[0] - pRec.m_iStepCountHist[HAR_STEP_HIST_LEN-1]);
        if (velocity < 0) velocity = 0;

        return (velocity * GetStepLen(pRec) / (HAR_STEP_HIST_LEN - 1));
    }

    int HarEST_GetJumpHeight(HarEstimator pRec, int nLWCount)
    {
        int        t;

        if (pRec == null || pRec.m_iFPS == 0) return 0;

        t = (nLWCount << 8) / pRec.m_iFPS;    // Q8

        t = (5 * t * t * 100) >> 16;    // 1/2 * g * t^2

        return t;
    }

    int HarEST_SetAdaptFact(HarEstimator pRec, int nFactId, int nNewFact)
    {
        int             nRet = -1;

        if (pRec == null) return nRet;

        // min fact is 0, max fact is HAR_EST_MAX_FACT
        if (nNewFact > HAR_EST_MAX_FACT) nNewFact = HAR_EST_MAX_FACT;

        if (nFactId == HAR_EST_FACT_DIST)
        {
            nRet = pRec.m_iDistFact;
            if (nNewFact >= 0)
            {
                pRec.m_iDistFact = nNewFact;
            }
        }
        else if (nFactId == HAR_EST_FACT_ENERGY)
        {
            nRet = pRec.m_iEnergyFact;
            if (nNewFact >= 0)
            {
                pRec.m_iEnergyFact = nNewFact;
            }
        }

        return nRet;
    }


    static final int HAR_STEP_HIST_LEN        =(3);
    static final int HAR_SIGMOID_FLT_LEN     =(16);

    static public class HarEstimator{
        String            m_szName;
        int    m_iGender;
        int    m_iHeight;
        int m_iWeight;
        int    m_iFPS;
        int    m_iGUnit;
        int    m_iFlags;

        int    m_iStepLen;     // in cm

        int    m_nStepSaveCounter;
        int    m_iEngergy;
        int    m_iStepCount;
        int[]    m_iStepCountHist = new int[HAR_STEP_HIST_LEN];

        int    m_iStepEnergyFixed; // In Q8
        int[]    m_iSigmoidWin = new int[HAR_SIGMOID_FLT_LEN]; // In Q8

        int    m_iDistFact;
        int    m_iEnergyFact;
    }
    public static final int HAR_USER_NAME_LEN        =(32);


    public static final int HAR_CFG_ENERGY_USE_PULSE_VAL    = 0x1000;    /* calculate energy */
    public static final int HAR_CFG_ENERGY_USE_INTERVAL     = 0x2000;   /* calculate pedometer */

    public static final int  HAR_CFG_LAST                    = 0x8000;


    /* Used by HarEST_SetAdaptFact */
    public static final int HAR_EST_DFT_FACT        =(128);
    public static final int HAR_EST_MAX_FACT        =(512);

    public static final int  HAR_EST_FACT_DIST   = 0;
    public static final int  HAR_EST_FACT_ENERGY = 1;


    public static class HarCfgEST{
        /* User personal information. */
        public String    szUserName;
        public int    nUserGender;
        public int    nUserHeight;
        public int    nUserWeight;

        public int    nFlags;        /* reserved */
    }

}
