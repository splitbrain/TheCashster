package org.splitbrain.thecashster.model;

import com.google.android.gms.maps.model.LatLng;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Objects;

import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

import static java.lang.Math.round;

/**
 * Model for a single place. We keep used places stored locally for
 * fast display when using them again
 *
 * @author Andreas Gohr andi@splitbrain.org
 */
@SuppressWarnings("unused")
public class Place extends RealmObject {

    @PrimaryKey
    private String id = "";
    private String name = "";
    private String foursquare = "";
    private Double lat = 0.0;
    private Double lon = 0.0;
    @Index
    private Date lastused;
    private String address = "";
    private String category = "";

    @Ignore
    private boolean local = false;
    @Ignore
    private int distance = 0;
    @Ignore
    private String info = "";


    public Place() {
        lastused = new Date();
    }

    /**
     * Recalculates the primary key
     * <p>
     * The primary key is a MD5 hash of either the name or the foursquare identifier
     */
    private void updateId() {
        String s = name;
        if (!foursquare.isEmpty()) s = foursquare;

        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                StringBuilder h = new StringBuilder(Integer.toHexString(0xFF & aMessageDigest));
                while (h.length() < 2)
                    h.insert(0, "0");
                hexString.append(h);
            }
            id = hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            // should not happen
            e.printStackTrace();
        }

        lastused = new Date();
    }

    /**
     * Set the location in one go
     */
    public void setLatLng(LatLng ll) {
        this.lat = ll.latitude;
        this.lon = ll.longitude;
    }

    /**
     * Set the distance property by calculating it from the given point
     */
    public void setDistanceFrom(LatLng ll) {
        LatLng self = new LatLng(lat, lon);
        distance = calculateDistance(self, ll);
    }

    /**
     * Calculate the distance between the given points in meters
     *
     * @link https://stackoverflow.com/a/8550740/172068
     */
    private static int calculateDistance(LatLng ll1, LatLng ll2) {
        final int earthRadius = 6371;

        float dLat = (float) Math.toRadians(ll2.latitude - ll1.latitude);
        float dLon = (float) Math.toRadians(ll2.longitude - ll1.longitude);
        float a =
                (float) (Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(ll1.latitude))
                        * Math.cos(Math.toRadians(ll2.latitude)) * Math.sin(dLon / 2) * Math.sin(dLon / 2));
        float c = (float) (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
        return round(earthRadius * c * 1000);
    }

    /**
     * set the last used date to now
     */
    public void updateLastUsed() {
        lastused = new Date();
    }

    // region Default Setter/Getter

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        updateId();
    }

    public String getFoursquare() {
        return foursquare;
    }

    public void setFoursquare(String foursquare) {
        this.foursquare = foursquare;
        updateId();
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Date getLastused() {
        return lastused;
    }

    public void setLastused(Date lastused) {
        this.lastused = lastused;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isLocal() {
        return local;
    }

    @SuppressWarnings("SameParameterValue")
    public void setLocal(boolean local) {
        this.local = local;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    // endregion

    // region Behaviour overrides

    /**
     * Check if this place equals another based on the primary key
     *
     * @param obj the comparision place
     * @return true if the primary keys are the same
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof Place)) return false;
        Place place = (Place) obj;
        return !place.getId().equals("") &&
                !this.getId().equals("") &&
                place.getId().equals(this.getId());

    }

    /**
     * @return hash based on primary key
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // endregion
}
