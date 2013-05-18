package com.colorcloud.movementsensor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.colorcloud.movementsensor.MovementSensorApp.MSAppLog;


/**
 *<code><pre>
 * CLASS:
 *  Util functions to handle JSON document.
 *
 * RESPONSIBILITIES:
 * 	encapsulate, persistance, instantiation of JSON objects.
 *
 * COLABORATORS:
 *
 * USAGE:
 * 	See each method.
 *
 *</pre></code>
 */

public class JSONUtils {
    public static final String TAG = "LSAPP_UtilsJSON";

    /**
     * find whether a json array contains a json object with certain key.
     * the comparison is based on the string value of passed in key. If key is not provided, use the entire object's string value.
     * return true if found, false otherwise.
     * wifissid=[{"wifibssid":"00:14:6c:14:ec:fa","wifissid":"PInternet"},{...}, ... ]
     */
    public static boolean findJSONObject(JSONArray jsonarray, JSONObject jsonobj, String key) {
        String objstr = null;
        if (key == null) {
            objstr = jsonobj.toString();
        } else {
            try {
                objstr = jsonobj.getString(key);
            } catch (JSONException e) {
                objstr = null;
                MSAppLog.e(TAG, "findJSONObject:  get key Exception: " + e.toString());
            }
        }

        // java is f* verbose...no expressive power!
        if (objstr != null) {
            objstr = objstr.trim();
            if (objstr.length() == 0) {
                return false;
            }
        } else {
            MSAppLog.d(TAG, "findJSONObject:  empty key string! no found. ");
            return false;
        }

        int size = jsonarray.length();
        JSONObject entry = null;
        String entrystr = null;
        for (int i=0; i<size; i++) {
            try {
                entry = jsonarray.getJSONObject(i);
                if (key == null) {
                    entrystr = entry.toString();
                } else {
                    entrystr = entry.getString(key);
                }
            } catch (JSONException e) {
                MSAppLog.e(TAG, "findJSONObject: getJSONObject Exception: " + e.toString());
                continue;
            }

            if (entrystr != null) {
                entrystr = entrystr.trim();
            }

            if (objstr.equals(entrystr)) {
                MSAppLog.d(TAG, "findJSONObject: match :" + objstr);
                return true;   // return immediately
            }
        }
        return false;
    }

    /**
     * convert json array string to jsonarray
     */
    public static JSONArray getJsonArray(String jsonstr) {
        JSONArray curjsons = null;
        if (jsonstr == null) {
            return null;
        }
        try {
            curjsons = new JSONArray(jsonstr);  // convert string back to json array
        } catch (JSONException e) {
            MSAppLog.e(TAG, "getJSONArray:" + e.toString());
        }
        return curjsons;
    }

    /**
     * merge the new json array into the existing json array.
     * @return existing json array with newjson array added
     */
    public static JSONArray mergeJsonArrays(JSONArray existingjsons, JSONArray newjsons) {
        if (existingjsons == null)
            return newjsons;
        if (newjsons == null)
            return existingjsons;

        JSONObject newobj = null;
        for (int i=0; i<newjsons.length(); i++) {
            try {
                newobj = newjsons.getJSONObject(i);
            } catch (JSONException e) {
                MSAppLog.e(TAG, "mergeJSONArrays: getJSONObject Exception: " + e.toString());
                continue;
            }

            if (!findJSONObject(existingjsons, newobj, null)) {  // not a set, need to prevent dup
                existingjsons.put(newobj);
            }
        }
        return existingjsons;
    }

    /**
     * merge two jsonarray string and return one json array string
     * curstr is the current json array in string format, newstr is the to be merged json array in string format.
     */
    public static String mergeJsonArrayStrings(String curstr, String newstr) {
        JSONArray curjsons = null;
        JSONArray newjsons = null;

        MSAppLog.d(TAG, "mergeJSONArrays:" + curstr + " =+= " + newstr);

        // merge shortcut, if either one is null, return the other.
        if (curstr == null)
            return newstr;
        if (newstr == null)
            return curstr;

        try {
            curjsons = new JSONArray(curstr);  // convert string back to json array
            newjsons = new JSONArray(newstr);
        } catch (JSONException e) {
            MSAppLog.e(TAG, "mergeJSONArrays:" + e.toString());
            return curstr;   // return the original curstr, no merge.
        }

        JSONObject newobj = null;
        for (int i=0; i<newjsons.length(); i++) {
            try {
                newobj = newjsons.getJSONObject(i);
            } catch (JSONException e) {
                MSAppLog.e(TAG, "mergeJSONArrays: getJSONObject Exception: " + e.toString());
                continue;
            }

            if (!findJSONObject(curjsons, newobj, null)) {
                curjsons.put(newobj);
            }
        }

        return curjsons.toString();
    }

    /**
     * fuzzy match whether runtime cur wifi ssid jsonarray matches to static db wifi ssid jsonarray
     * match criteria : turn to positive if single match exist. can be more sophisticated.
     * wifissid=[{"wifibssid":"00:14:6c:14:ec:fa","wifissid":"PInternet"},{...}, ... ]
     * @param dbJsonStr  static db set
     * @param curJsonStr runtime current set
     * @return true if two array has common object, false otherwise.
     */
    public static boolean fuzzyMatchJsonArrays(String dbJsonStr, String curJsonStr, String key) {
        MSAppLog.d(TAG, "fuzzyMatchJSONArrays : dbsdbjsonstret : " + dbJsonStr + " : curjsonstr :" +curJsonStr);
        if (dbJsonStr == null || curJsonStr == null) {
            return false;    // no match if either of them is null.
        }

        JSONArray dbjsons = null;
        JSONArray curjsons = null;
        try {
            dbjsons = new JSONArray(dbJsonStr);  // convert string back to json array
            curjsons = new JSONArray(curJsonStr);
        } catch (JSONException e) {
            MSAppLog.e(TAG, "mergeJSONArrays:" + e.toString());
            return false;   // no merge if either is wrong
        }

        boolean match = false;
        JSONObject curobj = null;
        for (int i=0; i<curjsons.length(); i++) {
            try {
                curobj = curjsons.getJSONObject(i);
            } catch (JSONException e) {
                MSAppLog.e(TAG, "mergeJSONArrays: getJSONObject Exception: " + e.toString());
                continue;  // skip this entry if can not construct object.
            }

            if (findJSONObject(dbjsons, curobj, key)) {
                match = true;
                break;
            }
        }
        return match;
    }

    /**
     * get set of values from JSONArray with key, if key is null, get the string of each entire json object.
     * when you are using json, you are dealing with immutable string, no need Generic.
     * @return a set of values
     */
    public static Set<String> getValueSetFromJsonArray(JSONArray jsonarray, String key) {
        Set<String> valset = new HashSet<String>();
        if (jsonarray == null) {
            return valset;
        }

        JSONObject curobj = null;
        String valstr = null;
        for (int i=0; i<jsonarray.length(); i++) {
            try {
                curobj = jsonarray.getJSONObject(i);
                if (key == null) {
                    valstr = curobj.toString();
                } else {
                    valstr = curobj.getString(key);
                }
                valset.add(valstr);
                MSAppLog.d(TAG, "getValueSetFromJSONArray: " + valstr);
            } catch (JSONException e) {
                MSAppLog.e(TAG, "getValueSetFromJSONArray: Exception: " + e.toString());
                continue;  // skip this entry if can not construct object.
            }
        }
        return valset;
    }

    /**
     * convert json object array to hash map with key is bssid and val is ssid. Json Array string format as follows.
     *   wifissid=[{"wifibssid":"00:14:6c:14:ec:fa","wifissid":"PInternet"}, {...}, ... ]
     * @param outmap is defined by the caller and must not be null, contains the converted map
     * @param bagssid is defined by the caller and must not be null, contains the dup ssid name set.
     */
    public static void convertJSonArrayToMap(String ssidjsonarray, String mapkey, String mapval, Map<String, String> outmap, Set<String> bagssid) {
        JSONArray jsonarray = JSONUtils.getJsonArray(ssidjsonarray);

        if (jsonarray == null || outmap == null) {
            MSAppLog.d(TAG, "convertJSonArrayToMap: null jsonarray or map, return null");
            return;
        }

        JSONObject curobj = null;
        for (int i=0; i<jsonarray.length(); i++) {
            try {
                curobj = jsonarray.getJSONObject(i);
                if (outmap.containsValue(curobj.getString(mapval))) {  // populate bag ssid if exist.
                    bagssid.add(curobj.getString(mapval));
                }
                outmap.put(curobj.getString(mapkey), curobj.getString(mapval));
                MSAppLog.d(TAG, "convertJSonArrayToMap: " + curobj.getString(mapkey) + " :: " + curobj.getString(mapval));
            } catch (JSONException e) {
                MSAppLog.e(TAG, "convertJSonArrayToMap: Exception: " + e.toString());
                continue;  // skip this entry if can not construct object.
            }
        }
        return;
    }
}
