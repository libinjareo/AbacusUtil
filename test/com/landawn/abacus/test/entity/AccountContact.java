/*
 * Generated by Abacus.
 */
package com.landawn.abacus.test.entity;

import java.sql.Timestamp;
import com.landawn.abacus.util.N;


/**
 * Generated by Abacus. DO NOT edit it!
 * @version ${version}
 */
public class AccountContact {
    private long id;
    private long accountId;
    private String mobile;
    private String telephone;
    private String email;
    private String address;
    private String address2;
    private String city;
    private String state;
    private String country;
    private String zipCode;
    private String category;
    private String description;
    private int status;
    private Timestamp lastUpdateTime;
    private Timestamp createTime;

    public AccountContact() {
    }

    public AccountContact(long id) {
        this();

        setId(id);
    }

    public AccountContact(long accountId, String mobile, String telephone, String email, 
        String address, String address2, String city, String state, 
        String country, String zipCode, String category, String description, 
        int status, Timestamp lastUpdateTime, Timestamp createTime) {
        this();

        setAccountId(accountId);
        setMobile(mobile);
        setTelephone(telephone);
        setEmail(email);
        setAddress(address);
        setAddress2(address2);
        setCity(city);
        setState(state);
        setCountry(country);
        setZipCode(zipCode);
        setCategory(category);
        setDescription(description);
        setStatus(status);
        setLastUpdateTime(lastUpdateTime);
        setCreateTime(createTime);
    }

    public AccountContact(long id, long accountId, String mobile, String telephone, String email, 
        String address, String address2, String city, String state, 
        String country, String zipCode, String category, String description, 
        int status, Timestamp lastUpdateTime, Timestamp createTime) {
        this();

        setId(id);
        setAccountId(accountId);
        setMobile(mobile);
        setTelephone(telephone);
        setEmail(email);
        setAddress(address);
        setAddress2(address2);
        setCity(city);
        setState(state);
        setCountry(country);
        setZipCode(zipCode);
        setCategory(category);
        setDescription(description);
        setStatus(status);
        setLastUpdateTime(lastUpdateTime);
        setCreateTime(createTime);
    }

    public long getId() {
        return id;
    }

    public AccountContact setId(long id) {
        this.id = id;

        return this;
    }

    public long getAccountId() {
        return accountId;
    }

    public AccountContact setAccountId(long accountId) {
        this.accountId = accountId;

        return this;
    }

    public String getMobile() {
        return mobile;
    }

    public AccountContact setMobile(String mobile) {
        this.mobile = mobile;

        return this;
    }

    public String getTelephone() {
        return telephone;
    }

    public AccountContact setTelephone(String telephone) {
        this.telephone = telephone;

        return this;
    }

    public String getEmail() {
        return email;
    }

    public AccountContact setEmail(String email) {
        this.email = email;

        return this;
    }

    public String getAddress() {
        return address;
    }

    public AccountContact setAddress(String address) {
        this.address = address;

        return this;
    }

    public String getAddress2() {
        return address2;
    }

    public AccountContact setAddress2(String address2) {
        this.address2 = address2;

        return this;
    }

    public String getCity() {
        return city;
    }

    public AccountContact setCity(String city) {
        this.city = city;

        return this;
    }

    public String getState() {
        return state;
    }

    public AccountContact setState(String state) {
        this.state = state;

        return this;
    }

    public String getCountry() {
        return country;
    }

    public AccountContact setCountry(String country) {
        this.country = country;

        return this;
    }

    public String getZipCode() {
        return zipCode;
    }

    public AccountContact setZipCode(String zipCode) {
        this.zipCode = zipCode;

        return this;
    }

    public String getCategory() {
        return category;
    }

    public AccountContact setCategory(String category) {
        this.category = category;

        return this;
    }

    public String getDescription() {
        return description;
    }

    public AccountContact setDescription(String description) {
        this.description = description;

        return this;
    }

    public int getStatus() {
        return status;
    }

    public AccountContact setStatus(int status) {
        this.status = status;

        return this;
    }

    public Timestamp getLastUpdateTime() {
        return lastUpdateTime;
    }

    public AccountContact setLastUpdateTime(Timestamp lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;

        return this;
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public AccountContact setCreateTime(Timestamp createTime) {
        this.createTime = createTime;

        return this;
    }

    public AccountContact copy() {
        AccountContact copy = new AccountContact();

        copy.id = this.id;
        copy.accountId = this.accountId;
        copy.mobile = this.mobile;
        copy.telephone = this.telephone;
        copy.email = this.email;
        copy.address = this.address;
        copy.address2 = this.address2;
        copy.city = this.city;
        copy.state = this.state;
        copy.country = this.country;
        copy.zipCode = this.zipCode;
        copy.category = this.category;
        copy.description = this.description;
        copy.status = this.status;
        copy.lastUpdateTime = this.lastUpdateTime;
        copy.createTime = this.createTime;

        return copy;
    }

    public int hashCode() {
        int h = 17;
        h = 31 * h + N.hashCode(id);
        h = 31 * h + N.hashCode(accountId);
        h = 31 * h + N.hashCode(mobile);
        h = 31 * h + N.hashCode(telephone);
        h = 31 * h + N.hashCode(email);
        h = 31 * h + N.hashCode(address);
        h = 31 * h + N.hashCode(address2);
        h = 31 * h + N.hashCode(city);
        h = 31 * h + N.hashCode(state);
        h = 31 * h + N.hashCode(country);
        h = 31 * h + N.hashCode(zipCode);
        h = 31 * h + N.hashCode(category);
        h = 31 * h + N.hashCode(description);
        h = 31 * h + N.hashCode(status);
        h = 31 * h + N.hashCode(lastUpdateTime);
        h = 31 * h + N.hashCode(createTime);

        return h;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof AccountContact) {
            AccountContact other = (AccountContact) obj;

            if (N.equals(id, other.id)
                && N.equals(accountId, other.accountId)
                && N.equals(mobile, other.mobile)
                && N.equals(telephone, other.telephone)
                && N.equals(email, other.email)
                && N.equals(address, other.address)
                && N.equals(address2, other.address2)
                && N.equals(city, other.city)
                && N.equals(state, other.state)
                && N.equals(country, other.country)
                && N.equals(zipCode, other.zipCode)
                && N.equals(category, other.category)
                && N.equals(description, other.description)
                && N.equals(status, other.status)
                && N.equals(lastUpdateTime, other.lastUpdateTime)
                && N.equals(createTime, other.createTime)) {

                return true;
            }
        }

        return false;
    }

    public String toString() {
        return "{"
                 + "id=" + N.toString(id) + ", "
                 + "accountId=" + N.toString(accountId) + ", "
                 + "mobile=" + N.toString(mobile) + ", "
                 + "telephone=" + N.toString(telephone) + ", "
                 + "email=" + N.toString(email) + ", "
                 + "address=" + N.toString(address) + ", "
                 + "address2=" + N.toString(address2) + ", "
                 + "city=" + N.toString(city) + ", "
                 + "state=" + N.toString(state) + ", "
                 + "country=" + N.toString(country) + ", "
                 + "zipCode=" + N.toString(zipCode) + ", "
                 + "category=" + N.toString(category) + ", "
                 + "description=" + N.toString(description) + ", "
                 + "status=" + N.toString(status) + ", "
                 + "lastUpdateTime=" + N.toString(lastUpdateTime) + ", "
                 + "createTime=" + N.toString(createTime)
                 + "}";
    }
}