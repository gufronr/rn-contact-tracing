package com.wix.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;

import com.google.gson.annotations.SerializedName;
import com.wix.crypto.custom.Pair;
import com.wix.crypto.custom.Triplet;
import com.wix.crypto.key.DayKey;
import com.wix.crypto.key.EpochKey;
import com.wix.crypto.key.UserKey;
import com.wix.crypto.utilities.BytesUtils;
import com.wix.crypto.utilities.DerivationUtils;
import com.wix.crypto.utilities.Hex;
import com.wix.specialble.db.DBClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by hagai on 11/05/2020.
 */
public class User {

    public static final String USER_PREFS = "user_prefs";
    public static final String PREFS_KEY = "user_data";
    private static final String TAG = "User";

    @SerializedName("UserId")
    private byte[] mUserId;
    @SerializedName("KeyId")
    private byte[] mKeyId;
    @SerializedName("KeyMasterCommitment")
    private byte[] mKeyMasterCommitment;
    @SerializedName("KeyMasterVerification")
    private byte[] mKeyMasterVerification;
    @SerializedName("EpochKeys")
    private Map<Time, EpochKey> mEpochKeys;
    @SerializedName("CurrentDay")
    private int mCurrentDay;
    @SerializedName("CurrentDayMasterKey")
    private byte[] mCurrentDayMasterKey;

    // this should be db based data
//    @Expose(serialize = false)
//    private List<Contact> mContacts; // not serializable

    DBClient dbClient;
    SharedPreferences prefs;

    public User() {}

    public User(byte[] userId, byte[] masterKey, int initTime, Context ctx)
    {
        setContext(ctx);
        mUserId = userId;
        mKeyId = DerivationUtils.getKeyId(masterKey);
        mKeyMasterCommitment = DerivationUtils.getMasterKeyCommitment(mKeyId, userId);
        mKeyMasterVerification = DerivationUtils.getKeyMasterVerification(masterKey);
        mEpochKeys = new HashMap<>();
//        mContacts = new ArrayList<>();
        Time t = new Time(initTime, Constants.None); //TODO: check if correct way
        mCurrentDay = t.getDay();
        mCurrentDayMasterKey = DerivationUtils.getNextDayMasterKey(masterKey, true);

        generateEpochKeys(mCurrentDay);
        serialize();
    }

    public void setContext(Context ctx)
    {
        prefs = ctx.getSharedPreferences(USER_PREFS, MODE_PRIVATE);
        dbClient = DBClient.getInstance(ctx);
    }


    public static User deserialize(Context ctx)
    {
        SharedPreferences prefs = ctx.getSharedPreferences(USER_PREFS, MODE_PRIVATE);
        String userData = prefs.getString(PREFS_KEY, null);


        if(userData != null)
        {
            User u = new User();
            try
            {
                u.setContext(ctx);

                JSONObject jo = new JSONObject(userData);
                u.mUserId =  Hex.fromHexString(jo.getString("mUserId"));

                u.mKeyId =  Hex.fromHexString(jo.getString("mKeyId"));
                u.mKeyMasterCommitment =  Hex.fromHexString(jo.getString("mKeyMasterCommitment"));
                u.mKeyMasterVerification =  Hex.fromHexString(jo.getString("mKeyMasterVerification"));

                u.mEpochKeys = new HashMap<>();

                JSONArray epochKeysMap = jo.getJSONArray("mEpochKeys");

                for (int i =0; i < epochKeysMap.length(); i++)
                {
                    JSONObject curEpoch = epochKeysMap.getJSONObject(i);
                    Time t = Time.fromJson(curEpoch.getJSONObject("Time"));
                    EpochKey k = EpochKey.fromJson(curEpoch.getJSONObject("EpochKey"));
                    u.mEpochKeys.put(t, k);
                }
                u.mCurrentDay = jo.getInt("mCurrentDay");
                u.mCurrentDayMasterKey =  Hex.fromHexString(jo.getString("mCurrentDayMasterKey"));
            }
            catch (JSONException e)
            {
                e.printStackTrace();
            }
            return u;

        }

        return null;
    }

    public void serialize()
    {
        JSONObject jo = new JSONObject();
        try
        {
            jo.put("mUserId", Hex.toHexString(mUserId));
            jo.put("mKeyId", Hex.toHexString(mKeyId));

            jo.put("mKeyMasterCommitment", Hex.toHexString(mKeyMasterCommitment));

            jo.put("mKeyMasterVerification", Hex.toHexString(mKeyMasterVerification));



            JSONArray epochKeysMap = new JSONArray();

            for (Time t : mEpochKeys.keySet())
            {

                JSONObject curEpoch = new JSONObject();
                curEpoch.put("Time", t.toJson());
                curEpoch.put("EpochKey", mEpochKeys.get(t).toJson());

                epochKeysMap.put(curEpoch);

            }

            jo.put("mEpochKeys", epochKeysMap);


            jo.put("mCurrentDay", mCurrentDay);
            jo.put("mCurrentDayMasterKey", Hex.toHexString(mCurrentDayMasterKey));

        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(PREFS_KEY, jo.toString());
        prefsEditor.apply();
    }





    /**
     * Update the key database to store key's of specific time intreval.
     * To protect the user's privacy, all information (including contact information)
     * older than past_time will be deleted.
     *
     * @param pastTime - start time to store keys(should be a time from the past).
    All stored keys before past_time will be deleted.
     * @param futureTime - end time to store keys(should be a time from the future).
     */
    public void updateKeyDatabase(int pastTime, int futureTime) {

        Time past = new Time(pastTime, Constants.None); // TODO: check if correct
        Time future = new Time(futureTime, Constants.None); // TODE: check if correct

        generateEpochKeys(future.getDay());
        deleteHistory(past.getTime());
        serialize();
    }

    /**
     * Generate the ephemeral id of a specific time. User should have the right epoch keys.
     *
     * @param time - current time.
     * @param geoHash - current location.
     * @return - The ephemeral id.
     *
     * Raises an error if epoch keys are not present.
     */
    public byte[] generateEphemeralId(int time, byte[] geoHash)
    {

        assert geoHash.length == Constants.GEOHASH_LEN;
        Time t = new Time(time, Constants.None); // TODO: check if correct

        EpochKey epochKey = null;
        if(!mEpochKeys.containsKey(t) && mEpochKeys.get(t) != null)// "Epoch key is not present";
        {
            int timeStamp = (int)(System.currentTimeMillis() / 1000);
            this.updateKeyDatabase(timeStamp - 14 * 24 * 3600, timeStamp + 24*3600);
        }

        epochKey = mEpochKeys.get(t);
        int tUnitS = t.getUnits();

        byte[] mask = Crypto.AES(epochKey.getEpochEncKey(), BytesUtils.numToBytes(tUnitS, Constants.MESSAGE_LEN));
        byte[] userRand = Arrays.copyOf(epochKey.getEpochVerKey(), Constants.USER_RAND_LEN);

        byte[] zeroBytesThree = new byte[] {0x00, 0x00, 0x00};
        byte[] zeroBytesFour = new byte[]  {0x00, 0x00, 0x00, 0x00};

        byte[] plain = BytesUtils.byteConcatenation(BytesUtils.byteConcatenation(zeroBytesThree, geoHash),BytesUtils.byteConcatenation(userRand, zeroBytesFour));

        byte[] cIJS = BytesUtils.xor(plain, mask);

        return BytesUtils.byteConcatenation(Arrays.copyOf(cIJS, 12),Arrays.copyOf(Crypto.AES(epochKey.getEpocMacKey(),cIJS),4));
    }

    /**
     * Check for matching with the infected user.
     * @param infectedKeyDatabase
     * @return -  List of matches with the infected user.
     */
    public List<Match> findCryptoMatches(Map<Integer, Map<Integer, ArrayList<byte[]>>> infectedKeyDatabase) {

        List<Match> matches = new ArrayList<>();

        // Make sure the contacts are sorted so as to make the sliding window work properly
//        Collections.sort(mContacts, new Comparator<Contact>() {
//
//            @Override
//            public int compare(Contact o1, Contact o2) {
//
//                return o1.getTimestamp() - o2.getTimestamp(); // TODO: check if correct comparisson
//            }
//        });

        // domain is a time up to units (actually a string "day-epoch-unit")
        // and its range is a list of (mask, epochMAC)
        Map<String, ArrayList<Pair<byte[], byte[]>>> mapUnitKeys = new HashMap<>();

        int earlierTime = Constants.None;

        Cursor c = dbClient.getCursorAll();
        while (c.moveToNext())
        {
            Contact contact = new Contact
            (
                    c.getBlob(c.getColumnIndex("ephemeral_id")),
                    c.getBlob(c.getColumnIndex("rssi")),
                    c.getInt(c.getColumnIndex("timestamp")),
                    c.getBlob(c.getColumnIndex("geohash"))
            );

            int time = contact.getTimestamp() - Time.JITTER_THRESHOLD;

            // Remove all entries unit_keys[t] for t < time (will save memory usage)
            // For it to work we need self.contact to be ordered by contact.time

            if(earlierTime != Constants.None) {

                while(earlierTime < time) {

                    String timeDictKey = new Time(earlierTime, Constants.None).getStrWithUnits();
                    if(mapUnitKeys.containsKey(timeDictKey)) {

                        mapUnitKeys.remove(timeDictKey);
                    }
                    earlierTime += Time.UNIT;

                }
            }
            else {
                earlierTime = time;
            }

            while(time <= contact.getTimestamp() + Time.JITTER_THRESHOLD)
            {

                Time t = new Time(time, Constants.None);
                String timeKey = t.getStrWithUnits();
                int unit = t.getUnits();
                time += Time.UNIT;

                if(!mapUnitKeys.containsKey(timeKey)) {

                    mapUnitKeys.put(timeKey, new ArrayList<Pair<byte[], byte[]>>());
                    if(infectedKeyDatabase.get(t.getDay()) != null && infectedKeyDatabase.get(t.getDay()).get(t.getEpoch()) != null )
                    {
                        for (int i = 0; i < infectedKeyDatabase.get(t.getDay()).get(t.getEpoch()).size(); i++) {
                            byte[] epochKey = infectedKeyDatabase.get(t.getDay()).get(t.getEpoch()).get(i);

                            Pair<byte[], byte[]> epochEncAndMac = DerivationUtils.getEpochKeys(epochKey, t.getDay(), t.getEpoch());
                            byte[] mask = Crypto.AES(epochEncAndMac.getFirst(), BytesUtils.numToBytes(unit, Constants.MESSAGE_LEN));
                            mapUnitKeys.get(timeKey).add(new Pair<>(mask, epochEncAndMac.getSecond()));
                        }
                    }
                }

                for ( Pair<byte[], byte[]> entry : mapUnitKeys.get(timeKey)) {
                    Triplet<Boolean, byte[],byte[]> match = isMatch(entry.getFirst(), entry.getSecond(),contact);
                    if(match.getFirst())
                    {
                        matches.add(new Match(contact,match.getSecond(),match.getThird(),t,unit));
                    }
                }
            }

        }
        return matches;
    }

    /**
     * Delete my keys in a time period.
     * @param startTime - start time of period to delete.
     * @param endTime - end time of period to delete.
     *
     * Note: only deleting key and not contacts.
     */
    public void deleteMyKeys(int startTime, int endTime) {

        Time start = new Time(startTime,Constants.None); //TODO: check if correct
        Time end = new Time(endTime, Constants.None); //TODO: check if correct

        List<Time> epochKeysToDelete = new ArrayList<>();
        for(Time t : mEpochKeys.keySet()) {
            if(t.largerEqualThan(start) && end.largerEqualThan(t)) {
                epochKeysToDelete.add(t);
            }
        }

        for(Time t : epochKeysToDelete) {

            mEpochKeys.remove(t);
        }
        serialize();
    }

    /**
     * Get a willing infected user keys.
     * The keys will be sent to all user by the server.
     *
     * @return - Keys to be sent to the server.
     */
    public UserKey getKeysForServer() {

        List<Triplet<Integer, Integer, byte[]>> epochs = new ArrayList<>();

        for(Time t : mEpochKeys.keySet()) {
            Triplet<Integer, Integer, byte[]> triplet = new Triplet<Integer,Integer,byte[]>(t.getDay(), t.getEpoch(), mEpochKeys.get(t).getPreKey());
            epochs.add(triplet);
        }

        UserKey keys = new UserKey(mUserId, mKeyId, epochs, mKeyMasterVerification);
        serialize();

        return keys;
    }

    /**
     * Store an ephemeral id from BLE received in the wild.
     *
     * @param otherEphemeralId -  other user ephemeral id.
     * @param rssi - ?
     * @param time - current time.
     * @param ownLocation - current location
     * @return
     */
    public boolean storeContact(byte[] otherEphemeralId, byte[] rssi, int time, byte[] ownLocation) {

//        if (mContacts.size() >= Time.MAX_CONTACTS_IN_WINDOW) {
//            int pastContactTime = mContacts.get(mContacts.size() - Time.MAX_CONTACTS_IN_WINDOW).getTimestamp();
//
//            if(time - pastContactTime < Time.WINDOW)
//                // If there have been too many contacts in this epoch, ignore this contact.
//                return false;
//        }
//
//
        dbClient.storeContact(new Contact(otherEphemeralId, rssi, time, ownLocation));
        return true;
    }

    /**
     * deletes a contact from local contact DB.
     *
     * @param contact - Contact to delete.
     */
    public void deleteContact(Contact contact) {
//
//        //TODO: check if correct impl
//
//        List<Contact> contacts = new ArrayList<>();
//
//        for(Contact c : mContacts) {
//
//            if(c.equals(contact)) {
//                contacts.add(c);
//            }
//        }
//
//        mContacts = contacts;

        dbClient.delete(contact);

    }

    /**
     * Delete all history before a specific time.
     * Should be used also to delete days that are more then 14 days in the past.
     *
     * @param dTime - time to delete history from
     *
     * Note -  deleting all keys and contacts.
     */
    public void deleteHistory(int dTime)
    {
        Time t = new Time(dTime, Constants.None);
        Map<Time, EpochKey> dictEpochKeys = new HashMap<>();

        for(Map.Entry<Time, EpochKey> entry : mEpochKeys.entrySet()) {

            if( entry.getKey().compareTo(t) >= 0) {
                dictEpochKeys.put(entry.getKey(), entry.getValue());
            }
        }
        mEpochKeys = dictEpochKeys;
        serialize();

        dbClient.deleteContactHistory(dTime);
    }

    /**
     * Makes sure the user has all epoch keys corresponding to a given day
     *
     * @param targetDay - day to update.
     */
    private void generateEpochKeys(int targetDay) {

        Log.e("hagai", "generateEpochKeys: ");

        boolean keysPresent = true;
        for(int epoch = 0; epoch < Time.EPOCHS_IN_DAY; epoch ++) {

            Time t = new Time(targetDay, epoch);
            if(!mEpochKeys.containsKey(t)){
                keysPresent = false;
                break;
            }
        }

        if(keysPresent)
            return;

        assert mCurrentDay <= targetDay : "Cannot retrieve keys from the past";
        while (mCurrentDay <= targetDay) {

            DayKey currentDayKey = new DayKey(mCurrentDay, mCurrentDayMasterKey, mKeyMasterVerification, mKeyMasterCommitment);

            for(int i = 0; i < Time.EPOCHS_IN_DAY; i ++) { // iterate over the epochs of each day

                mEpochKeys.put(new Time(mCurrentDay, i), new EpochKey(mCurrentDay, i, currentDayKey));
            }

            mCurrentDay ++;
            mCurrentDayMasterKey = DerivationUtils.getNextDayMasterKey(mCurrentDayMasterKey, false);
        }
        serialize();
    }

    private Triplet<Boolean, byte[], byte[]> isMatch (byte[] mask, byte[] epochMac, Contact contact) {

        byte[] ephId = contact.getEphemeral_id();
        byte[] plain = BytesUtils.xor(mask, ephId);
        byte[] zeros = Arrays.copyOf(plain, 3);
        byte[] ephidGeohash = Arrays.copyOfRange(plain, 3, 3 + Constants.GEOHASH_LEN);
        byte[] ephIdUserRand = Arrays.copyOfRange(plain, 3 + Constants.GEOHASH_LEN, 3 + Constants.GEOHASH_LEN + Constants.USER_RAND_LEN);

        // First three bytes of plaintext are zero
        for(byte b : zeros) {

            if ( b != 0x00 ) {

                return new Triplet<>(false, new byte[]{0x00}, new byte[]{0x00});
            }
        }

        // The application check the part of EphID which corresponds to the MAC (Matched Occured)

        byte[] x = BytesUtils.byteConcatenation(Arrays.copyOf(ephId, ephId.length - 4), Arrays.copyOfRange(mask,mask.length-4,mask.length));
        byte[] y = Arrays.copyOfRange(ephId, ephId.length - 4,ephId.length);

        if(Arrays.equals(y,Arrays.copyOf(Crypto.AES(epochMac, x), 4)))
        {
            return new Triplet<>(true, ephidGeohash, ephIdUserRand);
        }

        return new Triplet<>(false, new byte[]{0x00}, new byte[]{0x00});
    }

    public byte[] getUserId() { return mUserId; }

    public byte[] getKeyId() { return mKeyId; }

    public byte[] getKeyMasterCommitment() { return mKeyMasterCommitment; }

    public byte[] getKeyMasterVerification() { return mKeyMasterVerification; }

    public Map<Time, EpochKey> getEpochKeys() { return mEpochKeys; }

    public List<Contact> getContacts() { return dbClient.getAllContacts(); }

    public int getCurrentDay() { return mCurrentDay; }

    public byte[] getCurrentDayMasterKey() { return mCurrentDayMasterKey; }

}