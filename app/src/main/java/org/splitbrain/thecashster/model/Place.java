package org.splitbrain.thecashster.model;

import com.google.android.gms.maps.model.LatLng;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Objects;

import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

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
    private String foreign = "";
    private Double lat = 0.0;
    private Double lon = 0.0;
    @Index
    private Date lastused;
    private String info = "";


    public Place() {
        lastused = new Date();
    }

    /**
     * Recalculates the primary key
     * <p>
     * The primary key is a MD5 hash of either the name or the foreign identifier
     */
    private void updateId() {
        String s = name;
        if (!foreign.isEmpty()) s = foreign;

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

    public String getForeign() {
        return foreign;
    }

    public void setForeign(String foreign) {
        this.foreign = foreign;
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

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public Date getLastused() {
        return lastused;
    }

    public void setLastused(Date lastused) {
        this.lastused = lastused;
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
