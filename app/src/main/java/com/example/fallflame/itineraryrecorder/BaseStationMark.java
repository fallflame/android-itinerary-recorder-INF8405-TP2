package com.example.fallflame.itineraryrecorder;

/**
 * Created by FallFlame on 15/3/2.
 */
public class BaseStationMark {

    private String type_r;
    private String mcc;
    private String mnc;
    private String mnc_name;
    private int cell_id;
    private int lac;
    private String niv_sig_sb;
    private double long_sb;
    private double lat_sb;

    public double getLat_sb() {
        return lat_sb;
    }

    public void setLat_sb(double lat_sb) {
        this.lat_sb = lat_sb;
    }

    public String getType_r() {
        return type_r;
    }

    public void setType_r(String type_r) {
        this.type_r = type_r;
    }

    public String getMcc() {
        return mcc;
    }

    public void setMcc(String mcc) {
        this.mcc = mcc;
    }

    public String getMnc() {
        return mnc;
    }

    public void setMnc(String mnc) {
        this.mnc = mnc;
    }

    public String getMnc_name() {
        return mnc_name;
    }

    public void setMnc_name(String mnc_name) {
        this.mnc_name = mnc_name;
    }

    public int getCell_id() {
        return cell_id;
    }

    public void setCell_id(int cell_id) {
        this.cell_id = cell_id;
    }

    public int getLac() {
        return lac;
    }

    public void setLac(int lac) {
        this.lac = lac;
    }

    public String getNiv_sig_sb() {
        return niv_sig_sb;
    }

    public void setNiv_sig_sb(String niv_sig_sb) {
        this.niv_sig_sb = niv_sig_sb;
    }

    public double getLong_sb() {
        return long_sb;
    }

    public void setLong_sb(double long_sb) {
        this.long_sb = long_sb;
    }


    public String getInfoString(){

        String ret =  "Type_R: " + type_r + "\n"
                    + "mcc: " + mcc + "\n"
                    + "mnc: " + mnc + "\n"
                    + "MNC_Name: " + mnc_name + "\n"
                    + "Cell_ID: " + cell_id + "\n"
                    + "LAC: " + lac + "\n"
                    + "Niv_sig_sb: " + niv_sig_sb + "\n"
                    + "Lng: " + long_sb + "\n"
                    + "Lat: " + lat_sb + "\n";

        return ret;
    }

}
