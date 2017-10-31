package org.splitbrain.thecashster.model;

import java.util.Date;
import java.util.UUID;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

/**
 * Model for a transaction
 * <p>
 * This is stored locally until it has been transferred to Google Sheets
 *
 * @author Andreas Gohr andi@splitbrain.org
 */
@SuppressWarnings("unused")
public class Transaction extends RealmObject {
    @PrimaryKey
    private String txid;
    private Date dt;
    private float amount = 0.0f;
    private Place place;

    /**
     * Constructor
     * <p>
     * This is need for the Realm's sake
     */
    public Transaction() {
        this.txid = UUID.randomUUID().toString();
        this.dt = new Date();
    }

    /**
     * Constructor
     * <p>
     * Preferred way to create a new transaction
     */
    public Transaction(float amount, Place place) {
        this.txid = UUID.randomUUID().toString();
        this.amount = amount;
        this.place = place;
        this.dt = new Date();
    }

    // region default Setter/Getters

    public String getTxid() {
        return txid;
    }

    public Date getDt() {
        return dt;
    }

    public void setDt(Date dt) {
        this.dt = dt;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }

    public Place getPlace() {
        return place;
    }

    public void setPlace(Place place) {
        this.place = place;
    }

    // endregion
}
