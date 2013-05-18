package com.colorcloud.movementsensor.walksensor;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.lang.Byte;
import java.util.StringTokenizer;

public class TestMain {

    /**
     * @param args
     */

    private final int DFT_GRAVITY     =(8192);
    private final int DFT_FPS         =(20);


    int    g_frame_no = 0;
    Engine         g_hareng = null;
    Engine.HarConfig      g_harcfg = null;
    Recognizer recognizer = null;
    int    g_fps = DFT_FPS;
    int    g_gravity = DFT_GRAVITY;
    int             g_predef_fps = 0;
    int             g_resamp_max = 0;
    int             g_resamp_ctr = 0;
    int             g_thres = -1;
    int             g_samp_range = 0;
    int             g_bit_1g = 0;


    int NormPrec(int nVal)
    {
        int nNeg;
        if (nVal >= Short.MAX_VALUE) { nVal -= Short.MAX_VALUE; nNeg = 0; }
        else { nVal = Short.MAX_VALUE- nVal; nNeg = 1; }

        if (g_samp_range > 0)
        {
            if (nVal > g_samp_range * g_gravity) nVal = g_samp_range * g_gravity;
        }
        if (g_bit_1g > 0)
        {
            nVal = StepCounter.HAR_SFT_INT(((nVal << 13) / g_gravity), (13 - g_bit_1g));
            nVal = (nVal << (13 - g_bit_1g));
            nVal = StepCounter.HAR_SFT_INT(nVal * g_gravity, 13);
        }

        return ((nNeg != 0 )?(Short.MAX_VALUE - nVal):(Short.MAX_VALUE + nVal));
    }

    int InitHarEngine()
    {

        // calc re-sample counter for pre-defined fps
        if (g_predef_fps > 0 && g_predef_fps < (int)g_fps)
        {
            g_resamp_max = ((g_fps << 8) / g_predef_fps + 128) >> 8;
            g_resamp_ctr = 0;
        }
        else
        {
            g_resamp_max = 1;
            g_resamp_ctr = 0;
        }

        // init engine
        recognizer.HarInit(g_hareng, g_gravity, g_fps/g_resamp_max);
        if (g_hareng == null)
        { 
            //printf("HarInit() failed, GUint=%d, fps=%d\n", g_gravity, g_fps/g_resamp_max);
            return 0; 
        }

        if (g_thres > 0)
        {
            recognizer.HarSetGlobalThres(g_hareng, g_thres);
        }

        recognizer.HarSetConfig(g_hareng, g_harcfg);

        return 1;
    }

    int[][] jump_data = new int[][]{{0,0,10},
            {0,0,11},
            {0,0,12},
            {0,0,13},
            {0,0,14},
            {0,0,15},
            {0,0,16},
            {0,0,17},
            {0,0,18},
            {0,0,18},
            {0,0,17},
            {0,0,16},
            {0,0,15},
            {0,0,14},
            {0,0,13},
            {0,0,12},
            {0,0,11},
            {0,0,10}};

    
    public void prepareEngine() {

        g_hareng = new Engine();
        g_harcfg = new Engine.HarConfig();


        g_harcfg.szUserName = "user";
        g_harcfg.nUserGender = Engine.HAR_GENDER_MALE;
        g_harcfg.nUserHeight = 68;
        g_harcfg.nUserWeight = 150;
        g_harcfg.nFlags |= Engine.HAR_CFG_CHK_STEP_STABLE;
        g_harcfg.nFlags |= Engine.HAR_CFG_CHK_RAW_PULSE;
        g_harcfg.nFlags |= Engine.HAR_CFG_DELAY_SHOW_STEP;
        g_harcfg.nFlags |= Engine.HAR_CFG_CALC_PEDOMETER;
        g_harcfg.nFlags |= Engine.HAR_CFG_CALC_ENERGY;
        g_thres = 180;
        g_predef_fps = 200;
        g_samp_range = 10;
        g_bit_1g = 10;

        recognizer = new Recognizer(); 
        InitHarEngine();

    }

    public int feedData(float[] data){

        int[] values  = new int[3];
        for( int i = 0; i<3;i++){
            values[i] = (int)(data[i]*1000);
        }

        //recognizer.FeedData(g_hareng, (long)(values[0]), 
        //        (values[1]),
        //        (values[2]));

        recognizer.HarCalcRecord(g_hareng);
        Engine.HarResult result =recognizer.HarGetResult(g_hareng);
        return result.nCurMotion;
    }
}
