/*
 * Generated by Abacus.
 */
package com.landawn.abacus.entity;

import java.util.List;
import java.sql.Timestamp;
import com.landawn.abacus.util.N;
import com.landawn.abacus.entity.CodesPNL;


/**
 * Generated by Abacus. DO NOT edit it!
 * @version ${version}
 */
public class Account implements CodesPNL.AccountPNL {
    private long id;
    private String gui;
    private String firstName;
    private String lastName;
    private int status;
    private Timestamp lastUpdateTime;
    private Timestamp createTime;
    private List<AccountDevice> devices;

    public Account() {
    }

    public Account(long id) {
        this();

        setId(id);
    }

    public Account(String gui, String firstName, String lastName, int status, 
        Timestamp lastUpdateTime, Timestamp createTime, 
        List<AccountDevice> devices) {
        this();

        setGUI(gui);
        setFirstName(firstName);
        setLastName(lastName);
        setStatus(status);
        setLastUpdateTime(lastUpdateTime);
        setCreateTime(createTime);
        setDevices(devices);
    }

    public Account(long id, String gui, String firstName, String lastName, int status, 
        Timestamp lastUpdateTime, Timestamp createTime, 
        List<AccountDevice> devices) {
        this();

        setId(id);
        setGUI(gui);
        setFirstName(firstName);
        setLastName(lastName);
        setStatus(status);
        setLastUpdateTime(lastUpdateTime);
        setCreateTime(createTime);
        setDevices(devices);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getGUI() {
        return gui;
    }

    public void setGUI(String gui) {
        this.gui = gui;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Timestamp getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Timestamp lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    public List<AccountDevice> getDevices() {
        return devices;
    }

    public void setDevices(List<AccountDevice> devices) {
        this.devices = devices;
    }

    public Account copy() {
        Account copy = new Account();

        copy.id = this.id;
        copy.gui = this.gui;
        copy.firstName = this.firstName;
        copy.lastName = this.lastName;
        copy.status = this.status;
        copy.lastUpdateTime = this.lastUpdateTime;
        copy.createTime = this.createTime;
        copy.devices = this.devices;

        return copy;
    }

    @Override
    public int hashCode() {
        int h = 17;
        h = 31 * h + N.hashCode(id);
        h = 31 * h + N.hashCode(gui);
        h = 31 * h + N.hashCode(firstName);
        h = 31 * h + N.hashCode(lastName);
        h = 31 * h + N.hashCode(status);
        h = 31 * h + N.hashCode(lastUpdateTime);
        h = 31 * h + N.hashCode(createTime);
        h = 31 * h + N.hashCode(devices);

        return h;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof Account) {
            Account other = (Account) obj;

            if (N.equals(id, other.id)
                && N.equals(gui, other.gui)
                && N.equals(firstName, other.firstName)
                && N.equals(lastName, other.lastName)
                && N.equals(status, other.status)
                && N.equals(lastUpdateTime, other.lastUpdateTime)
                && N.equals(createTime, other.createTime)
                && N.equals(devices, other.devices)) {

                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return "{"
                 + "id=" + N.toString(id) + ", "
                 + "gui=" + N.toString(gui) + ", "
                 + "firstName=" + N.toString(firstName) + ", "
                 + "lastName=" + N.toString(lastName) + ", "
                 + "status=" + N.toString(status) + ", "
                 + "lastUpdateTime=" + N.toString(lastUpdateTime) + ", "
                 + "createTime=" + N.toString(createTime) + ", "
                 + "devices=" + N.toString(devices)
                 + "}";
    }
}
