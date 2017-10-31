package org.splitbrain.thecashster.model;

import java.util.Date;

/**
 * @author Andreas Gohr andi@splitbrain.org
 */

public class Transaction {
    private Date dt;
    private float amount;
    private String place;
    private String placeId;

    public Transaction() {
        dt = new Date();
    }

    public Transaction(float amount) {
        this.amount = amount;
    }

    public Transaction(float amount, String place) {
        this.amount = amount;
        this.place = place;
    }

    public Transaction(float amount, String place, String placeId) {
        this.amount = amount;
        this.place = place;
        this.placeId = placeId;
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

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public String getPlaceId() {
        return placeId;
    }

    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }
}
