package com.example.fallflame.itineraryrecorder;

import java.io.Serializable;
import java.text.SimpleDateFormat;

/**
 * Created by FallFlame on 15/3/2.
 */
public class ItineraryPointMark implements Serializable{
    private ItineraryPointMark previousMark;
    private String mode;
    private double[] position; //double[3] lat, lng, alt
    private long currentTime;
    private String imageURI;
    private double batteryLevel;
    private String wifiInfo;


    public ItineraryPointMark(){
        this.currentTime = System.currentTimeMillis();
    }

    public String getDirection(){

        if (previousMark == null)
            return "";

        double bearing = bearing(previousMark.getPosition()[0], previousMark.getPosition()[1], position[0], position[1]);

        String ret = "error";

        if (bearing >= 0 && bearing <= 360 / 16
                || bearing > 360 * 15 / 16  && bearing <= 360)
            ret = "North";

        else if (bearing > 360 / 16 && bearing <= 360 * 3 / 16)
            ret = "North-East";

        else if (bearing > 360 * 3 / 16 && bearing <= 360 * 5 / 16)
            ret = "East";

        else if (bearing > 360 * 5 / 16 && bearing <= 360 * 7 / 16)
            ret = "South-East";

        else if (bearing > 360 * 7 / 16 && bearing <= 360 * 9 / 16)
            ret = "South";

        else if (bearing > 360 * 9 / 16 && bearing <= 360 * 11 / 16)
            ret = "South-West";

        else if (bearing > 360 * 11 / 16 && bearing <= 360 * 13 / 16)
            ret = "West";

        else if (bearing > 360 * 13 / 16 && bearing <= 360 * 15 / 16)
            ret = "North-West";

        return ret;
    }

    public double getDistanceFromPreviousMark(){

        if(previousMark == null)
            return 0;

        double horizontalDistance = distFrom(position[0], position[1], previousMark.getPosition()[0], previousMark.getPosition()[1]);
        double verticalDistance = position[2] - previousMark.getPosition()[2];
        double distance = Math.sqrt(horizontalDistance * horizontalDistance + verticalDistance * verticalDistance);

        return distance;
    }

    /**
     *
     * @return in m/s
     */
    public double getSpeed(){
        if (previousMark == null)
            return 0;

        return this.getDistanceFromPreviousMark() / (this.currentTime - this.previousMark.getCurrentTime()) * 1000;
    }

    /**
     * http://stackoverflow.com/a/123305/4285717
     * @return in meters
     */
    private double distFrom(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6371 * 1000;
        double dLat = Math.toRadians(lat2-lat1);
        double dLng = Math.toRadians(lng2-lng1);
        double sindLat = Math.sin(dLat / 2);
        double sindLng = Math.sin(dLng / 2);
        double a = Math.pow(sindLat, 2) + Math.pow(sindLng, 2)
                * Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double dist = earthRadius * c;

        return dist;
    }

    /**
     * http://stackoverflow.com/a/9462757/4285717
     * @return bearing in degree
     */
    private double bearing(double lat1, double lon1, double lat2, double lon2){
        double longitude1 = lon1;
        double longitude2 = lon2;
        double latitude1 = Math.toRadians(lat1);
        double latitude2 = Math.toRadians(lat2);
        double longDiff= Math.toRadians(longitude2-longitude1);
        double y= Math.sin(longDiff)*Math.cos(latitude2);
        double x=Math.cos(latitude1)*Math.sin(latitude2)-Math.sin(latitude1)*Math.cos(latitude2)*Math.cos(longDiff);

        return (Math.toDegrees(Math.atan2(y, x))+360)%360;
    }

    public String getInfoString(){

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timeString = df.format(currentTime);
        java.text.DecimalFormat decimalFormat6 = new java.text.DecimalFormat(".000000");

        String ret =  "Mode: " + mode + "\n"
                    + "Date: " + timeString + "\n"
                    + "Lat: " + decimalFormat6.format(position[0]) + "\n"
                    + "Lng: " + decimalFormat6.format(position[1]) + "\n"
                    + "Battery: " + (int)(batteryLevel * 100) + "%\n"
                    + "Direction: " + getDirection() + "\n"
                    + "Distance(relative): " + (int)getDistanceFromPreviousMark() + "\n"
                    + "Speed: " + (int) getSpeed() + " m/s\n";

        if (getWifiInfo() != null)
            ret += "Wifi: " + getWifiInfo();

        return ret;
    }

    /******************* Setter and Getter *******************/

    public void setPreviousMark(ItineraryPointMark previousMark) {
        this.previousMark = previousMark;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    public void setPosition(double lat, double lng, double alt) {
        double[] position = {lat, lng, alt};
        this.position = position;
    }

    public double[] getPosition() {
        return position;
    }

    public void setImageURI(String imageURI) {
        this.imageURI = imageURI;
    }

    public String getImageURI() {
        return imageURI;
    }

    public void setBatteryLevel(double batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public double getBatteryLevel() {
        return batteryLevel;
    }

    public long getCurrentTime() {
        return currentTime;
    }

    public void setWifiInfo(String wifiInfo) {
        this.wifiInfo = wifiInfo;
    }

    public String getWifiInfo() {
        return wifiInfo;
    }
}

