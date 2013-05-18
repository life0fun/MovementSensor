package com.colorcloud.movementsensor.walksensor;

public class Record{

    /************************************************************************/
    /* Macro definitions                                                    */
    /************************************************************************/
    public static final String MR_DFT_NAME = ("Tester");
    public static final int MR_DFT_GENDER        =Engine.HAR_GENDER_MALE;
    public static final int MR_DFT_HEIGHT        =(184);
    public static final int MR_DFT_WEIGHT        =(81);
    public static final int MR_DFT_GUINT        =(8192);
    public static final int MR_DFT_FPS            =(100);

    public static final int MR_ENERGY_FACT1     =(166);   // (170)   // mid(0.65,0.69) << 8

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
    public static final int[] MR_SIGMOID_WIN  = new int[]{ 21, 41, 59, 75, 87, 97, 105, 111, 116, 119, 
        122, 123, 125, 126, 126, 127 };
    public static final int MR_SIGMOID_COEF     =(102);   // 0.4 in Q8 is 102

    public static final int MR_PULSE_MIN        =(0);     // 
    public static final int MR_PULSE_MAX        =(128);   // 0.5G in Q8
    public static final int MR_PULSE_RANGE      =(MR_PULSE_MAX-MR_PULSE_MIN);
    public static final int MR_PULSE_VAL_MID    =(16);    //
    /************************************************************************/
    /* static functions                                                     */
    /************************************************************************/
    public static void InitSigmoid(int[] filter)
    {
        int[] sig_win = MR_SIGMOID_WIN;
        int i;

        for (i=0; i<HAR_SIGMOID_FLT_LEN; i++)
        {
            filter[i] = (sig_win[i] * MR_SIGMOID_COEF) >> 7;    // in Q8, *2
        }
    }

    public static int GetSigmoidFact(HarRecord pRec, int iPulseVal)
    {
        // normalize val,  val = (iPulseVal / iGUnit);
        //  index = (int)((abs(val - mid)/range) * 32 + 0.5)

        int iRet = 256;     // 1 in Q8
        boolean b_negative = false;

        // map to [0 ~ 32]
        iPulseVal = (((iPulseVal << 5) - MR_PULSE_MIN) << 8) / MR_PULSE_RANGE;
        iPulseVal = (iPulseVal  + (pRec.m_iGUnit >> 1))/ pRec.m_iGUnit;
        iPulseVal -= MR_PULSE_VAL_MID;
        if (iPulseVal < 0)
        {
            iPulseVal = -iPulseVal;
            b_negative = true;
        }
        if (iPulseVal > 0)
        {
            if (iPulseVal > HAR_SIGMOID_FLT_LEN) iPulseVal = HAR_SIGMOID_FLT_LEN;
            if ( ! b_negative )
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

    public static int CalcEngergy(HarRecord pRec, int iPulseVal)
    {
        // statistics1 : 1 hour walk, 50kg, 233KCalories
        // statistics2 : 68kg, 1h: 
        //                 255KC(slow walk, 4km/h), fact=0.64, =~= 2steps/s
        //                 555KC(fast walk, 8km/h), fact=0.69, =~= 4steps/s
        //                 655KC(slow run , 9km/h), fact=0.72
        //                 700KC(fast run, 12km/h)

        // method1 : 0.43*Height(cm)+0.57*weight(kg)+0.26*freq(steps/min)+0.92*time(min)-108.4
        // method2 : (weight(kg)/50) * 1.29 * (pulse(in G) * 2.2)
        // method3 : (weight(kg)/68) * (steplen(cm) * fact) * (1+sigmoid(pulseVal));

        int        iRet = 0, iTemp1;

        if (iPulseVal <=0) return 0;
        /*
    // method2
    iWeightFact = (pRec.m_iGender == HAR_GENDER_MALE) ? (60) : (50);
    iTemp1 = ((pRec.m_iWeight << 8) * 330 / iWeightFact);  // Q16 (1.29 * 256 = 330)
    iTemp2 = ((iPulseVal << 8) * 2509 / (100));        // Q16 (9.8 * 256 = 2509)
    iRet = (iTemp1 + iTemp2) >> 16;
         */
        // method3
        iTemp1 = GetSigmoidFact(pRec, iPulseVal);
        iRet = (pRec.m_iStepEnergyFixed * iTemp1) >> 16;
        // LOCAL_PRINT("pulse=%d, energy=%d  ", iPulseVal, iRet);

        return iRet;
    }

    /************************************************************************/
    /* exported functions                                                   */
    /************************************************************************/
    public static boolean  MR_InitDefault(HarRecord pRec)
    {
        return MR_Init(pRec, MR_DFT_NAME, MR_DFT_GENDER, MR_DFT_HEIGHT, 
                MR_DFT_WEIGHT, MR_DFT_GUINT, MR_DFT_FPS);
    }

    public static boolean MR_Init(HarRecord pRec, String szUserName, int nGender, 
            int nHeight, int nWeight, int nGUnit, int nFPS)
    {
        int        iMinHeight;
        int        iFact;

        if (pRec == null || szUserName == null) return false;

        pRec.m_szName = szUserName;
        pRec.m_iGender = nGender;
        pRec.m_iHeight = nHeight;
        pRec.m_iWeight = nWeight;
        pRec.m_iGUnit = nGUnit;
        pRec.m_iFPS = nFPS;

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
        MR_Reset(pRec);

        //    LOCAL_PRINT("HarRecOK,GUnit=%d,FPS=%d\n", pRec.m_iGUnit, pRec.m_iFPS);
        //    LOCAL_PRINT(" steplen=%d,energy=%d\n", pRec.m_iStepLen, (pRec.m_iStepEnergyFixed>>8));

        return true;
    }

    public static boolean  MR_Reset(HarRecord pRec)
    {
        if (pRec == null) return false;

        // clear the counters
        pRec.m_iEngergy = 0;
        pRec.m_nStepSaveCounter = 0;
        pRec.m_iStepCount = 0;
        for(int i=0;i<HAR_STEP_HIST_LEN;i++){
            pRec.m_iStepCountHist[i] = 0;        
        }

        return true;
    }

    public static boolean  MR_Update(HarRecord pRec, int iStepCount, int iPulseVal)
    {
        int        i;

        if (pRec == null) return false;

        if (iStepCount < pRec.m_iStepCount || iStepCount > pRec.m_iStepCount + 2)
        {
            MR_Reset(pRec);
        }

        pRec.m_iStepCount = iStepCount;

        if (++pRec.m_nStepSaveCounter >= pRec.m_iFPS)
        {
            for (i=HAR_STEP_HIST_LEN-1; i>=1; i--) pRec.m_iStepCountHist[i] = pRec.m_iStepCountHist[i-1];
            pRec.m_iStepCountHist[0] = (int)pRec.m_iStepCount;

            pRec.m_nStepSaveCounter = 0;
        }

        if (iPulseVal > 0)
        {
            pRec.m_iEngergy += CalcEngergy(pRec, iPulseVal);
        }

        return true;
    }

    public static int MR_GetDistance(HarRecord pRec)
    {
        if (pRec == null) return 0;
        return pRec.m_iStepCount * pRec.m_iStepLen;
    }

    public static int MR_GetTotalEnergy(HarRecord pRec)
    {
        if (pRec == null) return 0;
        return pRec.m_iEngergy;
    }

    public static int MR_GetVelocity(HarRecord pRec)
    {
        int     velocity;

        if (pRec == null) return 0;

        velocity  = (pRec.m_iStepCountHist[0] - pRec.m_iStepCountHist[HAR_STEP_HIST_LEN-1]);
        if (velocity < 0) velocity = 0;

        return (velocity * pRec.m_iStepLen / (HAR_STEP_HIST_LEN -1));
    }

    public static int MR_GetJumpHeight(HarRecord pRec, int iLWCount)
    {
        int        t;

        if (pRec == null || pRec.m_iFPS == 0) return 0;

        t = (iLWCount << 8) / pRec.m_iFPS;    // Q8

        t = (5 * t * t * 100) >> 16;    // 1/2 * g * t^2

        return t;
    }


    static public class ParameterLength{
        static public int STABLE_CHECK_LEN    =(30);
        static public int FILTER_GAUSS_LEN    =(41);
        static public int FILTER_MEAN_LEN    =(8);
        static public int PULSE_DETECT_LEN    =(128);
        static public int PULSE_ENSURE_LEN    =(48);
        static public int STEP_INTER_HIST_LEN =(4);
        static public int RAW_PLUSE_HIST_LEN  =(4);
    }

    /*=============================================================================*/
    static public class TMotionVar{
        public int        type;
        public int        prev_type;        // reset at the beginning in each frame
        public int        stat_count;
        public int        type_count;
        public int        total_count;
        public int        updated;
    }

    static public class TStepDelayShowVar{
        public byte      is_walking;     /* is walking or not */
        public byte      temp_counter;   /* save steps before walking, then move to main step counter */
        public short     score;          /* determine when to switch to "walking" state */
        public int     pulse_high;     /* Pulse is high enough to confide */
    }
    public static final int HAR_STEP_HIST_LEN        =3;
    public static final int HAR_SIGMOID_FLT_LEN     =16;

    public static class HarRecord{
        public String            m_szName;
        public int    m_iGender;
        public int    m_iHeight;
        public int    m_iWeight;
        public int    m_iFPS;
        public int    m_iGUnit;

        //    Float            m_fStepLen;        // in meter
        public int    m_iStepLen;     // in cm

        public int    m_nStepSaveCounter;
        //    Float            m_fEngergy;
        public int    m_iEngergy;

        public int    m_iStepCount;
        public int[]    m_iStepCountHist = new int [HAR_STEP_HIST_LEN];

        public int    m_iStepEnergyFixed; // In Q8
        public int[]    m_iSigmoidWin = new int[HAR_SIGMOID_FLT_LEN]; // In Q8
    }
}
